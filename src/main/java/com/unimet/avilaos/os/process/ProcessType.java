package com.unimet.avilaos.os.process;

/**
 * Tipos de proceso que el usuario puede crear (Vista 1).
 *
 * @author donatovodola
 */
public enum ProcessType {

    CPU_BOUND("CPU bound"),
    IO_BOUND("I/O bound"),
    PRODUCER("Productor"),
    CONSUMER("Consumidor");

    private final String label;

    ProcessType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** true si el proceso trabaja con un buffer (productor o consumidor). */
    public boolean usesBuffer() {
        return this == PRODUCER || this == CONSUMER;
    }
}
