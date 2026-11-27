package com.unimet.avilaos.os.fs;

/**
 * Quién está usando el simulador: nombre de usuario y modo. El sistema de
 * archivos decide con esto qué puede hacer cada proceso de E/S (el proceso actúa
 * en nombre del usuario que lo creó).
 *
 * Inmutable: cambiar de modo es crear otra sesión.
 *
 * Fuera del sistema de archivos, el enunciado reserva al administrador gestionar los procesos,
 * cambiar las políticas de planificación y la configuración; el modo usuario solo lee archivos
 * propios o públicos y crea procesos de E/S sobre los suyos. adminOnly da el motivo para rechazar
 * esas acciones (la interfaz lo muestra sin detener la simulación).
 *
 * @author NicollPinzon
 */
public final class UserSession {

    private final String userName;
    private final UserMode mode;

    /**
     * @throws IllegalArgumentException si el nombre está vacío o el modo es null
     */
    public UserSession(String userName, UserMode mode) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("La sesión necesita un nombre de usuario");
        }
        if (mode == null) {
            throw new IllegalArgumentException("La sesión necesita un modo (administrador o usuario)");
        }
        this.userName = userName.trim();
        this.mode = mode;
    }

    /** Sesión de administrador con ese nombre. */
    public static UserSession admin(String userName) {
        return new UserSession(userName, UserMode.ADMIN);
    }

    /** Sesión de usuario restringido con ese nombre. */
    public static UserSession user(String userName) {
        return new UserSession(userName, UserMode.USER);
    }

    public String getUserName() {
        return userName;
    }

    public UserMode getMode() {
        return mode;
    }

    public boolean isAdmin() {
        return mode == UserMode.ADMIN;
    }

    /**
     * Motivo para rechazar una acción que solo puede hacer el administrador.
     *
     * @param action lo que se intentó, en infinitivo (por ejemplo "cambiar la política de disco")
     * @return null si la sesión es de administrador; si no, el motivo para mostrar
     */
    public String adminOnly(String action) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Falta la acción que se quiere hacer");
        }
        return isAdmin() ? null : "En modo usuario no se puede " + action.trim()
                + ": solo el administrador puede hacerlo.";
    }

    /** Ejemplo: "user1 (Usuario)". */
    @Override
    public String toString() {
        return userName + " (" + mode.getLabel() + ")";
    }
}
