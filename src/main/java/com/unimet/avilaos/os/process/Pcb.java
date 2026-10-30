package com.unimet.avilaos.os.process;

import java.util.concurrent.Semaphore;

/**
 * Bloque de control de proceso (PCB). Un proceso de ÁvilaOS es monohilo, así que
 * el PCB es también la unidad que el planificador asigna al procesador.
 *
 * Es DATO, no un hilo de Java: "ejecutar" el proceso significa que la CPU avanza
 * su PC. El kernel copia el PC entre la CPU y el PCB en cada cambio de contexto.
 *
 * Lo escribe el hilo de su computador y lo lee la GUI, por eso los campos que
 * cambian son volatile. Además, dos computadores pueden tocar el mismo PCB en el
 * mismo tick (ej. su computador lo termina por deadline mientras un productor de
 * otro computador hace semSignal y lo despierta). Por eso cada cambio de estado
 * (verificar + cambiar) ocurre dentro de stateMutex: si no, ambos verían BLOCKED,
 * ambos pasarían la validación y un proceso TERMINADO podría volver a LISTO.
 *
 * Las decisiones (cuándo bloquear, terminar, etc.) las toma el kernel; el PCB solo
 * guarda el estado y valida que la transición sea legal.
 *
 * Elementos mínimos que exige el enunciado: ID, nombre, computador, estado, tipo,
 * prioridad, memoria asignada, deadline y tiempo restante de ejecución.
 *
 * @author donatovodola
 */
public class Pcb {

    // ---------- Identidad y atributos que da el usuario (no cambian) ----------
    private final int id;
    private final String name;
    private final ProcessType type;
    private final int priority;            // menor número = más prioridad
    private final int memorySize;          // MB que ocupa en la RAM de su computador
    private final int totalInstructions;
    private final int computerId;          // no hay migración: nunca cambia
    private final long initialDeadline;    // en ciclos
    private final long arrivalCycle;       // ciclo en que se creó (métricas)

    // I/O bound
    private final int ioInterval;          // cada cuántas instrucciones pide E/S
    private final int ioDuration;          // ciclos que tarda en satisfacerse la E/S

    // Productor / consumidor
    private final String bufferName;
    private final int operationInterval;   // cada cuántos ciclos produce/consume un elemento
    private final int itemsRequired;       // elementos que necesita para terminar

    // ---------- Estado de ejecución (cambia durante la simulación) ----------
    private volatile ProcessState state = ProcessState.NEW;
    private volatile BlockReason blockReason;          // null si no está bloqueado
    private volatile String blockDetail;               // ej. "'lleno' de B1" (sin repetir "semáforo")
    private volatile int programCounter;
    private volatile int remainingInstructions;
    private volatile long remainingDeadline;
    private volatile int quantumUsed;                  // solo importa en Round Robin
    private volatile long readySequence;               // orden de llegada a listos (lo pone el Scheduler)
    private volatile int itemsDone;
    private volatile boolean deadlineMissed;

    /** Mutex de los cambios de estado: verificar y cambiar deben ser atómicos. */
    private final Semaphore stateMutex = new Semaphore(1, true);

    // ---------- Contabilidad para métricas ----------
    private volatile long firstRunCycle = -1;
    private volatile long finishCycle = -1;
    private volatile long blockedOnSemaphoreCycles;

