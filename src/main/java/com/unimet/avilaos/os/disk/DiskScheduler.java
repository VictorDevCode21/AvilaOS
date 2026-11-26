package com.unimet.avilaos.os.disk;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.SeekPlan;
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
 *   <li>la GUI cambia la política en ejecución con {@link #setPolicy} y lee la
 *       cola con {@link #getPendingRequests} (una copia).</li>
 * </ul>
 *
 * Cambio de política en caliente: la nueva política se usa desde la siguiente
 * llamada a next(). La solicitud que el disco ya está atendiendo no se
 * interrumpe, y como las políticas no guardan estado no queda nada a medias.
 *
 * Concurrencia: la cola y la política se protegen con un Semaphore justo. La
 * política se consulta FUERA del mutex, sobre una copia de la cola (regla del
 * equipo: no llamar código ajeno con el mutex tomado). Si mientras tanto la
 * solicitud elegida se canceló, se vuelve a preguntar.
 *
 * @author NicollPinzon
 */
public class DiskScheduler {

    private final int blockCount;
    private final LinkedList<DiskRequest> queue = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1, true);

    private DiskSchedulingPolicy policy;

    /**
     * @param blockCount    cantidad de bloques del disco (mayor que 0)
     * @param initialPolicy política con la que arranca
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public DiskScheduler(int blockCount, DiskPolicyType initialPolicy) {
        if (blockCount <= 0) {
            throw new IllegalArgumentException("El disco debe tener al menos 1 bloque");
        }
        if (initialPolicy == null) {
            throw new IllegalArgumentException("El planificador de disco necesita una política inicial");
        }
        this.blockCount = blockCount;
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
        if (request.getTargetBlock() >= blockCount) {
            throw new IllegalArgumentException("El bloque " + request.getTargetBlock()
                    + " está fuera del disco (bloques 0 a " + (blockCount - 1) + ")");
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
            int removed = 0;
            LinkedList<DiskRequest> snapshot = queue.copy();
            for (int i = 0; i < snapshot.size(); i++) {
                DiskRequest r = snapshot.get(i);
                if (r.getProcessId() == processId && queue.remove(r)) {
                    removed++;
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
     * @return el recorrido, o null si la cola está vacía
     * @throws IllegalArgumentException si el cabezal está fuera del disco o la dirección es null
     * @throws IllegalStateException    si la política devuelve un recorrido inválido
     */
    public SeekPlan next(int headBlock, HeadDirection direction) {
        if (headBlock < 0 || headBlock >= blockCount) {
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
                if (queue.isEmpty()) {
                    return null;
                }
                snapshot = queue.copy();
                current = policy;
            } finally {
                mutex.release();
            }

            SeekPlan plan = current.select(snapshot, headBlock, direction, blockCount);   // FUERA del mutex
            check(plan, snapshot, headBlock, current);

            mutex.acquireUninterruptibly();
            try {
                if (queue.remove(plan.getRequest())) {
                    return plan;
                }
                // La eligió pero se canceló mientras tanto: se vuelve a preguntar.
            } finally {
                mutex.release();
            }
        }
    }

    // ---------- Lo usa la GUI ----------

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

    public int getBlockCount() {
        return blockCount;
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
        if (plan.getSweepEdge() >= blockCount || plan.getJumpTo() >= blockCount) {
            throw new IllegalStateException("La política " + name + " llevó el cabezal fuera del disco");
        }
    }
}
