package com.unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Disk and suspension metrics (Project 2), on top of the Metrics view:
 * a row of indicators (total displacement, average wait, served requests, suspensions and
 * reactivations), the cumulative displacement chart and the requests served by each policy.
 *
 * It only shows the copies it receives ({@link #showMetrics}, {@link #addSeekSample}).
 *
 * @author donatovodola
 */
public final class DiskMetricsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final UiKit.Kpi seekKpi = new UiKit.Kpi("Desplazamiento total");
    private final UiKit.Kpi waitKpi = new UiKit.Kpi("Espera promedio en la cola");
    private final UiKit.Kpi servedKpi = new UiKit.Kpi("Solicitudes atendidas");
    private final UiKit.Kpi suspendKpi = new UiKit.Kpi("Suspensiones");
    private final UiKit.Kpi resumeKpi = new UiKit.Kpi("Reactivaciones");
    private final SeekChart chart;
    private final PolicyBars bars;

    /** @param policies disk policy labels, in order (same order as DiskMetrics.servedPerPolicy) */
    public DiskMetricsPanel(String[] policies) {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        chart = new SeekChart(policies);
        bars = new PolicyBars(policies, chart);

        JPanel kpis = Theme.transparent(new JPanel(new GridLayout(1, 5, 12, 0)));
        kpis.add(seekKpi);
        kpis.add(waitKpi);
        kpis.add(servedKpi);
        kpis.add(suspendKpi);
        kpis.add(resumeKpi);
        seekKpi.setToolTipText("Bloques que recorrió el cabezal (el salto de C-SCAN y C-LOOK no cuenta)");
        waitKpi.setToolTipText("Ciclos que una solicitud espera en la cola de E/S antes de ser atendida");
        add(kpis, BorderLayout.NORTH);

        UiKit.Card chartCard = new UiKit.Card(new BorderLayout(0, 8));
        chartCard.setBorder(Theme.padding(16, 18, 14, 18));
        JPanel chartHead = Theme.transparent(new JPanel(new BorderLayout()));
        chartHead.add(Theme.label("Desplazamiento acumulado del cabezal", Theme.SECTION, Theme.TEXT),
                BorderLayout.WEST);
        chartHead.add(Theme.label("color = política activa · más plano = menos movimiento", Theme.SMALL,
                Theme.MUTED), BorderLayout.EAST);
        chartCard.add(chartHead, BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

        UiKit.Card barsCard = new UiKit.Card(new BorderLayout(0, 10));
        barsCard.setBorder(Theme.padding(16, 18, 14, 18));
        barsCard.add(Theme.label("Solicitudes atendidas por política", Theme.SECTION, Theme.TEXT),
                BorderLayout.NORTH);
        barsCard.add(bars, BorderLayout.CENTER);
        barsCard.setPreferredSize(new Dimension(330, 10));

        JPanel row = Theme.transparent(new JPanel(new BorderLayout(14, 0)));
        row.add(chartCard, BorderLayout.CENTER);
        row.add(barsCard, BorderLayout.EAST);
        add(row, BorderLayout.CENTER);
        clear();
    }

    /** Shows a new copy of the metrics. */
    public void showMetrics(DiskData.DiskMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("Las métricas del disco no pueden ser null");
        }
        seekKpi.show(metrics.totalSeek() + " bloques", "desde el ciclo 0");
        double wait = metrics.averageWait();
        boolean knownWait = Double.isFinite(wait) && wait >= 0;
        waitKpi.show(knownWait ? String.format("%.1f ciclos", wait) : "—",
                metrics.served() == 0 ? "aún no se atiende ninguna"
                        : knownWait ? "por solicitud atendida" : "pendiente: el disco aún no la mide");
        servedKpi.show(String.valueOf(metrics.served()), "entre todas las políticas");
        if (metrics.suspensions() < 0 || metrics.reactivations() < 0) {        // not counted yet
            suspendKpi.show("—", "pendiente: mediano plazo");
            resumeKpi.show("—", "pendiente: mediano plazo");
        } else {
            suspendKpi.show(String.valueOf(metrics.suspensions()), "planificador a mediano plazo");
            resumeKpi.show(String.valueOf(metrics.reactivations()),
                    Math.max(0, metrics.suspensions() - metrics.reactivations()) + " siguen en swap");
        }
        bars.setCounts(metrics.servedPerPolicy());       // null: not counted per policy yet
    }

    /** Adds a point to the cumulative displacement chart. */
    public void addSeekSample(long cycle, long cumulativeSeek, String policy) {
        chart.addSample(cycle, cumulativeSeek, policy);
    }

    /** Empties everything (e.g. on reset). */
    public void clear() {
        chart.clear();
        seekKpi.show("0 bloques", "desde el ciclo 0");
        waitKpi.show("—", "aún no se atiende ninguna");
        servedKpi.show("0", "entre todas las políticas");
        suspendKpi.show("0", "planificador a mediano plazo");
        resumeKpi.show("0", "0 siguen en swap");
        bars.setCounts(new long[0]);
    }

    /**
     * Horizontal bars: one per policy, in its chart color. If the counts bring more entries than
     * labels, the extra ones are shown as "Otra 1", "Otra 2"... so the bars always add up to the
     * "Solicitudes atendidas" indicator.
     */
    private static final class PolicyBars extends JComponent {

        private static final long serialVersionUID = 1L;
        private static final int ROW = 30;
        private final String[] policies;
        private final transient SeekChart colors;
        private long[] counts = new long[0];
        private boolean available = true;

        PolicyBars(String[] policies, SeekChart colors) {
            this.policies = policies.clone();
            this.colors = colors;
            setPreferredSize(new Dimension(280, policies.length * ROW));
        }

        /** @param counts served per policy, or null if the simulation doesn't count them yet */
        void setCounts(long[] counts) {
            this.available = counts != null;
            this.counts = counts == null ? new long[0] : counts.clone();
            setPreferredSize(new Dimension(280, rows() * ROW));
            revalidate();
            repaint();
        }

        private int rows() {
            return Math.max(policies.length, counts.length);
        }

        /**
         * Name of row i: the GUI label, else the name the chart learned for that index (e.g. C-LOOK),
         * so the bar has the same name and color as its chart stretch; "Otra n" only if nobody knows it.
         */
        private String nameOf(int i) {
            if (i < policies.length) {
                return policies[i];
            }
            String[] learned = colors.policyNames();
            return i < learned.length ? learned[i] : "Otra " + (i - policies.length + 1);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            long max = 1;
            for (long c : counts) {
                max = Math.max(max, c);
            }
            g2.setFont(Theme.SMALL);
            FontMetrics fm = g2.getFontMetrics();
            if (!available) {
                g2.setColor(Theme.MUTED);
                g2.drawString("Pendiente: el disco aún no cuenta", 0, 18);
                g2.drawString("las solicitudes por política.", 0, 36);
                g2.dispose();
                return;
            }
            int labelW = 58;
            int numberW = 36;
            int barW = getWidth() - labelW - numberW - 8;
            for (int i = 0; i < rows(); i++) {
                long count = i < counts.length ? counts[i] : 0;
                String name = nameOf(i);
                int y = i * ROW + 6;
                g2.setColor(Theme.TEXT);
                g2.drawString(name, 0, y + 12);
                g2.setColor(new Color(0xF1F3F6));
                g2.fillRoundRect(labelW, y + 2, barW, 12, 12, 12);
                int filled = (int) Math.round(barW * count / (double) max);
                if (filled > 0) {
                    g2.setColor(colors.colorOf(name));
                    g2.fillRoundRect(labelW, y + 2, Math.max(filled, 12), 12, 12, 12);
                }
                g2.setColor(Theme.TEXT);
                String text = String.valueOf(count);
                g2.drawString(text, getWidth() - fm.stringWidth(text), y + 12);
            }
            g2.dispose();
        }
    }
}
