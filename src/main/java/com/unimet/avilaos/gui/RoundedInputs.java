package com.unimet.avilaos.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.AbstractButton;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicFormattedTextFieldUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.plaf.basic.BasicTextFieldUI;

/**
 * Rounded look for the input fields of every window and dialog: text fields, combo boxes,
 * spinners, plain buttons and titled borders. {@link #install()} registers it once (Theme.install calls it), so
 * existing forms change look without touching their code.
 *
 * Swing paints these components as squares, so each one first paints its corners with the color
 * behind it (see {@link #backgroundBehind}) and then a rounded white background. The border is a
 * thin rounded line that turns blue while the field has the focus.
 *
 * @author donatovodola
 */
public final class RoundedInputs {

    /** Corner radius of the inputs. */
    public static final int RADIUS = 10;
    private static final Color DISABLED_FILL = new Color(0xF3F4F6);

    private RoundedInputs() {
    }

    /** Registers the rounded UI delegates and borders in the UIManager (call after the look and feel). */
    public static void install() {
        register("TextFieldUI", TextFieldUI.class);
        register("FormattedTextFieldUI", FormattedTextFieldUI.class);
        register("ComboBoxUI", ComboBoxUI.class);
        register("SpinnerUI", SpinnerUI.class);
        register("ButtonUI", ButtonUI.class);
        UIManager.put("Button.border", new RoundedBorder(Theme.BORDER_STRONG, 1, new Insets(5, 14, 5, 14)));
        UIManager.put("TextField.border", new RoundedBorder(Theme.BORDER_STRONG, 1, new Insets(5, 10, 5, 10)));
        UIManager.put("FormattedTextField.border",
                new RoundedBorder(Theme.BORDER_STRONG, 1, new Insets(5, 10, 5, 10)));
        UIManager.put("ComboBox.border", new RoundedBorder(Theme.BORDER_STRONG, 1, new Insets(3, 8, 3, 4)));
        UIManager.put("Spinner.border", new RoundedBorder(Theme.BORDER_STRONG, 1, new Insets(2, 6, 2, 4)));
        UIManager.put("TitledBorder.border", new RoundedBorder(Theme.BORDER, 1, new Insets(6, 8, 6, 8), false));
    }

    private static void register(String key, Class<?> ui) {
        UIManager.put(key, ui.getName());
        UIManager.put(ui.getName(), ui);
    }

    /**
     * Color behind a component: the fill of the nearest card, or the background of the nearest
     * opaque parent. Used to paint the corners outside the rounded shape.
     */
    public static Color backgroundBehind(Component component) {
        Container parent = component.getParent();
        while (parent != null) {
            if (parent instanceof UiKit.Card card) {
                Color fill = card.getFill();
                if (fill.getAlpha() == 255) {
                    return fill;
                }
                return blend(fill, backgroundBehind(card));       // tinted zones are semi-transparent
            }
            if (parent.isOpaque() && parent.getBackground() != null) {
                return parent.getBackground();
            }
            parent = parent.getParent();
        }
        return Theme.BACKGROUND;
    }

    private static Color blend(Color top, Color bottom) {
        float a = top.getAlpha() / 255f;
        return new Color(Math.round(top.getRed() * a + bottom.getRed() * (1 - a)),
                Math.round(top.getGreen() * a + bottom.getGreen() * (1 - a)),
                Math.round(top.getBlue() * a + bottom.getBlue() * (1 - a)));
    }

