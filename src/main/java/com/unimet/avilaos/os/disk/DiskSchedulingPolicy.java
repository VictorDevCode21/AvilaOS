package com.unimet.avilaos.os.disk;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.SeekPlan;

/**
 * POLÍTICA de planificación de disco (el QUÉ): dada la cola de E/S y el estado
 * del cabezal, elige la siguiente solicitud y el recorrido para llegar a ella.
 * El planificador de disco es el MECANISMO (el CÓMO): guarda la cola, la protege
 * con un semáforo, saca la solicitud elegida y permite cambiar de política en
 * ejecución. Es la misma separación que SchedulingPolicy y Scheduler en la CPU.
 *
 * Interfaz exigida por el enunciado: agregar una política nueva es crear una
 * clase que implemente esto y una constante en DiskPolicyType que la cree
 * (el enum es el catálogo que usan la GUI, el JSON y las métricas). El
 * planificador de disco no se toca.
 *
 * Las implementaciones no guardan estado entre llamadas (la dirección del
 * cabezal llega como parámetro), así que cambiar de política a mitad de la
 * cola no deja nada a medias.
 *
 * @author NicollPinzon
 */
public interface DiskSchedulingPolicy {

    /** Qué política es (para la GUI, el log, las métricas y la configuración). */
    DiskPolicyType getType();

    /**
     * Elige la siguiente solicitud a atender.
     *
     * @param pending    solicitudes pendientes en orden de llegada; nunca vacía.
     *                   Es una copia: la política no debe modificarla.
     * @param headBlock  bloque donde está el cabezal
     * @param direction  dirección actual del cabezal
     * @param diskSize   cantidad total de bloques del disco (el último es diskSize - 1).
     *                   No confundir con DiskRequest.getBlockCount(), que son los
     *                   bloques que lee o escribe una solicitud.
     * @return el recorrido hasta la solicitud elegida, que debe ser uno de los
     *         objetos de pending
     */
    SeekPlan select(LinkedList<DiskRequest> pending, int headBlock, HeadDirection direction,
            int diskSize);
}
