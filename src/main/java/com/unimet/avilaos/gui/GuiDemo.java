package com.unimet.avilaos.gui;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.CpuMode;
import com.unimet.avilaos.os.process.BlockReason;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.process.ProcessType;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Demo TEMPORAL de la interfaz con datos falsos, mientras el kernel no existe.
 * Se elimina cuando la GUI se conecte al Cluster real.
 *
 * @author NicollPinzon
 */
public final class GuiDemo {

    private static final String[] POLICIES = {"FCFS", "Round Robin", "EDF", "Prioridades apropiativas"};

    private GuiDemo() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GuiDemo::launch);
    }

    public static void launch() {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {
            // si Nimbus no está disponible se usa el aspecto por defecto
        }

        MainWindow window = new MainWindow(2, POLICIES);

        // ----- Computador 1 -----
        Pcb running1 = ready(Pcb.cpuBound("Navegador", 1, 200, 50, 80, 1, 0));
        running1.markRunning(0);
        LinkedList<Pcb> new1 = list(Pcb.cpuBound("Editor", 2, 900, 30, 60, 1, 0));
        LinkedList<Pcb> ready1 = list(ready(Pcb.ioBound("Copia", 3, 100, 40, 90, 1, 0, 5, 3)),
                ready(Pcb.cpuBound("Compilador", 1, 150, 70, 120, 1, 0)));
        Pcb blocked1 = ready(Pcb.bufferUser("Prod", ProcessType.PRODUCER, 2, 50, 20, 100, 1, 0, "B1", 2, 5));
        blocked1.markRunning(0);
        blocked1.markBlocked(BlockReason.SEMAPHORE, "semáforo 'e' del buffer B1");

        // ----- Computador 2 -----
        Pcb blocked2 = ready(Pcb.ioBound("Backup", 2, 120, 30, 70, 2, 0, 4, 6));
        blocked2.markRunning(0);
        blocked2.markBlocked(BlockReason.IO, "disco");
        Pcb done2 = ready(Pcb.cpuBound("Script", 1, 60, 10, 30, 2, 0));
        done2.markRunning(0);
        done2.markTerminated(9, false);

        window.getComputerPanel(1).showQueues(new1, ready1, list(blocked1), new LinkedList<>());
        window.getComputerPanel(1).showMemory(500, 1024);
        window.getComputerPanel(2).showQueues(new LinkedList<>(), new LinkedList<>(), list(blocked2), list(done2));
        window.getComputerPanel(2).showMemory(120, 2048);
        window.getComputerPanel(2).showRunning(null, CpuMode.KERNEL);

        for (int id = 1; id <= window.getComputerCount(); id++) {
            int computer = id;
            window.getComputerPanel(id).onPolicyChanged(policy ->
                    window.appendLog(0, "Computador " + computer + " cambia su política a " + policy));
        }

        // "Reloj" de mentira: avanza el PC del proceso en CPU para ver la vista en tiempo real
        long[] cycle = {0};
        Timer timer = new Timer(500, e -> {
            cycle[0]++;
            running1.setProgramCounter(running1.getProgramCounter() + 1);
            running1.instructionExecuted();
            running1.tickDeadline();
            CpuMode mode = cycle[0] % 5 == 0 ? CpuMode.KERNEL : CpuMode.USER;
            window.setCycle(cycle[0]);
            window.getComputerPanel(1).showRunning(running1, mode);
            if (cycle[0] % 5 == 0) {
                window.appendLog(cycle[0], "Computador 1 ejecuta código del SO (planificador)");
            }
        });
        window.getStartPauseButton().addActionListener(e -> {
            if (timer.isRunning()) {
                timer.stop();
                window.getStartPauseButton().setText("Iniciar");
            } else {
                timer.start();
                window.getStartPauseButton().setText("Pausar");
            }
        });
        window.getCycleDurationSpinner().addChangeListener(e ->
                timer.setDelay((Integer) window.getCycleDurationSpinner().getValue()));

        window.getComputerPanel(1).showRunning(running1, CpuMode.USER);
        window.setVisible(true);
    }

    private static Pcb ready(Pcb pcb) {
        pcb.markReady();
        return pcb;
    }

    private static LinkedList<Pcb> list(Pcb... items) {
        LinkedList<Pcb> result = new LinkedList<>();
        for (Pcb p : items) {
            result.addLast(p);
        }
        return result;
    }
}
