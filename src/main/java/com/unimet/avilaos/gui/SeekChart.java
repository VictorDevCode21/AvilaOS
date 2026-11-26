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
 * Cumulative head displacement over time, to compare disk policies (the statement asks for this
 * chart). The line takes the color of the policy that was active in each stretch, and a dashed
 * mark shows every policy change, so a flatter stretch means less head movement.
 *
 * Drawn with Graphics2D: points are kept in growing arrays (no java.util collections) and, when
 * there are more points than pixels, only one point per pixel column is drawn.
 *
 * @author donatovodola
 */
public final class SeekChart extends JComponent {

    private static final long serialVersionUID = 1L;

    /** One color per policy, in the order of the policy labels. */
    private static final Color[] POLICY_COLORS = {
        new Color(0x8B95A7), Theme.ACCENT, Theme.STATE_BLOCKED, new Color(0x7C3AED), new Color(0x0D9488),
        new Color(0xDB2777)
    };
    private static final int LEFT = 52;
    private static final int RIGHT = 16;
    private static final int TOP = 26;
    private static final int BOTTOM = 30;

    private final String[] policies;
    private long[] cycles = new long[256];
    private long[] values = new long[256];
    private int[] policyOf = new int[256];
    private int size;

    /** @param policies policy labels, in order (their index picks the color) */
    public SeekChart(String[] policies) {
        this.policies = policies.clone();
        setOpaque(false);
        setPreferredSize(new Dimension(600, 260));
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    /** Color used for a policy (the legend of the metrics view uses the same). */
    public Color colorOf(String policy) {
        int index = indexOf(policy);
        return POLICY_COLORS[Math.max(0, index) % POLICY_COLORS.length];
    }

    /**
     * Adds one point. Cycles must not go backwards (a reset should call {@link #clear()} first).
     *
     * @param cycle      clock cycle
     * @param cumulative total head displacement so far, in blocks
     * @param policy     disk policy active at that cycle
     */
    public void addSample(long cycle, long cumulative, String policy) {
        if (size > 0 && cycle < cycles[size - 1]) {
            return;
        }
        if (size == cycles.length) {
            cycles = grow(cycles);
            values = grow(values);
            int[] p = new int[size * 2];
            System.arraycopy(policyOf, 0, p, 0, size);
            policyOf = p;
        }
        cycles[size] = cycle;
        values[size] = cumulative;
        policyOf[size] = Math.max(0, indexOf(policy));
        size++;
        repaint();
    }

    /** Removes every point (e.g. on reset). */
    public void clear() {
        size = 0;
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (size == 0) {
            return null;
        }
        long maxCycle = Math.max(1, cycles[size - 1]);
        long cycle = Math.round((e.getX() - LEFT) / (double) Math.max(1, getWidth() - LEFT - RIGHT) * maxCycle);
        int i = 0;
        while (i < size - 1 && cycles[i + 1] <= cycle) {
            i++;
        }
        return "Ciclo " + cycles[i] + " · " + values[i] + " bloques recorridos · " + policies[policyOf[i]];
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth() - LEFT - RIGHT;
        int h = getHeight() - TOP - BOTTOM;
        if (w <= 10 || h <= 10) {
            g2.dispose();
            return;
        }
        long maxCycle = size == 0 ? 100 : Math.max(10, cycles[size - 1]);
        long maxValue = size == 0 ? 100 : Math.max(10, values[size - 1]);
        maxValue = niceCeiling(maxValue);
        paintGrid(g2, w, h, maxCycle, maxValue);
        if (size == 0) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.SMALL);
            String text = "Todavía no hay movimiento del cabezal";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, LEFT + (w - fm.stringWidth(text)) / 2, TOP + h / 2);
            g2.dispose();
            return;
        }
        // Dashed mark and name at every policy change
        g2.setFont(Theme.SMALL_BOLD);
        FontMetrics labels = g2.getFontMetrics();
        for (int i = 0; i < size; i++) {
            if (i == 0 || policyOf[i] != policyOf[i - 1]) {
                int x = x(cycles[i], maxCycle, w);
                g2.setColor(Theme.BORDER_STRONG);
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f,
                        new float[] {3f, 3f}, 0f));
                g2.drawLine(x, TOP, x, TOP + h);
                // Name only if the next change is far enough, so two quick changes don't overlap
                String name = policies[policyOf[i]];
                int next = nextChange(i);
                if (next < 0 || x(cycles[next], maxCycle, w) > x + labels.stringWidth(name) + 10) {
                    g2.setColor(POLICY_COLORS[policyOf[i] % POLICY_COLORS.length]);
                    g2.drawString(name, x + 4, TOP - 8);
                }
            }
        }
        // The line: at most one point per pixel column, colored by its policy
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int lastX = x(cycles[0], maxCycle, w);
        int lastY = y(values[0], maxValue, h);
        for (int i = 1; i < size; i++) {
            int px = x(cycles[i], maxCycle, w);
            if (px == lastX && i < size - 1 && policyOf[i] == policyOf[i + 1]) {
                continue;
            }
            int py = y(values[i], maxValue, h);
            g2.setColor(POLICY_COLORS[policyOf[i] % POLICY_COLORS.length]);
            g2.drawLine(lastX, lastY, px, py);
            lastX = px;
            lastY = py;
        }
        g2.dispose();
    }

    private void paintGrid(Graphics2D g2, int w, int h, long maxCycle, long maxValue) {
        g2.setFont(Theme.SMALL.deriveFont(10.5f));
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i <= 4; i++) {
            long value = maxValue * i / 4;
            int y = y(value, maxValue, h);
            g2.setColor(new Color(0xEEF0F3));
            g2.drawLine(LEFT, y, LEFT + w, y);
            g2.setColor(Theme.MUTED);
            String label = String.valueOf(value);
            g2.drawString(label, LEFT - 8 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
        }
        for (int i = 0; i <= 4; i++) {
            long cycle = maxCycle * i / 4;
            int x = x(cycle, maxCycle, w);
            String label = String.valueOf(cycle);
            g2.drawString(label, Math.min(x - fm.stringWidth(label) / 2, LEFT + w - fm.stringWidth(label)),
                    TOP + h + 18);
        }
        g2.drawString("ciclo", LEFT + w - fm.stringWidth("ciclo"), TOP + h + 18 + fm.getHeight() - 4);
        g2.drawString("bloques", 4, TOP - 8);
    }

    private static int x(long cycle, long maxCycle, int w) {
        return LEFT + (int) Math.round(cycle * (double) w / maxCycle);
    }

    private static int y(long value, long maxValue, int h) {
        return TOP + h - (int) Math.round(value * (double) h / maxValue);
    }

    /** Rounds up to 1, 2 or 5 times a power of ten, so the axis labels are round numbers. */
    private static long niceCeiling(long value) {
        long power = 1;
        while (power * 10 <= value) {
            power *= 10;
        }
        for (long step : new long[] {1, 2, 5, 10}) {
            if (step * power >= value) {
                return step * power;
            }
        }
        return value;
    }

    /** Index of the next point where the policy changes after i, or -1. */
    private int nextChange(int i) {
        for (int j = i + 1; j < size; j++) {
            if (policyOf[j] != policyOf[i]) {
                return j;
            }
        }
        return -1;
    }

    private int indexOf(String policy) {
        for (int i = 0; i < policies.length; i++) {
            if (policies[i].equalsIgnoreCase(policy)) {
                return i;
            }
        }
        return -1;
    }

    private long[] grow(long[] array) {
        long[] bigger = new long[array.length * 2];
        System.arraycopy(array, 0, bigger, 0, size);
        return bigger;
    }
}
