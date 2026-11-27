package com.unimet.avilaos.config;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.IoOperation;
import com.unimet.avilaos.os.disk.DiskPolicyType;
import com.unimet.avilaos.os.fs.FsOperation;
import com.unimet.avilaos.os.fs.Permission;
import com.unimet.avilaos.os.scheduling.PolicyType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON de procesos y configuración (formato del enunciado): la configuración global,
 * el computador y los procesos de E/S que se cargan al iniciar.
 *
 * <pre>
 * { "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "FIFO" },
 *   "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
 *   "processes": [
 *     { "name": "Proc_95", "memory": 16, "deadline": 500, "instructions": 2,
 *       "targetPath": "/sistema/config.sys", "operation": "READ" } ] }
 * </pre>
 *
 * Campos opcionales (el enunciado no los trae; tienen un valor por defecto):
 * <ul>
 *   <li>globalConfig: "initialDirection" (UP o DOWN) y "positionsPerCycle". Si faltan, quedan
 *       sin valor (null y 0 en Settings) y SimulationLoader usa los de la configuración del
 *       diálogo o del CSV (DiskConfig);</li>
 *   <li>proceso: "priority" (por defecto 1); "newName" (UPDATE que renombra; sin él,
 *       UPDATE reescribe el archivo en su lugar); para CREATE, "targetPath" es la ruta
 *       nueva y se agregan "blocks" (archivo), "permissions" (por defecto public),
 *       "type" (FILE o DIRECTORY, por defecto FILE) y "owner" (dueño de un directorio).</li>
 * </ul>
 *
 * Decisiones:
 * <ul>
 *   <li>"quantum": 0 solo tiene sentido si la política no es Round Robin: se guarda el
 *       quantum por defecto (3) para cuando la cambien desde Planificación. Con Round
 *       Robin, 0 es un error.</li>
 *   <li>Las operaciones son CREATE, READ, UPDATE y DELETE: SUSPEND y RESUME las genera
 *       el planificador a mediano plazo, no un proceso.</li>
 *   <li>Cada error dice el proceso y la línea y columna; claves desconocidas son un error.</li>
 * </ul>
 * Los datos del disco que no están aquí (bloques y cabezal) vienen del JSON del
 * sistema de archivos; los junta SimulationLoader.
 *
 * @author NicollPinzon
 */
public final class ProcessesJson {

    /** Quantum que se guarda cuando el JSON trae 0 (política sin quantum). */
    public static final int DEFAULT_QUANTUM = 3;
    public static final int DEFAULT_PRIORITY = 1;
    /** RAM máxima que se acepta (MB), para que un número enorme no se acepte por error. */
    public static final int MAX_RAM = 1_000_000;

    /**
     * Lo que dice el JSON fuera de los procesos.
     *
     * @param cycleDurationMs   duración del ciclo
     * @param swapBlocks        bloques del área de intercambio
     * @param diskPolicy        política de disco inicial
     * @param headDirection     dirección inicial del cabezal, o null si el JSON no la trae
     * @param positionsPerCycle posiciones del cabezal por ciclo, o 0 si el JSON no las trae
     * @param ramCapacity       RAM del computador
     * @param cpuPolicy         política de CPU inicial
     * @param quantum           quantum de Round Robin (DEFAULT_QUANTUM si el JSON trae 0)
     */
    public record Settings(long cycleDurationMs, int swapBlocks, DiskPolicyType diskPolicy,
            HeadDirection headDirection, int positionsPerCycle, int ramCapacity, PolicyType cpuPolicy, int quantum) {
    }

    /**
     * Un proceso de E/S del JSON, ya validado.
     *
     * @param number     número del proceso en el JSON (desde 1), para los mensajes
     * @param path       ruta del archivo o directorio (en CREATE, la ruta nueva)
     * @param newName    UPDATE: el nombre nuevo, o null si reescribe en su lugar
     * @param sizeBlocks CREATE de archivo: su tamaño; en las demás, 0
     * @param directory  CREATE: true si crea un directorio
     * @param permission CREATE: el permiso; en las demás, null
     * @param owner      CREATE de directorio: su dueño (null = quien lo crea)
     */
    public record ProcessSpec(int number, String name, int priority, int memory, int instructions, long deadline,
            IoOperation operation, String path, String newName, int sizeBlocks, boolean directory,
            Permission permission, String owner) {

        /** La operación del sistema de archivos, con el nombre del proceso como creador. */
        public FsOperation toOperation() {
            return switch (operation) {
                case READ -> FsOperation.read(path, name);
                case DELETE -> FsOperation.delete(path, name);
                case UPDATE -> newName == null ? FsOperation.update(path, name) : FsOperation.rename(path, newName, name);
                case CREATE -> {
                    int slash = path.lastIndexOf('/');
                    if (!path.startsWith("/") || slash == path.length() - 1) {
                        throw new IllegalArgumentException("La ruta nueva " + path
                                + " debe empezar con / y terminar con el nombre de lo que se crea");
                    }
                    String parent = slash == 0 ? "/" : path.substring(0, slash);
                    String created = path.substring(slash + 1);
                    yield directory ? FsOperation.createDirectory(parent, created, owner, permission, name)
                            : FsOperation.createFile(parent, created, sizeBlocks, permission, name);
                }
                default -> throw new IllegalStateException(operation + " no es una operación de un proceso");
            };
        }
    }

