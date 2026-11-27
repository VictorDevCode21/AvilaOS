package com.unimet.avilaos.os.fs;

/**
 * Reglas de acceso del modo administrador y del modo usuario, todas en un solo
 * lugar. Cada método devuelve null si se permite, o el motivo del rechazo en
 * español (la GUI lo muestra sin detener la simulación).
 *
 * Del enunciado:
 * <ul>
 *   <li>el administrador puede hacer todo;</li>
 *   <li>los usuarios crean archivos solo dentro de sus propios directorios, y no
 *       crean directorios;</li>
 *   <li>todos ven la estructura y leen sus archivos propios o los públicos;</li>
 *   <li>cambiar el nombre es del administrador.</li>
 * </ul>
 * Decisiones del equipo (el enunciado no las fija):
 * <ul>
 *   <li>"su propio directorio" es un directorio cuyo dueño es ese usuario; el
 *       administrador elige el dueño al crear un directorio (por ejemplo
 *       /usuarios/user1 con dueño user1);</li>
 *   <li>un usuario puede eliminar sus propios archivos, pero no directorios;</li>
 *   <li>el dueño de la raíz ("admin") no puede usar el modo usuario;</li>
 *   <li>leer un archivo público ajeno está permitido: el enunciado dice "leer sus
 *       archivos propios o los públicos", y leer no modifica nada.</li>
 * </ul>
 *
 * @author NicollPinzon
 */
final class AccessRules {

    private AccessRules() {
    }

    /**
     * El dueño de la raíz (normalmente "admin") es el administrador: no puede
     * entrar en modo usuario, porque así podría modificar los archivos del
     * sistema como si fueran "suyos".
     */
    static String canUseSession(UserSession session, String rootOwner) {
        if (!session.isAdmin() && session.getUserName().equals(rootOwner)) {
            return "El usuario " + rootOwner + " es el administrador: solo puede usar el modo administrador.";
        }
        return null;
    }

    static String canCreateDirectory(UserSession session) {
        return session.isAdmin() ? null : "Solo el administrador puede crear directorios.";
    }

    static String canCreateFile(UserSession session, FsNode parent) {
        if (session.isAdmin() || parent.owner.equals(session.getUserName())) {
            return null;
        }
        return "Solo puedes crear archivos dentro de tus propios directorios: " + parent.path()
                + " es de " + parent.owner + ".";
    }

    static String canRead(UserSession session, FsNode file) {
        if (session.isAdmin() || file.permission == Permission.PUBLIC
                || file.owner.equals(session.getUserName())) {
            return null;
        }
        return "El archivo " + file.path() + " es privado de " + file.owner + ".";
    }

    static String canRename(UserSession session) {
        return session.isAdmin() ? null : "Solo el administrador puede cambiar nombres.";
    }

    static String canDelete(UserSession session, FsNode node) {
        if (session.isAdmin()) {
            return null;
        }
        if (node.directory) {
            return "Solo el administrador puede eliminar directorios.";
        }
        if (node.owner.equals(session.getUserName())) {
            return null;
        }
        return "Solo puedes eliminar tus propios archivos: " + node.path() + " es de " + node.owner + ".";
    }
}
