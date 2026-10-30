package com.unimet.avilaos.metrics;

/**
 * Un punto de la gráfica de utilización: en qué ciclo se tomó y cuánto
 * se usó la CPU en el intervalo que termina en ese ciclo (0 a 1).
 * Inmutable: segura para pasar entre el hilo del computador y el de la GUI.
 *
 * @param cycle       ciclo en el que termina el intervalo
 * @param utilization fracción del intervalo en que la CPU estuvo ocupada (0 a 1)
 *
 * @author NicollPinzon
 */
public record UtilizationSample(long cycle, double utilization) {
}
