package com.unimet.avilaos.hardware;

/**
 * Tipos de interrupción que el hardware simulado puede generar.
 *
 * @author victor1102
 */
public enum InterruptType {

    IO_COMPLETION("Fin de E/S"),
    NETWORK_COMPLETION("Fin de acceso remoto por red");

    private final String label;

    InterruptType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}