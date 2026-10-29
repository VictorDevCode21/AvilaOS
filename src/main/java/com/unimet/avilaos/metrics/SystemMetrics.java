package com.unimet.avilaos.metrics;

import com.unimet.avilaos.datastructures.LinkedList;
import java.util.concurrent.Semaphore;

/**
 * Métricas del sistema distribuido completo: junta las de cada computador
 * y lleva los contadores de producidos/consumidos de cada buffer.
 *
 * Los promedios del sistema se calculan sumando los acumulados de todos los
 * computadores (no promediando los promedios), para que un computador con
 * pocos procesos no pese lo mismo que uno con muchos.
 *
 * @author NicollPinzon
 */
public class SystemMetrics {

    private final LinkedList<ComputerMetrics> computers = new LinkedList<>();
    private final LinkedList<BufferStats> buffers = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1, true);

    // ---------- Registro ----------

    public void addComputer(ComputerMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("Las métricas del computador no pueden ser null");
        }
        mutex.acquireUninterruptibly();
        try {
            computers.addLast(metrics);
        } finally {
            mutex.release();
        }
    }

    /** Se llama al crear un buffer. Si ya existía uno con ese nombre, no hace nada. */
    public void registerBuffer(String bufferName) {
        mutex.acquireUninterruptibly();
        try {
            if (findBuffer(bufferName) == null) {
                buffers.addLast(new BufferStats(bufferName));
            }
        } finally {
            mutex.release();
        }
    }

    /** Un productor insertó un elemento en el buffer. */
    public void itemProduced(String bufferName) {
        mutex.acquireUninterruptibly();
        try {
            requireBuffer(bufferName).addProduced();
        } finally {
            mutex.release();
        }
    }

    /** Un consumidor extrajo un elemento del buffer. */
    public void itemConsumed(String bufferName) {
        mutex.acquireUninterruptibly();
        try {
            requireBuffer(bufferName).addConsumed();
        } finally {
            mutex.release();
        }
    }

    // ---------- Consultas (GUI) ----------

    public LinkedList<ComputerMetrics> getComputers() {
        mutex.acquireUninterruptibly();
        try {
            return computers.copy();
        } finally {
            mutex.release();
        }
    }

    /** Copias de los contadores de cada buffer (la GUI no puede modificarlos). */
    public LinkedList<BufferStats> getBufferStats() {
        mutex.acquireUninterruptibly();
        try {
            LinkedList<BufferStats> result = new LinkedList<>();
            for (int i = 0; i < buffers.size(); i++) {
                result.addLast(buffers.get(i).copy());
            }
            return result;
        } finally {
            mutex.release();
        }
    }

    public long getCompletedProcesses() {
        return total().completed();
    }

    public double getThroughput() {
        ComputerMetrics.Totals t = total();
        return t.lastCycle() == 0 ? 0.0 : (double) t.completed() / t.lastCycle();
    }

    public double getCpuUtilization() {
        ComputerMetrics.Totals t = total();
        long cycles = t.busyCycles() + t.idleCycles();
        return cycles == 0 ? 0.0 : (double) t.busyCycles() / cycles;
    }

    public double getAverageResponseTime() {
        ComputerMetrics.Totals t = total();
        return t.responseCount() == 0 ? 0.0 : (double) t.responseSum() / t.responseCount();
    }

    public double getDeadlineComplianceRate() {
        ComputerMetrics.Totals t = total();
        return t.completed() == 0 ? 0.0 : (double) t.onTime() / t.completed();
    }

    public double getFairnessIndex() {
        ComputerMetrics.Totals t = total();
        return ComputerMetrics.jain(t.fairnessCount(), t.fairnessSum(), t.fairnessSquareSum());
    }

    public double getAverageSemaphoreBlockedTime() {
        ComputerMetrics.Totals t = total();
        return t.bufferUsers() == 0 ? 0.0 : (double) t.semaphoreBlockedSum() / t.bufferUsers();
    }

    // ---------- Internos ----------

    /** Suma los acumulados de todos los computadores. */
    private ComputerMetrics.Totals total() {
        LinkedList<ComputerMetrics> list = getComputers();
        long lastCycle = 0, completed = 0, onTime = 0, responseCount = 0, responseSum = 0;
        long fairnessCount = 0, bufferUsers = 0, blocked = 0, busy = 0, idle = 0;
        double fairnessSum = 0, fairnessSquareSum = 0;
        for (int i = 0; i < list.size(); i++) {
            ComputerMetrics.Totals t = list.get(i).snapshot();
            lastCycle = Math.max(lastCycle, t.lastCycle());   // el reloj es global: todos van en el mismo ciclo
            completed += t.completed();
            onTime += t.onTime();
            responseCount += t.responseCount();
            responseSum += t.responseSum();
            fairnessCount += t.fairnessCount();
            fairnessSum += t.fairnessSum();
            fairnessSquareSum += t.fairnessSquareSum();
            bufferUsers += t.bufferUsers();
            blocked += t.semaphoreBlockedSum();
            busy += t.busyCycles();
            idle += t.idleCycles();
        }
        return new ComputerMetrics.Totals(lastCycle, completed, onTime, responseCount, responseSum,
                fairnessCount, fairnessSum, fairnessSquareSum, bufferUsers, blocked, busy, idle);
    }

    private BufferStats findBuffer(String bufferName) {
        for (int i = 0; i < buffers.size(); i++) {
            if (buffers.get(i).getBufferName().equals(bufferName)) {
                return buffers.get(i);
            }
        }
        return null;
    }

    private BufferStats requireBuffer(String bufferName) {
        BufferStats stats = findBuffer(bufferName);
        if (stats == null) {
            throw new IllegalArgumentException("No existe el buffer " + bufferName);
        }
        return stats;
    }
}
