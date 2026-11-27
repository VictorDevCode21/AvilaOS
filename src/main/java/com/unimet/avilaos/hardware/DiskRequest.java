package com.unimet.avilaos.hardware;

/**
 * Solicitud que entra a la cola de E/S del disco: qué proceso la pide, qué
 * operación es, sobre qué archivo o directorio trabaja, en qué bloque empieza y
 * cuántos bloques lee o escribe.
 *
 * Es inmutable: la política de disco solo la lee para elegir, así que se puede
 * compartir con la GUI sin copiarla. Dos solicitudes distintas son objetos
 * distintos aunque tengan los mismos datos (las listas propias comparan con ==).
 *
 * El bloque objetivo es el que usan las políticas para medir distancias. El
 * límite superior (tamaño del disco) no se valida aquí porque la solicitud no
 * conoce el disco: lo valida el planificador de disco al encolarla. Cuál es el
 * bloque objetivo de cada operación (por ejemplo, al crear un archivo o al
 * eliminar un directorio) lo decide quien crea la solicitud y se documenta en
 * el sistema de archivos.
 *
 * @author NicollPinzon
 */
public final class DiskRequest {

    /** Destino de las solicitudes SUSPEND y RESUME, que trabajan sobre el área de intercambio. */
    public static final String SWAP_TARGET = "swap";

    private final int processId;
    private final IoOperation operation;
    private final String target;
    private final int targetBlock;
    private final int blockCount;
    private final long arrivalCycle;

    /**
     * @param processId    pid del proceso que la pide (mayor que 0)
     * @param operation    operación solicitada
     * @param target       ruta del archivo o directorio, o {@link #SWAP_TARGET}
     *                     para suspender o reactivar
     * @param targetBlock  bloque donde empieza la operación (0 o más)
     * @param blockCount   bloques que se leen o escriben (1 o más; 1 ciclo por bloque)
     * @param arrivalCycle ciclo del reloj en que entró a la cola (para la espera promedio)
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public DiskRequest(int processId, IoOperation operation, String target, int targetBlock,
            int blockCount, long arrivalCycle) {
        if (processId <= 0) {
            throw new IllegalArgumentException("El id del proceso debe ser mayor que 0");
        }
        if (operation == null) {
            throw new IllegalArgumentException("La solicitud de disco necesita una operación");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("La solicitud de disco necesita la ruta del archivo o \""
                    + SWAP_TARGET + "\"");
        }
        if (targetBlock < 0) {
            throw new IllegalArgumentException("El bloque objetivo no puede ser negativo: " + targetBlock);
        }
        if (blockCount <= 0) {
            throw new IllegalArgumentException("La solicitud debe abarcar al menos 1 bloque");
        }
        if (arrivalCycle < 0) {
            throw new IllegalArgumentException("El ciclo de llegada no puede ser negativo");
        }
        this.processId = processId;
        this.operation = operation;
        this.target = target.trim();
        this.targetBlock = targetBlock;
        this.blockCount = blockCount;
        this.arrivalCycle = arrivalCycle;
    }

    public int getProcessId() {
        return processId;
    }

    public IoOperation getOperation() {
        return operation;
    }

    /** Ruta del archivo o directorio, o {@link #SWAP_TARGET} si es de intercambio. */
    public String getTarget() {
        return target;
    }

    public int getTargetBlock() {
        return targetBlock;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public long getArrivalCycle() {
        return arrivalCycle;
    }

    /** Distancia en bloques desde el cabezal hasta el bloque objetivo. */
    public int distanceFrom(int headBlock) {
        return Math.abs(targetBlock - headBlock);
    }

    @Override
    public String toString() {
        return "P" + processId + " " + operation.getLabel() + " " + target + ", bloque " + targetBlock
                + " (" + blockCount + (blockCount == 1 ? " bloque" : " bloques") + ")";
    }
}
