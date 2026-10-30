package com.unimet.avilaos.cluster;

import com.unimet.avilaos.config.BufferConfig;
import com.unimet.avilaos.config.ComputerConfig;
import com.unimet.avilaos.config.ProcessConfig;
import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.events.EventLog;
import com.unimet.avilaos.hardware.GlobalClock;
import com.unimet.avilaos.metrics.SystemMetrics;
import com.unimet.avilaos.os.Interconnect;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.process.PidGenerator;
import com.unimet.avilaos.os.scheduling.PolicyType;
import com.unimet.avilaos.os.sync.BoundedBuffer;
import com.unimet.avilaos.os.sync.KernelSemaphore;
import java.util.concurrent.Semaphore;

/**
 * El sistema distribuido: N computadores (instancias de Computer), los buffers,
 * el reloj global, el log y las métricas del sistema.
 *
 * Implementa Interconnect: es "la red" que ve cada kernel. Así el kernel (capa os)
 * no importa el clúster (capa superior): inversión de dependencias, igual que
 * InterruptHandler entre el hardware y el SO.
 *
 * Hilos:
 *  - Cada computador corre en su propio hilo (el GlobalClock lo crea al registrarlo).
 *  - findBuffer lo llaman los kernels desde sus hilos y createBuffer la GUI: la lista
 *    de buffers va protegida con un semáforo.
 *  - El arreglo de computadores no cambia después de create(): se lee sin candado.
 *  - createProcess y createBuffer se llaman desde UN solo hilo (el de Swing).
 *
 * @author victor1102
 */
public class Cluster implements Interconnect {

    private final SimulationConfig config;
    private final GlobalClock clock;
    private final EventLog log = new EventLog();
    private final SystemMetrics systemMetrics = new SystemMetrics();
    private final LinkedList<BoundedBuffer> buffers = new LinkedList<>();
    private final Semaphore buffersMutex = new Semaphore(1, true);
    private final int networkLatency;
    private Computer[] computers = new Computer[0];

    private Cluster(SimulationConfig config) {
        this.config = config;
        this.clock = new GlobalClock(config.getCycleDurationMs());
        this.networkLatency = config.getNetworkLatency();
    }

