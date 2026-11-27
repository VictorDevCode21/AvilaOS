package com.unimet.avilaos.hardware;

/**
 * Tipos de interrupción que el hardware simulado puede generar.
 *
 * Interrumpen el dispositivo de E/S del Proyecto 1 y el disco del Proyecto 2.
 * La latencia de red no es un dispositivo: la cuenta el kernel como un
 * temporizador por proceso (ver Kernel y BlockReason.NETWORK).
 *
 * @author victor1102
 * @author NicollPinzon
 */
public enum InterruptType {

    IO_COMPLETION("Fin de E/S"),
    DISK_COMPLETION("Fin de E/S de disco");

    private final String label;

    InterruptType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
