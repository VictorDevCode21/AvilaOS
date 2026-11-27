package com.unimet.avilaos.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

/**
 * Estilo visual de ÁvilaOS: paleta, tipografías y el estilo de tablas y scrolls.
 *
 * Todo es Swing puro (el enunciado solo permite librerías externas para gráficas,
 * JSON/CSV e hilos): nada de FlatLaf ni temas de terceros. install() ajusta los
 * valores por defecto de Swing para que también los diálogos se vean igual.
 *
 * Los colores de estado son los mismos en toda la interfaz: un proceso Listo es
 * azul en la tabla, en el punto de la cola y en el menú.
 *
 * @author donatovodola
 */
public final class Theme {

    // ---------- Paleta ----------
    public static final Color BACKGROUND = new Color(0xF4F5F7);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SURFACE_ALT = new Color(0xF8F9FB);    // fondo de bloques dentro de una tarjeta
    public static final Color ROW_ALT = new Color(0xFAFBFC);        // filas alternas de las tablas
    public static final Color BORDER = new Color(0xE4E7EC);
    public static final Color BORDER_STRONG = new Color(0xD6DAE1);
    public static final Color TEXT = new Color(0x1D2433);
    public static final Color MUTED = new Color(0x5D6678);          // 4.5:1 o más sobre blanco
    public static final Color ACCENT = new Color(0x2F6FED);
    public static final Color ACCENT_HOVER = new Color(0x255FD6);
    public static final Color ACCENT_SOFT = new Color(0xEAF1FE);
    public static final Color ACCENT_DARK = new Color(0x1D4FB8);

    // ---------- Colores por estado del proceso ----------
    public static final Color STATE_NEW = new Color(0x8B95A7);
    public static final Color STATE_READY = new Color(0x3B82F6);
    public static final Color STATE_RUNNING = new Color(0x16A34A);
    public static final Color STATE_BLOCKED = new Color(0xD97706);
    public static final Color STATE_TERMINATED = new Color(0x6B7280);
    /** Ready/Suspended: in the swap area, waiting for RAM. */
    public static final Color STATE_READY_SUSPENDED = new Color(0x7C3AED);
    /** Blocked/Suspended: in the swap area and still waiting for its event. */
    public static final Color STATE_BLOCKED_SUSPENDED = new Color(0xDB2777);

    // ---------- Modo de la CPU ----------
    public static final Color USER_BG = new Color(0xE7F6EC);
    public static final Color USER_FG = new Color(0x15703A);
    public static final Color KERNEL_BG = new Color(0xFFF4E0);
    public static final Color KERNEL_FG = new Color(0x8A5300);

    /** Colores para las líneas de cada computador en las gráficas. */
    public static final Color[] SERIES = {
        ACCENT, STATE_RUNNING, STATE_BLOCKED, new Color(0x7C3AED), new Color(0xDB2777), new Color(0x0D9488)
    };

    // ---------- Tipografía ----------
    private static final String SANS = pickFamily("Segoe UI", "Inter", "Helvetica Neue", "Arial", Font.SANS_SERIF);
    private static final String MONO = pickFamily("Cascadia Mono", "Consolas", "JetBrains Mono", "Menlo",
            Font.MONOSPACED);

    public static final Font BODY = new Font(SANS, Font.PLAIN, 13);
    public static final Font BODY_BOLD = new Font(SANS, Font.BOLD, 13);
    public static final Font SMALL = new Font(SANS, Font.PLAIN, 12);
    public static final Font SMALL_BOLD = new Font(SANS, Font.BOLD, 12);
    public static final Font SECTION = new Font(SANS, Font.BOLD, 14);
    public static final Font CARD_TITLE = new Font(SANS, Font.BOLD, 15);
    public static final Font KPI = new Font(SANS, Font.BOLD, 18);
    public static final Font PAGE_TITLE = new Font(SANS, Font.BOLD, 22);
    public static final Font MONO_FONT = new Font(MONO, Font.PLAIN, 12);
    public static final Font MONO_BOLD = new Font(MONO, Font.BOLD, 15);

    public static final int RADIUS = 16;

    /** Corner radius of tables, inputs and small boxes (smaller than the cards). */
    public static final int RADIUS_SMALL = 12;

    // ---------- Computer colors ----------
    /** Alpha of the hardware zone (CPU and memory) of a computer card: the stronger tint. */
    public static final int ZONE_HARDWARE_ALPHA = 26;
    /** Alpha of the process zone (queues) of a computer card: the lighter tint. */
    public static final int ZONE_PROCESSES_ALPHA = 11;
    /** Alpha of the zone borders. */
    public static final int ZONE_BORDER_ALPHA = 60;

    private Theme() {
    }

