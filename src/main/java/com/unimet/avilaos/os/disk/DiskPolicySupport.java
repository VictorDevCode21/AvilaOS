package com.unimet.avilaos.os.disk;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;

/**
 * Búsquedas comunes a las políticas de disco, para no repetirlas en cada clase.
 * Todas recorren la cola en orden de llegada y, si hay empate, se quedan con la
 * que llegó primero: así los empates siempre se resuelven igual (FIFO).
 *
 * Solo lee la lista que recibe; no la modifica.
 *
 * @author NicollPinzon
 */
final class DiskPolicySupport {

    private DiskPolicySupport() {
    }

    /** La más cercana al cabezal en cualquier dirección (SSTF). */
    static DiskRequest nearest(LinkedList<DiskRequest> pending, int headBlock) {
        DiskRequest best = null;
        for (int i = 0; i < pending.size(); i++) {
            DiskRequest r = pending.get(i);
            if (best == null || r.distanceFrom(headBlock) < best.distanceFrom(headBlock)) {
                best = r;
            }
        }
        return best;
    }

    /**
     * La más cercana que está por delante del cabezal en la dirección dada,
     * contando el bloque donde está el cabezal (se atiende sin moverse).
     *
     * @return null si no queda ninguna por delante
     */
    static DiskRequest nearestAhead(LinkedList<DiskRequest> pending, int headBlock,
            HeadDirection direction) {
        DiskRequest best = null;
        for (int i = 0; i < pending.size(); i++) {
            DiskRequest r = pending.get(i);
            if (isAhead(r, headBlock, direction)
                    && (best == null || r.distanceFrom(headBlock) < best.distanceFrom(headBlock))) {
                best = r;
            }
        }
        return best;
    }

    /**
     * La del bloque más bajo (si la dirección es UP) o la del más alto (si es
     * DOWN): la primera que se encuentra al empezar un barrido en esa dirección
     * desde el extremo contrario (C-SCAN y C-LOOK después del salto).
     */
    static DiskRequest firstFromStart(LinkedList<DiskRequest> pending, HeadDirection direction) {
        DiskRequest best = null;
        for (int i = 0; i < pending.size(); i++) {
            DiskRequest r = pending.get(i);
            if (best == null
                    || (r.getTargetBlock() - best.getTargetBlock()) * direction.getStep() < 0) {
                best = r;
            }
        }
        return best;
    }

    /** Último bloque en la dirección dada: blockCount - 1 si es UP, 0 si es DOWN. */
    static int edge(HeadDirection direction, int blockCount) {
        return direction == HeadDirection.UP ? blockCount - 1 : 0;
    }

    private static boolean isAhead(DiskRequest r, int headBlock, HeadDirection direction) {
        return (r.getTargetBlock() - headBlock) * direction.getStep() >= 0;
    }
}
