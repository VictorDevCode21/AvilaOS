package com.unimet.avilaos.os.memory;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.MainMemory;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.process.ProcessState;
import java.util.concurrent.Semaphore;

/**
 * Administrador de memoria de UN computador: planificador a largo plazo.
 *
 * No hay memoria virtual en el Proyecto 1: un proceso entra a la RAM completo
 * o espera en la cola de Nuevos. Cada ciclo el kernel llama admit(), así que
 * cuando se libera memoria los procesos que esperan se reintentan solos.
 *
 * Política de admisión: se recorre la cola de Nuevos en orden de llegada y se
 * admite a TODO el que quepa (un proceso grande que no cabe no bloquea a los
 * pequeños que vienen detrás). Un proceso grande podría esperar mucho, pero su
 * deadline sigue corriendo en Nuevos y el kernel lo termina si se vence.
 *
 * Los buffers que viven en este computador también ocupan su RAM, y la ocupan
 * durante toda la simulación: se reservan con reserveBuffer() en la MISMA
 * MainMemory, así la barra de memoria de la GUI los incluye y un proceso que
 * nunca podría caber se rechaza al crearlo (misma regla que SimulationConfig).
 *
 * Este administrador NO cambia estados ni escribe el log: eso lo hace el kernel
 * (un solo lugar para las transiciones). Aquí solo se decide quién cabe.
 *
 * Concurrencia: lo usa el hilo del computador (admit, release) y puede usarlo
 * el hilo de Swing (submit al crear procesos, consultas). Orden fijo de
 * candados: primero el mutex de este administrador, luego el de MainMemory.
 * MainMemory nunca llama de vuelta aquí, así que no puede haber deadlock.
 *
 * @author victor1102
 */
public class MemoryManager {

    private final int computerId;
    private final MainMemory memory;
    private final LinkedList<Pcb> newQueue = new LinkedList<>();   // esperan RAM (estado NEW)
    private final LinkedList<Pcb> resident = new LinkedList<>();   // tienen su RAM reservada
    private final Semaphore mutex = new Semaphore(1, true);
    private int bufferMemory;                                      // MB reservados para buffers (no se liberan)

    /**
     * @param computerId computador al que pertenece (1 o mayor)
     * @param memory     RAM de ese computador
     */
    public MemoryManager(int computerId, MainMemory memory) {
        if (computerId < 1) {
            throw new IllegalArgumentException("El computador debe ser 1 o mayor");
        }
        if (memory == null) {
            throw new IllegalArgumentException("El administrador de memoria necesita la RAM del computador");
        }
        this.computerId = computerId;
        this.memory = memory;
    }

    // ---------- Lo usa el kernel ----------

    /**
     * Un proceso recién creado entra a la cola de Nuevos.
     *
     * @throws IllegalArgumentException si es null, no está en NEW, es de otro
     *                                  computador o no cabría nunca (más grande que
     *                                  la RAM que les queda a los procesos)
     * @throws IllegalStateException    si ya fue enviado antes
     */
    public void submit(Pcb pcb) {
        if (pcb == null) {
            throw new IllegalArgumentException("El proceso no puede ser null");
        }
        if (pcb.getComputerId() != computerId) {
            throw new IllegalArgumentException(pcb + " es del computador " + pcb.getComputerId()
                    + ", no del computador " + computerId);
        }
        if (pcb.getState() != ProcessState.NEW) {
            throw new IllegalArgumentException(pcb + " debe estar en Nuevo para pedir memoria");
        }
        mutex.acquireUninterruptibly();
        try {
            int limit = memory.getTotalSize() - bufferMemory;
            if (pcb.getMemorySize() > limit) {
                throw new IllegalArgumentException(pcb + " necesita " + pcb.getMemorySize()
                        + " MB y el computador " + computerId + " solo tiene " + limit
                        + " MB para procesos (descontando sus buffers)");
            }
            if (newQueue.contains(pcb) || resident.contains(pcb)) {
                throw new IllegalStateException(pcb + " ya fue enviado al administrador de memoria");
            }
            newQueue.addLast(pcb);
        } finally {
            mutex.release();
        }
    }

