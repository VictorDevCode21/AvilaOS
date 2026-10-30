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
 * Cada vez que se llama update() agrega únicamente los puntos nuevos,
 * así no se redibuja todo el historial en cada ciclo.
 *
 * @author NicollPinzon
 */
public class CpuUsageChart extends JPanel {

    /** Máximo de puntos por línea; los más viejos se descartan para no llenar la memoria. */
    private static final int MAX_POINTS = 500;

    private final XYSeriesCollection dataset = new XYSeriesCollection();
    private XYSeries[] series = new XYSeries[0];

    public CpuUsageChart() {
        super(new BorderLayout());

        JFreeChart chart = ChartFactory.createXYLineChart(
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
     * Debe llamarse desde el hilo de Swing (SwingUtilities.invokeLater).
     */
    public void update(LinkedList<ComputerMetrics> computers) {
        ensureSeries(computers);
        for (int i = 0; i < computers.size(); i++) {
            LinkedList<UtilizationSample> history = computers.get(i).getUtilizationHistory();
            XYSeries line = series[i];
            long lastShown = line.getItemCount() == 0 ? -1 : (long) line.getMaxX();  // último ciclo dibujado
            boolean changed = false;
            for (int j = 0; j < history.size(); j++) {
                UtilizationSample sample = history.get(j);
                if (sample.cycle() > lastShown) {
                    line.add(sample.cycle(), sample.utilization() * 100, false);
                    changed = true;
                }
            }
            if (changed) {
                line.fireSeriesChanged();               // un solo redibujo por computador
            }
        }
    }

    /** Borra todas las líneas (ej. al iniciar una simulación nueva). */
    public void clear() {
        dataset.removeAllSeries();
        series = new XYSeries[0];
    }

    // ---------- Internos ----------

    /** Crea una línea por computador la primera vez (o si cambió la cantidad). */
    private void ensureSeries(LinkedList<ComputerMetrics> computers) {
        if (series.length == computers.size()) {
            return;
        }
        clear();
        series = new XYSeries[computers.size()];
        for (int i = 0; i < series.length; i++) {
            XYSeries line = new XYSeries("Computador " + computers.get(i).getComputerId());
            line.setMaximumItemCount(MAX_POINTS);
            series[i] = line;
            dataset.addSeries(line);
        }
    }
}
