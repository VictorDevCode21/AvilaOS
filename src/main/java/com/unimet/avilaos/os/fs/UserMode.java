package com.unimet.avilaos.os.fs;

/**
 * Modo de uso del simulador (enum exigido por el enunciado): el administrador
 * puede hacer todas las operaciones; el usuario tiene acceso restringido según
 * las reglas de {@link AccessRules}.
 *
 * @author NicollPinzon
 */
public enum UserMode {

    ADMIN("Administrador"),
    USER("Usuario");

    private final String label;

    UserMode(String label) {
        this.label = label;
    }

    /** Texto en español para la GUI y el log. */
    public String getLabel() {
        return label;
    }

    /** Busca por etiqueta ("Usuario") o por nombre del enum ("USER"), sin importar mayúsculas. */
    public static UserMode fromLabel(String label) {
        String text = label == null ? "" : label.trim();
        for (UserMode mode : values()) {
            if (mode.label.equalsIgnoreCase(text) || mode.name().equalsIgnoreCase(text)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Modo de usuario desconocido: " + label);
    }
}
