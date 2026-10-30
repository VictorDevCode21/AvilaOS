package com.unimet.avilaos.os.process;

/**
 * Motivo por el que un proceso está en BLOCKED. Justificación para la defensa:
 *  - IO: un I/O bound espera que el dispositivo termine; lo despierta la interrupción IO_COMPLETION.
 *  - SEMAPHORE: hizo semWait sobre empty, full o mutex de un buffer ("vacíos", "lleno",
 *    "mutex" en la GUI) y el valor no le permitió seguir; lo despierta un semSignal de otro proceso.
 *  - NETWORK: accede a un buffer de otro computador y espera los ciclos de latencia;
 *    el kernel descuenta esa latencia en cada ciclo y, al llegar a 0, lo pasa a Listo
 *    (no es un dispositivo, así que no genera ninguna interrupción).
 * Los tres son esperas que SÍ terminan (no son interbloqueos).
 *
 * @author donatovodola
 */
public enum BlockReason {

    IO("Esperando E/S"),
    SEMAPHORE("Esperando semáforo"),
    NETWORK("Latencia de red");

    private final String label;

    BlockReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}