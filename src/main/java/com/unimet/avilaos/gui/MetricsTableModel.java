package com.unimet.avilaos.gui;

import com.unimet.avilaos.metrics.MetricsSnapshot;
import javax.swing.table.AbstractTableModel;

/**
 * Modelo de tabla con las métricas de rendimiento: una fila por computador y una
 * fila final "Sistema" con el total del clúster.
 *
 * Cada fila es una MetricsSnapshot (foto inmutable tomada en un solo instante), así
 * los valores de una fila son consistentes entre sí. Solo formatea: no calcula nada.
 *
 * @author victor1102
 */
public final class MetricsTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    /** Encabezados cortos para que quepan; el nombre completo sale en COLUMN_TIPS. */
    static final String[] COLUMNS = {
        "", "Term.", "Throughput", "CPU %", "Resp. prom.", "Ret. prom.",
        "Deadl. %", "Equidad", "Bloq. sem."
    };

    /** Tooltip de cada encabezado (mismo orden que COLUMNS). */
    static final String[] COLUMN_TIPS = {
        "Computador, o el total del sistema",
        "Procesos terminados (a tiempo o por deadline)",
        "Procesos terminados a tiempo por ciclo",
        "Utilización de CPU: ciclos ocupada / ciclos totales",
        "Tiempo de respuesta promedio: primer ciclo en CPU - llegada (ciclos, solo terminados)",
        "Tiempo de retorno promedio: fin - llegada (ciclos, solo terminados)",
        "Cumplimiento de deadlines: terminados a tiempo / terminados",
        "Equidad, índice de Jain sobre la fracción del tiempo que cada proceso quería la CPU y la tuvo (1 = equidad perfecta)",
        "Ciclos bloqueado en semáforos (promedio de productores y consumidores terminados)"
    };

    static final int[] COLUMN_WIDTHS = {95, 45, 75, 55, 75, 70, 60, 60, 70};

    private transient String[] labels = new String[0];
    private transient MetricsSnapshot[] rows = new MetricsSnapshot[0];

    /**
     * Reemplaza las filas. Debe llamarse desde el hilo de Swing.
     *
     * @param computers foto de cada computador, en orden (índice 0 = Computador 1)
     * @param system    foto del sistema completo (última fila)
     */
    public void setMetrics(MetricsSnapshot[] computers, MetricsSnapshot system) {
        String[] newLabels = new String[computers.length + 1];
        MetricsSnapshot[] newRows = new MetricsSnapshot[computers.length + 1];
        for (int i = 0; i < computers.length; i++) {
            newLabels[i] = "Computador " + (i + 1);
            newRows[i] = computers[i];
        }
        newLabels[computers.length] = "Sistema";
        newRows[computers.length] = system;
        labels = newLabels;
        rows = newRows;
        fireTableDataChanged();
    }

    /** Vacía la tabla (ej. al reiniciar la simulación). */
    public void clear() {
        labels = new String[0];
        rows = new MetricsSnapshot[0];
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return rows.length;
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        MetricsSnapshot m = rows[rowIndex];
        if (columnIndex >= 4 && m.completedProcesses() == 0) {
            return "—";                                   // promedios: todavía no terminó nadie
        }
        return switch (columnIndex) {
            case 0 -> labels[rowIndex];
            case 1 -> m.completedProcesses();
            case 2 -> String.format("%.3f", m.throughput());
            case 3 -> String.format("%.2f", m.cpuUtilization() * 100);
            case 4 -> String.format("%.2f", m.averageResponseTime());
            case 5 -> String.format("%.2f", m.averageTurnaroundTime());
            case 6 -> String.format("%.2f", m.deadlineComplianceRate() * 100);
            case 7 -> String.format("%.3f", m.fairnessIndex());
            case 8 -> String.format("%.2f", m.averageSemaphoreBlockedTime());
            default -> "";
        };
    }
}
