package com.unimet.avilaos.hardware;

import com.unimet.avilaos.datastructures.LinkedList;
import java.util.concurrent.Semaphore;

/**
 * Reloj global del sistema distribuido.
 *
 * Cada componente registrado (normalmente un computador) recibe un hilo de Java
 * propio ("trabajador") que espera el tick. En cada ciclo el reloj:
 *   1. espera la duración del ciclo (solo para visualización),
 *   2. da un permiso de "tick" a cada trabajador,
 *   3. espera a que TODOS terminen (barrera con el semáforo cycleDone).
 * Así ningún computador se adelanta ni se atrasa: todos avanzan exactamente un ciclo.
 *
 * El reloj no toma decisiones del sistema operativo: solo marca el ritmo.
 *
 * @author victor1102
 */
public class GlobalClock implements Runnable {

    /** Trabajadores registrados (uno por componente). Compartida entre hilos. */
    private final LinkedList<ClockWorker> workers = new LinkedList<>();

    /** Mutex que protege la lista de trabajadores. */
    private final Semaphore workersMutex = new Semaphore(1, true);

    /** Barrera: cada trabajador da 1 permiso al terminar su ciclo; el reloj espera N. */
    private final Semaphore cycleDone = new Semaphore(0);

    private volatile long cycleDurationMs;
    private volatile long currentCycle = 0;
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private Thread clockThread;

    public GlobalClock(long cycleDurationMs) {
        setCycleDurationMs(cycleDurationMs);
    }

    // ---------- Registro de componentes ----------

    /**
     * Registra un componente que reaccionará al reloj, con su propio hilo de Java.
     *
     * @param listener componente (ej. un Computer)
     * @param name     nombre del hilo, útil para depurar (ej. "Computer-1")
     */
    public void register(ClockListener listener, String name) {
        ClockWorker worker = new ClockWorker(listener, cycleDone, name);
        workersMutex.acquireUninterruptibly();
        try {
            workers.addLast(worker);
        } finally {
            workersMutex.release();
        }
        worker.start();   // arranca dormido: su tick empieza en 0
    }

    // ---------- Control de la simulación ----------

    public void start() {
        if (running) {
            return;
        }
        running = true;
        paused = false;
        clockThread = new Thread(this, "GlobalClock");
        clockThread.setDaemon(true);
        clockThread.start();
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    /** Detiene el reloj y todos los trabajadores. Para volver a simular hay que registrar de nuevo. */
    public void stop() {
        running = false;
        if (clockThread != null) {
            clockThread.interrupt();
        }
        workersMutex.acquireUninterruptibly();
        try {
            for (int i = 0; i < workers.size(); i++) {
                workers.get(i).shutdown();
            }
            workers.clear();
        } finally {
            workersMutex.release();
        }
    }

    // ---------- Bucle del reloj ----------

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(cycleDurationMs);    // solo para que el usuario pueda ver la simulación
                if (paused) {
                    continue;
                }
                int fired = fireTick();          // 1) da un tick a cada trabajador
                cycleDone.acquire(fired);        // 2) BARRERA: espera a que todos terminen
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Avanza el contador global y despierta a cada trabajador. Devuelve cuántos despertó. */
    private int fireTick() {
        workersMutex.acquireUninterruptibly();
        try {
            currentCycle++;
            int count = workers.size();
            for (int i = 0; i < count; i++) {
                workers.get(i).fire(currentCycle);
            }
            return count;
        } finally {
            workersMutex.release();
        }
    }

    // ---------- Consultas y configuración ----------

    public long getCurrentCycle() {
        return currentCycle;
    }

    public long getCycleDurationMs() {
        return cycleDurationMs;
    }

    /** Se puede cambiar en plena ejecución (lo exige el enunciado). */
    public void setCycleDurationMs(long cycleDurationMs) {
        if (cycleDurationMs <= 0) {
            throw new IllegalArgumentException("La duración del ciclo debe ser mayor que 0 ms");
        }
        this.cycleDurationMs = cycleDurationMs;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return paused;
    }

    // ---------- Trabajador: un hilo de Java por componente ----------

    /**
     * Hilo que espera el tick del reloj, ejecuta onTick del componente y avisa que terminó.
     */
    private static final class ClockWorker implements Runnable {

        private final ClockListener listener;
        private final Semaphore tick = new Semaphore(0);   // 0: no puede ejecutar hasta que el reloj lo autorice
        private final Semaphore done;                      // compartido con el reloj (barrera)
        private final Thread thread;
        private volatile long cycle;
        private volatile boolean alive = true;

        ClockWorker(ClockListener listener, Semaphore done, String name) {
            this.listener = listener;
            this.done = done;
            this.thread = new Thread(this, name);
            this.thread.setDaemon(true);
        }

        void start() {
            thread.start();
        }

        /** Llamado por el reloj: guarda el número de ciclo y da el permiso. */
        void fire(long cycle) {
            this.cycle = cycle;
            tick.release();
        }

        void shutdown() {
            alive = false;
            thread.interrupt();
        }

        @Override
        public void run() {
            while (alive) {
                try {
                    tick.acquire();              // 💤 hasta que el reloj haga fire()
                } catch (InterruptedException e) {
                    return;                      // stop() nos despertó para terminar
                }
                try {
                    listener.onTick(cycle);      // el componente avanza exactamente un ciclo
                } catch (RuntimeException e) {
                    System.err.println("Error en " + thread.getName() + ", ciclo " + cycle + ": " + e);
                    e.printStackTrace();
                } finally {
                    done.release();              // SIEMPRE avisa, aunque haya error (si no, el reloj se congela)
                }
            }
        }
    }
}