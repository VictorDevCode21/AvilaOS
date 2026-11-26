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
 * opens it gets the two files back and loads them.
 *
 * @author donatovodola
 */
public final class LoadDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Checks one file and says if it can be used (and a summary or the error). */
    @FunctionalInterface
    public interface Validator {
        JsonPreCheck.Result check(File file);
    }

    /** Checks that the two files fit together; returns null if fine, or a warning (it does not block). */
    @FunctionalInterface
    public interface CrossValidator {
        String warning(File fileSystem, File processes);
    }

    /**
     * Files chosen by the user.
     *
     * @param fileSystem file system JSON
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
        super(owner, "Cargar la simulación", true);
        this.crossValidator = cross;
        fileSystemRow = new FileRow("Sistema de archivos",
                "Disco (bloques y cabezal inicial) y los archivos que ya existen", fsValidator);
        processesRow = new FileRow("Procesos y configuración",
                "Ciclo, swap, política de disco inicial, el computador y sus procesos", procValidator);

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
            result = new Selection(fileSystemRow.file, processesRow.file);
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
        String warning = ready && crossValidator != null
                ? crossValidator.warning(fileSystemRow.file, processesRow.file) : null;
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

        FileRow(String title, String description, Validator validator) {
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

        void choose(File chosen) {
            file = chosen;
            lastFolder = chosen.getParentFile();
            JsonPreCheck.Result check = validator.check(chosen);
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
