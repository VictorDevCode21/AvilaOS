package com.unimet.avilaos.os.sync;

import com.unimet.avilaos.datastructures.LinkedQueue;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.process.ProcessState;
import java.util.concurrent.Semaphore;

/**
 * Semáforo SIMULADO de ÁvilaOS (Stallings, cap. 5, Fig. 5.3): un contador y una
 * cola de procesos bloqueados. Bloquea PROCESOS de ÁvilaOS (PCB), no hilos de Java.
 *
 * No confundir con java.util.concurrent.Semaphore, que aquí se usa solo como
 * mutex interno (javaMutex) para proteger el contador, la cola y el dueño entre
 * hilos de Java (los computadores que lo operan y la GUI que lo lee).
 *
 * Convención de Stallings para el valor:
 *  - value >= 0: cuántos semWait pueden pasar sin bloquearse.
 *  - value < 0: |value| es la cantidad de procesos esperando en la cola.
 *
 * Es un semáforo FUERTE: la cola es FIFO, así que el que más lleva esperando es
 * el primero en despertar. Eso evita la inanición.
 *
 * Este semáforo NO cambia el estado de ningún PCB (un solo lugar para las
 * transiciones: el kernel, como en Scheduler y MemoryManager). Además, un
 * computador nunca toca el PCB de otro (opción B): semSignal solo DEVUELVE al
 * proceso despertado y el kernel lo entrega a su computador dueño.
 *
 * Contrato con el kernel:
 *  - semWait devuelve false → el kernel hace pcb.markBlocked(SEMAPHORE, describe()).
 *  - semSignal devuelve un PCB → se envía a su computador, que hace markReady + addReady.
 *    Si al llegar el aviso el proceso ya está Terminado, se IGNORA: su turno ya
 *    se pasó cuando lo terminaron (regla siguiente). Nunca se pasa dos veces.
 *  - Al terminar un proceso que usa este semáforo (deadline), el kernel deshace
 *    lo que el proceso dejó tomado (como SEM_UNDO en UNIX):
 *      · si estaba bloqueado aquí y removeWaiting(pcb) devuelve true → listo,
 *        se devolvió su decremento;
 *      · si estaba bloqueado aquí y removeWaiting(pcb) devuelve false → ya lo
 *        habían despertado y el aviso viaja: semSignal(pcb) para pasar el turno;
 *      · si es dueño del mutex (getHolder() == pcb) → semSignal(pcb).
 *    Se aplica UNA sola de estas reglas por semáforo: en el mutex, un proceso
 *    despertado cuyo aviso viaja ya es el dueño, así que las dos últimas reglas
 *    describen el mismo semSignal(pcb) (hacerlo dos veces falla: "sin tenerlo").
 *  - Defensa: si por error queda en la cola un proceso ya Terminado (el kernel
 *    olvidó removeWaiting), semSignal lo salta y pasa el turno al siguiente, para
 *    que el mutex nunca quede en manos de un proceso muerto.
 *
 * @author donatovodola
 */
public class KernelSemaphore {

    private final String name;          // "vacíos", "lleno", "mutex" (texto para GUI y log)
    private final String ownerName;     // buffer al que pertenece, ej. "B1"
    private final SemaphoreType type;
    private final int maxValue;         // binario: 1; de conteo: capacidad del buffer
    private int value;
    private Pcb holder;                 // solo binario: quién está en la región crítica
    private final LinkedQueue<Pcb> waiting = new LinkedQueue<>();
    private final Semaphore javaMutex = new Semaphore(1, true);

    /**
     * @param name         nombre visible, ej. "vacíos"
     * @param ownerName    buffer dueño, ej. "B1"
     * @param type         de conteo o binario
     * @param initialValue valor inicial (de 0 a maxValue)
     * @param maxValue     valor máximo (binario: debe ser 1; de conteo: 1 o más)
     */
    public KernelSemaphore(String name, String ownerName, SemaphoreType type, int initialValue, int maxValue) {
        if (name == null || name.isBlank() || ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("El semáforo necesita nombre y buffer dueño");
        }
        if (type == null) {
            throw new IllegalArgumentException("Debe indicarse el tipo de semáforo");
        }
        if (type == SemaphoreType.BINARY && maxValue != 1) {
            throw new IllegalArgumentException("Un semáforo binario tiene valor máximo 1");
        }
        if (maxValue < 1) {
            throw new IllegalArgumentException("El valor máximo de un semáforo debe ser 1 o más");
        }
        if (initialValue < 0 || initialValue > maxValue) {
            throw new IllegalArgumentException("El valor inicial debe estar entre 0 y " + maxValue);
        }
        this.name = name.trim();
        this.ownerName = ownerName.trim();
        this.type = type;
        this.maxValue = maxValue;
        this.value = initialValue;
    }

