package com.unimet.avilaos.config;

/**
 * Configuración de un buffer de la carga inicial: capacidad y computador anfitrión.
 * Inmutable (record).
 *
 * @param name           nombre único, ej. "B1"
 * @param hostComputerId computador en cuya RAM vive
 * @param capacity       elementos que caben (mayor que 0)
 * @param memorySize     MB que ocupa en la RAM del anfitrión (mayor que 0)
 *
 * @author donatovodola
 */
public record BufferConfig(String name, int hostComputerId, int capacity, int memorySize) {

    public BufferConfig {
        name = ConfigText.requireName(name, "El buffer");
        if (hostComputerId < 1) {
            throw new IllegalArgumentException("El anfitrión del buffer " + name + " debe ser 1 o mayor");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("La capacidad del buffer " + name + " debe ser mayor que 0");
        }
        if (memorySize <= 0) {
            throw new IllegalArgumentException("La memoria del buffer " + name + " debe ser mayor que 0");
        }
    }
}
