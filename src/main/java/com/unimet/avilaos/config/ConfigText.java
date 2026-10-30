package com.unimet.avilaos.config;

/**
 * Validación de textos que van al archivo CSV.
 * Un nombre no puede tener comas ni saltos de línea: romperían las columnas.
 *
 * @author donatovodola
 */
final class ConfigText {

    private ConfigText() {
    }

    /** Devuelve el nombre sin espacios sobrantes, o lanza error si es inválido. */
    static String requireName(String name, String what) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(what + " debe tener nombre");
        }
        if (name.contains(",") || name.contains("\n") || name.contains("\r")) {
            throw new IllegalArgumentException(what + " '" + name.trim() + "' no puede contener comas ni saltos de línea");
        }
        return name.trim();
    }
}
