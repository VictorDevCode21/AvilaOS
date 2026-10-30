package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.BufferConfig;
import com.unimet.avilaos.config.ProcessConfig;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.events.EventLog;
import com.unimet.avilaos.hardware.CpuMode;
import com.unimet.avilaos.os.process.BlockReason;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.process.PidGenerator;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Demo TEMPORAL de la interfaz mientras el kernel no existe. Está en Test Packages
 * (no entra en el programa entregado) y se elimina cuando la GUI se conecte al Cluster real.
 * En NetBeans: clic derecho sobre este archivo → Run File.
 *
 * Para poder probar la GUI, imita de forma muy simple lo que hará el kernel
 * (FCFS, admisión por RAM, deadline y terminación). Esta lógica NO es parte de la GUI:
 * con la simulación real, la GUI solo mostrará lo que decida el kernel.
 *
 * - "Reiniciar" vuelve al ciclo 0 con la carga inicial.
 * - Cuando ya no queda ningún proceso por ejecutar, el reloj se detiene solo.
 *
 * @author NicollPinzon
 */
public final class GuiDemo {

    private static final String[] POLICIES = {"FCFS", "Round Robin", "EDF", "Prioridades apropiativas"};
    /** RAM de cada computador: distinta a propósito para probar la validación por computador. */
    private static final int[] RAM_MB = {1024, 2048};

    private final MainWindow window = new MainWindow(2, POLICIES);
    private final EventLog eventLog = new EventLog();
    private final DemoComputer[] computers = new DemoComputer[2];
    private final LinkedList<String> buffers = new LinkedList<>();
    private final Timer timer;
    private long cycle;

    private GuiDemo() {
        timer = new Timer(500, e -> tick());
        eventLog.addListener(entry -> SwingUtilities.invokeLater(() -> window.appendLog(entry)));
        wireButtons();
        loadInitialState();
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
        new GuiDemo().window.setVisible(true);
    }

    // ---------- Carga inicial y reinicio ----------

    private void loadInitialState() {
        cycle = 0;
        PidGenerator.reset();                       // los IDs vuelven a empezar en P1
        eventLog.clear();
        window.resetView();
        buffers.clear();
        buffers.addLast("B1");
        for (int i = 0; i < computers.length; i++) {
            computers[i] = new DemoComputer(i + 1);
        }

        DemoComputer c1 = computers[0];
        c1.add(Pcb.cpuBound("Navegador", 1, 200, 12, 40, 1, 0));
        c1.add(Pcb.cpuBound("Editor", 2, 300, 8, 30, 1, 0));
        c1.add(Pcb.cpuBound("Compilador", 1, 900, 10, 60, 1, 0));    // espera RAM en Nuevos
        Pcb producer = Pcb.bufferUser("Prod", com.unimet.avilaos.os.process.ProcessType.PRODUCER,
                2, 50, 20, 25, 1, 0, "B1", 2, 5);
        producer.markReady();
        producer.markRunning(0);
        producer.markBlocked(BlockReason.SEMAPHORE, "'vacíos' de B1");
        c1.blocked.addLast(producer);
        c1.usedRam += producer.getMemorySize();

        DemoComputer c2 = computers[1];
        c2.add(Pcb.cpuBound("Script", 1, 60, 6, 20, 2, 0));
        c2.add(Pcb.cpuBound("Backup", 3, 120, 15, 12, 2, 0));       // su deadline vence antes de terminar

        eventLog.log(0, "ÁvilaOS", "Carga inicial lista: pulsa Iniciar");
        timer.setDelay((Integer) window.getCycleDurationSpinner().getValue());
        window.getStartPauseButton().setEnabled(true);
        refresh(CpuMode.KERNEL, CpuMode.KERNEL);
    }

