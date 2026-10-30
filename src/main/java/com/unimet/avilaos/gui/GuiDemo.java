package com.unimet.avilaos.gui;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.events.EventLog;
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
        EventLog eventLog = new EventLog();
        eventLog.addListener(entry -> SwingUtilities.invokeLater(() -> window.appendLog(entry)));

        // ----- Computador 1 -----
        Pcb running1 = ready(Pcb.cpuBound("Navegador", 1, 200, 50, 80, 1, 0));
        running1.markRunning(0);
        LinkedList<Pcb> new1 = list(Pcb.cpuBound("Editor", 2, 900, 30, 60, 1, 0));
        LinkedList<Pcb> ready1 = list(ready(Pcb.ioBound("Copia", 3, 100, 40, 90, 1, 0, 5, 3)),
                ready(Pcb.cpuBound("Compilador", 1, 150, 70, 120, 1, 0)));
        Pcb blocked1 = ready(Pcb.bufferUser("Prod", ProcessType.PRODUCER, 2, 50, 20, 100, 1, 0, "B1", 2, 5));
        blocked1.markRunning(0);
        blocked1.markBlocked(BlockReason.SEMAPHORE, "'vacíos' de B1");

        // ----- Computador 2 -----
        Pcb blocked2 = ready(Pcb.ioBound("Backup", 2, 120, 30, 70, 2, 0, 4, 6));
        blocked2.markRunning(0);
        blocked2.markBlocked(BlockReason.IO, "disco");
        Pcb done2 = ready(Pcb.cpuBound("Script", 1, 60, 10, 30, 2, 0));
        done2.markRunning(0);
        done2.markTerminated(9, false);

        LinkedList<Pcb> blockedList1 = list(blocked1);
        LinkedList<Pcb> doneList1 = new LinkedList<>();
        LinkedList<Pcb> new2 = new LinkedList<>();
        LinkedList<Pcb> ready2 = new LinkedList<>();
        LinkedList<Pcb> blockedList2 = list(blocked2);
        LinkedList<Pcb> doneList2 = list(done2);

        window.getComputerPanel(1).showQueues(new1, ready1, blockedList1, doneList1);
        window.getComputerPanel(1).showMemory(500, 1024);
        window.getComputerPanel(2).showQueues(new2, ready2, blockedList2, doneList2);
        window.getComputerPanel(2).showMemory(120, 2048);
        window.getComputerPanel(2).showRunning(null, CpuMode.KERNEL);

        for (int id = 1; id <= window.getComputerCount(); id++) {
            int computer = id;
            window.getComputerPanel(id).onPolicyChanged(policy ->
                    eventLog.log(0, "Computador " + computer, "cambia su política a " + policy));
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
                eventLog.log(cycle[0], "Computador 1", "ejecuta código del SO (planificador)");
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

        // Vista 1: crear buffer y crear proceso (en la demo se agregan a la cola de Nuevos)
        LinkedList<String> buffers = new LinkedList<>();
        buffers.addLast("B1");
        window.getCreateBufferButton().addActionListener(e -> {
            BufferFormData data = BufferFormDialog.showDialog(window, window.getComputerCount(),
                    2048, toArray(buffers));
            if (data != null) {
                buffers.addLast(data.name());
                eventLog.log(cycle[0], "ÁvilaOS", "Se crea el buffer " + data.name() + " (capacidad "
                        + data.capacity() + ") en el Computador " + data.hostComputerId());
            }
        });
        window.getCreateProcessButton().addActionListener(e -> {
            ProcessFormData data = ProcessFormDialog.showDialog(window, window.getComputerCount(),
                    2048, toArray(buffers));
            if (data == null) {
                return;                                     // el usuario canceló
            }
            // En la demo, "automático" elige el computador con menos procesos nuevos.
            // Con el kernel real esta decisión la toma ÁvilaOS, no la GUI.
            int computerId = data.isAutomatic() ? (new1.size() <= new2.size() ? 1 : 2) : data.computerId();
            Pcb pcb = toPcb(data, computerId, cycle[0]);
            LinkedList<Pcb> target = computerId == 1 ? new1 : new2;
            target.addLast(pcb);
            if (computerId == 1) {
                window.getComputerPanel(1).showQueues(new1, ready1, blockedList1, doneList1);
            } else {
                window.getComputerPanel(2).showQueues(new2, ready2, blockedList2, doneList2);
            }
            eventLog.log(cycle[0], "ÁvilaOS", "Se crea el proceso " + pcb + " (" + pcb.getType().getLabel()
                    + ") en el Computador " + computerId + (data.isAutomatic() ? " (asignación automática)" : ""));
        });

        window.getComputerPanel(1).showRunning(running1, CpuMode.USER);
        window.setVisible(true);
    }

    /** En la demo la GUI crea el Pcb; con el kernel real lo hará el Cluster con estos mismos datos. */
    private static Pcb toPcb(ProcessFormData d, int computerId, long arrivalCycle) {
        return switch (d.type()) {
            case CPU_BOUND -> Pcb.cpuBound(d.name(), d.priority(), d.memorySize(), d.instructions(),
                    d.deadline(), computerId, arrivalCycle);
            case IO_BOUND -> Pcb.ioBound(d.name(), d.priority(), d.memorySize(), d.instructions(),
                    d.deadline(), computerId, arrivalCycle, d.ioInterval(), d.ioDuration());
            case PRODUCER, CONSUMER -> Pcb.bufferUser(d.name(), d.type(), d.priority(), d.memorySize(),
                    d.instructions(), d.deadline(), computerId, arrivalCycle, d.bufferName(),
                    d.operationInterval(), d.itemsRequired());
        };
    }

    private static String[] toArray(LinkedList<String> items) {
        String[] result = new String[items.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = items.get(i);
        }
        return result;
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
