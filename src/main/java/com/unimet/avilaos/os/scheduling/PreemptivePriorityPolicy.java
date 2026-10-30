package com.unimet.avilaos.os.scheduling;

import com.unimet.avilaos.datastructures.ElementComparator;
import com.unimet.avilaos.os.process.Pcb;

/**
 * Prioridades apropiativas: ejecuta primero al de mayor prioridad
 * (convención del equipo: MENOR número = MAYOR prioridad). Si llega a listos uno
 * de prioridad estrictamente mayor que el que corre, lo expropia.
 * Entre iguales se respeta el orden de llegada.
 *
 * @author donatovodola
 */
public class PreemptivePriorityPolicy implements SchedulingPolicy {

    @Override
    public PolicyType getType() {
        return PolicyType.PREEMPTIVE_PRIORITY;
    }

    @Override
    public ElementComparator<Pcb> readyOrder() {
        return (a, b) -> Integer.compare(a.getPriority(), b.getPriority());
    }

    @Override
    public boolean shouldPreempt(Pcb running, Pcb candidate) {
        return candidate.getPriority() < running.getPriority();
    }
}
