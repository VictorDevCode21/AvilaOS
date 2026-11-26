package com.unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.Timer;

/**
 * Floating notice (toast) for messages that must be seen from any view, mainly rejected
 * operations ("Operación rechazada: ..."). MainWindow places it over the right end of the KPI strip,
 * in the window's layered pane: showing or hiding it never moves the views, and it never covers a
 * control. It also takes the mouse clicks that land on it, so they never reach what is underneath.
 * It never blocks: no dialog, the simulation keeps running, and it hides by itself after a few
 * seconds (or with its × button).
 *
 * @author donatovodola
 */
public final class NoticeBar extends UiKit.Card {

    private static final long serialVersionUID = 1L;
    private static final int VISIBLE_MS = 8000;
    /** Text width in pixels: long messages wrap instead of making the notice wider than the view. */
    private static final int TEXT_WIDTH = 380;

    private final JLabel text = new JLabel();
    private final transient Timer hideTimer = new Timer(VISIBLE_MS, e -> setVisible(false));

    public NoticeBar() {
        super(new BorderLayout(12, 0));
        setBorder(Theme.padding(9, 14, 9, 8));
        text.setFont(Theme.SMALL);
        add(text, BorderLayout.CENTER);
        UiKit.Button close = new UiKit.Button("×", UiKit.Kind.GHOST);
        close.setToolTipText("Cerrar");
        close.addActionListener(e -> setVisible(false));
        add(close, BorderLayout.EAST);
        hideTimer.setRepeats(false);
        // Clicks on the notice stop here: they must never reach (and press) what is underneath
        addMouseListener(new java.awt.event.MouseAdapter() { });
        setVisible(false);
    }

    /** Red message: an operation was rejected and why. */
    public void showError(String message) {
        showMessage("Operación rechazada.", message, new Color(0xFDECEC), new Color(0x7F1D1D));
    }

    /** Neutral message (e.g. the mode changed). */
    public void showInfo(String message) {
        showMessage("", message, Theme.ACCENT_SOFT, Theme.ACCENT_DARK);
    }

    private void showMessage(String lead, String message, Color fill, Color foreground) {
        colors(fill, new Color(0, 0, 0, 38));     // thin border so it stands out over the view
        text.setForeground(foreground);
        String safe = message == null ? "" : message.replace("&", "&amp;").replace("<", "&lt;");
        text.setText("<html><div style='width:" + TEXT_WIDTH + "px'>" + (lead.isEmpty() ? "" : "<b>" + lead + "</b> ")
                + safe + "</div></html>");
        setSize(getPreferredSize());
        setVisible(true);
        hideTimer.restart();
        revalidate();
        repaint();
    }
}
