package com.unimet.avilaos.gui;

import com.unimet.avilaos.hardware.IoOperation;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * "Archivos" view: the file system as a JTree, the details of the selected node, buttons that ask
 * for an I/O process on it, a message area for accepted / rejected requests, and the file
 * allocation table (JTable) with the color of each file.
 *
 * The buttons never change a file: they only tell the {@link FileActionListener} which operation
 * and path the user chose. The simulation creates the I/O process (or rejects it and the view shows
 * the reason with {@link #showRejected}).
 *
 * @author donatovodola
 */
public final class FileSystemPanel extends WidthTrackingPanel {

    private static final long serialVersionUID = 1L;

    /** Receives the operation the user asked for on the selected path. */
    @FunctionalInterface
    public interface FileActionListener {
        /**
         * @param operation CREATE, READ, UPDATE or DELETE
         * @param path      selected path (for CREATE: the directory where the file goes)
         */
        void requested(IoOperation operation, String path);
    }

    /** Smallest height of the allocation table card: header and about three rows. */
    private static final int MIN_TABLE_CARD_HEIGHT = 170;

    private final transient OwnerPalette palette;
    private transient FileActionListener listener = (operation, path) -> { };
    private transient DiskData.FileEntry[] entries = new DiskData.FileEntry[0];
    private String treeKey = "";

    // Tree
    private final UiKit.Pill sessionPill = new UiKit.Pill("Administrador (admin)", Theme.KERNEL_BG, Theme.KERNEL_FG);
    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode("/"));
    private final JTree tree = new JTree(treeModel);

    // Details
    private final CellRenderers.Swatch nameSwatch = new CellRenderers.Swatch();
    private final Component swatchGap = Box.createHorizontalStrut(6);
    private final JLabel nameLabel = Theme.label("Selecciona un archivo", Theme.SECTION, Theme.TEXT);
    private final UiKit.Pill permissionPill = new UiKit.Pill("", null, Theme.TEXT);
    private final JLabel pathValue = value();
    private final JLabel typeValue = value();
    private final JLabel sizeValue = value();
    private final JLabel ownerValue = value();
    private final JLabel permissionValue = value();
    private final JLabel firstBlockValue = value();
    private final JTextArea chainText = new JTextArea();
    private final UiKit.Button readButton = new UiKit.Button("Leer", UiKit.Kind.PRIMARY);
    private final UiKit.Button renameButton = new UiKit.Button("Renombrar", UiKit.Kind.SECONDARY);
    private final UiKit.Button deleteButton = new UiKit.Button("Eliminar", UiKit.Kind.SECONDARY);
    private final UiKit.Button createButton = new UiKit.Button("+ Archivo aquí", UiKit.Kind.SECONDARY);
    /** Guarda el sistema de archivos en un JSON con el formato del enunciado (lo conecta el controlador). */
    private final UiKit.Button saveButton = new UiKit.Button("Guardar JSON…", UiKit.Kind.SECONDARY);
    private final UiKit.Card messageCard = new UiKit.Card(new BorderLayout());
    private final JTextArea messageText = new JTextArea();

    // Allocation table
    private final AllocationModel allocationModel = new AllocationModel();

    /** @param palette shared palette (same colors as the Disk view) */
    public FileSystemPanel(OwnerPalette palette) {
        super(new GridBagLayout(), true);      // follows the window's height down to the minimum, then scrolls
        this.palette = palette;
        JPanel top = Theme.transparent(new JPanel(new GridLayout(1, 2, 14, 0)));
        top.add(buildTreeCard());
        JComponent details = buildDetailCard();
        top.add(details);
        showDetails(null);
        // The tree and the details never shrink below what the details need; the table gives up
        // height first, and below that the whole view scrolls (see getScrollableTracksViewportHeight)
        int detailsHeight = details.getPreferredSize().height;
        top.setMinimumSize(new Dimension(0, detailsHeight));
        // The tree scrolls inside its card, so the row doesn't ask for the whole tree's height
        top.setPreferredSize(new Dimension(top.getPreferredSize().width, detailsHeight));
        JComponent allocation = buildAllocationCard();
        allocation.setMinimumSize(new Dimension(0, MIN_TABLE_CARD_HEIGHT));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.weighty = 1;
        c.gridy = 0;
        add(top, c);
        c.gridy = 1;
        c.insets = new Insets(14, 0, 0, 0);
        add(allocation, c);
    }

    // ---------- Construction ----------

    private JComponent buildTreeCard() {
        UiKit.Card card = new UiKit.Card(new BorderLayout(0, 10));
        card.setBorder(Theme.padding(16, 18, 16, 18));
        JPanel head = Theme.transparent(new JPanel(new BorderLayout()));
        head.add(Theme.label("Sistema de archivos", Theme.SECTION, Theme.TEXT), BorderLayout.WEST);
        sessionPill.setToolTipText("Modo actual: se cambia abajo en la barra de la izquierda");
        head.add(sessionPill, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(28);
        tree.setFont(Theme.BODY);
        tree.setBackground(Theme.SURFACE);
        tree.setBorder(Theme.padding(6, 6, 6, 6));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new NodeRenderer());
        tree.addTreeSelectionListener(e -> showDetails(selectedEntry()));
        card.add(UiKit.rounded(Theme.scroll(tree)), BorderLayout.CENTER);
        return card;
    }

    private JComponent buildDetailCard() {
        UiKit.Card card = new UiKit.Card(new BorderLayout(0, 12));
        card.setBorder(Theme.padding(16, 18, 16, 18));

        // Left-aligned with the fields below; the color swatch only shows for a selected file
        JPanel title = Theme.transparent(new JPanel());
        title.setLayout(new BoxLayout(title, BoxLayout.X_AXIS));
        nameSwatch.setOpaque(false);
        nameSwatch.setPreferredSize(new Dimension(22, 18));
        nameSwatch.setMaximumSize(new Dimension(22, 18));
        nameSwatch.setVisible(false);
        swatchGap.setVisible(false);
        title.add(nameSwatch);
        title.add(swatchGap);
        title.add(nameLabel);
        title.add(Box.createHorizontalStrut(8));
        title.add(permissionPill);
        title.add(Box.createHorizontalGlue());
        card.add(title, BorderLayout.NORTH);

        JPanel fields = Theme.transparent(new JPanel(new GridLayout(0, 2, 12, 8)));
        addField(fields, "Ruta", pathValue);
        addField(fields, "Tipo", typeValue);
        addField(fields, "Tamaño", sizeValue);
        addField(fields, "Dueño", ownerValue);
        addField(fields, "Permiso", permissionValue);
        addField(fields, "Primer bloque", firstBlockValue);

        JPanel middle = Theme.transparent(new JPanel(new BorderLayout(0, 10)));
        middle.add(fields, BorderLayout.NORTH);
        JPanel chain = Theme.transparent(new JPanel(new BorderLayout(0, 4)));
        chain.add(Theme.label("CADENA DE BLOQUES", Theme.SMALL_BOLD, Theme.MUTED), BorderLayout.NORTH);
        styleWrappingText(chainText, Theme.MONO_FONT, Theme.TEXT);
        chain.add(chainText, BorderLayout.CENTER);
        middle.add(chain, BorderLayout.CENTER);
        card.add(middle, BorderLayout.CENTER);

        JPanel bottom = Theme.transparent(new JPanel(new BorderLayout(0, 10)));
        JPanel actionsTitle = Theme.transparent(new JPanel(new BorderLayout()));
        actionsTitle.add(Theme.label("CREAR PROCESO DE E/S", Theme.SMALL_BOLD, Theme.MUTED), BorderLayout.WEST);
        bottom.add(actionsTitle, BorderLayout.NORTH);
        JPanel actions = Theme.transparent(new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)));
        deleteButton.setForeground(new Color(0xA61B1B));
        actions.add(readButton);
        actions.add(renameButton);
        actions.add(deleteButton);
        actions.add(createButton);
        bottom.add(actions, BorderLayout.CENTER);
        readButton.addActionListener(e -> fire(IoOperation.READ));
        renameButton.addActionListener(e -> fire(IoOperation.UPDATE));
        deleteButton.addActionListener(e -> fire(IoOperation.DELETE));
        createButton.addActionListener(e -> fire(IoOperation.CREATE));
        readButton.setToolTipText("Crea un proceso de E/S que lee este archivo");
        renameButton.setToolTipText("Crea un proceso de E/S que cambia el nombre");
        deleteButton.setToolTipText("Crea un proceso de E/S que lo elimina (un directorio, con todo su contenido)");
        createButton.setToolTipText("Crea un proceso de E/S que crea un archivo dentro de este directorio");

        styleWrappingText(messageText, Theme.SMALL, Theme.TEXT);
        messageCard.setBorder(Theme.padding(10, 12, 10, 12));
        messageCard.add(messageText);
        messageCard.setVisible(false);
        bottom.add(messageCard, BorderLayout.SOUTH);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildAllocationCard() {
        UiKit.Card card = new UiKit.Card(new BorderLayout(0, 10));
        card.setBorder(Theme.padding(16, 18, 16, 18));
        JPanel head = Theme.transparent(new JPanel(new BorderLayout()));
        head.add(Theme.label("Tabla de asignación de archivos", Theme.SECTION, Theme.TEXT), BorderLayout.WEST);
        JPanel right = Theme.transparent(new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0)));
        right.add(Theme.label("se actualiza con cada operación", Theme.SMALL, Theme.MUTED));
        saveButton.setToolTipText("Guarda el estado actual del sistema de archivos (formato del JSON del enunciado)");
        right.add(saveButton);
        head.add(right, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        JTable table = new JTable(allocationModel);
        Theme.styleTable(table);
        table.setRowSelectionAllowed(false);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setCellRenderer(new CellRenderers.ColorRenderer());
        int[] widths = {50, 260, 70, 90, 90, 110};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        JComponent scroll = UiKit.rounded(Theme.scroll(table));
        scroll.setPreferredSize(new Dimension(600, 210));
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private static void addField(JPanel grid, String name, JLabel value) {
        JPanel cell = Theme.transparent(new JPanel(new BorderLayout(0, 2)));
        cell.add(Theme.label(name, Theme.SMALL, Theme.MUTED), BorderLayout.NORTH);
        cell.add(value, BorderLayout.CENTER);
        grid.add(cell);
    }

    private static JLabel value() {
        return Theme.label("—", Theme.BODY_BOLD, Theme.TEXT);
    }

    private static void styleWrappingText(JTextArea area, java.awt.Font font, Color color) {
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(font);
        area.setForeground(color);
        area.setBorder(javax.swing.BorderFactory.createEmptyBorder());
    }

    // ---------- Methods to update the view (Swing thread) ----------

    /**
     * Shows the file system: rebuilds the tree only if something changed (keeps the selection) and
     * refreshes the allocation table.
     *
     * @param files every directory and file, in any order ("/" may be included or not)
     */
    public void showFiles(DiskData.FileEntry[] files) {
        entries = files == null ? new DiskData.FileEntry[0] : files;
        allocationModel.setFiles(entries, palette);
        StringBuilder key = new StringBuilder();
        for (DiskData.FileEntry f : entries) {
            key.append(f.path()).append(':').append(f.blocks()).append(':').append(f.owner())
                    .append(':').append(f.isPublic()).append('|');
        }
        if (!key.toString().equals(treeKey)) {
            treeKey = key.toString();
            rebuildTree();
        }
        showDetails(selectedEntry());
    }

    /** Who is told when the user clicks Leer / Renombrar / Eliminar / + Archivo (replaces the previous one). */
    public void setFileActionListener(FileActionListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("El listener no puede ser null");
        }
        this.listener = listener;
    }

    /** Qué hacer al pulsar "Guardar JSON…" (reemplaza la acción anterior). */
    public void setSaveAction(ActionListener action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de guardar no puede ser null");
        }
        for (ActionListener old : saveButton.getActionListeners()) {
            saveButton.removeActionListener(old);
        }
        saveButton.addActionListener(action);
    }

    /** Shows the active mode next to the tree title (e.g. "Usuario: user2"). */
    public void showSession(DiskData.Session session) {
        if (session == null) {
            throw new IllegalArgumentException("La sesión no puede ser null");
        }
        sessionPill.setText(session.longText());
        sessionPill.setColors(session.admin() ? Theme.KERNEL_BG : Theme.USER_BG,
                session.admin() ? Theme.KERNEL_FG : Theme.USER_FG);
    }

    /** Green message: the I/O process was created. */
    public void showAccepted(String text) {
        showMessage(text, new Color(0xE7F6EC), new Color(0x15703A));
    }

    /**
     * Red message inside the panel: the request was rejected and why. With MainWindow, prefer
     * MainWindow.showRejected (the floating notice) so a rejection is shown in one place only.
     */
    public void showRejected(String reason) {
        showMessage("Operación rechazada. " + reason, new Color(0xFDECEC), new Color(0x7F1D1D));
    }

    /** Hides the message. */
    public void clearMessage() {
        messageCard.setVisible(false);
    }

    /** Path of the selected node, or null. */
    public String getSelectedPath() {
        DiskData.FileEntry entry = selectedEntry();
        return entry == null ? null : entry.path();
    }

    // ---------- Internals ----------

    /** The swatch takes no room when there is no color (nothing selected, or a directory). */
    private void showSwatch(Color color) {
        nameSwatch.setColor(color);
        nameSwatch.setVisible(color != null);
        swatchGap.setVisible(color != null);
    }

    private void fire(IoOperation operation) {
        String path = getSelectedPath();
        if (path != null) {
            listener.requested(operation, path);
        }
    }

    private void showMessage(String text, Color fill, Color foreground) {
        messageCard.colors(fill, null);
        messageText.setForeground(foreground);
        messageText.setText(text);
        messageCard.setVisible(true);
        revalidate();
        repaint();
    }

    private DiskData.FileEntry selectedEntry() {
        TreePath selection = tree.getSelectionPath();
        if (selection == null) {
            return null;
        }
        Object user = ((DefaultMutableTreeNode) selection.getLastPathComponent()).getUserObject();
        return user instanceof DiskData.FileEntry entry ? entry : null;
    }

    private void showDetails(DiskData.FileEntry entry) {
        boolean has = entry != null;
        readButton.setEnabled(has && !entry.directory());
        renameButton.setEnabled(has && !"/".equals(entry.path()));
        deleteButton.setEnabled(has && !"/".equals(entry.path()));
        createButton.setEnabled(has && entry.directory());
        if (!has) {
            showSwatch(null);
            nameLabel.setText("Selecciona un archivo o directorio");
            permissionPill.setText("");
            for (JLabel label : new JLabel[] {pathValue, typeValue, sizeValue, ownerValue, permissionValue,
                firstBlockValue}) {
                label.setText("—");
            }
            chainText.setText("—");
            return;
        }
        showSwatch(entry.directory() ? null : palette.colorOf(entry.path(), false));
        nameLabel.setText(entry.name());
        permissionPill.setText(entry.isPublic() ? "Público" : "Privado");
        permissionPill.setColors(entry.isPublic() ? new Color(0xEAF1FE) : Theme.KERNEL_BG,
                entry.isPublic() ? Theme.ACCENT_DARK : Theme.KERNEL_FG);
        pathValue.setText(entry.path());
        pathValue.setToolTipText(entry.path());
        typeValue.setText(entry.directory() ? "Directorio" : "Archivo");
        sizeValue.setText(entry.directory() ? "—" : entry.blocks() + " bloques");
        ownerValue.setText(entry.owner());
        permissionValue.setText(entry.isPublic() ? "Público" : "Privado");
        firstBlockValue.setText(entry.directory() ? "—" : String.valueOf(entry.firstBlock()));
        chainText.setText(entry.directory() ? "Los directorios no ocupan bloques." : chainSummary(entry.chain()));
    }

    /** "34 → 35 → 36 → … → 61 → fin" (the middle is cut when the chain is long). */
    private static String chainSummary(int[] chain) {
        if (chain == null || chain.length == 0) {
            return "—";
        }
        StringBuilder text = new StringBuilder();
        int shown = chain.length <= 8 ? chain.length : 4;
        for (int i = 0; i < shown; i++) {
            text.append(chain[i]).append(" → ");
        }
        if (shown < chain.length) {
            text.append("… → ").append(chain[chain.length - 2]).append(" → ")
                    .append(chain[chain.length - 1]).append(" → ");
        }
        return text.append("fin").toString();
    }

    /** Builds the tree from the paths, keeping the selected path selected and every folder open. */
    private void rebuildTree() {
        String selected = getSelectedPath();
        DiskData.FileEntry rootEntry = find("/");
        if (rootEntry == null) {
            rootEntry = new DiskData.FileEntry("/", true, 0, -1, "admin", true, "JSON", new int[0]);
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootEntry);
        // Shorter paths first, so a parent always exists before its children
        DiskData.FileEntry[] sorted = entries.clone();
        for (int i = 1; i < sorted.length; i++) {
            DiskData.FileEntry current = sorted[i];
            int j = i - 1;
            while (j >= 0 && compare(sorted[j], current) > 0) {
                sorted[j + 1] = sorted[j];
                j--;
            }
            sorted[j + 1] = current;
        }
        for (DiskData.FileEntry entry : sorted) {
            if ("/".equals(entry.path())) {
                continue;
            }
            String parentPath = entry.path().substring(0, Math.max(1, entry.path().lastIndexOf('/')));
            DefaultMutableTreeNode parent = findNode(root, parentPath);
            (parent == null ? root : parent).add(new DefaultMutableTreeNode(entry, entry.directory()));
        }
        treeModel.setRoot(root);
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
        DefaultMutableTreeNode keep = selected == null ? null : findNode(root, selected);
        if (keep != null) {
            tree.setSelectionPath(new TreePath(keep.getPath()));
        }
    }

    /** Depth first, then directories before files, then by name. */
    private static int compare(DiskData.FileEntry a, DiskData.FileEntry b) {
        int depthA = a.path().split("/").length;
        int depthB = b.path().split("/").length;
        if (depthA != depthB) {
            return depthA - depthB;
        }
        if (a.directory() != b.directory()) {
            return a.directory() ? -1 : 1;
        }
        return a.path().compareToIgnoreCase(b.path());
    }

    private DiskData.FileEntry find(String path) {
        for (DiskData.FileEntry entry : entries) {
            if (entry.path().equals(path)) {
                return entry;
            }
        }
        return null;
    }

    private static DefaultMutableTreeNode findNode(DefaultMutableTreeNode node, String path) {
        Object user = node.getUserObject();
        if (user instanceof DiskData.FileEntry entry && entry.path().equals(path)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found = findNode((DefaultMutableTreeNode) node.getChildAt(i), path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ---------- Tree look ----------

    /** Folder or sheet icon, the name, a lock for private files and the size on the right. */
    private final class NodeRenderer extends DefaultTreeCellRenderer {

        private static final long serialVersionUID = 1L;

        NodeRenderer() {
            setBackgroundSelectionColor(Theme.ACCENT_SOFT);
            setBorderSelectionColor(null);
            setTextSelectionColor(Theme.ACCENT_DARK);
            setTextNonSelectionColor(Theme.TEXT);
            setBackgroundNonSelectionColor(Theme.SURFACE);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(t, value, selected, expanded, leaf, row, false);
            Object user = ((DefaultMutableTreeNode) value).getUserObject();
            if (user instanceof DiskData.FileEntry entry) {
                String size = entry.directory() ? "" : "   " + entry.blocks() + " b";
                setText(entry.name() + size);
                Color iconColor = entry.directory() ? Theme.STATE_BLOCKED
                        : palette.colorOf(entry.path(), false).darker();
                setIcon(new NodeIcon(entry.directory() ? NavIcon.FILES : NavIcon.DOCUMENT, iconColor,
                        !entry.directory() && !entry.isPublic()));
                setToolTipText(entry.path() + " · " + entry.owner() + " · "
                        + (entry.isPublic() ? "público" : "privado"));
            }
            setFont(selected ? Theme.BODY_BOLD : Theme.BODY);
            return this;
        }
    }

    /** 16 px icon drawn with NavIcon, plus a small lock when the file is private. */
    private static final class NodeIcon implements Icon {

        private final NavIcon icon;
        private final Color color;
        private final boolean locked;

        NodeIcon(NavIcon icon, Color color, boolean locked) {
            this.icon = icon;
            this.color = color;
            this.locked = locked;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            icon.paint(g2, x, y, 16);
            if (locked) {
                g2.setColor(Theme.KERNEL_FG);
                NavIcon.LOCK.paint(g2, x + 18, y + 2, 12);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return locked ? 32 : 18;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    /** Rows of the allocation table: one per FILE (directories have no blocks). */
    private static final class AllocationModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Color", "Archivo", "Bloques", "Primer bloque", "Dueño",
            "Creado por"};
        private transient DiskData.FileEntry[] rows = new DiskData.FileEntry[0];
        private transient Color[] colors = new Color[0];

        void setFiles(DiskData.FileEntry[] all, OwnerPalette palette) {
            int count = 0;
            for (DiskData.FileEntry f : all) {
                if (!f.directory()) {
                    count++;
                }
            }
            DiskData.FileEntry[] files = new DiskData.FileEntry[count];
            Color[] fileColors = new Color[count];
            int i = 0;
            for (DiskData.FileEntry f : all) {
                if (!f.directory()) {
                    files[i] = f;
                    fileColors[i] = palette.colorOf(f.path(), false);
                    i++;
                }
            }
            rows = files;
            colors = fileColors;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.length;
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Color.class : Object.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            DiskData.FileEntry f = rows[row];
            return switch (column) {
                case 0 -> colors[row];
                case 1 -> f.path();
                case 2 -> f.blocks();
                case 3 -> f.firstBlock();
                case 4 -> f.owner();
                default -> f.creator();
            };
        }
    }
}
