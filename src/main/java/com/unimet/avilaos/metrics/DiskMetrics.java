package com.unimet.avilaos.metrics;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.Disk;
import com.unimet.avilaos.os.disk.DiskPolicyType;
import java.util.concurrent.Semaphore;

/**
 * Métricas del disco y de la suspensión que pide el enunciado:
 * <ul>
 *   <li>desplazamiento total del cabezal (sin los saltos de C-SCAN y C-LOOK);</li>
 *   <li>tiempo promedio de espera de las solicitudes en la cola de E/S;</li>
 *   <li>cantidad de solicitudes atendidas por cada política;</li>
 *   <li>cantidad de suspensiones y reactivaciones;</li>
 *   <li>el desplazamiento acumulado con respecto al tiempo, para la gráfica que compara
 *       las políticas: una muestra cada vez que el disco termina una solicitud.</li>
 * </ul>
 *
 * El desplazamiento y la espera los mide el disco (Disk); aquí se cuenta lo que el disco no
 * sabe: qué política eligió cada solicitud (la que viene en su SeekPlan, no la vigente al
 * terminar) y las suspensiones y reactivaciones, que se cuentan al completarse.
 *
 * Lo llena el kernel en el hilo del computador y lo lee la GUI en el de Swing: un Semaphore
 * protege los contadores y las consultas devuelven copias.
 *
 * @author NicollPinzon
 */
public class DiskMetrics {

    /**
     * Un punto de la gráfica: el desplazamiento acumulado en un ciclo.
     *
     * @param cycle      ciclo en que el disco terminó la solicitud
     * @param cumulative desplazamiento total hasta ese momento
     * @param policy     política que eligió esa solicitud (etiqueta, por ejemplo "SSTF")
     */
    public record SeekSample(long cycle, long cumulative, String policy) {
    }

    /**
     * Las métricas en un momento (copias).
     *
     * @param totalSeek       desplazamiento total del cabezal, sin los saltos
     * @param averageWait     ciclos promedio que esperó una solicitud en la cola
     * @param served          solicitudes atendidas (todas las políticas)
     * @param servedPerPolicy atendidas por cada política, en el orden de DiskPolicyType.values()
     *                        (el mismo de DiskPolicyType.labels(), que usa la vista del disco)
     * @param suspensions     suspensiones completadas
     * @param reactivations   reactivaciones completadas
     */
    public record Snapshot(long totalSeek, double averageWait, long served, long[] servedPerPolicy,
            long suspensions, long reactivations) {
    }

    private final Disk disk;
    private final long[] servedPerPolicy = new long[DiskPolicyType.values().length];
    private final LinkedList<SeekSample> samples = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1, true);
    private long suspensions;
    private long reactivations;

    /** @param disk disco del computador (de él salen el desplazamiento y la espera) */
    public DiskMetrics(Disk disk) {
        if (disk == null) {
            throw new IllegalArgumentException("Las métricas del disco necesitan el disco");
        }
        this.disk = disk;
    }

    // ---------- Lo llama el kernel ----------

    /**
     * El disco terminó una solicitud (de archivo o del swap).
     *
     * @param cycle    ciclo en que terminó
     * @param chosenBy etiqueta de la política que la eligió (SeekPlan.getChosenBy), o null si no se sabe
     */
    public void requestServed(long cycle, String chosenBy) {
        long cumulative = disk.getTotalSeek();               // fuera del mutex: el disco tiene el suyo
        mutex.acquireUninterruptibly();
        try {
            if (chosenBy != null) {
                servedPerPolicy[DiskPolicyType.fromLabel(chosenBy).ordinal()]++;
            }
            samples.addLast(new SeekSample(cycle, cumulative, chosenBy));
        } finally {
            mutex.release();
        }
    }

    /** Una suspensión se completó (el disco terminó de escribir el proceso en el swap). */
    public void suspensionCompleted() {
        mutex.acquireUninterruptibly();
        try {
            suspensions++;
        } finally {
            mutex.release();
        }
    }

    /** Una reactivación se completó (el proceso volvió a la RAM). */
    public void reactivationCompleted() {
        mutex.acquireUninterruptibly();
        try {
            reactivations++;
        } finally {
            mutex.release();
        }
    }

    // ---------- Consultas (GUI) ----------

    public Snapshot getSnapshot() {
        long seek = disk.getTotalSeek();
        double wait = disk.getAverageWait();
        long served = disk.getServedRequests();
        mutex.acquireUninterruptibly();
        try {
            return new Snapshot(seek, wait, served, servedPerPolicy.clone(), suspensions, reactivations);
        } finally {
            mutex.release();
        }
    }

    public long getSuspensions() {
        mutex.acquireUninterruptibly();
        try {
            return suspensions;
        } finally {
            mutex.release();
        }
    }

    public long getReactivations() {
        mutex.acquireUninterruptibly();
        try {
            return reactivations;
        } finally {
            mutex.release();
        }
    }

    /** Cantidad de puntos de la gráfica guardados hasta ahora. */
    public int getSampleCount() {
        mutex.acquireUninterruptibly();
        try {
            return samples.size();
        } finally {
            mutex.release();
        }
    }

    /**
     * Los puntos de la gráfica desde el número from (0 = todos). La GUI guarda cuántos ya
     * dibujó y pide solo los nuevos en cada refresco.
     */
    public LinkedList<SeekSample> getSamplesSince(int from) {
        mutex.acquireUninterruptibly();
        try {
            LinkedList<SeekSample> result = new LinkedList<>();
            LinkedList<SeekSample> all = samples.copy();
            int index = 0;
            while (!all.isEmpty()) {
                SeekSample s = all.removeFirst();
                if (index++ >= from) {
                    result.addLast(s);
                }
            }
            return result;
        } finally {
            mutex.release();
        }
    }
}
