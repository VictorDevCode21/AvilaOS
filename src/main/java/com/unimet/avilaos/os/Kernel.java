package com.unimet.avilaos.os;

import com.unimet.avilaos.datastructures.ElementComparator;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.events.EventLog;
import com.unimet.avilaos.hardware.Cpu;
import com.unimet.avilaos.hardware.CpuMode;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.Interrupt;
import com.unimet.avilaos.hardware.InterruptHandler;
import com.unimet.avilaos.hardware.InterruptType;
import com.unimet.avilaos.hardware.IoDevice;
import com.unimet.avilaos.hardware.IoOperation;
import com.unimet.avilaos.hardware.SeekPlan;
import com.unimet.avilaos.metrics.ComputerMetrics;
import com.unimet.avilaos.metrics.SystemMetrics;
import com.unimet.avilaos.os.disk.DiskPolicyType;
import com.unimet.avilaos.os.fs.FsOperation;
import com.unimet.avilaos.os.fs.FsResult;
import com.unimet.avilaos.os.memory.MemoryManager;
import com.unimet.avilaos.os.process.BlockReason;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.process.ProcessState;
import com.unimet.avilaos.os.process.ProcessType;
import com.unimet.avilaos.os.scheduling.PolicyType;
import com.unimet.avilaos.os.scheduling.Scheduler;
import com.unimet.avilaos.os.sync.BoundedBuffer;
import com.unimet.avilaos.os.sync.KernelSemaphore;
import java.util.concurrent.Semaphore;

/**
 * Núcleo del sistema operativo de UN computador: el único que DECIDE.
 *
 * El hardware (Cpu, IoDevice, MainMemory) solo obedece; aquí se decide quién se
 * admite, quién usa la CPU, quién se bloquea, quién despierta y quién termina.
 * Es también el único lugar donde cambian los estados de los procesos.
 *
 * Cada ciclo, el Computer lo llama en su propio hilo, en este orden:
 *   1. io.onTick(c), disk.onTick(c)   el dispositivo de E/S del P1 y el disco avanzan
 *   2. kernel.beforeCpu(c)     PLANEA: solicitudes, despertares, admisión,
 *                              expropiación y qué hará la CPU este ciclo
 *   3. cpu.onTick(c)           EJECUTA (y atiende interrupciones → handleInterrupt)
 *   4. kernel.afterCpu(c)      APLICA el efecto, descuenta deadlines y termina procesos
 *   5. metrics.recordCycle(c)
 *
 * Decisiones (ver documento de diseño del Kernel):
 *  - Despachar, pedir E/S y enviar una solicitud remota cuestan 1 ciclo de trabajo
 *    del SO que no avanza el PC (no son instrucciones del proceso).
 *  - Cada operación sobre un buffer remoto espera la latencia de red antes de ejecutarse.
 *  - Los despertares se entregan como mensaje al computador dueño y se procesan al
 *    inicio del ciclo siguiente (opción B: nadie cambia el PCB de otro computador).
 *  - Si un proceso termina y se le vence el deadline en el mismo ciclo, gana terminar.
 *  - Al morir un proceso se deshace lo que dejó tomado (como SEM_UNDO en UNIX).
 *
 * Proceso de E/S de archivo (Proyecto 2):
 *  - Al obtener la CPU, su primera instrucción (en modo SO) es pedir su operación:
 *    el sistema de archivos la revisa (check) y, si se puede, la solicitud entra a la
 *    cola del disco y el proceso queda Bloqueado por E/S. Si no se puede (por ejemplo,
 *    otro proceso borró el archivo mientras esperaba en Listo), el proceso termina y el
 *    log explica por qué, como una llamada al sistema que falla.
 *  - Con la interrupción DISK_COMPLETION toma la solicitud terminada (Disk.pollCompleted),
 *    aplica la operación (FileSystem.apply), lo anota en el log y el proceso pasa a Listo
 *    para ejecutar el resto de sus instrucciones (o termina, si no le quedan).
 *  - Si muere esperando, su solicitud se cancela en la cola del disco y en el disco.
 *
 * Planificador a mediano plazo y modelo de 7 estados (Proyecto 2). Corre al inicio de cada
 * ciclo, antes de admitir Nuevos (mediumTerm):
 *  1. Un Nuevo por el que ya se suspendieron procesos entra apenas cabe, aunque sus
 *     víctimas todavía se estén escribiendo (si otro proceso terminó y liberó RAM antes,
 *     no espera de balde); las víctimas en camino siguen y después se reactivan normalmente.
 *  2. Reactivar: si hay RAM, el Listo/Suspendido de mayor prioridad (empate: el que lleva
 *     más tiempo suspendido) pide volver; su RAM se reserva al pedirlo y una solicitud
 *     RESUME pasa por la cola del disco. Al terminar, libera sus bloques del swap y pasa a
 *     Listo. Se reactiva antes de admitir Nuevos porque ya estaban en el sistema.
 *  3. Admitir Nuevos como en el P1, sin tocar la RAM prometida en el paso 1.
 *  4. Suspender: si un Nuevo no cabe ni contando la RAM de las suspensiones que ya están en
 *     camino, se eligen Bloqueados (por E/S) de menor prioridad y, si empatan, el que lleva
 *     más tiempo bloqueado, hasta juntar la RAM que falta. Nunca el proceso cuya solicitud
 *     está atendiendo el disco, ni uno cuya E/S ya terminó en este ciclo (su interrupción
 *     está pendiente: la CPU la entrega al final del ciclo, después del mediano plazo). Si no alcanza (o no hay swap), no se suspende ninguno. Cada
 *     uno reserva sus bloques del swap, pasa a Bloqueado/Suspendido y una solicitud SUSPEND
 *     pasa por la cola del disco; su RAM se libera cuando el disco termina de escribirlo
 *     (escribe y después libera, como dice el enunciado) y queda para ese Nuevo (así no la
 *     toma otro y no hay thrashing). Un bloque del swap equivale a una unidad de RAM.
 *  - Un Bloqueado/Suspendido cuyo evento ocurre pasa a Listo/Suspendido.
 *  - Si un suspendido muere, se cancelan sus solicitudes y se liberan su swap y su RAM.
 *
 * Hilos: todo lo de arriba corre en el hilo de su computador. Desde otros hilos solo
 * se llaman submit, deliverWakeup, requestPolicy, requestQuantum y las consultas,
 * que pasan por un mutex.
 *
 * @author victor1102
 * @author NicollPinzon
 */
public class Kernel implements InterruptHandler {

    /** Qué hace la CPU en el ciclo actual (lo decide beforeCpu, lo aplica afterCpu). */
    private enum Action {
        IDLE, DISPATCH, USER_INSTRUCTION, KERNEL_INSTRUCTION, IO_REQUEST, REMOTE_REQUEST, DISK_REQUEST
    }

