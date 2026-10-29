package com.unimet.avilaos.os.sync;

import com.unimet.avilaos.datastructures.LinkedQueue;
import com.unimet.avilaos.os.process.BlockReason;
import com.unimet.avilaos.os.process.Pcb;
import java.util.concurrent.Semaphore;

/**
 * Semáforo SIMULADO de ÁvilaOS (Stallings, cap. 5, Fig. 5.3): un contador y una
 * cola de procesos bloqueados. Bloquea PROCESOS de ÁvilaOS (PCB), no hilos de Java.
 *
 * No confundir con java.util.concurrent.Semaphore, que aquí se usa solo como
 * mutex interno (javaMutex) para proteger el contador y la cola, porque varios
 * computadores (hilos de Java distintos) pueden operar el mismo semáforo en el
 * mismo tick: un productor remoto hace semSignal mientras un consumidor local
 * hace semWait.
 *
 * Convención de Stallings para el valor:
 *  - value >= 0: cuántos semWait pueden pasar sin bloquearse.
 *  - value < 0: |value| es la cantidad de procesos esperando en la cola.
 *
 * Es un semáforo FUERTE: la cola es FIFO, así que el que más lleva esperando es
 * el primero en despertar. Eso evita la inanición.
 *
 * @author donatovodola
 */
public class KernelSemaphore {

    private final String name;          // "vacíos", "lleno", "mutex" (texto para GUI y log)
    private final String ownerName;     // buffer al que pertenece, ej. "B1"
    private final SemaphoreType type;
    private int value;
    private final LinkedQueue<Pcb> waiting = new LinkedQueue<>();
    private final Semaphore javaMutex = new Semaphore(1, true);

    /**
     * @param name         nombre visible, ej. "vacíos"
     * @param ownerName    buffer dueño, ej. "B1"
     * @param type         de conteo o binario
     * @param initialValue valor inicial (binario: 0 o 1; conteo: 0 o más)
     */
    public KernelSemaphore(String name, String ownerName, SemaphoreType type, int initialValue) {
        if (name == null || name.isBlank() || ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("El semáforo necesita nombre y buffer dueño");
        }
        if (type == null) {
            throw new IllegalArgumentException("Debe indicarse el tipo de semáforo");
        }
        if (initialValue < 0) {
            throw new IllegalArgumentException("El valor inicial de un semáforo no puede ser negativo");
        }
        if (type == SemaphoreType.BINARY && initialValue > 1) {
            throw new IllegalArgumentException("Un semáforo binario solo puede iniciar en 0 o 1");
        }
        this.name = name.trim();
        this.ownerName = ownerName.trim();
        this.type = type;
        this.value = initialValue;
    }

    // ---------- Operaciones atómicas ----------

    /**
     * semWait (Stallings): decrementa; si queda negativo, el proceso se bloquea.
     *
     * Si se bloquea, aquí mismo (dentro del mutex) se marca BLOCKED y se encola.
     * Hacerlo afuera abriría una ventana para la "señal perdida": otro computador
     * podría hacer semSignal antes de que el proceso esté en la cola.
     *
     * @param pcb proceso en ejecución que hace la operación
     * @return true si puede seguir; false si quedó bloqueado (el kernel debe liberar la CPU)
     */
    public boolean semWait(Pcb pcb) {
        if (pcb == null) {
            throw new IllegalArgumentException("semWait necesita un proceso");
        }
        javaMutex.acquireUninterruptibly();
        try {
            if (value > 0) {
                value--;
                return true;
            }
            // Primero el cambio de estado: si es inválido lanza error y el contador queda intacto.
            pcb.markBlocked(BlockReason.SEMAPHORE, describe());
            value--;
            waiting.enqueue(pcb);
            return false;
        } finally {
            javaMutex.release();
        }
    }

    /**
     * semSignal (Stallings): incrementa; si había procesos esperando, despierta
     * al primero de la cola y lo deja en READY.
     *
     * El proceso despertado puede ser de OTRO computador: quien llama debe
     * entregarlo a la cola de listos de pcb.getComputerId().
     *
     * @return el proceso despertado (ya en READY), o null si nadie esperaba
     */
    public Pcb semSignal() {
        javaMutex.acquireUninterruptibly();
        try {
            if (type == SemaphoreType.BINARY && value >= 1) {
                throw new IllegalStateException("semSignal sobre el mutex '" + name + "' de " + ownerName
                        + " que ya está libre: falta su semWait");
            }
            value++;
            while (value <= 0) {
                Pcb next = waiting.dequeue();
                try {
                    next.markReady();
                    return next;
                } catch (IllegalStateException alreadyTerminated) {
                    // Lo terminaron (deadline) sin sacarlo de la cola: se descarta su espera
                    // y la señal pasa al siguiente.
                    value++;
                }
            }
            return null;
        } finally {
            javaMutex.release();
        }
    }

    /**
     * Saca a un proceso de la cola sin despertarlo. Lo usa el kernel ANTES de
     * terminar por deadline a un proceso bloqueado aquí. Se devuelve su
     * decremento para que el valor siga contando bien a los que esperan.
     *
     * @return true si estaba en la cola
     */
    public boolean removeWaiting(Pcb pcb) {
        javaMutex.acquireUninterruptibly();
        try {
            if (waiting.remove(pcb)) {
                value++;
                return true;
            }
            return false;
        } finally {
            javaMutex.release();
        }
    }

    // ---------- Consultas (GUI y log) ----------

    public String getName() {
        return name;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public SemaphoreType getType() {
        return type;
    }

    public int getValue() {
        javaMutex.acquireUninterruptibly();
        try {
            return value;
        } finally {
            javaMutex.release();
        }
    }

    public int getWaitingCount() {
        javaMutex.acquireUninterruptibly();
        try {
            return waiting.size();
        } finally {
            javaMutex.release();
        }
    }

    /** Copia de la cola (mismo orden) para que la GUI la recorra sin tocar la original. */
    public LinkedQueue<Pcb> getWaitingSnapshot() {
        javaMutex.acquireUninterruptibly();
        try {
            return waiting.copy();
        } finally {
            javaMutex.release();
        }
    }

    /** Texto del motivo de bloqueo, ej. "'vacíos' de B1" (ver Pcb.describeBlock). */
    public String describe() {
        return "'" + name + "' de " + ownerName;
    }

    @Override
    public String toString() {
        return name + "=" + getValue();
    }
}
