package com.unimet.avilaos.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * Block map of the disk: one small rounded square per block, colored by its owner (file or
 * suspended process), with the swap area tinted and the head marked on the block and on a track
 * above the grid (with its direction).
 *
 * Hovering a block shows its number and owner. The component only paints the last snapshot it got.
 *
 * @author donatovodola
 */
public final class BlockMapPanel extends JComponent {

    private static final long serialVersionUID = 1L;

    private static final int COLUMNS = 32;
    private static final int CELL_H = 22;
    private static final int GAP = 3;
    private static final int GUTTER = 30;      // row numbers on the left
    private static final int TRACK_H = 34;     // head track on top
    private static final Color FREE_DATA = Color.WHITE;
    private static final Color FREE_SWAP = new Color(0xF3F0FF);
    private static final Color SWAP_TRACK = new Color(0xDDD6FE);
    private static final Color FREE_STROKE = new Color(0xC9CED8);
    private static final BasicStroke DASHED = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
            10f, new float[] {3f, 2f}, 0f);

    private final transient OwnerPalette palette;
    private transient DiskData.Snapshot snapshot;

    /** @param palette shared palette, so files keep the same color in every view */
    public BlockMapPanel(OwnerPalette palette) {
        this.palette = palette;
        setOpaque(false);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    /** Paints a new copy of the disk (call it from the Swing thread). */
    public void showSnapshot(DiskData.Snapshot snapshot) {
        this.snapshot = snapshot;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int total = snapshot == null ? 0 : snapshot.totalBlocks();
        int rows = Math.max(1, (total + COLUMNS - 1) / COLUMNS);
        return new Dimension(560, TRACK_H + rows * (CELL_H + GAP));
    }

    private float cellWidth() {
        return (getWidth() - GUTTER - (COLUMNS - 1) * GAP) / (float) COLUMNS;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (snapshot == null) {
            return null;
        }
        float cw = cellWidth();
        int col = (int) ((e.getX() - GUTTER) / (cw + GAP));
        int row = (e.getY() - TRACK_H) / (CELL_H + GAP);
        if (e.getX() < GUTTER || e.getY() < TRACK_H || col < 0 || col >= COLUMNS) {
            return null;
        }
        int block = row * COLUMNS + col;
        if (block < 0 || block >= snapshot.totalBlocks()) {
            return null;
        }
        DiskData.Block b = snapshot.blocks()[block];
        String area = block >= snapshot.swapStart() ? " (swap)" : "";
        String who = b == null ? "libre" : b.process() ? "proceso suspendido " + b.owner() : b.owner();
        String head = block == snapshot.head() ? " · cabezal aquí" : "";
        return "Bloque " + block + area + " · " + who + head;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (snapshot == null || snapshot.totalBlocks() == 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        paintTrack(g2);
        paintGrid(g2);
        g2.dispose();
    }

    /** Line from block 0 to the last block, swap part tinted, and the head marker with its direction. */
    private void paintTrack(Graphics2D g2) {
        int total = snapshot.totalBlocks();
        int left = GUTTER;
        int width = getWidth() - GUTTER;
        int y = TRACK_H - 12;
        int swapX = left + Math.round(width * snapshot.swapStart() / (float) total);
        g2.setColor(Theme.BORDER);
        g2.fillRoundRect(left, y, swapX - left, 4, 4, 4);
        g2.setColor(SWAP_TRACK);
        g2.fillRoundRect(swapX, y, left + width - swapX, 4, 4, 4);

        int headX = left + Math.round(width * (snapshot.head() + 0.5f) / total);
        String text = snapshot.movingUp() ? "cabezal " + snapshot.head() + " →"
                : "← cabezal " + snapshot.head();
        g2.setFont(Theme.SMALL_BOLD);
        FontMetrics fm = g2.getFontMetrics();
        int pillW = fm.stringWidth(text) + 16;
        int pillX = Math.max(0, Math.min(getWidth() - pillW, headX - pillW / 2));
        g2.setColor(Theme.TEXT);
        g2.fillRoundRect(pillX, 0, pillW, 18, 18, 18);
        g2.fillRect(headX - 1, 18, 2, y - 18 + 4);
        g2.setColor(Color.WHITE);
        g2.drawString(text, pillX + 8, 13);
    }

    private void paintGrid(Graphics2D g2) {
        float cw = cellWidth();
        int total = snapshot.totalBlocks();
        g2.setFont(Theme.SMALL.deriveFont(10f));
        FontMetrics fm = g2.getFontMetrics();
        for (int block = 0; block < total; block++) {
            int row = block / COLUMNS;
            int col = block % COLUMNS;
            int x = Math.round(GUTTER + col * (cw + GAP));
            int y = TRACK_H + row * (CELL_H + GAP);
            int w = Math.round(cw);
            if (col == 0) {
                g2.setColor(Theme.MUTED);
                String label = String.valueOf(block);
                g2.drawString(label, GUTTER - 6 - fm.stringWidth(label), y + (CELL_H + fm.getAscent()) / 2 - 2);
            }
            DiskData.Block b = snapshot.blocks()[block];
            boolean swap = block >= snapshot.swapStart();
            if (b == null) {
                g2.setColor(swap ? FREE_SWAP : FREE_DATA);
                g2.fillRoundRect(x, y, w, CELL_H, 6, 6);
                g2.setColor(FREE_STROKE);
                g2.setStroke(DASHED);
                g2.drawRoundRect(x, y, w - 1, CELL_H - 1, 6, 6);
                g2.setStroke(new BasicStroke(1f));
            } else {
                g2.setColor(palette.colorOf(b.owner(), b.process()));
                g2.fillRoundRect(x, y, w, CELL_H, 6, 6);
            }
            if (block == snapshot.head()) {
                g2.setColor(Theme.TEXT);
                g2.setStroke(new BasicStroke(2.2f));
                g2.drawRoundRect(x - 1, y - 1, w + 1, CELL_H + 1, 7, 7);
                g2.setStroke(new BasicStroke(1f));
            }
        }
    }
}
