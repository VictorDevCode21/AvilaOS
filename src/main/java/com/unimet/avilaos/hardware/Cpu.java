package com.unimet.avilaos.hardware;

import com.unimet.avilaos.datastructures.LinkedQueue;
import java.util.concurrent.Semaphore;

/**
 * Procesador simulado de un computador.
 *
 * Ejecuta exactamente una instrucción por ciclo de reloj sobre el contexto
 * cargado (PC, MAR, modo). No conoce procesos ni PCB: el kernel carga y guarda
 * su contexto en los cambios de proceso. Al final de cada ciclo revisa las
 * interrupciones pendientes y las entrega al InterruptHandler (el kernel),
 * igual que el ciclo de instrucción con interrupciones de Stallings (cap. 1).
 *
 * @author victor1102
 */
public class Cpu implements ClockListener {

    // ---------- Registros ----------
    private volatile int programCounter;
    private volatile int memoryAddressRegister;
    private volatile CpuMode mode = CpuMode.KERNEL;
    private volatile boolean idle = true;
    private volatile boolean kernelWork;      // el próximo ciclo es trabajo del SO (no avanza el PC)

    // ---------- Interrupciones ----------
    private final LinkedQueue<Interrupt> pendingInterrupts = new LinkedQueue<>();
    private final Semaphore interruptMutex = new Semaphore(1, true);
    private volatile InterruptHandler interruptHandler;

    // ---------- Contadores para métricas (solo los escribe el hilo de este computador) ----------
    private volatile long busyCycles;
    private volatile long idleCycles;
    private volatile long userCycles;
    private volatile long kernelCycles;

    /** Lo conecta el Computer al ensamblarse: cpu.setInterruptHandler(kernel). */
    public void setInterruptHandler(InterruptHandler interruptHandler) {
        this.interruptHandler = interruptHandler;
    }

    // ---------- Ciclo de instrucción ----------

    @Override
    public void onTick(long cycle) {
        if (kernelWork) {
            // Trabajo propio del SO (despachar, pedir E/S, enviar una solicitud remota):
            // la CPU está ocupada en modo SO, pero no ejecuta instrucciones del proceso.
            busyCycles++;
            kernelCycles++;
            kernelWork = false;
        } else if (idle) {
            idleCycles++;
        } else {
            memoryAddressRegister = programCounter;   // captación: MAR <- PC
            programCounter++;                         // PC apunta a la siguiente instrucción
            busyCycles++;
            if (mode == CpuMode.USER) {
                userCycles++;
            } else {
                kernelCycles++;
            }
        }
        serviceInterrupts();                          // fase de interrupción, al final del ciclo
    }

    /**
     * Entrega cada interrupción pendiente al handler.
     * Se saca de la cola DENTRO del mutex, pero el handler se llama FUERA:
     * si el kernel generara otra interrupción mientras la atiende, no nos
     * bloquearíamos contra nuestro propio mutex.
     */
    private void serviceInterrupts() {
        while (true) {
            Interrupt next;
            interruptMutex.acquireUninterruptibly();
            try {
                next = pendingInterrupts.dequeue();
            } finally {
                interruptMutex.release();
            }
            if (next == null) {
                return;
            }
            InterruptHandler handler = interruptHandler;
            if (handler != null) {
                handler.handleInterrupt(next);
            }
        }
    }

    /** Lo llama un dispositivo (E/S, red) para dejar una interrupción pendiente. */
    public void raiseInterrupt(Interrupt interrupt) {
        interruptMutex.acquireUninterruptibly();
        try {
            pendingInterrupts.enqueue(interrupt);
        } finally {
            interruptMutex.release();
        }
    }

    // ---------- Cambio de contexto (lo usa el kernel) ----------

    /** Carga el contexto de un proceso: la CPU deja de estar ociosa. */
    public void loadContext(int programCounter, CpuMode mode) {
        this.programCounter = programCounter;
        this.memoryAddressRegister = programCounter;
        this.mode = mode;
        this.idle = false;
    }

    /** Devuelve el PC actual para que el kernel lo guarde en el PCB. */
    public int saveContext() {
        return programCounter;
    }

    /** Deja la CPU sin proceso (ociosa). */
    public void unload() {
        this.idle = true;
        this.mode = CpuMode.KERNEL;
        this.kernelWork = false;
    }

    /** El kernel indica en qué modo se ejecutará la próxima instrucción. */
    public void setMode(CpuMode mode) {
        this.mode = mode;
    }

    /**
     * El próximo ciclo la CPU hace trabajo del sistema operativo (despachar, registrar
     * una E/S, enviar una solicitud remota): cuenta como ciclo ocupado en modo SO,
     * pero NO avanza el PC ni el MAR, porque no es una instrucción del proceso.
     */
    public void scheduleKernelWork() {
        this.mode = CpuMode.KERNEL;
        this.kernelWork = true;
    }

    /** true si el ciclo planificado es trabajo del SO (para la GUI). */
    public boolean isKernelWork() {
        return kernelWork;
    }

    // ---------- Consultas (GUI y métricas) ----------

    public int getProgramCounter() {
        return programCounter;
    }

    public int getMemoryAddressRegister() {
        return memoryAddressRegister;
    }

    public CpuMode getMode() {
        return mode;
    }

    public boolean isIdle() {
        return idle;
    }

    public long getBusyCycles() {
        return busyCycles;
    }

    public long getIdleCycles() {
        return idleCycles;
    }

    public long getUserCycles() {
        return userCycles;
    }

    public long getKernelCycles() {
        return kernelCycles;
    }

    /** Utilización = ciclos ocupada / ciclos totales (0 a 1). */
    public double getUtilization() {
        long total = busyCycles + idleCycles;
        return total == 0 ? 0.0 : (double) busyCycles / total;
    }
}