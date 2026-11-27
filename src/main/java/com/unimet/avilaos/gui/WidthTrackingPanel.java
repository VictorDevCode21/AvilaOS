package com.unimet.avilaos.gui;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;

/**
 * Panel for a scroll pane that always takes the visible width, so its content shrinks or wraps
 * instead of going past the right edge (only vertical scroll). Used by the computers and metrics
 * views, the Disk view (block map and legend) and the Files view.
 *
 * Height: if the viewport is taller than the panel needs, the panel fills it; if it is shorter,
 * the vertical scroll bar appears. "Needs" is the preferred height by default, or the minimum
 * height for views whose parts can give up height before scrolling (see the constructor).
 *
 * @author donatovodola
 */
public class WidthTrackingPanel extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    private final boolean shrinkToMinimum;

    /** Fills the viewport's height only when it is taller than the preferred height. */
    public WidthTrackingPanel(LayoutManager layout) {
        this(layout, false);
    }

    /**
     * @param layout          layout of the panel
     * @param shrinkToMinimum true to follow the viewport's height down to the minimum height (the
     *                        layout shrinks its parts) and only scroll below it
     */
    public WidthTrackingPanel(LayoutManager layout, boolean shrinkToMinimum) {
        super(layout);
        this.shrinkToMinimum = shrinkToMinimum;
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(16, visible.height - 32);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        if (!(getParent() instanceof JViewport viewport)) {
            return false;
        }
        return shrinkToMinimum ? viewport.getHeight() >= getMinimumSize().height
                : viewport.getHeight() > getPreferredSize().height;
    }
}
