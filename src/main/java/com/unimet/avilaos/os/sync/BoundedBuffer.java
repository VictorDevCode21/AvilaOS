package com.unimet.avilaos.os.sync;

import com.unimet.avilaos.datastructures.LinkedQueue;
import com.unimet.avilaos.os.process.Pcb;
import java.util.concurrent.Semaphore;

/**
 * Buffer de capacidad limitada para productores y consumidores (Stallings, Fig. 5.13).
 * Vive en la RAM de su computador anfitrión; el kernel reserva memorySize al crearlo.
 *
 * Trae sus tres semáforos simulados:
 *  - empty ("vacíos"): de conteo, empieza en la capacidad. El productor hace semWait aquí.
 *  - full  ("lleno"):  de conteo, empieza en 0. El consumidor hace semWait aquí.
 *  - mutex ("mutex"):  binario, empieza en 1. Exclusión mutua para insertar o extraer.
 *
 * Orden correcto (invertirlo puede causar interbloqueo):
 *  productor:  semWait(empty) → semWait(mutex) → insert()  → semSignal(mutex) → semSignal(full)
 *  consumidor: semWait(full)  → semWait(mutex) → extract() → semSignal(mutex) → semSignal(empty)
 *
 * El buffer NO ejecuta ese algoritmo: lo ejecuta el kernel, una operación por ciclo
 * (cada una cuenta como instrucción en modo sistema operativo). Aquí solo están
 * las piezas. Por eso insert/extract validan: si se llaman sin haber pasado por
 * los semáforos, el algoritmo está mal y se avisa con un error.
 *
 * @author donatovodola
 */
public class BoundedBuffer {

    private final String name;
    private final int hostComputerId;
    private final int capacity;
    private final int memorySize;

    private final KernelSemaphore empty;
    private final KernelSemaphore full;
    private final KernelSemaphore mutex;

    /** Elementos guardados (cada uno es un número de secuencia, para verlos en la GUI). */
    private final LinkedQueue<Integer> items = new LinkedQueue<>();
    private int nextItem = 1;
    /** Mutex de Java para items: la GUI lee mientras los computadores escriben. */
    private final Semaphore itemsMutex = new Semaphore(1, true);

    /**
     * @param name           nombre visible, ej. "B1"
     * @param hostComputerId computador anfitrión (1 o mayor)
     * @param capacity       elementos que caben (mayor que 0)
     * @param memorySize     MB que ocupa en la RAM del anfitrión (mayor que 0)
     */
    public BoundedBuffer(String name, int hostComputerId, int capacity, int memorySize) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El buffer debe tener nombre");
        }
        if (hostComputerId < 1) {
            throw new IllegalArgumentException("El computador anfitrión debe ser 1 o mayor");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("La capacidad del buffer debe ser mayor que 0");
        }
        if (memorySize <= 0) {
            throw new IllegalArgumentException("La memoria del buffer debe ser mayor que 0");
        }
        this.name = name.trim();
        this.hostComputerId = hostComputerId;
        this.capacity = capacity;
        this.memorySize = memorySize;
        this.empty = new KernelSemaphore("vacíos", this.name, SemaphoreType.COUNTING, capacity);
        this.full = new KernelSemaphore("lleno", this.name, SemaphoreType.COUNTING, 0);
        this.mutex = new KernelSemaphore("mutex", this.name, SemaphoreType.BINARY, 1);
    }

    // ---------- Operaciones sobre los elementos (dentro de la región crítica simulada) ----------

    /**
     * anyadir(): el productor mete un elemento.
     * Precondición: ya pasó semWait(empty) y semWait(mutex).
     *
     * @return número del elemento insertado
     */
    public int insert() {
        itemsMutex.acquireUninterruptibly();
        try {
            if (items.size() >= capacity) {
                throw new IllegalStateException("El buffer " + name
                        + " está lleno: se insertó sin pasar por semWait('vacíos')");
            }
            int item = nextItem++;
            items.enqueue(item);
            return item;
        } finally {
            itemsMutex.release();
        }
    }

    /**
     * extraer(): el consumidor saca el elemento más antiguo (FIFO).
     * Precondición: ya pasó semWait(full) y semWait(mutex).
     *
     * @return número del elemento extraído
     */
    public int extract() {
        itemsMutex.acquireUninterruptibly();
        try {
            Integer item = items.dequeue();
            if (item == null) {
                throw new IllegalStateException("El buffer " + name
                        + " está vacío: se extrajo sin pasar por semWait('lleno')");
            }
            return item;
        } finally {
            itemsMutex.release();
        }
    }

    // ---------- Ayudas para el kernel ----------

    /** true si el proceso está en otro computador: cada acceso paga la latencia de red. */
    public boolean isRemoteFor(Pcb pcb) {
        return pcb.getComputerId() != hostComputerId;
    }

    /**
     * Saca al proceso de la cola del semáforo donde esté esperando (si está en alguna).
     * El kernel lo llama antes de terminar por deadline a un proceso bloqueado aquí.
     */
    public boolean removeWaitingProcess(Pcb pcb) {
        return empty.removeWaiting(pcb) || full.removeWaiting(pcb) || mutex.removeWaiting(pcb);
    }

    // ---------- Consultas (GUI) ----------

    public String getName() {
        return name;
    }

    public int getHostComputerId() {
        return hostComputerId;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getMemorySize() {
        return memorySize;
    }

    public KernelSemaphore getEmpty() {
        return empty;
    }

    public KernelSemaphore getFull() {
        return full;
    }

    public KernelSemaphore getMutex() {
        return mutex;
    }

    public int getItemCount() {
        itemsMutex.acquireUninterruptibly();
        try {
            return items.size();
        } finally {
            itemsMutex.release();
        }
    }

    /** Copia de los elementos (del más antiguo al más nuevo) para la GUI. */
    public LinkedQueue<Integer> getItemsSnapshot() {
        itemsMutex.acquireUninterruptibly();
        try {
            return items.copy();
        } finally {
            itemsMutex.release();
        }
    }

    /** Ej. "B1 (H1) 2/3 [vacíos=1, lleno=2, mutex=1]". */
    @Override
    public String toString() {
        return name + " (H" + hostComputerId + ") " + getItemCount() + "/" + capacity
                + " [" + empty + ", " + full + ", " + mutex + "]";
    }
}
