package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.BufferConfig;
import com.unimet.avilaos.config.ComputerConfig;
import com.unimet.avilaos.config.ConfigRepository;
import com.unimet.avilaos.config.DiskConfig;
import com.unimet.avilaos.config.ProcessConfig;
import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.os.disk.DiskPolicyType;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Vista de configuración: arma una SimulationConfig NUEVA a partir de la actual.
 *
 * Permite cambiar la duración del ciclo, el quantum, el disco (bloques, swap, política inicial,
 * cabezal, dirección y velocidad) y la carga inicial de procesos de CPU (con el mismo formulario
 * de la ventana principal). También guarda la configuración en avilaos-config.csv y carga otra
 * desde un archivo CSV.
 *
 * Proyecto 2: un solo computador y sin productor-consumidor, así que el diálogo ya no muestra la
 * lista de computadores, los buffers ni la latencia de red. Si el archivo los trae (CSV del P1),
 * se conservan tal cual para que "Guardar" no los pierda, pero no se editan.
 *
 * El diálogo solo junta datos: la validación es la de SimulationConfig (la misma del CSV).
 * Nada se aplica hasta "Aceptar"; quien lo abre decide qué hacer con la configuración
 * (SimulationController reinicia la simulación con ella).
 *
 * @author victor1102
 */
