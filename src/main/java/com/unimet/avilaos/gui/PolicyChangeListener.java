package com.unimet.avilaos.gui;

/**
 * Aviso de que el USUARIO cambió la política de un computador en su selector.
 * Interfaz propia en vez de java.util.function.Consumer (no usamos java.util).
 *
 * @author NicollPinzon
 */
@FunctionalInterface
public interface PolicyChangeListener {

    /**
     * @param label etiqueta elegida (PolicyType.getLabel()); el clúster la convierte
     *              con PolicyType.fromLabel(...)
     */
    void policyChanged(String label);
}
