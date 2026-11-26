package com.unimet.avilaos.os.disk;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.SeekPlan;

/**
 * SSTF (Shortest Seek Time First): atiende la solicitud más cercana al cabezal,
 * hacia cualquier lado. Reduce mucho el desplazamiento (208 bloques en el
 * anexo), pero una solicitud lejana puede esperar indefinidamente si siguen
 * llegando solicitudes cerca del cabezal (inanición).
 *
 * Empate de distancia: gana la que llegó primero.
 *
 * @author NicollPinzon
 */
public final class SstfDiskPolicy implements DiskSchedulingPolicy {

    @Override
    public DiskPolicyType getType() {
        return DiskPolicyType.SSTF;
    }

    @Override
    public SeekPlan select(LinkedList<DiskRequest> pending, int headBlock, HeadDirection direction,
            int diskSize) {
        return SeekPlan.direct(DiskPolicySupport.nearest(pending, headBlock), headBlock, direction);
    }
}
