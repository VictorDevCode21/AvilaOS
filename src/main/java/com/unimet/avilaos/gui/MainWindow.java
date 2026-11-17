package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.events.EventLog;
import com.unimet.avilaos.events.LogEntry;
import com.unimet.avilaos.metrics.BufferStats;
import com.unimet.avilaos.metrics.ComputerMetrics;
import com.unimet.avilaos.metrics.MetricsSnapshot;
import com.unimet.avilaos.os.sync.BoundedBuffer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
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

/**
 * Ventana principal del simulador ÁvilaOS.
 *
 * Izquierda: menú con las vistas (Computadores, Buffers, Métricas, Log de eventos) y las
 * acciones Planificación y Configuración.
 * Arriba: el nombre de la vista, el ciclo de reloj y los controles (iniciar/pausar,
 * reiniciar, duración del ciclo, crear proceso y buffer), y una franja de indicadores
 * que se ve en todas las vistas (CPUs, memoria, procesos, rendimiento).
 * Centro: UNA vista a la vez, para no saturar la pantalla.
 *
 * Agregar una vista (ej. el disco en el Proyecto 2) es llamar addView con su panel.
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

    // Nombres de las vistas (también son el título de la página)
    static final String VIEW_COMPUTERS = "Computadores";
    static final String VIEW_BUFFERS = "Buffers";
    static final String VIEW_METRICS = "Métricas";
    static final String VIEW_LOG = "Log de eventos";

    // ---------- Controles ----------
    private final JLabel cycleLabel = Theme.label("Ciclo 0", Theme.MONO_BOLD, Theme.TEXT);
    private final UiKit.Button startPauseButton = new UiKit.Button("Iniciar", UiKit.Kind.PRIMARY);
    private final UiKit.Button resetButton = new UiKit.Button("Reiniciar", UiKit.Kind.SECONDARY);
    // Rangos de SimulationConfig: la GUI y el archivo CSV aceptan exactamente lo mismo
    private final JSpinner cycleDurationSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_CYCLE_MS,
            (int) SimulationConfig.MIN_CYCLE_MS, (int) SimulationConfig.MAX_CYCLE_MS, 50));
    private final UiKit.Button createProcessButton = new UiKit.Button("+ Proceso", UiKit.Kind.SECONDARY);
    private final UiKit.Button createBufferButton = new UiKit.Button("+ Buffer", UiKit.Kind.SECONDARY);
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
    private UiKit.NavItem[] navItems = new UiKit.NavItem[0];
    private String[] navNames = new String[0];

    // ---------- Indicadores (franja superior) ----------
    private final UiKit.Kpi cpuKpi = new UiKit.Kpi("CPUs");
    private final UiKit.Kpi memoryKpi = new UiKit.Kpi("Memoria");
    private final UiKit.Kpi processKpi = new UiKit.Kpi("Procesos");
    private final UiKit.Kpi performanceKpi = new UiKit.Kpi("Rendimiento");

    // ---------- Contenido de las vistas ----------
    private final JTextArea logArea = new JTextArea();
    private final BufferTableModel bufferModel = new BufferTableModel();
    private final MetricsTableModel metricsModel = new MetricsTableModel();
    private final CpuUsageChart cpuChart = new CpuUsageChart();

    private final ComputerPanel[] computerPanels;

    /**
     * @param computerCount número de computadores del clúster (mínimo 2)
     */
    public MainWindow(int computerCount) {
        super("ÁvilaOS - Simulador de sistema operativo distribuido");
        if (computerCount < 2) {
            throw new IllegalArgumentException("El clúster necesita al menos 2 computadores");
        }

        computerPanels = new ComputerPanel[computerCount];
        for (int i = 0; i < computerCount; i++) {
            computerPanels[i] = new ComputerPanel(i + 1);
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BACKGROUND);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildMain(), BorderLayout.CENTER);
        setContentPane(root);

        addView(VIEW_COMPUTERS, Theme.STATE_READY, buildComputersView());
        addView(VIEW_BUFFERS, Theme.STATE_BLOCKED, buildBuffersView());
        addView(VIEW_METRICS, Theme.STATE_RUNNING, buildMetricsView());
        addView(VIEW_LOG, Theme.STATE_NEW, buildLogView());
        showView(VIEW_COMPUTERS);

        setPreferredSize(new Dimension(1440, 900));
        setMinimumSize(new Dimension(1000, 640));
        pack();
        setLocationRelativeTo(null);

        // Hasta que alguien los conecte, los botones avisan "en construcción" en vez de quedarse mudos.
        for (JButton button : new JButton[] {startPauseButton, resetButton, createProcessButton,
            createBufferButton, planningButton, configButton}) {
            button.addActionListener(notAvailable);
        }
    }

    // ---------- Construcción ----------

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout()) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Theme.BORDER);
                g.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            }
        };
        sidebar.setBackground(Theme.SURFACE);
        sidebar.setPreferredSize(new Dimension(220, 10));
        sidebar.setBorder(Theme.padding(18, 12, 18, 12));

        JPanel brand = Theme.transparent(new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)));
        brand.add(new Logo());
        brand.add(Theme.label("ÁvilaOS", new Font(Theme.BODY.getFamily(), Font.BOLD, 17), Theme.TEXT));
        brand.setBorder(Theme.padding(0, 0, 18, 0));
        sidebar.add(brand, BorderLayout.NORTH);

        navList.setLayout(new BoxLayout(navList, BoxLayout.Y_AXIS));
        sidebar.add(navList, BorderLayout.CENTER);

        JPanel actions = Theme.transparent(new JPanel());
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.add(UiKit.divider());
        actions.add(Box.createVerticalStrut(6));
        JLabel heading = Theme.label("AJUSTES", Theme.SMALL_BOLD, Theme.MUTED);
        heading.setBorder(Theme.padding(0, 12, 6, 0));
        actions.add(heading);
        for (UiKit.Button button : new UiKit.Button[] {planningButton, configButton}) {
            button.setHorizontalAlignment(JButton.LEFT);
            button.setAlignmentX(LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            actions.add(button);
        }
        planningButton.setToolTipText("Política de cada computador y quantum de Round Robin");
        configButton.setToolTipText("Computadores, RAM, carga inicial y archivo CSV");
        heading.setAlignmentX(LEFT_ALIGNMENT);
        sidebar.add(actions, BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel buildMain() {
        JPanel main = Theme.transparent(new JPanel(new BorderLayout(0, 16)));
        main.setBorder(Theme.padding(18, 24, 20, 24));

        JPanel header = Theme.transparent(new JPanel(new BorderLayout(0, 14)));
        header.add(buildControlBar(), BorderLayout.NORTH);
        JPanel kpis = Theme.transparent(new JPanel(new GridLayout(1, 4, 12, 0)));
        kpis.add(cpuKpi);
        kpis.add(memoryKpi);
        kpis.add(processKpi);
        kpis.add(performanceKpi);
        header.add(kpis, BorderLayout.CENTER);
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
        controls.add(Theme.label("Ciclo (ms)", Theme.SMALL, Theme.MUTED));
        cycleDurationSpinner.setPreferredSize(new Dimension(80, 32));
        cycleDurationSpinner.setToolTipText("Duración de cada ciclo de reloj en milisegundos");
        controls.add(cycleDurationSpinner);
        controls.add(Box.createHorizontalStrut(4));
        createProcessButton.setToolTipText("Crear un proceso nuevo");
        createBufferButton.setToolTipText("Crear un buffer productor-consumidor");
        controls.add(createProcessButton);
        controls.add(createBufferButton);
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

    private JPanel buildBuffersView() {
        UiKit.Card card = new UiKit.Card(new BorderLayout(0, 12));
        card.setBorder(Theme.padding(16, 18, 16, 18));
        card.add(cardHeading("Buffers productor-consumidor",
                "Ocupación, valor de cada semáforo y los procesos que esperan en él"), BorderLayout.NORTH);
        JScrollPane table = Theme.scroll(newTable(bufferModel, BufferTableModel.COLUMN_TIPS,
                BufferTableModel.COLUMN_WIDTHS));
        table.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        card.add(table, BorderLayout.CENTER);
        return wrapView(card);
    }

    private JPanel buildMetricsView() {
        UiKit.Card tableCard = new UiKit.Card(new BorderLayout(0, 10));
        tableCard.setBorder(Theme.padding(16, 18, 14, 18));
        tableCard.add(cardHeading("Métricas de rendimiento",
                "Tiempos en ciclos. Los promedios, la equidad y el cumplimiento de deadlines solo cuentan"
                + " procesos TERMINADOS; la utilización de CPU es la acumulada hasta ahora."), BorderLayout.NORTH);
        JScrollPane table = Theme.scroll(newTable(metricsModel, MetricsTableModel.COLUMN_TIPS,
                MetricsTableModel.COLUMN_WIDTHS));
        table.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        table.setPreferredSize(new Dimension(600, 150));
        tableCard.add(table, BorderLayout.CENTER);

        UiKit.Card chartCard = new UiKit.Card(new BorderLayout(0, 10));
        chartCard.setBorder(Theme.padding(16, 18, 14, 18));
        chartCard.add(cardHeading("Utilización de CPU", "Porcentaje de ciclos ocupados de cada computador"),
                BorderLayout.NORTH);
        chartCard.add(cpuChart, BorderLayout.CENTER);

        JPanel view = Theme.transparent(new JPanel(new BorderLayout(0, 14)));
        view.add(tableCard, BorderLayout.NORTH);
        view.add(chartCard, BorderLayout.CENTER);
        return view;
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
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        card.add(scroll, BorderLayout.CENTER);
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
     * Agrega una vista al menú y al centro de la ventana (ej. "Disco" en el Proyecto 2).
     *
     * @param name  nombre en el menú y título de la página
     * @param color color del punto en el menú
     * @param view  panel de la vista
     */
    public void addView(String name, Color color, JComponent view) {
        UiKit.NavItem item = new UiKit.NavItem(name, color);
        item.addActionListener(e -> showView(name));
        navList.add(item);
        navList.add(Box.createVerticalStrut(2));
        views.add(view, name);

        UiKit.NavItem[] items = new UiKit.NavItem[navItems.length + 1];
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
        pageTitle.setText(name);
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
     * Muestra el estado de los buffers: ocupación, semáforos, quién espera en cada uno
     * y producidos/consumidos.
     *
     * @param buffers copia de la lista de buffers (Cluster.getBuffers())
     * @param stats   contadores de cada buffer (SystemMetrics.getBufferStats())
     */
    public void showBuffers(LinkedList<BoundedBuffer> buffers, LinkedList<BufferStats> stats) {
        bufferModel.setBuffers(buffers, stats);
        setBadge(VIEW_BUFFERS, String.valueOf(bufferModel.getRowCount()));
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
        cpuKpi.show(busyCpus + " de " + computerPanels.length + " ocupadas",
                runningNames.isEmpty() ? "Todas ociosas" : runningNames);
        int percent = totalMb == 0 ? 0 : (int) Math.round(usedMb * 100.0 / totalMb);
        memoryKpi.show(usedMb + " / " + totalMb + " MB", percent + " % en uso");
        processKpi.show(live + (live == 1 ? " vivo" : " vivos"),
                blocked + " bloqueados · " + terminated + " terminados");
        setBadge(VIEW_COMPUTERS, live == 0 ? "" : live + " vivos");
    }

    /** Agrega a la gráfica los puntos nuevos de utilización de CPU (SystemMetrics.getComputers()). */
    public void showCpuHistory(LinkedList<ComputerMetrics> computers) {
        cpuChart.showHistory(computers);
    }

    /** Muestra si la simulación está corriendo (cambia el texto de Iniciar/Pausar). */
    public void setRunning(boolean running) {
        startPauseButton.setText(running ? "Pausar" : "Iniciar");
    }

    /** Deja la vista como al abrir el programa: ciclo 0, y log, buffers, métricas, gráfica y paneles vacíos. */
    public void resetView() {
        setCycle(0);
        setRunning(false);
        logArea.setText("");
        bufferModel.clear();
        metricsModel.clear();
        cpuChart.clear();
        for (ComputerPanel panel : computerPanels) {
            panel.clear();
        }
        showOverview(0, "", 0, 0, 0, 0, 0);
        performanceKpi.show("—", "Aún no termina ningún proceso");
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

    /** Qué hacer al pulsar "+ Buffer". */
    public void setCreateBufferAction(ActionListener action) {
        replaceAction(createBufferButton, action);
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
            setPreferredSize(new Dimension(30, 30));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.ACCENT);
            g2.fillRoundRect(0, 0, 30, 30, 8, 8);
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