    private void wireButtons() {
        window.getStartPauseButton().addActionListener(e -> {
            if (timer.isRunning()) {
                timer.stop();
                window.setRunning(false);
            } else {
                timer.start();
                window.setRunning(true);
            }
        });
        window.getResetButton().addActionListener(e -> {
            timer.stop();
            loadInitialState();
        });
        window.getCycleDurationSpinner().addChangeListener(e ->
                timer.setDelay((Integer) window.getCycleDurationSpinner().getValue()));
        for (int id = 1; id <= window.getComputerCount(); id++) {
            int computer = id;
            window.getComputerPanel(id).onPolicyChanged(policy -> eventLog.log(cycle,
                    "Computador " + computer, "cambia su política a " + policy + " (la demo siempre usa FCFS)"));
        }
        window.getCreateBufferButton().addActionListener(e -> createBuffer());
        window.getCreateProcessButton().addActionListener(e -> createProcess());
    }

    // ---------- Un ciclo de reloj (imita al kernel) ----------

    private void tick() {
        cycle++;
        window.setCycle(cycle);
        CpuMode[] modes = new CpuMode[computers.length];
        for (int i = 0; i < computers.length; i++) {
            modes[i] = computers[i].tick();
        }
        refresh(modes[0], modes[1]);

        if (allFinished()) {                         // nada más por ejecutar: el reloj se detiene
            timer.stop();
            window.setRunning(false);
            window.getStartPauseButton().setEnabled(false);
            eventLog.log(cycle, "ÁvilaOS", "Simulación terminada: todos los procesos terminaron. "
                    + "Pulsa Reiniciar para volver a empezar.");
        }
    }

