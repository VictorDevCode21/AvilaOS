package com.unimet.avilaos.gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

/**
 * Mode selector at the bottom of the side rail, always visible: a shield for administrator or a
 * person for user, with the session name under it ("Admin", "user2").
 *
 * Clicking it opens a menu with "Administrador" and one "Usuario" entry per user. Choosing one only
 * tells the {@link SessionListener}; the simulation decides and then calls {@link #showSession}, so
 * the control always shows the real mode.
 *
 * @author donatovodola
 */
public final class SessionControl extends JButton {

    private static final long serialVersionUID = 1L;
    private static final int PILL_W = 48;
    private static final int PILL_H = 32;
    private static final Color ADMIN_FILL = new Color(0xFFF4E0);
    private static final Color USER_FILL = new Color(0xE7F6EC);

    /** Receives the mode the user picked in the menu. */
    @FunctionalInterface
    public interface SessionListener {
        void chosen(DiskData.Session session);
    }

    private transient SessionListener listener = session -> { };
    private transient DiskData.Session session = new DiskData.Session(true, "admin");
    private String[] users = {"user1", "user2"};

    public SessionControl() {
        super("Admin");
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.SMALL_BOLD);
        setHorizontalAlignment(CENTER);
        setVerticalAlignment(BOTTOM);
        setBorder(Theme.padding(0, 2, 2, 2));
        setAlignmentX(CENTER_ALIGNMENT);
        addActionListener(e -> openMenu());
        showSession(session);
    }

    /** Who is told when the user picks a mode (replaces the previous listener). */
    public void setSessionListener(SessionListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("El listener no puede ser null");
        }
        this.listener = listener;
    }

    /** Users offered in the menu (e.g. the owners found in the file system JSON). */
    public void setUsers(String[] users) {
        this.users = users == null ? new String[0] : users.clone();
    }

    /** Shows the mode the simulation is really using. */
    public void showSession(DiskData.Session session) {
        if (session == null) {
            throw new IllegalArgumentException("La sesión no puede ser null");
        }
        this.session = session;
        setText(session.shortText());
        setForeground(session.admin() ? Theme.KERNEL_FG : Theme.USER_FG);
        setToolTipText("Modo actual: " + session.longText() + " · clic para cambiarlo");
        repaint();
    }

    private void openMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem title = new JMenuItem("Modo de uso");
        title.setEnabled(false);
        menu.add(title);
        JRadioButtonMenuItem admin = new JRadioButtonMenuItem("Administrador: todas las operaciones",
                session.admin());
        admin.addActionListener(e -> listener.chosen(new DiskData.Session(true, "admin")));
        menu.add(admin);
        menu.addSeparator();
        for (String user : users) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem("Usuario: " + user,
                    !session.admin() && user.equals(session.user()));
            item.addActionListener(e -> listener.chosen(new DiskData.Session(false, user)));
            menu.add(item);
        }
        menu.show(this, getWidth(), 0);
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
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int pillX = (getWidth() - PILL_W) / 2;
        g2.setColor(session.admin() ? ADMIN_FILL : USER_FILL);
        g2.fillRoundRect(pillX, 0, PILL_W, PILL_H, PILL_H, PILL_H);
        if (getModel().isRollover()) {
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fillRoundRect(pillX, 0, PILL_W, PILL_H, PILL_H, PILL_H);
        }
        g2.setColor(session.admin() ? Theme.KERNEL_FG : Theme.USER_FG);
        (session.admin() ? NavIcon.ADMIN : NavIcon.USER).paint(g2, (getWidth() - 20) / 2, (PILL_H - 20) / 2, 20);
        g2.dispose();
        super.paintComponent(g);
    }
}