    /** Origen de las líneas del log que escribe el disco. */
    private static final String DISK_SOURCE = "Disco";
    /** Origen de las líneas del log del planificador a mediano plazo. */
    private static final String MEDIUM_TERM_SOURCE = "Mediano plazo";

    /** Un Nuevo por el que se suspendieron procesos: la RAM que liberen es para él. */
    private static final class Reservation {
        final Pcb newcomer;
        int pendingVictims;             // suspensiones que todavía se están escribiendo

        Reservation(Pcb newcomer, int pendingVictims) {
            this.newcomer = newcomer;
            this.pendingVictims = pendingVictims;
        }
    }

    /** Menor prioridad primero (número más alto); empate: el que lleva más tiempo bloqueado. */
    private static final ElementComparator<ProcessRuntime> VICTIM_ORDER = (a, b) -> {
        int byPriority = Integer.compare(b.pcb.getPriority(), a.pcb.getPriority());
        return byPriority != 0 ? byPriority : Long.compare(a.blockedCycle, b.blockedCycle);
    };

    /** Mayor prioridad primero (número más bajo); empate: el que lleva más tiempo suspendido. */
    private static final ElementComparator<ProcessRuntime> RESUME_ORDER = (a, b) -> {
        int byPriority = Integer.compare(a.pcb.getPriority(), b.pcb.getPriority());
        return byPriority != 0 ? byPriority : Long.compare(a.suspendedCycle, b.suspendedCycle);
    };

    private final int computerId;
    private final String source;
    private final Cpu cpu;
    private final IoDevice io;
    private final MemoryManager memory;
    private final Scheduler scheduler;
    private final EventLog log;
    private final ComputerMetrics metrics;
    private final SystemMetrics systemMetrics;      // puede ser null (pruebas)
    private final Interconnect network;
    private final Storage storage;

    // ---------- Estado del kernel (solo el hilo del computador) ----------
    private final LinkedList<ProcessRuntime> processes = new LinkedList<>();   // todos los vivos
    private ProcessRuntime running;
    private Action action = Action.IDLE;
    private long cycle;

    // ---------- Compartido con otros hilos (protegido por mutex) ----------
    private final Semaphore mutex = new Semaphore(1, true);
    private final LinkedList<Pcb> blocked = new LinkedList<>();
    private final LinkedList<Pcb> terminated = new LinkedList<>();
    private final LinkedList<Pcb> readySuspended = new LinkedList<>();
    private final LinkedList<Pcb> blockedSuspended = new LinkedList<>();
    private final LinkedList<Reservation> reservations = new LinkedList<>();   // solo el hilo del kernel
    private volatile long suspensions;
    private volatile long reactivations;
    private final LinkedList<Pcb> arrivals = new LinkedList<>();
    private final LinkedList<Wakeup> wakeups = new LinkedList<>();
    private PolicyType pendingPolicy;
    private int pendingQuantum;
    private DiskPolicyType pendingDiskPolicy;
    private volatile Pcb runningPcb;
    private volatile String activity = "Ociosa";

    /** Un despertar en camino: quién, desde qué semáforo y en qué ciclo se envió. */
    private record Wakeup(Pcb pcb, KernelSemaphore from, long sentCycle) {
    }

    public Kernel(int computerId, Cpu cpu, IoDevice io, MemoryManager memory, Scheduler scheduler,
            EventLog log, ComputerMetrics metrics, SystemMetrics systemMetrics, Interconnect network,
            Storage storage) {
        if (computerId < 1) {
            throw new IllegalArgumentException("El computador debe ser 1 o mayor");
        }
        if (cpu == null || io == null || memory == null || scheduler == null || log == null
                || metrics == null || network == null || storage == null) {
            throw new IllegalArgumentException("El kernel necesita CPU, E/S, memoria, planificador, log, métricas, red"
                    + " y almacenamiento");
        }
        if (memory.getComputerId() != computerId) {
            throw new IllegalArgumentException("La memoria es del computador " + memory.getComputerId()
                    + ", no del " + computerId);
        }
        this.computerId = computerId;
        this.source = "Computador " + computerId;
        this.cpu = cpu;
        this.io = io;
        this.memory = memory;
        this.scheduler = scheduler;
        this.log = log;
        this.metrics = metrics;
        this.systemMetrics = systemMetrics;
        this.network = network;
        this.storage = storage;
    }

    // =====================================================================
    // Desde otros hilos (GUI, clúster, otros computadores)
    // =====================================================================

    /**
     * Un proceso nuevo para este computador. Entra al sistema al inicio del próximo ciclo.
     *
     * @throws IllegalArgumentException si es de otro computador, no está en Nuevo
     *                                  o nunca cabría en la RAM (para que la GUI lo muestre)
     */
    public void submit(Pcb pcb) {
        if (pcb == null || pcb.getComputerId() != computerId || pcb.getState() != ProcessState.NEW) {
            throw new IllegalArgumentException("Solo se aceptan procesos nuevos del computador " + computerId);
        }
        if (pcb.getMemorySize() > memory.getProcessMemoryLimit()) {
            throw new IllegalArgumentException(pcb + " necesita " + pcb.getMemorySize() + " MB y el computador "
                    + computerId + " solo tiene " + memory.getProcessMemoryLimit() + " MB para procesos");
        }
        mutex.acquireUninterruptibly();
        try {
            arrivals.addLast(pcb);
        } finally {
            mutex.release();
        }
    }

    /** Un semSignal (de este u otro computador) despertó a uno de nuestros procesos. */
    public void deliverWakeup(Pcb pcb, KernelSemaphore from, long sentCycle) {
        mutex.acquireUninterruptibly();
        try {
            wakeups.addLast(new Wakeup(pcb, from, sentCycle));
        } finally {
            mutex.release();
        }
    }

    /** La GUI pide cambiar la política: se aplica al inicio del próximo ciclo. */
    public void requestPolicy(PolicyType policy) {
        if (policy == null) {
            throw new IllegalArgumentException("La política no puede ser null");
        }
        mutex.acquireUninterruptibly();
        try {
            pendingPolicy = policy;
        } finally {
            mutex.release();
        }
    }

