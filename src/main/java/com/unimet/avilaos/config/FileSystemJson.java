package com.unimet.avilaos.config;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.BlockMap;
import com.unimet.avilaos.os.fs.FileInfo;
import com.unimet.avilaos.os.fs.FileSystem;
import com.unimet.avilaos.os.fs.Permission;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON del sistema de archivos (formato del enunciado): leerlo, cargarlo en el
 * FileSystem del computador y guardarlo de nuevo.
 *
 * <pre>
 * { "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
 *   "fileSystem": [
 *     { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
 *     { "path": "/sistema/boot.bin", "type": "FILE", "startBlock": 11, "blocks": 23,
 *       "owner": "admin", "permissions": "public" } ] }
 * </pre>
 *
 * totalBlocks es la zona de archivos; el área de intercambio viene en el JSON de
 * procesos y va después (200 + 100 = 300). Dos campos opcionales permiten guardar
 * lo que cambió durante la simulación y volver a cargarlo sin perder nada:
 * <ul>
 *   <li>"chain": los bloques del archivo en orden, cuando no son consecutivos (un
 *       archivo creado en la simulación toma los primeros libres: 62, 63, 119…);</li>
 *   <li>"creator": el proceso que lo creó (si falta, es "JSON").</li>
 * </ul>
 * Un archivo que no se tocó se guarda igual que en el enunciado.
 *
 * Decisiones:
 * <ul>
 *   <li>Las entradas se cargan de menos a más profunda, así un directorio siempre
 *       va antes que su contenido aunque el JSON venga desordenado. Si falta el
 *       directorio de un archivo, es un error (no se crea solo).</li>
 *   <li>Todo o nada: primero se prueba la carga en un disco de prueba; solo si todo
 *       está bien se carga en el real. Así un error no deja el disco a medias.</li>
 *   <li>Cada error dice la entrada, su ruta y, si es de formato, la línea y columna.</li>
 *   <li>Claves desconocidas son un error, para que un tipeo no se ignore en silencio.</li>
 * </ul>
 *
 * @author NicollPinzon
 */
public final class FileSystemJson {

    /** El enunciado pide "una cantidad razonable, por ejemplo entre 100 y 500 bloques". */
    public static final int MIN_BLOCKS = 100;
    public static final int MAX_BLOCKS = 500;

    private static final String TYPE_FILE = "FILE";
    private static final String TYPE_DIRECTORY = "DIRECTORY";

    /**
     * Lo que dice el JSON del disco.
     *
     * @param totalBlocks bloques de la zona de archivos
     * @param initialHead bloque donde empieza el cabezal
     */
    public record DiskSettings(int totalBlocks, int initialHead) {
    }

    /**
     * Una entrada de "fileSystem" ya validada.
     *
     * @param number    número de la entrada en el JSON (desde 1), para los mensajes
     * @param chain     bloques del archivo en orden (vacío para directorios)
     * @param creator   proceso que lo creó, o FileSystem.LOADED_BY
     */
    public record Entry(int number, String path, boolean directory, int[] chain, String owner,
            Permission permission, String creator) {

        /** Profundidad de la ruta: "/a" = 1, "/a/b" = 2. */
        int depth() {
            int count = 0;
            for (int i = 0; i < path.length(); i++) {
                if (path.charAt(i) == '/') {
                    count++;
                }
            }
            return count;
        }
    }

    /**
     * El JSON del sistema de archivos leído y validado (todavía no cargado).
     *
     * @param entries entradas en el orden del archivo
     */
    public record Image(DiskSettings disk, LinkedList<Entry> entries) {

        /** Cantidad de directorios y archivos. */
        public int size() {
            return entries.size();
        }
    }

    private FileSystemJson() {
    }

    // =====================================================================
    // Leer
    // =====================================================================

    /**
     * Lee y valida el archivo (formato y datos), sin cargarlo.
     *
     * @throws IOException              si no se puede leer
     * @throws IllegalArgumentException si el JSON no es válido (el mensaje explica dónde y por qué)
     */
    public static Image read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Como read, desde el texto. */
    public static Image parse(String text) {
        JsonValue root = JsonParser.parse(text).requireObject("El JSON del sistema de archivos");
        root.requireOnlyKeys("el JSON del sistema de archivos", "diskConfig", "fileSystem");

        JsonValue disk = root.get("diskConfig", "el JSON del sistema de archivos").requireObject("\"diskConfig\"");
        disk.requireOnlyKeys("\"diskConfig\"", "totalBlocks", "initialHead");
        JsonValue totalValue = disk.get("totalBlocks", "\"diskConfig\"");
        int total = totalValue.asInt("\"totalBlocks\"");
        if (total < MIN_BLOCKS || total > MAX_BLOCKS) {
            throw totalValue.error("\"totalBlocks\" es " + total + ": debe estar entre " + MIN_BLOCKS + " y "
                    + MAX_BLOCKS);
        }
        JsonValue headValue = disk.get("initialHead", "\"diskConfig\"");
        int head = headValue.asInt("\"initialHead\"");
        if (head < 0 || head >= total) {
            throw headValue.error("\"initialHead\" es " + head + ": debe estar entre 0 y " + (total - 1));
        }

        LinkedList<JsonValue> items = root.get("fileSystem", "el JSON del sistema de archivos")
                .requireArray("\"fileSystem\"").getItems();
        LinkedList<Entry> entries = new LinkedList<>();
        int number = 0;
        while (!items.isEmpty()) {
            number++;
            entries.addLast(readEntry(items.removeFirst(), number));
        }
        return new Image(new DiskSettings(total, head), entries);
    }

    private static Entry readEntry(JsonValue item, int number) {
        String where = "la entrada " + number;
        item.requireObject("La entrada " + number + " de \"fileSystem\"");
        String path = item.get("path", where).asString("\"path\"").trim();
        String label = "la entrada " + number + " (" + path + ")";
        String type = item.get("type", label).asString("\"type\"").trim().toUpperCase();
        String owner = item.get("owner", label).asString("\"owner\"").trim();
        if (owner.isEmpty()) {
            throw item.get("owner", label).error("\"owner\" está vacío en " + label);
        }
        JsonValue permValue = item.get("permissions", label);
        Permission permission;
        try {
            permission = Permission.fromLabel(permValue.asString("\"permissions\""));
        } catch (IllegalArgumentException e) {
            throw permValue.error(e.getMessage() + " en " + label);
        }
        String creator = FileSystem.LOADED_BY;
        if (item.has("creator")) {
            creator = item.get("creator", label).asString("\"creator\"").trim();
            if (creator.isEmpty()) {
                throw item.get("creator", label).error("\"creator\" está vacío en " + label);
            }
        }

        if (type.equals(TYPE_DIRECTORY)) {
            item.requireOnlyKeys(label, "path", "type", "owner", "permissions", "creator");
            return new Entry(number, path, true, new int[0], owner, permission, creator);
        }
        if (!type.equals(TYPE_FILE)) {
            throw item.get("type", label).error("\"type\" debe ser FILE o DIRECTORY en " + label + ", no " + type);
        }
        item.requireOnlyKeys(label, "path", "type", "startBlock", "blocks", "chain", "owner", "permissions", "creator");
        JsonValue startValue = item.get("startBlock", label);
        int start = startValue.asInt("\"startBlock\"");
        JsonValue blocksValue = item.get("blocks", label);
        int blocks = blocksValue.asInt("\"blocks\"");
        if (blocks < 1 || blocks > MAX_BLOCKS) {
            throw blocksValue.error("\"blocks\" debe estar entre 1 y " + MAX_BLOCKS + " en " + label);
        }
        if (start < 0) {
            throw startValue.error("\"startBlock\" no puede ser negativo en " + label);
        }
        int[] chain;
        if (item.has("chain")) {
            JsonValue chainValue = item.get("chain", label).requireArray("\"chain\"");
            LinkedList<JsonValue> parts = chainValue.getItems();
            if (parts.size() != blocks) {
                throw chainValue.error("\"chain\" tiene " + parts.size() + " bloques y \"blocks\" dice " + blocks
                        + " en " + label);
            }
            chain = new int[blocks];
            for (int i = 0; i < blocks; i++) {
                chain[i] = parts.removeFirst().asInt("cada bloque de \"chain\"");
            }
            if (chain[0] != start) {
                throw chainValue.error("\"chain\" empieza en " + chain[0] + " pero \"startBlock\" es " + start
                        + " en " + label);
            }
        } else {
            if (start > Integer.MAX_VALUE - blocks) {
                throw startValue.error(blocks + " bloques desde el " + start + " se salen del disco en " + label);
            }
            chain = new int[blocks];
            for (int i = 0; i < blocks; i++) {
                chain[i] = start + i;               // si se sale de la zona de archivos, BlockMap lo rechaza al cargar
            }
        }
        return new Entry(number, path, false, chain, owner, permission, creator);
    }

    // =====================================================================
    // Cargar
    // =====================================================================

    /**
     * Carga las entradas en un sistema de archivos recién creado (solo la raíz).
     * Primero las prueba en un disco de prueba del mismo tamaño: si alguna falla, no
     * se carga nada y el mensaje dice qué entrada y por qué.
     *
     * @throws IllegalArgumentException si el disco no tiene el tamaño del JSON, el
     *                                  sistema de archivos no está vacío o una entrada no se puede cargar
     */
    public static void load(Image image, FileSystem fs) {
        if (image == null || fs == null) {
            throw new IllegalArgumentException("Faltan el JSON leído o el sistema de archivos");
        }
        int fileBlocks = fs.getBlockMap().getFileBlocks();
        if (fileBlocks != image.disk().totalBlocks()) {
            throw new IllegalArgumentException("El disco del computador tiene " + fileBlocks
                    + " bloques para archivos y el JSON dice " + image.disk().totalBlocks());
        }
        if (fs.list().size() > 1) {
            throw new IllegalArgumentException("El sistema de archivos ya tiene contenido: se carga en uno vacío");
        }
        LinkedList<Entry> ordered = byDepth(image.entries());
        FileSystem trial = new FileSystem(new BlockMap(fileBlocks, 0), fs.getRootOwner());
        loadAll(ordered, trial);                     // si algo falla, aquí y sin tocar el real
        loadAll(ordered, fs);
    }

    /** Lee el archivo y lo carga (read + load). */
    public static Image load(Path file, FileSystem fs) throws IOException {
        Image image = read(file);
        load(image, fs);
        return image;
    }

    private static void loadAll(LinkedList<Entry> ordered, FileSystem fs) {
        LinkedList<Entry> all = ordered.copy();
        while (!all.isEmpty()) {
            Entry e = all.removeFirst();
            try {
                if (e.directory()) {
                    fs.loadDirectory(e.path(), e.owner(), e.permission(), e.creator());
                } else {
                    fs.loadFile(e.path(), e.chain(), e.owner(), e.permission(), e.creator());
                }
            } catch (IllegalArgumentException | IllegalStateException ex) {
                throw new IllegalArgumentException("Entrada " + e.number() + " (" + e.path() + "): " + ex.getMessage(), ex);
            }
        }
    }

    /** De menos a más profunda; entre iguales, en el orden del JSON (insertSorted es estable). */
    private static LinkedList<Entry> byDepth(LinkedList<Entry> entries) {
        LinkedList<Entry> sorted = new LinkedList<>();
        LinkedList<Entry> all = entries.copy();
        while (!all.isEmpty()) {
            sorted.insertSorted(all.removeFirst(), (a, b) -> Integer.compare(a.depth(), b.depth()));
        }
        return sorted;
    }

    // =====================================================================
    // Guardar
    // =====================================================================

    /**
     * El sistema de archivos tal como está ahora, en el formato del enunciado.
     *
     * Este JSON solo describe la zona de archivos (el tamaño del swap viene en el JSON de
     * procesos), así que al cargarlo el cabezal debe estar entre 0 y totalBlocks − 1. Si al
     * guardar el cabezal está en el swap (por ejemplo atendiendo una suspensión en el bloque
     * 250), se guarda el último bloque de archivos: el archivo siempre se puede volver a cargar.
     *
     * @param initialHead posición del cabezal que se guarda (normalmente la actual)
     */
    public static String write(FileSystem fs, int initialHead) {
        if (fs == null) {
            throw new IllegalArgumentException("Falta el sistema de archivos");
        }
        int fileBlocks = fs.getBlockMap().getFileBlocks();
        int head = Math.max(0, Math.min(initialHead, fileBlocks - 1));   // en el swap → último bloque de archivos
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"diskConfig\": {\n    \"totalBlocks\": ").append(fileBlocks)
                .append(",\n    \"initialHead\": ").append(head).append("\n  },\n  \"fileSystem\": [");
        LinkedList<FileInfo> all = fs.list();
        all.removeFirst();                           // la raíz no se guarda: siempre existe
        boolean first = true;
        while (!all.isEmpty()) {
            FileInfo f = all.removeFirst();
            sb.append(first ? "\n" : ",\n").append("    {\n");
            first = false;
            field(sb, "path", quote(f.getPath()), false);
            field(sb, "type", quote(f.isDirectory() ? TYPE_DIRECTORY : TYPE_FILE), false);
            if (!f.isDirectory()) {
                int[] chain = f.getChain();
                field(sb, "startBlock", String.valueOf(f.getFirstBlock()), false);
                field(sb, "blocks", String.valueOf(f.getBlockCount()), false);
                if (!consecutive(chain)) {
                    field(sb, "chain", list(chain), false);
                }
            }
            field(sb, "owner", quote(f.getOwner()), false);
            boolean hasCreator = !FileSystem.LOADED_BY.equals(f.getCreator());
            field(sb, "permissions", quote(f.getPermission().getJsonName()), !hasCreator);
            if (hasCreator) {
                field(sb, "creator", quote(f.getCreator()), true);
            }
            sb.append("    }");
        }
        sb.append(first ? "]\n}\n" : "\n  ]\n}\n");
        return sb.toString();
    }

    /** Guarda write(fs, initialHead) en el archivo (UTF-8). */
    public static void save(FileSystem fs, int initialHead, Path file) throws IOException {
        Files.writeString(file, write(fs, initialHead), StandardCharsets.UTF_8);
    }

    private static void field(StringBuilder sb, String key, String value, boolean last) {
        sb.append("      \"").append(key).append("\": ").append(value).append(last ? "\n" : ",\n");
    }

    private static boolean consecutive(int[] chain) {
        for (int i = 1; i < chain.length; i++) {
            if (chain[i] != chain[i - 1] + 1) {
                return false;
            }
        }
        return true;
    }

    private static String list(int[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(values[i]);
        }
        return sb.append(']').toString();
    }

    /** Texto JSON con las comillas, barras y caracteres de control escapados. */
    static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
