package com.unimet.avilaos.cluster;

import com.unimet.avilaos.config.ComputerConfig;
import com.unimet.avilaos.config.DiskConfig;
import com.unimet.avilaos.events.EventLog;
import com.unimet.avilaos.hardware.BlockMap;
import com.unimet.avilaos.hardware.ClockListener;
import com.unimet.avilaos.hardware.Cpu;
import com.unimet.avilaos.hardware.Disk;
import com.unimet.avilaos.hardware.IoDevice;
import com.unimet.avilaos.hardware.MainMemory;
import com.unimet.avilaos.metrics.ComputerMetrics;
import com.unimet.avilaos.metrics.SystemMetrics;
import com.unimet.avilaos.os.Interconnect;
import com.unimet.avilaos.os.Kernel;
import com.unimet.avilaos.os.Storage;
import com.unimet.avilaos.os.disk.DiskScheduler;
import com.unimet.avilaos.os.fs.FileSystem;
import com.unimet.avilaos.os.memory.MemoryManager;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.scheduling.Scheduler;

/**
 * Un computador del clúster: el lugar donde se ensamblan el hardware (CPU, RAM, E/S,
 * disco) y su sistema operativo (memoria, planificador, planificador de disco, sistema
 * de archivos, kernel). No toma decisiones: las toma
 * el kernel. Todos los computadores son instancias de esta misma clase (lo exige el
 * enunciado), así que agregar uno más es crear otra instancia.
 *
 * El reloj global le da un hilo de Java propio y en cada tick ejecuta el ciclo en este
 * orden (ver el diseño del Kernel):
 *   1. io.onTick         el dispositivo del P1 avanza; si termina, deja la interrupción en la CPU
 *      disk.onTick       el disco avanza un ciclo de trabajo; si termina una solicitud,
 *                        deja la interrupción DISK_COMPLETION en la CPU
 *   2. kernel.beforeCpu  solicitudes, llegadas, despertares, admisión, expropiación y plan del ciclo
 *   3. cpu.onTick        ejecuta (MAR ← PC, PC + 1) o trabajo del SO; atiende interrupciones
 *   4. kernel.afterCpu   efecto de lo ejecutado, latencias de red, deadlines y fin de procesos
 *   5. metrics.recordCycle
 *
 * El disco implementa ClockListener como los demás componentes que reaccionan al reloj,
 * pero no se registra solo en el GlobalClock: tendría su propio hilo y su ciclo podría
 * caer antes o después que el de la CPU. El Computer le reenvía el tick en un orden fijo
 * (antes que la CPU, igual que el dispositivo de E/S), así que el fin de una solicitud se
 * atiende siempre en el mismo ciclo y la simulación se puede repetir.
 *
 * El planificador de disco y el sistema de archivos usan el MISMO mapa de bloques que el
 * disco (zona de archivos + swap, ver DiskConfig).
 *
 * @author victor1102
 * @author NicollPinzon
 */
public class Computer implements ClockListener {

    private final int id;
    private final Cpu cpu;
    private final IoDevice io;
    private final Disk disk;
    private final MainMemory ram;
    private final MemoryManager memory;
    private final Kernel kernel;
    private final ComputerMetrics metrics;

    /** Dueño de la raíz del sistema de archivos (el administrador). */
    public static final String ROOT_OWNER = "admin";

    /** Procesos enviados a este computador. Solo lo escribe el hilo que crea procesos (GUI o carga inicial). */
    private volatile int submitted;

    /**
     * @param config        id, RAM y política inicial
     * @param diskConfig    bloques, swap, política de disco y cabezal inicial
     * @param quantum       quantum inicial de Round Robin
     * @param log           log de eventos compartido por el clúster
     * @param systemMetrics métricas del sistema completo (para los buffers)
     * @param network       el clúster visto desde el kernel (buffers, despertares, latencia)
     */
    public Computer(ComputerConfig config, DiskConfig diskConfig, int quantum, EventLog log,
            SystemMetrics systemMetrics, Interconnect network) {
        if (config == null || diskConfig == null) {
            throw new IllegalArgumentException("El computador necesita su configuración y la de su disco");
        }
        this.id = config.id();
        this.cpu = new Cpu();
        this.io = new IoDevice(cpu);
        this.ram = new MainMemory(config.ramSize());
        this.memory = new MemoryManager(id, ram);
        Scheduler scheduler = new Scheduler(config.initialPolicy().newPolicy(), quantum);
        BlockMap blocks = new BlockMap(diskConfig.fileBlocks(), diskConfig.swapBlocks());
        DiskScheduler diskScheduler = new DiskScheduler(diskConfig.fileBlocks(), diskConfig.swapBlocks(),
                diskConfig.initialPolicy());
        this.disk = new Disk(cpu, blocks, diskScheduler, diskConfig.initialHead(), diskConfig.initialDirection(),
                diskConfig.positionsPerCycle());
        FileSystem fileSystem = new FileSystem(blocks, ROOT_OWNER);
        this.metrics = new ComputerMetrics(id, cpu);
        this.kernel = new Kernel(id, cpu, io, memory, scheduler, log, metrics, systemMetrics, network,
                new Storage(disk, diskScheduler, fileSystem));
        cpu.setInterruptHandler(kernel);
    }

    @Override
    public void onTick(long cycle) {
        io.onTick(cycle);
        disk.onTick(cycle);
        kernel.beforeCpu(cycle);
        cpu.onTick(cycle);
        kernel.afterCpu(cycle);
        metrics.recordCycle(cycle);
    }

    /** Entrega un proceso nuevo al kernel (entra al inicio del próximo ciclo). */
    public void submit(Pcb pcb) {
        kernel.submit(pcb);
        submitted++;
    }

    /** Procesos que siguen en el sistema (enviados y no terminados): la carga para el balanceo. */
    public int getLoad() {
        return (int) (submitted - metrics.getCompletedProcesses());
    }

    /** RAM que les queda a los procesos (total menos los buffers que viven aquí). */
    public int getProcessMemoryLimit() {
        return memory.getProcessMemoryLimit();
    }

    public int getId() {
        return id;
    }

    public Cpu getCpu() {
        return cpu;
    }

    public IoDevice getIo() {
        return io;
    }

    public Disk getDisk() {
        return disk;
    }

    public DiskScheduler getDiskScheduler() {
        return kernel.getStorage().diskScheduler();
    }

    public FileSystem getFileSystem() {
        return kernel.getStorage().fileSystem();
    }

    public MainMemory getRam() {
        return ram;
    }

    public MemoryManager getMemoryManager() {
        return memory;
    }

    public Kernel getKernel() {
        return kernel;
    }

    public ComputerMetrics getMetrics() {
        return metrics;
    }

    @Override
    public String toString() {
        return "Computador " + id;
    }
}
