package com.unimet.avilaos.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
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

    private final JLabel cycleLabel = new JLabel("Ciclo: 0");
    private final JButton startPauseButton = new JButton("Iniciar");
    private final JSpinner cycleDurationSpinner = new JSpinner(new SpinnerNumberModel(500, 10, 10_000, 50));
    private final JSpinner quantumSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 100, 1));
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
        split.setResizeWeight(0.7);
        add(split, BorderLayout.CENTER);

        setPreferredSize(new Dimension(1400, 900));
        pack();
        setLocationRelativeTo(null);

        // Botones aún sin funcionalidad (llegan en las siguientes ramas)
        createProcessButton.addActionListener(e -> notImplemented());
        createBufferButton.addActionListener(e -> notImplemented());
        configButton.addActionListener(e -> notImplemented());
    }

    // ---------- Construcción ----------

    private JPanel buildControlBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        cycleLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        bar.add(cycleLabel);
        bar.add(startPauseButton);
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
        JPanel grid = new JPanel(new GridLayout(0, 1, 8, 8));   // un computador debajo del otro
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
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ((DefaultCaret) logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // autoscroll
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log de eventos"));

        JSplitPane bottom = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, bufferArea, logScroll);
        bottom.setResizeWeight(0.5);
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

    public ComputerPanel getComputerPanel(int computerId) {
        return computerPanels[computerId - 1];
    }

    public int getComputerCount() {
        return computerPanels.length;
    }

    public JButton getStartPauseButton() {
        return startPauseButton;
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
}
