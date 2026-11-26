package com.unimet.avilaos.os.disk;

/**
 * Políticas de planificación de disco disponibles (enum exigido por el
 * enunciado). Las cuatro obligatorias más LOOK y C-LOOK. La etiqueta coincide
 * con los textos del selector de la GUI y con el JSON de configuración.
 *
 * @author NicollPinzon
 */
public enum DiskPolicyType {

    FIFO("FIFO"),
    SSTF("SSTF"),
    SCAN("SCAN"),
    C_SCAN("C-SCAN"),
    LOOK("LOOK"),
    C_LOOK("C-LOOK");

    private final String label;

    DiskPolicyType(String label) {
        this.label = label;
    }

    /** Texto para la GUI, el log y la configuración. */
    public String getLabel() {
        return label;
    }

    /**
     * Busca por etiqueta ("C-SCAN") o por nombre del enum ("C_SCAN"), sin
     * importar mayúsculas: sirve para el selector de la GUI y para el JSON.
     */
    public static DiskPolicyType fromLabel(String label) {
        String text = label == null ? "" : label.trim();
        for (DiskPolicyType type : values()) {
            if (type.label.equalsIgnoreCase(text) || type.name().equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Política de disco desconocida: " + label);
    }

    /** Etiquetas en orden, para llenar el selector de la GUI. */
    public static String[] labels() {
        DiskPolicyType[] all = values();
        String[] result = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            result[i] = all[i].label;
        }
        return result;
    }
}
