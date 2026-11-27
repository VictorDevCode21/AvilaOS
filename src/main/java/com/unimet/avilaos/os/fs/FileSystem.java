package com.unimet.avilaos.os.fs;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.BlockMap;
import java.util.concurrent.Semaphore;

/**
 * Sistema de archivos jerárquico de ÁvilaOS: un árbol propio de directorios y
 * archivos sobre el mapa de bloques del disco (asignación encadenada).
 *
 * Cada operación sobre un archivo la hace un proceso de E/S, nunca la interfaz
 * (lo pide el enunciado). Por eso hay dos momentos:
 * <ol>
 *   <li>{@link #check}: al crear el proceso de E/S. No cambia nada; dice si se
 *       permite y, si se permite, a qué bloque debe ir el cabezal y cuántos
 *       bloques se leen o escriben. Si se rechaza, la GUI muestra el motivo.</li>
 *   <li>{@link #apply}: cuando el disco termina de atender la solicitud. Vuelve a
 *       validar (mientras esperaba en la cola otro proceso pudo borrar el archivo
 *       o llenar el disco) y hace el cambio.</li>
 * </ol>
 *
 * Bloque objetivo de cada solicitud:
 * <ul>
 *   <li>leer, renombrar o eliminar un archivo: su primer bloque;</li>
 *   <li>crear un archivo: el primer bloque libre, donde empezará su cadena;</li>
 *   <li>cualquier operación sobre un directorio: el bloque 0. Los directorios no
 *       ocupan bloques; se toma el 0 como el lugar de la tabla de directorios,
 *       como el MBR del ejemplo del enunciado.</li>
 * </ul>
 * Leer cuesta un ciclo por bloque del archivo; crear, uno por bloque escrito;
 * renombrar o eliminar escriben solo los datos del directorio (1 bloque).
 *
 * Concurrencia: un Semaphore protege el árbol. Con él tomado se llama al
 * BlockMap, que nunca llama de vuelta, así que el orden es siempre
 * FileSystem → BlockMap y no hay espera circular. Las consultas devuelven copias.
 *
 * @author NicollPinzon
 */
public class FileSystem {

    /** Creador anotado en lo que se carga del JSON del sistema de archivos. */
    public static final String LOADED_BY = "JSON";

    private static final int DIRECTORY_BLOCK = 0;

    private final BlockMap blocks;
    private final FsNode root;
    private final Semaphore mutex = new Semaphore(1, true);

    /**
     * @param blocks    mapa de bloques del disco
     * @param rootOwner dueño de la raíz (normalmente "admin")
     */
    public FileSystem(BlockMap blocks, String rootOwner) {
        if (blocks == null) {
            throw new IllegalArgumentException("El sistema de archivos necesita el mapa de bloques");
        }
        if (rootOwner == null || rootOwner.isBlank()) {
            throw new IllegalArgumentException("La raíz necesita un dueño");
        }
        this.blocks = blocks;
        this.root = FsNode.directory("", rootOwner.trim(), Permission.PUBLIC, LOADED_BY);
    }

    // ---------- Operaciones de los procesos de E/S ----------

    /**
     * ¿Se puede hacer esta operación? No cambia nada.
     *
     * @return permitido con el bloque objetivo y la cantidad de bloques, o rechazado con el motivo
     */
    public FsResult check(UserSession session, FsOperation op) {
        requireArguments(session, op);
        mutex.acquireUninterruptibly();
        try {
            return evaluate(session, op);
        } finally {
            mutex.release();
        }
    }