    /**
     * Ajusta los valores por defecto de Swing (Metal) para que TODO, diálogos incluidos,
     * use la paleta y la tipografía: botones sin degradado, tablas y pestañas planas.
     * Llamar una vez, en el hilo de Swing, antes de crear ventanas.
     */
    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());   // Metal: igual en todo SO
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
            // Si falla se queda el que hay; el resto del tema igual se aplica
        }
        for (String key : new String[] {"Button.gradient", "ToggleButton.gradient", "CheckBox.gradient",
            "RadioButton.gradient", "ScrollBar.gradient", "Slider.gradient", "MenuBar.gradient",
            "InternalFrame.activeTitleGradient"}) {
            UIManager.put(key, null);
        }
        for (String key : new String[] {"Label.font", "Button.font", "ToggleButton.font", "CheckBox.font",
            "RadioButton.font", "ComboBox.font", "TextField.font", "FormattedTextField.font", "Spinner.font",
            "List.font", "Table.font", "TabbedPane.font", "OptionPane.messageFont", "OptionPane.buttonFont",
            "TitledBorder.font", "ToolTip.font", "Menu.font", "MenuItem.font", "TextArea.font"}) {
            UIManager.put(key, new FontUIResource(BODY));
        }
        UIManager.put("TableHeader.font", new FontUIResource(SMALL_BOLD));
        UIManager.put("ScrollBarUI", ThinScrollBarUI.class.getName());       // barras delgadas sin flechas
        UIManager.put(ThinScrollBarUI.class.getName(), ThinScrollBarUI.class);
        UIManager.put("ScrollBar.width", 10);
        color("Panel.background", BACKGROUND);
        color("OptionPane.background", BACKGROUND);
        color("Label.foreground", TEXT);
        color("Button.background", SURFACE);
        color("Button.foreground", TEXT);
        color("Button.select", ACCENT_SOFT);
        color("Button.focus", ACCENT_SOFT);
        color("Table.background", SURFACE);
        color("Table.foreground", TEXT);
        color("Table.gridColor", BORDER);
        color("Table.selectionBackground", ACCENT_SOFT);
        color("Table.selectionForeground", TEXT);
        color("TableHeader.background", SURFACE);
        color("TableHeader.foreground", MUTED);
        color("ScrollPane.background", SURFACE);
        color("Viewport.background", SURFACE);
        color("TabbedPane.selected", SURFACE);
        color("TabbedPane.contentAreaColor", SURFACE);
        color("TabbedPane.background", BACKGROUND);
        color("TabbedPane.focus", BACKGROUND);
        color("ToolTip.background", TEXT);
        color("ToolTip.foreground", Color.WHITE);
        color("ComboBox.background", SURFACE);
        color("ComboBox.selectionBackground", ACCENT_SOFT);
        color("ComboBox.selectionForeground", TEXT);
        color("TextField.selectionBackground", ACCENT_SOFT);
        color("ProgressBar.foreground", ACCENT);
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(6, 8, 6, 8));
        UIManager.put("TitledBorder.border", BorderFactory.createLineBorder(BORDER));
        color("TitledBorder.titleColor", MUTED);
        UIManager.put("ScrollPane.border", BorderFactory.createLineBorder(BORDER));
        RoundedInputs.install();                 // rounded text fields, combos, spinners and titled borders
    }

    // ---------- Computer colors ----------

    /**
     * Color of a computer: the same one its line uses in the CPU chart, so a card and its line
     * are easy to match.
     *
     * @param computerId computer number (1, 2, ...)
     */
    public static Color computerColor(int computerId) {
        return SERIES[(Math.max(computerId, 1) - 1) % SERIES.length];
    }

    /** The same color with the given alpha (0-255): a light, semi-transparent tint over white. */
    public static Color tint(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    /** A darker version of the color, readable as text over its own tint. */
    public static Color darker(Color color) {
        return new Color((int) (color.getRed() * 0.55), (int) (color.getGreen() * 0.55),
                (int) (color.getBlue() * 0.55));
    }

    // ---------- Fábricas de piezas comunes ----------

    /** Etiqueta de texto con fuente y color dados. */
    public static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /**
     * Scroll sin borde propio (la tarjeta que lo contiene ya dibuja uno) y fondo blanco. Si va
     * dentro de una vista que también se desplaza, le pasa la rueda del mouse cuando él ya no
     * puede moverse (NestedScrollPane), así la vista no se traba con el puntero encima.
     */
    public static JScrollPane scroll(Component view) {
        JScrollPane scroll = new NestedScrollPane(view);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SURFACE);
        scroll.setBackground(SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** Espacio interior de una tarjeta. */
    public static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /**
     * Estilo de tabla de la interfaz: filas altas, solo líneas horizontales, filas alternas,
     * encabezado blanco con texto gris y una línea inferior.
     */
    public static void styleTable(JTable table) {
        table.setRowHeight(26);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(0xEEF0F3));
        table.setIntercellSpacing(new java.awt.Dimension(0, 1));
        table.setFont(SMALL);
        table.setBackground(SURFACE);
        table.setForeground(TEXT);
        table.setSelectionBackground(ACCENT_SOFT);
        table.setSelectionForeground(TEXT);
        table.setDefaultRenderer(Object.class, new ZebraRenderer());
        table.setDefaultRenderer(Number.class, new ZebraRenderer());
        table.setDefaultRenderer(Integer.class, new ZebraRenderer());
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());
        header.setBackground(SURFACE);
    }

    private static void color(String key, Color value) {
        UIManager.put(key, new ColorUIResource(value));
    }

    /** Primera familia instalada de la lista (la última es lógica y siempre existe). */
    private static String pickFamily(String... families) {
        String[] installed;
        try {
            installed = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        } catch (RuntimeException e) {
            return families[families.length - 1];
        }
        for (String wanted : families) {
            for (String name : installed) {
                if (name.equalsIgnoreCase(wanted)) {
                    return name;
                }
            }
        }
        return families[families.length - 1];
    }

    /** Celdas con padding y filas alternas. */
    private static final class ZebraRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            setBorder(padding(0, 6, 0, 6));
            setHorizontalAlignment(value instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (!isSelected) {
                setBackground(row % 2 == 0 ? SURFACE : ROW_ALT);
                setForeground(TEXT);
            }
            return this;
        }
    }

    /** Encabezado plano: texto gris en negrita y una línea abajo. */
    private static final class HeaderRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setFont(SMALL_BOLD);
            setForeground(MUTED);
            setBackground(SURFACE);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), padding(6, 6, 6, 6)));
            return this;
        }
    }

    /** Hace que un componente no pinte fondo (para ponerlo sobre una tarjeta). */
    public static <T extends JComponent> T transparent(T component) {
        component.setOpaque(false);
        return component;
    }
}