    /**
     * Admite, en orden de llegada, a todos los procesos de Nuevos que quepan.
     * Les reserva la RAM y los saca de la cola. El kernel debe pasarlos a Listo.
     *
     * Defensa: si en la cola quedó un proceso que ya no está en Nuevo (por ejemplo,
     * el kernel lo terminó por deadline sin llamar removeFromNew), se descarta sin
     * reservarle RAM. Solo se LEE su estado; no se cambia.
     *
     * @return los admitidos en este llamado (vacía si ninguno cupo)
     */
    public LinkedList<Pcb> admit() {
        LinkedList<Pcb> admitted = new LinkedList<>();
        mutex.acquireUninterruptibly();
        try {
            int pending = newQueue.size();
            for (int i = 0; i < pending; i++) {          // se recorre una vez, sin get(i)
                Pcb pcb = newQueue.removeFirst();
                if (pcb.getState() != ProcessState.NEW) {
                    continue;                            // ya no espera memoria: se descarta
                }
                if (memory.allocate(pcb.getMemorySize())) {
                    resident.addLast(pcb);
                    admitted.addLast(pcb);
                } else {
                    newQueue.addLast(pcb);               // no cupo: vuelve al final, mismo orden
                }
            }
        } finally {
            mutex.release();
        }
        return admitted;
    }

    /**
     * Libera la RAM de un proceso que terminó (a tiempo o por deadline).
     *
     * @throws IllegalStateException si el proceso no tenía RAM reservada
     *                               (se liberaría dos veces: error del kernel)
     */
    public void release(Pcb pcb) {
        mutex.acquireUninterruptibly();
        try {
            if (!resident.remove(pcb)) {
                throw new IllegalStateException(pcb + " no tiene memoria reservada");
            }
            memory.free(pcb.getMemorySize());
        } finally {
            mutex.release();
        }
    }

    /**
     * Saca de Nuevos a un proceso que nunca llegó a entrar a la RAM
     * (se le venció el deadline esperando). No libera nada porque no tenía RAM.
     *
     * @return true si estaba en la cola de Nuevos
     */
    public boolean removeFromNew(Pcb pcb) {
        mutex.acquireUninterruptibly();
        try {
            return newQueue.remove(pcb);
        } finally {
            mutex.release();
        }
    }

    /**
     * Reserva la RAM de un buffer que vive en este computador, para toda la simulación.
     * Un buffer no puede esperar en Nuevos como un proceso: o hay RAM libre ahora, o no se crea.
     *
     * @param memorySize MB que ocupa el buffer (mayor que 0)
     * @return true si se reservó; false si no hay RAM libre suficiente en este momento
     */
    public boolean reserveBuffer(int memorySize) {
        if (memorySize <= 0) {
            throw new IllegalArgumentException("La memoria del buffer debe ser mayor que 0");
        }
        mutex.acquireUninterruptibly();
        try {
            if (!memory.allocate(memorySize)) {
                return false;
            }
            bufferMemory += memorySize;
            return true;
        } finally {
            mutex.release();
        }
    }

    // ---------- Consultas (kernel y GUI) ----------

    /** Copia de la cola de Nuevos en orden de llegada (el kernel la usa para descontar deadlines). */
    public LinkedList<Pcb> getNewProcesses() {
        mutex.acquireUninterruptibly();
        try {
            return newQueue.copy();
        } finally {
            mutex.release();
        }
    }

    /** Copia de los procesos que tienen su RAM reservada. */
    public LinkedList<Pcb> getResidentProcesses() {
        mutex.acquireUninterruptibly();
        try {
            return resident.copy();
        } finally {
            mutex.release();
        }
    }

    public int getNewCount() {
        mutex.acquireUninterruptibly();
        try {
            return newQueue.size();
        } finally {
            mutex.release();
        }
    }

    /** MB reservados para los buffers de este computador. */
    public int getBufferMemory() {
        mutex.acquireUninterruptibly();
        try {
            return bufferMemory;
        } finally {
            mutex.release();
        }
    }

    /** RAM máxima que puede pedir un proceso aquí: la total menos la de los buffers. */
    public int getProcessMemoryLimit() {
        mutex.acquireUninterruptibly();
        try {
            return memory.getTotalSize() - bufferMemory;
        } finally {
            mutex.release();
        }
    }

    public int getComputerId() {
        return computerId;
    }

    public MainMemory getMemory() {
        return memory;
    }
}