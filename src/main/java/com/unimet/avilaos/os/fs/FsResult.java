package com.unimet.avilaos.os.fs;

/**
 * Respuesta del sistema de archivos a una operación: si se permite (o se hizo),
 * el mensaje para el log o para el aviso de la GUI, y los datos de la solicitud
 * de disco (bloque objetivo y bloques que se leen o escriben).
 *
 * Un rechazo no es una excepción: la GUI muestra el mensaje y la simulación sigue
 * (lo pide el enunciado).
 *
 * @author NicollPinzon
 */
public final class FsResult {

    private final boolean ok;
    private final String message;
    private final int targetBlock;
    private final int blockCount;

    private FsResult(boolean ok, String message, int targetBlock, int blockCount) {
        this.ok = ok;
        this.message = message;
        this.targetBlock = targetBlock;
        this.blockCount = blockCount;
    }

    static FsResult allowed(String message, int targetBlock, int blockCount) {
        return new FsResult(true, message, targetBlock, blockCount);
    }

    static FsResult denied(String reason) {
        return new FsResult(false, reason, -1, 0);
    }

    /** true si la operación se permite (en check) o se hizo (en apply). */
    public boolean isOk() {
        return ok;
    }

    /** Qué se hizo, o por qué se rechazó, en español. */
    public String getMessage() {
        return message;
    }

    /** Bloque al que debe ir el cabezal (-1 si se rechazó). */
    public int getTargetBlock() {
        return targetBlock;
    }

    /** Bloques que la operación lee o escribe: 1 ciclo por bloque (0 si se rechazó). */
    public int getBlockCount() {
        return blockCount;
    }

    @Override
    public String toString() {
        return (ok ? "OK: " : "Rechazado: ") + message;
    }
}
