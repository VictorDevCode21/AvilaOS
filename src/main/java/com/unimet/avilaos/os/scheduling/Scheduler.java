package com.unimet.avilaos.os.scheduling;

import com.unimet.avilaos.datastructures.ElementComparator;
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
 * No cambia estados de los procesos (eso lo hace el kernel): solo maneja la cola
 * y la contabilidad del turno (número de llegada a listos y quantum).
 *
 * Desempate: cada proceso recibe un número de llegada (readySequence) al entrar
 * a listos. Si la política empata, gana el que llegó antes. Así FCFS y Round
 * Robin quedan en orden de llegada aunque antes se haya usado otra política.
 *
 * Hilos: lo modifica SOLO el hilo de su computador (el kernel). Un despertar
 * remoto llega como mensaje al computador dueño, que lo procesa en su propio
 * hilo; ningún computador toca la cola de otro. Los cambios de política o
 * quantum que pide la GUI los aplica el kernel al inicio de un ciclo. La GUI
 * solo LEE (getReadySnapshot), por eso igual todo acceso pasa por un mutex.
 *
 * @author donatovodola
 */
public class Scheduler {

    private final LinkedList<Pcb> readyQueue = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1, true);
    private volatile SchedulingPolicy policy;
    private ElementComparator<Pcb> order;      // política + desempate por llegada (protegido por mutex)
    private volatile int quantum;
    private long nextSequence = 1;             // protegido por mutex

    /**
     * @param policy  política inicial
     * @param quantum ciclos por turno para Round Robin (mayor que 0)
     */
    public Scheduler(SchedulingPolicy policy, int quantum) {
        if (policy == null) {
            throw new IllegalArgumentException("El planificador necesita una política");
        }
        this.policy = policy;
        this.order = withArrivalTieBreak(policy);
        this.quantum = validQuantum(quantum);
    }

    // ---------- Cola de listos ----------

    /**
     * Inserta un proceso en listos, en la posición que dicta la política.
     * Sirve igual para uno recién admitido, uno despertado o uno expropiado.
     *
     * @throws IllegalArgumentException si es null o no está en estado Listo
     * @throws IllegalStateException    si ya estaba en la cola (se despacharía dos veces)
     */
    public void addReady(Pcb pcb) {
        if (pcb == null || pcb.getState() != ProcessState.READY) {
            throw new IllegalArgumentException("Solo se encolan en listos procesos en estado Listo");
        }
        mutex.acquireUninterruptibly();
        try {
            if (readyQueue.contains(pcb)) {
                throw new IllegalStateException(pcb + " ya está en la cola de listos");
            }
            pcb.setReadySequence(nextSequence++);
            readyQueue.insertSorted(pcb, order);
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
     *
     * Solo se expropia si alguien espera en listos: sacar al proceso para volver
     * a elegirlo sería un cambio de contexto inútil. Si en Round Robin se le acabó
     * el quantum y la cola está vacía, se le da un quantum nuevo y sigue
     * (como el timer real, que se rearma cuando no hay nadie más).
     */
    public boolean shouldPreempt(Pcb running) {
        if (running == null) {
            return false;
        }
        SchedulingPolicy current = policy;
        boolean quantumExpired = current.isQuantumExpired(running, quantum);
        Pcb head;
        mutex.acquireUninterruptibly();
        try {
            head = readyQueue.peekFirst();
        } finally {
            mutex.release();
        }
        if (head == null) {
            if (quantumExpired) {
                running.resetQuantum();          // nadie espera: turno nuevo
            }
            return false;
        }
        return quantumExpired || current.shouldPreempt(running, head);
    }

    // ---------- Configuración en tiempo de ejecución ----------

    /**
     * Cambia la política y REORDENA la cola de inmediato, para que la GUI muestre
     * el nuevo orden en el mismo momento (lo exige el enunciado).
     *
     * El proceso en ejecución empieza un turno nuevo: los ciclos que llevaba con
     * la política anterior no cuentan para el quantum de la nueva.
     *
     * Debe llamarlo el hilo del computador (el kernel), al inicio de un ciclo.
     *
     * @param running proceso en ejecución, o null si la CPU está libre
     */
    public void setPolicy(SchedulingPolicy newPolicy, Pcb running) {
        if (newPolicy == null) {
            throw new IllegalArgumentException("La política no puede ser null");
        }
        mutex.acquireUninterruptibly();
        try {
            policy = newPolicy;
            order = withArrivalTieBreak(newPolicy);
            readyQueue.sort(order);
        } finally {
            mutex.release();
        }
        if (running != null) {
            running.resetQuantum();
        }
    }

    public void setQuantum(int quantum) {
        this.quantum = validQuantum(quantum);
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

    // ---------- Internos ----------

    private static int validQuantum(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor que 0");
        }
        return quantum;
    }

    /** Orden de la política; si empata, gana el que llegó antes a listos. */
    private static ElementComparator<Pcb> withArrivalTieBreak(SchedulingPolicy policy) {
        ElementComparator<Pcb> byPolicy = policy.readyOrder();
        return (a, b) -> {
            int result = byPolicy.compare(a, b);
            return result != 0 ? result : Long.compare(a.getReadySequence(), b.getReadySequence());
        };
    }
}
