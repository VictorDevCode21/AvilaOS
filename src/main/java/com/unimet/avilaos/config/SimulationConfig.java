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
    public static final long MIN_CYCLE_MS = 10;
    public static final long MAX_CYCLE_MS = 10_000;
    public static final int MAX_QUANTUM = 100;
    public static final int MAX_NETWORK_LATENCY = 1_000;

    private final long cycleDurationMs;
    private final int quantum;
    private final int networkLatency;
    private final LinkedList<ComputerConfig> computers = new LinkedList<>();
    private final LinkedList<BufferConfig> buffers = new LinkedList<>();
    private final LinkedList<ProcessConfig> processes = new LinkedList<>();

    /**
     * @param cycleDurationMs duración de un ciclo en ms (MIN_CYCLE_MS a MAX_CYCLE_MS)
     * @param quantum         quantum de Round Robin en ciclos (1 a MAX_QUANTUM)
     * @param networkLatency  ciclos extra por acceso remoto a un buffer (0 a MAX_NETWORK_LATENCY)
     */
    public SimulationConfig(long cycleDurationMs, int quantum, int networkLatency) {
        ConfigText.requireRange(cycleDurationMs, MIN_CYCLE_MS, MAX_CYCLE_MS, "La duración del ciclo (ms)");
        ConfigText.requireRange(quantum, 1, MAX_QUANTUM, "El quantum");
        ConfigText.requireRange(networkLatency, 0, MAX_NETWORK_LATENCY, "La latencia de red (ciclos)");
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
        checkBuffer(buffer);
        buffers.addLast(buffer);
    }

    /**
     * Comprueba que el buffer se pueda agregar, SIN agregarlo. La GUI lo usa antes de crear
     * el buffer en la simulación, para que la configuración y el clúster nunca discrepen.
     *
     * Reglas: el anfitrión existe, el nombre no se repite, cabe junto a los otros buffers
     * del anfitrión y, como su RAM no se libera nunca, ningún proceso de la configuración
     * queda sin poder entrar (si no, Reiniciar fallaría al volver a cargar esa configuración).
     *
     * @throws IllegalArgumentException con el motivo, si no se puede agregar
     */
    public void checkBuffer(BufferConfig buffer) {
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
        // Los buffers ocupan su RAM durante TODA la simulación: se suman todos los del anfitrión.
        int used = bufferMemoryOn(host.id());
        if (used + buffer.memorySize() > host.ramSize()) {
            throw new IllegalArgumentException("El buffer " + buffer.name() + " (" + buffer.memorySize()
                    + " MB) no cabe en el computador " + host.id() + ": tiene " + host.ramSize()
                    + " MB y sus buffers ya ocupan " + used + " MB");
        }
        // Ningún proceso puede quedar sin espacio para siempre por culpa del buffer nuevo.
        LinkedList<ProcessConfig> all = processes.copy();
        while (!all.isEmpty()) {
            ProcessConfig process = all.removeFirst();
            int limit = process.isAutomatic()
                    ? maxProcessMemoryLimitWith(host.id(), buffer.memorySize())
                    : processMemoryLimit(process.computerId())
                            - (process.computerId() == host.id() ? buffer.memorySize() : 0);
            if (process.memorySize() > limit) {
                throw new IllegalArgumentException("El buffer " + buffer.name() + " (" + buffer.memorySize()
                        + " MB) dejaría al proceso " + process.name() + " (" + process.memorySize()
                        + " MB) sin poder entrar nunca: "
                        + (process.isAutomatic()
                                ? "ningún computador tendría más de " + limit + " MB para procesos"
                                : "el computador " + process.computerId() + " quedaría con " + limit
                                        + " MB para procesos"));
            }
        }
    }

    public void addProcess(ProcessConfig process) {
        if (process == null) {
            throw new IllegalArgumentException("El proceso no puede ser null");
        }
        if (!process.isAutomatic() && findComputer(process.computerId()) == null) {
            throw new IllegalArgumentException("El computador " + process.computerId()
                    + " del proceso " + process.name() + " no existe");
        }
        requireFits(process);
        if (process.type().usesBuffer()) {
            BufferConfig buffer = findBuffer(process.bufferName());
            if (buffer == null) {
                throw new IllegalArgumentException("El buffer " + process.bufferName()
                        + " del proceso " + process.name() + " no existe");
            }
            if (!buffer.name().equals(process.bufferName())) {
                // "b1" → "B1": el proceso queda con el nombre exacto del buffer
                process = new ProcessConfig(process.name(), process.type(), process.priority(),
                        process.memorySize(), process.instructions(), process.deadline(), process.computerId(),
                        process.ioInterval(), process.ioDuration(), buffer.name(),
                        process.operationInterval(), process.itemsRequired());
            }
        }
        processes.addLast(process);
    }

    /**
     * Revisión final antes de arrancar o guardar. Vuelve a comprobar que cada proceso
     * quepa, por si se agregó un buffer después de él (desde la GUI el orden es libre).
     */
    public void validate() {
        if (computers.size() < MIN_COMPUTERS) {
            throw new IllegalArgumentException("Se necesitan al menos " + MIN_COMPUTERS + " computadores");
        }
        LinkedList<ProcessConfig> all = processes.copy();
        while (!all.isEmpty()) {
            requireFits(all.removeFirst());
        }
    }

    // ---------- Memoria disponible para procesos ----------

    /** MB que ocupan los buffers que viven en ese computador. */
    public int bufferMemoryOn(int computerId) {
        int total = 0;
        LinkedList<BufferConfig> all = buffers.copy();
        while (!all.isEmpty()) {
            BufferConfig b = all.removeFirst();
            if (b.hostComputerId() == computerId) {
                total += b.memorySize();
            }
        }
        return total;
    }

    /** RAM que les queda a los procesos en ese computador (la de los buffers no se libera nunca). */
    public int processMemoryLimit(int computerId) {
        ComputerConfig computer = findComputer(computerId);
        return computer == null ? 0 : computer.ramSize() - bufferMemoryOn(computerId);
    }

    /** La mayor RAM disponible para procesos entre todos los computadores (para "Automático"). */
    public int maxProcessMemoryLimit() {
        int best = 0;
        for (int id = 1; id <= computers.size(); id++) {
            best = Math.max(best, processMemoryLimit(id));
        }
        return best;
    }

    /** Como maxProcessMemoryLimit(), pero suponiendo un buffer extra de extraMb en el computador hostId. */
    private int maxProcessMemoryLimitWith(int hostId, int extraMb) {
        int best = 0;
        for (int id = 1; id <= computers.size(); id++) {
            best = Math.max(best, processMemoryLimit(id) - (id == hostId ? extraMb : 0));
        }
        return best;
    }

    /** Un proceso que nunca cabría se quedaría para siempre en Nuevos: se rechaza. */
    private void requireFits(ProcessConfig process) {
        int limit = process.isAutomatic() ? maxProcessMemoryLimit() : processMemoryLimit(process.computerId());
        if (process.memorySize() > limit) {
            throw new IllegalArgumentException("El proceso " + process.name() + " (" + process.memorySize()
                    + " MB) nunca cabría: " + (process.isAutomatic()
                            ? "ningún computador tiene más de " + limit + " MB libres para procesos"
                            : "el computador " + process.computerId() + " solo tiene " + limit
                                    + " MB libres para procesos (descontando sus buffers)"));
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
        LinkedList<ComputerConfig> all = computers.copy();
        while (!all.isEmpty()) {
            ComputerConfig c = all.removeFirst();
            if (c.id() == id) {
                return c;
            }
        }
        return null;
    }

    /** Busca sin distinguir mayúsculas ni espacios (BufferConfig.sameName). */
    public BufferConfig findBuffer(String name) {
        LinkedList<BufferConfig> all = buffers.copy();
        while (!all.isEmpty()) {
            BufferConfig b = all.removeFirst();
            if (BufferConfig.sameName(b.name(), name)) {
                return b;
            }
        }
        return null;
    }
}
