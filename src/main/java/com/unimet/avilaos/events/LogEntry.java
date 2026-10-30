package com.unimet.avilaos.events;

/**
 * Una entrada del log de eventos. Inmutable: segura para pasar entre hilos.
 *
 * @param cycle   ciclo de reloj en que ocurrió
 * @param source  quién lo generó (ej. "Computador 1", "Clúster")
 * @param message texto en español para la GUI
 *
 * @author victor1102
 */
public record LogEntry(long cycle, String source, String message) {

    /** Formato para la GUI, ej. "[Ciclo 587] Computador 1: selecciona el proceso P3-H1". */
    public String format() {
        return "[Ciclo " + cycle + "] " + source + ": " + message;
    }
}