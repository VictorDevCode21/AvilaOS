package com.unimet.avilaos.hardware;

/**
 * Operación que un proceso de E/S le pide al disco (enum exigido por el
 * enunciado del Proyecto 2). Va en el PCB del proceso de E/S y en cada
 * solicitud de la cola del disco.
 *
 * Las cuatro primeras son el CRUD del sistema de archivos. SUSPEND y RESUME
 * las genera el planificador a mediano plazo: también pasan por la cola del
 * disco porque escriben o leen el área de intercambio (swap).
 *
 * Está en hardware (y no en os.process) porque describe lo que hace el disco:
 * así el disco no depende del sistema operativo, igual que IoDevice en el P1.
 *
 * @author NicollPinzon
 */
public enum IoOperation {

    CREATE("Crear", true),
    READ("Leer", true),
    UPDATE("Actualizar", true),
    DELETE("Eliminar", true),
    SUSPEND("Suspender", false),
    RESUME("Reactivar", false);

    private final String label;
    private final boolean fileOperation;

    IoOperation(String label, boolean fileOperation) {
        this.label = label;
        this.fileOperation = fileOperation;
    }

    /** Texto en español para la GUI y el log. */
    public String getLabel() {
        return label;
    }

    /**
     * true si es una operación sobre un archivo o directorio (CRUD);
     * false si es de intercambio (suspender o reactivar un proceso).
     */
    public boolean isFileOperation() {
        return fileOperation;
    }

    /**
     * Busca por etiqueta ("Leer") o por nombre del enum ("READ"), sin importar
     * mayúsculas: el JSON de procesos del enunciado usa el nombre.
     */
    public static IoOperation fromLabel(String label) {
        String text = label == null ? "" : label.trim();
        for (IoOperation operation : values()) {
            if (operation.label.equalsIgnoreCase(text) || operation.name().equalsIgnoreCase(text)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("Operación de E/S desconocida: " + label);
    }
}
