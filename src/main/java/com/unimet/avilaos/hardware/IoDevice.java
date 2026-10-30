package com.unimet.avilaos.hardware;

import com.unimet.avilaos.datastructures.LinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * Dispositivo de E/S de un computador (un disco simplificado, al estilo de un
 * controlador DMA): atiende una solicitud a la vez, en orden FIFO, y cuando
 * termina avisa a la CPU con UNA interrupción IO_COMPLETION.
 *
 * Solo simula el TIEMPO de la operación (ciclos), no mueve datos reales.
 *
 * Orden dentro de un ciclo: el Computer debe llamar io.onTick() y luego
 * cpu.onTick() de forma SECUENCIAL, en su propio hilo. Así la interrupción que
 * se genere en este ciclo la atiende la CPU al final de ese mismo ciclo.
 * Este dispositivo NO debe registrarse directamente en el GlobalClock: tendría
 * su propio hilo worker y el orden respecto a la CPU quedaría indefinido.
 *
 * Regla de concurrencia: nunca se llama código ajeno (cpu.raiseInterrupt)
 * mientras se tiene tomado el mutex propio.
 *
 * @author victor1102
 */
public class IoDevice implements ClockListener {

    private final Cpu cpu;
    private final LinkedQueue<IoRequest> waiting = new LinkedQueue<>();
    private final Semaphore mutex = new Semaphore(1, true);

    private IoRequest current;                 // la que se está atendiendo (null = libre)
    private volatile long busyCycles;
    private volatile long completedRequests;
    private volatile long cancelledRequests;

    public IoDevice(Cpu cpu) {
        if (cpu == null) {
            throw new IllegalArgumentException("El dispositivo de E/S necesita la CPU para interrumpirla");
        }
        this.cpu = cpu;
    }

    // ---------- Lo usa el kernel ----------

    /**
     * Encola una solicitud de E/S de un proceso.
     *
     * @throws IllegalArgumentException si el id o la duración no son válidos
     * @throws IllegalStateException    si el proceso ya tiene una solicitud pendiente
     *                                  o en servicio (un proceso monohilo no puede
     *                                  pedir dos E/S a la vez: sería un error del kernel)
     */
    public void request(int processId, int durationCycles) {
        if (processId <= 0) {
            throw new IllegalArgumentException("El id del proceso debe ser mayor que 0");
        }
        if (durationCycles <= 0) {
            throw new IllegalArgumentException("La duración de la E/S debe ser mayor que 0 ciclos");
        }
        mutex.acquireUninterruptibly();
        try {
            if (hasRequest(processId)) {
                throw new IllegalStateException("P" + processId + " ya tiene una E/S pendiente");
            }
            waiting.enqueue(new IoRequest(processId, durationCycles));
        } finally {
            mutex.release();
        }
    }

    /**
     * Cancela la E/S de un proceso, esté esperando o siendo atendida.
     * El kernel la llama ANTES de terminar un proceso bloqueado por E/S
     * (por ejemplo, cuando se le vence el deadline). No se genera interrupción.
     *
     * @return true si el proceso tenía una solicitud y se canceló
     */
    public boolean cancel(int processId) {
        mutex.acquireUninterruptibly();
        try {
            boolean removed = false;
            if (current != null && current.processId == processId) {
                current = null;                // se aborta: no habrá IO_COMPLETION
                removed = true;
            } else {
                for (int i = 0; i < waiting.size(); i++) {
                    IoRequest r = waiting.get(i);
                    if (r.processId == processId) {
                        removed = waiting.remove(r);
                        break;
                    }
                }
            }
            if (removed) {
                cancelledRequests++;
            }
            return removed;
        } finally {
            mutex.release();
        }
    }

    // ---------- Un ciclo de reloj ----------

    @Override
    public void onTick(long cycle) {
        Interrupt finished = null;
        mutex.acquireUninterruptibly();
        try {
            if (current == null) {
                current = waiting.dequeue();   // toma la siguiente (o null si no hay)
            }
            if (current != null) {
                current.remaining--;
                busyCycles++;
                if (current.remaining == 0) {
                    finished = new Interrupt(InterruptType.IO_COMPLETION, current.processId);
                    current = null;
                    completedRequests++;
                }
            }
        } finally {
            mutex.release();
        }
        if (finished != null) {
            cpu.raiseInterrupt(finished);      // FUERA del mutex
        }
    }

    // ---------- Consultas (GUI y métricas) ----------

    public boolean isBusy() {
        mutex.acquireUninterruptibly();
        try {
            return current != null;
        } finally {
            mutex.release();
        }
    }

    /** @return id del proceso que se está atendiendo, o -1 si el dispositivo está libre */
    public int getCurrentProcessId() {
        mutex.acquireUninterruptibly();
        try {
            return current == null ? -1 : current.processId;
        } finally {
            mutex.release();
        }
    }

    /** @return ciclos que le faltan a la solicitud en servicio, o 0 si está libre */
    public int getCurrentRemaining() {
        mutex.acquireUninterruptibly();
        try {
            return current == null ? 0 : current.remaining;
        } finally {
            mutex.release();
        }
    }

    /** Copia de los ids en espera, en orden FIFO. */
    public int[] getWaitingProcessIds() {
        mutex.acquireUninterruptibly();
        try {
            int[] ids = new int[waiting.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = waiting.get(i).processId;
            }
            return ids;
        } finally {
            mutex.release();
        }
    }

    public long getBusyCycles() {
        return busyCycles;
    }

    public long getCompletedRequests() {
        return completedRequests;
    }

    public long getCancelledRequests() {
        return cancelledRequests;
    }

    // ---------- Internos ----------

    /** Debe llamarse con el mutex tomado. */
    private boolean hasRequest(int processId) {
        if (current != null && current.processId == processId) {
            return true;
        }
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).processId == processId) {
                return true;
            }
        }
        return false;
    }

    /** Una solicitud: de qué proceso es y cuántos ciclos le faltan. */
    private static final class IoRequest {
        private final int processId;
        private int remaining;

        private IoRequest(int processId, int durationCycles) {
            this.processId = processId;
            this.remaining = durationCycles;
        }
    }
}