package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.events.EventLog;
import com.unimet.avilaos.events.LogEntry;
import com.unimet.avilaos.metrics.BufferStats;
import com.unimet.avilaos.os.sync.BoundedBuffer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
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
 * Arriba: controles globales (ciclo de reloj, iniciar/pausar, reiniciar, duración del ciclo,
 * quantum y botones para crear procesos/buffers y configurar).
 * Centro: un ComputerPanel por computador, uno al lado del otro.
 * Abajo: tabla de buffers (semáforos y quién espera) y log de eventos.
 *
 * La ventana solo muestra y reenvía las acciones del usuario: no contiene lógica del SO.
 *
 * @author NicollPinzon
 */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Valores iniciales mientras no se cargue un archivo: los mismos de SimulationConfig.defaults(). */
    private static final SimulationConfig DEFAULTS = SimulationConfig.defaults();

    /** Quantum inicial mientras no se cargue la configuración. */
    public static final int DEFAULT_QUANTUM = DEFAULTS.getQuantum();

    /** Duración inicial del ciclo (ms) mientras no se cargue la configuración. */
    public static final int DEFAULT_CYCLE_MS = (int) DEFAULTS.getCycleDurationMs();

    /** Máximo de líneas en el log en pantalla (igual que la capacidad de EventLog). */
    public static final int MAX_LOG_LINES = EventLog.DEFAULT_CAPACITY;

    private final JLabel cycleLabel = new JLabel("Ciclo: 0");
    private final JButton startPauseButton = new JButton("Iniciar");
    private final JButton resetButton = new JButton("Reiniciar");
    // Rangos de SimulationConfig: la GUI y el archivo CSV aceptan exactamente lo mismo
    private final JSpinner cycleDurationSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_CYCLE_MS,
            (int) SimulationConfig.MIN_CYCLE_MS, (int) SimulationConfig.MAX_CYCLE_MS, 50));
    private final JSpinner quantumSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_QUANTUM,
            1, SimulationConfig.MAX_QUANTUM, 1));
    private final JButton createProcessButton = new JButton("Crear proceso");
    private final JButton createBufferButton = new JButton("Crear buffer");
    private final JButton configButton = new JButton("Configuración");

    /**
     * Acción por defecto de los botones que todavía nadie conectó: avisa en vez de no hacer nada.
     * Quien arma la simulación la reemplaza con setStartPauseAction / setResetAction /
     * setCreateProcessAction / setCreateBufferAction / setConfigAction.
     */
    private final transient ActionListener notAvailable = e -> JOptionPane.showMessageDialog(this,
            "Disponible en una próxima versión.", "En construcción", JOptionPane.INFORMATION_MESSAGE);

    private final JTextArea logArea = new JTextArea();
    private final JPanel bufferArea = new JPanel(new BorderLayout());
    private final BufferTableModel bufferModel = new BufferTableModel();

    private final ComputerPanel[] computerPanels;

    /**
     * @param computerCount número de computadores del clúster (mínimo 2)
     * @param policyLabels  políticas para los selectores (PolicyType.labels())
     */
    public MainWindow(int computerCount, String[] policyLabels) {
        super("ÁvilaOS - Simulador de sistema operativo distribuido");
        if (computerCount < 2) {
            throw new IllegalArgumentException("El clúster necesita al menos 2 computadores");
        }

        computerPanels = new ComputerPanel[computerCount];
        for (int i = 0; i < computerCount; i++) {
            computerPanels[i] = new ComputerPanel(i + 1, policyLabels);
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        add(buildControlBar(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildComputersArea(), buildBottomArea());
        split.setResizeWeight(0.75);
        add(split, BorderLayout.CENTER);

        setPreferredSize(new Dimension(1440, 900));
        pack();
        setLocationRelativeTo(null);

        // Hasta que alguien los conecte, los 5 botones avisan "en construcción" en vez de quedarse mudos.
        startPauseButton.addActionListener(notAvailable);
        resetButton.addActionListener(notAvailable);
        createProcessButton.addActionListener(notAvailable);
        createBufferButton.addActionListener(notAvailable);
        configButton.addActionListener(notAvailable);
    }

    // ---------- Construcción ----------

    private JPanel buildControlBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        cycleLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        resetButton.setToolTipText("Detiene la simulación y la vuelve al ciclo 0 con la carga inicial");
        bar.add(cycleLabel);
        bar.add(startPauseButton);
        bar.add(resetButton);
        bar.add(new JLabel("Duración ciclo (ms):"));
        bar.add(cycleDurationSpinner);
        bar.add(new JLabel("Quantum RR:"));
        bar.add(quantumSpinner);
        bar.add(createProcessButton);
        bar.add(createBufferButton);
        bar.add(configButton);
        return bar;
    }

    private JScrollPane buildComputersArea() {
        // Uno al lado del otro, como el boceto del enunciado
        JPanel grid = new WidthTrackingPanel(new GridLayout(1, 0, 8, 8));
        for (ComputerPanel panel : computerPanels) {
            grid.add(panel);
        }
        JScrollPane scroll = new JScrollPane(grid);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JSplitPane buildBottomArea() {
        bufferArea.setBorder(BorderFactory.createTitledBorder("Buffers"));
        bufferArea.add(new JScrollPane(newTable(bufferModel, BufferTableModel.COLUMN_TIPS,
                BufferTableModel.COLUMN_WIDTHS)));

        logArea.setEditable(false);
        logArea.setLineWrap(true);                      // las líneas largas no se cortan a la derecha
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ((DefaultCaret) logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // autoscroll
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log de eventos"));

        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bufferArea, logScroll);
        bottom.setResizeWeight(0.5);
        return bottom;
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
        table.getTableHeader().setReorderingAllowed(false);
        for (int i = 0; i < columnWidths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
        return table;
    }

    // ---------- Métodos para actualizar la vista (hilo de Swing) ----------

    public void setCycle(long cycle) {
        cycleLabel.setText("Ciclo: " + cycle);
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
    }

    /** Muestra si la simulación está corriendo (cambia el texto de Iniciar/Pausar). */
    public void setRunning(boolean running) {
        startPauseButton.setText(running ? "Pausar" : "Iniciar");
    }

    public void setQuantum(int quantum) {
        quantumSpinner.setValue(quantum);
    }

    /** Deja la vista como al abrir el programa: ciclo 0, log, buffers y paneles vacíos. */
    public void resetView() {
        setCycle(0);
        setRunning(false);
        logArea.setText("");
        bufferModel.clear();
        for (ComputerPanel panel : computerPanels) {
            panel.clear();
        }
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

    public JSpinner getQuantumSpinner() {
        return quantumSpinner;
    }

    // ---------- Acciones de los botones ----------
    // Cada set*Action REEMPLAZA cualquier acción anterior del botón (el aviso de "en construcción"
    // u otra que se haya puesto antes): el botón queda siempre con UNA sola acción.
    // Llamarlo dos veces no suma acciones; la segunda reemplaza a la primera.

    /** Qué hacer al pulsar "Iniciar"/"Pausar". Reemplaza cualquier acción anterior del botón. */
    public void setStartPauseAction(ActionListener action) {
        replaceAction(startPauseButton, action);
    }

    /** Qué hacer al pulsar "Reiniciar". Reemplaza cualquier acción anterior del botón. */
    public void setResetAction(ActionListener action) {
        replaceAction(resetButton, action);
    }

    /** Qué hacer al pulsar "Crear proceso". Reemplaza cualquier acción anterior del botón. */
    public void setCreateProcessAction(ActionListener action) {
        replaceAction(createProcessButton, action);
    }

    /** Qué hacer al pulsar "Crear buffer". Reemplaza cualquier acción anterior del botón. */
    public void setCreateBufferAction(ActionListener action) {
        replaceAction(createBufferButton, action);
    }

    /** Qué hacer al pulsar "Configuración". Reemplaza cualquier acción anterior del botón. */
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
