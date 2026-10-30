package com.unimet.avilaos.metrics;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.Cpu;
import com.unimet.avilaos.os.process.Pcb;
import java.util.concurrent.Semaphore;

/**
 * Métricas de rendimiento de UN computador.
 *
 * Las alimenta el computador desde su hilo (recordCycle en cada tick y
 * processTerminated cuando un proceso termina) y las lee la GUI desde el hilo
 * de Swing, por eso todo acceso pasa por un mutex.
 *
 * Definiciones (todas medidas en ciclos):
 * - Throughput: procesos que TERMINARON SU TRABAJO a tiempo / ciclos transcurridos
 *   (los que mató su deadline no cuentan: Stallings, cap. 9).
 * - Utilización de CPU: ciclos ocupada / ciclos totales (contadores de la Cpu).
 * - Tiempo de respuesta: primer ciclo en CPU - ciclo de llegada (Stallings, cap. 9).
 * - Tiempo de retorno: ciclo de fin - ciclo de llegada.
 * - Cumplimiento de deadlines: terminados a tiempo / terminados.
 * - Equidad: índice de Jain sobre la fracción de su tiempo en el sistema que
 *   cada proceso estuvo realmente en la CPU (1 = todos tratados igual).
 * - Bloqueo en semáforos: promedio de ciclos bloqueado de productores y consumidores.
 *
 * @author NicollPinzon
 */
public class ComputerMetrics {

    /** Cada cuántos ciclos se guarda un punto para la gráfica. */
    public static final int DEFAULT_SAMPLE_INTERVAL = 5;

    /** Máximo de puntos guardados: al pasarse se descarta el más viejo (igual que EventLog). */
    public static final int MAX_SAMPLES = 200;

    private final int computerId;
    private final Cpu cpu;
    private final int sampleInterval;
    private final Semaphore mutex = new Semaphore(1, true);

    // ---------- Gráfica de utilización ----------
    private final LinkedList<UtilizationSample> history = new LinkedList<>();
    private long busyAtLastSample;
    private long totalAtLastSample;
    private long lastCycle;

    // ---------- Acumulados de procesos terminados ----------
    private long completed;
    private long onTime;
    private long responseCount;          // terminados que llegaron a ejecutarse
    private long responseSum;
    private long turnaroundSum;
    private long fairnessCount;
    private double fairnessSum;          // suma de x
    private double fairnessSquareSum;    // suma de x²
    private long bufferUsers;            // productores/consumidores terminados
    private long semaphoreBlockedSum;

    public ComputerMetrics(int computerId, Cpu cpu) {
        this(computerId, cpu, DEFAULT_SAMPLE_INTERVAL);
    }

    public ComputerMetrics(int computerId, Cpu cpu, int sampleInterval) {
        if (computerId < 1) {
            throw new IllegalArgumentException("El computador debe ser 1 o mayor");   // misma regla que Pcb
        }
        if (cpu == null) {
            throw new IllegalArgumentException("Las métricas necesitan la CPU del computador");
        }
        if (sampleInterval <= 0) {
            throw new IllegalArgumentException("El intervalo de muestreo debe ser mayor que 0");
        }
        this.computerId = computerId;
        this.cpu = cpu;
        this.sampleInterval = sampleInterval;
    }

    // ---------- Registro (lo llama el computador) ----------

    /**
     * Se llama al final de cada ciclo, después del tick de la CPU.
     * Cada sampleInterval ciclos guarda la utilización de ese intervalo.
     */
    public void recordCycle(long cycle) {
        long busy = cpu.getBusyCycles();
        long total = busy + cpu.getIdleCycles();
        mutex.acquireUninterruptibly();
        try {
            lastCycle = cycle;
            long intervalTotal = total - totalAtLastSample;
            if (intervalTotal >= sampleInterval) {
                double utilization = (double) (busy - busyAtLastSample) / intervalTotal;
                history.addLast(new UtilizationSample(cycle, utilization));
                if (history.size() > MAX_SAMPLES) {
                    history.removeFirst();               // descarta el punto más viejo
                }
                busyAtLastSample = busy;
                totalAtLastSample = total;
            }
        } finally {
            mutex.release();
        }
    }

