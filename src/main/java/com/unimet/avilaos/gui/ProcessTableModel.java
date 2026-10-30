package com.unimet.avilaos.gui;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.os.process.Pcb;
import javax.swing.table.AbstractTableModel;

/**
 * Modelo de tabla que muestra los PCB de una cola (Nuevos, Listos, Bloqueados o Terminados).
 *
 * Solo lee los PCB: nunca los modifica. Guarda una "foto" (arreglo) de la cola,
 * así la tabla no cambia a mitad de un redibujo. No usa colecciones de java.util.
 *
 * IMPORTANTE: quien llama debe pasar una COPIA de la cola tomada con el candado
 * de su dueño (ej. Scheduler.getReadySnapshot()), nunca la cola viva del kernel:
 * recorrer la cola viva desde el hilo de Swing competiría con el hilo del computador.
 *
 * @author NicollPinzon
 */
public class ProcessTableModel extends AbstractTableModel {

    static final String[] COLUMNS = {
        "ID", "Nombre", "Tipo", "Prior.", "PC", "Instr. rest.", "Deadline", "Mem. MB", "Estado / motivo"
    };

    /** Columna del motivo de bloqueo: la más ancha, para que no se corte. */
    static final int STATE_COLUMN = 8;

    private Pcb[] rows = new Pcb[0];

    /**
     * Reemplaza el contenido de la tabla por los procesos de la copia, en el mismo orden.
     * Debe llamarse desde el hilo de Swing.
     *
     * @param snapshot copia de la cola (ver javadoc de la clase)
     */
    public void setProcesses(LinkedList<Pcb> snapshot) {
        Pcb[] newRows = new Pcb[snapshot.size()];
        for (int i = 0; i < newRows.length; i++) {
            newRows[i] = snapshot.get(i);
        }
        rows = newRows;
        fireTableDataChanged();
    }

    /** Vacía la tabla (ej. al reiniciar la simulación). */
    public void clear() {
        rows = new Pcb[0];
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
        Pcb p = rows[rowIndex];
        return switch (columnIndex) {
            case 0 -> p.toString();                       // P3-H1
            case 1 -> p.getName();
            case 2 -> p.getType().getLabel();
            case 3 -> p.getPriority();
            case 4 -> p.getProgramCounter();
            case 5 -> p.getRemainingInstructions();
            case 6 -> p.getRemainingDeadline();
            case 7 -> p.getMemorySize();
            case 8 -> stateText(p);
            default -> "";
        };
    }

    /** Estado, o el motivo completo si está bloqueado (ej. "Esperando semáforo: 'vacíos' de B1"). */
    static String stateText(Pcb p) {
        if (p.getBlockReason() != null) {
            return p.describeBlock();
        }
        if (p.isDeadlineMissed()) {
            return p.getState().getLabel() + " (venció su deadline)";
        }
        return p.getState().getLabel();
    }
}
