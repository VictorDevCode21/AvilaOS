package com.unimet.avilaos.gui;

import com.unimet.avilaos.cluster.Cluster;
import com.unimet.avilaos.cluster.Computer;
import com.unimet.avilaos.config.BufferConfig;
import com.unimet.avilaos.config.ProcessConfig;
import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.events.LogEntry;
import com.unimet.avilaos.hardware.Cpu;
import com.unimet.avilaos.os.Kernel;
import com.unimet.avilaos.os.scheduling.PolicyType;
import javax.swing.JOptionPane;
import javax.swing.Timer;

/**
 * Une la ventana con el clúster. La ventana solo muestra y reenvía clics; el clúster
 * simula. Este controlador:
 *  - conecta los botones, los selectores de política y los spinners con el clúster,
 *  - refresca la vista con un javax.swing.Timer (REFRESH_MS) que LEE el estado:
 *    la simulación nunca espera a la GUI y la GUI nunca toca el SO.
 *
 * Todo aquí corre en el hilo de Swing. Los computadores corren en sus propios hilos;
 * lo que se lee de ellos son copias tomadas con el candado de su dueño.
 *
 * @author victor1102
 */
public final class SimulationController {

    /** Cada cuánto se refresca la pantalla (ms). Independiente de la duración del ciclo. */
    public static final int REFRESH_MS = 150;

    private final SimulationConfig config;
    private final MainWindow window;
    private final Timer refreshTimer;
    private Cluster cluster;
    private long lastLogSequence = -1;

    public SimulationController(SimulationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración no puede ser null");
        }
        this.config = config;
        this.window = new MainWindow(config.getComputerCount(), PolicyType.labels());
        this.refreshTimer = new Timer(REFRESH_MS, e -> refresh());
    }

    /** Arma la primera simulación, conecta la ventana y la muestra. Llamar desde el hilo de Swing. */
    public void show() {
        cluster = Cluster.create(config);
        connect();
        showInitialSettings();
        refresh();
        window.setVisible(true);
        refreshTimer.start();
    }

    // ---------- Conexiones (una sola vez: siempre usan el clúster actual) ----------

    private void connect() {
        window.setStartPauseAction(e -> startOrPause());
        window.setResetAction(e -> reset());
        window.setCreateProcessAction(e -> createProcess());
        window.setCreateBufferAction(e -> createBuffer());
        window.getCycleDurationSpinner().addChangeListener(e -> cluster.setCycleDurationMs(
                ((Number) window.getCycleDurationSpinner().getValue()).longValue()));
        window.getQuantumSpinner().addChangeListener(e -> cluster.setQuantum(
                ((Number) window.getQuantumSpinner().getValue()).intValue()));
        for (int id = 1; id <= window.getComputerCount(); id++) {
            int computerId = id;
            window.getComputerPanel(id).onPolicyChanged(label ->
                    cluster.setPolicy(computerId, PolicyType.fromLabel(label)));
        }
    }

    /** Pone en la vista los valores de la configuración (sin disparar cambios). */
    private void showInitialSettings() {
        window.getCycleDurationSpinner().setValue((int) config.getCycleDurationMs());
        window.setQuantum(config.getQuantum());
        for (int id = 1; id <= config.getComputerCount(); id++) {
            window.getComputerPanel(id).showPolicy(config.findComputer(id).initialPolicy().getLabel());
        }
    }

    // ---------- Acciones ----------

    private void startOrPause() {
        if (!cluster.isRunning()) {
            cluster.start();
            window.setRunning(true);
        } else if (cluster.isPaused()) {
            cluster.resume();
            window.setRunning(true);
        } else {
            cluster.pause();
            window.setRunning(false);
        }
    }

    /** Detiene la simulación y arma una nueva desde la configuración (ciclo 0, PID desde 1). */
    private void reset() {
        cluster.shutdown();
        window.resetView();
        lastLogSequence = -1;
        cluster = Cluster.create(config);
        showInitialSettings();
        refresh();
    }

    /**
     * El proceso se agrega a la configuración (así se guarda y se repite al reiniciar)
     * y al clúster. El formulario ya validó con la misma configuración.
     */
    private void createProcess() {
        ProcessConfig process = ProcessFormDialog.showDialog(window, config);
        if (process == null) {
            return;
        }
        try {
            cluster.createProcess(process);
            config.addProcess(process);
        } catch (IllegalArgumentException ex) {
            showError("No se pudo crear el proceso", ex);
        }
        refresh();
    }

    /** Primero el clúster (puede no haber RAM libre ahora mismo); si lo acepta, la configuración. */
    private void createBuffer() {
        BufferConfig buffer = BufferFormDialog.showDialog(window, config);
        if (buffer == null) {
            return;
        }
        try {
            cluster.createBuffer(buffer);
            config.addBuffer(buffer);
        } catch (IllegalArgumentException ex) {
            showError("No se pudo crear el buffer", ex);
        }
        refresh();
    }

    private void showError(String title, IllegalArgumentException ex) {
        JOptionPane.showMessageDialog(window, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    // ---------- Refresco ----------

    private void refresh() {
        window.setCycle(cluster.getCurrentCycle());
        for (int id = 1; id <= cluster.getComputerCount(); id++) {
            Computer computer = cluster.getComputer(id);
            ComputerPanel panel = window.getComputerPanel(id);
            Kernel kernel = computer.getKernel();
            Cpu cpu = computer.getCpu();
            panel.showRunning(kernel.getRunning(), cpu.getMode(), cpu.getProgramCounter(),
                    cpu.getMemoryAddressRegister());
            panel.showMemory(computer.getRam().getUsedSize(), computer.getRam().getTotalSize());
            panel.showQueues(kernel.getNewSnapshot(), kernel.getReadySnapshot(),
                    kernel.getBlockedSnapshot(), kernel.getTerminatedSnapshot());
        }
        window.showBuffers(cluster.getBuffers(), cluster.getSystemMetrics().getBufferStats());
        appendNewLogEntries();
    }

    /** Agrega solo las entradas que no se han mostrado (cada LogEntry trae su número de secuencia). */
    private void appendNewLogEntries() {
        LinkedList<LogEntry> entries = cluster.getLog().getEntries();
        while (!entries.isEmpty()) {
            LogEntry entry = entries.removeFirst();
            if (entry.sequence() > lastLogSequence) {
                window.appendLog(entry);
                lastLogSequence = entry.sequence();
            }
        }
    }

    public Cluster getCluster() {
        return cluster;
    }

    public MainWindow getWindow() {
        return window;
    }
}