    /**
     * Hace la operación (cuando el disco terminó de atender la solicitud). Si ya
     * no se puede, no cambia nada y devuelve el motivo para el log.
     */
    public FsResult apply(UserSession session, FsOperation op) {
        requireArguments(session, op);
        mutex.acquireUninterruptibly();
        try {
            FsResult check = evaluate(session, op);
            if (!check.isOk()) {
                return FsResult.denied("No se pudo " + op + ": " + check.getMessage());
            }
            String done = switch (op.getOperation()) {
                case CREATE -> doCreate(session, op);
                case READ -> doRead(op);
                case UPDATE -> op.isRename() ? doRename(op) : doUpdate(op);
                case DELETE -> doDelete(op);
                default -> throw new IllegalStateException("Operación no soportada: " + op.getOperation());
            };
            return FsResult.allowed(done, check.getTargetBlock(), check.getBlockCount());
        } finally {
            mutex.release();
        }
    }

    // ---------- Carga del JSON (sin sesión: lo hace el sistema al iniciar) ----------

    /**
     * Agrega un directorio cargado del JSON. Su directorio padre ya debe existir.
     *
     * @throws IllegalArgumentException si la ruta no es válida, el padre no existe o ya existe
     */
    public void loadDirectory(String path, String owner, Permission permission) {
        loadDirectory(path, owner, permission, LOADED_BY);
    }

    /**
     * Como loadDirectory, con el proceso que lo creó (al volver a cargar un sistema
     * de archivos guardado, para que la tabla no lo pierda).
     */
    public void loadDirectory(String path, String owner, Permission permission, String creator) {
        String p = FsPaths.check(path);
        String by = requireCreator(creator);
        requireOwnerAndPermission(owner, permission);
        if (p.equals(FsPaths.ROOT)) {
            throw new IllegalArgumentException("La raíz \"/\" ya existe: no se carga como directorio");
        }
        mutex.acquireUninterruptibly();
        try {
            FsNode parent = parentForLoad(p);
            parent.addChild(FsNode.directory(FsPaths.nameOf(p), owner.trim(), permission, by));
        } finally {
            mutex.release();
        }
    }

    /**
     * Agrega un archivo cargado del JSON en los bloques consecutivos desde startBlock.
     *
     * @throws IllegalArgumentException si la ruta no es válida, el padre no existe,
     *                                  ya existe, o los bloques están ocupados o fuera del disco
     */
    public void loadFile(String path, int startBlock, int blockCount, String owner, Permission permission) {
        String p = FsPaths.check(path);
        requireOwnerAndPermission(owner, permission);
        if (p.equals(FsPaths.ROOT)) {
            throw new IllegalArgumentException("La raíz \"/\" es un directorio, no un archivo");
        }
        mutex.acquireUninterruptibly();
        try {
            FsNode parent = parentForLoad(p);
            blocks.allocateFileAt(startBlock, blockCount, p);
            parent.addChild(FsNode.file(FsPaths.nameOf(p), owner.trim(), permission, LOADED_BY, startBlock,
                    blockCount));
        } finally {
            mutex.release();
        }
    }

    /**
     * Agrega un archivo de un sistema de archivos guardado, en exactamente esos
     * bloques y en ese orden (su cadena puede estar salteada).
     *
     * @param chain   bloques del archivo en orden
     * @param creator proceso que lo creó, o LOADED_BY
     * @throws IllegalArgumentException si la ruta no es válida, el padre no existe,
     *                                  ya existe, o algún bloque está ocupado, repetido o fuera del disco
     */
    public void loadFile(String path, int[] chain, String owner, Permission permission, String creator) {
        String p = FsPaths.check(path);
        requireOwnerAndPermission(owner, permission);
        String by = requireCreator(creator);
        if (p.equals(FsPaths.ROOT)) {
            throw new IllegalArgumentException("La raíz \"/\" es un directorio, no un archivo");
        }
        if (chain == null || chain.length == 0) {
            throw new IllegalArgumentException("El archivo " + p + " necesita al menos 1 bloque");
        }
        mutex.acquireUninterruptibly();
        try {
            FsNode parent = parentForLoad(p);
            blocks.allocateFileChain(chain, p);
            parent.addChild(FsNode.file(FsPaths.nameOf(p), owner.trim(), permission, by, chain[0], chain.length));
        } finally {
            mutex.release();
        }
    }

    // ---------- Consultas (GUI, log, guardar el JSON) ----------

