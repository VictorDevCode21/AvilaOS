package com.unimet.avilaos.os.disk;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.SeekPlan;

/**
 * LOOK (opcional en el enunciado): igual que SCAN, pero el cabezal cambia de
 * dirección en la última SOLICITUD, sin seguir hasta el extremo del disco.
 * Ahorra el tramo inútil hasta el borde (anexo: 314 bloques contra 332 de SCAN).
 *
 * @author NicollPinzon
 */
public final class LookDiskPolicy implements DiskSchedulingPolicy {

    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.LOOK;
    }

    @Override
    public SeekPlan select(LinkedList<DiskRequest> pending, int headBlock, HeadDirection direction,
            int fileBlocks) {
        DiskRequest ahead = DiskPolicySupport.nearestAhead(pending, headBlock, direction);
        if (ahead != null) {
            return new SeekPlan(ahead, headBlock, SeekPlan.NONE, SeekPlan.NONE, direction);
        }
        HeadDirection back = direction.opposite();
        DiskRequest behind = DiskPolicySupport.nearestAhead(pending, headBlock, back);
        return new SeekPlan(behind, headBlock, SeekPlan.NONE, SeekPlan.NONE, back);
    }
}
