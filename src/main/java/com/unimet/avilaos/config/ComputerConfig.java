package com.unimet.avilaos.config;

import com.unimet.avilaos.os.scheduling.PolicyType;

/**
 * Configuración inicial de UN computador: tamaño de su RAM y política inicial.
 * Inmutable (record): se valida al crearse y ya no cambia.
 *
 * @param id            número del computador (1, 2, 3...)
 * @param ramSize       RAM en MB (mayor que 0)
 * @param initialPolicy política con la que arranca (se puede cambiar en ejecución)
 *
 * @author donatovodola
 */
public record ComputerConfig(int id, int ramSize, PolicyType initialPolicy) {

    public ComputerConfig {
        if (id < 1) {
            throw new IllegalArgumentException("El número de computador debe ser 1 o mayor");
        }
        if (ramSize <= 0) {
            throw new IllegalArgumentException("La RAM del computador " + id + " debe ser mayor que 0");
        }
        if (initialPolicy == null) {
            throw new IllegalArgumentException("El computador " + id + " necesita una política inicial");
        }
    }
}
