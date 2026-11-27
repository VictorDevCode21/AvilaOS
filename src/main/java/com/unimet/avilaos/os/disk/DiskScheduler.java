package com.unimet.avilaos.os.disk;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.SeekPlan;
import com.unimet.avilaos.hardware.SeekPlanSource;
import java.util.concurrent.Semaphore;

/**
 * Planificador de disco: el MECANISMO. Guarda la única cola de E/S del disco,
 * le pregunta a la política cuál sigue y saca esa solicitud de la cola. La
 * política (DiskSchedulingPolicy) es el QUÉ; esta clase no sabe nada de FIFO,
 * SCAN ni de ninguna otra, así que agregar una política no la modifica.
 *
 * Uso:
 * <ul>
 *   <li>el kernel encola con {@link #enqueue} la solicitud de un proceso de E/S
 *       (o de una suspensión o reactivación);</li>
 *   <li>el disco, cuando queda libre, pide {@link #next} con la posición y la
 *       dirección del cabezal y recorre el SeekPlan que recibe;</li>
 *   <li>la GUI cambia la política en ejecución con {@link #setPolicy}, lee la
 *       cola en orden de llegada con {@link #getPendingRequests} y en el orden
 *       en que se atendería con {@link #previewServiceOrder} (copias).</li>
 * </ul>
 *
 * Retener la primera decisión: el kernel puede pedir con {@link #holdFirstDecision} que el
 * disco no elija nada todavía (next() devuelve null aunque haya solicitudes) y soltarlo con
 * {@link #releaseFirstDecision}. Las solicitudes se siguen encolando. Lo usa la regla "cola
 * inicial completa": la primera decisión ve juntas las solicitudes de la carga inicial.
 *
 * Cambio de política en caliente: la nueva política se usa desde la siguiente
 * llamada a next(). La solicitud que el disco ya está atendiendo no se
 * interrumpe, y como las políticas no guardan estado no queda nada a medias.
 *
 * Implementa SeekPlanSource: el disco (hardware) le pide la siguiente solicitud
 * por esa interfaz, sin depender del sistema operativo.
 *
 * Concurrencia: la cola y la política se protegen con un Semaphore justo. La
 * política se consulta FUERA del mutex, sobre una copia de la cola (regla del
 * equipo: no llamar código ajeno con el mutex tomado). Si mientras tanto la
 * solicitud elegida se canceló, se vuelve a preguntar.
 *
 * @author NicollPinzon
 */
public class DiskScheduler implements SeekPlanSource {

