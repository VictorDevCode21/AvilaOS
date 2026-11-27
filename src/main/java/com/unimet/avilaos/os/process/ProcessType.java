package com.unimet.avilaos.os.process;

/**
 * Tipos de proceso que el usuario puede crear (Vista 1).
 *
 * FILE_IO es el proceso de E/S del Proyecto 2: hace UNA operación CRUD del sistema
 * de archivos por medio del disco. No se crea con el formulario de procesos del P1
 * (no tiene operación ni ruta), sino con su propio formulario o desde el JSON.
 *
 * @author donatovodola
 * @author NicollPinzon
 */
public enum ProcessType {

    CPU_BOUND("CPU bound"),
    IO_BOUND("I/O bound"),
    PRODUCER("Productor"),
    CONSUMER("Consumidor"),
    FILE_IO("E/S de archivo");

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

    /** true si es un proceso de E/S de archivo (su operación la atiende el disco). */
    public boolean isFileIo() {
        return this == FILE_IO;
    }

    /** Los tipos del formulario de procesos del P1: todos menos FILE_IO. */
    public static ProcessType[] formTypes() {
        ProcessType[] all = values();
        ProcessType[] result = new ProcessType[all.length - 1];
        int i = 0;
        for (ProcessType type : all) {
            if (!type.isFileIo()) {
                result[i++] = type;
            }
        }
        return result;
    }
}
