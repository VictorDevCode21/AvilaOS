package com.unimet.avilaos.hardware;

/**
 * De dónde saca el disco la siguiente solicitud a atender. Lo implementa el
 * planificador de disco (os.disk.DiskScheduler); el disco solo conoce esta
 * interfaz, así el hardware no depende del sistema operativo.
 *
 * @author NicollPinzon
 */
public interface SeekPlanSource {

    /**
     * Saca de la cola la siguiente solicitud y devuelve el recorrido del cabezal.
     *
     * @param headBlock bloque donde está el cabezal
     * @param direction dirección actual del cabezal
     * @return el recorrido, o null si no hay solicitudes
     */
    SeekPlan next(int headBlock, HeadDirection direction);
}