    /** Todos los nodos en orden de árbol (la raíz primero, cada directorio antes que su contenido). */
    public LinkedList<FileInfo> list() {
        mutex.acquireUninterruptibly();
        try {
            LinkedList<FileInfo> result = new LinkedList<>();
            collect(root, result);
            return result;
        } finally {
            mutex.release();
        }
    }

    /** Copia del nodo en esa ruta, o null si no existe. */
    public FileInfo find(String path) {
        String p = FsPaths.check(path);
        mutex.acquireUninterruptibly();
        try {
            FsNode node = resolve(p);
            return node == null ? null : info(node);
        } finally {
            mutex.release();
        }
    }

    public boolean exists(String path) {
        return find(path) != null;
    }

    public BlockMap getBlockMap() {
        return blocks;
    }

    /** Dueño de la raíz: el administrador (no puede usar el modo usuario). */
    public String getRootOwner() {
        return root.owner;
    }

    /**
     * Usuarios que pueden entrar en modo usuario: los dueños de archivos y
     * directorios, sin repetir y sin el dueño de la raíz, en orden de aparición.
     * Es la lista para el selector de sesión de la GUI.
     */
    public LinkedList<String> userNames() {
        mutex.acquireUninterruptibly();
        try {
            LinkedList<String> names = new LinkedList<>();
            collectOwners(root, names);
            return names;
        } finally {
            mutex.release();
        }
    }

    // ---------- Validación ----------

    private FsResult evaluate(UserSession session, FsOperation op) {
        String sessionProblem = AccessRules.canUseSession(session, root.owner);
        if (sessionProblem != null) {
            return FsResult.denied(sessionProblem);
        }
        return switch (op.getOperation()) {
            case CREATE -> evaluateCreate(session, op);
            case READ -> evaluateRead(session, op);
            case UPDATE -> op.isRename() ? evaluateRename(session, op) : evaluateUpdate(session, op);
            case DELETE -> evaluateDelete(session, op);
            default -> throw new IllegalArgumentException(op.getOperation().getLabel()
                    + " no es una operación del sistema de archivos");
        };
    }

    private FsResult evaluateCreate(UserSession session, FsOperation op) {
        FsNode parent = resolve(op.getPath());
        if (parent == null) {
            return FsResult.denied("No existe el directorio " + op.getPath() + ".");
        }
        if (!parent.directory) {
            return FsResult.denied(op.getPath() + " es un archivo, no un directorio.");
        }
        if (parent.child(op.getNewName()) != null) {
            return FsResult.denied("Ya existe " + op.getCreatedPath() + ".");
        }
        if (op.isDirectory()) {
            String reason = AccessRules.canCreateDirectory(session);
            return reason != null ? FsResult.denied(reason)
                    : FsResult.allowed("Se puede " + op + ".", DIRECTORY_BLOCK, 1);
        }
        String reason = AccessRules.canCreateFile(session, parent);
        if (reason != null) {
            return FsResult.denied(reason);
        }
        int free = blocks.getFreeFileBlocks();
        if (free < op.getSizeBlocks()) {
            return FsResult.denied("No hay bloques libres suficientes: " + op.getCreatedPath() + " necesita "
                    + op.getSizeBlocks() + " y quedan " + free + ".");
        }
        return FsResult.allowed("Se puede " + op + ".", blocks.firstFreeFileBlock(), op.getSizeBlocks());
    }

    private FsResult evaluateRead(UserSession session, FsOperation op) {
        FsNode node = resolve(op.getPath());
        if (node == null) {
            return FsResult.denied("No existe " + op.getPath() + ".");
        }
        if (node.directory) {
            return FsResult.allowed("Se puede listar " + op.getPath() + ".", DIRECTORY_BLOCK, 1);
        }
        String reason = AccessRules.canRead(session, node);
        return reason != null ? FsResult.denied(reason)
                : FsResult.allowed("Se puede " + op + ".", node.firstBlock, node.blockCount);
    }