    /** Se llama una vez por proceso, cuando pasa a Terminado (a tiempo o por deadline). */
    public void processTerminated(Pcb pcb) {
        if (pcb == null || pcb.getFinishCycle() < 0) {
            throw new IllegalArgumentException("Solo se registran procesos ya terminados");
        }
        long arrival = pcb.getArrivalCycle();
        long finish = pcb.getFinishCycle();
        long turnaround = Math.max(1, finish - arrival);
        long executed = pcb.getTotalInstructions() - pcb.getRemainingInstructions();

        mutex.acquireUninterruptibly();
        try {
            completed++;
            if (!pcb.isDeadlineMissed()) {
                onTime++;
            }
            turnaroundSum += turnaround;
            if (pcb.getFirstRunCycle() >= 0) {          // si nunca se ejecutó, no tiene respuesta
                responseCount++;
                responseSum += pcb.getFirstRunCycle() - arrival;
            }
            double servedRatio = Math.min(1.0, (double) executed / turnaround);
            fairnessCount++;
            fairnessSum += servedRatio;
            fairnessSquareSum += servedRatio * servedRatio;
            if (pcb.getType().usesBuffer()) {
                bufferUsers++;
                semaphoreBlockedSum += pcb.getBlockedOnSemaphoreCycles();
            }
        } finally {
            mutex.release();
        }
    }

    // ---------- Consultas (GUI) ----------
    // Cada fórmula vive en UN solo lugar (Totals.toMetrics): los getters solo leen la foto.

    public int getComputerId() {
        return computerId;
    }

    /** Todas las métricas de este computador tomadas en el mismo instante (una lectura consistente). */
    public MetricsSnapshot getSnapshot() {
        return snapshot().toMetrics();
    }

    public long getCompletedProcesses() {
        return getSnapshot().completedProcesses();
    }

    /** Procesos que terminaron su trabajo (sin vencer su deadline) por ciclo. */
    public double getThroughput() {
        return getSnapshot().throughput();
    }

    /** Utilización acumulada desde el inicio (0 a 1). */
    public double getCpuUtilization() {
        return getSnapshot().cpuUtilization();
    }

    public double getAverageResponseTime() {
        return getSnapshot().averageResponseTime();
    }

    public double getAverageTurnaroundTime() {
        return getSnapshot().averageTurnaroundTime();
    }

    /** Fracción de procesos terminados que cumplieron su deadline (0 a 1). */
    public double getDeadlineComplianceRate() {
        return getSnapshot().deadlineComplianceRate();
    }

    /** Índice de Jain (entre 1/n y 1; 1 = equidad perfecta). */
    public double getFairnessIndex() {
        return getSnapshot().fairnessIndex();
    }

    public double getAverageSemaphoreBlockedTime() {
        return getSnapshot().averageSemaphoreBlockedTime();
    }

    /** Copia de los puntos de la gráfica, para dibujarla sin bloquear al computador. */
    public LinkedList<UtilizationSample> getUtilizationHistory() {
        mutex.acquireUninterruptibly();
        try {
            return history.copy();
        } finally {
            mutex.release();
        }
    }

    // ---------- Para SystemMetrics (mismo paquete) ----------

    /** Foto de los acumulados en un solo instante, para sumar los de todo el clúster. */
    Totals snapshot() {
        mutex.acquireUninterruptibly();
        try {
            return new Totals(lastCycle, completed, onTime, responseCount, responseSum, turnaroundSum,
                    fairnessCount, fairnessSum, fairnessSquareSum, bufferUsers, semaphoreBlockedSum,
                    cpu.getBusyCycles(), cpu.getIdleCycles());
        } finally {
            mutex.release();
        }
    }

    record Totals(long lastCycle, long completed, long onTime, long responseCount, long responseSum,
            long turnaroundSum, long fairnessCount, double fairnessSum, double fairnessSquareSum,
            long bufferUsers, long semaphoreBlockedSum, long busyCycles, long idleCycles) {

        /** Convierte los acumulados en los valores que muestra la GUI. */
        MetricsSnapshot toMetrics() {
            long cycles = busyCycles + idleCycles;
            return new MetricsSnapshot(
                    lastCycle,
                    completed,
                    lastCycle == 0 ? 0.0 : (double) onTime / lastCycle,
                    cycles == 0 ? 0.0 : (double) busyCycles / cycles,
                    responseCount == 0 ? 0.0 : (double) responseSum / responseCount,
                    completed == 0 ? 0.0 : (double) turnaroundSum / completed,
                    completed == 0 ? 0.0 : (double) onTime / completed,
                    jain(fairnessCount, fairnessSum, fairnessSquareSum),
                    bufferUsers == 0 ? 0.0 : (double) semaphoreBlockedSum / bufferUsers);
        }
    }

    /**
     * J = (Σx)² / (n · Σx²).
     * Con n = 0, o si ningún proceso llegó a ejecutarse (Σx² = 0), no hay nada que
     * comparar y se devuelve 1 (nadie fue tratado peor que otro).
     */
    static double jain(long n, double sum, double squareSum) {
        if (n == 0 || squareSum == 0) {
            return 1.0;
        }
        return (sum * sum) / (n * squareSum);
    }
}
