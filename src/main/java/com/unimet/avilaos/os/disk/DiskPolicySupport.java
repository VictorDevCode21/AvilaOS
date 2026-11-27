package com.unimet.avilaos.os.disk;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;

/**
 * Búsquedas comunes a las políticas de disco, para no repetirlas en cada clase.
 * Todas recorren la cola en orden de llegada y, si hay empate, se quedan con la
 * que llegó primero: así los empates siempre se resuelven igual (FIFO).
 *
 * Solo lee la lista que recibe; no la modifica. Para recorrerla una sola vez
 * (O(n)) la pasa primero a un arreglo: LinkedList.get(i) empieza desde la
 * cabeza en cada llamada, y recorrerla con get(i) haría cada búsqueda O(n²).
 *
 * @author NicollPinzon
 */
final class DiskPolicySupport {

    private DiskPolicySupport() {
    }

    /**
     * Copia la cola a un arreglo en orden de llegada, en O(n): saca de una copia
     * con removeFirst() (O(1)) en lugar de usar get(i) (O(n) cada vez).
     */
    static DiskRequest[] toArray(LinkedList<DiskRequest> pending) {
        LinkedList<DiskRequest> copy = pending.copy();
        DiskRequest[] result = new DiskRequest[copy.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = copy.removeFirst();
        }
        return result;
    }

    /** La más cercana al cabezal en cualquier dirección (SSTF). */
    static DiskRequest nearest(LinkedList<DiskRequest> pending, int headBlock) {
        DiskRequest best = null;
        for (DiskRequest r : toArray(pending)) {
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
        for (DiskRequest r : toArray(pending)) {
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
        for (DiskRequest r : toArray(pending)) {
            if (best == null
                    || (r.getTargetBlock() - best.getTargetBlock()) * direction.getStep() < 0) {
                best = r;
            }
        }
        return best;
    }

    /**
     * Extremo hasta el que barren SCAN y C-SCAN en la dirección dada:
     * <ul>
     *   <li>DOWN: el bloque 0;</li>
     *   <li>UP: el último bloque de la zona de archivos (fileBlocks - 1), o el
     *       bloque del cabezal si ya está más arriba, en el área de intercambio.</li>
     * </ul>
     * Así, con el disco del enunciado (200 bloques de archivos + 100 de swap), el
     * cabezal llega al 199 como dicen los casos P3 y P4, y las solicitudes de
     * suspender o reactivar (que van al swap) se atienden igual.
     */
    static int edge(HeadDirection direction, int fileBlocks, int headBlock) {
        return direction == HeadDirection.UP ? Math.max(fileBlocks - 1, headBlock) : 0;
    }

    private static boolean isAhead(DiskRequest r, int headBlock, HeadDirection direction) {
        return (r.getTargetBlock() - headBlock) * direction.getStep() >= 0;
    }
}
