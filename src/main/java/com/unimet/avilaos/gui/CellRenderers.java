package com.unimet.avilaos.gui;

import com.unimet.avilaos.hardware.IoOperation;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 * Table cells used by the Disk and Files views: the I/O operation as a colored pill, and the
 * color square of a file.
 *
 * @author donatovodola
 */
public final class CellRenderers {

    private CellRenderers() {
    }

    /** Background of the pill of an I/O operation. */
    public static Color operationFill(IoOperation operation) {
        if (operation == null) {
            return new Color(0xEEF0F3);
        }
        return switch (operation) {
            case CREATE -> new Color(0xE7F6EC);
            case READ -> new Color(0xEAF1FE);
            case UPDATE -> new Color(0xFFF4E0);
            case DELETE -> new Color(0xFDECEC);
            case SUSPEND -> new Color(0xF1EAFE);
            case RESUME -> new Color(0xFCE7F3);
        };
    }

    /** Text color of the pill of an I/O operation (dark enough to read on its fill). */
    public static Color operationText(IoOperation operation) {
        if (operation == null) {
            return Theme.MUTED;
        }
        return switch (operation) {
            case CREATE -> new Color(0x15703A);
            case READ -> new Color(0x1D4FB8);
            case UPDATE -> new Color(0x8A5300);
            case DELETE -> new Color(0xA61B1B);
            case SUSPEND -> new Color(0x5B21B6);
            case RESUME -> new Color(0x9D174D);
        };
    }

    /** Shows the cell value (an IoOperation) as a colored pill with its name; the label goes in the tooltip. */
    public static final class OperationRenderer implements TableCellRenderer {

        private final JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        private final UiKit.Pill pill = new UiKit.Pill("", null, Theme.TEXT);

        public OperationRenderer() {
            cell.add(pill);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            IoOperation operation = value instanceof IoOperation op ? op : null;
            pill.setText(operation == null ? "" : operation.name());
            cell.setToolTipText(operation == null ? null : operation.getLabel());
            pill.setColors(operationFill(operation), operationText(operation));
            cell.setBackground(row % 2 == 0 ? Theme.SURFACE : Theme.ROW_ALT);
            return cell;
        }
    }

    /** Shows the cell value (a java.awt.Color) as a small rounded square. */
    public static final class ColorRenderer implements TableCellRenderer {

        private final Swatch swatch = new Swatch();

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            swatch.color = value instanceof Color c ? c : null;
            swatch.setBackground(row % 2 == 0 ? Theme.SURFACE : Theme.ROW_ALT);
            return swatch;
        }
    }

    /** Rounded color square; also used next to the file name in the Files view. */
    public static final class Swatch extends JComponent {

        private static final long serialVersionUID = 1L;
        private transient Color color;

        public Swatch() {
            setPreferredSize(new Dimension(16, 16));
            setOpaque(true);
        }

        public void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (isOpaque()) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            if (color == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            int size = Math.min(14, Math.min(getWidth(), getHeight()) - 2);
            g2.fillRoundRect(6, (getHeight() - size) / 2, size, size, 5, 5);
            g2.dispose();
        }
    }
}
