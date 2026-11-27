package com.unimet.avilaos.os.fs;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.BlockMap;

/**
 * Nodo del árbol del sistema de archivos (archivo o directorio). Solo lo usa
 * FileSystem, siempre con su mutex tomado; hacia afuera se entregan copias
 * ({@link FileInfo}).
 *
 * El árbol es propio: cada directorio guarda sus hijos en una LinkedList del
 * proyecto y cada nodo conoce a su padre, así la ruta se arma subiendo.
 *
 * @author NicollPinzon
 */
final class FsNode {

    String name;
    final boolean directory;
    final String owner;
    final Permission permission;
    final String creator;
    final int firstBlock;
    final int blockCount;
    FsNode parent;
    final LinkedList<FsNode> children;

    private FsNode(String name, boolean directory, String owner, Permission permission, String creator,
            int firstBlock, int blockCount) {
        this.name = name;
        this.directory = directory;
        this.owner = owner;
        this.permission = permission;
        this.creator = creator;
        this.firstBlock = firstBlock;
        this.blockCount = blockCount;
        this.children = directory ? new LinkedList<>() : null;
    }

    static FsNode directory(String name, String owner, Permission permission, String creator) {
        return new FsNode(name, true, owner, permission, creator, BlockMap.END, 0);
    }

    static FsNode file(String name, String owner, Permission permission, String creator, int firstBlock,
            int blockCount) {
        return new FsNode(name, false, owner, permission, creator, firstBlock, blockCount);
    }

    boolean isRoot() {
        return parent == null;
    }

    /** Ruta completa, armada subiendo hasta la raíz. */
    String path() {
        if (isRoot()) {
            return FsPaths.ROOT;
        }
        return FsPaths.join(parent.path(), name);
    }

    /** Hijo con ese nombre, o null (solo en directorios). */
    FsNode child(String childName) {
        for (int i = 0; i < children.size(); i++) {
            FsNode c = children.get(i);
            if (c.name.equals(childName)) {
                return c;
            }
        }
        return null;
    }

    void addChild(FsNode child) {
        child.parent = this;
        children.addLast(child);
    }
}