    // ---------- Operaciones atómicas ----------

    /**
     * semWait (Stallings): decrementa; si queda negativo, el proceso queda en la cola.
     * El proceso se encola DENTRO del mutex, así que una señal que llegue justo
     * después ya lo encuentra: no se puede perder.
     *
     * @param pcb proceso en ejecución que hace la operación
     * @return true si puede seguir; false si quedó en la cola (el kernel debe
     *         marcarlo Bloqueado y liberar la CPU)
     * @throws IllegalStateException si el proceso no está Ejecutando o ya esperaba aquí
     */
    public boolean semWait(Pcb pcb) {
        if (pcb == null) {
            throw new IllegalArgumentException("semWait necesita un proceso");
        }
        if (pcb.getState() != ProcessState.RUNNING) {
            throw new IllegalStateException(pcb + " no está Ejecutando: no puede hacer semWait");
        }
        javaMutex.acquireUninterruptibly();
        try {
            if (waiting.contains(pcb)) {
                throw new IllegalStateException(pcb + " ya espera en " + describe());
            }
            if (type == SemaphoreType.BINARY && holder == pcb) {
                throw new IllegalStateException(pcb + " ya tiene " + describe() + ": haría semWait dos veces");
            }
            value--;
            if (value >= 0) {
                if (type == SemaphoreType.BINARY) {
                    holder = pcb;
                }
                return true;
            }
            waiting.enqueue(pcb);
            return false;
        } finally {
            javaMutex.release();
        }
    }

    /**
     * semSignal (Stallings): incrementa; si había procesos esperando, saca al
     * primero de la cola y lo DEVUELVE (sin cambiar su estado: lo hace su kernel).
     * En el mutex, el turno pasa directo al despertado (pasa a ser el dueño).
     *
     * @param caller proceso que hace la operación. En el mutex debe ser su dueño;
     *               en uno de conteo puede ser cualquiera (incluso null).
     * @return el proceso despertado, o null si nadie esperaba
     * @throws IllegalStateException si el mutex lo libera quien no lo tiene, o si
     *                               el valor pasaría de su máximo (falta un semWait)
     */
    public Pcb semSignal(Pcb caller) {
        javaMutex.acquireUninterruptibly();
        try {
            if (type == SemaphoreType.BINARY && (holder == null || holder != caller)) {
                throw new IllegalStateException(caller + " libera " + describe() + " sin tenerlo"
                        + (holder == null ? " (está libre)" : " (lo tiene " + holder + ")"));
            }
            if (value >= maxValue) {
                throw new IllegalStateException("semSignal sobre " + describe() + " que ya está en su máximo ("
                        + maxValue + "): falta su semWait");
            }
            value++;
            Pcb next = null;
            while (value <= 0) {                      // por la convención, si value <= 0 hay alguien
                Pcb candidate = waiting.dequeue();
                if (candidate.getState() != ProcessState.TERMINATED) {
                    next = candidate;
                    break;
                }
                // Lo terminaron sin removeWaiting (falla del kernel): se descarta su espera y
                // el turno pasa al siguiente. Solo se LEE su estado, no se cambia (opción B).
                value++;
            }
            if (type == SemaphoreType.BINARY) {
                holder = next;                        // el turno pasa al despertado (o queda libre)
            }
            return next;
        } finally {
            javaMutex.release();
        }
    }

    /**
     * Saca a un proceso de la cola sin despertarlo. Lo usa el kernel ANTES de
     * terminar por deadline a un proceso que espera aquí. Se devuelve su
     * decremento para que el valor siga contando bien a los que esperan.
     *
     * @return true si estaba en la cola; false si no estaba (por ejemplo, ya lo
     *         despertaron y su aviso viaja: entonces el kernel debe hacer semSignal(pcb))
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

    public int getMaxValue() {
        return maxValue;
    }

    public int getValue() {
        javaMutex.acquireUninterruptibly();
        try {
            return value;
        } finally {
            javaMutex.release();
        }
    }

    /** Solo en el mutex: el proceso que está en la región crítica, o null si está libre. */
    public Pcb getHolder() {
        javaMutex.acquireUninterruptibly();
        try {
            return holder;
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

    /** Ej. "vacíos=2" o, en el mutex ocupado, "mutex=0 (P3-H1)". */
    @Override
    public String toString() {
        javaMutex.acquireUninterruptibly();
        try {
            return name + "=" + value + (holder == null ? "" : " (" + holder + ")");
        } finally {
            javaMutex.release();
        }
    }
}
