package com.unimet.avilaos.gui;

import com.unimet.avilaos.os.disk.DiskPolicyType;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Scrollable;
import javax.swing.table.AbstractTableModel;

/**
 * "Disco" view: block map with the head, legend of owners, the disk policy selector and the I/O
 * queue with the operation of each request.
 *
 * It only shows the copies it receives (showSnapshot, showQueue, showPolicy). Changing the policy
 * goes through {@link #setPolicyListener}: the simulation decides and then calls showPolicy.
 * The policy selector lists every {@link DiskPolicyType}, in the enum order.
 *
 * @author donatovodola
 */
public final class DiskPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Receives the disk policy the user clicked. */
    @FunctionalInterface
    public interface PolicyListener {
        void requested(DiskPolicyType policy);
    }

    private final transient OwnerPalette palette;
    private final BlockMapPanel blockMap;
    private final JLabel mapSubtitle = Theme.label(" ", Theme.SMALL, Theme.MUTED);
    private final JLabel freeLabel = Theme.label(" ", Theme.SMALL, Theme.MUTED);
    private final JPanel legend = Theme.transparent(new JPanel(new WrapLayout(FlowLayout.LEFT, 14, 6)));
    private String legendKey = "";

    private final SegmentedControl policySelector;
    private final JLabel nextLabel = Theme.label("—", Theme.BODY_BOLD, Theme.TEXT);
    private final JLabel currentLabel = Theme.label("El disco está libre", Theme.SMALL, Theme.ACCENT_DARK);
    private final UiKit.Pill queueCount = new UiKit.Pill("0", new Color(0xEEF0F3), Theme.MUTED);
    private final QueueModel queueModel = new QueueModel();

    /** @param palette shared palette (same colors as the Files view) */
    public DiskPanel(OwnerPalette palette) {
        super(new BorderLayout(14, 0));
        setOpaque(false);
        this.palette = palette;
        this.blockMap = new BlockMapPanel(palette);
        this.policySelector = new SegmentedControl(DiskPolicyType.labels());
        add(buildMapCard(), BorderLayout.CENTER);
        add(buildSideColumn(), BorderLayout.EAST);
    }

    // ---------- Construction ----------

    private JComponent buildMapCard() {
        UiKit.Card card = new UiKit.Card(new BorderLayout(0, 12));
        card.setBorder(Theme.padding(16, 18, 16, 18));

        // Title and free blocks on the first line, the zones on the second: they never overlap
        JPanel head = Theme.transparent(new JPanel(new BorderLayout(10, 4)));
        head.add(Theme.label("Mapa de bloques", Theme.SECTION, Theme.TEXT), BorderLayout.WEST);
        head.add(freeLabel, BorderLayout.EAST);
        head.add(mapSubtitle, BorderLayout.SOUTH);
        card.add(head, BorderLayout.NORTH);

        // As wide as the visible area: the block map scales its cells and the legend wraps
        JPanel center = new WidthTrackingPanel(new BorderLayout(0, 12));
        center.add(blockMap, BorderLayout.NORTH);
        center.add(legend, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(center);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildSideColumn() {
        JPanel column = Theme.transparent(new JPanel(new BorderLayout(0, 14)));
        column.setPreferredSize(new Dimension(430, 10));

        // Policy
        UiKit.Card policyCard = new UiKit.Card(new BorderLayout(0, 12));
        policyCard.setBorder(Theme.padding(16, 18, 16, 18));
        JPanel policyHead = Theme.transparent(new JPanel(new BorderLayout()));
        policyHead.add(Theme.label("Política de disco", Theme.SECTION, Theme.TEXT), BorderLayout.WEST);
        policyHead.add(Theme.label("se cambia en caliente", Theme.SMALL, Theme.MUTED), BorderLayout.EAST);
        policyCard.add(policyHead, BorderLayout.NORTH);
        policyCard.add(policySelector, BorderLayout.CENTER);
        UiKit.Card next = new UiKit.Card(new BorderLayout(0, 2)).colors(Theme.SURFACE_ALT, Theme.BORDER);
        next.setBorder(Theme.padding(10, 12, 10, 12));
        next.add(Theme.label("SIGUIENTE SOLICITUD", Theme.SMALL_BOLD, Theme.MUTED), BorderLayout.NORTH);
        next.add(nextLabel, BorderLayout.CENTER);
        policyCard.add(next, BorderLayout.SOUTH);
        column.add(policyCard, BorderLayout.NORTH);

        // I/O queue
        UiKit.Card queueCard = new UiKit.Card(new BorderLayout(0, 10));
        queueCard.setBorder(Theme.padding(16, 18, 16, 18));
        JPanel queueHead = Theme.transparent(new JPanel());
        queueHead.setLayout(new BoxLayout(queueHead, BoxLayout.Y_AXIS));
        JPanel titleRow = Theme.transparent(new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)));
        titleRow.add(Theme.label("Cola de E/S del disco", Theme.SECTION, Theme.TEXT));
        queueCount.setBorder(Theme.padding(1, 7, 1, 7));
        titleRow.add(queueCount);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        queueHead.add(titleRow);
        queueHead.add(Box.createVerticalStrut(8));
        UiKit.Card current = new UiKit.Card(new BorderLayout()).colors(Theme.ACCENT_SOFT, null);
        current.setBorder(Theme.padding(8, 10, 8, 10));
        current.add(currentLabel);
        current.setAlignmentX(LEFT_ALIGNMENT);
        current.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        queueHead.add(current);
        queueCard.add(queueHead, BorderLayout.NORTH);

        JTable table = new JTable(queueModel);
        Theme.styleTable(table);
        table.setRowSelectionAllowed(false);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(2).setCellRenderer(new CellRenderers.OperationRenderer());
        int[] widths = {28, 46, 90, 160, 56, 60};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.setRowHeight(30);
        queueCard.add(UiKit.rounded(Theme.scroll(table)), BorderLayout.CENTER);
        column.add(queueCard, BorderLayout.CENTER);
        return column;
    }

    // ---------- Methods to update the view (Swing thread) ----------

    /** Shows a new copy of the disk: block map, head, legend and free blocks. */
    public void showSnapshot(DiskData.Snapshot snapshot) {
        blockMap.showSnapshot(snapshot);
        int dataFree = 0;
        int swapFree = 0;
        for (int i = 0; i < snapshot.totalBlocks(); i++) {
            if (snapshot.blocks()[i] == null) {
                if (i >= snapshot.swapStart()) {
                    swapFree++;
                } else {
                    dataFree++;
                }
            }
        }
        mapSubtitle.setText(snapshot.totalBlocks() + " bloques · datos 0–" + (snapshot.swapStart() - 1)
                + " · swap " + snapshot.swapStart() + "–" + (snapshot.totalBlocks() - 1));
        freeLabel.setText("Libres: " + dataFree + " de datos · " + swapFree + " de swap");
        updateLegend(snapshot);
    }

    /**
     * Shows the I/O queue in the order the active policy will serve it.
     *
     * @param serving  request the disk is working on now, or null if it is idle
     * @param progress blocks already read/written of the current request
     * @param queue    waiting requests, first = next to be served
     */
    public void showQueue(DiskData.Request serving, int progress, DiskData.Request[] queue) {
        queueModel.setRequests(queue);
        queueCount.setText(String.valueOf(queue.length));
        if (serving == null) {
            currentLabel.setText("El disco está libre");
        } else {
            currentLabel.setText("Atendiendo: " + serving.pid() + " · " + operationName(serving) + " · "
                    + serving.target() + " · bloque " + serving.block() + " · " + progress + " bloques hechos");
        }
        currentLabel.setToolTipText(currentLabel.getText());
        nextLabel.setText(queue.length == 0 ? "—" : queue[0].pid() + " → bloque " + queue[0].block()
                + "  (" + operationName(queue[0]) + ")");
    }

    private static String operationName(DiskData.Request request) {
        return request.operation() == null ? "?" : request.operation().name();
    }

    /** Marks the policy the disk is really using (DiskScheduler.getPolicyType()). */
    public void showPolicy(DiskPolicyType policy) {
        if (policy != null) {
            policySelector.setSelected(policy.getLabel());
        }
    }

    /**
     * Who is told when the user clicks a policy. The simulation decides (e.g. DiskScheduler.setPolicy)
     * and then calls showPolicy; the selector does not change the disk by itself.
     */
    public void setPolicyListener(PolicyListener listener) {
        PolicyListener target = listener == null ? policy -> { } : listener;
        policySelector.setChoiceListener(label -> target.requested(DiskPolicyType.fromLabel(label)));
    }

    /** Empties the view (e.g. on reset). */
    public void clear() {
        queueModel.setRequests(new DiskData.Request[0]);
        queueCount.setText("0");
        currentLabel.setText("El disco está libre");
        nextLabel.setText("—");
    }

    /** Rebuilds the legend only when the set of owners changes (avoids flicker on every refresh). */
    private void updateLegend(DiskData.Snapshot snapshot) {
        StringBuilder key = new StringBuilder();
        String[] owners = new String[snapshot.totalBlocks()];
        boolean[] processes = new boolean[snapshot.totalBlocks()];
        int count = 0;
        String last = null;
        for (DiskData.Block block : snapshot.blocks()) {
            if (block == null || block.owner().equals(last)) {
                continue;
            }
            last = block.owner();
            boolean seen = false;
            for (int i = 0; i < count && !seen; i++) {
                seen = owners[i].equals(block.owner()) && processes[i] == block.process();
            }
            if (!seen) {
                owners[count] = block.owner();
                processes[count] = block.process();
                count++;
                key.append(block.process()).append(block.owner()).append('|');
            }
        }
        if (key.toString().equals(legendKey)) {
            return;
        }
        legendKey = key.toString();
        legend.removeAll();
        for (int i = 0; i < count; i++) {
            legend.add(legendItem(palette.colorOf(owners[i], processes[i]),
                    processes[i] ? owners[i] + " (suspendido)" : owners[i]));
        }
        legend.add(legendItem(null, "libre"));
        legend.revalidate();
        legend.repaint();
    }

    private static JComponent legendItem(Color color, String text) {
        JPanel item = Theme.transparent(new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)));
        CellRenderers.Swatch swatch = new CellRenderers.Swatch();
        swatch.setOpaque(false);
        swatch.setPreferredSize(new Dimension(20, 16));
        swatch.setColor(color == null ? Theme.BORDER : color);
        item.add(swatch);
        item.add(Theme.label(text, Theme.SMALL, Theme.TEXT));
        return item;
    }

    /** Rows of the I/O queue table. */
    private static final class QueueModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"#", "PID", "Operación", "Destino", "Bloque", "Espera"};
        private transient DiskData.Request[] rows = new DiskData.Request[0];

        void setRequests(DiskData.Request[] requests) {
            rows = requests == null ? new DiskData.Request[0] : requests;
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
        public Object getValueAt(int row, int column) {
            DiskData.Request r = rows[row];
            return switch (column) {
                case 0 -> row + 1;
                case 1 -> r.pid();
                case 2 -> r.operation();
                case 3 -> r.target();
                case 4 -> r.block();
                default -> r.waitCycles() + " c.";
            };
        }
    }

    /** Panel that takes the width of the scroll pane's viewport (only scrolls vertically). */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {

        private static final long serialVersionUID = 1L;

        WidthTrackingPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
