package com.unimet.avilaos.config;

import com.unimet.avilaos.datastructures.LinkedList;

/**
 * Un valor de un JSON ya leído: objeto, lista, texto, número, true/false o null.
 * Lo arma JsonParser; los lectores del sistema de archivos y de los procesos lo
 * recorren con los métodos de abajo, que validan el tipo y explican el error con
 * la línea y la columna del archivo.
 *
 * Inmutable. Los objetos guardan sus campos en el orden del archivo y no admiten
 * claves repetidas (lo revisa el parser).
 *
 * @author NicollPinzon
 */
public final class JsonValue {

    /** Tipos de valor de JSON. */
    public enum Kind {
        OBJECT("un objeto"),
        ARRAY("una lista"),
        STRING("un texto"),
        NUMBER("un número"),
        BOOLEAN("true o false"),
        NULL("null");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** Un campo de un objeto: "clave": valor. */
    public record Member(String key, JsonValue value) {
    }

    private final Kind kind;
    private final int line;
    private final int column;
    private final String text;                       // STRING: el texto; NUMBER: como venía escrito
    private final boolean bool;
    private final LinkedList<Member> members;        // OBJECT
    private final LinkedList<JsonValue> items;       // ARRAY

    private JsonValue(Kind kind, int line, int column, String text, boolean bool, LinkedList<Member> members,
            LinkedList<JsonValue> items) {
        this.kind = kind;
        this.line = line;
        this.column = column;
        this.text = text;
        this.bool = bool;
        this.members = members;
        this.items = items;
    }

    // ---------- Fábricas (las usa JsonParser) ----------

    static JsonValue object(LinkedList<Member> members, int line, int column) {
        return new JsonValue(Kind.OBJECT, line, column, null, false, members, null);
    }

    static JsonValue array(LinkedList<JsonValue> items, int line, int column) {
        return new JsonValue(Kind.ARRAY, line, column, null, false, null, items);
    }

    static JsonValue string(String text, int line, int column) {
        return new JsonValue(Kind.STRING, line, column, text, false, null, null);
    }

    static JsonValue number(String literal, int line, int column) {
        return new JsonValue(Kind.NUMBER, line, column, literal, false, null, null);
    }

    static JsonValue bool(boolean value, int line, int column) {
        return new JsonValue(Kind.BOOLEAN, line, column, null, value, null, null);
    }

    static JsonValue nullValue(int line, int column) {
        return new JsonValue(Kind.NULL, line, column, null, false, null, null);
    }

    // ---------- Consultas ----------

    public Kind getKind() {
        return kind;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    /** "Línea 12, columna 5" (para los mensajes de error). */
    public String position() {
        return "Línea " + line + ", columna " + column;
    }

    /** Un error con la posición de este valor delante. */
    public IllegalArgumentException error(String message) {
        return new IllegalArgumentException(position() + ": " + message);
    }

    /**
     * Revisa que sea un objeto.
     *
     * @param what qué se esperaba, para el mensaje (por ejemplo "\"diskConfig\"")
     */
    public JsonValue requireObject(String what) {
        return require(Kind.OBJECT, what);
    }

    /** Revisa que sea una lista. */
    public JsonValue requireArray(String what) {
        return require(Kind.ARRAY, what);
    }

    /** true si es un objeto y tiene esa clave. */
    public boolean has(String key) {
        return find(key) != null;
    }

    /** El valor de esa clave, o null si no está (o si no es un objeto). */
    public JsonValue find(String key) {
        if (kind != Kind.OBJECT) {
            return null;
        }
        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            if (m.key().equals(key)) {
                return m.value();
            }
        }
        return null;
    }

    /**
     * El valor obligatorio de esa clave.
     *
     * @param where de qué objeto se trata, para el mensaje (por ejemplo "la entrada 3")
     * @throws IllegalArgumentException si falta
     */
    public JsonValue get(String key, String where) {
        JsonValue value = find(key);
        if (value == null) {
            throw error("falta \"" + key + "\" en " + where);
        }
        return value;
    }

    /** Copia de los campos de un objeto, en el orden del archivo. */
    public LinkedList<Member> getMembers() {
        require(Kind.OBJECT, "un objeto");
        return members.copy();
    }

    /** Copia de los elementos de una lista, en orden. */
    public LinkedList<JsonValue> getItems() {
        require(Kind.ARRAY, "una lista");
        return items.copy();
    }

    /**
     * Revisa que el objeto no tenga claves fuera de las permitidas, para que un
     * error de tipeo ("startblock") no se ignore en silencio.
     */
    public void requireOnlyKeys(String where, String... allowed) {
        LinkedList<Member> all = getMembers();
        while (!all.isEmpty()) {
            Member m = all.removeFirst();
            boolean known = false;
            for (String key : allowed) {
                known |= key.equals(m.key());
            }
            if (!known) {
                throw m.value().error("\"" + m.key() + "\" no es una clave válida en " + where
                        + " (se aceptan: " + String.join(", ", allowed) + ")");
            }
        }
    }

    /**
     * El texto de un STRING.
     *
     * @param what nombre de la clave, para el mensaje
     */
    public String asString(String what) {
        require(Kind.STRING, what);
        return text;
    }

    /** El valor de un BOOLEAN. */
    public boolean asBoolean(String what) {
        require(Kind.BOOLEAN, what);
        return bool;
    }

    /**
     * Un número entero (sin decimales ni exponente) que quepa en un int.
     *
     * @param what nombre de la clave, para el mensaje
     */
    public int asInt(String what) {
        long value = asLong(what);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw error(what + " es demasiado grande: " + text);
        }
        return (int) value;
    }

    /** Un número entero (sin decimales ni exponente) que quepa en un long. */
    public long asLong(String what) {
        require(Kind.NUMBER, what);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == 'e' || c == 'E') {
                throw error(what + " debe ser un número entero, no " + text);
            }
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw error(what + " es demasiado grande: " + text);
        }
    }

    private JsonValue require(Kind expected, String what) {
        if (kind != expected) {
            throw error(what + " debe ser " + expected.getLabel() + ", no " + kind.getLabel());
        }
        return this;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case STRING -> "\"" + text + "\"";
            case NUMBER -> text;
            case BOOLEAN -> String.valueOf(bool);
            case NULL -> "null";
            case OBJECT -> "{" + members.size() + " campos}";
            case ARRAY -> "[" + items.size() + " elementos]";
        };
    }
}
