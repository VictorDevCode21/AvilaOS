package com.unimet.avilaos.os.fs;

/**
 * Permiso de un archivo o directorio: público (cualquiera lo lee) o privado
 * (solo su dueño y el administrador).
 *
 * @author NicollPinzon
 */
public enum Permission {

    PUBLIC("Público", "public"),
    PRIVATE("Privado", "private");

    private final String label;
    private final String jsonName;

    Permission(String label, String jsonName) {
        this.label = label;
        this.jsonName = jsonName;
    }

    /** Texto en español para la GUI y el log. */
    public String getLabel() {
        return label;
    }

    /** Cómo se escribe en el JSON del sistema de archivos ("public" / "private"). */
    public String getJsonName() {
        return jsonName;
    }

    /**
     * Busca por etiqueta ("Privado"), por el nombre del JSON ("private") o por el
     * nombre del enum ("PRIVATE"), sin importar mayúsculas.
     */
    public static Permission fromLabel(String label) {
        String text = label == null ? "" : label.trim();
        for (Permission permission : values()) {
            if (permission.label.equalsIgnoreCase(text) || permission.jsonName.equalsIgnoreCase(text)
                    || permission.name().equalsIgnoreCase(text)) {
                return permission;
            }
        }
        throw new IllegalArgumentException("Permiso desconocido: " + label + " (usa public o private)");
    }
}
