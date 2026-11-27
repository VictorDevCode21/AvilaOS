package com.unimet.avilaos.metrics;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.Disk;
import com.unimet.avilaos.os.disk.DiskPolicyType;
import java.util.concurrent.Semaphore;

/**
 * Métricas del disco y de la suspensión que pide el enunciado:
 * <ul>
 *   <li>desplazamiento total del cabezal (sin los saltos de C-SCAN y C-LOOK);</li>
 *   <li>tiempo promedio de espera de las solicitudes en la cola de E/S (las de archivos y
 *       las SUSPEND/RESUME del swap);</li>
 *   <li>cantidad de solicitudes atendidas por cada política;</li>
 *   <li>cantidad de suspensiones y reactivaciones;</li>
 *   <li>solicitudes canceladas (procesos que murieron esperando), en un solo número: las
 *       que se sacaron de la cola más las que el disco abortó o descartó;</li>
 *   <li>el desplazamiento acumulado con respecto al tiempo, para la gráfica que compara
 *       las políticas: una muestra cada vez que el disco termina una solicitud.</li>
 * </ul>
 *
 * El desplazamiento y la espera los mide el disco (Disk); aquí se cuenta lo que el disco no
 * sabe: qué política eligió cada solicitud (la que viene en su SeekPlan, no la vigente al
 * terminar) y las suspensiones y reactivaciones, que se cuentan al completarse.
 *
 * Lo llena el kernel en el hilo del computador y lo lee la GUI en el de Swing: un Semaphore
 * protege los contadores y las consultas devuelven copias. Las atendidas de la foto son la
 * suma de las atendidas por política (las cuenta esta clase), así el total siempre cuadra
 * con el detalle por política aunque el disco vaya un ciclo adelante.
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
     * @param averageWait     ciclos promedio que esperó una solicitud en la cola (incluye las
     *                        SUSPEND y RESUME del swap, que pasan por la misma cola)
     * @param served          solicitudes atendidas (todas las políticas; = la suma de servedPerPolicy)
     * @param servedPerPolicy atendidas por cada política, en el orden de DiskPolicyType.values()
     *                        (el mismo de DiskPolicyType.labels(), que usa la vista del disco)
     * @param suspensions     suspensiones completadas
     * @param reactivations   reactivaciones completadas
     * @param cancelled       solicitudes canceladas (sacadas de la cola + abortadas o descartadas por el disco)
     */
    public record Snapshot(long totalSeek, double averageWait, long served, long[] servedPerPolicy,
            long suspensions, long reactivations, long cancelled) {
    }

    private final Disk disk;
    private final long[] servedPerPolicy = new long[DiskPolicyType.values().length];
    private final LinkedList<SeekSample> samples = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1, true);
    private long served;
    private long suspensions;
    private long reactivations;
    private long removedFromQueue;

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
     * @param chosenBy etiqueta de la política que la eligió (SeekPlan.getChosenBy; el kernel pasa
     *                 la vigente si no viniera anotada)
     * @throws IllegalArgumentException si falta la política: el total debe cuadrar con el detalle
     */
    public void requestServed(long cycle, String chosenBy) {
        if (chosenBy == null || chosenBy.isBlank()) {
            throw new IllegalArgumentException("Una solicitud atendida necesita la política que la eligió");
        }
        long cumulative = disk.getTotalSeek();               // fuera del mutex: el disco tiene el suyo
        mutex.acquireUninterruptibly();
        try {
            servedPerPolicy[DiskPolicyType.fromLabel(chosenBy).ordinal()]++;
            served++;
            samples.addLast(new SeekSample(cycle, cumulative, chosenBy));
        } finally {
            mutex.release();
        }
    }

    /** El kernel sacó n solicitudes de la cola del disco porque su proceso murió. */
    public void requestsRemovedFromQueue(int n) {
        if (n <= 0) {
            return;
        }
        mutex.acquireUninterruptibly();
        try {
            removedFromQueue += n;
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
        long diskCancelled = disk.getCancelledRequests();
        mutex.acquireUninterruptibly();
        try {
            return new Snapshot(seek, wait, served, servedPerPolicy.clone(), suspensions, reactivations,
                    removedFromQueue + diskCancelled);
        } finally {
            mutex.release();
        }
    }

    /** Solicitudes canceladas en total: las sacadas de la cola y las que el disco abortó o descartó. */
    public long getCancelledRequests() {
        long diskCancelled = disk.getCancelledRequests();
        mutex.acquireUninterruptibly();
        try {
            return removedFromQueue + diskCancelled;
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
