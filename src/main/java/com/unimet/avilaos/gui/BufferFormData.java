package com.unimet.avilaos.gui;

/**
 * Datos de un buffer nuevo, ya validados por el formulario.
 * El clúster es quien crea el buffer en la RAM de su anfitrión.
 *
 * @param name           nombre único del buffer (ej. "B1")
 * @param capacity       cantidad máxima de elementos
 * @param memorySize     memoria que ocupa en su anfitrión (MB)
 * @param hostComputerId computador donde vive el buffer
 *
 * @author NicollPinzon
 */
public record BufferFormData(String name, int capacity, int memorySize, int hostComputerId) {
}
