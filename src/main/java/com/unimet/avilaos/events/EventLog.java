package com.unimet.avilaos.events;

import com.unimet.avilaos.datastructures.LinkedList;
import java.util.concurrent.Semaphore;

/**
 * Log de eventos de la simulación (sujeto del patrón Observer).
 *
 * Guarda las últimas entradas (descarta las más viejas al pasar la capacidad)
 * y avisa a los observadores registrados. Lo escriben los hilos de los
 * computadores y lo lee la GUI, por eso todo pasa por un mutex.
 *
 * Reglas de concurrencia:
 * - Los observadores se notifican FUERA del mutex: nunca se llama código ajeno
 *   con un candado tomado.
 * - Si un observador lanza una excepción, se aísla: un error de la GUI nunca
 *   debe cortar el tick de un computador ni dejar sin aviso a los demás.
 *
 * @author victor1102
 */
public class EventLog {

    public static final int DEFAULT_CAPACITY = 2000;

    private final int capacity;
    private final LinkedList<LogEntry> entries = new LinkedList<>();
    private final LinkedList<SimulationListener> listeners = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1, true);
    private long nextSequence = 1;

    public EventLog() {
        this(DEFAULT_CAPACITY);
    }

    public EventLog(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("La capacidad del log debe ser mayor que 0");
        }
        this.capacity = capacity;
    }

    // ---------- Observadores ----------

    public void addListener(SimulationListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("El observador no puede ser null");
        }
        mutex.acquireUninterruptibly();
        try {
            listeners.addLast(listener);
        } finally {
            mutex.release();
        }
    }

    public boolean removeListener(SimulationListener listener) {
        mutex.acquireUninterruptibly();
        try {
            return listeners.remove(listener);
        } finally {
            mutex.release();
        }
    }

    // ---------- Registro ----------

    /**
     * Agrega una entrada y avisa a los observadores.
     *
     * @throws IllegalArgumentException si el ciclo es negativo o el origen/mensaje están vacíos
     */
    public void log(long cycle, String source, String message) {
        if (cycle < 0) {
            throw new IllegalArgumentException("El ciclo no puede ser negativo");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("El evento debe indicar quién lo generó");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("El evento debe tener un mensaje");
        }
        LogEntry entry;
        LinkedList<SimulationListener> toNotify;
        mutex.acquireUninterruptibly();
        try {
            entry = new LogEntry(nextSequence++, cycle, source.trim(), message.trim());
            entries.addLast(entry);
            if (entries.size() > capacity) {
                entries.removeFirst();               // descarta la más vieja
            }
            toNotify = listeners.copy();             // foto de los observadores
        } finally {
            mutex.release();
        }
        for (int i = 0; i < toNotify.size(); i++) { // FUERA del mutex
            SimulationListener listener = toNotify.get(i);
            try {
                listener.onLogEntry(entry);
            } catch (RuntimeException e) {
                reportFailure(listener, e);
            }
        }
    }

    /** Lo llama el computador/clúster al terminar cada ciclo. */
    public void cycleCompleted(long cycle) {
        LinkedList<SimulationListener> toNotify;
        mutex.acquireUninterruptibly();
        try {
            toNotify = listeners.copy();
        } finally {
            mutex.release();
        }
        for (int i = 0; i < toNotify.size(); i++) {
            SimulationListener listener = toNotify.get(i);
            try {
                listener.onCycleCompleted(cycle);
            } catch (RuntimeException e) {
                reportFailure(listener, e);
            }
        }
    }

    // ---------- Consultas ----------

    /** Copia de las entradas guardadas, en el orden en que llegaron. */
    public LinkedList<LogEntry> getEntries() {
        mutex.acquireUninterruptibly();
        try {
            return entries.copy();
        } finally {
            mutex.release();
        }
    }

    public int size() {
        mutex.acquireUninterruptibly();
        try {
            return entries.size();
        } finally {
            mutex.release();
        }
    }

    public void clear() {
        mutex.acquireUninterruptibly();
        try {
            entries.clear();
        } finally {
            mutex.release();
        }
    }

    // ---------- Internos ----------

    /**
     * Se informa por la consola de errores y NO con log(): si el observador que
     * falló vuelve a recibir el aviso y vuelve a fallar, se crearía un ciclo infinito.
     */
    private void reportFailure(SimulationListener listener, RuntimeException e) {
        System.err.println("Observador del log falló (" + listener.getClass().getSimpleName() + "): " + e);
    }
}