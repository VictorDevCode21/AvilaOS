package com.unimet.avilaos.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * Row of options where exactly one is selected (e.g. the disk policy: FIFO, SSTF, SCAN...).
 *
 * Clicking an option does NOT select it by itself: it tells the listener, and whoever owns the
 * simulation decides and then calls {@link #setSelected(String)}. This way the view always shows
 * the policy the system is really using.
 *
 * @author donatovodola
 */
public final class SegmentedControl extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Receives the option the user clicked. */
    @FunctionalInterface
    public interface ChoiceListener {
        void chosen(String option);
    }

    private final Option[] options;
    private transient ChoiceListener listener = option -> { };

    /** @param labels the options, in order */
    public SegmentedControl(String[] labels) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));
        setOpaque(false);
        options = new Option[labels.length];
        for (int i = 0; i < labels.length; i++) {
            Option option = new Option(labels[i]);
            option.addActionListener(e -> listener.chosen(option.getText()));
            options[i] = option;
            add(option);
        }
    }

    /** Who is told when the user clicks an option (replaces the previous listener). */
    public void setChoiceListener(ChoiceListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("El listener no puede ser null");
        }
        this.listener = listener;
    }

    /** Marks the option the system is using (no listener is called). */
    public void setSelected(String label) {
        for (Option option : options) {
            option.setSelectedOption(option.getText().equals(label));
        }
    }

    /** One option: blue when selected, white with a border when not. */
    private static final class Option extends JButton {

        private static final long serialVersionUID = 1L;
        private boolean selected;

        Option(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.SMALL_BOLD);
            setForeground(Theme.TEXT);
            setBorder(Theme.padding(0, 12, 0, 12));
        }

        void setSelectedOption(boolean selected) {
            this.selected = selected;
            setForeground(selected ? Color.WHITE : Theme.TEXT);
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, 32);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected) {
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            } else {
                g2.setColor(getModel().isRollover() ? Theme.SURFACE_ALT : Theme.SURFACE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(Theme.BORDER_STRONG);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
