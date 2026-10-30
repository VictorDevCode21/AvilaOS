package com.unimet.avilaos.events;

/**
 * Observador de la simulación (patrón Observer). Lo implementa la GUI.
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
 *   LogEntry.sequence() o reconstruir la vista con EventLog.getEntries()
 *   en onCycleCompleted.
 *
 * @author victor1102
 */
public interface SimulationListener {

    /** Se generó una nueva entrada en el log. */
    void onLogEntry(LogEntry entry);

    /** Terminó un ciclo de reloj: buen momento para refrescar la GUI. */
    default void onCycleCompleted(long cycle) {
    }
}