    /** La GUI pide cambiar el quantum de Round Robin: se aplica al inicio del próximo ciclo. */
    public void requestQuantum(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor que 0");
        }
        mutex.acquireUninterruptibly();
        try {
            pendingQuantum = quantum;
        } finally {
            mutex.release();
        }
    }

    /** La GUI pide cambiar la política de disco: se aplica al inicio del próximo ciclo. */
    public void requestDiskPolicy(DiskPolicyType policy) {
        if (policy == null) {
            throw new IllegalArgumentException("La política de disco no puede ser null");
        }
        mutex.acquireUninterruptibly();
        try {
            pendingDiskPolicy = policy;
        } finally {
            mutex.release();
        }
    }

    // =====================================================================
    // 2. Antes de la CPU: planear el ciclo
    // =====================================================================

    public void beforeCpu(long cycle) {
        this.cycle = cycle;
        applyRequests();
        acceptArrivals();
        deliverWakeups();
        mediumTerm();
        preemptIfNeeded();
        planCpu();
        countReadyWaits();
    }

    private void applyRequests() {
        PolicyType policy;
        int quantum;
        DiskPolicyType diskPolicy;
        mutex.acquireUninterruptibly();
        try {
            policy = pendingPolicy;
            quantum = pendingQuantum;
            diskPolicy = pendingDiskPolicy;
            pendingPolicy = null;
            pendingQuantum = 0;
            pendingDiskPolicy = null;
        } finally {
            mutex.release();
        }
        if (policy != null && policy != scheduler.getPolicyType()) {
            scheduler.setPolicy(policy.newPolicy(), runningPcb);
            log.log(cycle, source, "cambia su política de planificación a " + policy.getLabel());
        }
        if (quantum > 0 && quantum != scheduler.getQuantum()) {
            scheduler.setQuantum(quantum);
            log.log(cycle, source, "cambia el quantum de Round Robin a " + quantum + " ciclos");
        }
        if (diskPolicy != null && diskPolicy != storage.diskScheduler().getPolicyType()) {
            storage.diskScheduler().setPolicy(diskPolicy);
            log.log(cycle, DISK_SOURCE, "cambia su política de planificación a " + diskPolicy.getLabel()
                    + " (se aplica desde la próxima solicitud)");
        }
    }

    private void acceptArrivals() {
        // De las llegadas a Nuevos DENTRO del mutex: así el proceso nunca está "entre" las
        // dos colas. El clúster revisa las llegadas (getArrivalsSnapshot, mismo mutex) y
        // MemoryManager.reserveBuffer revisa Nuevos: entre los dos siempre lo ven.
        LinkedList<Pcb> accepted = new LinkedList<>();
        LinkedList<String> rejections = new LinkedList<>();
        mutex.acquireUninterruptibly();
        try {
            while (!arrivals.isEmpty()) {
                Pcb pcb = arrivals.removeFirst();
                try {
                    memory.submit(pcb);
                    accepted.addLast(pcb);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    rejections.addLast("rechaza el proceso " + pcb + ": " + e.getMessage());
                }
            }
        } finally {
            mutex.release();
        }
        while (!accepted.isEmpty()) {
            Pcb pcb = accepted.removeFirst();
            processes.addLast(new ProcessRuntime(pcb));
            log.log(cycle, source, "llega el proceso " + pcb + " (" + pcb.getName() + ", "
                    + pcb.getType().getLabel() + ", " + pcb.getMemorySize() + " MB)");
        }
        while (!rejections.isEmpty()) {
            log.log(cycle, source, rejections.removeFirst());
        }
    }

    /** Despertares enviados en ciclos anteriores (los de este ciclo esperan al próximo). */
    private void deliverWakeups() {
        LinkedList<Wakeup> ready = new LinkedList<>();
        mutex.acquireUninterruptibly();
        try {
            int pending = wakeups.size();
            for (int i = 0; i < pending; i++) {
                Wakeup w = wakeups.removeFirst();
                if (w.sentCycle() < cycle) {
                    ready.addLast(w);
                } else {
                    wakeups.addLast(w);
                }
            }
        } finally {
            mutex.release();
        }
        while (!ready.isEmpty()) {
            Wakeup w = ready.removeFirst();
            ProcessRuntime rt = find(w.pcb());
            if (rt == null || w.pcb().getState() != ProcessState.BLOCKED
                    || w.pcb().getBlockReason() != BlockReason.SEMAPHORE) {
                continue;                                   // ya terminó: su turno se pasó al terminarlo
            }
            rt.waitingOn = null;
            rt.step = rt.step.next(rt.pcb.getType());       // su semWait se completó al despertar
            makeReady(rt, "despierta del semáforo " + w.from().describe());
        }
    }

    // =====================================================================
    // Planificador a mediano plazo (ver el diseño arriba)
    // =====================================================================

    private void mediumTerm() {
        dropFinishedReservations();
        admitReservedNewcomers();
        reactivateSuspended();
        admitNewProcesses();
        suspendForNewcomer();
    }

    /** RAM ya prometida a los Nuevos por los que se suspendieron procesos. */
    private int reservedRam() {
        int total = 0;
        for (int i = 0; i < reservations.size(); i++) {
            total += reservations.get(i).newcomer.getMemorySize();
        }
        return total;
    }

    /** RAM de los procesos que el disco está escribiendo en el swap (se liberará al terminar). */
    private int ramOnTheWay() {
        int total = 0;
        LinkedList<ProcessRuntime> all = processes.copy();
        while (!all.isEmpty()) {
            ProcessRuntime rt = all.removeFirst();
            if (rt.swappingOut) {
                total += rt.pcb.getMemorySize();
            }
        }
        return total;
    }

    private void dropFinishedReservations() {
        LinkedList<Reservation> all = reservations.copy();
        while (!all.isEmpty()) {
            Reservation r = all.removeFirst();
            if (r.newcomer.getState() != ProcessState.NEW) {
                reservations.remove(r);                     // terminó por deadline esperando
            }
        }
    }

    /**
     * Admite a cada Nuevo con reserva apenas cabe, sin esperar a que terminen de escribirse
     * sus víctimas: si la RAM ya está (por ejemplo, otro proceso terminó), esperar sería
     * dejarla ociosa. Respeta la RAM de las reservas que van antes que él.
     */
    private void admitReservedNewcomers() {
        LinkedList<Reservation> all = reservations.copy();
        int keepForEarlier = 0;
        while (!all.isEmpty()) {
            Reservation r = all.removeFirst();
            if (!memory.admitOne(r.newcomer, keepForEarlier)) {
                keepForEarlier += r.newcomer.getMemorySize();
            } else {
                reservations.remove(r);
                r.newcomer.markReady();
                scheduler.addReady(r.newcomer);
                log.log(cycle, source, "admite el proceso " + r.newcomer + " con la RAM que liberaron las suspensiones ("
                        + memory.getMemory().getFreeSize() + " MB libres)");
            }
        }
    }

    private void reactivateSuspended() {
        LinkedList<ProcessRuntime> candidates = new LinkedList<>();
        LinkedList<ProcessRuntime> all = processes.copy();
        while (!all.isEmpty()) {
            ProcessRuntime rt = all.removeFirst();
            if (rt.pcb.getState() == ProcessState.READY_SUSPENDED && rt.swapBlock >= 0 && !rt.swappingOut
                    && !rt.swappingIn) {
                candidates.insertSorted(rt, RESUME_ORDER);
            }
        }
        while (!candidates.isEmpty()) {
            ProcessRuntime rt = candidates.removeFirst();
            if (!memory.reserveForResume(rt.pcb, reservedRam())) {
                continue;                                   // no cabe todavía; quizá uno más pequeño sí
            }
            int size = rt.pcb.getMemorySize();
            rt.swappingIn = true;
            storage.diskScheduler().enqueue(new DiskRequest(rt.pcb.getId(), IoOperation.RESUME, DiskRequest.SWAP_TARGET,
                    rt.swapBlock, size, cycle));
            log.log(cycle, MEDIUM_TERM_SOURCE, "Se reactiva al proceso " + rt.pcb + ": hay RAM y el disco lo trae del swap ("
                    + size + " bloques desde el bloque " + rt.swapBlock + ")");
        }
    }

    /**
     * Si un Nuevo no cabe ni contando la RAM que viene en camino, suspende los Bloqueados
     * necesarios (o ninguno si no alcanza). Atiende a un solo Nuevo por ciclo.
     */
    private void suspendForNewcomer() {
        LinkedList<Pcb> waiting = memory.getNewProcesses();
        while (!waiting.isEmpty()) {
            Pcb newcomer = waiting.removeFirst();
            if (newcomer.getState() != ProcessState.NEW || hasReservation(newcomer)) {
                continue;
            }
            int future = memory.getMemory().getFreeSize() + ramOnTheWay() - reservedRam();
            int missing = newcomer.getMemorySize() - future;
            if (missing <= 0) {
                continue;                                   // entra en cuanto se libere lo que viene
            }
            LinkedList<ProcessRuntime> victims = chooseVictims(missing);
            if (victims != null) {
                suspend(victims, newcomer);
                return;
            }
        }
    }

    /** Los Bloqueados a suspender para juntar missing MB, o null si no alcanza (RAM o swap). */
    private LinkedList<ProcessRuntime> chooseVictims(int missing) {
        DiskRequest serving = storage.disk().getCurrentRequest();
        int servingPid = serving == null ? -1 : serving.getProcessId();
        LinkedList<ProcessRuntime> candidates = new LinkedList<>();
        LinkedList<ProcessRuntime> all = processes.copy();
        while (!all.isEmpty()) {
            ProcessRuntime rt = all.removeFirst();
            if (rt.pcb.getState() == ProcessState.BLOCKED && rt.pcb.getBlockReason() == BlockReason.IO
                    && rt.pcb.getId() != servingPid && !rt.swappingIn && !rt.swappingOut && memory.isResident(rt.pcb)
                    && !cpu.hasPendingInterruptFor(rt.pcb.getId())) {   // su E/S ya terminó: no espera nada
                candidates.insertSorted(rt, VICTIM_ORDER);
            }
        }
        int freeSwap = storage.disk().getBlockMap().getFreeSwapBlocks();
        LinkedList<ProcessRuntime> chosen = new LinkedList<>();
        int gathered = 0;
        int swapUsed = 0;
        while (!candidates.isEmpty() && gathered < missing) {
            ProcessRuntime rt = candidates.removeFirst();
            int size = rt.pcb.getMemorySize();
            if (swapUsed + size <= freeSwap) {              // un bloque del swap por unidad de RAM
                chosen.addLast(rt);
                gathered += size;
                swapUsed += size;
            }
        }
        return gathered >= missing ? chosen : null;
    }

    private void suspend(LinkedList<ProcessRuntime> victims, Pcb newcomer) {
        reservations.addLast(new Reservation(newcomer, victims.size()));
        while (!victims.isEmpty()) {
            ProcessRuntime rt = victims.removeFirst();
            int size = rt.pcb.getMemorySize();
            rt.swapBlock = storage.disk().getBlockMap().allocateSwap(size, rt.pcb.creatorLabel());
            rt.swappingOut = true;
            rt.suspendedCycle = cycle;
            rt.suspendedFor = newcomer;
            removeBlocked(rt.pcb);
            rt.pcb.markSuspended();
            mutex.acquireUninterruptibly();
            try {
                blockedSuspended.addLast(rt.pcb);
            } finally {
                mutex.release();
            }
            storage.diskScheduler().enqueue(new DiskRequest(rt.pcb.getId(), IoOperation.SUSPEND, DiskRequest.SWAP_TARGET,
                    rt.swapBlock, size, cycle));
            log.log(cycle, MEDIUM_TERM_SOURCE, "Se suspende al proceso " + rt.pcb + " para admitir a " + newcomer
                    + ": el disco lo escribe en el swap (" + size + " bloques desde el bloque " + rt.swapBlock + ")");
        }
    }

    private boolean hasReservation(Pcb pcb) {
        for (int i = 0; i < reservations.size(); i++) {
            if (reservations.get(i).newcomer == pcb) {
                return true;
            }
        }
        return false;
    }

    /** Una suspensión hecha para newcomer terminó de escribirse (o se canceló). */
    private void victimDone(Pcb newcomer) {
        for (int i = 0; i < reservations.size(); i++) {
            Reservation r = reservations.get(i);
            if (r.newcomer == newcomer && r.pendingVictims > 0) {
                r.pendingVictims--;
                return;
            }
        }
    }

    /** El disco terminó de escribir (SUSPEND) o de traer (RESUME) a un proceso. */
    private void swapCompleted(ProcessRuntime rt, DiskRequest done, String policy) {
        log.log(cycle, DISK_SOURCE, "atiende la solicitud de " + (rt == null ? "P" + done.getProcessId() : rt.pcb)
                + " en el bloque " + done.getTargetBlock() + " (" + policy + ")");
        if (rt == null) {
            return;                                          // ya terminó: terminate liberó todo
        }
        int size = rt.pcb.getMemorySize();
        if (done.getOperation() == IoOperation.SUSPEND) {
            rt.swappingOut = false;
            memory.release(rt.pcb);
            victimDone(rt.suspendedFor);
            suspensions++;                                  // se cuenta al completarse, igual que las reactivaciones
            log.log(cycle, MEDIUM_TERM_SOURCE, "El proceso " + rt.pcb + " ya está en el swap: se liberan " + size
                    + " MB de RAM para " + rt.suspendedFor);
            return;
        }
        rt.swappingIn = false;
        storage.disk().getBlockMap().free(rt.swapBlock);
        rt.swapBlock = -1;
        removeSuspended(rt.pcb);
        rt.pcb.markReady();
        scheduler.addReady(rt.pcb);
        reactivations++;
        log.log(cycle, MEDIUM_TERM_SOURCE, "El proceso " + rt.pcb + " vuelve a la RAM (libera " + size
                + " bloques del swap) y pasa a Listo");
    }

    /** Ocurrió el evento de un Bloqueado/Suspendido: sigue en el swap, pero ya está listo. */
    private void eventWhileSuspended(ProcessRuntime rt, String why) {
        mutex.acquireUninterruptibly();
        try {
            blockedSuspended.remove(rt.pcb);
            readySuspended.addLast(rt.pcb);
        } finally {
            mutex.release();
        }
        rt.pcb.markReadySuspended();
        log.log(cycle, source, "el proceso " + rt.pcb + " " + why + " estando suspendido y pasa a Listo/Suspendido");
    }

    private void removeSuspended(Pcb pcb) {
        mutex.acquireUninterruptibly();
        try {
            readySuspended.remove(pcb);
            blockedSuspended.remove(pcb);
        } finally {
            mutex.release();
        }
    }

    private void admitNewProcesses() {
        LinkedList<Pcb> admitted = memory.admit(reservedRam());
        while (!admitted.isEmpty()) {
            Pcb pcb = admitted.removeFirst();
            pcb.markReady();
            scheduler.addReady(pcb);
            log.log(cycle, source, "admite el proceso " + pcb + " (hay RAM: " + memory.getMemory().getFreeSize()
                    + " MB libres)");
        }
    }

    private void preemptIfNeeded() {
        if (running == null || !scheduler.shouldPreempt(running.pcb)) {
            return;
        }
        Pcb pcb = running.pcb;
        pcb.setProgramCounter(cpu.saveContext());
        cpu.unload();
        pcb.markReady();
        scheduler.addReady(pcb);
        setRunning(null);
        log.log(cycle, source, "expropia al proceso " + pcb + " (" + scheduler.getPolicyType().getLabel() + ")");
    }

    /** Decide qué hace la CPU en este ciclo. */
    private void planCpu() {
        if (running == null) {
            Pcb next = scheduler.selectNext();
            if (next == null) {
                cpu.unload();
                action = Action.IDLE;
                activity = "Ociosa";
                return;
            }
            ProcessRuntime rt = find(next);
            next.markRunning(cycle);
            cpu.loadContext(next.getProgramCounter(), CpuMode.KERNEL);
            cpu.scheduleKernelWork();                        // despachar cuesta 1 ciclo de SO
            setRunning(rt);
            action = Action.DISPATCH;
            activity = "SO: despacha a " + next;
            log.log(cycle, source, "selecciona el proceso " + next);
            return;
        }
        ProcessRuntime rt = running;
        ExecutionStep step = rt.step;
        if (step.isBufferOperation() && needsLatency(rt)) {
            cpu.scheduleKernelWork();
            action = Action.REMOTE_REQUEST;
            activity = "SO: " + rt.pcb + " envía " + step.getLabel() + " a " + rt.buffer.getName() + " (remoto)";
        } else if (step == ExecutionStep.IO_REQUEST) {
            cpu.scheduleKernelWork();
            action = Action.IO_REQUEST;
            activity = "SO: registra la E/S de " + rt.pcb;
        } else if (step == ExecutionStep.DISK_REQUEST) {
            cpu.setMode(CpuMode.KERNEL);                     // 1 instrucción del proceso, en modo SO
            action = Action.DISK_REQUEST;
            activity = rt.pcb + ": pide al disco " + rt.pcb.getFileOperation();
        } else if (step == ExecutionStep.USER) {
            cpu.setMode(CpuMode.USER);
            action = Action.USER_INSTRUCTION;
            activity = rt.pcb + ": " + step.getLabel();
        } else {
            cpu.setMode(CpuMode.KERNEL);
            action = Action.KERNEL_INSTRUCTION;
            activity = rt.pcb + ": " + step.getLabel() + " en " + rt.buffer.getName();
        }
    }

    private boolean needsLatency(ProcessRuntime rt) {
        BoundedBuffer buffer = bufferOf(rt);
        return buffer.isRemoteFor(rt.pcb) && !rt.latencyPaid && network.getNetworkLatency() > 0;
    }

    // =====================================================================
    // 3. Interrupciones (las entrega la CPU al final de su ciclo)
    // =====================================================================

    @Override
    public void handleInterrupt(Interrupt interrupt) {
        if (interrupt.type() == InterruptType.DISK_COMPLETION) {
            diskCompleted();
            return;
        }
        if (interrupt.type() != InterruptType.IO_COMPLETION) {
            log.log(cycle, source, "ignora la interrupción " + interrupt.type().getLabel());
            return;
        }
        ProcessRuntime rt = findById(interrupt.processId());
        if (rt != null && rt.pcb.getState() == ProcessState.BLOCKED_SUSPENDED
                && rt.pcb.getBlockReason() == BlockReason.IO) {
            eventWhileSuspended(rt, "termina su E/S");
            return;
        }
        if (rt == null || rt.pcb.getState() != ProcessState.BLOCKED || rt.pcb.getBlockReason() != BlockReason.IO) {
            log.log(cycle, source, "ignora el fin de E/S de P" + interrupt.processId() + " (ya no la espera)");
            return;
        }
        makeReady(rt, "termina su E/S");
    }

    /**
     * El disco terminó una solicitud: se aplica la operación en el sistema de archivos
     * y el proceso vuelve a Listo (o termina, si ya no le quedan instrucciones).
     * Hay una interrupción por solicitud terminada, así que se toma una sola.
     */
    private void diskCompleted() {
        SeekPlan plan = storage.disk().pollCompleted();
        if (plan == null) {
            return;
        }
        DiskRequest done = plan.getRequest();
        // La política que ELIGIÓ la solicitud, no la vigente ahora (pueden haberla cambiado)
        String policy = plan.getChosenBy() != null ? plan.getChosenBy()
                : storage.diskScheduler().getPolicyType().getLabel();
        ProcessRuntime rt = findById(done.getProcessId());
        if (!done.getOperation().isFileOperation()) {
            swapCompleted(rt, done, policy);                // SUSPEND o RESUME
            return;
        }
        boolean waiting = rt != null && (rt.pcb.getState() == ProcessState.BLOCKED
                || rt.pcb.getState() == ProcessState.BLOCKED_SUSPENDED);
        if (!waiting || !rt.pcb.getType().isFileIo() || rt.pcb.getBlockReason() != BlockReason.IO) {
            log.log(cycle, DISK_SOURCE, "termina la solicitud de P" + done.getProcessId()
                    + ", pero el proceso ya no la espera");
            return;
        }
        log.log(cycle, DISK_SOURCE, "atiende la solicitud de " + rt.pcb + " en el bloque " + done.getTargetBlock()
                + " (" + policy + ")");
        // Se aplica con "P5 (Proc_95)" como creador: el nombre se puede repetir, el PID no
        FsResult result = storage.fileSystem().apply(rt.pcb.getSession(),
                rt.pcb.getFileOperation().withCreator(rt.pcb.creatorLabel()));
        if (!result.isOk()) {
            terminate(rt, false, lowerFirst(result.getMessage()));
            return;
        }
        log.log(cycle, source, "el proceso " + rt.pcb + ": " + result.getMessage());
        if (rt.pcb.isFinished()) {
            terminate(rt, false, null);                     // su única instrucción era la operación
        } else if (rt.pcb.getState() == ProcessState.BLOCKED_SUSPENDED) {
            eventWhileSuspended(rt, "termina su E/S de disco");
        } else {
            makeReady(rt, "termina su E/S de disco");
        }
    }

    // =====================================================================
    // 4. Después de la CPU: aplicar el efecto del ciclo
    // =====================================================================

    public void afterCpu(long cycle) {
        this.cycle = cycle;
        countDownNetwork();
        applyAction();
        tickDeadlines();
        countSemaphoreWaits();
    }

    /** Latencias de red: las que llegan a 0 vuelven a Listo (ya pagaron su acceso). */
    private void countDownNetwork() {
        LinkedList<ProcessRuntime> all = processes.copy();
        while (!all.isEmpty()) {
            ProcessRuntime rt = all.removeFirst();
            if (rt.networkLeft > 0 && --rt.networkLeft == 0) {
                rt.latencyPaid = true;
                makeReady(rt, "recibe la respuesta remota de " + rt.buffer.getName());
            }
        }
    }

    private void applyAction() {
        ProcessRuntime rt = running;
        switch (action) {
            case IDLE, DISPATCH -> {
                return;                                      // el despachado ejecuta desde el próximo ciclo
            }
            case REMOTE_REQUEST -> {
                rt.networkLeft = network.getNetworkLatency();
                block(rt, BlockReason.NETWORK, rt.step.getLabel() + " en " + rt.buffer.getName()
                        + " (computador " + rt.buffer.getHostComputerId() + ")");
                log.log(cycle, source, "el proceso " + rt.pcb + " accede al buffer " + rt.buffer.getName()
                        + " de forma remota (" + rt.step.getLabel() + ", " + rt.networkLeft + " ciclos de red)");
                return;
            }
            case IO_REQUEST -> {
                io.request(rt.pcb.getId(), rt.pcb.getIoDuration());
                rt.sinceIo = 0;
                rt.step = ExecutionStep.USER;
                block(rt, BlockReason.IO, rt.pcb.getIoDuration() + " ciclos");
                log.log(cycle, source, "el proceso " + rt.pcb + " pide E/S (" + rt.pcb.getIoDuration() + " ciclos)");
                return;
            }
            case DISK_REQUEST -> {
                rt.pcb.instructionExecuted();
                requestDisk(rt);
                return;
            }
            case USER_INSTRUCTION -> {
                rt.pcb.instructionExecuted();
                afterUserInstruction(rt);
            }
            case KERNEL_INSTRUCTION -> {
                rt.pcb.instructionExecuted();
                if (!executeBufferOperation(rt)) {
                    return;                                  // se bloqueó en un semáforo
                }
            }
        }
        if (running == rt && rt.pcb.isFinished()) {
            terminate(rt, false, null);
        }
    }

    /**
     * El proceso de E/S pide su operación. El sistema de archivos la revisa con el
     * usuario del proceso: si se puede, la solicitud va a la cola del disco y el
     * proceso queda Bloqueado por E/S; si no, el proceso termina con el motivo.
     */
    private void requestDisk(ProcessRuntime rt) {
        FsOperation op = rt.pcb.getFileOperation();
        FsResult check = storage.fileSystem().check(rt.pcb.getSession(), op);
        if (!check.isOk()) {
            terminate(rt, false, "no se pudo " + op + ": " + check.getMessage());
            return;
        }
        String target = op.getOperation() == IoOperation.CREATE
                ? op.getCreatedPath() : op.getPath();
        DiskRequest request = new DiskRequest(rt.pcb.getId(), op.getOperation(), target, check.getTargetBlock(),
                check.getBlockCount(), cycle);
        storage.diskScheduler().enqueue(request);
        rt.step = ExecutionStep.USER;                        // al volver, ejecuta sus instrucciones de usuario
        block(rt, BlockReason.IO, "disco, " + op);
        // Sin paréntesis propios: el texto de crear ya trae "(3 bloques)"
        log.log(cycle, source, "el proceso " + rt.pcb + " pide al disco " + op + "; va al bloque "
                + check.getTargetBlock() + " y abarca " + check.getBlockCount()
                + (check.getBlockCount() == 1 ? " bloque" : " bloques") + "; queda bloqueado por E/S");
    }

    private void afterUserInstruction(ProcessRuntime rt) {
        switch (rt.pcb.getType()) {
            case CPU_BOUND, FILE_IO -> {
                // solo calcula (el proceso de E/S ya hizo su operación)
            }
            case IO_BOUND -> {
                rt.sinceIo++;
                if (rt.sinceIo >= rt.pcb.getIoInterval() && rt.pcb.getRemainingInstructions() > 0) {
                    rt.step = ExecutionStep.IO_REQUEST;      // la próxima vez pide E/S
                }
            }
            case PRODUCER -> {
                if (--rt.userLeft <= 0) {
                    rt.step = ExecutionStep.WAIT_EMPTY;      // ya produjo: a dejarlo en el buffer
                }
            }
            case CONSUMER -> {
                if (--rt.userLeft <= 0) {
                    rt.pcb.itemProcessed();                  // terminó de consumir un elemento
                    rt.step = ExecutionStep.WAIT_FULL;
                }
            }
        }
    }

    /**
     * Ejecuta la operación del productor-consumidor que toca (1 instrucción en modo SO).
     *
     * @return false si el proceso se bloqueó en un semáforo
     */
    private boolean executeBufferOperation(ProcessRuntime rt) {
        BoundedBuffer buffer = bufferOf(rt);
        ExecutionStep step = rt.step;
        rt.latencyPaid = false;                              // la próxima operación remota paga de nuevo
        String remote = buffer.isRemoteFor(rt.pcb) ? " (remoto)" : "";
        if (step.isWait()) {
            KernelSemaphore sem = semaphoreOf(buffer, step);
            if (!sem.semWait(rt.pcb)) {
                rt.waitingOn = sem;
                block(rt, BlockReason.SEMAPHORE, sem.describe());
                log.log(cycle, source, "el proceso " + rt.pcb + " se bloquea en el semáforo '" + sem.getName()
                        + "' del buffer " + buffer.getName() + remote);
                return false;
            }
        } else if (step == ExecutionStep.INSERT) {
            int item = buffer.insert(rt.pcb);
            if (systemMetrics != null) {
                systemMetrics.itemProduced(buffer.getName());
            }
            log.log(cycle, source, "el proceso " + rt.pcb + " inserta el elemento " + item + " en " + buffer.getName()
                    + remote + " (" + buffer.getItemCount() + "/" + buffer.getCapacity() + ")");
        } else if (step == ExecutionStep.EXTRACT) {
            // La métrica del buffer cuenta el elemento como consumido al EXTRAERLO; el
            // consumidor lo suma a sus elementos después de consumirlo (afterUserInstruction).
            // Si muere mientras consume, los dos números difieren en 1: miden cosas distintas.
            int item = buffer.extract(rt.pcb);
            if (systemMetrics != null) {
                systemMetrics.itemConsumed(buffer.getName());
            }
            log.log(cycle, source, "el proceso " + rt.pcb + " extrae el elemento " + item + " de " + buffer.getName()
                    + remote + " (" + buffer.getItemCount() + "/" + buffer.getCapacity() + ")");
        } else if (step.isSignal()) {
            KernelSemaphore sem = semaphoreOf(buffer, step);
            sendWakeup(sem.semSignal(rt.pcb), sem);
        }
        rt.step = step.next(rt.pcb.getType());
        if (step == ExecutionStep.SIGNAL_FULL) {
            rt.pcb.itemProcessed();                          // el productor completó un elemento
            rt.userLeft = rt.pcb.getOperationInterval();
        } else if (step == ExecutionStep.SIGNAL_EMPTY) {
            rt.userLeft = rt.pcb.getOperationInterval();     // ahora consume lo que extrajo
        }
        return true;
    }

    /** El deadline baja para TODOS los vivos; al llegar a 0 el proceso se termina. */
    private void tickDeadlines() {
        LinkedList<ProcessRuntime> all = processes.copy();
        while (!all.isEmpty()) {
            ProcessRuntime rt = all.removeFirst();
            if (rt.pcb.tickDeadline()) {
                terminate(rt, true, null);
            }
        }
    }

    /** Métrica del productor-consumidor: tiempo bloqueado en semáforos. */
    private void countSemaphoreWaits() {
        LinkedList<ProcessRuntime> all = processes.copy();
        while (!all.isEmpty()) {
            ProcessRuntime rt = all.removeFirst();
            if (rt.pcb.getState() == ProcessState.BLOCKED && rt.pcb.getBlockReason() == BlockReason.SEMAPHORE) {
                rt.pcb.addBlockedOnSemaphoreCycle();
            }
        }
    }

    /**
     * Métrica de equidad: un ciclo en Listos por cada proceso que QUERÍA la CPU en este ciclo
     * y no la obtuvo. Se cuenta justo después de planCpu, cuando ya se decidió quién usa la
     * CPU: el que está en Listos en ese momento esperó todo el ciclo. No se cuenta en afterCpu
     * porque ahí también estarían en Listos los que acaban de volver de E/S o de la red en ese
     * mismo ciclo (no esperaron la CPU): eso castigaría a los I/O bound.
     */
    private void countReadyWaits() {
        LinkedList<ProcessRuntime> all = processes.copy();
        while (!all.isEmpty()) {
            ProcessRuntime rt = all.removeFirst();
            if (rt.pcb.getState() == ProcessState.READY) {
                rt.pcb.addReadyCycle();
            }
        }
    }

    // =====================================================================
    // Terminación (con limpieza tipo SEM_UNDO)
    // =====================================================================

    /**
     * @param failure motivo si su operación de E/S no se pudo hacer (el proceso queda como
     *                fallido y las métricas no lo cuentan a tiempo), o null si terminó su
     *                trabajo o le venció el deadline
     */
    private void terminate(ProcessRuntime rt, boolean deadlineExpired, String failure) {
        Pcb pcb = rt.pcb;
        int removedFromQueue = 0;
        boolean aborted = false;
        // Solicitudes al disco que hay que cancelar: su operación de archivo o su ida/vuelta al swap
        boolean waitsForFile = pcb.getType().isFileIo() && pcb.getBlockReason() == BlockReason.IO
                && (pcb.getState() == ProcessState.BLOCKED || pcb.getState() == ProcessState.BLOCKED_SUSPENDED);
        if (waitsForFile || rt.swappingOut || rt.swappingIn) {
            // Se cancela en los dos sin mirar lo que devuelven: si el disco estaba
            // pidiendo la siguiente, Disk.cancel da false pero igual la descarta.
            removedFromQueue = storage.diskScheduler().cancel(pcb.getId());
            aborted = storage.disk().cancel(pcb.getId());
        }
        switch (pcb.getState()) {
            case NEW -> memory.removeFromNew(pcb);
            case READY -> scheduler.remove(pcb);
            case RUNNING -> {
                pcb.setProgramCounter(cpu.saveContext());
                cpu.unload();
                setRunning(null);
            }
            case BLOCKED -> {
                removeBlocked(pcb);
                switch (pcb.getBlockReason()) {
                    case IO -> {
                        if (!pcb.getType().isFileIo()) {
                            io.cancel(pcb.getId());
                        }
                    }
                    case NETWORK -> rt.networkLeft = 0;
                    case SEMAPHORE -> {
                        if (!rt.waitingOn.removeWaiting(pcb)) {
                            rt.step = rt.step.next(pcb.getType());   // ya lo habían despertado: pasó el semWait
                        }
                    }
                }
            }
            case READY_SUSPENDED, BLOCKED_SUSPENDED -> {
                removeSuspended(pcb);
                if (pcb.getState() == ProcessState.BLOCKED_SUSPENDED && pcb.getBlockReason() == BlockReason.IO
                        && !pcb.getType().isFileIo()) {
                    io.cancel(pcb.getId());
                }
            }
            case TERMINATED -> {
                return;
            }
        }
        int freedSwap = 0;
        if (rt.swapBlock >= 0) {
            freedSwap = storage.disk().getBlockMap().free(rt.swapBlock);
            rt.swapBlock = -1;
        }
        if (rt.swappingOut) {
            victimDone(rt.suspendedFor);                    // su RAM se libera aquí mismo
        }
        rt.swappingOut = false;
        rt.swappingIn = false;
        if (pcb.getType().usesBuffer()) {
            releaseBufferHoldings(rt);
        }
        if (failure != null) {
            pcb.markFailed(cycle, failure);
        } else {
            pcb.markTerminated(cycle, deadlineExpired);
        }
        if (memory.isResident(pcb)) {
            memory.release(pcb);                            // un suspendido ya en el swap no tiene RAM
        }
        processes.remove(rt);
        mutex.acquireUninterruptibly();
        try {
            terminated.addLast(pcb);
        } finally {
            mutex.release();
        }
        metrics.processTerminated(pcb);
        String text = failure != null ? "se termina: " + failure
                : deadlineExpired ? "se termina: venció su deadline" : "terminó su ejecución";
        log.log(cycle, source, "el proceso " + pcb + " " + text);
        String cause = deadlineExpired ? "venció su deadline" : "el proceso terminó";
        if (aborted) {
            log.log(cycle, DISK_SOURCE, "aborta la solicitud de " + pcb + " que estaba atendiendo (" + cause + ")");
        } else if (removedFromQueue > 0) {
            log.log(cycle, DISK_SOURCE, "saca de su cola la solicitud de " + pcb + " (" + cause + ")");
        }
        if (freedSwap > 0) {
            log.log(cycle, MEDIUM_TERM_SOURCE, "se liberan los " + freedSwap + " bloques del swap de " + pcb);
        }
    }

    /** "No se pudo leer /x" → "no se pudo leer /x" (para continuar una frase del log). */
    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Devuelve lo que el productor/consumidor dejó tomado según el paso en que iba,
     * para que los demás no se queden esperando para siempre.
     */
    private void releaseBufferHoldings(ProcessRuntime rt) {
        BoundedBuffer buffer = bufferOf(rt);
        Pcb pcb = rt.pcb;
        ExecutionStep step = rt.step;
        boolean passedFirstWait = step == ExecutionStep.WAIT_MUTEX || step == ExecutionStep.INSERT
                || step == ExecutionStep.EXTRACT;
        boolean doneButNotAnnounced = step == ExecutionStep.SIGNAL_MUTEX || step == ExecutionStep.SIGNAL_FULL
                || step == ExecutionStep.SIGNAL_EMPTY;
        if (buffer.getMutex().getHolder() == pcb) {
            sendWakeup(buffer.getMutex().semSignal(pcb), buffer.getMutex());
        }
        if (passedFirstWait) {
            // reservó un hueco (productor) o un elemento (consumidor) y no lo usó: se devuelve
            KernelSemaphore first = rt.isProducer() ? buffer.getEmpty() : buffer.getFull();
            sendWakeup(first.semSignal(pcb), first);
        } else if (doneButNotAnnounced) {
            // ya insertó/extrajo pero no avisó: se avisa en su nombre
            KernelSemaphore last = rt.isProducer() ? buffer.getFull() : buffer.getEmpty();
            sendWakeup(last.semSignal(pcb), last);
        }
    }

    // =====================================================================
    // Ayudas
    // =====================================================================

    private void makeReady(ProcessRuntime rt, String why) {
        removeBlocked(rt.pcb);
        rt.pcb.markReady();
        scheduler.addReady(rt.pcb);
        log.log(cycle, source, "el proceso " + rt.pcb + " " + why + " y pasa a Listo");
    }

    /** Saca al proceso de la CPU y lo deja Bloqueado con su motivo. */
    private void block(ProcessRuntime rt, BlockReason reason, String detail) {
        rt.blockedCycle = cycle;
        rt.pcb.setProgramCounter(cpu.saveContext());
        cpu.unload();
        rt.pcb.markBlocked(reason, detail);
        setRunning(null);
        mutex.acquireUninterruptibly();
        try {
            blocked.addLast(rt.pcb);
        } finally {
            mutex.release();
        }
    }

    private void removeBlocked(Pcb pcb) {
        mutex.acquireUninterruptibly();
        try {
            blocked.remove(pcb);
        } finally {
            mutex.release();
        }
    }

    private void sendWakeup(Pcb woken, KernelSemaphore from) {
        if (woken != null) {
            network.deliverWakeup(woken, from, cycle);
        }
    }

    private void setRunning(ProcessRuntime rt) {
        running = rt;
        runningPcb = rt == null ? null : rt.pcb;
    }

    private BoundedBuffer bufferOf(ProcessRuntime rt) {
        if (rt.buffer == null) {
            rt.buffer = network.findBuffer(rt.pcb.getBufferName());
            if (rt.buffer == null) {
                throw new IllegalStateException("No existe el buffer " + rt.pcb.getBufferName() + " de " + rt.pcb);
            }
        }
        return rt.buffer;
    }

    private static KernelSemaphore semaphoreOf(BoundedBuffer buffer, ExecutionStep step) {
        return switch (step) {
            case WAIT_EMPTY, SIGNAL_EMPTY -> buffer.getEmpty();
            case WAIT_FULL, SIGNAL_FULL -> buffer.getFull();
            case WAIT_MUTEX, SIGNAL_MUTEX -> buffer.getMutex();
            default -> throw new IllegalArgumentException(step + " no es una operación de semáforo");
        };
    }

    private ProcessRuntime find(Pcb pcb) {
        LinkedList<ProcessRuntime> all = processes.copy();
        while (!all.isEmpty()) {
            ProcessRuntime rt = all.removeFirst();
            if (rt.pcb == pcb) {
                return rt;
            }
        }
        return null;
    }

    private ProcessRuntime findById(int id) {
        LinkedList<ProcessRuntime> all = processes.copy();
        while (!all.isEmpty()) {
            ProcessRuntime rt = all.removeFirst();
            if (rt.pcb.getId() == id) {
                return rt;
            }
        }
        return null;
    }

    // =====================================================================
    // Consultas (GUI): siempre copias
    // =====================================================================

    public int getComputerId() {
        return computerId;
    }

    /** Proceso en la CPU (también durante el ciclo en que se despacha), o null. */
    public Pcb getRunning() {
        return runningPcb;
    }

    /** Qué hace la CPU en este ciclo, en palabras (ej. "SO: despacha a P3-H1"). */
    public String getActivity() {
        return activity;
    }

    public LinkedList<Pcb> getNewSnapshot() {
        return memory.getNewProcesses();
    }

    /**
     * Procesos enviados con submit() que todavía no entran al sistema (lo hacen al inicio
     * del próximo ciclo). Con la simulación en pausa pueden quedarse aquí un buen rato:
     * el clúster los revisa antes de reservar la RAM de un buffer nuevo.
     */
    public LinkedList<Pcb> getArrivalsSnapshot() {
        mutex.acquireUninterruptibly();
        try {
            return arrivals.copy();
        } finally {
            mutex.release();
        }
    }

    public LinkedList<Pcb> getReadySnapshot() {
        return scheduler.getReadySnapshot();
    }

    public LinkedList<Pcb> getBlockedSnapshot() {
        mutex.acquireUninterruptibly();
        try {
            return blocked.copy();
        } finally {
            mutex.release();
        }
    }

    /** Cola Listo/Suspendido: en el swap, ya listos, esperan RAM para volver. */
    public LinkedList<Pcb> getReadySuspendedSnapshot() {
        mutex.acquireUninterruptibly();
        try {
            return readySuspended.copy();
        } finally {
            mutex.release();
        }
    }

    /** Cola Bloqueado/Suspendido: en el swap y todavía esperando su evento. */
    public LinkedList<Pcb> getBlockedSuspendedSnapshot() {
        mutex.acquireUninterruptibly();
        try {
            return blockedSuspended.copy();
        } finally {
            mutex.release();
        }
    }

    /**
     * Suspensiones completadas: procesos que el disco terminó de escribir en el swap
     * (métrica del enunciado). Se cuentan al completarse, igual que las reactivaciones:
     * una suspensión cancelada porque la víctima murió antes no cuenta.
     */
    public long getSuspensions() {
        return suspensions;
    }

    /** Reactivaciones terminadas (el proceso volvió a la RAM). */
    public long getReactivations() {
        return reactivations;
    }

    public LinkedList<Pcb> getTerminatedSnapshot() {
        mutex.acquireUninterruptibly();
        try {
            return terminated.copy();
        } finally {
            mutex.release();
        }
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public MemoryManager getMemoryManager() {
        return memory;
    }

    public Storage getStorage() {
        return storage;
    }
}
