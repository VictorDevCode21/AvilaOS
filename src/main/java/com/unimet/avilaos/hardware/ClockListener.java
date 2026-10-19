package com.unimet.avilaos.hardware;

/**
 * Contrato común para todo componente que reacciona al reloj global
 * (CPU, dispositivo de E/S, computador). Exigido por el enunciado.
 *
 * El reloj llama a onTick una vez por ciclo; el componente debe avanzar
 * exactamente un ciclo y retornar. No debe bloquearse dentro de onTick.
 *
 * @author victor1102
 */
public interface ClockListener {

    /**
     * Ejecuta exactamente un ciclo.
     *
     * @param cycle número de ciclo global actual (empieza en 1)
     */
    void onTick(long cycle);
}