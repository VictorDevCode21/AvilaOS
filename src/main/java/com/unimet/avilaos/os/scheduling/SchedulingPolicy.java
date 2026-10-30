package com.unimet.avilaos.os.scheduling;

import com.unimet.avilaos.datastructures.ElementComparator;
import com.unimet.avilaos.os.process.Pcb;

/**
 * POLÍTICA de planificación (el QUÉ): decide el orden de la cola de listos y
 * cuándo expropiar. El Scheduler es el MECANISMO (el CÓMO): mantiene la cola,
 * selecciona y aplica lo que la política responde (Prepa 3: política contra mecanismo).
 *
 * Interfaz exigida por el enunciado: agregar una política nueva es crear una
 * clase que implemente esto y un valor en PolicyType; el Scheduler no se toca.
 *
 * @author donatovodola
 */
public interface SchedulingPolicy {

    /** Qué política es (para la GUI, el log y la configuración). */
    PolicyType getType();

    /**
     * Orden de la cola de listos: negativo si a va antes que b.
     * Si empatan (0), el Scheduler desempata por orden de llegada a listos
     * (readySequence), así que la política no necesita ocuparse de eso.
     */
    ElementComparator<Pcb> readyOrder();

    /**
     * ¿El proceso que llegó a listos debe sacar de la CPU al que está corriendo?
     * Por defecto no (políticas no apropiativas).
     */
    default boolean shouldPreempt(Pcb running, Pcb candidate) {
        return false;
    }

    /**
     * ¿Se le acabó el turno al proceso en ejecución? Solo Round Robin usa quantum.
     *
     * @param quantum ciclos por turno configurados en el computador
     */
    default boolean isQuantumExpired(Pcb running, int quantum) {
        return false;
    }
}