    /**
     * Arma el clúster completo desde la configuración: computadores, buffers y carga
     * inicial de procesos. El reloj queda creado pero detenido (ver start()).
     * Los PID empiezan en 1 en cada simulación.
     */
    public static Cluster create(SimulationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración no puede ser null");
        }
        config.validate();
        Cluster cluster = new Cluster(config);
        cluster.build();
        return cluster;
    }

    /** Fuera del constructor: los computadores reciben "this" ya construido. */
    private void build() {
        PidGenerator.reset();
        LinkedList<ComputerConfig> computerConfigs = config.getComputers();
        computers = new Computer[computerConfigs.size()];
        for (int i = 0; i < computers.length; i++) {
            Computer computer = new Computer(computerConfigs.get(i), config.getQuantum(), log, systemMetrics, this);
            computers[i] = computer;
            systemMetrics.addComputer(computer.getMetrics());
            clock.register(computer, "Computer-" + computer.getId());
        }
        LinkedList<BufferConfig> bufferConfigs = config.getBuffers();
        for (int i = 0; i < bufferConfigs.size(); i++) {
            installBuffer(bufferConfigs.get(i));
        }
        LinkedList<ProcessConfig> processConfigs = config.getProcesses();
        for (int i = 0; i < processConfigs.size(); i++) {
            submitProcess(processConfigs.get(i));
        }
    }

    // =====================================================================
    // Control de la simulación
    // =====================================================================

    public void start() {
        clock.start();
    }

    public void pause() {
        clock.pause();
    }

    public void resume() {
        clock.resume();
    }

    /** Detiene el reloj y los hilos de los computadores. Para simular de nuevo se crea otro Cluster. */
    public void shutdown() {
        clock.stop();
    }

    public boolean isRunning() {
        return clock.isRunning();
    }

    public boolean isPaused() {
        return clock.isPaused();
    }

    public void setCycleDurationMs(long cycleDurationMs) {
        clock.setCycleDurationMs(cycleDurationMs);
    }

    /** El quantum de Round Robin es del sistema: se aplica en todos los computadores. */
    public void setQuantum(int quantum) {
        for (Computer computer : computers) {
            computer.getKernel().requestQuantum(quantum);
        }
    }

    /** Cada computador cambia su política de forma independiente (lo exige el enunciado). */
    public void setPolicy(int computerId, PolicyType policy) {
        getComputer(computerId).getKernel().requestPolicy(policy);
    }

    // =====================================================================
    // Crear procesos y buffers (también en plena ejecución)
    // =====================================================================

    /**
     * Crea el PCB y lo entrega al computador elegido (o al que decida el balanceo).
     *
     * @return el PCB creado (para que la GUI pueda informar su PID y computador)
     * @throws IllegalArgumentException si el proceso no cabe en ningún computador
     */
    public Pcb createProcess(ProcessConfig process) {
        if (process == null) {
            throw new IllegalArgumentException("El proceso no puede ser null");
        }
        return submitProcess(process);
    }

    private Pcb submitProcess(ProcessConfig process) {
        Computer target = process.isAutomatic() ? leastLoaded(process.memorySize())
                : getComputer(process.computerId());
        Pcb pcb = process.toPcb(target.getId(), clock.getCurrentCycle());
        target.submit(pcb);
        if (process.isAutomatic()) {
            log.log(clock.getCurrentCycle(), "Clúster", "asigna " + pcb + " al " + target
                    + " (el de menos procesos en el sistema)");
        }
        return pcb;
    }

    /**
     * Balanceo de carga (opcional en el enunciado): "la cola más corta". Se elige el
     * computador con menos procesos vivos (nuevos, listos, en CPU y bloqueados) entre los
     * que tienen RAM suficiente para el proceso; si empatan, el de menor número.
     * Cuenta también los que acaban de llegar y aún no entraron, así una carga inicial
     * de varios procesos automáticos se reparte en vez de caer toda en el mismo.
     */
    private Computer leastLoaded(int memorySize) {
        Computer best = null;
        for (Computer computer : computers) {
            if (memorySize <= computer.getProcessMemoryLimit()
                    && (best == null || computer.getLoad() < best.getLoad())) {
                best = computer;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("Ningún computador tiene " + memorySize + " MB libres para procesos");
        }
        return best;
    }

    /**
     * Crea un buffer en su anfitrión: reserva su RAM (que no se libera nunca) y lo
     * registra en las métricas. Los nombres son únicos en todo el clúster.
     *
     * @throws IllegalArgumentException si el nombre se repite o el anfitrión no tiene RAM libre ahora
     */
    public BoundedBuffer createBuffer(BufferConfig buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("El buffer no puede ser null");
        }
        BoundedBuffer created = installBuffer(buffer);
        log.log(clock.getCurrentCycle(), "Clúster", "crea el buffer " + created.getName() + " en el Computador "
                + created.getHostComputerId() + " (capacidad " + created.getCapacity() + ", "
                + created.getMemorySize() + " MB)");
        return created;
    }

    private BoundedBuffer installBuffer(BufferConfig buffer) {
        if (findBuffer(buffer.name()) != null) {
            throw new IllegalArgumentException("Ya existe un buffer llamado " + buffer.name());
        }
        Computer host = getComputer(buffer.hostComputerId());
        // reserveBuffer() solo ve la cola de Nuevos. Un proceso enviado con la simulación en
        // pausa sigue en las llegadas del kernel: si el buffer lo dejara sin espacio, el kernel
        // lo rechazaría al reanudar y el proceso se perdería sin aparecer en ninguna cola.
        int newLimit = host.getProcessMemoryLimit() - buffer.memorySize();
        LinkedList<Pcb> arriving = host.getKernel().getArrivalsSnapshot();
        while (!arriving.isEmpty()) {
            Pcb pcb = arriving.removeFirst();
            if (pcb.getMemorySize() > newLimit) {
                throw new IllegalArgumentException("El buffer " + buffer.name() + " dejaría a " + pcb
                        + " (" + pcb.getMemorySize() + " MB, aún por entrar al Computador " + host.getId()
                        + ") sin poder entrar nunca: el límite para procesos bajaría a " + newLimit + " MB");
            }
        }
        if (!host.getMemoryManager().reserveBuffer(buffer.memorySize())) {
            throw new IllegalArgumentException("El Computador " + host.getId() + " no tiene ahora "
                    + buffer.memorySize() + " MB libres para el buffer " + buffer.name()
                    + " (la ocupan procesos en ejecución)");
        }
        systemMetrics.registerBuffer(buffer.name());
        BoundedBuffer created = new BoundedBuffer(buffer.name(), buffer.hostComputerId(), buffer.capacity(),
                buffer.memorySize());
        buffersMutex.acquireUninterruptibly();
        try {
            buffers.addLast(created);
        } finally {
            buffersMutex.release();
        }
        return created;
    }

    // =====================================================================
    // Interconnect: lo que ve cada kernel
    // =====================================================================

    @Override
    public BoundedBuffer findBuffer(String name) {
        buffersMutex.acquireUninterruptibly();
        try {
            for (int i = 0; i < buffers.size(); i++) {
                BoundedBuffer buffer = buffers.get(i);
                if (BufferConfig.sameName(buffer.getName(), name)) {
                    return buffer;
                }
            }
            return null;
        } finally {
            buffersMutex.release();
        }
    }

    /** El despertar va al computador dueño del PCB (opción B: nadie toca PCB ajenos). */
    @Override
    public void deliverWakeup(Pcb pcb, KernelSemaphore from, long sentCycle) {
        getComputer(pcb.getComputerId()).getKernel().deliverWakeup(pcb, from, sentCycle);
    }

    @Override
    public int getNetworkLatency() {
        return networkLatency;
    }

    // =====================================================================
    // Consultas (GUI)
    // =====================================================================

    public Computer getComputer(int computerId) {
        if (computerId < 1 || computerId > computers.length) {
            throw new IllegalArgumentException("No existe el computador " + computerId);
        }
        return computers[computerId - 1];
    }

    public int getComputerCount() {
        return computers.length;
    }

    /** Procesos que siguen en el sistema en todo el clúster (suma de Computer.getLoad()). */
    public int getTotalLoad() {
        int total = 0;
        for (Computer computer : computers) {
            total += computer.getLoad();
        }
        return total;
    }

    /** Copia de la lista de buffers (para mostrarlos). */
    public LinkedList<BoundedBuffer> getBuffers() {
        buffersMutex.acquireUninterruptibly();
        try {
            return buffers.copy();
        } finally {
            buffersMutex.release();
        }
    }

    public long getCurrentCycle() {
        return clock.getCurrentCycle();
    }

    public EventLog getLog() {
        return log;
    }

    public SystemMetrics getSystemMetrics() {
        return systemMetrics;
    }

    public SimulationConfig getConfig() {
        return config;
    }
}
