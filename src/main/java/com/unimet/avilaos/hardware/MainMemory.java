package com.unimet.avilaos.hardware;

import java.util.concurrent.Semaphore;

/**
 * Memoria principal (RAM) de un computador.
 * No se modela asignación contigua ni particiones (lo permite el enunciado):
 * solo se controla cuánta memoria está usada y cuánta libre.
 *
 * Es leída por la GUI y modificada por el kernel desde hilos distintos,
 * por eso todo acceso pasa por un mutex.
 *
 * @author victor1102
 */
public class MainMemory {

    private final int totalSize;
    private int usedSize;
    private final Semaphore mutex = new Semaphore(1, true);

    /**
     * @param totalSize tamaño de la RAM en MB (debe ser mayor que 0)
     */
    public MainMemory(int totalSize) {
        if (totalSize <= 0) {
            throw new IllegalArgumentException("El tamaño de la RAM debe ser mayor que 0");
        }
        this.totalSize = totalSize;
    }

    /**
     * Intenta reservar memoria. Verificar y reservar ocurren en la MISMA
     * sección crítica para que dos hilos no reserven el mismo espacio.
     *
     * @return true si había espacio y se reservó; false si no cabe
     */
    public boolean allocate(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("El tamaño a reservar debe ser mayor que 0");
        }
        mutex.acquireUninterruptibly();
        try {
            if (usedSize + size > totalSize) {
                return false;
            }
            usedSize += size;
            return true;
        } finally {
            mutex.release();
        }
    }

    /** Libera memoria previamente reservada (ej. al terminar un proceso). */
    public void free(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("El tamaño a liberar debe ser mayor que 0");
        }
        mutex.acquireUninterruptibly();
        try {
            if (size > usedSize) {
                throw new IllegalStateException("Se intenta liberar más memoria de la que está en uso");
            }
            usedSize -= size;
        } finally {
            mutex.release();
        }
    }

    public int getTotalSize() {
        return totalSize;
    }

    public int getUsedSize() {
        mutex.acquireUninterruptibly();
        try {
            return usedSize;
        } finally {
            mutex.release();
        }
    }

    public int getFreeSize() {
        mutex.acquireUninterruptibly();
        try {
            return totalSize - usedSize;
        } finally {
            mutex.release();
        }
    }

    /** Porcentaje de ocupación (0 a 100), para la barra de la GUI. */
    public double getUsagePercentage() {
        mutex.acquireUninterruptibly();
        try {
            return (usedSize * 100.0) / totalSize;
        } finally {
            mutex.release();
        }
    }
}