package com.unimet.avilaos.os.process;

/**
 * Estados de un proceso en ÁvilaOS (modelo de 5 estados, Stallings cap. 3).
 * Un solo BLOCKED; el motivo concreto se guarda aparte en BlockReason.
 *
 * Proyecto 2: aquí se agregarán READY_SUSPENDED y BLOCKED_SUSPENDED
 * sin tocar lo existente.
 *
 * @author donatovodola
 */
public enum ProcessState {

    NEW("Nuevo"),
    READY("Listo"),
    RUNNING("Ejecución"),
    BLOCKED("Bloqueado"),
    TERMINATED("Terminado");

    private final String label;

    ProcessState(String label) {
        this.label = label;
    }

    /** Texto en español para la GUI y el log. */
    public String getLabel() {
        return label;
    }

    /**
     * Transiciones válidas del diagrama de 5 estados.
     * Cualquier estado no terminado puede ir a TERMINATED: un proceso puede
     * ser eliminado por vencimiento de su deadline estando en Nuevo, Listo o Bloqueado.
     */
    public boolean canTransitionTo(ProcessState next) {
        if (next == TERMINATED) {
            return this != TERMINATED;
        }
        return switch (this) {
            case NEW -> next == READY;                         // admisión (hay RAM)
            case READY -> next == RUNNING;                     // despacho
            case RUNNING -> next == READY || next == BLOCKED;  // expropiación / espera de evento
            case BLOCKED -> next == READY;                     // ocurre el evento
            case TERMINATED -> false;
        };
    }
}
