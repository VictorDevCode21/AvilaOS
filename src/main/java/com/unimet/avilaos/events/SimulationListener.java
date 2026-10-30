package com.unimet.avilaos.events;

/**
 * Observador de la simulación. Lo implementa la GUI para enterarse de lo que
 * pasa sin que la lógica de ÁvilaOS dependa de ella ("la GUI observa, no es el sistema").
 *
 * Los avisos llegan desde los hilos de la simulación, NO desde el hilo de Swing:
 * quien implemente esto en la GUI debe actualizar la pantalla con
 * SwingUtilities.invokeLater(...).
 *
 * @author victor1102
 */
public interface SimulationListener {

    /** Se registró un evento nuevo en el log. */
    void onLogEntry(LogEntry entry);

    /** Terminó un ciclo de reloj en todo el clúster (para refrescar paneles). Opcional. */
    default void onCycleCompleted(long cycle) {
    }
}