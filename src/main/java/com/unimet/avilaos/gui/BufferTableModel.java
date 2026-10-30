package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.BufferConfig;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.datastructures.LinkedQueue;
import com.unimet.avilaos.metrics.BufferStats;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.sync.BoundedBuffer;
import com.unimet.avilaos.os.sync.KernelSemaphore;
import javax.swing.table.AbstractTableModel;

/**
 * Modelo de tabla con el estado de cada buffer del productor-consumidor: ocupación,
 * valor de sus 3 semáforos, procesos que esperan en cada uno y elementos producidos
 * y consumidos.
 *
 * Igual que ProcessTableModel, guarda una "foto" (arreglo de filas ya armadas) tomada
 * en setBuffers: todos los valores de una fila se leen en el mismo refresco y la tabla
 * no cambia a mitad de un redibujo. Solo lee: nunca modifica los buffers.
 *
 * @author victor1102
 */
public final class BufferTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    /** Encabezados cortos para que quepan; el nombre completo sale en COLUMN_TIPS. */
    static final String[] COLUMNS = {
        "Buffer", "Anf.", "Ocup.", "Vacíos", "Lleno", "Mutex",
        "Esp. vacíos", "Esp. lleno", "Esp. mutex", "Prod.", "Cons."
    };

    /** Ancho sugerido de cada columna: las de procesos en espera son las más anchas. */
    static final int[] COLUMN_WIDTHS = {50, 35, 45, 45, 40, 45, 95, 95, 95, 45, 45};

    /** Tooltip de cada encabezado (mismo orden que COLUMNS). */
    static final String[] COLUMN_TIPS = {
        "Nombre del buffer", "Computador anfitrión (en cuya RAM vive el buffer)", "Ocupación: elementos / capacidad",
        "Valor del semáforo 'vacíos' (espacios libres)", "Valor del semáforo 'lleno' (elementos listos)",
        "Valor del semáforo 'mutex' (1 = región crítica libre)",
        "Procesos bloqueados en 'vacíos'", "Procesos bloqueados en 'lleno'", "Procesos bloqueados en 'mutex'",
        "Elementos insertados desde el inicio", "Elementos extraídos desde el inicio"
    };

    /** Una fila ya calculada: así la tabla no vuelve a tocar los buffers al redibujarse. */
    private record Row(String name, int host, String occupancy, int empty, int full, int mutex,
            String waitingEmpty, String waitingFull, String waitingMutex, long produced, long consumed) {
    }

    private transient Row[] rows = new Row[0];   // la tabla nunca se serializa

    /**
     * Reemplaza el contenido por los buffers de la copia, en el mismo orden.
     * Debe llamarse desde el hilo de Swing.
     *
     * @param buffers copia de la lista de buffers (Cluster.getBuffers())
     * @param stats   producidos/consumidos de cada buffer (SystemMetrics.getBufferStats())
     */
    public void setBuffers(LinkedList<BoundedBuffer> buffers, LinkedList<BufferStats> stats) {
        LinkedList<BoundedBuffer> walk = buffers.copy();
        Row[] newRows = new Row[walk.size()];
        for (int i = 0; i < newRows.length; i++) {
            BoundedBuffer b = walk.removeFirst();
            BufferStats s = findStats(stats, b.getName());
            newRows[i] = new Row(b.getName(), b.getHostComputerId(), b.getItemCount() + "/" + b.getCapacity(),
                    b.getEmpty().getValue(), b.getFull().getValue(), b.getMutex().getValue(),
                    waitingText(b.getEmpty()), waitingText(b.getFull()), waitingText(b.getMutex()),
                    s == null ? 0 : s.getProduced(), s == null ? 0 : s.getConsumed());
        }
        rows = newRows;
        fireTableDataChanged();
    }

    /** Vacía la tabla (ej. al reiniciar la simulación). */
    public void clear() {
        rows = new Row[0];
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
        Row r = rows[rowIndex];
        return switch (columnIndex) {
            case 0 -> r.name();
            case 1 -> r.host();
            case 2 -> r.occupancy();
            case 3 -> r.empty();
            case 4 -> r.full();
            case 5 -> r.mutex();
            case 6 -> r.waitingEmpty();
            case 7 -> r.waitingFull();
            case 8 -> r.waitingMutex();
            case 9 -> r.produced();
            case 10 -> r.consumed();
            default -> "";
        };
    }

    /** Ej. "P3-H1, P5-H1", o "—" si nadie espera. Recorre una copia de la cola. */
    private static String waitingText(KernelSemaphore semaphore) {
        LinkedQueue<Pcb> waiting = semaphore.getWaitingSnapshot();
        if (waiting.isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        while (!waiting.isEmpty()) {
            sb.append(sb.length() == 0 ? "" : ", ").append(waiting.dequeue());
        }
        return sb.toString();
    }

    private static BufferStats findStats(LinkedList<BufferStats> stats, String bufferName) {
        for (int i = 0; i < stats.size(); i++) {
            if (BufferConfig.sameName(stats.get(i).getBufferName(), bufferName)) {
                return stats.get(i);
            }
        }
        return null;
    }
}
