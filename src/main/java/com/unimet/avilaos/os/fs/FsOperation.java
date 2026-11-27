package com.unimet.avilaos.os.fs;

import com.unimet.avilaos.hardware.IoOperation;

/**
 * Lo que un proceso de E/S le pide al sistema de archivos. Se crea al crear el
 * proceso y lo lleva hasta que el disco atiende su solicitud.
 *
 * Se construye con las fábricas estáticas, que validan la forma de los datos
 * (rutas, nombres, tamaño). Si la operación se puede hacer o no (existencia,
 * permisos, espacio) lo decide FileSystem.
 *
 * @author NicollPinzon
 */
public final class FsOperation {

    private final IoOperation operation;
    private final String path;
    private final String newName;
    private final boolean directory;
    private final int sizeBlocks;
    private final Permission permission;
    private final String owner;
    private final String creator;

    private FsOperation(IoOperation operation, String path, String newName, boolean directory, int sizeBlocks,
            Permission permission, String owner, String creator) {
        if (creator == null || creator.isBlank()) {
            throw new IllegalArgumentException("La operación necesita el proceso que la pide");
        }
        this.operation = operation;
        this.path = FsPaths.check(path);
        this.newName = newName == null ? null : FsPaths.checkName(newName);
        this.directory = directory;
        this.sizeBlocks = sizeBlocks;
        this.permission = permission;
        this.owner = owner;
        this.creator = creator.trim();
    }

    /**
     * Crear un archivo.
     *
     * @param parentPath directorio donde va
     * @param name       nombre del archivo
     * @param sizeBlocks tamaño en bloques (1 o más)
     * @param permission público o privado
     * @param creator    proceso que lo crea (por ejemplo "P3")
     */
    public static FsOperation createFile(String parentPath, String name, int sizeBlocks, Permission permission,
            String creator) {
        if (sizeBlocks < 1) {
            throw new IllegalArgumentException("El archivo debe tener al menos 1 bloque");
        }
        requirePermission(permission);
        FsPaths.checkName(name);
        return new FsOperation(IoOperation.CREATE, parentPath, name, false, sizeBlocks, permission, null, creator);
    }

    /**
     * Crear un directorio (solo el administrador).
     *
     * @param owner dueño del directorio nuevo (por ejemplo "user1" para su carpeta);
     *              null para que sea del usuario de la sesión
     */
    public static FsOperation createDirectory(String parentPath, String name, String owner, Permission permission,
            String creator) {
        requirePermission(permission);
        FsPaths.checkName(name);
        String cleanOwner = owner == null || owner.isBlank() ? null : owner.trim();
        return new FsOperation(IoOperation.CREATE, parentPath, name, true, 0, permission, cleanOwner, creator);
    }

    /** Leer un archivo (o listar un directorio). */
    public static FsOperation read(String path, String creator) {
        return new FsOperation(IoOperation.READ, path, null, false, 0, null, null, creator);
    }

    /**
     * Actualizar un archivo en su lugar, sin cambiarle el nombre: el disco reescribe
     * sus bloques y el árbol no cambia. Es lo que hace un UPDATE del JSON de procesos
     * que no trae "newName". Lo pueden hacer el administrador y el dueño del archivo.
     */
    public static FsOperation update(String path, String creator) {
        return new FsOperation(IoOperation.UPDATE, path, null, false, 0, null, null, creator);
    }

    /** Cambiar el nombre de un archivo o directorio (solo el administrador). */
    public static FsOperation rename(String path, String newName, String creator) {
        FsPaths.checkName(newName);
        return new FsOperation(IoOperation.UPDATE, path, newName, false, 0, null, null, creator);
    }

    /** Eliminar un archivo, o un directorio con todo su contenido. */
    public static FsOperation delete(String path, String creator) {
        return new FsOperation(IoOperation.DELETE, path, null, false, 0, null, null, creator);
    }

    public IoOperation getOperation() {
        return operation;
    }

    /** Ruta objetivo; en CREATE es el directorio donde se crea. */
    public String getPath() {
        return path;
    }

    /** UPDATE: true si cambia el nombre; false si reescribe el archivo en su lugar. */
    public boolean isRename() {
        return operation == IoOperation.UPDATE && newName != null;
    }

    /** CREATE: nombre del nuevo; UPDATE: el nuevo nombre (null si se reescribe en su lugar); en las demás, null. */
    public String getNewName() {
        return newName;
    }

    /** CREATE: true si se crea un directorio. */
    public boolean isDirectory() {
        return directory;
    }

    /** CREATE de archivo: tamaño en bloques; en las demás, 0. */
    public int getSizeBlocks() {
        return sizeBlocks;
    }

    /** CREATE: permiso del nuevo; en las demás, null. */
    public Permission getPermission() {
        return permission;
    }

    /** CREATE de directorio: dueño elegido, o null para el usuario de la sesión. */
    public String getOwner() {
        return owner;
    }

    public String getCreator() {
        return creator;
    }

    /** Ruta del nuevo archivo o directorio en un CREATE. */
    public String getCreatedPath() {
        return FsPaths.join(path, newName);
    }

    /** Ejemplo: "crear archivo /usuarios/user1/a.txt (3 bloques)". */
    @Override
    public String toString() {
        return switch (operation) {
            case CREATE -> directory ? "crear directorio " + getCreatedPath()
                    : "crear archivo " + getCreatedPath() + " (" + sizeBlocks + (sizeBlocks == 1 ? " bloque)" : " bloques)");
            case READ -> "leer " + path;
            case UPDATE -> newName == null ? "actualizar " + path : "renombrar " + path + " como " + newName;
            case DELETE -> "eliminar " + path;
            default -> operation.getLabel() + " " + path;
        };
    }

    private static void requirePermission(Permission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("Hay que indicar si es público o privado");
        }
    }
}
