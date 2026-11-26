package com.unimet.avilaos.gui;

import java.awt.Color;

/**
 * Gives each file (and each suspended process) a color and keeps it while the program runs, so the
 * same file has the same color in the block map, the legend, the tree and the allocation table.
 *
 * Files get light pastel colors that differ in lightness too (not only in hue). Suspended processes
 * get a separate, stronger violet/pink family so they never look like a file.
 *
 * Uses plain arrays (java.util collections are not allowed in the project).
 *
 * @author donatovodola
 */
public final class OwnerPalette {

    private static final Color[] FILE_COLORS = {
        new Color(0x93C5FD), new Color(0xFCA5A5), new Color(0x86EFAC), new Color(0xFDE68A),
        new Color(0xA5B4FC), new Color(0x5EEAD4), new Color(0xFDBA74), new Color(0xF9A8D4),
        new Color(0xBEF264), new Color(0x7DD3FC), new Color(0xD8B4FE), new Color(0xFCD34D)
    };

    private static final Color[] PROCESS_COLORS = {
        new Color(0x7C3AED), new Color(0xDB2777), new Color(0x6D28D9), new Color(0xBE185D)
    };

    private String[] keys = new String[16];
    private Color[] colors = new Color[16];
    private int size;
    private int nextFile;
    private int nextProcess;

    /**
     * Color of an owner. The first time an owner is asked for, it gets the next free color.
     *
     * @param owner   file path or process id
     * @param process true for a suspended process
     */
    public Color colorOf(String owner, boolean process) {
        String key = (process ? "p:" : "f:") + owner;
        for (int i = 0; i < size; i++) {
            if (keys[i].equals(key)) {
                return colors[i];
            }
        }
        Color color = process ? PROCESS_COLORS[nextProcess++ % PROCESS_COLORS.length]
                : FILE_COLORS[nextFile++ % FILE_COLORS.length];
        if (size == keys.length) {
            String[] k = new String[size * 2];
            Color[] c = new Color[size * 2];
            System.arraycopy(keys, 0, k, 0, size);
            System.arraycopy(colors, 0, c, 0, size);
            keys = k;
            colors = c;
        }
        keys[size] = key;
        colors[size] = color;
        size++;
        return color;
    }

    /** True if text on this color should be white (dark colors), false for dark text. */
    public static boolean needsLightText(Color color) {
        double luminance = (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
        return luminance < 0.55;
    }

    /** Forgets every color (e.g. when the simulation is reset with a new file system). */
    public void clear() {
        size = 0;
        nextFile = 0;
        nextProcess = 0;
    }
}
