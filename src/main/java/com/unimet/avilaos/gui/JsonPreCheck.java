package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.hardware.IoOperation;
import com.unimet.avilaos.os.disk.DiskPolicyType;
import com.unimet.avilaos.os.scheduling.PolicyType;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quick checks of the two JSON files before the simulation starts, so the Load dialog can explain
 * a bad file right away (wrong type, missing keys, values out of range) without stopping anything.
 *
 * PROVISIONAL: this is NOT the JSON parser. It only reads a few numbers with simple patterns. The
 * real parser (file system and processes) belongs to the configuration module; when it exists, the
 * controller can call it from the dialog's validators instead of (or after) these checks.
 *
 * @author donatovodola
 */
public final class JsonPreCheck {

    /** Disk size allowed by the statement. */
    public static final int MIN_BLOCKS = 100;
    public static final int MAX_BLOCKS = 500;
    /** Biggest file accepted (a few hundred KB is already a huge simulation). */
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private JsonPreCheck() {
    }

    /**
     * Result of a check.
     *
     * @param ok      true if the file can be used
     * @param message summary when ok (e.g. "200 bloques · 13 entradas"), or what is wrong
     */
    public record Result(boolean ok, String message) {

        static Result fail(String message) {
            return new Result(false, message);
        }
    }

