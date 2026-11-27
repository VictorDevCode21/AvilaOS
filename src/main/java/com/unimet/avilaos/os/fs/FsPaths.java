package com.unimet.avilaos.os.fs;

/**
 * Reglas de las rutas del sistema de archivos. Una ruta es absoluta: empieza en
 * "/" (la raíz) y separa los nombres con "/", sin barras repetidas ni al final.
 *
 * @author NicollPinzon
 */
final class FsPaths {

    static final String ROOT = "/";
    static final int MAX_NAME = 40;

    private FsPaths() {
    }

    /** Valida una ruta y la devuelve sin espacios alrededor. */
    static String check(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("La ruta no puede estar vacía");
        }
        String p = path.trim();
        if (!p.startsWith(ROOT)) {
            throw new IllegalArgumentException("La ruta debe empezar con \"/\": " + p);
        }
        if (p.equals(ROOT)) {
            return p;
        }
        if (p.endsWith("/")) {
            throw new IllegalArgumentException("La ruta no puede terminar en \"/\": " + p);
        }
        for (String part : p.substring(1).split("/", -1)) {
            checkName(part);
        }
        return p;
    }

    /** Valida un nombre de archivo o directorio y lo devuelve. */
    static String checkName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío (¿hay \"//\" en la ruta?)");
        }
        if (!name.equals(name.trim())) {
            throw new IllegalArgumentException("El nombre no puede empezar ni terminar con espacios: \"" + name + "\"");
        }
        if (name.contains("/")) {
            throw new IllegalArgumentException("El nombre no puede tener \"/\": " + name);
        }
        if (name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("\".\" y \"..\" no son nombres válidos");
        }
        if (name.length() > MAX_NAME) {
            throw new IllegalArgumentException("El nombre tiene más de " + MAX_NAME + " caracteres: " + name);
        }
        return name;
    }

    /** Directorio que contiene la ruta ("/" para los hijos de la raíz). */
    static String parentOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash == 0 ? ROOT : path.substring(0, slash);
    }

    /** Último nombre de la ruta. */
    static String nameOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Une un directorio y un nombre: join("/", "a") = "/a", join("/a", "b") = "/a/b". */
    static String join(String parent, String name) {
        return parent.equals(ROOT) ? ROOT + name : parent + "/" + name;
    }
}
