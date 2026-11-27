package com.unimet.avilaos.config;

import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.os.disk.DiskPolicyType;

/**
 * Parámetros del disco del computador (Proyecto 2): bloques de archivos, área de
 * intercambio, política de disco inicial, posición y dirección inicial del cabezal
 * y cuántas posiciones avanza el cabezal por ciclo.
 *
 * El disco tiene fileBlocks + swapBlocks bloques: primero la zona de archivos
 * (0 a fileBlocks − 1) y después el swap, como en el JSON del enunciado
 * (totalBlocks 200 + swapSizeBlocks 100 = 300).
 *
 * @param fileBlocks        bloques para archivos (MIN_FILE_BLOCKS a MAX_FILE_BLOCKS)
 * @param swapBlocks        bloques del área de intercambio (0 a MAX_SWAP_BLOCKS)
 * @param initialPolicy     política de disco con la que arranca
 * @param initialHead       bloque donde empieza el cabezal
 * @param initialDirection  dirección inicial del cabezal
 * @param positionsPerCycle posiciones que recorre el cabezal en un ciclo (1 a MAX_POSITIONS_PER_CYCLE)
 *
 * @author NicollPinzon
 */
public record DiskConfig(int fileBlocks, int swapBlocks, DiskPolicyType initialPolicy, int initialHead,
        HeadDirection initialDirection, int positionsPerCycle) {

    /** El enunciado pide "una cantidad razonable, por ejemplo entre 100 y 500 bloques". */
    public static final int MIN_FILE_BLOCKS = 100;
    public static final int MAX_FILE_BLOCKS = 500;
    public static final int MAX_SWAP_BLOCKS = 500;
    public static final int MAX_POSITIONS_PER_CYCLE = 500;

    public DiskConfig {
        ConfigText.requireRange(fileBlocks, MIN_FILE_BLOCKS, MAX_FILE_BLOCKS, "Los bloques del disco");
        ConfigText.requireRange(swapBlocks, 0, MAX_SWAP_BLOCKS, "Los bloques del área de intercambio");
        if (initialPolicy == null) {
            throw new IllegalArgumentException("Falta la política de disco inicial");
        }
        ConfigText.requireRange(initialHead, 0, fileBlocks + swapBlocks - 1, "El cabezal inicial");
        if (initialDirection == null) {
            throw new IllegalArgumentException("Falta la dirección inicial del cabezal");
        }
        ConfigText.requireRange(positionsPerCycle, 1, MAX_POSITIONS_PER_CYCLE, "Las posiciones del cabezal por ciclo");
    }

    /**
     * El disco de los casos de prueba del enunciado: 200 bloques + 100 de swap,
     * FIFO, cabezal en 50 hacia los bloques mayores, 10 posiciones por ciclo.
     */
    public static DiskConfig defaults() {
        return new DiskConfig(200, 100, DiskPolicyType.FIFO, 50, HeadDirection.UP, 10);
    }

    /** Bloques del disco completo (archivos + swap). */
    public int totalBlocks() {
        return fileBlocks + swapBlocks;
    }
}
