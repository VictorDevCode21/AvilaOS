package com.unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.File;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * "Antes de iniciar" dialog: the user must load BOTH JSON files (file system, and processes with
 * the configuration) before the clock can start, as the statement asks. "Iniciar simulación" stays
 * disabled while one is missing or has an error, and each row explains what is wrong.
 *
 * The dialog only picks and checks files: it does not load anything into the simulation. Whoever
 * opens it gets the two files back and loads them (the controller uses the same parsers, see
 * {@link JsonChecks}).
 *
 * Conservar el disco (lo pide el enunciado: reiniciar con procesos nuevos conservando el disco
 * modificado, y cargar otro JSON de procesos sobre el mismo disco): si quien lo abre ofrece el
 * disco actual, la fila del sistema de archivos tiene además "Usar el disco actual". Elegirlo
 * deja esa fila lista sin archivo, y la selección devuelve fileSystem = null.
 *
 * @author donatovodola
 */
public final class LoadDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /**
     * Result of checking one file.
     *
     * @param ok      true if the file can be used
     * @param message summary when ok (e.g. "200 bloques · cabezal en 50 · …"), or what is wrong
     */
    public record Check(boolean ok, String message) {

        public static Check ok(String summary) {
            return new Check(true, summary);
        }

        public static Check fail(String message) {
            return new Check(false, message);
        }
    }

    /** Checks one file and says if it can be used (and a summary or the error). */
    @FunctionalInterface
    public interface Validator {
        Check check(File file);
    }

    /**
     * Checks that the two files fit together; returns null if fine, or a warning (it does not block).
     * fileSystem es null si se eligió conservar el disco actual.
     */
    @FunctionalInterface
    public interface CrossValidator {
        String warning(File fileSystem, File processes);
    }

    /**
     * Files chosen by the user.
     *
     * @param fileSystem file system JSON, o null para conservar el disco actual de la simulación
     * @param processes  processes and configuration JSON
     */
    public record Selection(File fileSystem, File processes) {
    }

    private final FileRow fileSystemRow;
    private final FileRow processesRow;
    private final transient CrossValidator crossValidator;
    private final UiKit.Card warningCard = new UiKit.Card(new BorderLayout());
    private final JTextArea warningText = new JTextArea();
    private final UiKit.Button startButton = new UiKit.Button("Iniciar simulación", UiKit.Kind.PRIMARY);
    private transient Selection result;
    private File lastFolder;

    /**
     * @param owner         main window
     * @param fsValidator   check of the file system JSON
     * @param procValidator check of the processes JSON
     * @param cross         check of both together
     * @param previousFs    file system already loaded (kept to restart with the same disk), or null
     * @param previousProc  processes file already loaded, or null
     */
    public LoadDialog(Frame owner, Validator fsValidator, Validator procValidator, CrossValidator cross,
            File previousFs, File previousProc) {
        this(owner, fsValidator, procValidator, cross, previousFs, previousProc, null);
    }

    /**
     * Como el otro constructor, ofreciendo además conservar el disco actual.
     *
     * @param currentDisk resumen del disco actual (por ejemplo "200 bloques · cabezal en 62 · …") para
     *                    ofrecer "Usar el disco actual", o null si no hay un disco que conservar
     */
    public LoadDialog(Frame owner, Validator fsValidator, Validator procValidator, CrossValidator cross,
            File previousFs, File previousProc, String currentDisk) {
        super(owner, "Cargar la simulación", true);
        this.crossValidator = cross;
        fileSystemRow = new FileRow("Sistema de archivos",
                "Disco (bloques y cabezal inicial) y los archivos que ya existen", fsValidator, currentDisk);
        processesRow = new FileRow("Procesos y configuración",
                "Ciclo, swap, política de disco inicial, el computador y sus procesos", procValidator, null);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(Theme.SURFACE);
        content.setBorder(Theme.padding(22, 24, 20, 24));

        JPanel heading = Theme.transparent(new JPanel(new BorderLayout(0, 4)));
        heading.add(Theme.label("Antes de iniciar", Theme.PAGE_TITLE.deriveFont(19f), Theme.TEXT), BorderLayout.NORTH);
        heading.add(Theme.label("Carga los dos archivos JSON. Los dos son obligatorios para iniciar el reloj.",
                Theme.SMALL, Theme.MUTED), BorderLayout.CENTER);
        content.add(heading, BorderLayout.NORTH);

        JPanel rows = Theme.transparent(new JPanel(new BorderLayout(0, 12)));
        JPanel both = Theme.transparent(new JPanel(new GridLayout(2, 1, 0, 12)));
        both.add(fileSystemRow);
        both.add(processesRow);
        rows.add(both, BorderLayout.NORTH);
        warningText.setEditable(false);
        warningText.setFocusable(false);
        warningText.setOpaque(false);
        warningText.setLineWrap(true);
        warningText.setWrapStyleWord(true);
        warningText.setFont(Theme.SMALL);
        warningText.setForeground(Theme.KERNEL_FG);
        warningCard.colors(Theme.KERNEL_BG, null);
        warningCard.setBorder(Theme.padding(10, 12, 10, 12));
        warningCard.add(warningText);
        warningCard.setVisible(false);
        rows.add(warningCard, BorderLayout.CENTER);
        content.add(rows, BorderLayout.CENTER);

        JPanel buttons = Theme.transparent(new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)));
        UiKit.Button cancel = new UiKit.Button("Cancelar", UiKit.Kind.SECONDARY);
        cancel.addActionListener(e -> dispose());
        startButton.addActionListener(e -> {
            result = new Selection(fileSystemRow.keepCurrent ? null : fileSystemRow.file, processesRow.file);
            dispose();
        });
        buttons.add(cancel);
        buttons.add(startButton);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        if (previousFs != null) {
            fileSystemRow.choose(previousFs);
        }
        if (previousProc != null) {
            processesRow.choose(previousProc);
        }
        updateState();
        setMinimumSize(new Dimension(600, 10));
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Opens the dialog and waits until it closes.
     *
     * @return the two files, or null if the user cancelled
     */
    public Selection showDialog() {
        setVisible(true);
        return result;
    }

    /** "Iniciar" only when both files are fine; the cross check only warns. */
    private void updateState() {
        boolean ready = fileSystemRow.ok && processesRow.ok;
        startButton.setEnabled(ready);
        startButton.setToolTipText(ready ? null : "Carga los dos archivos para poder iniciar");
        String warning = null;
        if (ready && crossValidator != null) {
            try {
                warning = crossValidator.warning(fileSystemRow.keepCurrent ? null : fileSystemRow.file,
                        processesRow.file);
            } catch (RuntimeException e) {
                warning = "no se pudo comparar los dos archivos: " + e.getMessage();
            }
        }
        warningText.setText(warning == null ? "" : "Atención: " + warning);
        warningCard.setVisible(warning != null);
        pack();
    }

    /** One file: status icon, title, what it contains or what is wrong, and the choose button. */
    private final class FileRow extends UiKit.Card {

        private static final long serialVersionUID = 1L;
        private final transient Validator validator;
        private final StatusIcon icon = new StatusIcon();
        private final JTextArea detail = new JTextArea();
        private final UiKit.Button chooseButton = new UiKit.Button("Elegir archivo…", UiKit.Kind.PRIMARY);
        private transient File file;
        private boolean ok;
        private boolean keepCurrent;      // true: conservar el disco actual en vez de un archivo

        /** @param currentDisk resumen del disco actual para ofrecer conservarlo, o null */
        FileRow(String title, String description, Validator validator, String currentDisk) {
            super(new BorderLayout(14, 0));
            this.validator = validator;
            setBorder(Theme.padding(14, 14, 14, 14));
            add(icon, BorderLayout.WEST);
            JPanel texts = Theme.transparent(new JPanel(new BorderLayout(0, 3)));
            texts.add(Theme.label(title, Theme.BODY_BOLD, Theme.TEXT), BorderLayout.NORTH);
            detail.setEditable(false);
            detail.setFocusable(false);
            detail.setOpaque(false);
            detail.setLineWrap(true);
            detail.setWrapStyleWord(true);
            detail.setFont(Theme.SMALL);
            detail.setColumns(34);
            texts.add(detail, BorderLayout.CENTER);
            add(texts, BorderLayout.CENTER);
            JPanel right = Theme.transparent(new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)));
            if (currentDisk != null) {
                UiKit.Button keepButton = new UiKit.Button("Usar el disco actual", UiKit.Kind.SECONDARY);
                keepButton.setToolTipText("Conserva los archivos y bloques de la simulación actual, con sus cambios");
                keepButton.addActionListener(e -> keepCurrentDisk(currentDisk));
                right.add(keepButton);
                right.add(Box.createHorizontalStrut(8));
            }
            right.add(chooseButton);
            add(right, BorderLayout.EAST);
            chooseButton.addActionListener(e -> pick());
            setStatus(StatusIcon.EMPTY, "Falta · " + description, Theme.MUTED, Theme.SURFACE_ALT, Theme.BORDER);
        }

        private void pick() {
            JFileChooser chooser = new JFileChooser(lastFolder);
            chooser.setDialogTitle("Elegir JSON");
            chooser.setFileFilter(new FileNameExtensionFilter("Archivos JSON (*.json)", "json"));
            chooser.setAcceptAllFileFilterUsed(false);
            if (chooser.showOpenDialog(LoadDialog.this) == JFileChooser.APPROVE_OPTION) {
                choose(chooser.getSelectedFile());
            }
        }

        /** Deja la fila lista con el disco actual (sin archivo). */
        private void keepCurrentDisk(String summary) {
            keepCurrent = true;
            ok = true;
            setStatus(StatusIcon.OK, "Disco actual, con sus cambios · " + summary, Theme.MUTED, new Color(0xF0FDF4),
                    new Color(0xBBF7D0));
            chooseButton.setText("Cambiar");
            updateState();
        }

        void choose(File chosen) {
            keepCurrent = false;
            file = chosen;
            lastFolder = chosen.getParentFile();
            Check check;
            try {
                check = validator.check(chosen);
            } catch (RuntimeException e) {
                // A validator must never leave the row in its previous state: any error = not usable
                check = Check.fail("No se pudo validar el archivo: " + e.getMessage());
            }
            ok = check.ok();
            if (ok) {
                setStatus(StatusIcon.OK, chosen.getName() + " · " + check.message(), Theme.MUTED, new Color(0xF0FDF4),
                        new Color(0xBBF7D0));
            } else {
                setStatus(StatusIcon.ERROR, chosen.getName() + ": " + check.message(), new Color(0xA61B1B),
                        new Color(0xFEF2F2), new Color(0xFECACA));
            }
            chooseButton.setText("Cambiar");
            updateState();
        }

        private void setStatus(int state, String text, Color textColor, Color fill, Color stroke) {
            icon.state = state;
            icon.repaint();
            detail.setText(text);
            detail.setForeground(textColor);
            colors(fill, stroke);
        }
    }

    /** Round badge: upload arrow (missing), check (ok) or exclamation (error). */
    private static final class StatusIcon extends JComponent {

        private static final long serialVersionUID = 1L;
        static final int EMPTY = 0;
        static final int OK = 1;
        static final int ERROR = 2;
        int state;

        StatusIcon() {
            setPreferredSize(new Dimension(38, 38));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = (getHeight() - 36) / 2;
            Color fill = state == OK ? new Color(0xDCFCE7) : state == ERROR ? new Color(0xFEE2E2) : Theme.ACCENT_SOFT;
            Color mark = state == OK ? new Color(0x15703A) : state == ERROR ? new Color(0xA61B1B) : Theme.ACCENT;
            g2.setColor(fill);
            g2.fillRoundRect(0, y, 36, 36, 12, 12);
            g2.setColor(mark);
            g2.setStroke(new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            int cx = 18;
            int cy = y + 18;
            if (state == OK) {
                g2.drawPolyline(new int[] {cx - 7, cx - 2, cx + 7}, new int[] {cy, cy + 5, cy - 5}, 3);
            } else if (state == ERROR) {
                g2.drawLine(cx, cy - 7, cx, cy + 2);
                g2.fillOval(cx - 2, cy + 5, 4, 4);
            } else {
                g2.drawLine(cx, cy + 6, cx, cy - 6);
                g2.drawPolyline(new int[] {cx - 5, cx, cx + 5}, new int[] {cy - 1, cy - 6, cy - 1}, 3);
                g2.drawLine(cx - 7, cy + 8, cx + 7, cy + 8);
            }
            g2.dispose();
        }
    }
}
