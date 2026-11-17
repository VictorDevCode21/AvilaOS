package com.unimet.avilaos.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Barra de desplazamiento delgada, sin flechas: un riel claro y un "pulgar" gris redondeado.
 * Theme.install() la registra para todas las barras de la interfaz.
 *
 * @author donatovodola
 */
public final class ThinScrollBarUI extends BasicScrollBarUI {

    private static final int SIZE = 10;

    /** La llama Swing (UIManager "ScrollBarUI") para cada barra nueva. */
    public static ComponentUI createUI(JComponent component) {
        return new ThinScrollBarUI();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return invisibleButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return invisibleButton();
    }

    private static JButton invisibleButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(0, 0));
        button.setMinimumSize(new Dimension(0, 0));
        button.setMaximumSize(new Dimension(0, 0));
        return button;
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        return scrollbar.getOrientation() == VERTICAL ? new Dimension(SIZE, 48) : new Dimension(48, SIZE);
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
        g.setColor(Theme.SURFACE_ALT);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumb) {
        if (thumb.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isThumbRollover() ? new Color(0xA3AAB6) : new Color(0xC6CBD3));
        int pad = 2;
        g2.fillRoundRect(thumb.x + pad, thumb.y + pad, thumb.width - 2 * pad, thumb.height - 2 * pad, 8, 8);
        g2.dispose();
    }
}
