package com.unimet.avilaos.gui;

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
public class BufferFormDialog extends JDialog {

    public static final int NAME_MAX_LENGTH = 10;
    public static final int MAX_CAPACITY = 1_000;

    private final int[] ramPerComputer;     // RAM de cada computador (índice 0 = Computador 1)
    private final String[] existingNames;

    private final JTextField nameField = new JTextField(10);
    private final JTextField capacityField = new JTextField("5");
    private final JTextField memoryField = new JTextField("10");
    private final JComboBox<String> hostCombo = new JComboBox<>();

    private BufferFormData result;

    /**
     * Abre el formulario y espera a que el usuario lo cierre.
     *
     * @param owner         ventana principal
     * @param ramPerComputer RAM en MB de cada computador (el buffer ocupa la de su anfitrión)
     * @param existingNames nombres de los buffers que ya existen (no se pueden repetir)
     * @return los datos validados, o null si el usuario canceló
     */
    public static BufferFormData showDialog(Frame owner, int[] ramPerComputer,
            String[] existingNames) {
        BufferFormDialog dialog = new BufferFormDialog(owner, ramPerComputer, existingNames);
        dialog.setVisible(true);
        return dialog.result;
    }

    private BufferFormDialog(Frame owner, int[] ramPerComputer, String[] existingNames) {
        super(owner, "Crear buffer", true);
        this.ramPerComputer = ramPerComputer.clone();
        this.existingNames = existingNames;
        nameField.setText("B" + (existingNames.length + 1));       // sugerencia: B1, B2, ...
        for (int i = 1; i <= ramPerComputer.length; i++) {
            hostCombo.addItem("Computador " + i + " (" + ramPerComputer[i - 1] + " MB)");
        }
        FormValidator.clearErrorOnEdit(nameField, capacityField, memoryField);

        JPanel fields = new JPanel(new GridLayout(0, 2, 6, 6));
        fields.setBorder(BorderFactory.createTitledBorder("Datos del buffer"));
        addRow(fields, "Nombre (letras y números, máx. " + NAME_MAX_LENGTH + "):", nameField);
        addRow(fields, "Capacidad en elementos (1-" + MAX_CAPACITY + "):", capacityField);
        addRow(fields, "Memoria que ocupa MB (hasta la RAM del anfitrión):", memoryField);
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
        if (name != null && exists(name)) {
            v.addError(nameField, "Nombre: ya existe un buffer llamado " + name + ".");
        }
        int capacity = v.integer(capacityField, "Capacidad", 1, MAX_CAPACITY);
        int hostId = hostCombo.getSelectedIndex() + 1;
        int hostRam = ramPerComputer[hostId - 1];
        int memory = v.integer(memoryField, "Memoria (el Computador " + hostId + " tiene " + hostRam + " MB)",
                1, hostRam);

        if (v.hasErrors()) {
            JOptionPane.showMessageDialog(this, "Corrige estos datos:\n\n" + v.getMessage(),
                    "Datos inválidos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = new BufferFormData(name, capacity, memory, hostId);
        dispose();
    }

    /** Compara sin distinguir mayúsculas: "b1" y "B1" serían el mismo buffer. */
    private boolean exists(String name) {
        for (String existing : existingNames) {
            if (existing.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
