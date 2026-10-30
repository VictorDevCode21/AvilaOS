package com.unimet.avilaos.hardware;

/**
 * Quien atiende las interrupciones del hardware (el Kernel).
 * Permite que la CPU avise al sistema operativo sin depender de él:
 * equivale al vector de interrupciones de un procesador real.
 *
 * @author victor1102
 */
@FunctionalInterface
public interface InterruptHandler {

    /** Rutina de servicio de interrupción (ISR). */
    void handleInterrupt(Interrupt interrupt);
}