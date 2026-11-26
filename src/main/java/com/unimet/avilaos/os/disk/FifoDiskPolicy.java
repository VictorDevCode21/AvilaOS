package com.unimet.avilaos.os.disk;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.SeekPlan;

/**
 * FIFO: atiende las solicitudes en el orden en que llegaron, sin mirar dónde
 * está el cabezal. Es justa (nadie espera de más), pero el cabezal va y viene
 * por todo el disco: en el anexo recorre 518 bloques, el peor de los cuatro.
 *
 * @author NicollPinzon
 */
public final class FifoDiskPolicy implements DiskSchedulingPolicy {

    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.FIFO;
    }

    @Override
    public SeekPlan select(LinkedList<DiskRequest> pending, int headBlock, HeadDirection direction,
            int blockCount) {
        return SeekPlan.direct(pending.peekFirst(), headBlock, direction);
    }
}
