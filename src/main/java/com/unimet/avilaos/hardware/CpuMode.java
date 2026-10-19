package com.unimet.avilaos.hardware;

/**
 * Modo de ejecución del procesador.
 * USER: ejecuta instrucciones del programa del proceso.
 * KERNEL: ejecuta código del sistema operativo (planificador, semWait, semSignal,
 * inserción/extracción en buffers, atención de interrupciones).
 *
 * @author victor1102
 */
public enum CpuMode {

    USER("Usuario"),
    KERNEL("Sistema operativo");

    private final String label;

    CpuMode(String label) {
        this.label = label;
    }

    /** Texto en español para mostrar en la GUI. */
    public String getLabel() {
        return label;
    }
}