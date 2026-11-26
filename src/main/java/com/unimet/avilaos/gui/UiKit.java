package com.unimet.avilaos.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Componentes visuales propios de la interfaz, dibujados con Graphics2D (Swing puro).
 *
 *  - Card: panel blanco con esquinas redondeadas y borde fino.
 *  - Button: botón plano (principal, secundario o de texto).
 *  - Pill: etiqueta con fondo redondeado (política, modo de la CPU, contadores).
 *  - RailItem: opción de la barra lateral angosta (ícono, texto corto debajo y contador).
 *  - UsageBar: barra de ocupación redondeada (memoria).
 *  - RoundedBox: marco con esquinas redondeadas para tablas, scrolls y campos (recorta lo de adentro).
 *  - Kpi: tarjeta con un indicador (título, valor grande y detalle).
 *
 * No tienen lógica del simulador: solo se ven y avisan clics como cualquier JButton.
 *
 * @author donatovodola
 */
public final class UiKit {

    private UiKit() {
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g2;
    }

    // =====================================================================
    // Card
    // =====================================================================

    /** Panel blanco con esquinas redondeadas; el contenido va con el layout que se pase. */
    public static class Card extends JPanel {

        private static final long serialVersionUID = 1L;
        private Color fill = Theme.SURFACE;
        private Color stroke = Theme.BORDER;

        public Card(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(Theme.padding(14, 16, 14, 16));
        }

