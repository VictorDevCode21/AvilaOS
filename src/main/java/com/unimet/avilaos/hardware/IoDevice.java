package com.unimet.avilaos.hardware;

import com.unimet.avilaos.datastructures.LinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * Dispositivo de E/S de un computador (controlador de disco simplificado).
 *
 * Trabaja en paralelo con la CPU: en cada tick avanza la solicitud que está
 * atendiendo. Atiende una solicitud a la vez, en orden de llegada (FIFO).
 * Al terminar una, genera la interrupción IO_COMPLETION en la CPU para que
 * el kernel pase al proceso de Bloqueado a Listo.
 *
 * No conoce procesos ni PCB: solo un ID de proceso y una duración en ciclos.
 *
 * @author victor1102
 */
public class IoDevice implements ClockListener {

    private final Cpu cpu;
    private final LinkedQueue<IoRequest> waiting = new LinkedQueue<>();
    private final Semaphore mutex = new Semaphore(1, true);   // protege waiting y current
    private IoRequest current;                                // solicitud en servicio (null si está libre)

    private volatile long busyCycles;
    private volatile long completedRequests;

    /**
     * @param cpu CPU del mismo computador, a la que se le envían las interrupciones
     */
    public IoDevice(Cpu cpu) {
        if (cpu == null) {
            throw new IllegalArgumentException("El dispositivo de E/S necesita una CPU a la cual interrumpir");
        }
        this.cpu = cpu;
    }

    /**
     * El kernel registra una operación de E/S. Queda en cola si el dispositivo está ocupado.
     *
     * @param processId      proceso que pidió la E/S
     * @param durationCycles ciclos que tarda en satisfacerse (mayor que 0)
     */
    public void request(int processId, int durationCycles) {
        if (durationCycles <= 0) {
            throw new IllegalArgumentException("La duración de la E/S debe ser mayor que 0 ciclos");
        }
        mutex.acquireUninterruptibly();
        try {
            waiting.enqueue(new IoRequest(processId, durationCycles));
        } finally {
            mutex.release();
        }
    }

    @Override
    public void onTick(long cycle) {
        Interrupt finished = null;
        mutex.acquireUninterruptibly();
        try {
            if (current == null) {
                current = waiting.dequeue();          // toma la siguiente, si hay
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
            cpu.raiseInterrupt(finished);             // FUERA del mutex: no llamar código ajeno con un candado tomado
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

    /** ID del proceso que se está atendiendo, o -1 si el dispositivo está libre. */
    public int getCurrentProcessId() {
        mutex.acquireUninterruptibly();
        try {
            return current == null ? -1 : current.processId;
        } finally {
            mutex.release();
        }
    }

    /** Ciclos que le faltan a la solicitud actual, o 0 si está libre. */
    public int getCurrentRemaining() {
        mutex.acquireUninterruptibly();
        try {
            return current == null ? 0 : current.remaining;
        } finally {
            mutex.release();
        }
    }

    /** IDs de los procesos esperando el dispositivo, en orden de llegada (para la GUI). */
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

    // ---------- Solicitud de E/S (interna) ----------

    /** No es record porque 'remaining' cambia en cada ciclo. */
    private static final class IoRequest {

        private final int processId;
        private int remaining;

        IoRequest(int processId, int durationCycles) {
            this.processId = processId;
            this.remaining = durationCycles;
        }
    }
}