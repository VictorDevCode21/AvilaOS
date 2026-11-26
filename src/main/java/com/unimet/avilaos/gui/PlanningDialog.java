package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.os.scheduling.PolicyType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

/**
 * Ventana "Planificación": configura la rutina de planificación a corto plazo de
 * cada computador (su política) y el quantum de Round Robin del sistema.
 *
 * No es modal: se puede dejar abierta mientras corre la simulación. Muestra, por
 * computador, la política ELEGIDA (editable), la que el kernel tiene AHORA y
 * cuántos procesos esperan en listos. Un cambio aplicado lo toma el kernel al
 * inicio del próximo ciclo; mientras tanto la fila dice "pendiente".
 *
 * La ventana no toca el SO: al pulsar "Aplicar" avisa al PlanningListener
 * (SimulationController), que se lo pide al clúster.
 *
 * @author donatovodola
 */
public final class PlanningDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final Color PENDING_COLOR = new Color(200, 100, 0);
    private static final Color OK_COLOR = new Color(0, 128, 0);

    /** Qué hacer al pulsar "Aplicar". */
    @FunctionalInterface
    public interface PlanningListener {

        /**
         * @param policies política elegida por computador (índice 0 = Computador 1)
         * @param quantum  quantum de Round Robin para todos los computadores
         */
        void apply(PolicyType[] policies, int quantum);
    }

    private final JSpinner quantumSpinner = new JSpinner(new SpinnerNumberModel(1, 1,
            SimulationConfig.MAX_QUANTUM, 1));
    private final JComboBox<String> allCombo = new JComboBox<>(PolicyType.labels());
    private final JLabel quantumStatus = new JLabel();

    private final JComboBox<String>[] policyCombos;
    private final JLabel[] currentLabels;
    private final JLabel[] readyLabels;

    // Lo último que el kernel reporta (para "Restablecer" y para saber si algo está pendiente)
    private final PolicyType[] currentPolicies;
    private final PolicyType[] requestedPolicies;
    private int currentQuantum = 1;
    private int requestedQuantum;

    private transient PlanningListener listener;

    /**
     * @param owner         ventana principal
     * @param computerCount número de computadores del clúster
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public PlanningDialog(Frame owner, int computerCount) {
        super(owner, "Planificación", false);
        policyCombos = new JComboBox[computerCount];
        currentLabels = new JLabel[computerCount];
        readyLabels = new JLabel[computerCount];
        currentPolicies = new PolicyType[computerCount];
        requestedPolicies = new PolicyType[computerCount];

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.add(buildComputersSection(), BorderLayout.CENTER);
        north.add(buildQuantumSection(), BorderLayout.SOUTH);
        content.add(north, BorderLayout.NORTH);
        content.add(buildPoliciesHelp(), BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().registerKeyboardAction(e -> setVisible(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    // ---------- Construcción ----------

    private JPanel buildComputersSection() {
        JPanel grid = new JPanel(new GridLayout(0, 4, 10, 4));
        grid.add(bold("Computador"));
        grid.add(bold("Política elegida"));
        grid.add(bold("Política actual"));
        grid.add(bold("En listos"));
        for (int i = 0; i < policyCombos.length; i++) {
            policyCombos[i] = new JComboBox<>(PolicyType.labels());
            currentLabels[i] = new JLabel("—");
            readyLabels[i] = new JLabel("—");
            grid.add(new JLabel("Computador " + (i + 1)));
            grid.add(policyCombos[i]);
            grid.add(currentLabels[i]);
            grid.add(readyLabels[i]);
        }

        JButton copyButton = new JButton("Usar en todos");
        copyButton.setToolTipText("Pone esta política en todos los computadores (falta pulsar Aplicar)");
        copyButton.addActionListener(e -> {
            for (JComboBox<String> combo : policyCombos) {
                combo.setSelectedItem(allCombo.getSelectedItem());
            }
        });
        JPanel all = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        all.add(new JLabel("Misma política para todos:"));
        all.add(allCombo);
        all.add(copyButton);

        JPanel section = new JPanel(new BorderLayout(0, 8));
        section.setBorder(BorderFactory.createTitledBorder("Política de cada computador"));
        section.add(grid, BorderLayout.CENTER);
        section.add(all, BorderLayout.SOUTH);
        return section;
    }

    private JPanel buildQuantumSection() {
        JPanel section = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        section.setBorder(BorderFactory.createTitledBorder("Quantum de Round Robin (todo el sistema)"));
        section.add(new JLabel("Ciclos por turno:"));
        section.add(quantumSpinner);
        section.add(quantumStatus);
        quantumSpinner.setToolTipText("Solo lo usan los computadores con Round Robin; los demás lo ignoran");
        return section;
    }

    /** Tabla de referencia: cómo decide cada política (para elegir con criterio durante la demo). */
    private static JScrollPane buildPoliciesHelp() {
        String[] columns = {"Política", "Elige primero a…", "Apropiativa", "Usa quantum"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (PolicyType type : PolicyType.values()) {
            model.addRow(new Object[] {type.getLabel(), criterion(type), preemptive(type),
                type == PolicyType.ROUND_ROBIN ? "Sí" : "No"});
        }
        JTable table = new JTable(model);
        table.setRowSelectionAllowed(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(280);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(95);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createTitledBorder("Cómo decide cada política"));
        scroll.setPreferredSize(new java.awt.Dimension(760, 125));
        return scroll;
    }

    private static String criterion(PolicyType type) {
        return switch (type) {
            case FCFS -> "el que llegó antes a listos";
            case ROUND_ROBIN -> "el que llegó antes, por turnos de 'quantum' ciclos";
            case EDF -> "el de deadline más cercano";
            case PREEMPTIVE_PRIORITY -> "el de mayor prioridad (número menor)";
        };
    }

    private static String preemptive(PolicyType type) {
        return switch (type) {
            case FCFS -> "No";
            case ROUND_ROBIN -> "Sí, al agotar el quantum";
            case EDF -> "Sí, si llega un deadline menor";
            case PREEMPTIVE_PRIORITY -> "Sí, si llega más prioridad";
        };
    }

    private JPanel buildButtons() {
        JButton applyButton = new JButton("Aplicar");
        JButton restoreButton = new JButton("Restablecer");
        JButton closeButton = new JButton("Cerrar");
        applyButton.setToolTipText("El kernel de cada computador lo toma al inicio del próximo ciclo");
        restoreButton.setToolTipText("Vuelve a poner lo que tiene cada computador ahora");
        applyButton.addActionListener(e -> apply());
        restoreButton.addActionListener(e -> restore());
        closeButton.addActionListener(e -> setVisible(false));
        getRootPane().setDefaultButton(applyButton);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(restoreButton);
        buttons.add(applyButton);
        buttons.add(closeButton);
        return buttons;
    }

    private static JLabel bold(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    // ---------- Acciones ----------

    private void apply() {
        PolicyType[] policies = new PolicyType[policyCombos.length];
        for (int i = 0; i < policies.length; i++) {
            policies[i] = PolicyType.fromLabel((String) policyCombos[i].getSelectedItem());
        }
        int quantum = ((Number) quantumSpinner.getValue()).intValue();
        if (listener != null) {
            listener.apply(policies, quantum);
        }
        System.arraycopy(policies, 0, requestedPolicies, 0, policies.length);
        requestedQuantum = quantum;
        refreshStatus();
    }

    /** Pone en los selectores lo que tiene cada computador ahora (descarta lo no aplicado). */
    private void restore() {
        for (int i = 0; i < policyCombos.length; i++) {
            if (currentPolicies[i] != null) {
                policyCombos[i].setSelectedItem(currentPolicies[i].getLabel());
            }
        }
        quantumSpinner.setValue(currentQuantum);
    }

    // ---------- Métodos para quien alimenta la vista (hilo de Swing) ----------

    /** Qué hacer al pulsar "Aplicar". Reemplaza al anterior. */
    public void setPlanningListener(PlanningListener listener) {
        this.listener = listener;
    }

    /**
     * Carga los valores de la simulación en los selectores (al abrir o al reiniciar)
     * y olvida los cambios pendientes.
     *
     * @param policies política de cada computador (índice 0 = Computador 1)
     * @param quantum  quantum de Round Robin
     */
    public void load(PolicyType[] policies, int quantum) {
        for (int i = 0; i < policyCombos.length; i++) {
            currentPolicies[i] = policies[i];
            requestedPolicies[i] = null;
            policyCombos[i].setSelectedItem(policies[i].getLabel());
        }
        currentQuantum = quantum;
        requestedQuantum = 0;
        quantumSpinner.setValue(quantum);
        refreshStatus();
    }

    /**
     * Estado real de un computador, en cada refresco. NO toca los selectores: así no se
     * pisa lo que el usuario está eligiendo.
     *
     * @param computerId número del computador (1, 2, ...)
     * @param policy     política que tiene su planificador ahora
     * @param quantum    quantum que tiene su planificador ahora
     * @param readyCount procesos en su cola de listos
     */
    public void showComputer(int computerId, PolicyType policy, int quantum, int readyCount) {
        int i = computerId - 1;
        currentPolicies[i] = policy;
        currentQuantum = quantum;
        readyLabels[i].setText(String.valueOf(readyCount));
        refreshStatus();
    }

    private void refreshStatus() {
        for (int i = 0; i < currentLabels.length; i++) {
            PolicyType now = currentPolicies[i];
            String text = now == null ? "—" : now.getLabel();
            if (requestedPolicies[i] != null && requestedPolicies[i] != now) {
                currentLabels[i].setText(text + " (pendiente)");
                currentLabels[i].setForeground(PENDING_COLOR);
                currentLabels[i].setToolTipText("Pasa a " + requestedPolicies[i].getLabel()
                        + " al inicio del próximo ciclo (si está en pausa, al reanudar)");
            } else {
                requestedPolicies[i] = null;
                currentLabels[i].setText(text);
                currentLabels[i].setForeground(OK_COLOR);
                currentLabels[i].setToolTipText(null);
            }
        }
        if (requestedQuantum > 0 && requestedQuantum != currentQuantum) {
            quantumStatus.setText("Actual: " + currentQuantum + " (pasa a " + requestedQuantum
                    + " en el próximo ciclo)");
            quantumStatus.setForeground(PENDING_COLOR);
        } else {
            requestedQuantum = 0;
            quantumStatus.setText("Actual: " + currentQuantum);
            quantumStatus.setForeground(OK_COLOR);
        }
    }
}
