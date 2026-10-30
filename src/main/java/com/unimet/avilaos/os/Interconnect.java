package com.unimet.avilaos.os;

import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.sync.BoundedBuffer;
import com.unimet.avilaos.os.sync.KernelSemaphore;

/**
 * La red del clúster vista desde el kernel de UN computador.
 *
 * El kernel (capa os) necesita llegar a otros computadores, pero no puede importar
 * la capa cluster, que está encima. Por eso define aquí lo que necesita y el
 * Cluster lo implementa (inversión de dependencias, igual que InterruptHandler
 * entre hardware y SO).
 *
 * @author victor1102
 */
public interface Interconnect {

    /** El buffer con ese nombre, viva en el computador que viva; null si no existe. */
    BoundedBuffer findBuffer(String name);

    /**
     * Entrega un despertar al kernel del computador dueño del proceso (opción B:
     * ningún computador cambia el PCB de otro). Lo procesa al inicio del ciclo siguiente.
     *
     * @param pcb       proceso despertado (puede ser de este u otro computador)
     * @param from      semáforo que lo despertó (para el log)
     * @param sentCycle ciclo en que se hizo el semSignal
     */
    void deliverWakeup(Pcb pcb, KernelSemaphore from, long sentCycle);

    /** Ciclos que tarda cada acceso a un buffer remoto. */
    int getNetworkLatency();
}