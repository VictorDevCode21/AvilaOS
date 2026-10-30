package com.unimet.avilaos.gui;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Valida los campos de un formulario y junta TODOS los errores en un solo mensaje.
 *
 * Nunca lanza excepciones hacia afuera: un dato inválido marca el campo en rojo
 * y se agrega a la lista de errores, así el simulador sigue funcionando
 * (lo exige el enunciado: "gestionar entradas inválidas sin interrumpir el flujo").
 *
 * @author NicollPinzon
 */
public class FormValidator {

    private static final Border ERROR_BORDER = BorderFactory.createLineBorder(Color.RED, 2);
    private static final String ORIGINAL_BORDER = "avilaos.originalBorder";

    private final StringBuilder errors = new StringBuilder();
    private int errorCount;

    /**
     * Valida un texto obligatorio con largo máximo.
     *
     * @return el texto sin espacios a los lados, o null si es inválido
     */
    public String text(JTextField field, String label, int maxLength) {
        String value = field.getText().trim();
        if (value.isEmpty()) {
            return fail(field, label + ": no puede estar vacío.");
        }
        if (value.length() > maxLength) {
            return fail(field, label + ": máximo " + maxLength + " caracteres.");
        }
        if (value.indexOf(',') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            // la configuración se guarda en CSV: una coma rompería las columnas
            return fail(field, label + ": no puede contener comas.");
        }
        ok(field);
        return value;
    }

    /**
     * Valida un texto con solo letras y números (sin espacios), ej. el nombre de un buffer.
     *
     * @return el texto, o null si es inválido
     */
    public String code(JTextField field, String label, int maxLength) {
        String value = text(field, label, maxLength);
        if (value == null) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isLetterOrDigit(value.charAt(i))) {
                return fail(field, label + ": solo letras y números, sin espacios ni símbolos.");
            }
        }
        return value;
    }

    /**
     * Valida un número entero dentro de un rango [min, max].
     *
     * @return el número, o -1 si es inválido
     */
    public int integer(JTextField field, String label, int min, int max) {
        String raw = field.getText().trim();
        if (raw.isEmpty()) {
            fail(field, label + ": no puede estar vacío.");
            return -1;
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            fail(field, label + ": debe ser un número entero (escribiste \"" + raw + "\").");
            return -1;
        }
        if (value < min || value > max) {
            fail(field, label + ": debe estar entre " + min + " y " + max + ".");
            return -1;
        }
        ok(field);
        return value;
    }

    /** Agrega un error que no corresponde a un solo campo (ej. "no hay buffers creados"). */
    public void addError(String message) {
        append(message);
    }

    /** Agrega un error y marca el campo en rojo (ej. un nombre repetido). */
    public void addError(JTextField field, String message) {
        fail(field, message);
    }

    public boolean hasErrors() {
        return errorCount > 0;
    }

    /** Todos los errores, uno por línea, listos para mostrar al usuario. */
    public String getMessage() {
        return errors.toString();
    }

    /**
     * Quita el borde rojo de cada campo en cuanto el usuario lo vuelve a editar,
     * para que vea qué le falta corregir.
     */
    public static void clearErrorOnEdit(JTextField... fields) {
        for (JTextField field : fields) {
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    ok(field);
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    ok(field);
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    ok(field);
                }
            });
        }
    }

    // ---------- Internos ----------

    private String fail(JTextField field, String message) {
        if (field.getClientProperty(ORIGINAL_BORDER) == null) {
            field.putClientProperty(ORIGINAL_BORDER, field.getBorder());   // para poder restaurarlo
        }
        field.setBorder(ERROR_BORDER);
        append(message);
        return null;
    }

    private void append(String message) {
        errorCount++;
        errors.append("• ").append(message).append('\n');
    }

    private static void ok(JTextField field) {
        Object original = field.getClientProperty(ORIGINAL_BORDER);
        if (original instanceof Border border) {
            field.setBorder(border);
        }
    }
}
