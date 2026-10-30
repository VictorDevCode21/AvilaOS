package com.unimet.avilaos.os;

import com.unimet.avilaos.os.process.ProcessType;

/**
 * Próximo paso del "programa" de un proceso (lo que hará cuando tenga la CPU).
 *
 * El kernel lo usa para saber qué ejecuta la CPU en cada ciclo:
 *  - USER: una instrucción de usuario (calcular, producir o consumir).
 *  - IO_REQUEST: llamada al sistema para pedir E/S (1 ciclo de trabajo del SO).
 *  - Las operaciones del productor-consumidor (anexo del enunciado), cada una
 *    una instrucción en modo sistema operativo:
 *      productor:  WAIT_EMPTY → WAIT_MUTEX → INSERT  → SIGNAL_MUTEX → SIGNAL_FULL
 *      consumidor: WAIT_FULL  → WAIT_MUTEX → EXTRACT → SIGNAL_MUTEX → SIGNAL_EMPTY
 *
 * @author victor1102
 */
public enum ExecutionStep {

    USER("instrucción de usuario"),
    IO_REQUEST("solicitud de E/S"),
    WAIT_EMPTY("semWait('vacíos')"),
    WAIT_FULL("semWait('lleno')"),
    WAIT_MUTEX("semWait('mutex')"),
    INSERT("insertar en el buffer"),
    EXTRACT("extraer del buffer"),
    SIGNAL_MUTEX("semSignal('mutex')"),
    SIGNAL_FULL("semSignal('lleno')"),
    SIGNAL_EMPTY("semSignal('vacíos')");

    private final String label;

    ExecutionStep(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** true si es una operación sobre un buffer (paga latencia si el buffer es remoto). */
    public boolean isBufferOperation() {
        return this != USER && this != IO_REQUEST;
    }

    /** true si es un semWait (puede bloquear al proceso). */
    public boolean isWait() {
        return this == WAIT_EMPTY || this == WAIT_FULL || this == WAIT_MUTEX;
    }

    /** true si es un semSignal (puede despertar a otro proceso). */
    public boolean isSignal() {
        return this == SIGNAL_MUTEX || this == SIGNAL_FULL || this == SIGNAL_EMPTY;
    }

    /** Paso siguiente dentro de la secuencia del productor o del consumidor. */
    public ExecutionStep next(ProcessType type) {
        boolean producer = type == ProcessType.PRODUCER;
        return switch (this) {
            case WAIT_EMPTY, WAIT_FULL -> WAIT_MUTEX;
            case WAIT_MUTEX -> producer ? INSERT : EXTRACT;
            case INSERT, EXTRACT -> SIGNAL_MUTEX;
            case SIGNAL_MUTEX -> producer ? SIGNAL_FULL : SIGNAL_EMPTY;
            case SIGNAL_FULL, SIGNAL_EMPTY, USER, IO_REQUEST -> USER;
        };
    }
}