    /** Checks the file system JSON: diskConfig (totalBlocks 100-500, initialHead in range) and fileSystem. */
    public static Result checkFileSystem(File file) {
        String text;
        try {
            text = read(file);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
        if (!has(text, "diskConfig") || !has(text, "fileSystem")) {
            return Result.fail("No parece el JSON del sistema de archivos: faltan \"diskConfig\" o \"fileSystem\".");
        }
        try {
            return checkFileSystemText(text);
        } catch (Invalid e) {
            return Result.fail(e.getMessage());
        }
    }

    private static Result checkFileSystemText(String text) {
        int total = requiredInt(text, "totalBlocks", "número de bloques del disco");
        if (total < MIN_BLOCKS || total > MAX_BLOCKS) {
            return Result.fail("\"totalBlocks\" es " + total + ": debe estar entre " + MIN_BLOCKS + " y "
                    + MAX_BLOCKS + ".");
        }
        int head = requiredInt(text, "initialHead", "posición inicial del cabezal");
        if (head < 0 || head >= total) {
            return Result.fail("\"initialHead\" es " + head + ": debe estar entre 0 y " + (total - 1) + ".");
        }
        int entries = count(text, "\"path\"");
        if (entries == 0) {
            return Result.fail("\"fileSystem\" no tiene entradas (cada una necesita \"path\").");
        }
        Matcher start = valuesOf(text, "startBlock");
        while (start.find()) {
            int block = toInt(start.group(1), "startBlock");
            if (block < 0 || block >= total) {
                return Result.fail("Hay un \"startBlock\" = " + block + " fuera del disco (0 a " + (total - 1) + ").");
            }
        }
        // totalBlocks is the file zone: the swap area goes after it (BlockMap, DiskScheduler), so the files must fit here
        long used = usedBlocks(text);
        if (used > total) {
            return Result.fail("Los archivos ocupan " + used + " bloques y el disco tiene " + total
                    + " para archivos (\"totalBlocks\").");
        }
        return new Result(true, total + " bloques · cabezal en " + head + " · " + entries + " entradas · "
                + used + " bloques usados");
    }

    /** Checks the processes JSON: globalConfig, computer and processes, with types and ranges. */
    public static Result checkProcesses(File file) {
        String text;
        try {
            text = read(file);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
        if (!has(text, "globalConfig") || !has(text, "computer") || !has(text, "processes")) {
            return Result.fail("No parece el JSON de procesos: faltan \"globalConfig\", \"computer\" o \"processes\".");
        }
        try {
            return checkProcessesText(text);
        } catch (Invalid e) {
            return Result.fail(e.getMessage());
        }
    }

    private static Result checkProcessesText(String text) {
        int cycle = requiredInt(text, "cycleDurationMs", "duración del ciclo");
        if (cycle < SimulationConfig.MIN_CYCLE_MS || cycle > SimulationConfig.MAX_CYCLE_MS) {
            return Result.fail("\"cycleDurationMs\" debe ser un número entre " + SimulationConfig.MIN_CYCLE_MS
                    + " y " + SimulationConfig.MAX_CYCLE_MS + " ms.");
        }
        int swap = requiredInt(text, "swapSizeBlocks", "tamaño del área de intercambio");
        if (swap < 1 || swap > MAX_BLOCKS) {
            return Result.fail("\"swapSizeBlocks\" debe ser un número entre 1 y " + MAX_BLOCKS + ".");
        }
        String diskPolicy = string(text, "initialDiskPolicy");
        DiskPolicyType disk;
        try {
            disk = DiskPolicyType.fromLabel(diskPolicy);
        } catch (IllegalArgumentException e) {
            return Result.fail("\"initialDiskPolicy\" debe ser una de: " + String.join(", ", DiskPolicyType.labels())
                    + ".");
        }
        int ram = requiredInt(text, "ramCapacity", "RAM del computador");
        if (ram < 1) {
            return Result.fail("\"ramCapacity\" debe ser un número mayor que 0.");
        }
        String policy = string(text, "policy");
        PolicyType cpu;
        try {
            cpu = PolicyType.fromLabel(policy);
        } catch (IllegalArgumentException e) {
            return Result.fail("\"policy\" (CPU) no es válida: " + policy + " (se aceptan: " + PolicyType.accepted() + ").");
        }
        // a policy that uses the quantum needs at least 1; the others ignore it (0 is fine)
        int quantum = requiredInt(text, "quantum", "quantum de Round Robin");
        int minQuantum = cpu.usesQuantum() ? 1 : 0;
        if (quantum < minQuantum || quantum > SimulationConfig.MAX_QUANTUM) {
            return Result.fail("\"quantum\" es " + quantum + ": debe estar entre " + minQuantum + " y "
                    + SimulationConfig.MAX_QUANTUM + (minQuantum == 1 ? " con " + cpu.getLabel() + "." : "."));
        }
        int processes = count(text, "\"name\"");
        Matcher memory = valuesOf(text, "memory");
        while (memory.find()) {
            int mb = toInt(memory.group(1), "memory");
            if (mb < 1 || mb > ram) {
                return Result.fail("Un proceso pide " + mb + " de memoria: debe estar entre 1 y la RAM (" + ram + ").");
            }
        }
        Matcher operation = Pattern.compile("\"operation\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
        while (operation.find()) {
            String op = operation.group(1);
            if (!isFileOperation(op)) {
                return Result.fail("Operación desconocida: \"" + op + "\" (usa CREATE, READ, UPDATE o DELETE).");
            }
        }
        return new Result(true, processes + " procesos · RAM " + ram + " · CPU " + policy
                + (quantum > 0 ? " q=" + quantum : "") + " · disco " + disk.getLabel() + " · swap " + swap);
    }

    /**
     * Checks that the two files fit together. The swap area does NOT take blocks from "totalBlocks":
     * it goes after the file zone (BlockMap, DiskScheduler), as in the statement's example, where the
     * files fill the 200 blocks and the swap adds 100 more. So this only warns about things that
     * would make processes fail later:
     * <ul>
     *   <li>a process asks for more memory than the swap has (1 unit of memory = 1 block), so it
     *       could never be suspended;</li>
     *   <li>a process reads, renames or deletes a path that is not in the file system JSON;</li>
     *   <li>a process creates a file and the file zone is already full.</li>
     * </ul>
     *
     * @return null if everything fits, or the warnings to show (one per line)
     */
    public static String crossCheck(File fileSystem, File processes) {
        try {
            String fs = read(fileSystem);
            String procs = read(processes);
            StringBuilder warnings = new StringBuilder();

            int swap = requiredInt(procs, "swapSizeBlocks", "tamaño del área de intercambio");
            int biggest = 0;
            Matcher memory = valuesOf(procs, "memory");
            while (memory.find()) {
                biggest = Math.max(biggest, toInt(memory.group(1), "memory"));
            }
            if (biggest > swap) {
                warn(warnings, "Un proceso pide " + biggest + " de memoria y la swap tiene " + swap
                        + " bloques: ese proceso no se podrá suspender.");
            }

            String[] targets = stringsOf(procs, "targetPath");
            String[] operations = stringsOf(procs, "operation");
            if (targets.length == operations.length) {        // otherwise the pairs can't be matched safely
                String[] paths = stringsOf(fs, "path");
                boolean creates = false;
                for (int i = 0; i < targets.length; i++) {
                    if ("CREATE".equalsIgnoreCase(operations[i])) {
                        creates = true;
                    } else if (!contains(paths, targets[i])) {
                        warn(warnings, "Un proceso de " + operations[i] + " usa " + targets[i]
                                + ", que no está en el sistema de archivos.");
                    }
                }
                int total = requiredInt(fs, "totalBlocks", "número de bloques del disco");
                if (creates && usedBlocks(fs) >= total) {
                    warn(warnings, "Hay procesos que crean archivos, pero los archivos ya llenan los " + total
                            + " bloques: se rechazarán hasta que se borre alguno.");
                }
            }
            return warnings.length() == 0 ? null : warnings.toString();
        } catch (IllegalArgumentException e) {      // includes Invalid: each file already shows its own error
            return null;
        }
    }

    private static void warn(StringBuilder warnings, String line) {
        if (warnings.length() > 0) {
            warnings.append('\n');
        }
        warnings.append(line);
    }

    /** Every string value of the key, in order (e.g. all "path" values). */
    private static String[] stringsOf(String text, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        String[] values = new String[count];
        m.reset();
        for (int i = 0; m.find(); i++) {
            values[i] = m.group(1);
        }
        return values;
    }

    private static boolean contains(String[] values, String value) {
        for (String v : values) {
            if (v.equals(value)) {
                return true;
            }
        }
        return false;
    }

    // ---------- Helpers ----------

    /** A value that is missing, not an integer or too big for an int: becomes a Result.fail, never escapes. */
    private static final class Invalid extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        Invalid(String message) {
            super(message);
        }
    }

    /**
     * Integer value of "key" (first match).
     *
     * @throws Invalid if the key is missing, the value is not an integer (e.g. "doscientos") or it does
     *                 not fit in an int (e.g. 99999999999)
     */
    private static int requiredInt(String text, String key, String meaning) {
        Matcher m = valuesOf(text, key);
        if (!m.find()) {
            throw new Invalid("Falta \"" + key + "\" (" + meaning + ").");
        }
        return toInt(m.group(1), key);
    }

    /** Matcher over every raw value of "key" (whatever comes after the colon, up to , } ] or end of line). */
    private static Matcher valuesOf(String text, String key) {
        return Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([^,}\\]\\r\\n]*)").matcher(text);
    }

