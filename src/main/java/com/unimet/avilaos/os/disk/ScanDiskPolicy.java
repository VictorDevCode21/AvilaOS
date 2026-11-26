package com.unimet.avilaos.os.disk;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.SeekPlan;

/**
 * SCAN (ascensor): el cabezal avanza en su dirección atendiendo lo que
 * encuentra; cuando ya no queda nada por delante sigue hasta el ÚLTIMO bloque
 * del disco, cambia de dirección y atiende lo que quedó atrás. Así lo pide el
 * enunciado ("llega al 199 y regresa"; anexo: 332 bloques).
 *
 * Evita la inanición de SSTF: toda solicitud se atiende, como mucho, en la
 * vuelta siguiente.
 *
 * @author NicollPinzon
 */
public final class ScanDiskPolicy implements DiskSchedulingPolicy {

    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.SCAN;
    }

    @Override
    public SeekPlan select(LinkedList<DiskRequest> pending, int headBlock, HeadDirection direction,
            int blockCount) {
        DiskRequest ahead = DiskPolicySupport.nearestAhead(pending, headBlock, direction);
        if (ahead != null) {
            return new SeekPlan(ahead, headBlock, SeekPlan.NONE, SeekPlan.NONE, direction);
        }
        HeadDirection back = direction.opposite();
        DiskRequest behind = DiskPolicySupport.nearestAhead(pending, headBlock, back);
        int edge = DiskPolicySupport.edge(direction, blockCount);
        int sweep = headBlock == edge ? SeekPlan.NONE : edge;    // ya está en el extremo
        return new SeekPlan(behind, headBlock, sweep, SeekPlan.NONE, back);
    }
}
