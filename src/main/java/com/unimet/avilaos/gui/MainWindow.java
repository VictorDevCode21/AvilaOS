package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.events.EventLog;
import com.unimet.avilaos.events.LogEntry;
import com.unimet.avilaos.metrics.ComputerMetrics;
import com.unimet.avilaos.metrics.MetricsSnapshot;
import com.unimet.avilaos.os.disk.DiskPolicyType;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

/**
 * Ventana principal del simulador ÁvilaOS.
 *
 * Izquierda: barra angosta con un ícono y un texto corto por vista (Computadores, Disco,
 * Archivos, Métricas, Log de eventos). Proyecto 2: ya no hay vista de Buffers.
 * Arriba: el nombre de la vista, el ciclo de reloj y los controles (iniciar/pausar,
 * reiniciar, duración del ciclo, cargar los JSON, crear proceso, Planificación y Configuración), y una
 * franja de indicadores que se ve en todas las vistas (CPUs, memoria, procesos, rendimiento).
 * Centro: UNA vista a la vez, para no saturar la pantalla.
 *
 * Agregar una vista (ej. el disco en el Proyecto 2) es llamar addView con su nombre, texto corto,
 * ícono y panel.
 * La ventana solo muestra y reenvía las acciones del usuario: no contiene lógica del SO.
 *
 * @author NicollPinzon
 */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Valores iniciales mientras no se cargue un archivo: los mismos de SimulationConfig.defaults(). */
    private static final SimulationConfig DEFAULTS = SimulationConfig.defaults();

    /** Quantum inicial mientras no se cargue la configuración (se cambia en la ventana Planificación). */
    public static final int DEFAULT_QUANTUM = DEFAULTS.getQuantum();

    /** Duración inicial del ciclo (ms) mientras no se cargue la configuración. */
    public static final int DEFAULT_CYCLE_MS = (int) DEFAULTS.getCycleDurationMs();

    /** Máximo de líneas en el log en pantalla (igual que la capacidad de EventLog). */
    public static final int MAX_LOG_LINES = EventLog.DEFAULT_CAPACITY;

    /** Width of the side rail in pixels. */
    private static final int RAIL_WIDTH = 84;

    // Nombres de las vistas (también son el título de la página)
    static final String VIEW_COMPUTERS = "Computadores";
    static final String VIEW_METRICS = "Métricas";
    static final String VIEW_LOG = "Log de eventos";
    static final String VIEW_DISK = "Disco";
    static final String VIEW_FILES = "Archivos";

    // ---------- Controles ----------
    private final JLabel cycleLabel = Theme.label("Ciclo 0", Theme.MONO_BOLD, Theme.TEXT);
    private final UiKit.Button startPauseButton = new UiKit.Button("Iniciar", UiKit.Kind.PRIMARY);
    private final UiKit.Button resetButton = new UiKit.Button("Reiniciar", UiKit.Kind.SECONDARY);
    // Rangos de SimulationConfig: la GUI y el archivo CSV aceptan exactamente lo mismo
    private final JSpinner cycleDurationSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_CYCLE_MS,
            (int) SimulationConfig.MIN_CYCLE_MS, (int) SimulationConfig.MAX_CYCLE_MS, 50));
    private final UiKit.Button createProcessButton = new UiKit.Button("+ Proceso", UiKit.Kind.SECONDARY);
    private final UiKit.Button createIoProcessButton = new UiKit.Button("+ Proceso de E/S", UiKit.Kind.SECONDARY);
    /** Its text says if both JSON files (file system, processes and config) are loaded. */
    private final UiKit.Button loadJsonButton = new UiKit.Button("Cargar JSON", UiKit.Kind.SECONDARY);
    private final UiKit.Button planningButton = new UiKit.Button("Planificación", UiKit.Kind.GHOST);
    private final UiKit.Button configButton = new UiKit.Button("Configuración", UiKit.Kind.GHOST);

    /**
     * Acción por defecto de los botones que todavía nadie conectó: avisa en vez de no hacer nada.
     * Quien arma la simulación la reemplaza con los set*Action.
     */
    private final transient ActionListener notAvailable = e -> JOptionPane.showMessageDialog(this,
            "Disponible en una próxima versión.", "En construcción", JOptionPane.INFORMATION_MESSAGE);

    // ---------- Navegación ----------
    private final JPanel navList = Theme.transparent(new JPanel());
    private final CardLayout viewLayout = new CardLayout();
    private final JPanel views = Theme.transparent(new JPanel(viewLayout));
    private final JLabel pageTitle = Theme.label(VIEW_COMPUTERS, Theme.PAGE_TITLE, Theme.TEXT);
    private UiKit.RailItem[] navItems = new UiKit.RailItem[0];
    private String[] navNames = new String[0];

    // ---------- Indicadores (franja superior) ----------
    private final UiKit.Kpi cpuKpi;
    private final UiKit.Kpi memoryKpi = new UiKit.Kpi("Memoria");
    private final UiKit.Kpi processKpi = new UiKit.Kpi("Procesos");
    private final UiKit.Kpi performanceKpi = new UiKit.Kpi("Rendimiento");

    // ---------- Contenido de las vistas ----------
    private final JTextArea logArea = new JTextArea();
    private final MetricsTableModel metricsModel = new MetricsTableModel();
    private final CpuUsageChart cpuChart = new CpuUsageChart();
    /** Shared by the Disk and Files views so a file has the same color in both. */
    private final transient OwnerPalette ownerPalette = new OwnerPalette();
    private final DiskPanel diskPanel = new DiskPanel(ownerPalette);
    private final FileSystemPanel fileSystemPanel = new FileSystemPanel(ownerPalette);
    private final DiskMetricsPanel diskMetricsPanel = new DiskMetricsPanel(DiskPolicyType.labels());
    /** Mode selector (administrator / user) at the bottom of the rail, always visible. */
    private final SessionControl sessionControl = new SessionControl();
    /** Banner for rejected operations and other notices, visible from every view. */
    private final NoticeBar noticeBar = new NoticeBar();
    /** KPI strip: the floating notice is placed over it (read-only cards, nothing to click). */
    private final JPanel kpiStrip = Theme.transparent(new JPanel());

    private final ComputerPanel[] computerPanels;

    /**
     * @param computerCount number of computers (at least 1: Project 2 uses a single computer)
     */
    public MainWindow(int computerCount) {
        super("ÁvilaOS - Simulador de sistema operativo distribuido");
        if (computerCount < 1) {
            throw new IllegalArgumentException("Se necesita al menos 1 computador");
        }

        cpuKpi = new UiKit.Kpi(computerCount == 1 ? "CPU" : "CPUs");
        computerPanels = new ComputerPanel[computerCount];
        for (int i = 0; i < computerCount; i++) {
            computerPanels[i] = new ComputerPanel(i + 1, computerCount == 1);
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BACKGROUND);
        root.add(buildRail(), BorderLayout.WEST);
        root.add(buildMain(), BorderLayout.CENTER);
        setContentPane(root);
        // The notice floats over the views (layered pane), so showing it never moves them
        getLayeredPane().add(noticeBar, javax.swing.JLayeredPane.POPUP_LAYER);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                placeNotice();
            }
        });

        addView(VIEW_COMPUTERS, "Equipos", NavIcon.COMPUTER, buildComputersView());
        addView(VIEW_DISK, "Disco", NavIcon.DISK, diskPanel);
        addView(VIEW_FILES, "Archivos", NavIcon.FILES, fileSystemPanel);
        addView(VIEW_METRICS, "Métricas", NavIcon.METRICS, buildMetricsView());
        addView(VIEW_LOG, "Log", NavIcon.LOG, buildLogView());
        showView(VIEW_COMPUTERS);

        setPreferredSize(new Dimension(1440, 900));
        setMinimumSize(new Dimension(1000, 640));
        pack();
        setLocationRelativeTo(null);

        // Hasta que alguien los conecte, los botones avisan "en construcción" en vez de quedarse mudos.
        for (JButton button : new JButton[] {startPauseButton, resetButton, createProcessButton, createIoProcessButton,
            loadJsonButton, planningButton, configButton}) {
            button.addActionListener(notAvailable);
        }
    }

    // ---------- Construcción ----------

    /** Narrow side rail: the logo on top and one icon with a short label per view. */
    private JPanel buildRail() {
        JPanel rail = new JPanel(new BorderLayout()) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Theme.BORDER);
                g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            }
        };
        rail.setBackground(Theme.SURFACE);
        rail.setPreferredSize(new Dimension(RAIL_WIDTH, 10));
        rail.setBorder(Theme.padding(16, 0, 16, 0));

        JPanel brand = Theme.transparent(new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)));
        Logo logo = new Logo();
        logo.setToolTipText("ÁvilaOS");
        brand.add(logo);
        brand.setBorder(Theme.padding(0, 0, 22, 0));
        rail.add(brand, BorderLayout.NORTH);

        navList.setLayout(new BoxLayout(navList, BoxLayout.Y_AXIS));
        rail.add(navList, BorderLayout.CENTER);
        JPanel session = Theme.transparent(new JPanel());
        session.setLayout(new BoxLayout(session, BoxLayout.Y_AXIS));
        JLabel modeTitle = Theme.label("MODO", Theme.SMALL_BOLD.deriveFont(10f), Theme.MUTED);
        modeTitle.setAlignmentX(CENTER_ALIGNMENT);
        session.add(modeTitle);
        session.add(Box.createVerticalStrut(6));
        session.add(sessionControl);
        rail.add(session, BorderLayout.SOUTH);
        return rail;
    }

    private JPanel buildMain() {
        JPanel main = Theme.transparent(new JPanel(new BorderLayout(0, 16)));
        main.setBorder(Theme.padding(18, 24, 20, 24));

        JPanel header = Theme.transparent(new JPanel(new BorderLayout(0, 14)));
        header.add(buildControlBar(), BorderLayout.NORTH);
        kpiStrip.setLayout(new GridLayout(1, 4, 12, 0));
        kpiStrip.add(cpuKpi);
        kpiStrip.add(memoryKpi);
        kpiStrip.add(processKpi);
        kpiStrip.add(performanceKpi);
        header.add(kpiStrip, BorderLayout.CENTER);
        main.add(header, BorderLayout.NORTH);
        main.add(views, BorderLayout.CENTER);
        return main;
    }

    private JPanel buildControlBar() {
        JPanel bar = Theme.transparent(new JPanel(new BorderLayout(12, 0)));
        bar.add(pageTitle, BorderLayout.WEST);

        JPanel controls = Theme.transparent(new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)));
        UiKit.Card cycle = new UiKit.Card(new BorderLayout());
        cycle.setBorder(Theme.padding(6, 12, 6, 12));
        cycle.add(cycleLabel);
        controls.add(cycle);
        controls.add(startPauseButton);
        resetButton.setToolTipText("Detiene la simulación y la vuelve al ciclo 0 con la carga inicial");
        controls.add(resetButton);
        controls.add(Box.createHorizontalStrut(4));
        cycleDurationSpinner.setPreferredSize(new Dimension(92, 32));
        cycleDurationSpinner.setToolTipText("Ciclo (ms): duración de cada ciclo de reloj en milisegundos");
        showMillisecondsUnit(cycleDurationSpinner);
        controls.add(cycleDurationSpinner);        // rounded by RoundedInputs, like every spinner
        controls.add(Box.createHorizontalStrut(4));
        planningButton.setToolTipText("Política de cada computador y quantum de Round Robin");
        configButton.setToolTipText("Computadores, RAM, carga inicial y archivo CSV");
        controls.add(planningButton);
        controls.add(configButton);
        controls.add(Box.createHorizontalStrut(4));
        loadJsonButton.setToolTipText("Sistema de archivos y procesos: los dos son obligatorios antes de iniciar");
        controls.add(loadJsonButton);
        createProcessButton.setToolTipText("Crear un proceso nuevo");
        controls.add(createProcessButton);
        createIoProcessButton.setToolTipText("Crear un proceso que crea, lee, renombra o elimina un archivo");
        controls.add(createIoProcessButton);
        bar.add(controls, BorderLayout.EAST);
        return bar;
    }

    private JScrollPane buildComputersView() {
        // Uno al lado del otro, como el boceto del enunciado
        JPanel grid = new WidthTrackingPanel(new GridLayout(1, 0, 14, 14));
        grid.setOpaque(false);
        for (ComputerPanel panel : computerPanels) {
            grid.add(panel);
        }
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JComponent buildMetricsView() {
        UiKit.Card tableCard = new UiKit.Card(new BorderLayout(0, 10));
        tableCard.setBorder(Theme.padding(16, 18, 14, 18));
        tableCard.add(cardHeading("Métricas de rendimiento",
                "Tiempos en ciclos. Los promedios, la equidad y el cumplimiento de deadlines solo cuentan"
                + " procesos TERMINADOS; la utilización de CPU es la acumulada hasta ahora."), BorderLayout.NORTH);
        JScrollPane table = Theme.scroll(newTable(metricsModel, MetricsTableModel.COLUMN_TIPS,
                MetricsTableModel.COLUMN_WIDTHS));
        table.setPreferredSize(new Dimension(600, 150));
        tableCard.add(UiKit.rounded(table), BorderLayout.CENTER);

        UiKit.Card chartCard = new UiKit.Card(new BorderLayout(0, 10));
        chartCard.setBorder(Theme.padding(16, 18, 14, 18));
        chartCard.add(cardHeading("Utilización de CPU", "Porcentaje de ciclos ocupados de cada computador"),
                BorderLayout.NORTH);
        chartCard.add(cpuChart, BorderLayout.CENTER);
        chartCard.setPreferredSize(new Dimension(600, 300));

        // Project 2 disk metrics on top, then the Project 1 metrics; the whole view scrolls
        JPanel view = new WidthTrackingPanel(new GridLayout(0, 1, 0, 14));
        view.setOpaque(false);
        JPanel stack = Theme.transparent(new JPanel(new BorderLayout(0, 14)));
        stack.add(diskMetricsPanel, BorderLayout.NORTH);
        JPanel cpuPart = Theme.transparent(new JPanel(new BorderLayout(0, 14)));
        cpuPart.add(tableCard, BorderLayout.NORTH);
        cpuPart.add(chartCard, BorderLayout.CENTER);
        stack.add(cpuPart, BorderLayout.CENTER);
        view.add(stack);
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildLogView() {
        logArea.setEditable(false);
        logArea.setLineWrap(true);                      // las líneas largas no se cortan a la derecha
        logArea.setWrapStyleWord(true);
        logArea.setFont(Theme.MONO_FONT);
        logArea.setForeground(Theme.TEXT);
        logArea.setBackground(Theme.SURFACE);
        logArea.setBorder(Theme.padding(8, 10, 8, 10));
        ((DefaultCaret) logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // autoscroll

        UiKit.Card card = new UiKit.Card(new BorderLayout(0, 12));
        card.setBorder(Theme.padding(16, 18, 16, 18));
        card.add(cardHeading("Log de eventos", "Decisiones del SO en orden: lo más reciente queda abajo"),
                BorderLayout.NORTH);
        JScrollPane scroll = Theme.scroll(logArea);
        card.add(UiKit.rounded(scroll), BorderLayout.CENTER);
        return wrapView(card);
    }

    private static JPanel cardHeading(String title, String subtitle) {
        JPanel heading = Theme.transparent(new JPanel(new BorderLayout(0, 2)));
        heading.add(Theme.label(title, Theme.SECTION, Theme.TEXT), BorderLayout.NORTH);
        JTextArea sub = new JTextArea(subtitle);          // JTextArea: hace salto de línea si no cabe
        sub.setEditable(false);
        sub.setFocusable(false);
        sub.setOpaque(false);
        sub.setLineWrap(true);
        sub.setWrapStyleWord(true);
        sub.setFont(Theme.SMALL);
        sub.setForeground(Theme.MUTED);
        sub.setBorder(BorderFactory.createEmptyBorder());
        heading.add(sub, BorderLayout.CENTER);
        return heading;
    }

    private static JPanel wrapView(JComponent content) {
        JPanel view = Theme.transparent(new JPanel(new BorderLayout()));
        view.add(content, BorderLayout.CENTER);
        return view;
    }

    /**
     * Tabla de solo lectura con el texto completo de la celda y del encabezado al pasar el mouse
     * (los encabezados son cortos para que quepan).
     */
    private static JTable newTable(TableModel model, String[] columnTips, int[] columnWidths) {
        JTable table = new JTable(model) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());
                if (row < 0 || col < 0) {
                    return null;
                }
                Object value = getValueAt(row, col);
                return value == null ? null : value.toString();
            }
        };
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        table.setTableHeader(new JTableHeader(table.getColumnModel()) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent e) {
                int col = columnAtPoint(e.getPoint());
                return col < 0 || col >= columnTips.length ? null : columnTips[col];
            }
        });
        Theme.styleTable(table);
        for (int i = 0; i < columnWidths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
        return table;
    }

    // ---------- Navegación ----------

    /**
     * Adds a view to the side rail and to the center of the window (e.g. "Disco" in Project 2).
     *
     * @param name       full name: page title and rail tooltip
     * @param shortLabel one short word shown under the icon
     * @param icon       icon of the rail item
     * @param view       panel of the view
     */
    public void addView(String name, String shortLabel, NavIcon icon, JComponent view) {
        UiKit.RailItem item = new UiKit.RailItem(shortLabel, icon);
        item.setToolTipText(name);
        item.addActionListener(e -> showView(name));
        navList.add(item);
        navList.add(Box.createVerticalStrut(10));
        views.add(view, name);

        UiKit.RailItem[] items = new UiKit.RailItem[navItems.length + 1];
        String[] names = new String[navNames.length + 1];
        System.arraycopy(navItems, 0, items, 0, navItems.length);
        System.arraycopy(navNames, 0, names, 0, navNames.length);
        items[navItems.length] = item;
        names[navNames.length] = name;
        navItems = items;
        navNames = names;
    }

    /** Muestra una vista por su nombre y la marca en el menú. */
    public void showView(String name) {
        viewLayout.show(views, name);
        // With one computer (Project 2) the page is "Computador", singular
        pageTitle.setText(VIEW_COMPUTERS.equals(name) && computerPanels.length == 1 ? "Computador" : name);
        for (int i = 0; i < navItems.length; i++) {
            navItems[i].setActive(navNames[i].equals(name));
        }
    }

    private void setBadge(String view, String badge) {
        for (int i = 0; i < navItems.length; i++) {
            if (navNames[i].equals(view)) {
                navItems[i].setBadge(badge);
            }
        }
    }

    // ---------- Métodos para actualizar la vista (hilo de Swing) ----------

    public void setCycle(long cycle) {
        String text = "Ciclo " + cycle;
        if (!text.equals(cycleLabel.getText())) {
            cycleLabel.setText(text);
        }
    }

    /** Agrega una entrada del EventLog. Si se pasa de MAX_LOG_LINES, borra la más vieja. */
    public void appendLog(LogEntry entry) {
        logArea.append(entry.format() + "\n");
        int extra = logArea.getLineCount() - 1 - MAX_LOG_LINES;     // la última línea está vacía
        if (extra > 0) {
            try {
                logArea.replaceRange("", 0, logArea.getLineEndOffset(extra - 1));
            } catch (BadLocationException e) {
                logArea.setText("");                                // no debería pasar
            }
        }
    }

    /**
     * Muestra la tabla de métricas: una fila por computador y la fila "Sistema".
     *
     * @param computers foto de cada computador (índice 0 = Computador 1)
     * @param system    foto del sistema completo
     */
    public void showMetrics(MetricsSnapshot[] computers, MetricsSnapshot system) {
        metricsModel.setMetrics(computers, system);
        performanceKpi.show(String.format("CPU %.0f %%", system.cpuUtilization() * 100),
                system.completedProcesses() == 0 ? "Aún no termina ningún proceso"
                : String.format("Throughput %.3f · equidad %.2f", system.throughput(), system.fairnessIndex()));
    }

    /**
     * Indicadores de la franja superior y contador del menú.
     *
     * @param busyCpus     computadores con un proceso en CPU
     * @param runningNames procesos en CPU, separados por coma ("" si ninguno)
     * @param usedMb       RAM usada sumando todos los computadores
     * @param totalMb      RAM total sumando todos los computadores
     * @param live         procesos vivos (nuevos, listos, en CPU y bloqueados)
     * @param blocked      procesos bloqueados
     * @param terminated   procesos terminados
     */
    public void showOverview(int busyCpus, String runningNames, int usedMb, int totalMb, int live, int blocked,
            int terminated) {
        if (computerPanels.length == 1) {
            cpuKpi.show(busyCpus == 1 ? "Ocupada" : "Ociosa",
                    runningNames.isEmpty() ? "Esperando un proceso en Listos" : runningNames);
        } else {
            cpuKpi.show(busyCpus + " de " + computerPanels.length + " ocupadas",
                    runningNames.isEmpty() ? "Todas ociosas" : runningNames);
        }
        int percent = totalMb == 0 ? 0 : (int) Math.round(usedMb * 100.0 / totalMb);
        memoryKpi.show(usedMb + " / " + totalMb + " MB", percent + " % en uso");
        processKpi.show(live + (live == 1 ? " vivo" : " vivos"),
                blocked + " bloqueados · " + terminated + " terminados");
        setBadge(VIEW_COMPUTERS, live == 0 ? "" : String.valueOf(live));
    }

    /** Disk and suspension metrics (Project 2): indicators and requests served per policy. */
    public void showDiskMetrics(DiskData.DiskMetrics metrics) {
        diskMetricsPanel.showMetrics(metrics);
    }

    /** Adds a point to the cumulative head displacement chart. */
    public void addSeekSample(long cycle, long cumulativeSeek, String policy) {
        diskMetricsPanel.addSeekSample(cycle, cumulativeSeek, policy);
    }

    /** Agrega a la gráfica los puntos nuevos de utilización de CPU (SystemMetrics.getComputers()). */
    public void showCpuHistory(LinkedList<ComputerMetrics> computers) {
        cpuChart.showHistory(computers);
    }

    /** Muestra si la simulación está corriendo (cambia el texto de Iniciar/Pausar). */
    public void setRunning(boolean running) {
        startPauseButton.setText(running ? "Pausar" : "Iniciar");
    }

    /** Deja la vista como al abrir el programa: ciclo 0, y log, métricas, gráfica y paneles vacíos. */
    public void resetView() {
        setCycle(0);
        setRunning(false);
        logArea.setText("");
        metricsModel.clear();
        cpuChart.clear();
        diskPanel.clear();
        diskMetricsPanel.clear();
        fileSystemPanel.clearMessage();
        for (ComputerPanel panel : computerPanels) {
            panel.clear();
        }
        showOverview(0, "", 0, 0, 0, 0, 0);
        performanceKpi.show("—", "Aún no termina ningún proceso");
    }

    // ---------- User mode and notices ----------

    /** Who is told when the user picks a mode at the bottom of the rail (the simulation decides). */
    public void setSessionListener(SessionControl.SessionListener listener) {
        sessionControl.setSessionListener(listener);
    }

    /** Users offered in the mode menu (e.g. the owners of the file system JSON). */
    public void setUsers(String[] users) {
        sessionControl.setUsers(users);
    }

    /** Shows the mode the simulation is really using, in the rail and in the Files view. */
    public void showSession(DiskData.Session session) {
        if (session == null) {
            throw new IllegalArgumentException("La sesión no puede ser null");
        }
        sessionControl.showSession(session);
        fileSystemPanel.showSession(session);
    }

    /** Red banner, visible from any view: the operation was rejected and why (nothing stops). */
    public void showRejected(String reason) {
        noticeBar.showError(reason);
        placeNotice();
    }

    /** Neutral banner, visible from any view (e.g. "Ahora estás como user2"). */
    public void showNotice(String message) {
        noticeBar.showInfo(message);
        placeNotice();
    }

    /**
     * Puts the floating notice over the right end of the KPI strip. The KPI cards only show data,
     * so the notice never hides a control of any view (e.g. the disk policy selector).
     */
    private void placeNotice() {
        if (!kpiStrip.isShowing()) {
            return;
        }
        java.awt.Dimension size = noticeBar.getPreferredSize();
        int y = Math.max(0, (kpiStrip.getHeight() - size.height) / 2);
        java.awt.Point corner = javax.swing.SwingUtilities.convertPoint(kpiStrip, kpiStrip.getWidth() - size.width,
                y, getLayeredPane());
        noticeBar.setBounds(Math.max(12, corner.x), corner.y, size.width, size.height);
        noticeBar.revalidate();
    }

    /** "Disco" view: block map, head, disk policy and I/O queue. */
    public DiskPanel getDiskPanel() {
        return diskPanel;
    }

    /** "Archivos" view: JTree, node details, I/O process buttons and allocation table. */
    public FileSystemPanel getFileSystemPanel() {
        return fileSystemPanel;
    }

    public ComputerPanel getComputerPanel(int computerId) {
        return computerPanels[computerId - 1];
    }

    public int getComputerCount() {
        return computerPanels.length;
    }

    /** Habilita o deshabilita Iniciar/Pausar (ej. cuando ya no queda trabajo por simular). */
    public void setStartPauseEnabled(boolean enabled) {
        startPauseButton.setEnabled(enabled);
    }

    /**
     * Shows the unit inside the spinner ("500 ms") without taking room in the top bar. While the
     * user types, the field shows only the number, so "300" is accepted as before.
     */
    private static void showMillisecondsUnit(JSpinner spinner) {
        if (!(spinner.getEditor() instanceof JSpinner.DefaultEditor editor)
                || !(editor.getTextField().getFormatter() instanceof NumberFormatter current)) {
            return;
        }
        NumberFormatter display = new NumberFormatter(new DecimalFormat("0 ms"));
        NumberFormatter edit = new NumberFormatter(new DecimalFormat("0"));
        for (NumberFormatter formatter : new NumberFormatter[] {display, edit}) {
            formatter.setValueClass(current.getValueClass());
            formatter.setMinimum(current.getMinimum());
            formatter.setMaximum(current.getMaximum());
        }
        editor.getTextField().setFormatterFactory(new DefaultFormatterFactory(display, display, edit));
    }

    public JSpinner getCycleDurationSpinner() {
        return cycleDurationSpinner;
    }

    // ---------- Acciones de los botones ----------
    // Cada set*Action REEMPLAZA cualquier acción anterior del botón (el aviso de "en construcción"
    // u otra que se haya puesto antes): el botón queda siempre con UNA sola acción.

    /** Qué hacer al pulsar "Iniciar"/"Pausar". */
    public void setStartPauseAction(ActionListener action) {
        replaceAction(startPauseButton, action);
    }

    /** Qué hacer al pulsar "Reiniciar". */
    public void setResetAction(ActionListener action) {
        replaceAction(resetButton, action);
    }

    /** Qué hacer al pulsar "+ Proceso". */
    public void setCreateProcessAction(ActionListener action) {
        replaceAction(createProcessButton, action);
    }

    /** Qué hacer al pulsar "+ Proceso de E/S" (normalmente abrir el IoProcessDialog). */
    public void setCreateIoProcessAction(ActionListener action) {
        replaceAction(createIoProcessButton, action);
    }

    /** Qué hacer al pulsar "Cargar JSON" (normalmente abrir el LoadDialog). */
    public void setLoadJsonAction(ActionListener action) {
        replaceAction(loadJsonButton, action);
    }

    /**
     * Shows which JSON files are loaded next to the "Cargar JSON" button.
     *
     * @param fileSystemName name of the file system JSON, or null if missing
     * @param processesName  name of the processes and config JSON, or null if missing
     */
    public void showLoadedJson(String fileSystemName, String processesName) {
        boolean both = fileSystemName != null && processesName != null;
        loadJsonButton.setText(both ? "JSON \u2713" : fileSystemName == null && processesName == null
                ? "Cargar JSON" : "Falta un JSON");
        loadJsonButton.setForeground(both ? Theme.USER_FG : Theme.TEXT);
        loadJsonButton.setToolTipText("Sistema de archivos: " + (fileSystemName == null ? "falta" : fileSystemName)
                + " · Procesos: " + (processesName == null ? "falta" : processesName));
    }

    /** Qué hacer al pulsar "Planificación". */
    public void setPlanningAction(ActionListener action) {
        replaceAction(planningButton, action);
    }

    /** Qué hacer al pulsar "Configuración". */
    public void setConfigAction(ActionListener action) {
        replaceAction(configButton, action);
    }

    /**
     * Quita TODAS las acciones del botón (no solo el aviso) y deja únicamente la nueva:
     * así nunca se ejecutan dos acciones a la vez por un mismo clic.
     */
    private static void replaceAction(JButton button, ActionListener action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción del botón no puede ser null");
        }
        for (ActionListener old : button.getActionListeners()) {
            button.removeActionListener(old);
        }
        button.addActionListener(action);
    }

    /** Logo: cuadrado azul con una montaña (el Ávila) dibujada. */
    private static final class Logo extends JComponent {

        private static final long serialVersionUID = 1L;

        Logo() {
            setPreferredSize(new Dimension(36, 36));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.ACCENT);
            g2.fillRoundRect(0, 0, 36, 36, 10, 10);
            g2.translate(3, 3);
            g2.setColor(Color.WHITE);
            g2.setStroke(new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            int[] xs = {6, 12, 16, 19, 24};
            int[] ys = {22, 10, 17, 13, 22};
            g2.drawPolyline(xs, ys, xs.length);
            g2.drawLine(6, 22, 24, 22);
            g2.dispose();
        }
    }

    /**
     * Panel que siempre usa el ancho visible: los computadores se reparten la ventana
     * en vez de salirse por la derecha (solo hay scroll vertical si no caben de alto).
     */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {

        private static final long serialVersionUID = 1L;

        WidthTrackingPanel(GridLayout layout) {
            super(layout);
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
            return 160;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        /** Si sobra alto, las tablas lo usan; si falta, aparece el scroll vertical. */
        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport
                    && viewport.getHeight() > getPreferredSize().height;
        }
    }
}
