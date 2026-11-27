package com.unimet.avilaos.os.process;

/**
 * Estados de un proceso en ÁvilaOS: modelo de 7 estados (Stallings, cap. 3, figura 3.9b).
 * Un solo BLOCKED; el motivo concreto se guarda aparte en BlockReason (un proceso
 * suspendido conserva el suyo).
 *
 * Proyecto 2: READY_SUSPENDED y BLOCKED_SUSPENDED son procesos que están en el área de
 * intercambio del disco, no en la RAM. Transiciones que usa ÁvilaOS:
 * <ul>
 *   <li>Bloqueado → Bloqueado/Suspendido: el planificador a mediano plazo lo suspende
 *       para admitir a un Nuevo que no cabe;</li>
 *   <li>Bloqueado/Suspendido → Listo/Suspendido: ocurre su evento (termina su E/S);</li>
 *   <li>Listo/Suspendido → Listo: se reactiva (vuelve a la RAM).</li>
 * </ul>
 * Stallings también muestra Listo → Listo/Suspendido, Nuevo → Listo/Suspendido y
 * Bloqueado/Suspendido → Bloqueado; ÁvilaOS no los usa (solo suspende Bloqueados y solo
 * reactiva Listos/Suspendidos), así que no se permiten.
 *
 * @author donatovodola
 * @author NicollPinzon
 */
public enum ProcessState {

    NEW("Nuevo"),
    READY("Listo"),
    RUNNING("Ejecución"),
    BLOCKED("Bloqueado"),
    READY_SUSPENDED("Listo/Suspendido"),
    BLOCKED_SUSPENDED("Bloqueado/Suspendido"),
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
     * Transiciones válidas del diagrama de 7 estados.
     * Cualquier estado no terminado puede ir a TERMINATED: un proceso puede ser eliminado
     * por vencimiento de su deadline en cualquier cola, también estando suspendido.
     */
    public boolean canTransitionTo(ProcessState next) {
        if (next == TERMINATED) {
            return this != TERMINATED;
        }
        return switch (this) {
            case NEW -> next == READY;                         // admisión (hay RAM)
            case READY -> next == RUNNING;                     // despacho
            case RUNNING -> next == READY || next == BLOCKED;  // expropiación / espera de evento
            case BLOCKED -> next == READY                      // ocurre el evento
                    || next == BLOCKED_SUSPENDED;              // se suspende (mediano plazo)
            case BLOCKED_SUSPENDED -> next == READY_SUSPENDED; // ocurre el evento estando en el swap
            case READY_SUSPENDED -> next == READY;             // se reactiva (vuelve a la RAM)
            case TERMINATED -> false;
        };
    }
}
