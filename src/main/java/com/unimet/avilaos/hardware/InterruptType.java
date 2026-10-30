package com.unimet.avilaos.hardware;

/**
 * Tipos de interrupción que el hardware simulado puede generar.
 *
 * Solo el dispositivo de E/S interrumpe: la latencia de red no es un dispositivo,
 * la cuenta el kernel como un temporizador por proceso (ver Kernel y BlockReason.NETWORK).
 * Si en el Proyecto 2 se agrega otro dispositivo (el disco), su interrupción va aquí.
 *
 * @author victor1102
 */
public enum InterruptType {

    IO_COMPLETION("Fin de E/S");

    private final String label;

    InterruptType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}