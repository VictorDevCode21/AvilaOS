package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.ProcessConfig;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * "Nuevo proceso de E/S" form: the user picks the operation (CREATE, READ, UPDATE, DELETE), the
 * file or directory, and the data of the process that will do it.
 *
 * The form never touches the file system: it only returns a {@link DiskData.IoProcess}. Whoever
 * opened it creates the I/O process, which goes through the queues like any other process and asks
 * the disk when it gets the CPU (as the statement requires).
 *
 * Every field is checked for type and range; invalid values are marked in red and listed, and the
 * form stays open, so the simulation is never interrupted.
 *
 * @author donatovodola
 */
public final class IoProcessDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String[] OPERATIONS = {"CREATE", "READ", "UPDATE", "DELETE"};
    private static final int FILE_NAME_MAX = 40;
    private static final int MAX_DEADLINE = (int) Math.min(Integer.MAX_VALUE, ProcessConfig.MAX_DEADLINE);

    /**
     * Limits the form checks against (copies taken by the caller).
     *
     * @param files      every file and directory, to fill the target list and spot repeated names
     * @param freeBlocks free blocks of the disk (data area), the biggest file that can be created
     * @param maxMemory  RAM of the computer: the most memory a process can ask for
     */
    public record Limits(DiskData.FileEntry[] files, int freeBlocks, int maxMemory) {
    }

    private final transient Limits limits;
    private transient DiskData.IoProcess result;
    private String operation = "READ";
    private final int nextNumber;

    private final SegmentedControl operationSelector = new SegmentedControl(OPERATIONS);
    private final JLabel operationHint = Theme.label(" ", Theme.SMALL, Theme.MUTED);
    private final JLabel targetLabel = Theme.label("Archivo", Theme.SMALL_BOLD, Theme.TEXT);
    private final JComboBox<String> targetCombo = new JComboBox<>();

    /** Holds only the fields of the current operation, so the card is as tall as they need. */
    private final JPanel extra = Theme.transparent(new JPanel(new BorderLayout()));
    private JComponent createFields;
    private JComponent renameFields;
    /** Last suggested process name: it is replaced on a change of operation only if the user kept it. */
    private String suggestedName = "";
    private final JComboBox<String> createKind = new JComboBox<>(new String[] {"Archivo", "Directorio"});
    private final JTextField newFileName = new JTextField("nuevo.txt");
    private final JTextField sizeField = new JTextField("5");
    private final JComboBox<String> permissionCombo = new JComboBox<>(new String[] {"Público", "Privado"});
    private final JTextField renameField = new JTextField();

    private final JTextField processName = new JTextField();
    private final JTextField memoryField = new JTextField("16");
    private final JTextField priorityField = new JTextField("1");
    private final JTextField deadlineField = new JTextField("500");

    private final UiKit.Card errorCard = new UiKit.Card(new BorderLayout());
    private final JLabel errorText = new JLabel();

    /**
     * Opens the form and waits until it closes.
     *
     * @param owner           main window
     * @param limits          what the values are checked against
     * @param presetOperation operation to start with (e.g. the button the user clicked), or null
     * @param presetPath      selected path in the tree, or null
     * @param number          number for the default process name (e.g. 7 gives "IO_7")
     * @return the request, or null if the user cancelled
     */
    public static DiskData.IoProcess showDialog(Frame owner, Limits limits, String presetOperation,
            String presetPath, int number) {
        IoProcessDialog dialog = new IoProcessDialog(owner, limits, presetOperation, presetPath, number);
        dialog.setVisible(true);
        return dialog.result;
    }

    private IoProcessDialog(Frame owner, Limits limits, String presetOperation, String presetPath, int number) {
        super(owner, "Nuevo proceso de E/S", true);
        this.limits = limits;
        this.nextNumber = number;
        if (presetOperation != null && contains(OPERATIONS, presetOperation)) {
            operation = presetOperation;
        }

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBackground(Theme.SURFACE);
        content.setBorder(Theme.padding(22, 24, 20, 24));

        JPanel heading = Theme.transparent(new JPanel(new BorderLayout(0, 4)));
        heading.add(Theme.label("Nuevo proceso de E/S", Theme.PAGE_TITLE.deriveFont(19f), Theme.TEXT),
                BorderLayout.NORTH);
        heading.add(Theme.label("La interfaz solo crea el proceso: entra a Nuevos y, cuando tenga la CPU, pide el disco.",
                Theme.SMALL, Theme.MUTED), BorderLayout.CENTER);
        content.add(heading, BorderLayout.NORTH);

        // Cards keep their own height (NORTH); the error message, when shown, goes under them
        JPanel body = Theme.transparent(new JPanel(new BorderLayout(0, 14)));
        body.add(buildOperationCard(), BorderLayout.NORTH);
        JPanel lower = Theme.transparent(new JPanel(new BorderLayout(0, 14)));
        lower.add(buildProcessCard(), BorderLayout.NORTH);
        styleError();
        lower.add(errorCard, BorderLayout.CENTER);
        body.add(lower, BorderLayout.CENTER);
        content.add(body, BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(content);

        operationSelector.setChoiceListener(this::selectOperation);
        createKind.addActionListener(e -> sizeField.setEnabled("Archivo".equals(createKind.getSelectedItem())));
        FormValidator.clearErrorOnEdit(newFileName, sizeField, renameField, processName, memoryField, priorityField,
                deadlineField);
        selectOperation(operation);
        if (presetPath != null) {
            targetCombo.setSelectedItem(presetPath);
        }
        setMinimumSize(new Dimension(620, 10));
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    // ---------- Construction ----------

    private JComponent buildOperationCard() {
        UiKit.Card card = new UiKit.Card(new BorderLayout(0, 12)).colors(Theme.SURFACE_ALT, Theme.BORDER);
        card.setBorder(Theme.padding(14, 16, 14, 16));
        JPanel top = Theme.transparent(new JPanel(new BorderLayout(0, 6)));
        top.add(Theme.label("Operación", Theme.SMALL_BOLD, Theme.TEXT), BorderLayout.NORTH);
        top.add(operationSelector, BorderLayout.CENTER);
        top.add(operationHint, BorderLayout.SOUTH);
        card.add(top, BorderLayout.NORTH);

        JPanel middle = Theme.transparent(new JPanel(new BorderLayout(0, 12)));
        JPanel target = Theme.transparent(new JPanel(new BorderLayout(0, 4)));
        target.add(targetLabel, BorderLayout.NORTH);
        targetCombo.setFont(Theme.MONO_FONT);
        target.add(targetCombo, BorderLayout.CENTER);
        middle.add(target, BorderLayout.NORTH);

        JPanel create = Theme.transparent(new JPanel(new GridLayout(2, 2, 12, 10)));
        create.add(field("Crear", createKind));
        create.add(field("Nombre (sin /)", newFileName));
        create.add(field("Tamaño (bloques) · libres: " + limits.freeBlocks(), sizeField));
        create.add(field("Permiso", permissionCombo));
        JPanel rename = Theme.transparent(new JPanel(new BorderLayout()));
        rename.add(field("Nombre nuevo (sin /)", renameField), BorderLayout.NORTH);
        createFields = create;
        renameFields = rename;
        middle.add(extra, BorderLayout.CENTER);
        card.add(middle, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildProcessCard() {
        UiKit.Card card = new UiKit.Card(new BorderLayout(0, 10)).colors(Theme.SURFACE_ALT, Theme.BORDER);
        card.setBorder(Theme.padding(14, 16, 14, 16));
        card.add(Theme.label("Proceso que hará la operación", Theme.SMALL_BOLD, Theme.TEXT), BorderLayout.NORTH);
        JPanel grid = Theme.transparent(new JPanel(new GridLayout(2, 2, 12, 10)));
        grid.add(field("Nombre del proceso (máx. " + ProcessConfig.NAME_MAX_LENGTH + ")", processName));
        grid.add(field("Memoria (1 a " + limits.maxMemory() + ")", memoryField));
        grid.add(field("Prioridad (0 a " + ProcessConfig.MAX_PRIORITY + ", 0 = más alta)", priorityField));
        grid.add(field("Deadline (ciclos, 1 a " + MAX_DEADLINE + ")", deadlineField));
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildButtons() {
        JPanel buttons = Theme.transparent(new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)));
        UiKit.Button cancel = new UiKit.Button("Cancelar", UiKit.Kind.SECONDARY);
        UiKit.Button create = new UiKit.Button("Crear proceso", UiKit.Kind.PRIMARY);
        cancel.addActionListener(e -> dispose());
        create.addActionListener(e -> onCreate());
        getRootPane().setDefaultButton(create);
        buttons.add(cancel);
        buttons.add(create);
        return buttons;
    }

    private static JComponent field(String label, JComponent input) {
        JPanel cell = Theme.transparent(new JPanel(new BorderLayout(0, 4)));
        cell.add(Theme.label(label, Theme.SMALL, Theme.MUTED), BorderLayout.NORTH);
        input.setPreferredSize(new Dimension(220, 32));
        cell.add(input, BorderLayout.CENTER);
        return cell;
    }

    private void styleError() {
        errorText.setFont(Theme.SMALL);
        errorText.setForeground(new Color(0x7F1D1D));
        errorCard.colors(new Color(0xFDECEC), null);
        errorCard.setBorder(Theme.padding(10, 12, 10, 12));
        errorCard.add(errorText);
        errorCard.setVisible(false);
    }

    // ---------- Behaviour ----------

    /** Shows the right target list and extra fields for the operation. */
    private void selectOperation(String chosen) {
        operation = chosen;
        // Errors of the previous operation may be on fields that are about to be hidden: start clean
        errorCard.setVisible(false);
        FormValidator.clearErrors(newFileName, sizeField, renameField, processName, memoryField, priorityField,
                deadlineField);
        operationSelector.setSelected(chosen);
        Object previous = targetCombo.getSelectedItem();
        targetCombo.removeAllItems();
        for (DiskData.FileEntry entry : limits.files()) {
            boolean fits = switch (chosen) {
                case "CREATE" -> entry.directory();
                case "READ" -> !entry.directory();
                default -> !"/".equals(entry.path());          // UPDATE and DELETE: files and folders
            };
            if (fits) {
                targetCombo.addItem(entry.path());
            }
        }
        if (previous != null) {
            targetCombo.setSelectedItem(previous);
        }
        switch (chosen) {
            case "CREATE" -> {
                targetLabel.setText("Directorio donde se crea");
                operationHint.setText("Crea un archivo (ocupa bloques con asignación encadenada) o un directorio.");
                showExtra(createFields);
            }
            case "UPDATE" -> {
                targetLabel.setText("Archivo o directorio a renombrar");
                operationHint.setText("Cambia el nombre; los bloques no se mueven.");
                showExtra(renameFields);
            }
            case "DELETE" -> {
                targetLabel.setText("Archivo o directorio a eliminar");
                operationHint.setText("Libera sus bloques. Un directorio se elimina con todo su contenido.");
                showExtra(null);
            }
            default -> {
                targetLabel.setText("Archivo a leer");
                operationHint.setText("Lee el archivo bloque por bloque siguiendo su cadena.");
                showExtra(null);
            }
        }
        String suggestion = "IO_" + chosen.charAt(0) + chosen.substring(1).toLowerCase() + "_" + nextNumber;
        String typed = processName.getText().trim();
        if (typed.isEmpty() || typed.equals(suggestedName)) {     // keep a name the user wrote
            processName.setText(suggestion);
        }
        suggestedName = suggestion;
        pack();
    }

    /** Shows only the given fields under the target (null = none), so the card shrinks for READ/DELETE. */
    private void showExtra(JComponent fields) {
        extra.removeAll();
        if (fields != null) {
            extra.add(fields, BorderLayout.CENTER);
        }
        extra.revalidate();
        extra.repaint();
    }

    /** Checks every field; if something is wrong it is listed and the form stays open. */
    private void onCreate() {
        FormValidator v = new FormValidator();
        String path = (String) targetCombo.getSelectedItem();
        if (path == null) {
            v.addError(switch (operation) {
                case "CREATE" -> "Directorio: no hay ningún directorio cargado.";
                case "READ" -> "Archivo: no hay archivos para leer.";
                default -> "Destino: no hay archivos ni directorios.";
            });
        }
        String newName = null;
        boolean directory = false;
        int size = 0;
        boolean isPublic = true;
        if ("CREATE".equals(operation)) {
            directory = "Directorio".equals(createKind.getSelectedItem());
            newName = fileName(v, newFileName, "Nombre");
            if (newName != null && path != null && exists(join(path, newName))) {
                v.addError(newFileName, "Nombre: ya existe " + join(path, newName) + ".");
                newName = null;
            }
            if (!directory) {
                if (limits.freeBlocks() < 1) {
                    v.addError(sizeField, "Tamaño: el disco no tiene bloques libres.");
                } else {
                    size = v.integer(sizeField, "Tamaño (bloques libres: " + limits.freeBlocks() + ")", 1,
                            limits.freeBlocks());
                }
            }
            isPublic = "Público".equals(permissionCombo.getSelectedItem());
        } else if ("UPDATE".equals(operation)) {
            newName = fileName(v, renameField, "Nombre nuevo");
            if (newName != null && path != null) {
                String renamed = join(parentOf(path), newName);
                if (renamed.equals(path)) {
                    v.addError(renameField, "Nombre nuevo: es igual al actual.");
                    newName = null;
                } else if (exists(renamed)) {
                    v.addError(renameField, "Nombre nuevo: ya existe " + renamed + ".");
                    newName = null;
                }
            }
        }
        String name = v.text(processName, "Nombre del proceso", ProcessConfig.NAME_MAX_LENGTH);
        int memory = v.integer(memoryField, "Memoria", 1, Math.max(1, limits.maxMemory()));
        int priority = v.integer(priorityField, "Prioridad", 0, ProcessConfig.MAX_PRIORITY);
        int deadline = v.integer(deadlineField, "Deadline", 1, MAX_DEADLINE);

        if (v.hasErrors()) {
            // HTML with a fixed width: long messages wrap and the label gets the right height
            errorText.setText("<html><div style='width:520px'><b>Corrige estos datos:</b><br>"
                    + escape(v.getMessage().trim()).replace("\n", "<br>") + "</div></html>");
            errorCard.setVisible(true);
            pack();
            return;
        }
        result = new DiskData.IoProcess(name, operation, path, newName, directory, size, isPublic, memory, priority,
                deadline);
        dispose();
    }

    /** A file or directory name: not empty, no "/", letters, digits, dot, dash and underscore only. */
    private static String fileName(FormValidator v, JTextField field, String label) {
        String value = v.text(field, label, FILE_NAME_MAX);
        if (value == null) {
            return null;
        }
        if (".".equals(value) || "..".equals(value)) {
            v.addError(field, label + ": \".\" y \"..\" no son nombres válidos.");
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '.' && c != '-' && c != '_') {
                v.addError(field, label + ": solo letras, números, punto, guion y guion bajo (sin / ni espacios).");
                return null;
            }
        }
        return value;
    }

    private boolean exists(String path) {
        for (DiskData.FileEntry entry : limits.files()) {
            if (entry.path().equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static String join(String directory, String name) {
        return "/".equals(directory) ? "/" + name : directory + "/" + name;
    }

    private static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash <= 0 ? "/" : path.substring(0, slash);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean contains(String[] values, String value) {
        for (String v : values) {
            if (v.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
