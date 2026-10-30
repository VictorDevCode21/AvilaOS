package com.unimet.avilaos.gui;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.CpuMode;
import com.unimet.avilaos.os.process.Pcb;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

/**
 * "Vista 2": estado de UN computador del clúster.
 *
 * Se crea una instancia por computador (igual que la clase Computer del modelo),
 * así que mostrar 2, 3 o 4 computadores es solo crear más paneles.
 * El panel no decide nada: recibe datos con los métodos show* y avisa
 * al exterior cuando el USUARIO cambia la política.
 *
 * @author NicollPinzon
 */
public final class ComputerPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color USER_COLOR = new Color(0, 128, 0);
    private static final Color KERNEL_COLOR = new Color(200, 100, 0);
    private static final String NONE = "—";

    private final int computerId;

    // ---------- CPU (PCB del proceso en ejecución) ----------
    private final JLabel processLabel = new JLabel(NONE);
    private final JLabel typeLabel = new JLabel(NONE);
    private final JLabel pcLabel = new JLabel(NONE);
    private final JLabel marLabel = new JLabel(NONE);
    private final JLabel priorityLabel = new JLabel(NONE);
    private final JLabel deadlineLabel = new JLabel(NONE);
    private final JLabel remainingLabel = new JLabel(NONE);
    private final JLabel memoryLabel = new JLabel(NONE);
    private final JLabel modeLabel = new JLabel(NONE);

    // ---------- Memoria ----------
    private final JProgressBar memoryBar = new JProgressBar(0, 100);

    // ---------- Política ----------
    private final JComboBox<String> policyCombo;
    private boolean updatingPolicy;          // true mientras la GUI cambia el selector por código

    // ---------- Colas ----------
    private final ProcessTableModel newModel = new ProcessTableModel();
    private final ProcessTableModel readyModel = new ProcessTableModel();
    private final ProcessTableModel blockedModel = new ProcessTableModel();
    private final ProcessTableModel terminatedModel = new ProcessTableModel();

    /**
     * @param computerId   número del computador (1, 2, ...)
     * @param policyLabels etiquetas de las políticas para el selector (PolicyType.labels())
     */
    public ComputerPanel(int computerId, String[] policyLabels) {
        this.computerId = computerId;
        this.policyCombo = new JComboBox<>(policyLabels);

        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "Computador " + computerId,
                0, 0, new Font(Font.SANS_SERIF, Font.BOLD, 14)));

        add(buildTopSection(), BorderLayout.NORTH);
        add(buildQueuesSection(), BorderLayout.CENTER);
        showIdle();
    }

    // ---------- Construcción de la vista ----------

    private JPanel buildTopSection() {
        // CPU: dos pares etiqueta-valor por fila para que quepa a lo ancho
        JPanel cpu = new JPanel(new GridLayout(5, 4, 6, 2));
        cpu.setBorder(BorderFactory.createTitledBorder("CPU"));
        addPair(cpu, "Proceso:", processLabel);
        addPair(cpu, "Modo:", modeLabel);
        addPair(cpu, "Tipo:", typeLabel);
        addPair(cpu, "Prioridad:", priorityLabel);
        addPair(cpu, "PC:", pcLabel);                          // registros de la CPU, juntos
        addPair(cpu, "MAR:", marLabel);
        addPair(cpu, "Deadline:", deadlineLabel);
        addPair(cpu, "Instr. rest.:", remainingLabel);
        addPair(cpu, "Memoria:", memoryLabel);
        cpu.add(new JLabel());                                  // relleno de la última fila
        cpu.add(new JLabel());
        modeLabel.setFont(modeLabel.getFont().deriveFont(Font.BOLD));

        JPanel memory = new JPanel(new BorderLayout());
        memory.setBorder(BorderFactory.createTitledBorder("Memoria principal"));
        memoryBar.setStringPainted(true);
        memoryBar.setString(NONE);
        memory.add(memoryBar, BorderLayout.CENTER);

        JPanel policy = new JPanel(new BorderLayout());
        policy.setBorder(BorderFactory.createTitledBorder("Política de planificación"));
        policy.add(policyCombo, BorderLayout.CENTER);

        JPanel side = new JPanel(new GridLayout(2, 1, 0, 4));
        side.add(memory);
        side.add(policy);

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(cpu, BorderLayout.CENTER);
        top.add(side, BorderLayout.EAST);
        side.setPreferredSize(new Dimension(230, 120));       // alto suficiente para la barra y el selector
        return top;
    }

    private JPanel buildQueuesSection() {
        // Una cola debajo de la otra: cada tabla usa todo el ancho y el motivo no se corta
        JPanel queues = new JPanel(new GridLayout(4, 1, 0, 4));
        queues.add(wrapTable("Nuevos", newModel));
        queues.add(wrapTable("Listos", readyModel));
        queues.add(wrapTable("Bloqueados", blockedModel));
        queues.add(wrapTable("Terminados", terminatedModel));
        return queues;
    }

    private static JScrollPane wrapTable(String title, ProcessTableModel model) {
        JTable table = new JTable(model) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent e) {        // texto completo al pasar el mouse
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());
                if (row < 0 || col < 0) {
                    return null;
                }
                Object value = getValueAt(row, col);
                return value == null ? null : value.toString();
            }
        };
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        table.setTableHeader(new JTableHeader(table.getColumnModel()) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent e) {        // nombre completo de la columna
                int col = columnAtPoint(e.getPoint());
                return col < 0 ? null : ProcessTableModel.COLUMN_TIPS[col];
            }
        });
        table.getTableHeader().setReorderingAllowed(false);
        TableColumnModel columns = table.getColumnModel();
        int[] widths = {55, 90, 75, 40, 45, 50, 50, 45, 230};
        for (int i = 0; i < widths.length; i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder(title));
        scroll.setPreferredSize(new Dimension(480, 95));       // las columnas se ajustan al ancho real
        return scroll;
    }

    private static void addPair(JPanel panel, String label, JLabel value) {
        panel.add(new JLabel(label));
        panel.add(value);
    }

    // ---------- Métodos que usa quien alimenta la vista ----------
    // Todos deben llamarse desde el hilo de Swing (SwingUtilities.invokeLater).

    /**
     * Muestra el proceso en CPU con los registros de la CPU.
     * PC y MAR vienen de la Cpu (no del PCB): el PCB solo guarda el PC al salir de la CPU.
     *
     * @param running proceso en CPU, o null si está ociosa
     * @param mode    modo de la CPU (usuario o kernel)
     * @param pc      Cpu.getProgramCounter()
     * @param mar     Cpu.getMemoryAddressRegister()
     */
    public void showRunning(Pcb running, CpuMode mode, int pc, int mar) {
        if (running == null) {
            showIdle();
            return;
        }
        processLabel.setText(running + " (" + running.getName() + ")");
        typeLabel.setText(running.getType().getLabel());
        pcLabel.setText(String.valueOf(pc));
        marLabel.setText(String.valueOf(mar));
        priorityLabel.setText(String.valueOf(running.getPriority()));
        deadlineLabel.setText(running.getRemainingDeadline() + " ciclos");
        remainingLabel.setText(String.valueOf(running.getRemainingInstructions()));
        memoryLabel.setText(running.getMemorySize() + " MB");
        modeLabel.setText(mode.getLabel());
        modeLabel.setForeground(mode == CpuMode.USER ? USER_COLOR : KERNEL_COLOR);
    }

    /** CPU sin proceso: todo en "—". */
    public void showIdle() {
        processLabel.setText("Ociosa");
        for (JLabel label : new JLabel[] {typeLabel, pcLabel, marLabel, priorityLabel, deadlineLabel,
            remainingLabel, memoryLabel, modeLabel}) {
            label.setText(NONE);
        }
        modeLabel.setForeground(getForeground());
    }

    /** Muestra la ocupación de la RAM. */
    public void showMemory(int usedMb, int totalMb) {
        int percent = totalMb == 0 ? 0 : (int) Math.round(usedMb * 100.0 / totalMb);
        memoryBar.setValue(percent);
        // El enunciado pide ver la ocupación "usada / libre"
        memoryBar.setString("Usada " + usedMb + " · Libre " + (totalMb - usedMb) + " MB");
        memoryBar.setToolTipText(percent + "% de " + totalMb + " MB en uso");
    }

    /**
     * Muestra las cuatro colas. El orden de cada tabla es el orden real de la cola.
     * Recibe COPIAS tomadas con el candado de su dueño (ver ProcessTableModel).
     */
    public void showQueues(LinkedList<Pcb> newQueue, LinkedList<Pcb> readyQueue,
            LinkedList<Pcb> blockedQueue, LinkedList<Pcb> terminatedQueue) {
        newModel.setProcesses(newQueue);
        readyModel.setProcesses(readyQueue);
        blockedModel.setProcesses(blockedQueue);
        terminatedModel.setProcesses(terminatedQueue);
    }

    /**
     * Pone en el selector la política que tiene el computador (ej. la de la configuración).
     * NO avisa a onPolicyChanged: solo los cambios hechos por el usuario se reportan.
     *
     * @param label etiqueta de la política (PolicyType.getLabel())
     */
    public void showPolicy(String label) {
        updatingPolicy = true;
        try {
            policyCombo.setSelectedItem(label);
        } finally {
            updatingPolicy = false;
        }
    }

    /**
     * Registra qué hacer cuando el USUARIO cambia la política en el selector.
     * Recibe la etiqueta; el clúster la convierte con PolicyType.fromLabel(...).
     */
    public void onPolicyChanged(PolicyChangeListener listener) {
        policyCombo.addActionListener(e -> {
            if (!updatingPolicy) {
                listener.policyChanged((String) policyCombo.getSelectedItem());
            }
        });
    }

    /** Deja el panel como recién creado (ej. al reiniciar la simulación). */
    public void clear() {
        showIdle();
        showMemory(0, 0);
        memoryBar.setString(NONE);
        newModel.clear();
        readyModel.clear();
        blockedModel.clear();
        terminatedModel.clear();
    }

    public int getComputerId() {
        return computerId;
    }
}
