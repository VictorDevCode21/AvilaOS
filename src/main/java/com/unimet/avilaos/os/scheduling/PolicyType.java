package com.unimet.avilaos.os.scheduling;

/**
 * Políticas de planificación disponibles (enum exigido por el enunciado).
 * La etiqueta coincide con los textos del selector de la GUI.
 *
 * @author donatovodola
 */
public enum PolicyType {

    FCFS("FCFS"),
    ROUND_ROBIN("Round Robin"),
    EDF("EDF"),
    PREEMPTIVE_PRIORITY("Prioridades apropiativas");

    private final String label;

    PolicyType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Crea una instancia nueva de la política (cada computador tiene la suya). */
    public SchedulingPolicy newPolicy() {
        return switch (this) {
            case FCFS -> new FcfsPolicy();
            case ROUND_ROBIN -> new RoundRobinPolicy();
            case EDF -> new EdfPolicy();
            case PREEMPTIVE_PRIORITY -> new PreemptivePriorityPolicy();
        };
    }

    /** Busca por etiqueta (lo que devuelve el selector de la GUI o el archivo de configuración). */
    public static PolicyType fromLabel(String label) {
        for (PolicyType type : values()) {
            if (type.label.equalsIgnoreCase(label == null ? "" : label.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Política desconocida: " + label);
    }

    /** Etiquetas en orden, para llenar el selector de la GUI. */
    public static String[] labels() {
        PolicyType[] all = values();
        String[] result = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            result[i] = all[i].label;
        }
        return result;
    }
}
