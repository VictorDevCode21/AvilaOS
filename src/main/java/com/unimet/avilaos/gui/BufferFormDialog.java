package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.BufferConfig;
import com.unimet.avilaos.config.SimulationConfig;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/**
 * Formulario para crear un buffer de capacidad limitada:
 * nombre único, capacidad, memoria que ocupa y computador anfitrión.
 *
 * @author NicollPinzon
 */
public final class BufferFormDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    // Los MISMOS límites que BufferConfig (formulario y CSV validan igual)
    private static final int NAME_MAX_LENGTH = BufferConfig.NAME_MAX_LENGTH;
    private static final int MAX_CAPACITY = BufferConfig.MAX_CAPACITY;

    /** Solo se lee: de aquí salen la RAM libre del anfitrión y los nombres (la misma fuente que valida addBuffer). */
    private final transient SimulationConfig config;

    private final JTextField nameField = new JTextField(10);
    private final JTextField capacityField = new JTextField("5");
    private final JTextField memoryField = new JTextField("10");
    private final JComboBox<String> hostCombo = new JComboBox<>();

    private transient BufferConfig result;

    /**
     * Abre el formulario y espera a que el usuario lo cierre.
     *
     * El formulario NO agrega el buffer: quien lo llama hace config.addBuffer(resultado).
     *
     * @param owner  ventana principal
     * @param config configuración actual (computadores, buffers existentes y su memoria)
     * @return la configuración del buffer, o null si el usuario canceló
     */
    public static BufferConfig showDialog(Frame owner, SimulationConfig config) {
        BufferFormDialog dialog = new BufferFormDialog(owner, config);
        dialog.setVisible(true);
        return dialog.result;
    }

    private BufferFormDialog(Frame owner, SimulationConfig config) {
        super(owner, "Crear buffer", true);
        this.config = config;
        nameField.setText("B" + (config.getBuffers().size() + 1));  // sugerencia: B1, B2, ...
        for (int id = 1; id <= config.getComputerCount(); id++) {
            // RAM total menos los buffers que ya viven ahí (no es la RAM libre en este instante)
            hostCombo.addItem("Computador " + id + " (" + config.processMemoryLimit(id) + " MB sin usar por buffers)");
        }
        FormValidator.clearErrorOnEdit(nameField, capacityField, memoryField);

        JPanel fields = new JPanel(new GridLayout(0, 2, 6, 6));
        fields.setBorder(BorderFactory.createTitledBorder("Datos del buffer"));
        addRow(fields, "Nombre (letras y números, máx. " + NAME_MAX_LENGTH + "):", nameField);
        addRow(fields, "Capacidad en elementos (1-" + MAX_CAPACITY + "):", capacityField);
        addRow(fields, "Memoria que ocupa MB (hasta la RAM sin usar por buffers del anfitrión):", memoryField);
        addRow(fields, "Computador anfitrión:", hostCombo);

        JButton create = new JButton("Crear");
        JButton cancel = new JButton("Cancelar");
        create.addActionListener(e -> onCreate());
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(create);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(create);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(fields, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private static void addRow(JPanel panel, String label, JComponent field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    private void onCreate() {
        FormValidator v = new FormValidator();
        String name = v.code(nameField, "Nombre", NAME_MAX_LENGTH);
        if (name != null && config.findBuffer(name) != null) {
            v.addError(nameField, "Nombre: ya existe un buffer llamado " + name + ".");
        }
        int capacity = v.integer(capacityField, "Capacidad", 1, MAX_CAPACITY);
        int hostId = hostCombo.getSelectedIndex() + 1;
        // Mismo límite que SimulationConfig.addBuffer: la RAM que no ocupan los otros buffers
        int free = config.processMemoryLimit(hostId);
        String freeText = "el Computador " + hostId + " tiene " + free + " MB sin ocupar por otros buffers";
        int memory = -1;
        if (free < 1) {
            v.addError(memoryField, "Memoria: " + freeText + ", no cabe otro buffer.");
        } else {
            memory = v.integer(memoryField, "Memoria (" + freeText + ")", 1, free);
        }

        if (v.hasErrors()) {
            JOptionPane.showMessageDialog(this, "Corrige estos datos:\n\n" + v.getMessage(),
                    "Datos inválidos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            BufferConfig candidate = new BufferConfig(name, hostId, capacity, memory);   // nombre, anfitrión, capacidad, memoria
            config.checkBuffer(candidate);     // misma regla que addBuffer: el error se ve aquí, con el formulario abierto
            result = candidate;
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Datos inválidos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        dispose();
    }
}
