package com.unimet.avilaos.os.disk;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.SeekPlan;

/**
 * C-LOOK (opcional en el enunciado): igual que C-SCAN, pero sin ir hasta los
 * extremos. Cuando no queda nada por delante, el cabezal salta directo a la
 * primera solicitud del otro lado (la del bloque más bajo si va hacia arriba) y
 * sigue en la misma dirección.
 *
 * Igual que en C-SCAN, el salto de regreso no cuenta como desplazamiento
 * (anexo: 167 bloques). Se usa el mismo criterio en las dos para que sean
 * comparables en las métricas.
 *
 * @author NicollPinzon
 */
public final class CLookDiskPolicy implements DiskSchedulingPolicy {

    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.C_LOOK;
    }

    @Override
    public SeekPlan select(LinkedList<DiskRequest> pending, int headBlock, HeadDirection direction,
            int fileBlocks) {
        DiskRequest ahead = DiskPolicySupport.nearestAhead(pending, headBlock, direction);
        if (ahead != null) {
            return new SeekPlan(ahead, headBlock, SeekPlan.NONE, SeekPlan.NONE, direction);
        }
        DiskRequest first = DiskPolicySupport.firstFromStart(pending, direction);
        return new SeekPlan(first, headBlock, SeekPlan.NONE, first.getTargetBlock(), direction);
    }
}
