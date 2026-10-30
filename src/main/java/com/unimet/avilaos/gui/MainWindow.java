package com.unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.DefaultCaret;

/**
 * Ventana principal del simulador ÁvilaOS.
 *
 * Arriba: controles globales (ciclo de reloj, iniciar/pausar, duración del ciclo,
 * quantum y botones para crear procesos/buffers y configurar).
 * Centro: un ComputerPanel por computador.
 * Abajo: buffers y log de eventos.
 *
 * La ventana solo muestra y reenvía las acciones del usuario: no contiene lógica del SO.
 *
 * @author NicollPinzon
 */
public class MainWindow extends JFrame {

    /** Quantum inicial mientras no se cargue la configuración (igual a SimulationConfig.defaults()). */
    public static final int DEFAULT_QUANTUM = 3;

    private final JLabel cycleLabel = new JLabel("Ciclo: 0");
    private final JButton startPauseButton = new JButton("Iniciar");
    private final JButton resetButton = new JButton("Reiniciar");
    private final JSpinner cycleDurationSpinner = new JSpinner(new SpinnerNumberModel(500, 10, 10_000, 50));
    private final JSpinner quantumSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_QUANTUM, 1, 100, 1));
    private final JButton createProcessButton = new JButton("Crear proceso");
    private final JButton createBufferButton = new JButton("Crear buffer");
    private final JButton configButton = new JButton("Configuración");

    private final JTextArea logArea = new JTextArea();
    private final JPanel bufferArea = new JPanel(new BorderLayout());

    private final ComputerPanel[] computerPanels;

    /**
     * @param computerCount número de computadores del clúster (mínimo 2)
     * @param policies      políticas disponibles para los selectores
     */
    public MainWindow(int computerCount, String[] policies) {
        super("ÁvilaOS - Simulador de sistema operativo distribuido");
        if (computerCount < 2) {
            throw new IllegalArgumentException("El clúster necesita al menos 2 computadores");
        }

        computerPanels = new ComputerPanel[computerCount];
        for (int i = 0; i < computerCount; i++) {
            computerPanels[i] = new ComputerPanel(i + 1, policies);
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

        // Crear proceso y Crear buffer los conecta quien arma la simulación
        // (abre ProcessFormDialog / BufferFormDialog). Configuración llega en otra rama.
        configButton.addActionListener(e -> notImplemented());
    }

    // ---------- Construcción ----------

    private JPanel buildControlBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        cycleLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        bar.add(cycleLabel);
        resetButton.setToolTipText("Detiene la simulación y la vuelve al ciclo 0 con la carga inicial");
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
        bufferArea.add(new JLabel("Aquí irá el estado de los buffers", JLabel.CENTER));

        logArea.setEditable(false);
        logArea.setLineWrap(true);                      // las líneas largas no se cortan a la derecha
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ((DefaultCaret) logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // autoscroll
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log de eventos"));

        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bufferArea, logScroll);
        bottom.setResizeWeight(0.4);
        return bottom;
    }

    private void notImplemented() {
        JOptionPane.showMessageDialog(this, "Disponible en una próxima versión.",
                "En construcción", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- Métodos para actualizar la vista (hilo de Swing) ----------

    public void setCycle(long cycle) {
        cycleLabel.setText("Ciclo: " + cycle);
    }

    /** Agrega una línea al log con el número de ciclo. */
    public void appendLog(long cycle, String message) {
        logArea.append("[Ciclo " + cycle + "] " + message + "\n");
    }

    /** Muestra si la simulación está corriendo (cambia el texto de Iniciar/Pausar). */
    public void setRunning(boolean running) {
        startPauseButton.setText(running ? "Pausar" : "Iniciar");
    }

    /** Deja la vista como al abrir el programa: ciclo 0, log vacío y paneles vacíos. */
    public void resetView() {
        setCycle(0);
        setRunning(false);
        logArea.setText("");
        for (ComputerPanel panel : computerPanels) {
            panel.clear();
        }
    }

    public void setQuantum(int quantum) {
        quantumSpinner.setValue(quantum);
    }

    public ComputerPanel getComputerPanel(int computerId) {
        return computerPanels[computerId - 1];
    }

    public int getComputerCount() {
        return computerPanels.length;
    }

    public JButton getStartPauseButton() {
        return startPauseButton;
    }

    public JButton getResetButton() {
        return resetButton;
    }

    public JSpinner getCycleDurationSpinner() {
        return cycleDurationSpinner;
    }

    public JSpinner getQuantumSpinner() {
        return quantumSpinner;
    }

    public JButton getCreateProcessButton() {
        return createProcessButton;
    }

    public JButton getCreateBufferButton() {
        return createBufferButton;
    }

    public JButton getConfigButton() {
        return configButton;
    }

    /**
     * Panel que siempre usa el ancho visible: los computadores se reparten la ventana
     * en vez de salirse por la derecha (solo hay scroll vertical si no caben de alto).
     */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {

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