public final class ConfigDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final Frame owner;
    private final JTextField cycleField = new JTextField(8);
    private final JTextField quantumField = new JTextField(8);
    /**
     * Latencia de red del archivo (P1: solo cuenta con buffers en OTRO computador). Con un solo
     * computador no se usa ni se muestra; se conserva para que "Guardar" no la pierda.
     */
    private int networkLatency;

    // Disco (Proyecto 2): DiskConfig
    private final JTextField fileBlocksField = new JTextField(8);
    private final JTextField swapField = new JTextField(8);
    private final JComboBox<String> diskPolicyCombo = new JComboBox<>(DiskPolicyType.labels());
    private final JTextField headField = new JTextField(8);
    private final JComboBox<String> directionCombo = new JComboBox<>(directionLabels());
    private final JTextField positionsField = new JTextField(8);

    /**
     * El computador del archivo (su RAM y su política inicial). Proyecto 2: uno solo, y al cargar
     * los JSON su RAM y su política salen del JSON de procesos, así que no se muestra; se conserva
     * para armar la configuración y para que "Guardar" no lo pierda.
     */
    private transient LinkedList<ComputerConfig> computers = new LinkedList<>();

    // Carga inicial: se guarda aparte y se vuelve a validar al armar la configuración
    /** Buffers del archivo (P1): no se muestran ni se crean; se conservan para "Guardar". */
    private transient LinkedList<BufferConfig> buffers = new LinkedList<>();
    private transient LinkedList<ProcessConfig> processes = new LinkedList<>();
    private final JList<String> processList = new JList<>();

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
        this.owner = owner;
        FormValidator.clearErrorOnEdit(cycleField, quantumField, fileBlocksField, swapField, headField,
                positionsField);
        processList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(buildGeneralFields());
        center.add(buildDiskFields());
        center.add(buildLoadArea());

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(center, BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(content);

        showConfig(current);
        pack();
        setLocationRelativeTo(owner);
    }

    // ---------- Construcción ----------

    private JPanel buildGeneralFields() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Parámetros generales"));
        addRow(panel, "Duración del ciclo (" + SimulationConfig.MIN_CYCLE_MS + "-"
                + SimulationConfig.MAX_CYCLE_MS + " ms):", cycleField);
        addRow(panel, "Quantum de Round Robin (1-" + SimulationConfig.MAX_QUANTUM + " ciclos):", quantumField);
        return panel;
    }

    /**
     * El disco de la configuración del enunciado: bloques, swap, política inicial, cabezal y su
     * dirección, y velocidad. Debajo, una nota con la precedencia frente a los JSON.
     */
    private JPanel buildDiskFields() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 6, 6));
        addRow(panel, "Bloques para archivos (" + DiskConfig.MIN_FILE_BLOCKS + "-" + DiskConfig.MAX_FILE_BLOCKS + "):",
                fileBlocksField);
        addRow(panel, "Área de intercambio (0-" + DiskConfig.MAX_SWAP_BLOCKS + " bloques, después de los archivos):",
                swapField);
        addRow(panel, "Política de disco inicial:", comboCell(diskPolicyCombo));
        addRow(panel, "Posición inicial del cabezal (0 a archivos + swap − 1):", headField);
        addRow(panel, "Dirección inicial del cabezal:", comboCell(directionCombo));
        addRow(panel, "Posiciones del cabezal por ciclo (1-" + DiskConfig.MAX_POSITIONS_PER_CYCLE + "):",
                positionsField);
        JLabel help = new JLabel("<html>Si se cargan los JSON, mandan en lo que traen: bloques y cabezal (sistema de"
                + " archivos), swap y política de disco (procesos). De aquí solo se usan la dirección y las"
                + " posiciones por ciclo cuando el JSON de procesos no las trae.</html>");
        help.setFont(help.getFont().deriveFont(help.getFont().getSize2D() - 1f));
        help.setPreferredSize(new Dimension(1, help.getPreferredSize().height * 3));
        JPanel section = new JPanel(new BorderLayout(4, 6));
        section.setBorder(BorderFactory.createTitledBorder("Disco"));
        section.add(panel, BorderLayout.CENTER);
        section.add(help, BorderLayout.SOUTH);
        return section;
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

    private JPanel buildLoadArea() {
        JButton addProcess = new JButton("Agregar proceso");
        JButton removeProcess = new JButton("Quitar proceso");
        addProcess.addActionListener(e -> addProcess());
        removeProcess.addActionListener(e -> removeProcess());

        JPanel panel = new JPanel(new GridLayout(1, 1, 8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Carga inicial"));
        panel.add(wrapList("Procesos de CPU", processList, addProcess, removeProcess));
        return panel;
    }

    private static JPanel wrapList(String title, JList<String> list, JButton add, JButton remove) {
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(350, 150));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(add);
        buttons.add(remove);
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
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
        JLabel note = new JLabel("Al aceptar, la simulación actual se detiene y empieza una nueva con esta configuración.");
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

    /** Pone en el formulario todos los valores de la configuración (al abrir y al cargar un archivo). */
    private void showConfig(SimulationConfig config) {
        cycleField.setText(String.valueOf(config.getCycleDurationMs()));
        quantumField.setText(String.valueOf(config.getQuantum()));
        networkLatency = config.getNetworkLatency();
        DiskConfig disk = config.getDisk();
        fileBlocksField.setText(String.valueOf(disk.fileBlocks()));
        swapField.setText(String.valueOf(disk.swapBlocks()));
        diskPolicyCombo.setSelectedItem(disk.initialPolicy().getLabel());
        headField.setText(String.valueOf(disk.initialHead()));
        directionCombo.setSelectedItem(disk.initialDirection().getLabel());
        positionsField.setText(String.valueOf(disk.positionsPerCycle()));
        computers = config.getComputers();      // ya son copias
        buffers = config.getBuffers();          // ya son copias
        processes = config.getProcesses();
        refreshLists();
    }

    private void refreshLists() {
        String[] processRows = new String[processes.size()];
        for (int i = 0; i < processRows.length; i++) {
            processRows[i] = describe(processes.get(i));
        }
        processList.setListData(processRows);
    }

    /**
     * Ej. "Calculo — CPU bound, 128 MB, 60 instr." (un productor de un CSV del P1 agrega su buffer).
     * Sin el computador: el Proyecto 2 tiene uno solo.
     */
    private static String describe(ProcessConfig p) {
        StringBuilder sb = new StringBuilder(p.name()).append(" — ").append(p.type().getLabel())
                .append(", ").append(p.memorySize()).append(" MB, ").append(p.instructions()).append(" instr.");
        if (p.type().usesBuffer()) {
            sb.append(", buffer ").append(p.bufferName());
        }
        return sb.toString();
    }

    // ---------- Carga inicial ----------

    /** El formulario valida contra la configuración que se está armando. */
    private void addProcess() {
        SimulationConfig draft = buildConfig();
        if (draft == null) {
            return;
        }
        ProcessConfig process = ProcessFormDialog.showDialog(owner, draft);
        if (process == null) {
            return;
        }
        try {
            draft.addProcess(process);
            processes.addLast(process);
            refreshLists();
        } catch (IllegalArgumentException ex) {
            showError("No se pudo agregar el proceso", ex.getMessage());
        }
    }

    private void removeProcess() {
        int index = processList.getSelectedIndex();
        if (index < 0) {
            showError("Quitar proceso", "Selecciona el proceso que quieres quitar.");
            return;
        }
        processes.remove(processes.get(index));
        refreshLists();
    }

    // ---------- Armar y validar ----------

    /**
     * Arma la configuración con lo que hay en el formulario, en el orden que exige
     * SimulationConfig (computadores → buffers → procesos), y la valida.
     * Si algo está mal, lo muestra y devuelve null (el diálogo sigue abierto).
     */
    private SimulationConfig buildConfig() {
        FormValidator v = new FormValidator();
        int cycle = v.integer(cycleField, "Duración del ciclo (ms)",
                (int) SimulationConfig.MIN_CYCLE_MS, (int) SimulationConfig.MAX_CYCLE_MS);
        int quantum = v.integer(quantumField, "Quantum", 1, SimulationConfig.MAX_QUANTUM);
        int fileBlocks = v.integer(fileBlocksField, "Bloques para archivos", DiskConfig.MIN_FILE_BLOCKS,
                DiskConfig.MAX_FILE_BLOCKS);
        int swap = v.integer(swapField, "Área de intercambio", 0, DiskConfig.MAX_SWAP_BLOCKS);
        // El rango del cabezal depende de los dos tamaños; si uno está mal, solo se muestra ese error.
        int lastBlock = fileBlocks > 0 && swap >= 0 ? fileBlocks + swap - 1
                : DiskConfig.MAX_FILE_BLOCKS + DiskConfig.MAX_SWAP_BLOCKS - 1;
        int head = v.integer(headField, "Posición inicial del cabezal", 0, lastBlock);
        int positions = v.integer(positionsField, "Posiciones del cabezal por ciclo", 1,
                DiskConfig.MAX_POSITIONS_PER_CYCLE);
        if (v.hasErrors()) {
            JOptionPane.showMessageDialog(this, "Corrige estos datos:\n\n" + v.getMessage(),
                    "Datos inválidos", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        try {
            SimulationConfig config = new SimulationConfig(cycle, quantum, networkLatency);
            config.setDisk(new DiskConfig(fileBlocks, swap,
                    DiskPolicyType.fromLabel((String) diskPolicyCombo.getSelectedItem()), head,
                    directionOf(directionCombo.getSelectedItem()), positions));
            for (int i = 0; i < computers.size(); i++) {
                config.addComputer(computers.get(i).ramSize(), computers.get(i).initialPolicy());
            }
            for (int i = 0; i < buffers.size(); i++) {
                config.addBuffer(buffers.get(i));
            }
            for (int i = 0; i < processes.size(); i++) {
                config.addProcess(processes.get(i));
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
