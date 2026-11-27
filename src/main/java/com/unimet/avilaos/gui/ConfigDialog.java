package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.ConfigRepository;
import com.unimet.avilaos.config.DiskConfig;
import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.hardware.HeadDirection;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Vista de configuración: arma una SimulationConfig NUEVA a partir de la actual.
 *
 * Proyecto 2: los dos JSON (obligatorios antes de iniciar) mandan en casi todo: el ciclo, el
 * quantum, la RAM, la política de CPU, los procesos y, del disco, los bloques, el swap, la política
 * inicial y el cabezal inicial. Por eso el diálogo solo muestra lo que todavía tiene efecto: la
 * dirección inicial del cabezal y las posiciones que recorre por ciclo, que son el valor por
 * defecto de las claves opcionales "initialDirection" y "positionsPerCycle" del JSON de procesos
 * (ver SimulationLoader). Nada de esto se aplica en caliente; la duración del ciclo se cambia en la
 * barra superior.
 *
 * Los otros cuatro datos del disco que el enunciado pide en la configuración (bloques, área de
 * intercambio, política de disco inicial y cabezal inicial) se muestran solo para lectura, con el
 * valor de la configuración actual: después de cargar los JSON son los de los JSON.
 *
 * Lo que no se muestra (ciclo, quantum, latencia, computador, buffers, procesos y el resto del
 * disco) se conserva tal como vino de la configuración o del archivo CSV, para que "Guardar" no lo
 * pierda.
 *
 * El diálogo solo junta datos: la validación es la de SimulationConfig (la misma del CSV).
 * Nada se aplica hasta "Aceptar"; quien lo abre decide qué hacer con la configuración
 * (SimulationController arma la simulación de nuevo con ella).
 *
 * @author victor1102
 */