    private FsResult evaluateRename(UserSession session, FsOperation op) {
        FsNode node = resolve(op.getPath());
        if (node == null) {
            return FsResult.denied("No existe " + op.getPath() + ".");
        }
        if (node.isRoot()) {
            return FsResult.denied("La raíz no se puede renombrar.");
        }
        String reason = AccessRules.canRename(session);
        if (reason != null) {
            return FsResult.denied(reason);
        }
        if (node.name.equals(op.getNewName())) {
            return FsResult.denied(op.getPath() + " ya se llama " + op.getNewName() + ".");
        }
        if (node.parent.child(op.getNewName()) != null) {
            return FsResult.denied("Ya existe " + FsPaths.join(node.parent.path(), op.getNewName()) + ".");
        }
        return FsResult.allowed("Se puede " + op + ".", node.directory ? DIRECTORY_BLOCK : node.firstBlock, 1);
    }

    /** Actualizar en su lugar: el disco reescribe todos los bloques del archivo. */
    private FsResult evaluateUpdate(UserSession session, FsOperation op) {
        FsNode node = resolve(op.getPath());
        if (node == null) {
            return FsResult.denied("No existe " + op.getPath() + ".");
        }
        if (node.directory) {
            return FsResult.denied(op.getPath() + " es un directorio: para actualizarlo hay que darle un nombre nuevo.");
        }
        String reason = AccessRules.canWrite(session, node);
        return reason != null ? FsResult.denied(reason)
                : FsResult.allowed("Se puede " + op + ".", node.firstBlock, node.blockCount);
    }

    private FsResult evaluateDelete(UserSession session, FsOperation op) {
        FsNode node = resolve(op.getPath());
        if (node == null) {
            return FsResult.denied("No existe " + op.getPath() + ".");
        }
        if (node.isRoot()) {
            return FsResult.denied("La raíz no se puede eliminar.");
        }
        String reason = AccessRules.canDelete(session, node);
        return reason != null ? FsResult.denied(reason)
                : FsResult.allowed("Se puede " + op + ".", node.directory ? DIRECTORY_BLOCK : node.firstBlock, 1);
    }

    // ---------- Cambios (ya validados, con el mutex tomado) ----------

    private String doCreate(UserSession session, FsOperation op) {
        FsNode parent = resolve(op.getPath());
        String path = op.getCreatedPath();
        if (op.isDirectory()) {
            String owner = op.getOwner() != null ? op.getOwner() : session.getUserName();
            parent.addChild(FsNode.directory(op.getNewName(), owner, op.getPermission(), op.getCreator()));
            return "Se creó el directorio " + path + " (dueño " + owner + ", " + op.getPermission().getLabel()
                    .toLowerCase() + ").";
        }
        int first = blocks.allocateFile(op.getSizeBlocks(), path);
        parent.addChild(FsNode.file(op.getNewName(), session.getUserName(), op.getPermission(), op.getCreator(),
                first, op.getSizeBlocks()));
        return "Se creó " + path + ": " + op.getSizeBlocks() + (op.getSizeBlocks() == 1 ? " bloque" : " bloques")
                + " desde el bloque " + first + ".";
    }

    private String doRead(FsOperation op) {
        FsNode node = resolve(op.getPath());
        if (node.directory) {
            return "Se listó " + op.getPath() + " (" + node.children.size() + " elementos).";
        }
        return "Se leyó " + op.getPath() + " (" + node.blockCount + (node.blockCount == 1 ? " bloque)." : " bloques).");
    }

    private String doRename(FsOperation op) {
        FsNode node = resolve(op.getPath());
        String before = node.path();
        node.name = op.getNewName();
        relabelBlocks(node);
        return "Se renombró " + before + " como " + node.path() + ".";
    }

    private String doUpdate(FsOperation op) {
        FsNode node = resolve(op.getPath());
        return "Se actualizó " + op.getPath() + " (" + node.blockCount + (node.blockCount == 1 ? " bloque)." : " bloques).");
    }

