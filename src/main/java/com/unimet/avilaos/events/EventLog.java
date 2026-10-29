package com.unimet.avilaos.events;

import com.unimet.avilaos.datastructures.LinkedList;
import java.util.concurrent.Semaphore;

/**
 * Log de eventos del sistema distribuido. Lo escriben los kernels de todos los
 * computadores (desde hilos distintos) y lo observa la GUI.
 *
 * - Guarda como máximo 'capacity' entradas: al pasarse, descarta la más antigua,
 *   para que una simulación larga no llene la memoria.
 * - Avisa a los observadores FUERA del mutex (misma regla que IoDevice y Cpu:
 *   nunca llamar código ajeno con un candado tomado).
 *
 * @author victor1102
 */
public class EventLog {

    public static final int DEFAULT_CAPACITY = 2000;

    private final int capacity;
    private final LinkedList<LogEntry> entries = new LinkedList<>();
    private final LinkedList<SimulationListener> listeners = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1, true);

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

    public void removeListener(SimulationListener listener) {
        mutex.acquireUninterruptibly();
        try {
            listeners.remove(listener);
        } finally {
            mutex.release();
        }
    }

    // ---------- Escritura ----------

    /**
     * Registra un evento y avisa a los observadores.
     *
     * @param cycle   ciclo actual
     * @param source  quién lo genera (ej. "Computador 1")
     * @param message descripción en español
     */
    public void log(long cycle, String source, String message) {
        LogEntry entry = new LogEntry(cycle, source, message);
        LinkedList<SimulationListener> toNotify;
        mutex.acquireUninterruptibly();
        try {
            entries.addLast(entry);
            if (entries.size() > capacity) {
                entries.removeFirst();               // descarta la más antigua
            }
            toNotify = listeners.copy();             // copia para avisar sin el candado
        } finally {
            mutex.release();
        }
        for (int i = 0; i < toNotify.size(); i++) {
            toNotify.get(i).onLogEntry(entry);       // FUERA del mutex
        }
    }

    /** Avisa a los observadores que terminó un ciclo (lo llamará el clúster). */
    public void cycleCompleted(long cycle) {
        LinkedList<SimulationListener> toNotify;
        mutex.acquireUninterruptibly();
        try {
            toNotify = listeners.copy();
        } finally {
            mutex.release();
        }
        for (int i = 0; i < toNotify.size(); i++) {
            toNotify.get(i).onCycleCompleted(cycle);
        }
    }

    // ---------- Lectura ----------

    /** Copia de las entradas, de la más antigua a la más reciente. */
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
}