        /** Colores de fondo y borde (ej. un bloque gris dentro de una tarjeta). */
        public Card colors(Color fill, Color stroke) {
            this.fill = fill;
            this.stroke = stroke;
            repaint();
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
            if (stroke != null) {
                g2.setColor(stroke);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =====================================================================
    // Button
    // =====================================================================

    /** Variante del botón. */
    public enum Kind {
        PRIMARY, SECONDARY, GHOST
    }

    /** Botón plano con esquinas redondeadas; cambia de tono al pasar el mouse y al presionarlo. */
    public static class Button extends JButton {

        private static final long serialVersionUID = 1L;
        private final Kind kind;

        public Button(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(kind == Kind.PRIMARY ? Theme.BODY_BOLD : Theme.BODY);
            setForeground(kind == Kind.PRIMARY ? Color.WHITE : Theme.TEXT);
            setBorder(Theme.padding(0, 14, 0, 14));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, 34);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            boolean hover = getModel().isRollover() && isEnabled();
            boolean pressed = getModel().isPressed();
            Color fill;
            Color stroke = null;
            switch (kind) {
                case PRIMARY -> fill = !isEnabled() ? Theme.BORDER_STRONG
                        : pressed ? Theme.ACCENT_DARK : hover ? Theme.ACCENT_HOVER : Theme.ACCENT;
                case SECONDARY -> {
                    fill = pressed ? Theme.ACCENT_SOFT : hover ? Theme.SURFACE_ALT : Theme.SURFACE;
                    stroke = Theme.BORDER_STRONG;
                }
                default -> fill = pressed ? Theme.ACCENT_SOFT : hover ? new Color(0xEEF0F3) : new Color(0, 0, 0, 0);
            }
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            if (stroke != null) {
                g2.setColor(stroke);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =====================================================================
    // Pill
    // =====================================================================

    /** Etiqueta con fondo redondeado. Se le cambian texto y colores en vivo. */
    public static class Pill extends JLabel {

        private static final long serialVersionUID = 1L;
        private Color fill;

        public Pill(String text, Color fill, Color foreground) {
            super(text);
            this.fill = fill;
            setForeground(foreground);
            setFont(Theme.SMALL_BOLD);
            setBorder(Theme.padding(3, 9, 3, 9));
            setOpaque(false);
        }

        public void setColors(Color fill, Color foreground) {
            this.fill = fill;
            setForeground(foreground);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (fill != null && getText() != null && !getText().isEmpty()) {
                Graphics2D g2 = smooth(g);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    // =====================================================================
    // RailItem
    // =====================================================================

    /**
     * Option of the narrow side rail: an icon inside a rounded pill and a short label below it.
     * The full view name goes in the tooltip. A small blue bubble on the pill shows a counter.
     */
    public static class RailItem extends JButton {

        private static final long serialVersionUID = 1L;
        private static final int PILL_W = 48;
        private static final int PILL_H = 32;
        private static final int ICON = 20;
        private final NavIcon icon;
        private boolean active;
        private String badge = "";

        /**
         * @param label short text under the icon (one word, e.g. "Disco")
         * @param icon  icon to draw
         */
        public RailItem(String label, NavIcon icon) {
            super(label);
            this.icon = icon;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.SMALL);
            setForeground(Theme.MUTED);
            setHorizontalAlignment(CENTER);
            setVerticalAlignment(BOTTOM);
            setBorder(Theme.padding(0, 2, 2, 2));
            setAlignmentX(CENTER_ALIGNMENT);
        }

        public void setActive(boolean active) {
            this.active = active;
            setFont(active ? Theme.SMALL_BOLD : Theme.SMALL);
            setForeground(active ? Theme.ACCENT_DARK : Theme.MUTED);
            repaint();
        }

        /** Short counter on the pill (e.g. "9"); empty = hidden. */
        public void setBadge(String badge) {
            String value = badge == null ? "" : badge;
            if (!value.equals(this.badge)) {
                this.badge = value;
                repaint();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(76, 56);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int pillX = (getWidth() - PILL_W) / 2;
            if (active || getModel().isRollover()) {
                g2.setColor(active ? Theme.ACCENT_SOFT : new Color(0xEEF0F3));
                g2.fillRoundRect(pillX, 0, PILL_W, PILL_H, PILL_H, PILL_H);
            }
            g2.setColor(active ? Theme.ACCENT_DARK : Theme.MUTED);
            icon.paint(g2, (getWidth() - ICON) / 2, (PILL_H - ICON) / 2, ICON);
            if (!badge.isEmpty()) {
                g2.setFont(Theme.SMALL_BOLD.deriveFont(10f));
                FontMetrics fm = g2.getFontMetrics();
                int w = Math.max(16, fm.stringWidth(badge) + 8);
                int x = pillX + PILL_W - w + 4;
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(x, -1, w, 16, 16, 16);
                g2.setColor(Color.WHITE);
                g2.drawString(badge, x + (w - fm.stringWidth(badge)) / 2, 11);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // =====================================================================
    // UsageBar
    // =====================================================================

    /** Barra de ocupación redondeada: valor de 0 a 100. */
    public static class UsageBar extends JComponent {

        private static final long serialVersionUID = 1L;
        private int percent;

        public UsageBar() {
            setPreferredSize(new Dimension(120, 8));
            setMinimumSize(new Dimension(40, 8));
        }

        public void setPercent(int percent) {
            int value = Math.max(0, Math.min(100, percent));
            if (value != this.percent) {
                this.percent = value;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int h = getHeight();
            g2.setColor(new Color(0xE7EAEE));
            g2.fillRoundRect(0, 0, getWidth(), h, h, h);
            int w = (int) Math.round(getWidth() * percent / 100.0);
            if (w > 0) {
                g2.setColor(percent >= 90 ? Theme.STATE_BLOCKED : Theme.ACCENT);
                g2.fillRoundRect(0, 0, Math.max(w, h), h, h, h);
            }
            g2.dispose();
        }
    }

    // =====================================================================
    // Kpi
    // =====================================================================

    /** Tarjeta de indicador: título pequeño, valor grande y una línea de detalle. */
    public static class Kpi extends Card {

        private static final long serialVersionUID = 1L;
        private final JLabel value = Theme.label("—", Theme.KPI, Theme.TEXT);
        private final JLabel detail = Theme.label(" ", Theme.SMALL, Theme.MUTED);

        public Kpi(String title) {
            super(new BorderLayout());
            setBorder(Theme.padding(12, 14, 12, 14));
            JPanel column = Theme.transparent(new JPanel());
            column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
            JLabel heading = Theme.label(title, Theme.SMALL, Theme.MUTED);
            column.add(heading);
            column.add(javax.swing.Box.createVerticalStrut(4));
            column.add(value);
            column.add(javax.swing.Box.createVerticalStrut(2));
            column.add(detail);
            add(column, BorderLayout.CENTER);
        }

        public void show(String value, String detail) {
            if (!value.equals(this.value.getText())) {
                this.value.setText(value);
            }
            String line = detail == null || detail.isEmpty() ? " " : detail;
            if (!line.equals(this.detail.getText())) {
                this.detail.setText(line);
                this.detail.setToolTipText(line.isBlank() ? null : line);
            }
        }
    }

    /** Título de sección con un punto de color y un contador (ej. "● Listos  3"). */
    public static class SectionHeader extends JPanel {

        private static final long serialVersionUID = 1L;
        private final Pill count;

        public SectionHeader(String title, Color dot) {
            super(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
            JComponent mark = new JComponent() {
                private static final long serialVersionUID = 1L;

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = smooth(g);
                    g2.setColor(dot);
                    g2.fillOval(0, getHeight() / 2 - 4, 8, 8);
                    g2.dispose();
                }
            };
            mark.setPreferredSize(new Dimension(8, 16));
            add(mark);
            add(Theme.label(title, Theme.SMALL_BOLD, Theme.TEXT));
            count = new Pill("0", new Color(0xEEF0F3), Theme.MUTED);
            count.setBorder(Theme.padding(1, 7, 1, 7));
            add(count);
        }

        public void setCount(int value) {
            String text = String.valueOf(value);
            if (!text.equals(count.getText())) {
                count.setText(text);
            }
        }
    }

    // =====================================================================
    // RoundedBox
    // =====================================================================

    /**
     * Frame with rounded corners around a component that paints square (a table inside a scroll,
     * a spinner...). The content is clipped to the rounded shape and a thin border is drawn on top,
     * so the square corners of the content never show.
     */
    public static class RoundedBox extends JPanel {

        private static final long serialVersionUID = 1L;
        private final int radius;
        private final Color stroke;

        /**
         * @param content component to frame (its own border is removed)
         * @param radius  corner radius in pixels
         * @param stroke  border color, or null for no border
         */
        public RoundedBox(JComponent content, int radius, Color stroke) {
            super(new BorderLayout());
            this.radius = radius;
            this.stroke = stroke;
            setOpaque(false);
            content.setBorder(BorderFactory.createEmptyBorder());
            add(content, BorderLayout.CENTER);
        }

        @Override
        protected void paintChildren(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            super.paintChildren(g2);
            g2.dispose();
            if (stroke != null) {
                Graphics2D line = smooth(g);
                line.setColor(stroke);
                line.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
                line.dispose();
            }
        }
    }

    /** Shortcut: frames the component with the small radius and the normal border color. */
    public static RoundedBox rounded(JComponent content) {
        return new RoundedBox(content, Theme.RADIUS_SMALL, Theme.BORDER);
    }

    /** Línea fina para separar bloques dentro de una tarjeta. */
    public static JComponent divider() {
        JComponent line = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
                g2.dispose();
            }
        };
        line.setPreferredSize(new Dimension(10, 9));
        return line;
    }
}
