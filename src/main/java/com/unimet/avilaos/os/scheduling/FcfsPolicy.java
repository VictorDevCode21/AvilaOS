package com.unimet.avilaos.os.scheduling;

import com.unimet.avilaos.datastructures.ElementComparator;
import com.unimet.avilaos.os.process.Pcb;

/**
 * FCFS (First Come, First Served): el primero en llegar a listos es el primero
 * en ejecutarse, y corre hasta que termina o se bloquea. No apropiativa.
 *
 * El comparador siempre empata: insertSorted coloca al nuevo DESPUÉS de los
 * iguales, así que la cola queda en orden de llegada (FIFO puro).
 *
 * @author donatovodola
 */
public class FcfsPolicy implements SchedulingPolicy {

    @Override
    public PolicyType getType() {
        return PolicyType.FCFS;
    }

    @Override
    public ElementComparator<Pcb> readyOrder() {
        return (a, b) -> 0;
    }
}
