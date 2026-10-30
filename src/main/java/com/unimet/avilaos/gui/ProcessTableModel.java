package com.unimet.avilaos.gui;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.os.process.Pcb;
import javax.swing.table.AbstractTableModel;

/**
 * Modelo de tabla que muestra los PCB de una cola (Nuevos, Listos, Bloqueados o Terminados).
 *
 * Solo lee los PCB: nunca los modifica. Guarda una "foto" (arreglo) de la cola
 * para que la tabla no se altere mientras el kernel cambia la cola real en otro hilo.
 * No usa colecciones de java.util (lo prohíbe el enunciado).
 *
 * @author NicollPinzon
 */
public class ProcessTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "ID", "Nombre", "Tipo", "Prior.", "PC", "Instr. rest.", "Deadline", "Memoria (MB)", "Estado / motivo"
    };

    private Pcb[] rows = new Pcb[0];

    /**
     * Reemplaza el contenido de la tabla por los procesos de la cola, en el mismo orden.
     * Debe llamarse desde el hilo de Swing.
     */
    public void setProcesses(LinkedList<Pcb> queue) {
        LinkedList<Pcb> snapshot = queue.copy();
        Pcb[] newRows = new Pcb[snapshot.size()];
        for (int i = 0; i < newRows.length; i++) {
            newRows[i] = snapshot.get(i);
        }
        rows = newRows;
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
            case 8 -> p.getBlockReason() == null ? p.getState().getLabel() : p.describeBlock();
            default -> "";
        };
    }
}
