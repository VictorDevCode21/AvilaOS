package com.unimet.avilaos;

import com.unimet.avilaos.cluster.Cluster;
import com.unimet.avilaos.config.BufferConfig;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.gui.SimulationController;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import com.unimet.avilaos.metrics.BufferStats;
import com.unimet.avilaos.metrics.MetricsSnapshot;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.scheduling.PolicyType;
import com.unimet.avilaos.os.sync.BoundedBuffer;
import com.unimet.avilaos.config.ProcessConfig;
import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.os.process.ProcessType;

/**
 * Lab de pruebas local (NO se commitea).
 *
 * @author victor1102
 */
public class FinalLab {

    static int ok;
    static int fail;

    static void check(String what, boolean cond) {
        if (cond) {
            ok++;
            System.out.println("OK    " + what);
        } else {
            fail++;
            System.out.println("FALLA " + what);
        }
    }

    static boolean rejects(Runnable r, String expectedFragment) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            System.out.println("      mensaje: " + e.getMessage());
            return expectedFragment == null || e.getMessage().contains(expectedFragment);
        }
    }

    static boolean accepts(Runnable r) {
        try {
            r.run();
            return true;
        } catch (IllegalArgumentException e) {
            System.out.println("      error inesperado: " + e.getMessage());
            return false;
        }
    }

    static ProcessConfig buffered(String name, ProcessType type, int instructions, int interval, int items) {
        return new ProcessConfig(name, type, 1, 32, instructions, 100_000, 0, 0, 0, "B1", interval, items);
    }

    public static void main(String[] args) throws Exception {
        String only = args.length > 0 ? args[0] : "all";
        if (only.equals("samplegen")) {
            java.nio.file.Path out = java.nio.file.Path.of(SAMPLE_FILE);
            new com.unimet.avilaos.config.ConfigRepository().save(sampleConfig(), out);
            System.out.println("escrito: " + out.toAbsolutePath());
            return;
        }
        if (only.equals("all") || only.equals("d3")) {
            d3();
        }
        if (only.equals("all") || only.equals("fair")) {
            fairness();
        }
        if (only.equals("all") || only.equals("sim")) {
            simulation();
        }
        if (only.equals("all") || only.equals("stress")) {
            arrivalsStress();
        }
        if (only.equals("all") || only.equals("sample")) {
            sampleRun();
        }
        if (only.equals("all") || only.equals("config")) {
            configRoundTrip();
        }
        if (only.equals("all") || only.equals("gui")) {
            gui();
        }
        if (only.equals("all") || only.equals("autopause")) {
            autoPause();
            autoPauseShort();
        }
        System.out.println("TOTAL: " + ok + " OK, " + fail + " FALLA");
        System.exit(fail == 0 ? 0 : 1);
    }

    // ---------- D3 ----------

    static void d3() {
        System.out.println("== D3 ==");
        check("productor justo en el mínimo (150 = 10 x (10+5)) se acepta",
                accepts(() -> buffered("Prod", ProcessType.PRODUCER, 150, 10, 10)));
        check("productor con 149 se rechaza con el mínimo en el mensaje",
                rejects(() -> buffered("Prod", ProcessType.PRODUCER, 149, 10, 10), "al menos 150 (10 × (10 + 5))"));
        check("consumidor justo en el mínimo se acepta",
                accepts(() -> buffered("Cons", ProcessType.CONSUMER, 35, 2, 5)));
        check("consumidor con uno menos se rechaza",
                rejects(() -> buffered("Cons", ProcessType.CONSUMER, 34, 2, 5), "al menos 35"));
        check("CPU bound con pocas instrucciones no se ve afectado", accepts(() ->
                new ProcessConfig("C", ProcessType.CPU_BOUND, 1, 32, 1, 100, 0, 0, 0, "B1", 1000, 10_000)));
        check("I/O bound no se ve afectado", accepts(() ->
                new ProcessConfig("I", ProcessType.IO_BOUND, 1, 32, 3, 100, 0, 1, 1, null, 1000, 10_000)));
        check("valores máximos no desbordan (10000 x (1000+5)) y se rechazan",
                rejects(() -> buffered("Big", ProcessType.PRODUCER, ProcessConfig.MAX_INSTRUCTIONS,
                        ProcessConfig.MAX_OPERATION_INTERVAL, ProcessConfig.MAX_ITEMS), "10050000"));
        check("SimulationConfig.defaults() sigue siendo válido", accepts(() -> SimulationConfig.defaults().validate()));
        check("ejemplo del javadoc de ConfigRepository (100 instr, 2, 5) es válido", accepts(() -> {
            SimulationConfig c = SimulationConfig.defaults();
            c.addBuffer(new BufferConfig("B1", 1, 3, 64));
            c.addProcess(new ProcessConfig("Prod", ProcessType.PRODUCER, 1, 64, 100, 300, 2, 0, 0, "B1", 2, 5));
            c.validate();
        }));
    }

    // ---------- Utilidades de simulación ----------

    static int terminatedCount(Cluster cluster) {
        int n = 0;
        for (int id = 1; id <= cluster.getComputerCount(); id++) {
            n += cluster.getComputer(id).getKernel().getTerminatedSnapshot().size();
        }
        return n;
    }

    /** Corre a 1 ms por ciclo hasta que terminen "expected" procesos o pase el timeout. */
    static boolean runUntilDone(Cluster cluster, int expected, long timeoutMs) throws InterruptedException {
        cluster.setCycleDurationMs(1);
        cluster.start();
        long end = System.currentTimeMillis() + timeoutMs;
        while (terminatedCount(cluster) < expected && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
        cluster.pause();
        Thread.sleep(50);                       // deja que el ciclo en curso termine
        return terminatedCount(cluster) >= expected;
    }

    static ProcessConfig cpu(String name, int instructions, long deadline, int computer) {
        return new ProcessConfig(name, ProcessType.CPU_BOUND, 1, 32, instructions, deadline, computer, 0, 0, null, 0, 0);
    }

    static ProcessConfig io(String name, int instructions, int computer) {
        return new ProcessConfig(name, ProcessType.IO_BOUND, 1, 32, instructions, 100_000, computer, 3, 6, null, 0, 0);
    }

    static Pcb find(LinkedList<Pcb> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getName().equals(name)) {
                return list.get(i);
            }
        }
        return null;
    }

    static boolean between01(double v) {
        return v >= 0.0 && v <= 1.0 + 1e-9;
    }

    // ---------- Fairness ----------

    static void fairness() throws InterruptedException {
        System.out.println("== Fairness ==");
        // 1) dos CPU bound en el mismo computador con Round Robin
        SimulationConfig a = new SimulationConfig(10, 3, 0);
        a.addComputer(1024, PolicyType.ROUND_ROBIN);
        a.addComputer(1024, PolicyType.FCFS);
        a.addProcess(cpu("A", 60, 100_000, 1));
        a.addProcess(cpu("B", 60, 100_000, 1));
        Cluster ca = Cluster.create(a);
        boolean doneA = runUntilDone(ca, 2, 30_000);
        MetricsSnapshot ma = ca.getComputer(1).getMetrics().getSnapshot();
        LinkedList<Pcb> ta = ca.getComputer(1).getKernel().getTerminatedSnapshot();
        ca.shutdown();
        check("RR: los dos CPU bound terminan", doneA);
        System.out.printf("      equidad RR = %.4f%n", ma.fairnessIndex());
        check("RR: equidad de dos CPU bound cercana a 1 (>= 0.95)", ma.fairnessIndex() >= 0.95);
        double[] fractions = new double[ta.size()];
        for (int i = 0; i < ta.size(); i++) {
            Pcb p = ta.get(i);
            long executed = p.getTotalInstructions() - p.getRemainingInstructions();
            long turnaround = p.getFinishCycle() - p.getArrivalCycle();
            fractions[i] = (double) executed / (executed + p.getReadyCycles());
            System.out.printf("      %s ejecutadas=%d listos=%d retorno=%d fracción=%.3f%n", p.getName(), executed,
                    p.getReadyCycles(), turnaround, fractions[i]);
            check("RR: " + p.getName() + " ejecutadas + listos <= retorno (listos solo cuenta en Listos)",
                    executed + p.getReadyCycles() <= turnaround);
            check("RR: " + p.getName() + " estuvo en Listos (readyCycles > 0)", p.getReadyCycles() > 0);
        }

        check("RR: fracciones casi iguales (diferencia < 0.05)",
                fractions.length == 2 && Math.abs(fractions[0] - fractions[1]) < 0.05);

        // 2) un I/O bound solo en un computador: nunca espera la CPU
        SimulationConfig b = new SimulationConfig(10, 3, 0);
        b.addComputer(1024, PolicyType.ROUND_ROBIN);
        b.addComputer(1024, PolicyType.FCFS);
        b.addProcess(io("IO", 30, 1));
        Cluster cb = Cluster.create(b);
        boolean doneB = runUntilDone(cb, 1, 30_000);
        MetricsSnapshot mb = cb.getComputer(1).getMetrics().getSnapshot();
        Pcb iop = cb.getComputer(1).getKernel().getTerminatedSnapshot().get(0);
        cb.shutdown();
        long executed = iop.getTotalInstructions() - iop.getRemainingInstructions();
        long turnaround = iop.getFinishCycle() - iop.getArrivalCycle();
        double oldRatio = (double) executed / turnaround;
        double newRatio = executed + iop.getReadyCycles() == 0 ? 1.0
                : (double) executed / (executed + iop.getReadyCycles());
        System.out.printf("      IO ejecutadas=%d listos=%d retorno=%d  x_antes=%.3f x_ahora=%.3f equidad=%.4f%n",
                executed, iop.getReadyCycles(), turnaround, oldRatio, newRatio, mb.fairnessIndex());
        check("IO solo: termina", doneB);
        check("IO solo: readyCycles == 0 (al volver de E/S se despacha en el siguiente ciclo, no espera)",
                iop.getReadyCycles() == 0);
        check("IO solo: fracción atendida = 1.0", Math.abs(newRatio - 1.0) < 1e-9);
        check("IO solo: equidad = 1", Math.abs(mb.fairnessIndex() - 1.0) < 1e-9);
        check("IO solo: la fracción atendida ahora es alta (>= 0.8) y antes era baja (< 0.5)",
                newRatio >= 0.8 && oldRatio < 0.5);

        // 3) un I/O bound y un CPU bound juntos: antes el I/O bound bajaba el índice
        SimulationConfig c = new SimulationConfig(10, 3, 0);
        c.addComputer(1024, PolicyType.ROUND_ROBIN);
        c.addComputer(1024, PolicyType.FCFS);
        c.addProcess(io("IO", 30, 1));
        c.addProcess(cpu("CPU", 30, 100_000, 1));
        Cluster cc = Cluster.create(c);
        boolean doneC = runUntilDone(cc, 2, 30_000);
        MetricsSnapshot mc = cc.getComputer(1).getMetrics().getSnapshot();
        LinkedList<Pcb> tc = cc.getComputer(1).getKernel().getTerminatedSnapshot();
        cc.shutdown();
        double s1 = 0;
        double s2 = 0;
        for (int i = 0; i < tc.size(); i++) {
            Pcb p = tc.get(i);
            double x = Math.min(1.0, (double) (p.getTotalInstructions() - p.getRemainingInstructions())
                    / Math.max(1, p.getFinishCycle() - p.getArrivalCycle()));
            s1 += x;
            s2 += x * x;
        }
        double oldJain = s1 * s1 / (tc.size() * s2);
        System.out.printf("      IO+CPU: equidad ahora=%.4f antes=%.4f%n", mc.fairnessIndex(), oldJain);
        check("IO+CPU: terminan", doneC);
        check("IO+CPU: la equidad nueva es >= 0.9", mc.fairnessIndex() >= 0.9);
    }

    // ---------- Simulación completa ----------

    static void simulation() throws InterruptedException {
        System.out.println("== Simulación completa ==");
        SimulationConfig config = new SimulationConfig(10, 3, 2);
        config.addComputer(1024, PolicyType.ROUND_ROBIN);
        config.addComputer(512, PolicyType.FCFS);
        config.addComputer(768, PolicyType.EDF);
        config.addBuffer(new BufferConfig("B1", 1, 3, 64));
        // productor remoto (computador 2) en el mínimo exacto de D3, consumidor local
        config.addProcess(new ProcessConfig("Prod", ProcessType.PRODUCER, 1, 64, 8 * (2 + 5), 100_000, 2,
                0, 0, "B1", 2, 8));
        config.addProcess(new ProcessConfig("Cons", ProcessType.CONSUMER, 1, 64, 200, 100_000, 1,
                0, 0, "b1", 3, 8));
        for (int i = 1; i <= 5; i++) {
            config.addProcess(cpu("Auto" + i, 20 + 5 * i, 100_000, ProcessConfig.AUTOMATIC));
        }
        config.addProcess(io("IOB", 24, 3));
        config.addProcess(cpu("Corto", 500, 15, 3));
        config.validate();
        int total = config.getProcesses().size();

        Cluster cluster = Cluster.create(config);
        boolean done = runUntilDone(cluster, total, 30_000);
        check("todos los procesos (" + total + ") terminan antes de 30 s (ciclo " + cluster.getCurrentCycle() + ")", done);

        LinkedList<BoundedBuffer> buffers = cluster.getBuffers();
        BoundedBuffer b1 = buffers.get(0);
        int empty = b1.getEmpty().getValue();
        int full = b1.getFull().getValue();
        System.out.printf("      B1: vacíos=%d lleno=%d mutex=%d elementos=%d/%d%n", empty, full,
                b1.getMutex().getValue(), b1.getItemCount(), b1.getCapacity());
        check("buffer: vacíos + lleno = capacidad", empty + full == b1.getCapacity());
        check("buffer: lleno = elementos", full == b1.getItemCount());
        check("buffer: mutex = 1", b1.getMutex().getValue() == 1);
        check("buffer: nadie esperando", b1.getEmpty().getWaitingCount() == 0
                && b1.getFull().getWaitingCount() == 0 && b1.getMutex().getWaitingCount() == 0);

        int[] expectedRam = {64, 0, 0};
        boolean ramOk = true;
        for (int id = 1; id <= 3; id++) {
            int used = cluster.getComputer(id).getRam().getUsedSize();
            System.out.println("      RAM computador " + id + ": " + used + " MB");
            ramOk &= used == expectedRam[id - 1];
        }
        check("RAM final = solo la de los buffers", ramOk);

        BufferStats stats = cluster.getSystemMetrics().getBufferStats().get(0);
        System.out.println("      B1 producidos=" + stats.getProduced() + " consumidos=" + stats.getConsumed());
        check("producidos = consumidos = 8", stats.getProduced() == 8 && stats.getConsumed() == 8);

        LinkedList<Pcb> t1 = cluster.getComputer(1).getKernel().getTerminatedSnapshot();
        LinkedList<Pcb> t2 = cluster.getComputer(2).getKernel().getTerminatedSnapshot();
        LinkedList<Pcb> t3 = cluster.getComputer(3).getKernel().getTerminatedSnapshot();
        Pcb prod = find(t2, "Prod");
        Pcb cons = find(t1, "Cons");
        Pcb shortDeadline = find(t3, "Corto");
        check("D3: el productor en el mínimo completó sus 8 elementos", prod != null && prod.getItemsDone() == 8
                && !prod.isDeadlineMissed());
        check("el consumidor completó sus 8 elementos", cons != null && cons.getItemsDone() == 8);
        check("el de deadline corto terminó por deadline", shortDeadline != null && shortDeadline.isDeadlineMissed());

        boolean metricsOk = true;
        for (int id = 0; id <= 3; id++) {
            MetricsSnapshot m = id == 0 ? cluster.getSystemMetrics().getSnapshot()
                    : cluster.getComputer(id).getMetrics().getSnapshot();
            System.out.printf("      %s: term=%d thr=%.4f cpu=%.3f resp=%.2f ret=%.2f dl=%.3f jain=%.3f sem=%.2f%n",
                    id == 0 ? "Sistema" : "C" + id, m.completedProcesses(), m.throughput(), m.cpuUtilization(),
                    m.averageResponseTime(), m.averageTurnaroundTime(), m.deadlineComplianceRate(),
                    m.fairnessIndex(), m.averageSemaphoreBlockedTime());
            metricsOk &= between01(m.throughput()) && between01(m.cpuUtilization())
                    && between01(m.deadlineComplianceRate()) && between01(m.fairnessIndex())
                    && m.averageResponseTime() >= 0 && m.averageTurnaroundTime() >= 0
                    && m.averageSemaphoreBlockedTime() >= 0;
        }
        check("métricas en rango (0..1 donde corresponde, promedios >= 0)", metricsOk);
        check("sistema: terminados = " + total, cluster.getSystemMetrics().getSnapshot().completedProcesses() == total);
        cluster.shutdown();
    }

    // ---------- GUI ----------

    interface EdtCall<T> {
        T call() throws Exception;
    }

    @SuppressWarnings("unchecked")
    static <T> T onEdt(EdtCall<T> call) throws Exception {
        Object[] box = new Object[1];
        Exception[] error = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                box[0] = call.call();
            } catch (Exception e) {
                error[0] = e;
            }
        });
        if (error[0] != null) {
            throw error[0];
        }
        return (T) box[0];
    }

    /** Recorre el árbol de componentes y junta los que son de esa clase (en orden). */
    static <T> LinkedList<T> findAll(Container root, Class<T> type) {
        LinkedList<T> found = new LinkedList<>();
        collect(root, type, found);
        return found;
    }

    static <T> void collect(Component c, Class<T> type, LinkedList<T> found) {
        if (type.isInstance(c)) {
            found.addLast(type.cast(c));
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, found);
            }
        }
    }

    static AbstractButton button(Container root, String text) {
        LinkedList<AbstractButton> all = findAll(root, AbstractButton.class);
        for (int i = 0; i < all.size(); i++) {
            if (text.equals(all.get(i).getText())) {
                return all.get(i);
            }
        }
        return null;
    }

    static JTable tableWithModel(Container root, String modelSimpleName) {
        LinkedList<JTable> all = findAll(root, JTable.class);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getModel().getClass().getSimpleName().equals(modelSimpleName)) {
                return all.get(i);
            }
        }
        return null;
    }

    static JDialog waitDialog(String title) throws Exception {
        for (int i = 0; i < 100; i++) {
            JDialog d = onEdt(() -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof JDialog dialog && dialog.isShowing() && title.equals(dialog.getTitle())) {
                        return dialog;
                    }
                }
                return null;
            });
            if (d != null) {
                return d;
            }
            Thread.sleep(50);
        }
        return null;
    }

    static void screenshot(Window window, String name) {
        try {
            Rectangle r = onEdt(() -> window.getBounds());
            File out = new File(System.getProperty("lab.shots", System.getProperty("java.io.tmpdir")), name);
            out.getParentFile().mkdirs();
            ImageIO.write(new Robot().createScreenCapture(r), "png", out);
            System.out.println("      captura: " + out.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("      no se pudo tomar la captura: " + e);
        }
    }

    static SimulationConfig guiConfig() {
        SimulationConfig config = new SimulationConfig(20, 3, 2);
        config.addComputer(1024, PolicyType.ROUND_ROBIN);
        config.addComputer(1024, PolicyType.FCFS);
        config.addComputer(1024, PolicyType.EDF);
        config.addBuffer(new BufferConfig("B1", 1, 2, 64));
        config.addProcess(new ProcessConfig("Prod", ProcessType.PRODUCER, 1, 64, 2000, 100_000, 2,
                0, 0, "B1", 2, 200));
        config.addProcess(new ProcessConfig("Cons1", ProcessType.CONSUMER, 1, 64, 2000, 100_000, 1,
                0, 0, "B1", 6, 100));
        config.addProcess(new ProcessConfig("Cons2", ProcessType.CONSUMER, 1, 64, 2000, 100_000, 3,
                0, 0, "B1", 6, 100));
        for (int i = 1; i <= 4; i++) {
            config.addProcess(cpu("Auto" + i, 20 * i, 100_000, ProcessConfig.AUTOMATIC));
        }
        config.addProcess(io("IOB", 200, 2));
        return config;
    }

    static void gui() throws Exception {
        System.out.println("== GUI ==");
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("      sin pantalla: se omite");
            return;
        }
        SimulationConfig config = guiConfig();
        SimulationController controller = onEdt(() -> {
            SimulationController c = new SimulationController(config);
            c.show();
            return c;
        });
        Window window = controller.getWindow();
        Thread.sleep(400);

        JTable buffers = onEdt(() -> tableWithModel(controller.getWindow(), "BufferTableModel"));
        check("hay tabla de buffers", buffers != null);
        check("tabla de buffers: una fila por buffer (1)", buffers != null && onEdt(() -> buffers.getRowCount()) == 1);

        onEdt(() -> {
            button(controller.getWindow(), "Iniciar").doClick();
            return null;
        });
        Thread.sleep(1200);
        long cycle = controller.getCluster().getCurrentCycle();
        String label = onEdt(() -> findLabelStarting(controller.getWindow(), "Ciclo: "));
        System.out.println("      tras ~1 s: ciclo " + cycle + ", etiqueta '" + label + "'");
        check("Iniciar: el ciclo avanza", cycle > 0);
        check("la etiqueta del ciclo se actualiza", label != null && !label.equals("Ciclo: 0"));
        check("el botón ahora dice Pausar", onEdt(() -> button(controller.getWindow(), "Pausar")) != null);

        // un buffer creado en ejecución aparece en el siguiente refresco
        SwingUtilities.invokeLater(() -> button(controller.getWindow(), "Crear buffer").doClick());
        JDialog dialog = waitDialog("Crear buffer");
        check("se abre el diálogo Crear buffer", dialog != null);
        if (dialog != null) {
            onEdt(() -> {
                findAll(dialog, JTextField.class).get(0).setText("B2");
                button(dialog, "Crear").doClick();
                return null;
            });
            Thread.sleep(400);
            check("buffer creado en ejecución aparece en la tabla (2 filas)", onEdt(() -> buffers.getRowCount()) == 2);
        }
        String sems = onEdt(() -> {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < buffers.getColumnCount(); c++) {
                sb.append(buffers.getColumnName(c)).append('=').append(buffers.getValueAt(0, c)).append("  ");
            }
            return sb.toString();
        });
        System.out.println("      fila B1: " + sems);

        JTable metrics = onEdt(() -> tableWithModel(controller.getWindow(), "MetricsTableModel"));
        if (metrics != null) {
            int n = controller.getCluster().getComputerCount();
            check("tabla de métricas: N+1 filas (" + (n + 1) + ")", onEdt(() -> metrics.getRowCount()) == n + 1);
        } else {
            System.out.println("      (todavía no hay tabla de métricas)");
        }
        guiExtra(controller);

        screenshot(window, "avilaos-running.png");

        onEdt(() -> {
            button(controller.getWindow(), "Pausar").doClick();
            return null;
        });
        Thread.sleep(300);
        long paused = controller.getCluster().getCurrentCycle();
        Thread.sleep(500);
        check("Pausar: el ciclo se detiene", controller.getCluster().getCurrentCycle() == paused);
        check("el botón vuelve a decir Iniciar", onEdt(() -> button(controller.getWindow(), "Iniciar")) != null);

        onEdt(() -> {
            button(controller.getWindow(), "Reiniciar").doClick();
            return null;
        });
        Thread.sleep(400);
        String afterReset = onEdt(() -> findLabelStarting(controller.getWindow(), "Ciclo: "));
        check("Reiniciar: ciclo 0", controller.getCluster().getCurrentCycle() == 0 && "Ciclo: 0".equals(afterReset));
        check("Reiniciar: la tabla de buffers muestra los de la config (2)", onEdt(() -> buffers.getRowCount()) == 2);
        guiAfterReset(controller);
        guiConfigDialog(controller);
    }

    static String findLabelStarting(Container root, String prefix) {
        LinkedList<JLabel> all = findAll(root, JLabel.class);
        for (int i = 0; i < all.size(); i++) {
            String t = all.get(i).getText();
            if (t != null && t.startsWith(prefix)) {
                return t;
            }
        }
        return null;
    }

    /** Chequeos extra de la GUI que se agregan con las tareas siguientes. */
    static void guiExtra(SimulationController controller) throws Exception {
        javax.swing.JTabbedPane tabs = onEdt(() -> findAll(controller.getWindow(), javax.swing.JTabbedPane.class).get(0));
        String titles = onEdt(() -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tabs.getTabCount(); i++) {
                sb.append(i == 0 ? "" : "|").append(tabs.getTitleAt(i));
            }
            return sb.toString();
        });
        check("pestañas: " + titles, titles.equals("Log de eventos|Métricas|Utilización de CPU"));
        Thread.sleep(1500);                          // que haya puntos en la gráfica y algún terminado
        onEdt(() -> {
            tabs.setSelectedIndex(1);
            return null;
        });
        Thread.sleep(300);
        JTable metrics = onEdt(() -> tableWithModel(controller.getWindow(), "MetricsTableModel"));
        String row = onEdt(() -> {
            StringBuilder sb = new StringBuilder();
            int last = metrics.getRowCount() - 1;
            for (int c = 0; c < metrics.getColumnCount(); c++) {
                sb.append(metrics.getColumnName(c)).append('=').append(metrics.getValueAt(last, c)).append("  ");
            }
            return sb.toString();
        });
        System.out.println("      fila Sistema: " + row);
        check("la última fila es Sistema", row.startsWith("=Sistema"));
        screenshot(controller.getWindow(), "avilaos-metrics.png");
        onEdt(() -> {
            tabs.setSelectedIndex(2);
            return null;
        });
        Thread.sleep(300);
        screenshot(controller.getWindow(), "avilaos-chart.png");
    }

    static void guiAfterReset(SimulationController controller) throws Exception {
        JTable metrics = onEdt(() -> tableWithModel(controller.getWindow(), "MetricsTableModel"));
        int n = controller.getCluster().getComputerCount();
        check("Reiniciar: métricas otra vez N+1 filas y 0 terminados en Sistema", onEdt(() ->
                metrics.getRowCount() == n + 1 && "0".equals(String.valueOf(metrics.getValueAt(n, 1)))));
        screenshot(controller.getWindow(), "avilaos-after-reset.png");
    }

    // ---------- Config ----------

    static String describeConfig(SimulationConfig c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getCycleDurationMs()).append('/').append(c.getQuantum()).append('/').append(c.getNetworkLatency());
        LinkedList<com.unimet.avilaos.config.ComputerConfig> cs = c.getComputers();
        for (int i = 0; i < cs.size(); i++) {
            sb.append(" | ").append(cs.get(i));
        }
        LinkedList<BufferConfig> bs = c.getBuffers();
        for (int i = 0; i < bs.size(); i++) {
            sb.append(" | ").append(bs.get(i));
        }
        LinkedList<ProcessConfig> ps = c.getProcesses();
        for (int i = 0; i < ps.size(); i++) {
            sb.append(" | ").append(ps.get(i));
        }
        return sb.toString();
    }

    static void configRoundTrip() throws Exception {
        System.out.println("== Config ==");
        SimulationConfig c = new SimulationConfig(250, 7, 4);
        c.addComputer(2048, PolicyType.EDF);
        c.addComputer(512, PolicyType.PREEMPTIVE_PRIORITY);
        c.addComputer(1024, PolicyType.ROUND_ROBIN);
        c.addBuffer(new BufferConfig("B1", 2, 4, 128));
        c.addBuffer(new BufferConfig("Cola", 3, 10, 32));
        c.addProcess(new ProcessConfig("Prod", ProcessType.PRODUCER, 2, 64, 70, 900, 1, 0, 0, "B1", 2, 10));
        c.addProcess(new ProcessConfig("Cons", ProcessType.CONSUMER, 3, 64, 400, 900, 0, 0, 0, "Cola", 5, 7));
        c.addProcess(new ProcessConfig("Calc", ProcessType.CPU_BOUND, 0, 100, 500, 50, 3, 0, 0, null, 0, 0));
        c.addProcess(new ProcessConfig("Disco", ProcessType.IO_BOUND, 9, 16, 80, 1000, 0, 8, 12, null, 0, 0));
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("avilaos-lab", ".csv");
        com.unimet.avilaos.config.ConfigRepository repo = new com.unimet.avilaos.config.ConfigRepository();
        repo.save(c, tmp);
        SimulationConfig loaded = repo.load(tmp);
        java.nio.file.Files.delete(tmp);
        String a = describeConfig(c);
        String b = describeConfig(loaded);
        System.out.println("      original: " + a);
        check("guardar + cargar da la misma configuración (ciclo, quantum, latencia, computadores, RAM, "
                + "políticas, buffers y procesos)", a.equals(b));
        check("cantidades iguales (3 computadores, 2 buffers, 4 procesos)", loaded.getComputerCount() == 3
                && loaded.getBuffers().size() == 2 && loaded.getProcesses().size() == 4);
    }

    // ---------- GUI: diálogo de configuración ----------

    static void click(Container root, String text) throws Exception {
        onEdt(() -> {
            button(root, text).doClick();
            return null;
        });
    }

    /** Pulsa sin esperar: para botones que abren un diálogo modal. */
    static void clickLater(Container root, String text) {
        SwingUtilities.invokeLater(() -> button(root, text).doClick());
    }

    /** Cierra un JOptionPane abierto (por título) y devuelve su mensaje. */
    static String closeMessage(String title) throws Exception {
        JDialog d = waitDialog(title);
        if (d == null) {
            return null;
        }
        return onEdt(() -> {
            javax.swing.JOptionPane pane = findAll(d, javax.swing.JOptionPane.class).get(0);
            String msg = String.valueOf(pane.getMessage());
            d.dispose();
            return msg;
        });
    }

    static int countComputerRows(JDialog d) throws Exception {
        return onEdt(() -> {
            int n = 0;
            LinkedList<JLabel> labels = findAll(d, JLabel.class);
            for (int i = 0; i < labels.size(); i++) {
                if (labels.get(i).getText().matches("Computador \\d+")) {
                    n++;
                }
            }
            return n;
        });
    }

    static void guiConfigDialog(SimulationController controller) throws Exception {
        System.out.println("== GUI: configuración ==");
        Window oldWindow = controller.getWindow();
        // archivo a cargar con "Cargar…": 4 computadores, ciclo 30
        SimulationConfig toLoad = new SimulationConfig(30, 4, 1);
        toLoad.addComputer(1024, PolicyType.ROUND_ROBIN);
        toLoad.addComputer(1024, PolicyType.FCFS);
        toLoad.addComputer(1024, PolicyType.EDF);
        toLoad.addComputer(2048, PolicyType.PREEMPTIVE_PRIORITY);
        toLoad.addBuffer(new BufferConfig("B1", 4, 3, 64));
        toLoad.addProcess(new ProcessConfig("Prod", ProcessType.PRODUCER, 1, 64, 100, 100_000, 1, 0, 0, "B1", 2, 10));
        toLoad.addProcess(new ProcessConfig("Cons", ProcessType.CONSUMER, 1, 64, 100, 100_000, 4, 0, 0, "B1", 2, 10));
        toLoad.addProcess(cpu("Auto1", 80, 100_000, 0));
        File loadFile = new File("lab-load.csv").getAbsoluteFile();
        new com.unimet.avilaos.config.ConfigRepository().save(toLoad, loadFile.toPath());

        clickLater(oldWindow, "Configuración");
        JDialog d = waitDialog("Configuración");
        check("se abre el diálogo de configuración", d != null);
        if (d == null) {
            return;
        }
        String general = onEdt(() -> {
            LinkedList<JTextField> f = findAll(d, JTextField.class);
            return f.get(0).getText() + "/" + f.get(1).getText() + "/" + f.get(2).getText();
        });
        check("muestra ciclo/quantum/latencia actuales (20/3/2): " + general, general.equals("20/3/2"));
        check("muestra 3 computadores", countComputerRows(d) == 3);
        screenshot(d, "avilaos-config.png");

        // quitar el computador 3 deja a Cons2 sin computador: aviso y no se acepta
        clickLater(d, "Quitar último");
        String warn = closeMessage("Computador en uso");
        System.out.println("      aviso: " + (warn == null ? null : warn.replace('\n', ' ')));
        check("quitar un computador en uso avisa", warn != null && warn.contains("Cons2"));
        clickLater(d, "Aceptar");
        String err = closeMessage("Computador en uso");
        check("Aceptar con procesos huérfanos se rechaza y el diálogo sigue abierto",
                err != null && onEdt(() -> d.isShowing()));
        click(d, "Agregar computador");
        check("Agregar computador vuelve a 3", countComputerRows(d) == 3);

        // validación de campos
        onEdt(() -> {
            findAll(d, JTextField.class).get(1).setText("abc");
            return null;
        });
        clickLater(d, "Aceptar");
        String bad = closeMessage("Datos inválidos");
        System.out.println("      quantum 'abc': " + (bad == null ? null : bad.replace('\n', ' ')));
        check("quantum no numérico se rechaza", bad != null && bad.contains("Quantum"));
        onEdt(() -> {
            findAll(d, JTextField.class).get(1).setText(String.valueOf(SimulationConfig.MAX_QUANTUM + 1));
            return null;
        });
        clickLater(d, "Aceptar");
        bad = closeMessage("Datos inválidos");
        check("quantum fuera de rango se rechaza", bad != null && bad.contains("entre 1 y " + SimulationConfig.MAX_QUANTUM));
        onEdt(() -> {
            findAll(d, JTextField.class).get(1).setText("3");
            return null;
        });

        // Cargar… con el JFileChooser
        clickLater(d, "Cargar…");
        JDialog chooserDialog = waitDialog("Cargar configuración");
        check("se abre el selector de archivo", chooserDialog != null);
        if (chooserDialog != null) {
            onEdt(() -> {
                javax.swing.JFileChooser fc = findAll(chooserDialog, javax.swing.JFileChooser.class).get(0);
                fc.setSelectedFile(loadFile);
                fc.approveSelection();
                return null;
            });
            Thread.sleep(300);
            String loadedGeneral = onEdt(() -> {
                LinkedList<JTextField> f = findAll(d, JTextField.class);
                return f.get(0).getText() + "/" + f.get(1).getText() + "/" + f.get(2).getText();
            });
            check("Cargar: ciclo/quantum/latencia del archivo (30/4/1): " + loadedGeneral, loadedGeneral.equals("30/4/1"));
            check("Cargar: 4 computadores", countComputerRows(d) == 4);
        }

        // Agregar proceso con D3 inválido: el formulario lo rechaza
        clickLater(d, "Agregar proceso");
        JDialog form = waitDialog("Crear proceso");
        check("Agregar proceso abre el formulario", form != null);
        if (form != null) {
            onEdt(() -> {
                LinkedList<JTextField> f = findAll(form, JTextField.class);
                f.get(0).setText("ProdX");
                @SuppressWarnings("unchecked")
                javax.swing.JComboBox<Object> type = findAll(form, javax.swing.JComboBox.class).get(0);
                type.setSelectedItem(ProcessType.PRODUCER);
                f.get(1).setText("20");      // instrucciones
                f.get(7).setText("2");       // intervalo
                f.get(8).setText("5");       // elementos -> mínimo 35
                return null;
            });
            clickLater(form, "Crear");
            String d3 = closeMessage("Datos inválidos");
            System.out.println("      D3 en el formulario: " + (d3 == null ? null : d3.replace('\n', ' ')));
            check("el formulario rechaza instrucciones insuficientes (D3) mostrando el mínimo",
                    d3 != null && d3.contains("al menos 35"));
            onEdt(() -> {
                findAll(form, JTextField.class).get(1).setText("35");
                return null;
            });
            click(form, "Crear");
            Thread.sleep(200);
            int procs = onEdt(() -> findAll(d, javax.swing.JList.class).get(1).getModel().getSize());
            check("con 35 instrucciones se agrega a la carga inicial (4 procesos)", procs == 4);
        }

        // Agregar buffer en el computador 4 y quitarlo
        clickLater(d, "Agregar buffer");
        JDialog bform = waitDialog("Crear buffer");
        if (bform != null) {
            onEdt(() -> {
                findAll(bform, JTextField.class).get(0).setText("B9");
                findAll(bform, javax.swing.JComboBox.class).get(0).setSelectedIndex(3);
                button(bform, "Crear").doClick();
                return null;
            });
        }
        Thread.sleep(200);
        int nBuffers = onEdt(() -> findAll(d, javax.swing.JList.class).get(0).getModel().getSize());
        check("Agregar buffer: 2 buffers en la carga inicial", nBuffers == 2);
        // B1 lo usan procesos: no se puede quitar
        onEdt(() -> {
            findAll(d, javax.swing.JList.class).get(0).setSelectedIndex(0);
            return null;
        });
        clickLater(d, "Quitar buffer");
        String inUse = closeMessage("Buffer en uso");
        check("quitar un buffer en uso se rechaza", inUse != null && inUse.contains("Prod"));
        // quitar el proceso Auto1
        onEdt(() -> {
            findAll(d, javax.swing.JList.class).get(1).setSelectedIndex(2);
            button(d, "Quitar proceso").doClick();
            return null;
        });
        int procsAfter = onEdt(() -> findAll(d, javax.swing.JList.class).get(1).getModel().getSize());
        check("Quitar proceso: quedan 3", procsAfter == 3);

        // Guardar en avilaos-config.csv (el directorio de trabajo del lab, fuera del repo)
        File saved = new File(com.unimet.avilaos.config.ConfigRepository.DEFAULT_FILE).getAbsoluteFile();
        saved.delete();
        clickLater(d, "Guardar");
        String savedMsg = closeMessage("Guardar");
        check("Guardar muestra confirmación", savedMsg != null && savedMsg.contains(saved.getName()));
        SimulationConfig reread = new com.unimet.avilaos.config.ConfigRepository().load(saved.toPath());
        System.out.println("      guardado: " + describeConfig(reread));
        check("el archivo guardado tiene 4 computadores, 2 buffers y 3 procesos", reread.getComputerCount() == 4
                && reread.getBuffers().size() == 2 && reread.getProcesses().size() == 3);
        screenshot(d, "avilaos-config-loaded.png");

        // Aceptar: cierra la ventana vieja y abre una nueva con 4 computadores
        click(d, "Aceptar");
        Thread.sleep(500);
        check("la ventana vieja se cerró", onEdt(() -> !oldWindow.isDisplayable()));
        Window newWindow = onEdt(() -> {
            for (Window w : Window.getWindows()) {
                if (w != oldWindow && w.isShowing() && w instanceof javax.swing.JFrame) {
                    return w;
                }
            }
            return null;
        });
        check("se abrió una ventana nueva", newWindow != null);
        if (newWindow == null) {
            return;
        }
        int panels = onEdt(() -> findAll((Container) newWindow, com.unimet.avilaos.gui.ComputerPanel.class).size());
        check("la ventana nueva tiene 4 computadores", panels == 4);
        JTable newMetrics = onEdt(() -> tableWithModel((Container) newWindow, "MetricsTableModel"));
        check("métricas de la ventana nueva: 5 filas", onEdt(() -> newMetrics.getRowCount()) == 5);
        JTable newBuffers = onEdt(() -> tableWithModel((Container) newWindow, "BufferTableModel"));
        check("buffers de la ventana nueva: 2 filas", onEdt(() -> newBuffers.getRowCount()) == 2);
        click((Container) newWindow, "Iniciar");
        Thread.sleep(1000);
        String label = onEdt(() -> findLabelStarting((Container) newWindow, "Ciclo: "));
        check("la simulación nueva avanza (" + label + ")", label != null && !label.equals("Ciclo: 0"));
        screenshot(newWindow, "avilaos-new-config.png");
        click((Container) newWindow, "Pausar");
        new File("lab-load.csv").delete();
    }

    // ---------- Estrés: llegadas -> Nuevos atómico ----------

    static boolean logContains(Cluster cluster, String text) {
        LinkedList<com.unimet.avilaos.events.LogEntry> entries = cluster.getLog().getEntries();
        while (!entries.isEmpty()) {
            if (entries.removeFirst().format().contains(text)) {
                return true;
            }
        }
        return false;
    }

    static void arrivalsStress() throws InterruptedException {
        System.out.println("== Estrés: buffer vs proceso que está entrando ==");
        int rounds = Integer.getInteger("lab.rounds", 1500);
        java.util.Random random = new java.util.Random();
        int bufferAccepted = 0;
        int processRejected = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < rounds; i++) {
            SimulationConfig config = new SimulationConfig(10, 3, 0);
            config.addComputer(1024, PolicyType.FCFS);
            config.addComputer(1024, PolicyType.FCFS);
            Cluster cluster = Cluster.create(config);
            cluster.setCycleDurationMs(1);
            cluster.start();
            cluster.createProcess(new ProcessConfig("Grande", ProcessType.CPU_BOUND, 1, 900, 5_000, 100_000, 1,
                    0, 0, null, 0, 0));
            long spin = (long) (random.nextDouble() * 1_200_000);          // 0 a 1.2 ms
            long until = System.nanoTime() + spin;
            while (System.nanoTime() < until) {
                Thread.onSpinWait();
            }
            try {
                cluster.createBuffer(new BufferConfig("B1", 1, 5, 200));
                bufferAccepted++;
            } catch (IllegalArgumentException expected) {
                // lo correcto: con el proceso de 900 MB no caben 200 MB de buffer
            }
            Thread.sleep(3);                                                // que el kernel procese la llegada
            if (logContains(cluster, "rechaza el proceso")) {
                processRejected++;
            }
            cluster.shutdown();
        }
        System.out.println("      " + rounds + " rondas en " + (System.currentTimeMillis() - start) + " ms: buffers aceptados="
                + bufferAccepted + ", procesos rechazados=" + processRejected);
        check("estrés: el buffer de 200 MB se rechaza SIEMPRE", bufferAccepted == 0);
        check("estrés: el log nunca dice 'rechaza el proceso'", processRejected == 0);
    }

    // ---------- GUI: pausa automática ----------

    static void autoPause() throws Exception {
        System.out.println("== GUI: pausa automática ==");
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("      sin pantalla: se omite");
            return;
        }
        SimulationConfig config = new SimulationConfig(10, 3, 0);
        config.addComputer(1024, PolicyType.ROUND_ROBIN);
        config.addComputer(1024, PolicyType.FCFS);
        config.addProcess(cpu("Corto1", 15, 100_000, 1));
        config.addProcess(cpu("Corto2", 20, 100_000, 2));
        SimulationController controller = onEdt(() -> {
            SimulationController c = new SimulationController(config);
            c.show();
            return c;
        });
        Container window = controller.getWindow();
        click(window, "Iniciar");
        long end = System.currentTimeMillis() + 10_000;
        while (!(controller.getCluster().isPaused() && controller.getCluster().getTotalLoad() == 0)
                && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
        Cluster cluster = controller.getCluster();
        check("los 2 procesos terminan (carga total 0)", cluster.getTotalLoad() == 0 && terminatedCount(cluster) == 2);
        check("el clúster queda en pausa solo", cluster.isPaused());
        long pausedAt = cluster.getCurrentCycle();
        Thread.sleep(500);
        System.out.println("      pausado en el ciclo " + pausedAt + " (terminados a tiempo: "
                + cluster.getSystemMetrics().getSnapshot().completedProcesses() + ")");
        check("el ciclo ya no avanza", cluster.getCurrentCycle() == pausedAt);
        check("el botón vuelve a decir Iniciar", onEdt(() -> button(window, "Iniciar")) != null);
        check("el log dice que se pausó", logContains(cluster, "todos los procesos terminaron: la simulación se pausa"));
        check("pocos ciclos ociosos tras el último proceso (<= 30)", onEdt(() -> {
            long lastFinish = 0;
            for (int id = 1; id <= cluster.getComputerCount(); id++) {
                LinkedList<Pcb> t = cluster.getComputer(id).getKernel().getTerminatedSnapshot();
                for (int i = 0; i < t.size(); i++) {
                    lastFinish = Math.max(lastFinish, t.get(i).getFinishCycle());
                }
            }
            System.out.println("      último fin en el ciclo " + lastFinish);
            return pausedAt - lastFinish <= 30;
        }));

        click(window, "Iniciar");
        Thread.sleep(500);
        check("Iniciar reanuda: no está en pausa y el ciclo avanza",
                !cluster.isPaused() && cluster.getCurrentCycle() > pausedAt);
        check("el botón dice Pausar", onEdt(() -> button(window, "Pausar")) != null);
        Thread.sleep(500);
        check("sin trabajo nuevo no vuelve a pausarse", !cluster.isPaused());
        onEdt(() -> {
            controller.getCluster().shutdown();
            controller.getWindow().dispose();
            return null;
        });
    }

    /** Un proceso de 1 instrucción termina antes del primer refresco: igual debe pausar. */
    static void autoPauseShort() throws Exception {
        System.out.println("== GUI: pausa automática con un proceso que termina antes del primer refresco ==");
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("      sin pantalla: se omite");
            return;
        }
        int paused = 0;
        for (int run = 1; run <= 8; run++) {
            SimulationConfig config = new SimulationConfig(10, 3, 0);
            config.addComputer(1024, PolicyType.ROUND_ROBIN);
            config.addComputer(1024, PolicyType.FCFS);
            config.addProcess(cpu("Uno", 1, 100_000, 1));
            SimulationController controller = onEdt(() -> {
                SimulationController c = new SimulationController(config);
                c.show();
                return c;
            });
            click(controller.getWindow(), "Iniciar");
            Thread.sleep(1500);
            Cluster cluster = controller.getCluster();
            boolean ok = cluster.isPaused() && cluster.getTotalLoad() == 0;
            System.out.println("      corrida " + run + ": " + (ok ? "en pausa" : "SIGUE CORRIENDO")
                    + " en el ciclo " + cluster.getCurrentCycle());
            if (ok) {
                paused++;
            }
            onEdt(() -> {
                controller.getCluster().shutdown();
                controller.getWindow().dispose();
                return null;
            });
        }
        check("proceso de 1 instrucción: las 8 corridas quedan en pausa (" + paused + "/8)", paused == 8);
    }

    // ---------- Config de ejemplo para la demo ----------

    static final String SAMPLE_FILE = System.getProperty("lab.sample",
            "/Users/victor1102/Documents/Java/AvilaOS/" + com.unimet.avilaos.config.ConfigRepository.DEFAULT_FILE);

    static ProcessConfig buffer(String name, ProcessType type, int priority, int memory, int instructions,
            int computer, String buffer, int interval, int items) {
        return new ProcessConfig(name, type, priority, memory, instructions, 5_000, computer, 0, 0, buffer, interval, items);
    }

    /** La carga de la demo: cada buffer produce exactamente lo que se consume. */
    static SimulationConfig sampleConfig() {
        SimulationConfig c = new SimulationConfig(300, 3, 2);
        c.addComputer(1024, PolicyType.ROUND_ROBIN);
        c.addComputer(1024, PolicyType.FCFS);
        c.addComputer(768, PolicyType.EDF);
        c.addBuffer(new BufferConfig("B1", 1, 3, 32));
        c.addBuffer(new BufferConfig("B2", 2, 2, 16));
        // B1: 8 + 6 producidos = 14 consumidos (mínimos D3: 64, 54 y 98)
        c.addProcess(buffer("ProdLocalB1", ProcessType.PRODUCER, 2, 64, 80, 1, "B1", 3, 8));
        c.addProcess(buffer("ProdRemotoB1", ProcessType.PRODUCER, 2, 64, 70, 2, "B1", 4, 6));
        c.addProcess(buffer("ConsRemotoB1", ProcessType.CONSUMER, 2, 64, 120, 3, "B1", 2, 14));
        // B2: 10 producidos = 10 consumidos (mínimos D3: 80 y 90)
        c.addProcess(buffer("ProdB2", ProcessType.PRODUCER, 3, 48, 100, 2, "B2", 3, 10));
        c.addProcess(buffer("ConsB2", ProcessType.CONSUMER, 3, 48, 110, 2, "B2", 4, 10));
        // Computador 1: dos CPU bound y un I/O bound con prioridades distintas
        c.addProcess(new ProcessConfig("Calculo", ProcessType.CPU_BOUND, 1, 128, 60, 5_000, 1, 0, 0, null, 0, 0));
        c.addProcess(new ProcessConfig("Compilador", ProcessType.CPU_BOUND, 4, 256, 90, 5_000, 1, 0, 0, null, 0, 0));
        c.addProcess(new ProcessConfig("Lector", ProcessType.IO_BOUND, 2, 64, 40, 5_000, 1, 5, 4, null, 0, 0));
        // Automáticos: ÁvilaOS elige el computador
        c.addProcess(new ProcessConfig("AutoCalculo", ProcessType.CPU_BOUND, 3, 96, 50, 5_000, 0, 0, 0, null, 0, 0));
        c.addProcess(new ProcessConfig("AutoDisco", ProcessType.IO_BOUND, 3, 32, 30, 5_000, 0, 6, 3, null, 0, 0));
        // Deadline corto: no le alcanza (200 instrucciones, 40 ciclos) y termina por deadline
        c.addProcess(new ProcessConfig("Urgente", ProcessType.CPU_BOUND, 0, 64, 200, 40, 3, 0, 0, null, 0, 0));
        c.validate();
        return c;
    }

    static void sampleRun() throws Exception {
        System.out.println("== Config de ejemplo (" + SAMPLE_FILE + ") ==");
        SimulationConfig config = new com.unimet.avilaos.config.ConfigRepository().load(java.nio.file.Path.of(SAMPLE_FILE));
        check("se carga con ConfigRepository.load", config != null);
        check("es igual a la que se generó", describeConfig(config).equals(describeConfig(sampleConfig())));
        check("ciclo 300 / quantum 3 / latencia 2", config.getCycleDurationMs() == 300 && config.getQuantum() == 3
                && config.getNetworkLatency() == 2);
        Cluster cluster = Cluster.create(config);
        check("Cluster.create la acepta", cluster != null);
        int total = config.getProcesses().size();
        boolean done = runUntilDone(cluster, total, 30_000);
        check("terminan los " + total + " procesos (ciclo " + cluster.getCurrentCycle() + ")", done);

        LinkedList<BoundedBuffer> buffers = cluster.getBuffers();
        LinkedList<BufferStats> stats = cluster.getSystemMetrics().getBufferStats();
        long[] expected = {14, 10};
        for (int i = 0; i < buffers.size(); i++) {
            BoundedBuffer b = buffers.get(i);
            BufferStats st = null;
            for (int j = 0; j < stats.size(); j++) {
                if (stats.get(j).getBufferName().equalsIgnoreCase(b.getName())) {
                    st = stats.get(j);
                }
            }
            int empty = b.getEmpty().getValue();
            int full = b.getFull().getValue();
            System.out.printf("      %s: vacíos=%d lleno=%d mutex=%d elementos=%d/%d producidos=%d consumidos=%d%n",
                    b.getName(), empty, full, b.getMutex().getValue(), b.getItemCount(), b.getCapacity(),
                    st == null ? -1 : st.getProduced(), st == null ? -1 : st.getConsumed());
            check(b.getName() + " consistente (vacíos + lleno = capacidad, lleno = elementos = 0, mutex = 1, nadie espera)",
                    empty + full == b.getCapacity() && full == b.getItemCount() && full == 0
                    && b.getMutex().getValue() == 1 && b.getEmpty().getWaitingCount() == 0
                    && b.getFull().getWaitingCount() == 0 && b.getMutex().getWaitingCount() == 0);
            check(b.getName() + ": producidos = consumidos = " + expected[i], st != null
                    && st.getProduced() == expected[i] && st.getConsumed() == expected[i]);
        }

        StringBuilder missed = new StringBuilder();
        boolean itemsOk = true;
        for (int id = 1; id <= cluster.getComputerCount(); id++) {
            LinkedList<Pcb> t = cluster.getComputer(id).getKernel().getTerminatedSnapshot();
            for (int i = 0; i < t.size(); i++) {
                Pcb p = t.get(i);
                if (p.isDeadlineMissed()) {
                    missed.append(p.getName()).append(' ');
                }
                if (p.getType().usesBuffer() && p.getItemsDone() != p.getItemsRequired()) {
                    itemsOk = false;
                    System.out.println("      " + p.getName() + " hizo " + p.getItemsDone() + "/" + p.getItemsRequired());
                }
            }
        }
        System.out.println("      terminados por deadline: " + missed);
        check("solo Urgente termina por deadline", missed.toString().trim().equals("Urgente"));
        check("cada productor/consumidor completó sus elementos", itemsOk);
        MetricsSnapshot m = cluster.getSystemMetrics().getSnapshot();
        System.out.printf("      Sistema: term=%d thr=%.4f cpu=%.3f resp=%.2f ret=%.2f dl=%.3f jain=%.3f sem=%.2f%n",
                m.completedProcesses(), m.throughput(), m.cpuUtilization(), m.averageResponseTime(),
                m.averageTurnaroundTime(), m.deadlineComplianceRate(), m.fairnessIndex(), m.averageSemaphoreBlockedTime());
        check("cumplimiento de deadlines < 100% (se ve el que venció)", m.deadlineComplianceRate() < 1.0);
        cluster.shutdown();
    }
}
