package com.unimet.avilaos.os.scheduling;

import com.unimet.avilaos.datastructures.ElementComparator;
import com.unimet.avilaos.os.process.Pcb;

/**
 * Round Robin: cola FIFO, pero cada proceso corre como máximo 'quantum' ciclos
 * seguidos. Al agotarlo vuelve al FINAL de la cola de listos (expropiación por
 * tiempo). Da buen tiempo de respuesta y equidad.
 *
 * El quantum no se guarda aquí sino en el Scheduler, porque se puede cambiar en
 * plena ejecución; el PCB lleva cuántos ciclos ha usado en su turno (quantumUsed).
 *
 * @author donatovodola
 */
public class RoundRobinPolicy implements SchedulingPolicy {

    @Override
    public PolicyType getType() {
        return PolicyType.ROUND_ROBIN;
    }

    @Override
    public ElementComparator<Pcb> readyOrder() {
        return (a, b) -> 0;
    }

    @Override
    public boolean isQuantumExpired(Pcb running, int quantum) {
        return running.getQuantumUsed() >= quantum;
    }
}
