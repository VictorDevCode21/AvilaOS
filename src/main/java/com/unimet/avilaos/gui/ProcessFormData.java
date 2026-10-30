package com.unimet.avilaos.gui;

import com.unimet.avilaos.os.process.ProcessType;

/**
 * Datos de un proceso nuevo, ya validados por la Vista 1.
 *
 * La GUI no crea el PCB: entrega estos datos y el clúster decide el ciclo de
 * llegada, asigna el computador si es automático y crea el Pcb.
 * Los campos que no aplican al tipo quedan en 0 (o null para el buffer).
 *
 * @param name              nombre del proceso
 * @param type              CPU bound, I/O bound, productor o consumidor
 * @param instructions      cantidad de instrucciones
 * @param memorySize        memoria que requiere (MB)
 * @param priority          prioridad (0 = la más alta)
 * @param deadline          ciclos que tiene para terminar
 * @param computerId        computador elegido, o AUTOMATIC para que ÁvilaOS lo asigne
 * @param ioInterval        I/O bound: cada cuántas instrucciones pide E/S
 * @param ioDuration        I/O bound: cuántos ciclos tarda la E/S
 * @param bufferName        productor/consumidor: buffer con el que trabaja
 * @param operationInterval productor/consumidor: cada cuántos ciclos produce/consume
 * @param itemsRequired     productor/consumidor: elementos que necesita para terminar
 *
 * @author NicollPinzon
 */
public record ProcessFormData(String name, ProcessType type, int instructions, int memorySize,
        int priority, int deadline, int computerId, int ioInterval, int ioDuration,
        String bufferName, int operationInterval, int itemsRequired) {

    /** Valor de computerId cuando el usuario deja que ÁvilaOS elija el computador. */
    public static final int AUTOMATIC = 0;

    public boolean isAutomatic() {
        return computerId == AUTOMATIC;
    }
}
