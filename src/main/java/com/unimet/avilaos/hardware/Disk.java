package com.unimet.avilaos.hardware;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.datastructures.LinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * Disco simulado (SD): un componente del hardware que obedece al reloj, igual
 * que la CPU y el dispositivo de E/S del Proyecto 1. Atiende UNA solicitud a la
 * vez: pide la siguiente al planificador de disco (por la interfaz
 * {@link SeekPlanSource}), mueve el cabezal por el recorrido que este le da y
 * transfiere los bloques. Al terminar deja la solicitud en
 * {@link #pollCompleted()} y avisa a la CPU con una interrupción
 * DISK_COMPLETION; el kernel aplica entonces la operación del sistema de
 * archivos y despierta al proceso.
 *
 * Tiempo (lo pide el enunciado):
 * <ul>
 *   <li>el cabezal recorre hasta N posiciones por ciclo (configurable);</li>
 *   <li>cada bloque leído o escrito cuesta 1 ciclo;</li>
 *   <li>el salto de C-SCAN y C-LOOK cuesta 1 ciclo y no cuenta como
 *       desplazamiento (acuerdo del equipo, coherente con el anexo).</li>
 * </ul>
 * Cada tramo del recorrido (barrido, salto, movimiento) termina en su ciclo: si
 * al cabezal le sobran posiciones al llegar, no se usan en el tramo siguiente.
 * La transferencia empieza en el ciclo siguiente al de llegada, salvo que la
 * solicitud esté en el bloque del cabezal (entonces empieza en el mismo ciclo).
 *
 * Implementa ClockListener, la misma interfaz que los demás componentes que
 * reaccionan al reloj (lo pide el enunciado). Igual que el IoDevice del P1, no
 * se registra directamente en el GlobalClock: el Computer llama disk.onTick()
 * antes que cpu.onTick(), en su propio hilo, así la interrupción de este ciclo
 * la atiende la CPU al final del mismo ciclo.
 *
 * Concurrencia: un Semaphore protege el estado. El planificador (código ajeno)
 * y cpu.raiseInterrupt se llaman FUERA del mutex. Mientras el disco le pide la
 * siguiente solicitud al planificador, esa solicitud ya salió de la cola pero
 * todavía no está en el disco; si en ese momento llega un cancel(pid), se anota
 * y la solicitud se descarta al recibirla si es de ese proceso (sin interrupción).
 *
 * @author NicollPinzon
 */
public class Disk implements ClockListener {

    private final Cpu cpu;
    private final BlockMap blocks;
    private final SeekPlanSource source;
    private final LinkedQueue<DiskRequest> completed = new LinkedQueue<>();
    private final LinkedList<Integer> cancelledWhileFetching = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1, true);

    private int head;
    private HeadDirection direction;
    private int positionsPerCycle;

    private SeekPlan plan;               // recorrido en curso (null = libre)
    private boolean fetching;            // pidiendo la siguiente solicitud al planificador
    private DiskPhase phase = DiskPhase.IDLE;
    private int legTarget;               // a dónde va el tramo actual (SWEEP o SEEK)
    private int remainingBlocks;         // bloques que faltan por transferir

    private long totalSeek;              // desplazamiento total, sin los saltos
    private long jumps;
    private long servedRequests;
    private long cancelledRequests;
    private long busyCycles;

    /**
     * @param cpu               CPU a la que avisa con la interrupción de fin de E/S de disco
     * @param blocks            mapa de bloques (da el tamaño del disco)
     * @param source            planificador de disco
     * @param initialHead       posición inicial del cabezal
     * @param initialDirection  dirección inicial del cabezal
     * @param positionsPerCycle posiciones que recorre el cabezal por ciclo (1 o más)
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public Disk(Cpu cpu, BlockMap blocks, SeekPlanSource source, int initialHead, HeadDirection initialDirection,
            int positionsPerCycle) {
        if (cpu == null) {
            throw new IllegalArgumentException("El disco necesita la CPU para interrumpirla");
        }
        if (blocks == null) {
            throw new IllegalArgumentException("El disco necesita su mapa de bloques");
        }
        if (source == null) {
            throw new IllegalArgumentException("El disco necesita el planificador de disco");
        }
        if (initialHead < 0 || initialHead >= blocks.getTotalBlocks()) {
            throw new IllegalArgumentException("El cabezal inicial " + initialHead + " está fuera del disco (0 a "
                    + (blocks.getTotalBlocks() - 1) + ")");
        }
        if (initialDirection == null) {
            throw new IllegalArgumentException("El disco necesita la dirección inicial del cabezal");
        }
        checkSpeed(positionsPerCycle);
        this.cpu = cpu;
        this.blocks = blocks;
        this.source = source;
        this.head = initialHead;
        this.direction = initialDirection;
        this.positionsPerCycle = positionsPerCycle;
    }

    // ---------- Un ciclo de reloj ----------

    @Override
    public void onTick(long cycle) {
        int headNow;
        HeadDirection directionNow;
        boolean idle;
        mutex.acquireUninterruptibly();
        try {
            idle = plan == null;
            fetching = idle;
            headNow = head;
            directionNow = direction;
        } finally {
            mutex.release();
        }
        SeekPlan next = idle ? source.next(headNow, directionNow) : null;   // FUERA del mutex

        Interrupt finished = null;
        mutex.acquireUninterruptibly();
        try {
            fetching = false;
            if (next != null && wasCancelledWhileFetching(next.getRequest().getProcessId())) {
                cancelledRequests++;          // la cancelaron mientras se pedía: no se atiende
                next = null;
            }
            cancelledWhileFetching.clear();
            if (plan == null && next != null) {
                start(next);
            }
            if (plan != null) {
                busyCycles++;
                if (step()) {
                    DiskRequest request = plan.getRequest();
                    completed.enqueue(request);
                    finished = new Interrupt(InterruptType.DISK_COMPLETION, request.getProcessId());
                    direction = plan.getDirectionAfter();
                    servedRequests++;
                    clear();
                }
            }
        } finally {
            mutex.release();
        }
        if (finished != null) {
            cpu.raiseInterrupt(finished);                                    // FUERA del mutex
        }
    }

    // ---------- Lo usa el kernel ----------

    /**
     * Saca la siguiente solicitud terminada (en el orden en que terminaron). El
     * kernel la pide al atender cada interrupción DISK_COMPLETION.
     *
     * @return la solicitud terminada, o null si no hay
     */
    public DiskRequest pollCompleted() {
        mutex.acquireUninterruptibly();
        try {
            return completed.dequeue();
        } finally {
            mutex.release();
        }
    }

    /**
     * Aborta la solicitud en servicio si es de ese proceso (por ejemplo, se le
     * venció el deadline). No hay interrupción. Las que esperan en la cola las
     * cancela el planificador de disco.
     *
     * Si llega justo mientras el disco le pide la siguiente solicitud al
     * planificador, se anota: si la solicitud que llega es de ese proceso, se
     * descarta sin atenderla. En ese caso devuelve false (todavía no había
     * solicitud en servicio), pero la solicitud se descarta igual y se cuenta
     * en {@link #getCancelledRequests()} cuando llega.
     *
     * @return true si se abortó la solicitud en servicio; false si no había
     *         una de ese proceso en servicio (incluido el caso anterior)
     */
    public boolean cancel(int processId) {
        mutex.acquireUninterruptibly();
        try {
            if (fetching) {
                cancelledWhileFetching.addLast(processId);
            }
            if (plan == null || plan.getRequest().getProcessId() != processId) {
                return false;
            }
            cancelledRequests++;
            clear();
            return true;
        } finally {
            mutex.release();
        }
    }

    /** Cambia las posiciones por ciclo del cabezal (configuración). */
    public void setPositionsPerCycle(int positionsPerCycle) {
        checkSpeed(positionsPerCycle);
        mutex.acquireUninterruptibly();
        try {
            this.positionsPerCycle = positionsPerCycle;
        } finally {
            mutex.release();
        }
    }

    // ---------- Consultas (GUI, log y métricas) ----------

    public int getHeadBlock() {
        mutex.acquireUninterruptibly();
        try {
            return head;
        } finally {
            mutex.release();
        }
    }

    public HeadDirection getHeadDirection() {
        mutex.acquireUninterruptibly();
        try {
            return direction;
        } finally {
            mutex.release();
        }
    }

    public DiskPhase getPhase() {
        mutex.acquireUninterruptibly();
        try {
            return phase;
        } finally {
            mutex.release();
        }
    }

    /** Solicitud en servicio (inmutable), o null si el disco está libre. */
    public DiskRequest getCurrentRequest() {
        mutex.acquireUninterruptibly();
        try {
            return plan == null ? null : plan.getRequest();
        } finally {
            mutex.release();
        }
    }

    /** Recorrido en curso (inmutable), o null si el disco está libre. */
    public SeekPlan getCurrentPlan() {
        mutex.acquireUninterruptibly();
        try {
            return plan;
        } finally {
            mutex.release();
        }
    }

    public boolean isBusy() {
        return getCurrentRequest() != null;
    }

    /** Desplazamiento total del cabezal en bloques (los saltos no cuentan). */
    public long getTotalSeek() {
        mutex.acquireUninterruptibly();
        try {
            return totalSeek;
        } finally {
            mutex.release();
        }
    }

    public long getJumps() {
        mutex.acquireUninterruptibly();
        try {
            return jumps;
        } finally {
            mutex.release();
        }
    }

    public long getServedRequests() {
        mutex.acquireUninterruptibly();
        try {
            return servedRequests;
        } finally {
            mutex.release();
        }
    }

    public long getCancelledRequests() {
        mutex.acquireUninterruptibly();
        try {
            return cancelledRequests;
        } finally {
            mutex.release();
        }
    }

    public long getBusyCycles() {
        mutex.acquireUninterruptibly();
        try {
            return busyCycles;
        } finally {
            mutex.release();
        }
    }

    public int getPositionsPerCycle() {
        mutex.acquireUninterruptibly();
        try {
            return positionsPerCycle;
        } finally {
            mutex.release();
        }
    }

    public BlockMap getBlockMap() {
        return blocks;
    }

    // ---------- Internos (con el mutex tomado) ----------

    private void start(SeekPlan next) {
        int last = blocks.getTotalBlocks() - 1;
        if (next.getRequest().getTargetBlock() > last || next.getSweepEdge() > last || next.getJumpTo() > last) {
            throw new IllegalStateException("El recorrido " + next + " se sale del disco (0 a " + last + ")");
        }
        if (next.getStartBlock() != head) {
            throw new IllegalStateException("El recorrido empieza en " + next.getStartBlock()
                    + " y el cabezal está en " + head);
        }
        plan = next;
        remainingBlocks = next.getRequest().getBlockCount();
        if (next.hasSweep()) {
            enterMove(DiskPhase.SWEEP, next.getSweepEdge());
        } else if (next.hasJump()) {
            phase = DiskPhase.JUMP;
        } else {
            enterMove(DiskPhase.SEEK, next.getRequest().getTargetBlock());
        }
    }

    /** Avanza un ciclo. @return true si la solicitud terminó en este ciclo */
    private boolean step() {
        switch (phase) {
            case SWEEP, SEEK -> {
                int distance = Math.abs(legTarget - head);
                int moved = Math.min(positionsPerCycle, distance);
                direction = HeadDirection.between(head, legTarget, direction);
                head += moved * direction.getStep();
                totalSeek += moved;
                if (head == legTarget) {
                    afterLeg();
                }
                return false;
            }
            case JUMP -> {
                head = plan.getJumpTo();
                jumps++;
                enterMove(DiskPhase.SEEK, plan.getRequest().getTargetBlock());
                return false;
            }
            case TRANSFER -> {
                remainingBlocks--;
                return remainingBlocks == 0;
            }
            default -> throw new IllegalStateException("El disco no tiene nada que hacer en la fase " + phase);
        }
    }

    /** Terminó un tramo de movimiento: sigue el salto o la transferencia. */
    private void afterLeg() {
        if (phase == DiskPhase.SWEEP && plan.hasJump()) {
            phase = DiskPhase.JUMP;
        } else if (phase == DiskPhase.SWEEP) {
            enterMove(DiskPhase.SEEK, plan.getRequest().getTargetBlock());
        } else {
            phase = DiskPhase.TRANSFER;
        }
    }

    /**
     * Entra a un tramo de movimiento. Si el cabezal ya está en el destino, el
     * tramo no gasta ciclo: se pasa directo al siguiente (si es la transferencia,
     * empieza en este mismo ciclo).
     */
    private void enterMove(DiskPhase movePhase, int target) {
        phase = movePhase;
        legTarget = target;
        if (head == target) {
            afterLeg();
        }
    }

    /** LinkedList.contains compara referencias; aquí se compara el número del proceso. */
    private boolean wasCancelledWhileFetching(int processId) {
        for (int i = 0; i < cancelledWhileFetching.size(); i++) {
            if (cancelledWhileFetching.get(i) == processId) {
                return true;
            }
        }
        return false;
    }

    private void clear() {
        plan = null;
        phase = DiskPhase.IDLE;
        remainingBlocks = 0;
    }

    private static void checkSpeed(int positionsPerCycle) {
        if (positionsPerCycle < 1) {
            throw new IllegalArgumentException("El cabezal debe recorrer al menos 1 posición por ciclo");
        }
    }
}
