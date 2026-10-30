package com.unimet.avilaos.config;

import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.process.ProcessType;

/**
 * Un proceso con todos sus atributos (lo pide el enunciado). Sirve igual para la
 * carga inicial del CSV y para los procesos que crea el usuario en la Vista 1.
 * NO es un PCB: es la "receta". El PCB se crea con toPcb, que es cuando se le
 * asigna un ID único.
 *
 * Los límites están aquí (no en la GUI) para que el CSV y el formulario apliquen
 * exactamente las mismas reglas. La memoria máxima depende de la RAM de cada
 * computador, así que esa la valida SimulationConfig.
 *
 * Los campos que no aplican a su tipo se guardan en 0 (o null el buffer).
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

    public static final int NAME_MAX_LENGTH = 30;
    public static final int MAX_PRIORITY = 99;
    public static final int MAX_INSTRUCTIONS = 100_000;
    public static final long MAX_DEADLINE = 1_000_000;
    public static final int MAX_IO_DURATION = 1_000;
    public static final int MAX_OPERATION_INTERVAL = 1_000;
    public static final int MAX_ITEMS = 10_000;

    /**
     * Operaciones de buffer por elemento, cada una cuesta 1 instrucción:
     * productor = wait vacíos, wait mutex, insertar, signal mutex, signal lleno;
     * consumidor = wait lleno, wait mutex, extraer, signal mutex, signal vacíos.
     */
    public static final int BUFFER_OPERATIONS_PER_ITEM = 5;

    public ProcessConfig {
        name = ConfigText.requireName(name, "El proceso", NAME_MAX_LENGTH);
        if (type == null) {
            throw new IllegalArgumentException("El proceso " + name + " necesita un tipo");
        }
        ConfigText.requireRange(priority, 0, MAX_PRIORITY, "La prioridad de " + name);
        if (memorySize <= 0) {
            throw new IllegalArgumentException("La memoria de " + name + " debe ser mayor que 0");
        }
        ConfigText.requireRange(instructions, 1, MAX_INSTRUCTIONS, "Las instrucciones de " + name);
        ConfigText.requireRange(deadline, 1, MAX_DEADLINE, "El deadline de " + name);
        if (computerId < 0) {
            throw new IllegalArgumentException("El computador de " + name + " debe ser 0 (automático) o 1 o mayor");
        }
        if (type == ProcessType.IO_BOUND) {
            ConfigText.requireRange(ioInterval, 1, instructions, "El intervalo de E/S de " + name
                    + " (no puede pasar de sus instrucciones)");
            ConfigText.requireRange(ioDuration, 1, MAX_IO_DURATION, "La duración de E/S de " + name);
        } else {
            ioInterval = 0;
            ioDuration = 0;
        }
        if (type.usesBuffer()) {
            bufferName = ConfigText.requireCode(bufferName, "El buffer de " + name, BufferConfig.NAME_MAX_LENGTH);
            ConfigText.requireRange(operationInterval, 1, MAX_OPERATION_INTERVAL, "El intervalo de operación de " + name);
            ConfigText.requireRange(itemsRequired, 1, MAX_ITEMS, "Los elementos requeridos de " + name);
            // D3: termina al completar sus elementos O al agotar sus instrucciones. Si no le
            // alcanzan, terminaría "a tiempo" sin haber hecho su trabajo: se rechaza aquí.
            long minimum = minimumBufferInstructions(operationInterval, itemsRequired);
            if (instructions < minimum) {
                throw new IllegalArgumentException("Las instrucciones de " + name + " (" + instructions
                        + ") no alcanzan para " + itemsRequired + " elementos: necesita al menos " + minimum
                        + " (" + itemsRequired + " × (" + operationInterval + " + " + BUFFER_OPERATIONS_PER_ITEM + "))");
            }
        } else {
            bufferName = null;
            operationInterval = 0;
            itemsRequired = 0;
        }
    }

    /**
     * Instrucciones mínimas de un productor/consumidor para completar sus elementos:
     * cada elemento son operationInterval instrucciones de usuario más las 5 del buffer.
     * En long para que no se desborde con valores grandes.
     */
    public static long minimumBufferInstructions(int operationInterval, int itemsRequired) {
        return (long) itemsRequired * ((long) operationInterval + BUFFER_OPERATIONS_PER_ITEM);
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
