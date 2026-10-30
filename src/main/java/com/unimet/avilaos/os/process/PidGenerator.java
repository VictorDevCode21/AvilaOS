package com.unimet.avilaos.os.process;

import java.util.concurrent.Semaphore;

/**
 * Genera IDs de proceso únicos en TODO el clúster (lo exige el enunciado).
 * Es uno solo para todos los computadores; por eso es estático.
 *
 * Varios hilos pueden crear procesos a la vez (la GUI, la carga inicial desde el
 * archivo, cada computador), así que leer y aumentar el contador va en la misma
 * sección crítica: si no, dos hilos podrían leer el mismo valor y repetir ID.
 *
 * @author donatovodola
 */
public final class PidGenerator {

    private static int nextId = 1;
    private static final Semaphore mutex = new Semaphore(1, true);

    private PidGenerator() {
        // no se instancia
    }

    /** Devuelve un ID nuevo, nunca repetido desde el último reset. */
    public static int nextPid() {
        mutex.acquireUninterruptibly();
        try {
            return nextId++;
        } finally {
            mutex.release();
        }
    }

    /** Reinicia el contador al empezar una simulación nueva. */
    public static void reset() {
        mutex.acquireUninterruptibly();
        try {
            nextId = 1;
        } finally {
            mutex.release();
        }
    }
}
