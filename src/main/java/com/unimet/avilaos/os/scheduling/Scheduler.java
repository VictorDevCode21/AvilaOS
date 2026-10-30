package com.unimet.avilaos.os.scheduling;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.process.ProcessState;
import java.util.concurrent.Semaphore;

/**
 * Planificador a corto plazo de UN computador: el MECANISMO.
 * Mantiene la única cola de listos del computador, selecciona al siguiente y
 * consulta a la política (SchedulingPolicy) para ordenar y expropiar. No sabe
 * qué política tiene: cambiarla no requiere tocar esta clase.
 *
 * No cambia estados de los procesos (eso lo hace el kernel): solo maneja la cola.
 *
 * La cola la usan varios hilos: el de este computador, el de OTRO computador
 * cuando un semSignal remoto despierta a uno de nuestros procesos, y la GUI
 * (lectura). Por eso todo acceso pasa por un mutex de Java.
 *
 * @author donatovodola
 */
public class Scheduler {

    private final LinkedList<Pcb> readyQueue = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1, true);
    private volatile SchedulingPolicy policy;
    private volatile int quantum;

    /**
     * @param policy  política inicial
     * @param quantum ciclos por turno para Round Robin (mayor que 0)
     */
    public Scheduler(SchedulingPolicy policy, int quantum) {
        if (policy == null) {
            throw new IllegalArgumentException("El planificador necesita una política");
        }
        this.policy = policy;
        setQuantum(quantum);
    }

    // ---------- Cola de listos ----------

    /**
     * Inserta un proceso en listos, en la posición que dicta la política.
     * Sirve igual para uno recién admitido, uno despertado o uno expropiado.
     */
    public void addReady(Pcb pcb) {
        if (pcb == null || pcb.getState() != ProcessState.READY) {
            throw new IllegalArgumentException("Solo se encolan en listos procesos en estado Listo");
        }
        mutex.acquireUninterruptibly();
        try {
            readyQueue.insertSorted(pcb, policy.readyOrder());
        } finally {
            mutex.release();
        }
    }

    /** Saca y devuelve el siguiente a ejecutar (el primero de la cola); null si no hay. */
    public Pcb selectNext() {
        mutex.acquireUninterruptibly();
        try {
            return readyQueue.removeFirst();
        } finally {
            mutex.release();
        }
    }

    /** Saca a un proceso de listos sin ejecutarlo (ej. se le venció el deadline). */
    public boolean remove(Pcb pcb) {
        mutex.acquireUninterruptibly();
        try {
            return readyQueue.remove(pcb);
        } finally {
            mutex.release();
        }
    }

    // ---------- Expropiación ----------

    /**
     * ¿Hay que sacar de la CPU al que corre? Lo pregunta el kernel en cada ciclo.
     * Por quantum (Round Robin) o porque el primero de listos tiene más derecho
     * (EDF, prioridades).
     */
    public boolean shouldPreempt(Pcb running) {
        if (running == null) {
            return false;
        }
        SchedulingPolicy current = policy;
        if (current.isQuantumExpired(running, quantum)) {
            return true;
        }
        Pcb head;
        mutex.acquireUninterruptibly();
        try {
            head = readyQueue.peekFirst();
        } finally {
            mutex.release();
        }
        return head != null && current.shouldPreempt(running, head);
    }

    // ---------- Configuración en tiempo de ejecución ----------

    /**
     * Cambia la política y REORDENA la cola de inmediato, para que la GUI muestre
     * el nuevo orden en el mismo momento (lo exige el enunciado).
     */
    public void setPolicy(SchedulingPolicy newPolicy) {
        if (newPolicy == null) {
            throw new IllegalArgumentException("La política no puede ser null");
        }
        mutex.acquireUninterruptibly();
        try {
            policy = newPolicy;
            readyQueue.sort(newPolicy.readyOrder());
        } finally {
            mutex.release();
        }
    }

    public void setQuantum(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor que 0");
        }
        this.quantum = quantum;
    }

    // ---------- Consultas (GUI, métricas, balanceo de carga) ----------

    public SchedulingPolicy getPolicy() {
        return policy;
    }

    public PolicyType getPolicyType() {
        return policy.getType();
    }

    public int getQuantum() {
        return quantum;
    }

    public int getReadyCount() {
        mutex.acquireUninterruptibly();
        try {
            return readyQueue.size();
        } finally {
            mutex.release();
        }
    }

    /** Copia de la cola en su orden actual, para que la GUI la recorra sin tocar la original. */
    public LinkedList<Pcb> getReadySnapshot() {
        mutex.acquireUninterruptibly();
        try {
            return readyQueue.copy();
        } finally {
            mutex.release();
        }
    }
}
