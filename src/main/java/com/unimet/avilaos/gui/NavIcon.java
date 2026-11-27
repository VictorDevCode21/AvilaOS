package com.unimet.avilaos.gui;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

/**
 * Icons of the side rail and of the views (file tree), drawn with Graphics2D (no image files).
 *
 * Each constant paints itself inside a square of the given size, using the current color of the
 * Graphics2D, so the same icon works for the active (blue) and inactive (gray) states.
 *
 * @author donatovodola
 */
public enum NavIcon {

    /** A chip: one computer and its CPU. */
    COMPUTER {
        @Override
        void draw(Graphics2D g, float u) {
            g.draw(rect(5, 5, 14, 14, 2, u));
            for (float p : new float[] {9, 15}) {
                g.draw(line(p, 1.5f, p, 5, u));
                g.draw(line(p, 19, p, 22.5f, u));
                g.draw(line(1.5f, p, 5, p, u));
                g.draw(line(19, p, 22.5f, p, u));
            }
        }
    },

    /** Axes and a rising line: performance metrics. */
    METRICS {
        @Override
        void draw(Graphics2D g, float u) {
            Path2D axes = new Path2D.Float();
            axes.moveTo(3 * u, 3 * u);
            axes.lineTo(3 * u, 21 * u);
            axes.lineTo(21 * u, 21 * u);
            g.draw(axes);
            Path2D trend = new Path2D.Float();
            trend.moveTo(7 * u, 15 * u);
            trend.lineTo(11 * u, 11 * u);
            trend.lineTo(14 * u, 14 * u);
            trend.lineTo(20 * u, 7 * u);
            g.draw(trend);
        }
    },

    /** Lines of text: the event log. */
    LOG {
        @Override
        void draw(Graphics2D g, float u) {
            g.draw(rect(4, 3, 16, 18, 2, u));
            g.draw(line(8, 8, 16, 8, u));
            g.draw(line(8, 12, 16, 12, u));
            g.draw(line(8, 16, 13, 16, u));
        }
    },

    /** A cylinder: the simulated disk (Project 2). */
    DISK {
        @Override
        void draw(Graphics2D g, float u) {
            g.drawOval(Math.round(3 * u), Math.round(2 * u), Math.round(18 * u), Math.round(6 * u));
            g.draw(line(3, 5, 3, 19, u));
            g.draw(line(21, 5, 21, 19, u));
            g.drawArc(Math.round(3 * u), Math.round(16 * u), Math.round(18 * u), Math.round(6 * u), 180, 180);
            g.drawArc(Math.round(3 * u), Math.round(9 * u), Math.round(18 * u), Math.round(6 * u), 180, 180);
        }
    },

    /** A folder: the file system (Project 2). */
    FILES {
        @Override
        void draw(Graphics2D g, float u) {
            Path2D folder = new Path2D.Float();
            folder.moveTo(3 * u, 6 * u);
            folder.lineTo(9 * u, 6 * u);
            folder.lineTo(11 * u, 8 * u);
            folder.lineTo(21 * u, 8 * u);
            folder.lineTo(21 * u, 19 * u);
            folder.lineTo(3 * u, 19 * u);
            folder.closePath();
            g.draw(folder);
        }
    },

    /** A sheet with a folded corner: one file (used in the file tree). */
    DOCUMENT {
        @Override
        void draw(Graphics2D g, float u) {
            Path2D sheet = new Path2D.Float();
            sheet.moveTo(6 * u, 3 * u);
            sheet.lineTo(14 * u, 3 * u);
            sheet.lineTo(19 * u, 8 * u);
            sheet.lineTo(19 * u, 21 * u);
            sheet.lineTo(6 * u, 21 * u);
            sheet.closePath();
            g.draw(sheet);
            Path2D fold = new Path2D.Float();
            fold.moveTo(14 * u, 3 * u);
            fold.lineTo(14 * u, 8 * u);
            fold.lineTo(19 * u, 8 * u);
            g.draw(fold);
        }
    },

    /** A person: user mode (restricted access). */
    USER {
        @Override
        void draw(Graphics2D g, float u) {
            g.drawOval(Math.round(8 * u), Math.round(3 * u), Math.round(8 * u), Math.round(8 * u));
            g.drawArc(Math.round(4 * u), Math.round(14 * u), Math.round(16 * u), Math.round(14 * u), 0, 180);
        }
    },

    /** A shield with a check: administrator mode (every operation allowed). */
    ADMIN {
        @Override
        void draw(Graphics2D g, float u) {
            Path2D shield = new Path2D.Float();
            shield.moveTo(12 * u, 2.5f * u);
            shield.lineTo(20 * u, 5.5f * u);
            shield.curveTo(20 * u, 13 * u, 17 * u, 18.5f * u, 12 * u, 21.5f * u);
            shield.curveTo(7 * u, 18.5f * u, 4 * u, 13 * u, 4 * u, 5.5f * u);
            shield.closePath();
            g.draw(shield);
            Path2D check = new Path2D.Float();
            check.moveTo(8.5f * u, 12 * u);
            check.lineTo(11 * u, 14.5f * u);
            check.lineTo(15.5f * u, 9.5f * u);
            g.draw(check);
        }
    },

    /** A padlock: private file. */
    LOCK {
        @Override
        void draw(Graphics2D g, float u) {
            g.draw(rect(5, 11, 14, 10, 2, u));
            g.drawArc(Math.round(8 * u), Math.round(4 * u), Math.round(8 * u), Math.round(12 * u), 0, 180);
            g.draw(line(8, 10, 8, 11, u));
            g.draw(line(16, 10, 16, 11, u));
        }
    };

    /**
     * Paints the icon in a size x size square starting at (x, y), with the current color.
     *
     * @param g    graphics to paint on (it is not modified: a copy is used)
     * @param x    left edge
     * @param y    top edge
     * @param size side of the square in pixels (the icons are designed on a 24 px grid)
     */
    public void paint(Graphics2D g, int x, int y, int size) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y);
        float u = size / 24f;
        g2.setStroke(new BasicStroke(Math.max(1.4f, 1.8f * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        draw(g2, u);
        g2.dispose();
    }

    /** Draws the icon on a 24 x 24 grid scaled by u. */
    abstract void draw(Graphics2D g, float u);

    private static java.awt.Shape rect(float x, float y, float w, float h, float r, float u) {
        return new java.awt.geom.RoundRectangle2D.Float(x * u, y * u, w * u, h * u, 2 * r * u, 2 * r * u);
    }

    private static java.awt.Shape line(float x1, float y1, float x2, float y2, float u) {
        return new java.awt.geom.Line2D.Float(x1 * u, y1 * u, x2 * u, y2 * u);
    }
}
