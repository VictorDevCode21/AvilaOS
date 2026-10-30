package com.unimet.avilaos.os.sync;

/**
 * Los dos tipos de semáforo que conviven en el productor-consumidor.
 * El enunciado advierte que confundirlos es el error más común:
 *  - COUNTING: cuenta recursos (espacios vacíos o elementos). Puede valer de 0 a N.
 *  - BINARY: mutex de exclusión mutua sobre el buffer. Solo vale 0 o 1.
 *
 * @author donatovodola
 */
public enum SemaphoreType {

    COUNTING("De conteo"),
    BINARY("Binario (mutex)");

    private final String label;

    SemaphoreType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
