package com.unimet.avilaos.os.disk;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.SeekPlan;

/**
 * C-SCAN (SCAN circular): atiende solo en una dirección. Cuando no queda nada
 * por delante, el cabezal llega al ÚLTIMO bloque, salta al extremo contrario
 * (el bloque 0 si va hacia arriba) y sigue atendiendo en la misma dirección.
 * El salto no cuenta como desplazamiento (anexo: 192 bloques "sin contar el
 * salto de regreso").
 *
 * Comparado con SCAN, la espera es más pareja: las solicitudes del principio
 * del disco no tienen que esperar a que el cabezal regrese recorriendo todo.
 *
 * @author NicollPinzon
 */
public final class CScanDiskPolicy implements DiskSchedulingPolicy {

    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.C_SCAN;
    }

    @Override
    public SeekPlan select(LinkedList<DiskRequest> pending, int headBlock, HeadDirection direction,
            int blockCount) {
        DiskRequest ahead = DiskPolicySupport.nearestAhead(pending, headBlock, direction);
        if (ahead != null) {
            return new SeekPlan(ahead, headBlock, SeekPlan.NONE, SeekPlan.NONE, direction);
        }
        DiskRequest first = DiskPolicySupport.firstFromStart(pending, direction);
        int edge = DiskPolicySupport.edge(direction, blockCount);
        int start = DiskPolicySupport.edge(direction.opposite(), blockCount);
        int sweep = headBlock == edge ? SeekPlan.NONE : edge;    // ya está en el extremo
        return new SeekPlan(first, headBlock, sweep, start, direction);
    }
}
