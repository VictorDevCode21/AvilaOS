package com.unimet.avilaos.hardware;

/**
 * Qué está haciendo el disco en un ciclo, para la GUI y el log.
 *
 * @author NicollPinzon
 */
public enum DiskPhase {

    IDLE("Libre"),
    SWEEP("Barriendo hasta el extremo"),
    JUMP("Saltando"),
    SEEK("Moviendo el cabezal"),
    TRANSFER("Leyendo / escribiendo");

    private final String label;

    DiskPhase(String label) {
        this.label = label;
    }

    /** Texto en español para la GUI y el log. */
    public String getLabel() {
        return label;
    }
}