    private String doDelete(FsOperation op) {
        FsNode node = resolve(op.getPath());
        int[] counts = new int[2];          // [archivos, bloques liberados]
        freeBlocks(node, counts);
        node.parent.children.remove(node);
        node.parent = null;
        if (!node.directory) {
            return "Se eliminó " + op.getPath() + " (" + counts[1] + " bloques liberados).";
        }
        return "Se eliminó el directorio " + op.getPath() + " con " + counts[0]
                + (counts[0] == 1 ? " archivo" : " archivos") + " (" + counts[1] + " bloques liberados).";
    }

    /** Borrado recursivo: libera la cadena de cada archivo del subárbol. */
    private void freeBlocks(FsNode node, int[] counts) {
        if (!node.directory) {
            counts[0]++;
            counts[1] += blocks.free(node.firstBlock);
            return;
        }
        for (int i = 0; i < node.children.size(); i++) {
            freeBlocks(node.children.get(i), counts);
        }
    }

    /** El dueño de un bloque es la ruta del archivo: al renombrar se actualiza todo el subárbol. */
    private void relabelBlocks(FsNode node) {
        if (!node.directory) {
            blocks.relabel(node.firstBlock, node.path());
            return;
        }
        for (int i = 0; i < node.children.size(); i++) {
            relabelBlocks(node.children.get(i));
        }
    }

    // ---------- Internos ----------

    private FsNode resolve(String path) {
        if (path.equals(FsPaths.ROOT)) {
            return root;
        }
        FsNode current = root;
        for (String part : path.substring(1).split("/")) {
            if (!current.directory) {
                return null;
            }
            current = current.child(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private FsNode parentForLoad(String path) {
        String parentPath = FsPaths.parentOf(path);
        FsNode parent = resolve(parentPath);
        if (parent == null) {
            throw new IllegalArgumentException("El directorio " + parentPath + " de " + path + " no existe");
        }
        if (!parent.directory) {
            throw new IllegalArgumentException(parentPath + " es un archivo, no un directorio: no puede contener a "
                    + path);
        }
        if (parent.child(FsPaths.nameOf(path)) != null) {
            throw new IllegalArgumentException(path + " está repetido");
        }
        return parent;
    }

    private void collectOwners(FsNode node, LinkedList<String> names) {
        if (!node.owner.equals(root.owner) && !containsText(names, node.owner)) {
            names.addLast(node.owner);
        }
        if (node.directory) {
            for (int i = 0; i < node.children.size(); i++) {
                collectOwners(node.children.get(i), names);
            }
        }
    }

    /** LinkedList.contains compara referencias; aquí hace falta comparar el texto. */
    private static boolean containsText(LinkedList<String> list, String text) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equals(text)) {
                return true;
            }
        }
        return false;
    }

    private void collect(FsNode node, LinkedList<FileInfo> result) {
        result.addLast(info(node));
        if (node.directory) {
            for (int i = 0; i < node.children.size(); i++) {
                collect(node.children.get(i), result);
            }
        }
    }

    private FileInfo info(FsNode node) {
        int[] chain = node.directory ? new int[0] : blocks.chainOf(node.firstBlock);
        return new FileInfo(node.path(), node.directory, node.blockCount, node.firstBlock, node.owner,
                node.permission, node.creator, chain);
    }

    private static void requireArguments(UserSession session, FsOperation op) {
        if (session == null) {
            throw new IllegalArgumentException("La operación necesita la sesión del usuario");
        }
        if (op == null) {
            throw new IllegalArgumentException("La operación no puede ser null");
        }
    }

    private static String requireCreator(String creator) {
        if (creator == null || creator.isBlank()) {
            throw new IllegalArgumentException("Falta el proceso que lo creó");
        }
        return creator.trim();
    }

    private static void requireOwnerAndPermission(String owner, Permission permission) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Falta el dueño");
        }
        if (permission == null) {
            throw new IllegalArgumentException("Falta el permiso (public o private)");
        }
    }
}
