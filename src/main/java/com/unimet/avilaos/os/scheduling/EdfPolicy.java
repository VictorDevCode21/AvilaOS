package com.unimet.avilaos.os.scheduling;

import com.unimet.avilaos.datastructures.ElementComparator;
import com.unimet.avilaos.os.process.Pcb;

/**
 * EDF (Earliest Deadline First): ejecuta primero al proceso cuyo deadline está
 * más cerca (menos ciclos restantes). Apropiativa: si llega uno con deadline
 * más urgente que el que corre, lo saca de la CPU (Stallings, cap. 10).
 *
 * No hace falta reordenar en cada tick: el deadline de TODOS baja 1 por ciclo,
 * así que el orden relativo entre ellos no cambia.
 *
 * @author donatovodola
 */
public class EdfPolicy implements SchedulingPolicy {

    @Override
    public PolicyType getType() {
        return PolicyType.EDF;
    }

    @Override
    public ElementComparator<Pcb> readyOrder() {
        return (a, b) -> Long.compare(a.getRemainingDeadline(), b.getRemainingDeadline());
    }

    @Override
    public boolean shouldPreempt(Pcb running, Pcb candidate) {
        return candidate.getRemainingDeadline() < running.getRemainingDeadline();
    }
}
