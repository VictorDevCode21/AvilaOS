package com.unimet.avilaos.cluster;

import com.unimet.avilaos.config.ComputerConfig;
import com.unimet.avilaos.events.EventLog;
import com.unimet.avilaos.hardware.ClockListener;
import com.unimet.avilaos.hardware.Cpu;
import com.unimet.avilaos.hardware.IoDevice;
import com.unimet.avilaos.hardware.MainMemory;
import com.unimet.avilaos.metrics.ComputerMetrics;
import com.unimet.avilaos.metrics.SystemMetrics;
import com.unimet.avilaos.os.Interconnect;
import com.unimet.avilaos.os.Kernel;
import com.unimet.avilaos.os.memory.MemoryManager;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.scheduling.Scheduler;

/**
 * Un computador del clúster: el lugar donde se ensamblan el hardware (CPU, RAM, E/S)
 * y su sistema operativo (memoria, planificador, kernel). No toma decisiones: las toma
 * el kernel. Todos los computadores son instancias de esta misma clase (lo exige el
 * enunciado), así que agregar uno más es crear otra instancia.
 *
 * Es el único ClockListener del computador: el reloj global le da un hilo de Java propio
 * y en cada tick ejecuta el ciclo en este orden (ver el diseño del Kernel):
 *   1. io.onTick         el dispositivo avanza; si termina, deja la interrupción en la CPU
 *   2. kernel.beforeCpu  solicitudes, llegadas, despertares, admisión, expropiación y plan del ciclo
 *   3. cpu.onTick        ejecuta (MAR ← PC, PC + 1) o trabajo del SO; atiende interrupciones
 *   4. kernel.afterCpu   efecto de lo ejecutado, latencias de red, deadlines y fin de procesos
 *   5. metrics.recordCycle
 *
 * @author victor1102
 */
public class Computer implements ClockListener {

    private final int id;
    private final Cpu cpu;
    private final IoDevice io;
    private final MainMemory ram;
    private final MemoryManager memory;
    private final Kernel kernel;
    private final ComputerMetrics metrics;

    /** Procesos enviados a este computador. Solo lo escribe el hilo que crea procesos (GUI o carga inicial). */
    private volatile int submitted;

    /**
     * @param config        id, RAM y política inicial
     * @param quantum       quantum inicial de Round Robin
     * @param log           log de eventos compartido por el clúster
     * @param systemMetrics métricas del sistema completo (para los buffers)
     * @param network       el clúster visto desde el kernel (buffers, despertares, latencia)
     */
    public Computer(ComputerConfig config, int quantum, EventLog log, SystemMetrics systemMetrics,
            Interconnect network) {
        if (config == null) {
            throw new IllegalArgumentException("El computador necesita su configuración");
        }
        this.id = config.id();
        this.cpu = new Cpu();
        this.io = new IoDevice(cpu);
        this.ram = new MainMemory(config.ramSize());
        this.memory = new MemoryManager(id, ram);
        Scheduler scheduler = new Scheduler(config.initialPolicy().newPolicy(), quantum);
        this.metrics = new ComputerMetrics(id, cpu);
        this.kernel = new Kernel(id, cpu, io, memory, scheduler, log, metrics, systemMetrics, network);
        cpu.setInterruptHandler(kernel);
    }

    @Override
    public void onTick(long cycle) {
        io.onTick(cycle);
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
