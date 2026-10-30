package com.unimet.avilaos.os;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.events.EventLog;
import com.unimet.avilaos.hardware.Cpu;
import com.unimet.avilaos.hardware.CpuMode;
import com.unimet.avilaos.hardware.Interrupt;
import com.unimet.avilaos.hardware.InterruptHandler;
import com.unimet.avilaos.hardware.InterruptType;
import com.unimet.avilaos.hardware.IoDevice;
import com.unimet.avilaos.metrics.ComputerMetrics;
import com.unimet.avilaos.metrics.SystemMetrics;
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
 *   1. io.onTick(c)            el disco avanza
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
 * Hilos: todo lo de arriba corre en el hilo de su computador. Desde otros hilos solo
 * se llaman submit, deliverWakeup, requestPolicy, requestQuantum y las consultas,
 * que pasan por un mutex.
 *
 * @author victor1102
 */
public class Kernel implements InterruptHandler {

    /** Qué hace la CPU en el ciclo actual (lo decide beforeCpu, lo aplica afterCpu). */
    private enum Action {
        IDLE, DISPATCH, USER_INSTRUCTION, KERNEL_INSTRUCTION, IO_REQUEST, REMOTE_REQUEST
    }

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

    // ---------- Estado del kernel (solo el hilo del computador) ----------
    private final LinkedList<ProcessRuntime> processes = new LinkedList<>();   // todos los vivos
    private ProcessRuntime running;
    private Action action = Action.IDLE;
    private long cycle;

    // ---------- Compartido con otros hilos (protegido por mutex) ----------
    private final Semaphore mutex = new Semaphore(1, true);
    private final LinkedList<Pcb> blocked = new LinkedList<>();
    private final LinkedList<Pcb> terminated = new LinkedList<>();
    private final LinkedList<Pcb> arrivals = new LinkedList<>();
    private final LinkedList<Wakeup> wakeups = new LinkedList<>();
    private PolicyType pendingPolicy;
    private int pendingQuantum;
    private volatile Pcb runningPcb;
    private volatile String activity = "Ociosa";

    /** Un despertar en camino: quién, desde qué semáforo y en qué ciclo se envió. */
    private record Wakeup(Pcb pcb, KernelSemaphore from, long sentCycle) {
    }

    public Kernel(int computerId, Cpu cpu, IoDevice io, MemoryManager memory, Scheduler scheduler,
            EventLog log, ComputerMetrics metrics, SystemMetrics systemMetrics, Interconnect network) {
        if (computerId < 1) {
            throw new IllegalArgumentException("El computador debe ser 1 o mayor");
        }
        if (cpu == null || io == null || memory == null || scheduler == null || log == null
                || metrics == null || network == null) {
            throw new IllegalArgumentException("El kernel necesita CPU, E/S, memoria, planificador, log, métricas y red");
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

    // =====================================================================
    // 2. Antes de la CPU: planear el ciclo
    // =====================================================================

    public void beforeCpu(long cycle) {
        this.cycle = cycle;
        applyRequests();
        acceptArrivals();
        deliverWakeups();
        admitNewProcesses();
        preemptIfNeeded();
        planCpu();
        countReadyWaits();
    }

    private void applyRequests() {
        PolicyType policy;
        int quantum;
        mutex.acquireUninterruptibly();
        try {
            policy = pendingPolicy;
            quantum = pendingQuantum;
            pendingPolicy = null;
            pendingQuantum = 0;
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
    }

    private void acceptArrivals() {
        LinkedList<Pcb> batch = drain(arrivals);
        while (!batch.isEmpty()) {
            Pcb pcb = batch.removeFirst();
            try {
                memory.submit(pcb);
                processes.addLast(new ProcessRuntime(pcb));
                log.log(cycle, source, "llega el proceso " + pcb + " (" + pcb.getName() + ", "
                        + pcb.getType().getLabel() + ", " + pcb.getMemorySize() + " MB)");
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.log(cycle, source, "rechaza el proceso " + pcb + ": " + e.getMessage());
            }
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

    private void admitNewProcesses() {
        LinkedList<Pcb> admitted = memory.admit();
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
        if (interrupt.type() != InterruptType.IO_COMPLETION) {
            log.log(cycle, source, "ignora la interrupción " + interrupt.type().getLabel());
            return;
        }
        ProcessRuntime rt = findById(interrupt.processId());
        if (rt == null || rt.pcb.getState() != ProcessState.BLOCKED || rt.pcb.getBlockReason() != BlockReason.IO) {
            log.log(cycle, source, "ignora el fin de E/S de P" + interrupt.processId() + " (ya no la espera)");
            return;
        }
        makeReady(rt, "termina su E/S");
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
            terminate(rt, false);
        }
    }

    private void afterUserInstruction(ProcessRuntime rt) {
        switch (rt.pcb.getType()) {
            case CPU_BOUND -> {
                // solo calcula
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
                terminate(rt, true);
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

    private void terminate(ProcessRuntime rt, boolean deadlineExpired) {
        Pcb pcb = rt.pcb;
        boolean admitted = pcb.getState() != ProcessState.NEW;
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
                    case IO -> io.cancel(pcb.getId());
                    case NETWORK -> rt.networkLeft = 0;
                    case SEMAPHORE -> {
                        if (!rt.waitingOn.removeWaiting(pcb)) {
                            rt.step = rt.step.next(pcb.getType());   // ya lo habían despertado: pasó el semWait
                        }
                    }
                }
            }
            case TERMINATED -> {
                return;
            }
        }
        if (pcb.getType().usesBuffer()) {
            releaseBufferHoldings(rt);
        }
        pcb.markTerminated(cycle, deadlineExpired);
        if (admitted) {
            memory.release(pcb);
        }
        processes.remove(rt);
        mutex.acquireUninterruptibly();
        try {
            terminated.addLast(pcb);
        } finally {
            mutex.release();
        }
        metrics.processTerminated(pcb);
        log.log(cycle, source, "el proceso " + pcb + (deadlineExpired
                ? " se termina: venció su deadline" : " terminó su ejecución"));
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

    private LinkedList<Pcb> drain(LinkedList<Pcb> list) {
        mutex.acquireUninterruptibly();
        try {
            LinkedList<Pcb> copy = list.copy();
            list.clear();
            return copy;
        } finally {
            mutex.release();
        }
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
}