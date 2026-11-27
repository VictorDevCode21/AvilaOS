package com.unimet.avilaos.gui;

import java.awt.Component;
import java.awt.event.MouseWheelEvent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Scroll de una tabla o un árbol que va DENTRO de una vista que también se desplaza (Métricas,
 * Disco, Archivos, Equipos).
 *
 * Un JScrollPane normal se queda con todos los eventos de la rueda del mouse aunque no tenga nada
 * que desplazar (por ejemplo, la tabla de métricas con una sola fila): con el puntero encima, la
 * vista completa deja de moverse y parece trabada. Este scroll usa la rueda mientras puede
 * desplazarse en ese sentido; cuando no (no tiene barra o ya llegó al tope), le pasa el evento al
 * scroll de la vista que lo contiene. Con Shift (desplazamiento horizontal) se comporta como siempre.
 *
 * @author victor1102
 */
final class NestedScrollPane extends JScrollPane {

    private static final long serialVersionUID = 1L;

    NestedScrollPane(Component view) {
        super(view);
    }

    @Override
    protected void processMouseWheelEvent(MouseWheelEvent e) {
        JScrollPane outer = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (outer == null || e.isShiftDown() || canScroll(e.getWheelRotation())) {
            super.processMouseWheelEvent(e);
            return;
        }
        outer.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, outer));
    }

    /** true si la barra vertical está visible y todavía puede moverse en el sentido de la rueda. */
    private boolean canScroll(int rotation) {
        JScrollBar bar = getVerticalScrollBar();
        if (!bar.isVisible() || rotation == 0) {
            return false;
        }
        return rotation < 0 ? bar.getValue() > bar.getMinimum()
                : bar.getValue() + bar.getVisibleAmount() < bar.getMaximum();
    }
}
