package com.unimet.avilaos.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * FlowLayout that reports the height it really needs when its items wrap to more lines, so a
 * legend with many items grows downward instead of being cut on the right.
 *
 * @author donatovodola
 */
public final class WrapLayout extends FlowLayout {

    private static final long serialVersionUID = 1L;

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension size = layoutSize(target, false);
        size.width -= getHgap() + 1;
        return size;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            Container container = target;
            while (container.getSize().width == 0 && container.getParent() != null) {
                container = container.getParent();
            }
            targetWidth = container.getSize().width;
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }
            Insets insets = target.getInsets();
            int maxWidth = targetWidth - (insets.left + insets.right + getHgap() * 2);
            int width = 0;
            int height = 0;
            int rowWidth = 0;
            int rowHeight = 0;
            for (int i = 0; i < target.getComponentCount(); i++) {
                Component c = target.getComponent(i);
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = preferred ? c.getPreferredSize() : c.getMinimumSize();
                if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                    width = Math.max(width, rowWidth);
                    height += rowHeight + getVgap();
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += d.width + getHgap();
                rowHeight = Math.max(rowHeight, d.height);
            }
            width = Math.max(width, rowWidth);
            height += rowHeight;
            return new Dimension(width + insets.left + insets.right + getHgap() * 2,
                    height + insets.top + insets.bottom + getVgap() * 2);
        }
    }
}
