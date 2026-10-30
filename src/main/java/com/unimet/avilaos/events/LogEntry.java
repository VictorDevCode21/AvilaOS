package com.unimet.avilaos.events;

/**
 * Una línea del log de eventos. Inmutable: segura para pasar entre hilos.
 *
 * @param sequence número de orden global (1, 2, 3...). Lo asigna el EventLog al
 *                 guardar la entrada, así que refleja el orden REAL de llegada aunque
 *                 las notificaciones a los observadores lleguen desordenadas.
 * @param cycle    ciclo de reloj en que ocurrió
 * @param source   quién lo generó, ej. "Computador 1"
 * @param message  qué pasó, ej. "P3-H1 pasa a Bloqueado (E/S)"
 *
 * @author victor1102
 */
public record LogEntry(long sequence, long cycle, String source, String message) {

    /** Formato para la GUI, ej. "[Ciclo 587] Computador 1: P3-H1 pasa a Listo". */
    public String format() {
        return "[Ciclo " + cycle + "] " + source + ": " + message;
    }
}