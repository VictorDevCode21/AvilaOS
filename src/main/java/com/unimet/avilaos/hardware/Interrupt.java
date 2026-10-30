package com.unimet.avilaos.hardware;

/**
 * Una interrupción concreta: qué pasó y a qué proceso afecta.
 * Inmutable: segura para pasar entre hilos.
 *
 * @param type      tipo de interrupción
 * @param processId ID del proceso cuya operación terminó
 *
 * @author victor1102
 */
public record Interrupt(InterruptType type, int processId) {
}