    /**
     * El JSON de procesos leído y validado.
     *
     * @param processes en el orden del archivo
     */
    public record Image(Settings settings, LinkedList<ProcessSpec> processes) {
    }

    private ProcessesJson() {
    }

    /**
     * Lee y valida el archivo.
     *
     * @throws IOException              si no se puede leer
     * @throws IllegalArgumentException si el JSON no es válido (el mensaje explica dónde y por qué)
     */
    public static Image read(Path file) throws IOException {
        return parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** Como read, desde el texto. */
    public static Image parse(String text) {
        JsonValue root = JsonParser.parse(text).requireObject("El JSON de procesos");
        root.requireOnlyKeys("el JSON de procesos", "globalConfig", "computer", "processes");

        JsonValue global = root.get("globalConfig", "el JSON de procesos").requireObject("\"globalConfig\"");
        global.requireOnlyKeys("\"globalConfig\"", "cycleDurationMs", "swapSizeBlocks", "initialDiskPolicy",
                "initialDirection", "positionsPerCycle");
        long cycle = range(global.get("cycleDurationMs", "\"globalConfig\""), "\"cycleDurationMs\"",
                SimulationConfig.MIN_CYCLE_MS, SimulationConfig.MAX_CYCLE_MS);
        int swap = (int) range(global.get("swapSizeBlocks", "\"globalConfig\""), "\"swapSizeBlocks\"", 0,
                DiskConfig.MAX_SWAP_BLOCKS);
        JsonValue diskPolicyValue = global.get("initialDiskPolicy", "\"globalConfig\"");
        DiskPolicyType diskPolicy;
        try {
            diskPolicy = DiskPolicyType.fromLabel(diskPolicyValue.asString("\"initialDiskPolicy\""));
        } catch (IllegalArgumentException e) {
            throw diskPolicyValue.error(e.getMessage() + " (se aceptan: " + String.join(", ", DiskPolicyType.labels())
                    + ")");
        }
        HeadDirection direction = null;                   // sin valor: la de la configuración
        if (global.has("initialDirection")) {
            JsonValue d = global.get("initialDirection", "\"globalConfig\"");
            String text2 = d.asString("\"initialDirection\"").trim().toUpperCase();
            if (text2.equals("UP")) {
                direction = HeadDirection.UP;
            } else if (text2.equals("DOWN")) {
                direction = HeadDirection.DOWN;
            } else {
                throw d.error("\"initialDirection\" debe ser UP o DOWN, no " + text2);
            }
        }
        int positions = 0;                                // sin valor: las de la configuración
        if (global.has("positionsPerCycle")) {
            positions = (int) range(global.get("positionsPerCycle", "\"globalConfig\""), "\"positionsPerCycle\"", 1,
                    DiskConfig.MAX_POSITIONS_PER_CYCLE);
        }

        JsonValue computer = root.get("computer", "el JSON de procesos").requireObject("\"computer\"");
        computer.requireOnlyKeys("\"computer\"", "ramCapacity", "policy", "quantum");
        int ram = (int) range(computer.get("ramCapacity", "\"computer\""), "\"ramCapacity\"", 1, MAX_RAM);
        JsonValue policyValue = computer.get("policy", "\"computer\"");
        PolicyType cpuPolicy;
        try {
            cpuPolicy = PolicyType.fromLabel(policyValue.asString("\"policy\""));
        } catch (IllegalArgumentException e) {
            throw policyValue.error(e.getMessage());
        }
        JsonValue quantumValue = computer.get("quantum", "\"computer\"");
        int quantum = (int) range(quantumValue, "\"quantum\"", 0, SimulationConfig.MAX_QUANTUM);
        if (quantum == 0) {
            if (cpuPolicy.usesQuantum()) {
                throw quantumValue.error("\"quantum\" es 0 y la política " + cpuPolicy.getLabel()
                        + " necesita un quantum de 1 a " + SimulationConfig.MAX_QUANTUM);
            }
            quantum = DEFAULT_QUANTUM;
        }

        LinkedList<JsonValue> items = root.get("processes", "el JSON de procesos").requireArray("\"processes\"")
                .getItems();
        LinkedList<ProcessSpec> processes = new LinkedList<>();
        int number = 0;
        while (!items.isEmpty()) {
            number++;
            processes.addLast(readProcess(items.removeFirst(), number, ram));
        }
        return new Image(new Settings(cycle, swap, diskPolicy, direction, positions, ram, cpuPolicy, quantum),
                processes);
    }

    private static ProcessSpec readProcess(JsonValue item, int number, int ram) {
        item.requireObject("El proceso " + number + " de \"processes\"");
        JsonValue nameValue = item.get("name", "el proceso " + number);
        String name;
        try {
            name = ConfigText.requireName(nameValue.asString("\"name\""), "El nombre del proceso " + number,
                    ProcessConfig.NAME_MAX_LENGTH);
        } catch (IllegalArgumentException e) {
            throw nameValue.error(e.getMessage());
        }
        String where = "el proceso " + number + " (" + name + ")";
        item.requireOnlyKeys(where, "name", "priority", "memory", "deadline", "instructions", "targetPath", "operation",
                "newName", "blocks", "permissions", "type", "owner");
        int priority = DEFAULT_PRIORITY;
        if (item.has("priority")) {
            priority = (int) range(item.get("priority", where), "\"priority\" de " + name, 0, ProcessConfig.MAX_PRIORITY);
        }
        int memory = (int) range(item.get("memory", where), "\"memory\" de " + name, 1, ram);
        long deadline = range(item.get("deadline", where), "\"deadline\" de " + name, 1, ProcessConfig.MAX_DEADLINE);
        int instructions = (int) range(item.get("instructions", where), "\"instructions\" de " + name, 1,
                ProcessConfig.MAX_INSTRUCTIONS);
        JsonValue pathValue = item.get("targetPath", where);
        String path = pathValue.asString("\"targetPath\"").trim();
        JsonValue opValue = item.get("operation", where);
        IoOperation op;
        try {
            op = IoOperation.fromLabel(opValue.asString("\"operation\""));
        } catch (IllegalArgumentException e) {
            throw opValue.error(e.getMessage() + " en " + where + " (se aceptan: CREATE, READ, UPDATE, DELETE)");
        }
        if (!op.isFileOperation()) {
            throw opValue.error(op.name() + " no es una operación de un proceso en " + where
                    + ": la genera el planificador a mediano plazo (se aceptan: CREATE, READ, UPDATE, DELETE)");
        }

        String newName = null;
        int size = 0;
        boolean directory = false;
        Permission permission = null;
        String owner = null;
        if (op == IoOperation.CREATE) {
            String type = item.has("type") ? item.get("type", where).asString("\"type\"").trim().toUpperCase() : "FILE";
            if (!type.equals("FILE") && !type.equals("DIRECTORY")) {
                throw item.get("type", where).error("\"type\" debe ser FILE o DIRECTORY en " + where + ", no " + type);
            }
            directory = type.equals("DIRECTORY");
            permission = Permission.PUBLIC;
            if (item.has("permissions")) {
                JsonValue p = item.get("permissions", where);
                try {
                    permission = Permission.fromLabel(p.asString("\"permissions\""));
                } catch (IllegalArgumentException e) {
                    throw p.error(e.getMessage() + " en " + where);
                }
            }
            if (directory) {
                onlyFor(item, where, "blocks", "un CREATE de archivo");
                if (item.has("owner")) {
                    owner = item.get("owner", where).asString("\"owner\"").trim();
                }
            } else {
                size = (int) range(item.get("blocks", where), "\"blocks\" de " + name, 1, DiskConfig.MAX_FILE_BLOCKS);
                onlyFor(item, where, "owner", "un CREATE de directorio (el dueño de un archivo es quien lo crea)");
            }
            onlyFor(item, where, "newName", "UPDATE");
        } else {
            for (String key : new String[] {"blocks", "permissions", "type", "owner"}) {
                onlyFor(item, where, key, "CREATE");
            }
            if (op == IoOperation.UPDATE) {
                if (item.has("newName")) {
                    newName = item.get("newName", where).asString("\"newName\"").trim();
                }
            } else {
                onlyFor(item, where, "newName", "UPDATE");
            }
        }
        ProcessSpec spec = new ProcessSpec(number, name, priority, memory, instructions, deadline, op, path, newName,
                size, directory, permission, owner);
        try {
            spec.toOperation();                      // valida la ruta y el nombre nuevo
        } catch (IllegalArgumentException e) {
            throw pathValue.error(e.getMessage() + " en " + where);
        }
        return spec;
    }

    /** Un campo que solo vale para cierta operación es un error en las demás. */
    private static void onlyFor(JsonValue item, String where, String key, String operation) {
        if (item.has(key)) {
            throw item.get(key, where).error("\"" + key + "\" solo se usa en " + operation + " (" + where + ")");
        }
    }

    private static long range(JsonValue value, String what, long min, long max) {
        long n = value.asLong(what);
        if (n < min || n > max) {
            throw value.error(what + " es " + n + ": debe estar entre " + min + " y " + max);
        }
        return n;
    }
}
