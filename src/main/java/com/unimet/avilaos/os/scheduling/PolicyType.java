package com.unimet.avilaos.os.scheduling;

/**
 * Políticas de planificación disponibles (enum exigido por el enunciado).
 * La etiqueta coincide con los textos del selector de la GUI.
 *
 * Cada constante crea su propia política (fábrica por constante) y describe
 * cómo decide, sin ningún switch sobre el enum. Así se corrige lo que señalaron
 * los evaluadores del Proyecto 1: agregar una política es crear su clase y una
 * constante aquí; el Scheduler, el Kernel y la GUI no se tocan. Es el mismo
 * diseño que DiskPolicyType en el disco.
 *
 * Los textos (criterio y expropiación) son los de la tabla de ayuda de la
 * ventana Planificación; usesQuantum() le dice a la GUI y a la configuración si
 * la política usa el quantum, y debe coincidir con lo que hace isQuantumExpired()
 * en la clase de la política.
 *
 * @author donatovodola
 * @author NicollPinzon
 */
public enum PolicyType {

    FCFS("FCFS", null, "el que llegó antes a listos", "No", false) {
        @Override
        public SchedulingPolicy newPolicy() {
            return new FcfsPolicy();
        }
    },
    ROUND_ROBIN("Round Robin", "RR", "el que llegó antes, por turnos de 'quantum' ciclos",
            "Sí, al agotar el quantum", true) {
        @Override
        public SchedulingPolicy newPolicy() {
            return new RoundRobinPolicy();
        }
    },
    EDF("EDF", null, "el de deadline más cercano", "Sí, si llega un deadline menor", false) {
        @Override
        public SchedulingPolicy newPolicy() {
            return new EdfPolicy();
        }
    },
    PREEMPTIVE_PRIORITY("Prioridades apropiativas", null, "el de mayor prioridad (número menor)",
            "Sí, si llega más prioridad", false) {
        @Override
        public SchedulingPolicy newPolicy() {
            return new PreemptivePriorityPolicy();
        }
    };

    private final String label;
    private final String alias;          // abreviatura que también se acepta al leer (por ejemplo "RR"), o null
    private final String criterion;
    private final String preemption;
    private final boolean quantum;

    PolicyType(String label, String alias, String criterion, String preemption, boolean quantum) {
        this.label = label;
        this.alias = alias;
        this.criterion = criterion;
        this.preemption = preemption;
        this.quantum = quantum;
    }

    public String getLabel() {
        return label;
    }

    /** A quién elige primero (columna "Elige primero a…" de la ventana Planificación). */
    public String getCriterion() {
        return criterion;
    }

    /** Si es apropiativa y cuándo expropia (columna "Apropiativa"). */
    public String getPreemption() {
        return preemption;
    }

    /** true si la política usa el quantum (hoy solo Round Robin). */
    public boolean usesQuantum() {
        return quantum;
    }

    /** Crea una instancia nueva de la política (cada computador tiene la suya). */
    public abstract SchedulingPolicy newPolicy();

    /**
     * Busca por etiqueta ("Round Robin"), por nombre del enum ("ROUND_ROBIN") o por
     * su abreviatura ("RR"), sin importar mayúsculas: sirve para el selector de la GUI
     * y para los archivos de configuración y el JSON de procesos escritos con cualquiera.
     *
     * @throws IllegalArgumentException si no es ninguna; el mensaje lista los valores válidos
     */
    public static PolicyType fromLabel(String label) {
        String text = label == null ? "" : label.trim();
        for (PolicyType type : values()) {
            if (type.label.equalsIgnoreCase(text) || type.name().equalsIgnoreCase(text)
                    || (type.alias != null && type.alias.equalsIgnoreCase(text))) {
                return type;
            }
        }
        throw new IllegalArgumentException("Política desconocida: " + label + " (se aceptan: " + accepted() + ")");
    }

    /** "FCFS, Round Robin (RR), EDF, Prioridades apropiativas": para los mensajes de error. */
    public static String accepted() {
        StringBuilder sb = new StringBuilder();
        for (PolicyType type : values()) {
            sb.append(sb.length() == 0 ? "" : ", ").append(type.label);
            if (type.alias != null) {
                sb.append(" (").append(type.alias).append(')');
            }
        }
        return sb.toString();
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
