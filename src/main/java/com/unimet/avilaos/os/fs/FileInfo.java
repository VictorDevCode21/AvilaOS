package com.unimet.avilaos.os.fs;

/**
 * Copia de un archivo o directorio para la GUI (JTree, tabla de asignación) y
 * para guardar el JSON. Es inmutable: cambiarla no cambia el sistema de archivos.
 *
 * @author NicollPinzon
 */
public final class FileInfo {

    private final String path;
    private final boolean directory;
    private final int blockCount;
    private final int firstBlock;
    private final String owner;
    private final Permission permission;
    private final String creator;
    private final int[] chain;

    FileInfo(String path, boolean directory, int blockCount, int firstBlock, String owner,
            Permission permission, String creator, int[] chain) {
        this.path = path;
        this.directory = directory;
        this.blockCount = blockCount;
        this.firstBlock = firstBlock;
        this.owner = owner;
        this.permission = permission;
        this.creator = creator;
        this.chain = chain.clone();
    }

    /** Ruta completa, por ejemplo "/usuarios/notas.txt" ("/" para la raíz). */
    public String getPath() {
        return path;
    }

    /** Último nombre de la ruta ("/" para la raíz). */
    public String getName() {
        return path.equals(FsPaths.ROOT) ? FsPaths.ROOT : FsPaths.nameOf(path);
    }

    public boolean isDirectory() {
        return directory;
    }

    /** Tamaño en bloques (0 en los directorios). */
    public int getBlockCount() {
        return blockCount;
    }

    /** Primer bloque de la cadena (BlockMap.END en los directorios). */
    public int getFirstBlock() {
        return firstBlock;
    }

    public String getOwner() {
        return owner;
    }

    public Permission getPermission() {
        return permission;
    }

    /** Proceso que lo creó (por ejemplo "P3"), o "JSON" si se cargó del archivo. */
    public String getCreator() {
        return creator;
    }

    /** Bloques de la cadena en orden (vacío en los directorios). Es una copia. */
    public int[] getChain() {
        return chain.clone();
    }

    @Override
    public String toString() {
        return path + (directory ? " (directorio)" : " (" + blockCount + " bloques desde " + firstBlock + ")")
                + " · " + owner + " · " + permission.getLabel();
    }
}
