package com.unimet.avilaos.os;

import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.process.ProcessType;
import com.unimet.avilaos.os.sync.BoundedBuffer;
import com.unimet.avilaos.os.sync.KernelSemaphore;

/**
 * Lo que el kernel necesita saber de un proceso para ejecutarlo y que NO es parte
 * del PCB: en qué paso de su programa va, cuántas instrucciones le faltan para su
 * próxima E/S o su próximo elemento, en qué semáforo espera y cuánta latencia de
 * red le queda. Solo lo usa el hilo de su computador.
 *
 * @author victor1102
 */
final class ProcessRuntime {

    final Pcb pcb;
    ExecutionStep step;
    int userLeft;                 // productor/consumidor: instrucciones de usuario del tramo actual
    int sinceIo;                  // I/O bound: instrucciones desde la última E/S
    boolean latencyPaid;          // la operación remota actual ya esperó la latencia
    int networkLeft;              // ciclos de latencia que faltan (Bloqueado por red)
    KernelSemaphore waitingOn;    // semáforo en cuya cola espera (Bloqueado por semáforo)
    BoundedBuffer buffer;         // buffer con el que trabaja (productor/consumidor)

    ProcessRuntime(Pcb pcb) {
        this.pcb = pcb;
        if (pcb.getType() == ProcessType.CONSUMER) {
            this.step = ExecutionStep.WAIT_FULL;            // primero espera un elemento
        } else {
            this.step = ExecutionStep.USER;
            this.userLeft = pcb.getOperationInterval();     // productor: primero produce
        }
    }

    boolean isProducer() {
        return pcb.getType() == ProcessType.PRODUCER;
    }

    boolean isConsumer() {
        return pcb.getType() == ProcessType.CONSUMER;
    }
}