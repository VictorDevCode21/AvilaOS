package com.unimet.avilaos.config;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.os.scheduling.PolicyType;

/**
 * Todos los parámetros de una simulación (enunciado, sección 7):
 * duración del ciclo, quantum, latencia de red, computadores con su RAM y
 * política inicial, y la carga inicial de buffers y procesos.
 *
 * Se arma en este orden: computadores → buffers → procesos, porque cada paso
 * valida contra el anterior (un buffer necesita que exista su anfitrión; un
 * productor necesita que exista su buffer). ConfigRepository respeta ese orden
 * aunque el archivo venga desordenado.
 *
 * Solo la usa el hilo de la GUI al configurar y al arrancar, por eso no lleva mutex.
 * Los getters devuelven copias para que nadie la modifique por fuera.
 *
 * @author donatovodola
 */
public class SimulationConfig {

    public static final int MIN_COMPUTERS = 2;

    private final long cycleDurationMs;
    private final int quantum;
    private final int networkLatency;
    private final LinkedList<ComputerConfig> computers = new LinkedList<>();
    private final LinkedList<BufferConfig> buffers = new LinkedList<>();
    private final LinkedList<ProcessConfig> processes = new LinkedList<>();

    /**
     * @param cycleDurationMs duración de un ciclo en ms (mayor que 0)
     * @param quantum         quantum de Round Robin en ciclos (mayor que 0)
     * @param networkLatency  ciclos extra por acceso remoto a un buffer (0 o más)
     */
    public SimulationConfig(long cycleDurationMs, int quantum, int networkLatency) {
        if (cycleDurationMs <= 0) {
            throw new IllegalArgumentException("La duración del ciclo debe ser mayor que 0 ms");
        }
        if (quantum <= 0) {
            throw new IllegalArgumentException("El quantum debe ser mayor que 0");
        }
        if (networkLatency < 0) {
            throw new IllegalArgumentException("La latencia de red no puede ser negativa");
        }
        this.cycleDurationMs = cycleDurationMs;
        this.quantum = quantum;
        this.networkLatency = networkLatency;
    }

    // ---------- Armado (cada paso valida contra lo anterior) ----------

    /** Agrega el siguiente computador; su número es la cantidad actual + 1. */
    public ComputerConfig addComputer(int ramSize, PolicyType initialPolicy) {
        ComputerConfig computer = new ComputerConfig(computers.size() + 1, ramSize, initialPolicy);
        computers.addLast(computer);
        return computer;
    }

    public void addBuffer(BufferConfig buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("El buffer no puede ser null");
        }
        ComputerConfig host = findComputer(buffer.hostComputerId());
        if (host == null) {
            throw new IllegalArgumentException("El anfitrión del buffer " + buffer.name()
                    + " (computador " + buffer.hostComputerId() + ") no existe");
        }
        if (findBuffer(buffer.name()) != null) {
            throw new IllegalArgumentException("Ya existe un buffer llamado " + buffer.name());
        }
        if (buffer.memorySize() > host.ramSize()) {
            throw new IllegalArgumentException("El buffer " + buffer.name() + " (" + buffer.memorySize()
                    + " MB) no cabe en la RAM del computador " + host.id() + " (" + host.ramSize() + " MB)");
        }
        buffers.addLast(buffer);
    }

    public void addProcess(ProcessConfig process) {
        if (process == null) {
            throw new IllegalArgumentException("El proceso no puede ser null");
        }
        if (!process.isAutomatic()) {
            ComputerConfig computer = findComputer(process.computerId());
            if (computer == null) {
                throw new IllegalArgumentException("El computador " + process.computerId()
                        + " del proceso " + process.name() + " no existe");
            }
            // Si nunca cabe, se quedaría para siempre en la cola de nuevos.
            if (process.memorySize() > computer.ramSize()) {
                throw new IllegalArgumentException("El proceso " + process.name() + " (" + process.memorySize()
                        + " MB) nunca cabría en la RAM del computador " + computer.id()
                        + " (" + computer.ramSize() + " MB)");
            }
        }
        if (process.type().usesBuffer() && findBuffer(process.bufferName()) == null) {
            throw new IllegalArgumentException("El buffer " + process.bufferName()
                    + " del proceso " + process.name() + " no existe");
        }
        processes.addLast(process);
    }

    /** Revisión final antes de arrancar o guardar. */
    public void validate() {
        if (computers.size() < MIN_COMPUTERS) {
            throw new IllegalArgumentException("Se necesitan al menos " + MIN_COMPUTERS + " computadores");
        }
    }

    // ---------- Configuración por defecto ----------

    /** 2 computadores sin carga inicial: punto de partida si no hay archivo. */
    public static SimulationConfig defaults() {
        SimulationConfig config = new SimulationConfig(500, 3, 2);
        config.addComputer(1024, PolicyType.ROUND_ROBIN);
        config.addComputer(1024, PolicyType.FCFS);
        return config;
    }

    // ---------- Consultas ----------

    public long getCycleDurationMs() {
        return cycleDurationMs;
    }

    public int getQuantum() {
        return quantum;
    }

    public int getNetworkLatency() {
        return networkLatency;
    }

    public int getComputerCount() {
        return computers.size();
    }

    public LinkedList<ComputerConfig> getComputers() {
        return computers.copy();
    }

    public LinkedList<BufferConfig> getBuffers() {
        return buffers.copy();
    }

    public LinkedList<ProcessConfig> getProcesses() {
        return processes.copy();
    }

    public ComputerConfig findComputer(int id) {
        for (int i = 0; i < computers.size(); i++) {
            if (computers.get(i).id() == id) {
                return computers.get(i);
            }
        }
        return null;
    }

    public BufferConfig findBuffer(String name) {
        for (int i = 0; i < buffers.size(); i++) {
            if (buffers.get(i).name().equals(name)) {
                return buffers.get(i);
            }
        }
        return null;
    }
}
