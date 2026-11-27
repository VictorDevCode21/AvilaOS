package com.unimet.avilaos.config;

import com.unimet.avilaos.datastructures.LinkedList;

/**
 * Lector de JSON hecho a mano (sin librerías ni colecciones de java.util), igual
 * que el lector del CSV del Proyecto 1. Convierte el texto en un árbol de JsonValue.
 *
 * Es un parser descendente recursivo: cada método lee un tipo de valor y avanza
 * por el texto. Acepta el JSON estándar (RFC 8259): objetos, listas, textos con
 * escapes (\" \\ \/ \b \f \n \r \t \\uXXXX), números, true, false y null, con
 * espacios y saltos de línea en cualquier parte. Ignora el BOM que agrega el Bloc
 * de notas de Windows.
 *
 * Cada error dice la línea y la columna y qué se esperaba, por ejemplo
 * "Línea 12, columna 5: se esperaba una coma o '}'".
 *
 * Uso: {@code JsonValue root = JsonParser.parse(texto);}
 *
 * @author NicollPinzon
 */
public final class JsonParser {

    /** Profundidad máxima de objetos y listas anidados (evita desbordar la pila). */
    private static final int MAX_DEPTH = 64;

    private final String text;
    private int pos;
    private int line = 1;
    private int column = 1;

    private JsonParser(String text) {
        this.text = text;
    }

