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
    static String requireName(String name, String what, int maxLength) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(what + " debe tener nombre");
        }
        String trimmed = name.trim();
        if (trimmed.contains(",") || trimmed.contains("\n") || trimmed.contains("\r")) {
            throw new IllegalArgumentException(what + " '" + trimmed + "' no puede contener comas ni saltos de línea");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(what + " '" + trimmed + "' tiene más de " + maxLength + " caracteres");
        }
        return trimmed;
    }

    /** Como requireName, pero solo letras y números (ej. el nombre de un buffer: "B1"). */
    static String requireCode(String name, String what, int maxLength) {
        String trimmed = requireName(name, what, maxLength);
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isLetterOrDigit(trimmed.charAt(i))) {
                throw new IllegalArgumentException(what + " '" + trimmed + "' solo puede tener letras y números");
            }
        }
        return trimmed;
    }

    /** Lanza error si el valor está fuera de [min, max]. */
    static void requireRange(long value, long min, long max, String what) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(what + " debe estar entre " + min + " y " + max + " (es " + value + ")");
        }
    }
}