    /** Parses a raw JSON value as an int, with a clear message instead of an exception. */
    private static int toInt(String raw, String key) {
        String value = raw.trim();
        if (!value.matches("-?\\d+")) {
            throw new Invalid("\"" + key + "\" debe ser un número entero (dice " + value + ").");
        }
        String digits = value.startsWith("-") ? value.substring(1) : value;
        if (digits.length() > 10 || Long.parseLong(value) > Integer.MAX_VALUE
                || Long.parseLong(value) < Integer.MIN_VALUE) {
            throw new Invalid("\"" + key + "\" es demasiado grande: " + value + ".");
        }
        return Integer.parseInt(value);
    }

    private static String read(File file) {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("El archivo no existe.");
        }
        if (!file.getName().toLowerCase().endsWith(".json")) {
            throw new IllegalArgumentException("El archivo debe tener extensión .json.");
        }
        if (file.length() == 0) {
            throw new IllegalArgumentException("El archivo está vacío.");
        }
        if (file.length() > MAX_BYTES) {
            throw new IllegalArgumentException("El archivo es demasiado grande (más de 5 MB).");
        }
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
            if (!text.startsWith("{") || !text.endsWith("}")) {
                throw new IllegalArgumentException("No es un JSON válido: debe empezar con { y terminar con }.");
            }
            return text;
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    private static boolean has(String text, String key) {
        return text.contains("\"" + key + "\"");
    }

    /** First string value of "key", or null. */
    private static String string(String text, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static int count(String text, String token) {
        int count = 0;
        int from = text.indexOf(token);
        while (from >= 0) {
            count++;
            from = text.indexOf(token, from + token.length());
        }
        return count;
    }

    /** Sum of every "blocks" value (blocks used by the files). */
    private static long usedBlocks(String text) {
        long used = 0;
        Matcher m = valuesOf(text, "blocks");
        while (m.find()) {
            used += toInt(m.group(1), "blocks");
        }
        return used;
    }

    /** A process of the JSON may only ask for a file operation (SUSPEND / RESUME belong to the kernel). */
    private static boolean isFileOperation(String op) {
        try {
            return IoOperation.fromLabel(op).isFileOperation();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
