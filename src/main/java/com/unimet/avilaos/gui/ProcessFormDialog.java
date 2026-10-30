package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.ProcessConfig;
import com.unimet.avilaos.os.process.ProcessType;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/**
 * "Vista 1": formulario para crear un proceso.
 *
 * Muestra los campos comunes y, según el tipo elegido, los campos extra
 * (E/S para I/O bound; buffer, intervalo y elementos para productor/consumidor).
 * Al pulsar "Crear" valida tipo de dato y rango de TODOS los campos: si algo
 * está mal, marca los campos en rojo, explica cada error y no se cierra.
 *
 * @author NicollPinzon
 */
public final class ProcessFormDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    // ---------- Rangos permitidos: los MISMOS que ProcessConfig (formulario y CSV validan igual) ----------
    private static final int NAME_MAX_LENGTH = ProcessConfig.NAME_MAX_LENGTH;
    private static final int MAX_INSTRUCTIONS = ProcessConfig.MAX_INSTRUCTIONS;
    private static final int MAX_PRIORITY = ProcessConfig.MAX_PRIORITY;
    private static final int MAX_DEADLINE = (int) ProcessConfig.MAX_DEADLINE;
    private static final int MAX_IO_DURATION = ProcessConfig.MAX_IO_DURATION;
    private static final int MAX_OPERATION_INTERVAL = ProcessConfig.MAX_OPERATION_INTERVAL;
    private static final int MAX_ITEMS = ProcessConfig.MAX_ITEMS;

    private static final String CARD_CPU = "cpu";
    private static final String CARD_IO = "io";
    private static final String CARD_BUFFER = "buffer";

    private final int[] ramPerComputer;     // RAM de cada computador (índice 0 = Computador 1)
    private final int maxMemoryMb;          // RAM del computador más grande (para "Automático")

    // ---------- Campos comunes ----------
    private final JTextField nameField = new JTextField(15);
    private final JComboBox<ProcessType> typeCombo = new JComboBox<>(ProcessType.values());
    private final JTextField instructionsField = new JTextField("50");
    private final JTextField memoryField = new JTextField("100");
    private final JTextField priorityField = new JTextField("1");
    private final JTextField deadlineField = new JTextField("200");
    private final JComboBox<String> computerCombo = new JComboBox<>();

    // ---------- I/O bound ----------
    private final JTextField ioIntervalField = new JTextField("5");
    private final JTextField ioDurationField = new JTextField("3");

    // ---------- Productor / consumidor ----------
    private final JComboBox<String> bufferCombo;
    private final JTextField operationIntervalField = new JTextField("2");
    private final JTextField itemsField = new JTextField("5");

    private final CardLayout cards = new CardLayout();
    private final JPanel extraPanel = new JPanel(cards);

    private transient ProcessConfig result;

    /**
     * Abre el formulario y espera a que el usuario lo cierre.
     *
     * @param owner         ventana principal
     * @param ramPerComputer RAM en MB de cada computador (índice 0 = Computador 1)
     * @param bufferNames   buffers existentes, para productores y consumidores
     * @return la configuración del proceso (computerId 0 = automático), o null si el usuario canceló
     */
    public static ProcessConfig showDialog(Frame owner, int[] ramPerComputer,
            String[] bufferNames) {
        ProcessFormDialog dialog = new ProcessFormDialog(owner, ramPerComputer, bufferNames);
        dialog.setVisible(true);          // es modal: se queda aquí hasta que se cierre
        return dialog.result;
    }

    private ProcessFormDialog(Frame owner, int[] ramPerComputer, String[] bufferNames) {
        super(owner, "Crear proceso", true);
        this.ramPerComputer = ramPerComputer.clone();
        this.maxMemoryMb = FormValidator.max(ramPerComputer);
        this.bufferCombo = new JComboBox<>(bufferNames);

        computerCombo.addItem("Automático (ÁvilaOS decide)");
        for (int i = 1; i <= ramPerComputer.length; i++) {
            computerCombo.addItem("Computador " + i + " (" + ramPerComputer[i - 1] + " MB)");
        }
        typeCombo.setRenderer(new DefaultListCellRenderer() {   // muestra "Productor" y no "PRODUCER"
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                Object text = value instanceof ProcessType type ? type.getLabel() : value;
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        typeCombo.addActionListener(e -> showExtraFields());
        FormValidator.clearErrorOnEdit(nameField, instructionsField, memoryField, priorityField,
                deadlineField, ioIntervalField, ioDurationField, operationIntervalField, itemsField);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(buildCommonFields(), BorderLayout.NORTH);
        content.add(buildExtraFields(), BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(content);

        showExtraFields();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    // ---------- Construcción ----------

    private JPanel buildCommonFields() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Datos del proceso"));
        addRow(panel, "Nombre (máx. " + NAME_MAX_LENGTH + "):", nameField);
        addRow(panel, "Tipo:", typeCombo);
        addRow(panel, "Instrucciones (1-" + MAX_INSTRUCTIONS + "):", instructionsField);
        addRow(panel, "Memoria MB (hasta la RAM del computador):", memoryField);
        addRow(panel, "Prioridad (0 = más alta, 0-" + MAX_PRIORITY + "):", priorityField);
        addRow(panel, "Deadline en ciclos (1-" + MAX_DEADLINE + "):", deadlineField);
        addRow(panel, "Computador:", computerCombo);
        return panel;
    }

    private JPanel buildExtraFields() {
        JPanel cpu = new JPanel(new BorderLayout());
        cpu.add(new JLabel("Un proceso CPU bound solo usa el procesador: no necesita más datos."),
                BorderLayout.NORTH);

        JPanel io = new JPanel(new GridLayout(0, 2, 6, 6));
        addRow(io, "Pide E/S cada (instrucciones):", ioIntervalField);
        addRow(io, "Duración de la E/S (1-" + MAX_IO_DURATION + " ciclos):", ioDurationField);

        JPanel buffer = new JPanel(new GridLayout(0, 2, 6, 6));
        addRow(buffer, "Buffer:", bufferCombo);
        addRow(buffer, "Produce/consume cada (1-" + MAX_OPERATION_INTERVAL + " ciclos):",
                operationIntervalField);
        addRow(buffer, "Elementos para terminar (1-" + MAX_ITEMS + "):", itemsField);

        extraPanel.add(cpu, CARD_CPU);
        extraPanel.add(io, CARD_IO);
        extraPanel.add(buffer, CARD_BUFFER);
        extraPanel.setBorder(BorderFactory.createTitledBorder("Datos según el tipo"));
        return extraPanel;
    }

    private JPanel buildButtons() {
        JButton create = new JButton("Crear");
        JButton cancel = new JButton("Cancelar");
        create.addActionListener(e -> onCreate());
        cancel.addActionListener(e -> dispose());

        getRootPane().setDefaultButton(create);                       // Enter = Crear
        getRootPane().registerKeyboardAction(e -> dispose(),            // Esc = Cancelar
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(cancel);
        panel.add(create);
        return panel;
    }

    private static void addRow(JPanel panel, String label, JComponent field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    /** Cambia los campos extra según el tipo elegido. */
    private void showExtraFields() {
        ProcessType type = (ProcessType) typeCombo.getSelectedItem();
        if (type == ProcessType.IO_BOUND) {
            cards.show(extraPanel, CARD_IO);
        } else if (type != null && type.usesBuffer()) {
            cards.show(extraPanel, CARD_BUFFER);
        } else {
            cards.show(extraPanel, CARD_CPU);
        }
    }

    // ---------- Validación ----------

    private void onCreate() {
        FormValidator v = new FormValidator();
        ProcessType type = (ProcessType) typeCombo.getSelectedItem();

        String name = v.text(nameField, "Nombre", NAME_MAX_LENGTH);
        int instructions = v.integer(instructionsField, "Instrucciones", 1, MAX_INSTRUCTIONS);
        int computerId = computerCombo.getSelectedIndex();   // 0 = automático, 1..N = computador
        // La memoria se valida contra la RAM del computador elegido; si es automático,
        // contra el más grande (ÁvilaOS lo pondrá donde quepa).
        int ramLimit = computerId == ProcessConfig.AUTOMATIC ? maxMemoryMb : ramPerComputer[computerId - 1];
        String ramOwner = computerId == ProcessConfig.AUTOMATIC
                ? "ningún computador tiene más de " + maxMemoryMb + " MB"
                : "el Computador " + computerId + " tiene " + ramLimit + " MB";
        int memory = v.integer(memoryField, "Memoria (" + ramOwner + ")", 1, ramLimit);
        int priority = v.integer(priorityField, "Prioridad", 0, MAX_PRIORITY);
        int deadline = v.integer(deadlineField, "Deadline", 1, MAX_DEADLINE);

        int ioInterval = 0;
        int ioDuration = 0;
        String bufferName = null;
        int operationInterval = 0;
        int items = 0;

        if (type == ProcessType.IO_BOUND) {
            int maxInterval = instructions > 0 ? instructions : MAX_INSTRUCTIONS;
            ioInterval = v.integer(ioIntervalField, "Pide E/S cada", 1, maxInterval);
            ioDuration = v.integer(ioDurationField, "Duración de la E/S", 1, MAX_IO_DURATION);
        } else if (type != null && type.usesBuffer()) {
            bufferName = (String) bufferCombo.getSelectedItem();
            if (bufferName == null) {
                v.addError("Buffer: no hay buffers creados. Crea uno primero con \"Crear buffer\".");
            }
            operationInterval = v.integer(operationIntervalField, "Produce/consume cada", 1,
                    MAX_OPERATION_INTERVAL);
            items = v.integer(itemsField, "Elementos para terminar", 1, MAX_ITEMS);
        }

        if (v.hasErrors()) {
            JOptionPane.showMessageDialog(this, "Corrige estos datos:\n\n" + v.getMessage(),
                    "Datos inválidos", JOptionPane.WARNING_MESSAGE);
            return;                       // el formulario sigue abierto con los campos en rojo
        }

        try {
            // ProcessConfig vuelve a validar (las mismas reglas que al leer el CSV)
            result = new ProcessConfig(name, type, priority, memory, instructions, deadline, computerId,
                    ioInterval, ioDuration, bufferName, operationInterval, items);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Datos inválidos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        dispose();
    }
}