    private Pcb(String name, ProcessType type, int priority, int memorySize, int totalInstructions,
            long deadline, int computerId, long arrivalCycle, int ioInterval, int ioDuration,
            String bufferName, int operationInterval, int itemsRequired) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del proceso no puede estar vacío");
        }
        if (type == null) {
            throw new IllegalArgumentException("Debe indicarse el tipo de proceso");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("La prioridad no puede ser negativa");
        }
        if (memorySize <= 0) {
            throw new IllegalArgumentException("La memoria del proceso debe ser mayor que 0");
        }
        if (totalInstructions <= 0) {
            throw new IllegalArgumentException("La cantidad de instrucciones debe ser mayor que 0");
        }
        if (deadline <= 0) {
            throw new IllegalArgumentException("El deadline debe ser mayor que 0 ciclos");
        }
        if (computerId < 1) {
            throw new IllegalArgumentException("El computador debe ser 1 o mayor");
        }
        if (arrivalCycle < 0) {
            throw new IllegalArgumentException("El ciclo de llegada no puede ser negativo");
        }
        this.id = PidGenerator.nextPid();   // se pide el ID solo si todo lo anterior es válido
        this.name = name.trim();
        this.type = type;
        this.priority = priority;
        this.memorySize = memorySize;
        this.totalInstructions = totalInstructions;
        this.remainingInstructions = totalInstructions;
        this.initialDeadline = deadline;
        this.remainingDeadline = deadline;
        this.computerId = computerId;
        this.arrivalCycle = arrivalCycle;
        this.ioInterval = ioInterval;
        this.ioDuration = ioDuration;
        this.bufferName = bufferName;
        this.operationInterval = operationInterval;
        this.itemsRequired = itemsRequired;
    }

    // ---------- Fábricas: una por tipo, cada una valida sus propios campos ----------

    public static Pcb cpuBound(String name, int priority, int memorySize, int instructions,
            long deadline, int computerId, long arrivalCycle) {
        return new Pcb(name, ProcessType.CPU_BOUND, priority, memorySize, instructions,
                deadline, computerId, arrivalCycle, 0, 0, null, 0, 0);
    }

    public static Pcb ioBound(String name, int priority, int memorySize, int instructions,
            long deadline, int computerId, long arrivalCycle, int ioInterval, int ioDuration) {
        if (ioInterval <= 0 || ioDuration <= 0) {
            throw new IllegalArgumentException("El intervalo y la duración de E/S deben ser mayores que 0");
        }
        return new Pcb(name, ProcessType.IO_BOUND, priority, memorySize, instructions,
                deadline, computerId, arrivalCycle, ioInterval, ioDuration, null, 0, 0);
    }

    /** @param type PRODUCER o CONSUMER */
    public static Pcb bufferUser(String name, ProcessType type, int priority, int memorySize,
            int instructions, long deadline, int computerId, long arrivalCycle,
            String bufferName, int operationInterval, int itemsRequired) {
        if (type == null || !type.usesBuffer()) {
            throw new IllegalArgumentException("El tipo debe ser Productor o Consumidor");
        }
        if (bufferName == null || bufferName.isBlank()) {
            throw new IllegalArgumentException("Debe indicarse el buffer");
        }
        if (operationInterval <= 0 || itemsRequired <= 0) {
            throw new IllegalArgumentException("El intervalo y los elementos requeridos deben ser mayores que 0");
        }
        return new Pcb(name, type, priority, memorySize, instructions, deadline, computerId,
                arrivalCycle, 0, 0, bufferName.trim(), operationInterval, itemsRequired);
    }

    // ---------- Transiciones de estado (las ordena el kernel) ----------

    /**
     * Cambia de estado validando el diagrama de 5 estados.
     * Debe llamarse con stateMutex tomado (lo hacen los métodos mark...).
     */
    private void transitionTo(ProcessState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("Transición inválida de " + this + ": "
                    + state.getLabel() + " → " + next.getLabel());
        }
        state = next;
    }

    /** NEW → READY (admitido porque había RAM) o BLOCKED/RUNNING → READY. */
    public void markReady() {
        stateMutex.acquireUninterruptibly();
        try {
            transitionTo(ProcessState.READY);
            blockReason = null;
            blockDetail = null;
            quantumUsed = 0;
        } finally {
            stateMutex.release();
        }
    }

    /** READY → RUNNING. Registra la primera vez que corre (tiempo de respuesta). */
    public void markRunning(long cycle) {
        stateMutex.acquireUninterruptibly();
        try {
            transitionTo(ProcessState.RUNNING);
            if (firstRunCycle < 0) {
                firstRunCycle = cycle;
            }
        } finally {
            stateMutex.release();
        }
    }

    /**
     * RUNNING → BLOCKED con su motivo.
     *
     * @param detail solo el complemento, sin repetir el motivo: ej. "'lleno' de B1"
     *               (describeBlock lo muestra como "Esperando semáforo: 'lleno' de B1")
     */
    public void markBlocked(BlockReason reason, String detail) {
        if (reason == null) {
            throw new IllegalArgumentException("Un bloqueo necesita motivo");
        }
        stateMutex.acquireUninterruptibly();
        try {
            transitionTo(ProcessState.BLOCKED);
            blockReason = reason;
            blockDetail = detail;
        } finally {
            stateMutex.release();
        }
    }

    /** Cualquier estado → TERMINATED. deadlineExpired = true si lo mató el deadline. */
    public void markTerminated(long cycle, boolean deadlineExpired) {
        stateMutex.acquireUninterruptibly();
        try {
            transitionTo(ProcessState.TERMINATED);
            finishCycle = cycle;
            deadlineMissed = deadlineExpired;
            blockReason = null;
            blockDetail = null;
        } finally {
            stateMutex.release();
        }
    }

    // ---------- Avance por ciclo (lo llama el kernel en cada tick) ----------

    /** Una instrucción ejecutada en la CPU: descuenta lo restante y suma al quantum. */
    public void instructionExecuted() {
        if (remainingInstructions > 0) {
            remainingInstructions--;
        }
        quantumUsed++;
    }

    /**
     * Resta un ciclo al deadline. Corre en TODOS los estados menos Terminado,
     * porque el tiempo pasa aunque el proceso esté en una cola.
     *
     * @return true si el deadline llegó a 0 (el kernel debe terminarlo)
     */
    public boolean tickDeadline() {
        if (state == ProcessState.TERMINATED) {
            return false;
        }
        if (remainingDeadline > 0) {
            remainingDeadline--;
        }
        return remainingDeadline == 0;
    }

    /** Suma un ciclo bloqueado en semáforo (métrica del productor-consumidor). */
    public void addBlockedOnSemaphoreCycle() {
        blockedOnSemaphoreCycles++;
    }

    public void itemProcessed() {
        itemsDone++;
    }

    public boolean isFinished() {
        return type.usesBuffer() ? itemsDone >= itemsRequired : remainingInstructions == 0;
    }

    // ---------- Cambio de contexto ----------

    public void setProgramCounter(int programCounter) {
        this.programCounter = programCounter;
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public void resetQuantum() {
        quantumUsed = 0;
    }

    /**
     * Número de llegada a la cola de listos. Lo asigna el Scheduler cada vez que
     * el proceso entra a listos; sirve para desempatar y para FCFS/Round Robin.
     */
    public void setReadySequence(long readySequence) {
        this.readySequence = readySequence;
    }

    public long getReadySequence() {
        return readySequence;
    }

    // ---------- Getters ----------

    public int getId() { return id; }
    public String getName() { return name; }
    public ProcessType getType() { return type; }
    public int getPriority() { return priority; }
    public int getMemorySize() { return memorySize; }
    public int getTotalInstructions() { return totalInstructions; }
    public int getComputerId() { return computerId; }
    public long getInitialDeadline() { return initialDeadline; }
    public long getArrivalCycle() { return arrivalCycle; }
    public int getIoInterval() { return ioInterval; }
    public int getIoDuration() { return ioDuration; }
    public String getBufferName() { return bufferName; }
    public int getOperationInterval() { return operationInterval; }
    public int getItemsRequired() { return itemsRequired; }
    public ProcessState getState() { return state; }
    public BlockReason getBlockReason() { return blockReason; }
    public String getBlockDetail() { return blockDetail; }
    public int getRemainingInstructions() { return remainingInstructions; }
    public long getRemainingDeadline() { return remainingDeadline; }
    public int getQuantumUsed() { return quantumUsed; }
    public int getItemsDone() { return itemsDone; }
    public boolean isDeadlineMissed() { return deadlineMissed; }
    public long getFirstRunCycle() { return firstRunCycle; }
    public long getFinishCycle() { return finishCycle; }
    public long getBlockedOnSemaphoreCycles() { return blockedOnSemaphoreCycles; }

    /** Texto para la GUI de bloqueados, ej. "Esperando semáforo: 'lleno' de B1". */
    public String describeBlock() {
        if (blockReason == null) {
            return "";
        }
        return blockDetail == null ? blockReason.getLabel() : blockReason.getLabel() + ": " + blockDetail;
    }

    /**
     * Formato corto para colas y log, ej. "P3-H1": proceso 3, hilo 1.
     * H es el hilo (TCB 1:1 con el PCB, modelo monohilo), NO el computador:
     * en el log del enunciado "Computador 1 selecciona el proceso P3-H2" no puede
     * significar computador 2, porque no hay migración. El computador se muestra
     * aparte en la GUI y el log (getComputerId).
     */
    @Override
    public String toString() {
        return "P" + id + "-H1";
    }
}
