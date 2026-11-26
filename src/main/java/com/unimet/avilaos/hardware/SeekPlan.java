package com.unimet.avilaos.hardware;

/**
 * Recorrido que debe hacer el cabezal para atender una solicitud: lo decide la
 * política de disco y lo ejecuta el disco, N posiciones por ciclo.
 *
 * Hace falta más que "la solicitud elegida" porque SCAN y C-SCAN no van directo
 * al bloque: el enunciado pide que SCAN llegue al último bloque y regrese, y que
 * C-SCAN llegue al último bloque y salte al 0 (anexo: 332 y 192 bloques). El
 * recorrido tiene hasta tres tramos, en este orden:
 * <ol>
 *   <li>barrido hasta un extremo (sweepEdge), si lo hay: cuenta como desplazamiento;</li>
 *   <li>salto a otro bloque (jumpTo), si lo hay: NO cuenta como desplazamiento
 *       (el anexo mide C-SCAN "sin contar el salto de regreso");</li>
 *   <li>tramo hasta el bloque objetivo de la solicitud: cuenta.</li>
 * </ol>
 * Ejemplos: FIFO, SSTF y LOOK van directo (sin barrido ni salto); SCAN usa solo
 * el barrido; C-SCAN usa barrido y salto al 0; C-LOOK usa solo el salto.
 *
 * Es inmutable y se calcula al elegir la solicitud.
 *
 * @author NicollPinzon
 */
public final class SeekPlan {

    /** Valor de sweepEdge y jumpTo cuando ese tramo no existe. */
    public static final int NONE = -1;

    private final DiskRequest request;
    private final int startBlock;
    private final int sweepEdge;
    private final int jumpTo;
    private final HeadDirection directionAfter;

    /**
     * @param request        solicitud elegida
     * @param startBlock     posición del cabezal al elegirla
     * @param sweepEdge      extremo al que barre antes (NONE si va directo)
     * @param jumpTo         bloque al que salta después del barrido (NONE si no salta)
     * @param directionAfter dirección en la que queda el cabezal al llegar al objetivo.
     *                       La política es responsable de que sea coherente con el
     *                       recorrido (por ejemplo, SCAN la invierte después del barrido);
     *                       SeekPlan no la recalcula.
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public SeekPlan(DiskRequest request, int startBlock, int sweepEdge, int jumpTo,
            HeadDirection directionAfter) {
        if (request == null) {
            throw new IllegalArgumentException("El recorrido necesita una solicitud");
        }
        if (startBlock < 0) {
            throw new IllegalArgumentException("La posición del cabezal no puede ser negativa");
        }
        if (sweepEdge < NONE || jumpTo < NONE) {
            throw new IllegalArgumentException("Los bloques del recorrido no pueden ser negativos (use SeekPlan.NONE)");
        }
        if (directionAfter == null) {
            throw new IllegalArgumentException("El recorrido necesita la dirección final del cabezal");
        }
        this.request = request;
        this.startBlock = startBlock;
        this.sweepEdge = sweepEdge;
        this.jumpTo = jumpTo;
        this.directionAfter = directionAfter;
    }

    /** Recorrido directo: el cabezal va del bloque actual al objetivo. */
    public static SeekPlan direct(DiskRequest request, int startBlock, HeadDirection current) {
        HeadDirection after = HeadDirection.between(startBlock, request.getTargetBlock(), current);
        return new SeekPlan(request, startBlock, NONE, NONE, after);
    }

    public DiskRequest getRequest() {
        return request;
    }

    public int getStartBlock() {
        return startBlock;
    }

    public int getSweepEdge() {
        return sweepEdge;
    }

    public int getJumpTo() {
        return jumpTo;
    }

    public HeadDirection getDirectionAfter() {
        return directionAfter;
    }

    public boolean hasSweep() {
        return sweepEdge != NONE;
    }

    public boolean hasJump() {
        return jumpTo != NONE;
    }

    /** Bloque desde el que empieza el último tramo (el que llega al objetivo). */
    public int getLastLegStart() {
        if (hasJump()) {
            return jumpTo;
        }
        return hasSweep() ? sweepEdge : startBlock;
    }

    /**
     * Bloques que recorre el cabezal, sin contar el salto: barrido + último tramo.
     * Es lo que suma el desplazamiento total de las métricas.
     */
    public int getSeekDistance() {
        int sweep = hasSweep() ? Math.abs(sweepEdge - startBlock) : 0;
        return sweep + Math.abs(request.getTargetBlock() - getLastLegStart());
    }

    /** Ejemplo: "180 → 199 → (salto) 0 → 11". */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append(startBlock);
        if (hasSweep()) {
            sb.append(" → ").append(sweepEdge);
        }
        if (hasJump()) {
            sb.append(" → (salto) ").append(jumpTo);
        }
        if (request.getTargetBlock() != getLastLegStart()) {
            sb.append(" → ").append(request.getTargetBlock());
        }
        return sb.toString();
    }
}