    private boolean allFinished() {
        for (DemoComputer c : computers) {
            if (c.running != null || !c.newQueue.isEmpty() || !c.ready.isEmpty() || !c.blocked.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void refresh(CpuMode mode1, CpuMode mode2) {
        CpuMode[] modes = {mode1, mode2};
        for (int i = 0; i < computers.length; i++) {
            DemoComputer c = computers[i];
            ComputerPanel panel = window.getComputerPanel(c.id);
            if (c.running == null) {
                panel.showIdle();
            } else {
                // con la Cpu real: cpu.getProgramCounter() y cpu.getMemoryAddressRegister().
                // Tras captar, MAR <- PC y PC++, así que MAR = PC - 1.
                int pc = c.running.getProgramCounter();
                panel.showRunning(c.running, modes[i], pc, Math.max(0, pc - 1));
            }
            panel.showMemory(c.usedRam, RAM_MB[c.id - 1]);
            panel.showQueues(c.newQueue.copy(), c.ready.copy(), c.blocked.copy(), c.done.copy());
        }
    }

    // ---------- Vista 1 ----------

    private void createBuffer() {
        BufferConfig data = BufferFormDialog.showDialog(window, RAM_MB,
                toArray(buffers));
        if (data != null) {
            buffers.addLast(data.name());
            eventLog.log(cycle, "Computador " + data.hostComputerId(),
                    "se crea el buffer " + data.name() + " (capacidad " + data.capacity() + ")");
        }
    }

    private void createProcess() {
        ProcessConfig data = ProcessFormDialog.showDialog(window, RAM_MB,
                toArray(buffers));
        if (data == null) {
            return;                                     // el usuario canceló
        }
        // "Automático": en la demo, el computador con menos procesos pendientes donde quepa.
        // Con el kernel real esta decisión la toma ÁvilaOS, no la GUI.
        int computerId = data.isAutomatic() ? leastLoadedThatFits(data.memorySize()) : data.computerId();
        Pcb pcb = data.toPcb(computerId, cycle);          // el mismo método que usa la configuración
        computers[computerId - 1].add(pcb);
        eventLog.log(cycle, "Computador " + computerId, "se crea el proceso " + pcb + " ("
                + pcb.getType().getLabel() + ")" + (data.isAutomatic() ? " por asignación automática" : ""));
        window.getStartPauseButton().setEnabled(true);  // hay trabajo nuevo: se puede volver a iniciar
        refresh(CpuMode.KERNEL, CpuMode.KERNEL);
    }

    /** Computador con menos procesos pendientes cuya RAM alcanza para el proceso. */
    private int leastLoadedThatFits(int memoryMb) {
        int best = -1;
        for (DemoComputer c : computers) {
            if (memoryMb <= RAM_MB[c.id - 1] && (best < 0 || c.pending() < computers[best - 1].pending())) {
                best = c.id;
            }
        }
        return best;                                    // el formulario ya garantizó que cabe en alguno
    }

    private static String[] toArray(LinkedList<String> items) {
        String[] result = new String[items.size()];
        LinkedList<String> walk = items.copy();
        for (int i = 0; i < result.length; i++) {
            result[i] = walk.removeFirst();
        }
        return result;
    }

    // ---------- Computador de mentira (FCFS) ----------

    /** Imita lo mínimo de un computador para la demo. NO es el Computer real. */
    private final class DemoComputer {

        final int id;
        final String source;
        final LinkedList<Pcb> newQueue = new LinkedList<>();
        final LinkedList<Pcb> ready = new LinkedList<>();
        final LinkedList<Pcb> blocked = new LinkedList<>();
        final LinkedList<Pcb> done = new LinkedList<>();
        Pcb running;
        int usedRam;

        DemoComputer(int id) {
            this.id = id;
            this.source = "Computador " + id;
        }

        void add(Pcb pcb) {
            newQueue.addLast(pcb);
        }

        int pending() {
            return newQueue.size() + ready.size() + blocked.size() + (running == null ? 0 : 1);
        }

        /** Un ciclo: deadlines, ejecución, admisión y despacho. Devuelve el modo de la CPU. */
        CpuMode tick() {
            expireDeadlines();
            CpuMode mode = CpuMode.USER;
            if (running != null) {
                running.setProgramCounter(running.getProgramCounter() + 1);
                running.instructionExecuted();
                if (running.isFinished()) {
                    terminate(running, false);
                    running = null;
                }
            }
            admit();
            if (running == null && !ready.isEmpty()) {          // despacho FCFS (código del SO)
                running = ready.removeFirst();
                running.markRunning(cycle);
                eventLog.log(cycle, source, "selecciona el proceso " + running);
                mode = CpuMode.KERNEL;
            }
            return mode;
        }

        /** El deadline baja en todas las colas; al llegar a 0 el proceso se termina. */
        private void expireDeadlines() {
            if (running != null && running.tickDeadline()) {
                terminate(running, true);
                running = null;
            }
            expireIn(newQueue);
            expireIn(ready);
            expireIn(blocked);
        }

        private void expireIn(LinkedList<Pcb> queue) {
            LinkedList<Pcb> snapshot = queue.copy();
            for (int i = 0; i < snapshot.size(); i++) {
                Pcb p = snapshot.get(i);
                if (p.tickDeadline()) {
                    queue.remove(p);
                    terminate(p, true);
                }
            }
        }

        /** Nuevo → Listo solo si hay RAM, en orden de llegada. */
        private void admit() {
            while (!newQueue.isEmpty() && usedRam + newQueue.peekFirst().getMemorySize() <= RAM_MB[id - 1]) {
                Pcb p = newQueue.removeFirst();
                usedRam += p.getMemorySize();
                p.markReady();
                ready.addLast(p);
                eventLog.log(cycle, source, "admite " + p + " (hay RAM)");
            }
        }

        private void terminate(Pcb p, boolean deadlineExpired) {
            boolean admitted = p.getState() != com.unimet.avilaos.os.process.ProcessState.NEW;
            p.markTerminated(cycle, deadlineExpired);
            if (admitted) {
                usedRam -= p.getMemorySize();                     // libera su memoria
            }
            done.addLast(p);
            eventLog.log(cycle, source, p + (deadlineExpired
                    ? " se termina: venció su deadline" : " terminó su ejecución"));
        }
    }
}