    /**
     * Lee un documento JSON completo.
     *
     * @throws IllegalArgumentException si el texto no es un JSON válido (con línea y columna)
     */
    public static JsonValue parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("No hay texto para leer");
        }
        JsonParser parser = new JsonParser(text);
        if (text.startsWith("﻿")) {
            parser.pos = 1;                          // BOM del Bloc de notas
        }
        parser.skipSpaces();
        if (parser.atEnd()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        JsonValue value = parser.readValue(0);
        parser.skipSpaces();
        if (!parser.atEnd()) {
            throw parser.error("sobra texto después del final del JSON");
        }
        return value;
    }

    // ---------- Valores ----------

    private JsonValue readValue(int depth) {
        if (atEnd()) {
            throw error("el archivo termina antes de tiempo: falta un valor");
        }
        char c = peek();
        return switch (c) {
            case '{' -> readObject(depth + 1);
            case '[' -> readArray(depth + 1);
            case '"' -> {
                int l = line;
                int col = column;
                yield JsonValue.string(readString(), l, col);
            }
            case 't' -> readWord("true", JsonValue.bool(true, line, column));
            case 'f' -> readWord("false", JsonValue.bool(false, line, column));
            case 'n' -> readWord("null", JsonValue.nullValue(line, column));
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield readNumber();
                }
                throw error("se esperaba un valor (objeto, lista, texto, número, true, false o null), no '"
                        + printable(c) + "'");
            }
        };
    }

    private JsonValue readObject(int depth) {
        checkDepth(depth);
        int l = line;
        int col = column;
        advance();                                   // {
        LinkedList<JsonValue.Member> members = new LinkedList<>();
        skipSpaces();
        if (!atEnd() && peek() == '}') {
            advance();
            return JsonValue.object(members, l, col);
        }
        while (true) {
            skipSpaces();
            if (atEnd() || peek() != '"') {
                throw error(atEnd() ? "el archivo termina dentro de un objeto: falta '}'"
                        : "se esperaba el nombre de un campo entre comillas");
            }
            int keyLine = line;
            int keyColumn = column;
            String key = readString();
            for (int i = 0; i < members.size(); i++) {
                if (members.get(i).key().equals(key)) {
                    throw new IllegalArgumentException("Línea " + keyLine + ", columna " + keyColumn
                            + ": el campo \"" + key + "\" está repetido");
                }
            }
            skipSpaces();
            expect(':', "se esperaba ':' después de \"" + key + "\"");
            skipSpaces();
            members.addLast(new JsonValue.Member(key, readValue(depth)));
            skipSpaces();
            if (atEnd()) {
                throw error("el archivo termina dentro de un objeto: falta '}'");
            }
            char c = peek();
            advance();
            if (c == '}') {
                return JsonValue.object(members, l, col);
            }
            if (c != ',') {
                throw errorBack("se esperaba una coma o '}' después del valor de \"" + key + "\"");
            }
        }
    }

    private JsonValue readArray(int depth) {
        checkDepth(depth);
        int l = line;
        int col = column;
        advance();                                   // [
        LinkedList<JsonValue> items = new LinkedList<>();
        skipSpaces();
        if (!atEnd() && peek() == ']') {
            advance();
            return JsonValue.array(items, l, col);
        }
        while (true) {
            skipSpaces();
            items.addLast(readValue(depth));
            skipSpaces();
            if (atEnd()) {
                throw error("el archivo termina dentro de una lista: falta ']'");
            }
            char c = peek();
            advance();
            if (c == ']') {
                return JsonValue.array(items, l, col);
            }
            if (c != ',') {
                throw errorBack("se esperaba una coma o ']' entre los elementos de la lista");
            }
        }
    }

    /** Lee "…" (el cursor está en la comilla de apertura) y devuelve el texto sin escapes. */
    private String readString() {
        advance();                                   // "
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (atEnd()) {
                throw error("el archivo termina dentro de un texto: falta la comilla de cierre");
            }
            char c = peek();
            if (c == '"') {
                advance();
                return sb.toString();
            }
            if (c == '\n' || c == '\r') {
                throw error("un texto no puede tener un salto de línea: falta la comilla de cierre");
            }
            if (c < 0x20) {
                throw error("un texto no puede tener caracteres de control (usa \\n, \\t…)");
            }
            if (c != '\\') {
                sb.append(c);
                advance();
                continue;
            }
            advance();                               // barra invertida
            if (atEnd()) {
                throw error("el archivo termina dentro de un texto");
            }
            char e = peek();
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    int code = 0;
                    for (int i = 0; i < 4; i++) {
                        advance();
                        if (atEnd()) {
                            throw error("\\u necesita 4 dígitos hexadecimales");
                        }
                        int digit = Character.digit(peek(), 16);
                        if (digit < 0) {
                            throw error("\\u necesita 4 dígitos hexadecimales, no '" + printable(peek()) + "'");
                        }
                        code = code * 16 + digit;
                    }
                    sb.append((char) code);
                }
                default -> throw error("escape desconocido \\" + printable(e));
            }
            advance();
        }
    }

    /** Número según JSON: -?(0|[1-9][0-9]*)(.[0-9]+)?([eE][+-]?[0-9]+)? */
    private JsonValue readNumber() {
        int l = line;
        int col = column;
        int start = pos;
        if (peek() == '-') {
            advance();
        }
        if (atEnd() || !isDigit(peek())) {
            throw error("se esperaba un dígito");
        }
        if (peek() == '0') {
            advance();
            if (!atEnd() && isDigit(peek())) {
                throw error("un número no puede empezar con 0 (salvo el 0 mismo)");
            }
        } else {
            digits();
        }
        if (!atEnd() && peek() == '.') {
            advance();
            if (atEnd() || !isDigit(peek())) {
                throw error("se esperaba un dígito después del punto decimal");
            }
            digits();
        }
        if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
            advance();
            if (!atEnd() && (peek() == '+' || peek() == '-')) {
                advance();
            }
            if (atEnd() || !isDigit(peek())) {
                throw error("se esperaba un dígito en el exponente");
            }
            digits();
        }
        return JsonValue.number(text.substring(start, pos), l, col);
    }

    private JsonValue readWord(String word, JsonValue value) {
        if (!text.startsWith(word, pos)) {
            throw error("se esperaba " + word);
        }
        for (int i = 0; i < word.length(); i++) {
            advance();
        }
        return value;
    }

    // ---------- Cursor ----------

    private void digits() {
        while (!atEnd() && isDigit(peek())) {
            advance();
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void skipSpaces() {
        while (!atEnd()) {
            char c = peek();
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            advance();
        }
    }

    private void expect(char c, String message) {
        if (atEnd() || peek() != c) {
            throw error(message);
        }
        advance();
    }

    private boolean atEnd() {
        return pos >= text.length();
    }

    private char peek() {
        return text.charAt(pos);
    }

    /** Avanza un carácter llevando la cuenta de línea y columna (\r\n cuenta como un salto). */
    private void advance() {
        char c = text.charAt(pos++);
        if (c == '\n') {
            line++;
            column = 1;
        } else if (c != '\r') {
            column++;
        }
    }

    private void checkDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("hay más de " + MAX_DEPTH + " niveles de objetos o listas anidados");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Línea " + line + ", columna " + column + ": " + message);
    }

    /** Error en el carácter que se acaba de leer (para "se esperaba una coma"). */
    private IllegalArgumentException errorBack(String message) {
        return new IllegalArgumentException("Línea " + line + ", columna " + Math.max(1, column - 1) + ": " + message);
    }

    private static String printable(char c) {
        return c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c);
    }
}
