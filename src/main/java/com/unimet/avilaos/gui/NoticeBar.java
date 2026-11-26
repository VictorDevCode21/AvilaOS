package com.unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.Timer;

/**
 * Thin banner above the views for messages that must be seen from any view, mainly rejected
 * operations ("Operación rechazada: ..."). It never blocks: no dialog, the simulation keeps running,
 * and it hides by itself after a few seconds (or with its × button).
 *
 * @author donatovodola
 */
public final class NoticeBar extends UiKit.Card {

    private static final long serialVersionUID = 1L;
    private static final int VISIBLE_MS = 8000;

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
        colors(fill, null);
        text.setForeground(foreground);
        String safe = message == null ? "" : message.replace("&", "&amp;").replace("<", "&lt;");
        text.setText("<html>" + (lead.isEmpty() ? "" : "<b>" + lead + "</b> ") + safe + "</html>");
        setVisible(true);
        hideTimer.restart();
        revalidate();
        repaint();
    }
}