public final class ConfigDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** La configuración de la que se parte (la actual o la del CSV cargado): da todo lo que no se edita. */
    private transient SimulationConfig base;

    private final JComboBox<String> directionCombo = new JComboBox<>(directionLabels());
    private final JTextField positionsField = new JTextField(8);

    // Solo lectura: los manda el JSON (se muestran porque el enunciado los pide en la configuración)
    private final JTextField fileBlocksValue = readOnlyField();
    private final JTextField swapValue = readOnlyField();
    private final JTextField diskPolicyValue = readOnlyField();
    private final JTextField headValue = readOnlyField();

    private transient SimulationConfig result;

    /**
     * Muestra el diálogo (modal) con los valores de la configuración actual.
     *
     * @param owner   ventana principal
     * @param current configuración de la simulación actual (no se modifica)
     * @return la configuración nueva ya validada, o null si el usuario canceló
     */
    public static SimulationConfig showDialog(Frame owner, SimulationConfig current) {
        ConfigDialog dialog = new ConfigDialog(owner, current);
        dialog.setVisible(true);          // es modal: se queda aquí hasta que se cierre
        return dialog.result;
    }

    private ConfigDialog(Frame owner, SimulationConfig current) {
        super(owner, "Configuración", true);
        FormValidator.clearErrorOnEdit(positionsField);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(buildDiskFields(), BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(content);

        showConfig(current);
        pack();
        setLocationRelativeTo(owner);
    }

    // ---------- Construcción ----------

    /** Lo que sigue teniendo efecto: dirección inicial y velocidad del cabezal, con su nota. */
    private JPanel buildDiskFields() {
        JPanel fixed = new JPanel(new GridLayout(0, 2, 6, 6));
        addRow(fixed, "Bloques para archivos:", fileBlocksValue);
        addRow(fixed, "Área de intercambio (bloques):", swapValue);
        addRow(fixed, "Política de disco inicial:", diskPolicyValue);
        addRow(fixed, "Posición inicial del cabezal:", headValue);
        JLabel fixedTitle = new JLabel("Vienen de los JSON cargados (antes de cargarlos, de la configuración):"
                + " no se editan aquí.");
        fixedTitle.setFont(fixedTitle.getFont().deriveFont(Font.ITALIC,
                fixedTitle.getFont().getSize2D() - 1f));
        JPanel fixedPart = new JPanel(new BorderLayout(4, 6));
        fixedPart.add(fixedTitle, BorderLayout.NORTH);
        fixedPart.add(fixed, BorderLayout.CENTER);

        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        addRow(panel, "Dirección inicial del cabezal:", comboCell(directionCombo));
        addRow(panel, "Posiciones del cabezal por ciclo (1-" + DiskConfig.MAX_POSITIONS_PER_CYCLE + "):",
                positionsField);
        // Con un ancho fijo, el HTML calcula solo cuántas líneas ocupa (no se corta)
        JLabel help = new JLabel("<html><div style='width:420px'>Se usan cuando el JSON de procesos no trae"
                + " \"initialDirection\" ni"
                + " \"positionsPerCycle\" (los JSON del enunciado no los traen). El ciclo, el quantum, la RAM,"
                + " la política de CPU y los procesos también salen de los dos JSON que se cargan antes de"
                + " iniciar. La duración del ciclo se cambia en la barra superior.</div></html>");
        help.setFont(help.getFont().deriveFont(help.getFont().getSize2D() - 1f));
        JPanel editable = new JPanel(new BorderLayout(4, 6));
        editable.add(panel, BorderLayout.CENTER);
        editable.add(help, BorderLayout.SOUTH);
        JPanel section = new JPanel(new BorderLayout(4, 12));
        section.setBorder(BorderFactory.createTitledBorder("Disco"));
        section.add(fixedPart, BorderLayout.NORTH);
        section.add(editable, BorderLayout.CENTER);
        return section;
    }

    /** Campo que solo muestra un valor (no se edita ni recibe el foco), atenuado para que se note. */
    private static JTextField readOnlyField() {
        JTextField field = new JTextField(8);
        field.setEditable(false);
        field.setFocusable(false);
        field.setBackground(Theme.SURFACE_ALT);
        field.setForeground(Theme.MUTED);
        return field;
    }

    /** En un FlowLayout el combo conserva su altura normal en vez de llenar la celda de la grilla. */
    private static JPanel comboCell(JComboBox<String> combo) {
        JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        cell.add(combo);
        return cell;
    }

    private static String[] directionLabels() {
        HeadDirection[] all = HeadDirection.values();
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            labels[i] = all[i].getLabel();
        }
        return labels;
    }

    private static HeadDirection directionOf(Object label) {
        for (HeadDirection d : HeadDirection.values()) {
            if (d.getLabel().equals(label)) {
                return d;
            }
        }
        return HeadDirection.UP;
    }

    private JPanel buildButtons() {
        JButton load = new JButton("Cargar…");
        JButton save = new JButton("Guardar");
        JButton accept = new JButton("Aceptar");
        JButton cancel = new JButton("Cancelar");
        load.setToolTipText("Carga la configuración desde un archivo CSV");
        save.setToolTipText("Guarda la configuración en " + ConfigRepository.DEFAULT_FILE
                + " (la que se usa al abrir ÁvilaOS)");
        load.addActionListener(e -> loadFromFile());
        save.addActionListener(e -> save());
        accept.addActionListener(e -> accept());
        cancel.addActionListener(e -> dispose());
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        left.add(load);
        left.add(save);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.add(cancel);
        right.add(accept);
        JLabel note = new JLabel("Al aceptar, la simulación se vuelve a armar: el diálogo de carga se abre con los"
                + " mismos JSON.");
        note.setFont(note.getFont().deriveFont(note.getFont().getSize2D() - 1f));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(note, BorderLayout.NORTH);
        panel.add(left, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private static void addRow(JPanel panel, String label, JComponent field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    // ---------- Mostrar una configuración ----------

    /** Toma la configuración de base y muestra lo que se edita (al abrir y al cargar un archivo). */
    private void showConfig(SimulationConfig config) {
        base = config;
        DiskConfig disk = config.getDisk();
        directionCombo.setSelectedItem(disk.initialDirection().getLabel());
        positionsField.setText(String.valueOf(disk.positionsPerCycle()));
        fileBlocksValue.setText(String.valueOf(disk.fileBlocks()));
        swapValue.setText(String.valueOf(disk.swapBlocks()));
        diskPolicyValue.setText(disk.initialPolicy().getLabel());
        headValue.setText(String.valueOf(disk.initialHead()));
    }

    // ---------- Armar y validar ----------

    /**
     * Arma la configuración: todo lo de la base, con la dirección y las posiciones por ciclo del
     * formulario, en el orden que exige SimulationConfig (computadores → buffers → procesos), y la
     * valida. Si algo está mal, lo muestra y devuelve null (el diálogo sigue abierto).
     */
    private SimulationConfig buildConfig() {
        FormValidator v = new FormValidator();
        int positions = v.integer(positionsField, "Posiciones del cabezal por ciclo", 1,
                DiskConfig.MAX_POSITIONS_PER_CYCLE);
        if (v.hasErrors()) {
            JOptionPane.showMessageDialog(this, "Corrige estos datos:\n\n" + v.getMessage(),
                    "Datos inválidos", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        try {
            SimulationConfig config = new SimulationConfig(base.getCycleDurationMs(), base.getQuantum(),
                    base.getNetworkLatency());
            DiskConfig disk = base.getDisk();
            config.setDisk(new DiskConfig(disk.fileBlocks(), disk.swapBlocks(), disk.initialPolicy(),
                    disk.initialHead(), directionOf(directionCombo.getSelectedItem()), positions,
                    disk.waitForInitialRequests()));
            for (int i = 0; i < base.getComputers().size(); i++) {
                config.addComputer(base.getComputers().get(i).ramSize(), base.getComputers().get(i).initialPolicy());
            }
            for (int i = 0; i < base.getBuffers().size(); i++) {
                config.addBuffer(base.getBuffers().get(i));
            }
            for (int i = 0; i < base.getProcesses().size(); i++) {
                config.addProcess(base.getProcesses().get(i));
            }
            config.validate();
            return config;
        } catch (IllegalArgumentException ex) {
            showError("Configuración inválida", ex.getMessage());
            return null;
        }
    }

    // ---------- Botones ----------

    private void accept() {
        SimulationConfig config = buildConfig();
        if (config != null) {
            result = config;
            dispose();
        }
    }

    private void save() {
        SimulationConfig config = buildConfig();
        if (config == null) {
            return;
        }
        Path file = Path.of(ConfigRepository.DEFAULT_FILE);
        try {
            new ConfigRepository().save(config, file);
            JOptionPane.showMessageDialog(this, "Configuración guardada en:\n" + file.toAbsolutePath(),
                    "Guardar", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            showError("No se pudo guardar", "No se pudo escribir " + file.toAbsolutePath() + ":\n" + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            showError("Configuración inválida", ex.getMessage());
        }
    }

    private void loadFromFile() {
        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.dir")));
        chooser.setDialogTitle("Cargar configuración");
        chooser.setFileFilter(new FileNameExtensionFilter("Configuración de ÁvilaOS (*.csv)", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            showConfig(new ConfigRepository().load(file.toPath()));
            pack();
        } catch (IOException ex) {
            showError("No se pudo cargar", "No se pudo leer " + file + ":\n" + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            showError("Archivo inválido", file.getName() + ":\n" + ex.getMessage());
        }
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }
}