    private final int fileBlocks;
    private final int diskSize;
    private final LinkedList<DiskRequest> queue = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1, true);

    private DiskSchedulingPolicy policy;
    private boolean holding;            // true: next() no elige nada hasta releaseFirstDecision()

    /**
     * Disco sin área de intercambio (todos los bloques son de archivos).
     *
     * @param diskSize      cantidad de bloques del disco (mayor que 0)
     * @param initialPolicy política con la que arranca
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public DiskScheduler(int diskSize, DiskPolicyType initialPolicy) {
        this(diskSize, 0, initialPolicy);
    }

    /**
     * Disco con zona de archivos y área de intercambio al final (como el BlockMap).
     * SCAN y C-SCAN barren hasta el último bloque de la zona de archivos (el 199
     * con el JSON del enunciado); las solicitudes al swap se aceptan y se atienden igual.
     *
     * @param fileBlocks    bloques de la zona de archivos (mayor que 0)
     * @param swapBlocks    bloques del área de intercambio (0 o más)
     * @param initialPolicy política con la que arranca
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public DiskScheduler(int fileBlocks, int swapBlocks, DiskPolicyType initialPolicy) {
        if (fileBlocks <= 0) {
            throw new IllegalArgumentException("El disco debe tener al menos 1 bloque");
        }
        if (swapBlocks < 0) {
            throw new IllegalArgumentException("El área de intercambio no puede tener bloques negativos");
        }
        if (initialPolicy == null) {
            throw new IllegalArgumentException("El planificador de disco necesita una política inicial");
        }
        this.fileBlocks = fileBlocks;
        this.diskSize = fileBlocks + swapBlocks;
        this.policy = initialPolicy.newPolicy();
    }

    // ---------- Lo usa el kernel ----------

    /**
     * Agrega una solicitud al final de la cola de E/S (orden de llegada).
     *
     * @throws IllegalArgumentException si es null o su bloque está fuera del disco
     * @throws IllegalStateException    si esa misma solicitud ya está en la cola
     */
    public void enqueue(DiskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de disco no puede ser null");
        }
        if (request.getTargetBlock() >= diskSize) {
            throw new IllegalArgumentException("El bloque " + request.getTargetBlock()
                    + " está fuera del disco (bloques 0 a " + (diskSize - 1) + ")");
        }
        mutex.acquireUninterruptibly();
        try {
            if (queue.contains(request)) {
                throw new IllegalStateException("La solicitud " + request + " ya está en la cola de E/S");
            }
            queue.addLast(request);
        } finally {
            mutex.release();
        }
    }

    /**
     * Saca de la cola las solicitudes de un proceso (por ejemplo, si se le vence
     * el deadline mientras espera el disco). La que el disco ya está atendiendo
     * no está en la cola: esa la cancela el disco.
     *
     * @return cuántas solicitudes se sacaron
     */
    public int cancel(int processId) {
        mutex.acquireUninterruptibly();
        try {
            // Se vuelve a armar la cola sin las del proceso: una sola pasada, O(n).
            int removed = 0;
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                DiskRequest r = queue.removeFirst();
                if (r.getProcessId() == processId) {
                    removed++;
                } else {
                    queue.addLast(r);
                }
            }
            return removed;
        } finally {
            mutex.release();
        }
    }

    // ---------- Lo usa el disco ----------

    /**
     * Elige la siguiente solicitud con la política actual, la saca de la cola y
     * devuelve el recorrido del cabezal para llegar a ella.
     *
     * @param headBlock bloque donde está el cabezal
     * @param direction dirección actual del cabezal
     * @return el recorrido, o null si la cola está vacía o la decisión está retenida
     * @throws IllegalArgumentException si el cabezal está fuera del disco o la dirección es null
     * @throws IllegalStateException    si la política devuelve un recorrido inválido
     */
    @Override
    public SeekPlan next(int headBlock, HeadDirection direction) {
        if (headBlock < 0 || headBlock >= diskSize) {
            throw new IllegalArgumentException("El cabezal está fuera del disco: " + headBlock);
        }
        if (direction == null) {
            throw new IllegalArgumentException("La dirección del cabezal no puede ser null");
        }
        while (true) {
            LinkedList<DiskRequest> snapshot;
            DiskSchedulingPolicy current;
            mutex.acquireUninterruptibly();
            try {
                if (holding || queue.isEmpty()) {
                    return null;
                }
                snapshot = queue.copy();
                current = policy;
            } finally {
                mutex.release();
            }

            SeekPlan plan = current.select(snapshot, headBlock, direction, fileBlocks);   // FUERA del mutex
            check(plan, snapshot, headBlock, current);

            mutex.acquireUninterruptibly();
            try {
                if (queue.remove(plan.getRequest())) {
                    return plan.chosenBy(current.getType().getLabel());   // para el log del disco
                }
                // La eligió pero se canceló mientras tanto: se vuelve a preguntar.
            } finally {
                mutex.release();
            }
        }
    }

    // ---------- Lo usa el kernel: retener la primera decisión ----------

    /**
     * Retiene las decisiones: mientras tanto next() devuelve null y las solicitudes esperan en
     * la cola. Lo pide el kernel antes de iniciar el reloj para la carga inicial.
     */
    public void holdFirstDecision() {
        mutex.acquireUninterruptibly();
        try {
            holding = true;
        } finally {
            mutex.release();
        }
    }

    /** Suelta la retención: el disco decide desde su próximo ciclo. No hace nada si no estaba retenido. */
    public void releaseFirstDecision() {
        mutex.acquireUninterruptibly();
        try {
            holding = false;
        } finally {
            mutex.release();
        }
    }

    // ---------- Lo usa la GUI ----------

    /** true si el disco todavía espera a la carga inicial (la vista del disco lo muestra). */
    public boolean isHolding() {
        mutex.acquireUninterruptibly();
        try {
            return holding;
        } finally {
            mutex.release();
        }
    }

    /**
     * Cambia la política en ejecución; se aplica desde la próxima solicitud.
     *
     * @throws IllegalArgumentException si es null
     */
    public void setPolicy(DiskPolicyType type) {
        if (type == null) {
            throw new IllegalArgumentException("La política de disco no puede ser null");
        }
        DiskSchedulingPolicy created = type.newPolicy();
        mutex.acquireUninterruptibly();
        try {
            policy = created;
        } finally {
            mutex.release();
        }
    }

    /**
     * Orden en que se atendería la cola si no llegara nada más, con la política
     * actual y a partir del cabezal dado. Es para la vista del disco: no saca
     * nada de la cola real (trabaja sobre una copia) y la política se consulta
     * fuera del mutex.
     *
     * @param headBlock bloque donde está el cabezal
     * @param direction dirección actual del cabezal
     * @return los recorridos en orden de servicio; vacía si no hay solicitudes
     * @throws IllegalArgumentException si el cabezal está fuera del disco o la dirección es null
     */
    public LinkedList<SeekPlan> previewServiceOrder(int headBlock, HeadDirection direction) {
        if (headBlock < 0 || headBlock >= diskSize) {
            throw new IllegalArgumentException("El cabezal está fuera del disco: " + headBlock);
        }
        if (direction == null) {
            throw new IllegalArgumentException("La dirección del cabezal no puede ser null");
        }
        LinkedList<DiskRequest> pending;
        DiskSchedulingPolicy current;
        mutex.acquireUninterruptibly();
        try {
            pending = queue.copy();
            current = policy;
        } finally {
            mutex.release();
        }
        LinkedList<SeekPlan> order = new LinkedList<>();
        int head = headBlock;
        HeadDirection dir = direction;
        while (!pending.isEmpty()) {
            SeekPlan plan = current.select(pending.copy(), head, dir, fileBlocks);
            check(plan, pending, head, current);
            pending.remove(plan.getRequest());
            order.addLast(plan);
            head = plan.getRequest().getTargetBlock();
            dir = plan.getDirectionAfter();
        }
        return order;
    }

    // ---------- Consultas (GUI, métricas y log) ----------

    public DiskPolicyType getPolicyType() {
        mutex.acquireUninterruptibly();
        try {
            return policy.getType();
        } finally {
            mutex.release();
        }
    }

    /** Copia de la cola en orden de llegada (la GUI puede recorrerla sin bloquear el disco). */
    public LinkedList<DiskRequest> getPendingRequests() {
        mutex.acquireUninterruptibly();
        try {
            return queue.copy();
        } finally {
            mutex.release();
        }
    }

    public int getPendingCount() {
        mutex.acquireUninterruptibly();
        try {
            return queue.size();
        } finally {
            mutex.release();
        }
    }

    /** Bloques del disco completo (zona de archivos + área de intercambio). */
    public int getDiskSize() {
        return diskSize;
    }

    /** Bloques de la zona de archivos: SCAN y C-SCAN barren hasta fileBlocks - 1. */
    public int getFileBlocks() {
        return fileBlocks;
    }

    /** Un error de la política se detecta aquí, antes de que el disco mueva el cabezal. */
    private void check(SeekPlan plan, LinkedList<DiskRequest> snapshot, int headBlock,
            DiskSchedulingPolicy current) {
        String name = current.getType().getLabel();
        if (plan == null) {
            throw new IllegalStateException("La política " + name + " no eligió ninguna solicitud");
        }
        if (!snapshot.contains(plan.getRequest())) {
            throw new IllegalStateException("La política " + name + " eligió una solicitud que no está en la cola");
        }
        if (plan.getStartBlock() != headBlock) {
            throw new IllegalStateException("La política " + name + " calculó el recorrido desde el bloque "
                    + plan.getStartBlock() + " y el cabezal está en " + headBlock);
        }
        if (plan.getSweepEdge() >= diskSize || plan.getJumpTo() >= diskSize) {
            throw new IllegalStateException("La política " + name + " llevó el cabezal fuera del disco");
        }
    }
}
