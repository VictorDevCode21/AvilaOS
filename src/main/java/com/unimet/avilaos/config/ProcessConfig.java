package com.unimet.avilaos.config;

import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.process.ProcessType;

/**
 * Un proceso de la carga inicial, con todos sus atributos (lo pide el enunciado).
 * NO es un PCB: es la "receta". El PCB se crea al iniciar la simulación con
 * toPcb, que es cuando se le asigna un ID único.
 *
 * Los campos que no aplican a su tipo se ignoran (ej. bufferName en un CPU bound).
 *
 * @param computerId 0 = asignación automática (la decide ÁvilaOS); 1 o más = manual
 *
 * @author donatovodola
 */
public record ProcessConfig(String name, ProcessType type, int priority, int memorySize,
        int instructions, long deadline, int computerId,
        int ioInterval, int ioDuration,
        String bufferName, int operationInterval, int itemsRequired) {

    public static final int AUTOMATIC = 0;

    public ProcessConfig {
        name = ConfigText.requireName(name, "El proceso");
        if (type == null) {
            throw new IllegalArgumentException("El proceso " + name + " necesita un tipo");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("La prioridad de " + name + " no puede ser negativa");
        }
        if (memorySize <= 0) {
            throw new IllegalArgumentException("La memoria de " + name + " debe ser mayor que 0");
        }
        if (instructions <= 0) {
            throw new IllegalArgumentException("Las instrucciones de " + name + " deben ser mayores que 0");
        }
        if (deadline <= 0) {
            throw new IllegalArgumentException("El deadline de " + name + " debe ser mayor que 0 ciclos");
        }
        if (computerId < 0) {
            throw new IllegalArgumentException("El computador de " + name + " debe ser 0 (automático) o 1 o mayor");
        }
        if (type == ProcessType.IO_BOUND && (ioInterval <= 0 || ioDuration <= 0)) {
            throw new IllegalArgumentException("El I/O bound " + name + " necesita intervalo y duración de E/S mayores que 0");
        }
        if (type.usesBuffer()) {
            bufferName = ConfigText.requireName(bufferName, "El buffer de " + name);
            if (operationInterval <= 0 || itemsRequired <= 0) {
                throw new IllegalArgumentException(name + " necesita intervalo y elementos requeridos mayores que 0");
            }
        } else {
            bufferName = null;
        }
    }

    public boolean isAutomatic() {
        return computerId == AUTOMATIC;
    }

    /**
     * Crea el PCB real. Se llama al iniciar la simulación, ya con el computador decidido.
     *
     * @param assignedComputer computador final (el de la config, o el que eligió el balanceo)
     * @param arrivalCycle     ciclo en que entra al sistema
     */
    public Pcb toPcb(int assignedComputer, long arrivalCycle) {
        return switch (type) {
            case CPU_BOUND -> Pcb.cpuBound(name, priority, memorySize, instructions, deadline,
                    assignedComputer, arrivalCycle);
            case IO_BOUND -> Pcb.ioBound(name, priority, memorySize, instructions, deadline,
                    assignedComputer, arrivalCycle, ioInterval, ioDuration);
            case PRODUCER, CONSUMER -> Pcb.bufferUser(name, type, priority, memorySize, instructions,
                    deadline, assignedComputer, arrivalCycle, bufferName, operationInterval, itemsRequired);
        };
    }
}
