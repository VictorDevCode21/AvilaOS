package com.unimet.avilaos.events;

/**
 * Observador del log de la simulación (patrón Observer). Hoy lo usa la demo GuiDemo (src/test);
 * la ventana principal no se registra: lee el log por copias (EventLog.getEntries) cada 150 ms.
 *
 * Reglas para quien lo implemente:
 * - Los métodos se llaman desde los hilos de los computadores, NO desde el hilo
 *   de Swing: para tocar componentes Swing hay que usar SwingUtilities.invokeLater.
 * - Deben ser rápidos y no bloquear: el computador que generó el evento espera
 *   a que terminen.
 * - Si lanzan una excepción, el EventLog la aísla (no rompe la simulación),
 *   pero ese observador se pierde ese evento.
 * - ORDEN: las entradas se GUARDAN en orden, pero dos computadores pueden
 *   notificar al mismo tiempo y adelantarse entre sí, así que onLogEntry puede
 *   recibirlas en otro orden. Para mostrar el log ordenado, usar
 *   LogEntry.sequence() o reconstruir la vista con EventLog.getEntries().
 *
 * @author victor1102
 */
@FunctionalInterface
public interface SimulationListener {

    /** Se generó una nueva entrada en el log. */
    void onLogEntry(LogEntry entry);
}