    /** Corners in the color behind, then the rounded fill (white, or gray when disabled). */
    private static void paintRoundedBackground(Graphics g, JComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(backgroundBehind(c));
        g2.fillRect(0, 0, c.getWidth(), c.getHeight());
        g2.setColor(c.isEnabled() ? Theme.SURFACE : DISABLED_FILL);
        g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, RADIUS, RADIUS);
        g2.dispose();
    }

    // =====================================================================
    // Border
    // =====================================================================

    /** Thin rounded line with inner padding; blue while the component has the focus. */
    public static final class RoundedBorder extends AbstractBorder {

        private static final long serialVersionUID = 1L;
        private final Color color;
        private final int thickness;
        private final Insets padding;
        private final boolean showFocus;

        /**
         * @param color     line color
         * @param thickness line width in pixels (2 for errors)
         * @param padding   space between the line and the content
         */
        public RoundedBorder(Color color, int thickness, Insets padding) {
            this(color, thickness, padding, true);
        }

        RoundedBorder(Color color, int thickness, Insets padding, boolean showFocus) {
            this.color = color;
            this.thickness = thickness;
            this.padding = padding;
            this.showFocus = showFocus;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focused = showFocus && hasFocus(c);
            g2.setColor(focused && thickness == 1 ? Theme.ACCENT : color);
            g2.setStroke(new BasicStroke(focused && thickness == 1 ? 1.6f : thickness));
            float half = (focused && thickness == 1 ? 1.6f : thickness) / 2f;
            g2.draw(new java.awt.geom.RoundRectangle2D.Float(x + half, y + half, width - 1 - 2 * half + 1,
                    height - 1 - 2 * half + 1, RADIUS, RADIUS));
            g2.dispose();
        }

        private static boolean hasFocus(Component c) {
            if (c.hasFocus()) {
                return true;
            }
            // A spinner or combo box: the focus is on its inner editor
            return c instanceof Container container && container.isAncestorOf(
                    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return getBorderInsets(c, new Insets(0, 0, 0, 0));
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.top = padding.top + thickness;
            insets.left = padding.left + thickness;
            insets.bottom = padding.bottom + thickness;
            insets.right = padding.right + thickness;
            return insets;
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }

    // =====================================================================
    // Text fields
    // =====================================================================

    /** JTextField with a rounded background. */
    public static final class TextFieldUI extends BasicTextFieldUI {

        public static ComponentUI createUI(JComponent c) {
            return new TextFieldUI();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            getComponent().addFocusListener(new RepaintOnFocus());
        }

        @Override
        protected void paintBackground(Graphics g) {
            paintRoundedBackground(g, getComponent());
        }
    }

    /** JFormattedTextField (also the editor inside spinners) with a rounded background. */
    public static final class FormattedTextFieldUI extends BasicFormattedTextFieldUI {

        public static ComponentUI createUI(JComponent c) {
            return new FormattedTextFieldUI();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            getComponent().addFocusListener(new RepaintOnFocus());
        }

        @Override
        protected void paintBackground(Graphics g) {
            paintRoundedBackground(g, getComponent());
        }
    }

    /** Repaints the field (and its spinner, if any) so the border turns blue / gray with the focus. */
    private static final class RepaintOnFocus extends java.awt.event.FocusAdapter {

        @Override
        public void focusGained(java.awt.event.FocusEvent e) {
            repaint(e.getComponent());
        }

        @Override
        public void focusLost(java.awt.event.FocusEvent e) {
            repaint(e.getComponent());
        }

        private static void repaint(Component c) {
            c.repaint();
            Component spinner = javax.swing.SwingUtilities.getAncestorOfClass(JSpinner.class, c);
            if (spinner != null) {
                spinner.repaint();
            }
        }
    }

    // =====================================================================
    // Combo box
    // =====================================================================

    /** JComboBox with a rounded background and a flat chevron instead of the gray Metal button. */
    public static final class ComboBoxUI extends BasicComboBoxUI {

        public static ComponentUI createUI(JComponent c) {
            return new ComboBoxUI();
        }

        @Override
        protected JButton createArrowButton() {
            JButton button = new ArrowButton(SwingConstants.SOUTH);
            button.setName("ComboBox.arrowButton");
            return button;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            paintRoundedBackground(g, c);
            hasFocus = comboBox.hasFocus();
            if (!comboBox.isEditable()) {
                Rectangle r = rectangleForCurrentValue();
                paintCurrentValue(g, r, false);
            }
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean focus) {
            // the rounded background is already painted
        }

        @Override
        public Dimension getMinimumSize(JComponent c) {
            Dimension size = super.getMinimumSize(c);
            return new Dimension(size.width, Math.max(size.height, 30));
        }
    }

    // =====================================================================
    // Spinner
    // =====================================================================

    /** JSpinner with a rounded background and flat arrows. */
    public static final class SpinnerUI extends BasicSpinnerUI {

        public static ComponentUI createUI(JComponent c) {
            return new SpinnerUI();
        }

        @Override
        protected Component createNextButton() {
            JButton button = new ArrowButton(SwingConstants.NORTH);
            button.setName("Spinner.nextButton");
            installNextButtonListeners(button);
            return button;
        }

        @Override
        protected Component createPreviousButton() {
            JButton button = new ArrowButton(SwingConstants.SOUTH);
            button.setName("Spinner.previousButton");
            installPreviousButtonListeners(button);
            return button;
        }

        @Override
        protected JComponent createEditor() {
            JComponent editor = super.createEditor();
            flatten(editor);
            return editor;
        }

        @Override
        protected void replaceEditor(JComponent oldEditor, JComponent newEditor) {
            super.replaceEditor(oldEditor, newEditor);
            flatten(newEditor);
        }

        /** The text inside the spinner draws no background or border of its own. */
        private static void flatten(JComponent editor) {
            editor.setOpaque(false);
            if (editor instanceof JSpinner.DefaultEditor defaultEditor) {
                JFormattedTextField field = defaultEditor.getTextField();
                field.setOpaque(false);
                field.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 4, 0, 0));
            }
        }

        @Override
        public void update(Graphics g, JComponent c) {
            paintRoundedBackground(g, c);
            paint(g, c);
        }
    }

    // =====================================================================
    // Plain buttons
    // =====================================================================

    /**
     * Plain JButton (dialogs, JOptionPane): white rounded background that darkens slightly on hover
     * and press. Buttons that paint themselves (UiKit.Button, rail items, arrows) turn off
     * "content area filled", so this UI leaves them alone.
     */
    public static final class ButtonUI extends BasicButtonUI {

        public static ComponentUI createUI(JComponent c) {
            return new ButtonUI();
        }

        @Override
        protected void installDefaults(AbstractButton b) {
            super.installDefaults(b);
            b.setRolloverEnabled(true);
            b.setOpaque(false);
        }

        @Override
        public void update(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            if (b.isContentAreaFilled()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(!b.isEnabled() ? DISABLED_FILL : b.getModel().isPressed() ? Theme.ACCENT_SOFT
                        : b.getModel().isRollover() ? Theme.SURFACE_ALT : Theme.SURFACE);
                g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, RADIUS, RADIUS);
                g2.dispose();
            }
            paint(g, c);
        }

        @Override
        protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect,
                Rectangle iconRect) {
            // the rounded border already turns blue with the focus
        }
    }

    /** Flat button with a small chevron (combo box and spinner arrows). */
    private static final class ArrowButton extends JButton {

        private static final long serialVersionUID = 1L;
        private final int direction;

        ArrowButton(int direction) {
            this.direction = direction;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFocusable(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setBorder(javax.swing.BorderFactory.createEmptyBorder());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(22, 14);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(!isEnabled() ? Theme.BORDER_STRONG : getModel().isRollover() ? Theme.ACCENT : Theme.MUTED);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int s = Math.min(4, Math.max(2, getHeight() / 4));
            if (direction == SwingConstants.NORTH) {
                g2.drawPolyline(new int[] {cx - s, cx, cx + s}, new int[] {cy + s / 2, cy - s / 2, cy + s / 2}, 3);
            } else {
                g2.drawPolyline(new int[] {cx - s, cx, cx + s}, new int[] {cy - s / 2, cy + s / 2, cy - s / 2}, 3);
            }
            g2.dispose();
        }
    }
}
