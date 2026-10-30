package com.unimet.avilaos.gui;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.metrics.ComputerMetrics;
import com.unimet.avilaos.metrics.UtilizationSample;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Gráfica de la utilización de CPU de cada computador con respecto al tiempo
 * (todas las líneas en el mismo gráfico, como pide el enunciado).
 *
 * Solo dibuja lo que guardan las ComputerMetrics: no calcula nada.
 * Cada vez que se llama showHistory() agrega únicamente los puntos nuevos y la
 * gráfica se redibuja UNA sola vez, sin importar cuántos computadores haya.
 *
 * Si showHistory() recibe otras métricas (nueva simulación, otra configuración),
 * la gráfica se reinicia sola: no depende de que alguien llame clear().
 *
 * @author NicollPinzon
 */
public final class CpuUsageChart extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Máximo de puntos por línea; los más viejos se descartan para no llenar la memoria. */
    private static final int MAX_POINTS = 500;

    private final XYSeriesCollection dataset = new XYSeriesCollection();
    private final JFreeChart chart;
    private XYSeries[] series = new XYSeries[0];
    private transient ComputerMetrics[] sources = new ComputerMetrics[0];   // de quién es cada línea

    public CpuUsageChart() {
        super(new BorderLayout());

        chart = ChartFactory.createXYLineChart(
                "Utilización de CPU por computador",
                "Ciclo de reloj",
                "Utilización (%)",
                dataset,
                PlotOrientation.VERTICAL,
                true,    // leyenda: una entrada por computador
                true,    // tooltips al pasar el mouse
                false);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
        yAxis.setRange(0, 100);                       // la utilización siempre va de 0% a 100%
        NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
        xAxis.setAutoRangeIncludesZero(false);
        xAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits()); // ciclos enteros

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 300));
        setBorder(BorderFactory.createTitledBorder("Métricas"));
        add(chartPanel, BorderLayout.CENTER);
    }

    // ---------- Actualización (hilo de Swing) ----------

    /**
     * Agrega a la gráfica los puntos nuevos de cada computador.
     * Debe llamarse desde el hilo de Swing (ej. un javax.swing.Timer de 100-200 ms).
     *
     * OJO: ComputerMetrics guarda solo las últimas MAX_SAMPLES muestras. Si pasan más
     * ciclos que eso entre dos llamadas, los puntos intermedios ya no existen y la línea
     * los salta. Con un Timer de 100-200 ms nunca pasa.
     *
     * (No se llama update() para no confundirse con JComponent.update(Graphics) de Swing.)
     *
     * @param computers métricas de cada computador, en orden (no puede ser null)
     */
    public void showHistory(LinkedList<ComputerMetrics> computers) {
        if (computers == null) {
            throw new IllegalArgumentException("La lista de métricas no puede ser null");
        }
        chart.setNotify(false);                         // se agregan todos los puntos sin redibujar...
        try {
            ensureSeries(computers);                    // dentro del try: reconstruir también redibuja una vez
            for (int i = 0; i < series.length; i++) {
                LinkedList<UtilizationSample> history = sources[i].getUtilizationHistory();
                XYSeries line = series[i];
                long lastShown = line.getItemCount() == 0 ? -1 : (long) line.getMaxX();  // último ciclo dibujado
                while (!history.isEmpty()) {
                    UtilizationSample sample = history.removeFirst();   // es una copia: se puede vaciar
                    if (sample.cycle() > lastShown) {
                        line.add(sample.cycle(), sample.utilization() * 100, false);
                    }
                }
                line.fireSeriesChanged();
            }
        } finally {
            chart.setNotify(true);                      // ...y se redibuja UNA vez
        }
    }

    /** Borra todas las líneas (ej. al iniciar una simulación nueva). */
    public void clear() {
        dataset.removeAllSeries();
        series = new XYSeries[0];
        sources = new ComputerMetrics[0];
    }

    // ---------- Internos ----------

    /**
     * Crea una línea por computador la primera vez, o vuelve a crearlas si las
     * métricas recibidas no son las mismas de antes (nueva simulación).
     */
    private void ensureSeries(LinkedList<ComputerMetrics> computers) {
        if (sameSources(computers)) {
            return;
        }
        clear();
        series = new XYSeries[computers.size()];
        sources = new ComputerMetrics[computers.size()];
        LinkedList<ComputerMetrics> walk = computers.copy();
        for (int i = 0; i < series.length; i++) {
            sources[i] = walk.removeFirst();
            XYSeries line = new XYSeries("Computador " + sources[i].getComputerId());
            line.setMaximumItemCount(MAX_POINTS);
            series[i] = line;
            dataset.addSeries(line);
        }
    }

    /** ¿Son exactamente las mismas métricas, en el mismo orden, que ya se dibujan? */
    private boolean sameSources(LinkedList<ComputerMetrics> computers) {
        if (computers.size() != sources.length) {
            return false;
        }
        LinkedList<ComputerMetrics> walk = computers.copy();
        for (int i = 0; i < sources.length; i++) {
            if (walk.removeFirst() != sources[i]) {
                return false;
            }
        }
        return true;
    }
}
