package com.unimet.avilaos.gui;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.CpuMode;
import com.unimet.avilaos.os.process.Pcb;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/**
 * "Vista 2": estado de UN computador del clúster.
 *
 * Se crea una instancia por computador (igual que la clase Computer del modelo),
 * así que mostrar 2, 3 o 4 computadores es solo crear más paneles.
 * El panel no decide nada: recibe datos con los métodos show* y avisa
 * al exterior cuando el usuario cambia la política.
 *
 * @author NicollPinzon
 */
public class ComputerPanel extends JPanel {

    private static final Color USER_COLOR = new Color(0, 128, 0);
    private static final Color KERNEL_COLOR = new Color(200, 100, 0);

    private final int computerId;

    // ---------- CPU ----------
    private final JLabel processLabel = new JLabel("—");
    private final JLabel pcLabel = new JLabel("—");
    private final JLabel priorityLabel = new JLabel("—");
    private final JLabel deadlineLabel = new JLabel("—");
    private final JLabel modeLabel = new JLabel("—");

    // ---------- Memoria ----------
    private final JProgressBar memoryBar = new JProgressBar(0, 100);

    // ---------- Política ----------
    private final JComboBox<String> policyCombo;

    // ---------- Colas ----------
    private final ProcessTableModel newModel = new ProcessTableModel();
    private final ProcessTableModel readyModel = new ProcessTableModel();
    private final ProcessTableModel blockedModel = new ProcessTableModel();
    private final ProcessTableModel terminatedModel = new ProcessTableModel();

    /**
     * @param computerId número del computador (1, 2, ...)
     * @param policies   nombres de las políticas disponibles para el selector
     */
    public ComputerPanel(int computerId, String[] policies) {
        this.computerId = computerId;
        this.policyCombo = new JComboBox<>(policies);

        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "Computador " + computerId,
                0, 0, new Font(Font.SANS_SERIF, Font.BOLD, 14)));

        add(buildTopSection(), BorderLayout.NORTH);
        add(buildQueuesSection(), BorderLayout.CENTER);
    }

    // ---------- Construcción de la vista ----------

    private JPanel buildTopSection() {
        JPanel top = new JPanel(new GridLayout(1, 3, 8, 0));

        // CPU
        JPanel cpu = new JPanel(new GridLayout(5, 2, 4, 2));
        cpu.setBorder(BorderFactory.createTitledBorder("CPU"));
        addRow(cpu, "Proceso:", processLabel);
        addRow(cpu, "PC:", pcLabel);
        addRow(cpu, "Prioridad:", priorityLabel);
        addRow(cpu, "Deadline:", deadlineLabel);
        addRow(cpu, "Modo:", modeLabel);
        modeLabel.setFont(modeLabel.getFont().deriveFont(Font.BOLD));

        // Memoria
        JPanel memory = new JPanel(new BorderLayout());
        memory.setBorder(BorderFactory.createTitledBorder("Memoria principal"));
        memoryBar.setStringPainted(true);
        memoryBar.setString("—");
        memory.add(memoryBar, BorderLayout.CENTER);

        // Política
        JPanel policy = new JPanel(new BorderLayout());
        policy.setBorder(BorderFactory.createTitledBorder("Política de planificación"));
        policy.add(policyCombo, BorderLayout.NORTH);

        top.add(cpu);
        top.add(memory);
        top.add(policy);
        return top;
    }

    private JPanel buildQueuesSection() {
        JPanel queues = new JPanel(new GridLayout(2, 2, 6, 6));
        queues.add(wrapTable("Nuevos", newModel));
        queues.add(wrapTable("Listos", readyModel));
        queues.add(wrapTable("Bloqueados", blockedModel));
        queues.add(wrapTable("Terminados", terminatedModel));
        return queues;
    }

    private static JScrollPane wrapTable(String title, ProcessTableModel model) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder(title));
        scroll.setPreferredSize(new Dimension(600, 120));
        return scroll;
    }

    private static void addRow(JPanel panel, String label, JLabel value) {
        panel.add(new JLabel(label));
        panel.add(value);
    }

    // ---------- Métodos que usa quien alimenta la vista ----------
    // Todos deben llamarse desde el hilo de Swing (SwingUtilities.invokeLater).

    /** Muestra el proceso en CPU, o "Ociosa" si running es null. */
    public void showRunning(Pcb running, CpuMode mode) {
        if (running == null) {
            processLabel.setText("Ociosa");
            pcLabel.setText("—");
            priorityLabel.setText("—");
            deadlineLabel.setText("—");
        } else {
            processLabel.setText(running + " (" + running.getName() + ")");
            pcLabel.setText(String.valueOf(running.getProgramCounter()));
            priorityLabel.setText(String.valueOf(running.getPriority()));
            deadlineLabel.setText(running.getRemainingDeadline() + " ciclos");
        }
        modeLabel.setText(mode.getLabel());
        modeLabel.setForeground(mode == CpuMode.USER ? USER_COLOR : KERNEL_COLOR);
    }

    /** Muestra la ocupación de la RAM. */
    public void showMemory(int usedMb, int totalMb) {
        int percent = totalMb == 0 ? 0 : (int) Math.round(usedMb * 100.0 / totalMb);
        memoryBar.setValue(percent);
        memoryBar.setString("Usada " + usedMb + " MB / Libre " + (totalMb - usedMb)
                + " MB (" + percent + "%)");
    }

    /** Muestra las cuatro colas. El orden de cada tabla es el orden real de la cola. */
    public void showQueues(LinkedList<Pcb> newQueue, LinkedList<Pcb> readyQueue,
            LinkedList<Pcb> blockedQueue, LinkedList<Pcb> terminatedQueue) {
        newModel.setProcesses(newQueue);
        readyModel.setProcesses(readyQueue);
        blockedModel.setProcesses(blockedQueue);
        terminatedModel.setProcesses(terminatedQueue);
    }

    /** Registra qué hacer cuando el usuario cambia la política en el selector. */
    public void onPolicyChanged(Consumer<String> action) {
        policyCombo.addActionListener(e -> action.accept((String) policyCombo.getSelectedItem()));
    }

    public int getComputerId() {
        return computerId;
    }
}
