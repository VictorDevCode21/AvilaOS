package com.unimet.avilaos.hardware;

/**
 * Dirección en la que se mueve el cabezal del disco. La GUI la muestra junto
 * a la posición del cabezal, y las políticas SCAN, C-SCAN, LOOK y C-LOOK la
 * usan para decidir qué solicitud atender.
 *
 * @author NicollPinzon
 */
public enum HeadDirection {

    UP("Ascendente ↑", 1),
    DOWN("Descendente ↓", -1);

    private final String label;
    private final int step;

    HeadDirection(String label, int step) {
        this.label = label;
        this.step = step;
    }

    /** Texto en español para la GUI y el log. */
    public String getLabel() {
        return label;
    }

    /** +1 si el cabezal va hacia los bloques mayores, -1 si va hacia los menores. */
    public int getStep() {
        return step;
    }

    /** La dirección contraria (la que toma SCAN al llegar al extremo). */
    public HeadDirection opposite() {
        return this == UP ? DOWN : UP;
    }

    /**
     * Dirección del movimiento entre dos bloques. Si son el mismo bloque no hay
     * movimiento y se conserva la dirección actual.
     */
    public static HeadDirection between(int from, int to, HeadDirection current) {
        if (to > from) {
            return UP;
        }
        if (to < from) {
            return DOWN;
        }
        return current;
    }
}
