package com.unimet.avilaos.config;

/**
 * Configuración de un buffer: nombre, anfitrión, capacidad y memoria.
 * Inmutable (record). Los límites son los mismos que usa el formulario de la GUI.
 *
 * Los nombres se comparan sin distinguir mayúsculas (ver sameName): "b1" y "B1"
 * son el mismo buffer en todo ÁvilaOS (configuración, métricas y GUI).
 *
 * @param name           nombre único, ej. "B1" (letras y números)
 * @param hostComputerId computador en cuya RAM vive
 * @param capacity       elementos que caben
 * @param memorySize     MB que ocupa en la RAM del anfitrión
 *
 * @author donatovodola
 */
public record BufferConfig(String name, int hostComputerId, int capacity, int memorySize) {

    public static final int NAME_MAX_LENGTH = 10;
    public static final int MAX_CAPACITY = 1_000;

    public BufferConfig {
        name = ConfigText.requireCode(name, "El buffer", NAME_MAX_LENGTH);
        if (hostComputerId < 1) {
            throw new IllegalArgumentException("El anfitrión del buffer " + name + " debe ser 1 o mayor");
        }
        ConfigText.requireRange(capacity, 1, MAX_CAPACITY, "La capacidad del buffer " + name);
        if (memorySize <= 0) {
            throw new IllegalArgumentException("La memoria del buffer " + name + " debe ser mayor que 0");
        }
    }

    /** Regla única de ÁvilaOS para comparar nombres de buffer: sin espacios y sin distinguir mayúsculas. */
    public static boolean sameName(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }
}
