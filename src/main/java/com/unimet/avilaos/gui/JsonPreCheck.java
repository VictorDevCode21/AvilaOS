package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.SimulationConfig;
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
    /** Disk policies the simulator knows. PROVISIONAL: will come from the disk policy enum. */
    private static final String[] DISK_POLICIES = MainWindow.DISK_POLICIES;

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
        Integer total = number(text, "totalBlocks");
        if (total == null) {
            return Result.fail("Falta \"totalBlocks\" (número de bloques del disco).");
        }
        if (total < MIN_BLOCKS || total > MAX_BLOCKS) {
            return Result.fail("\"totalBlocks\" es " + total + ": debe estar entre " + MIN_BLOCKS + " y "
                    + MAX_BLOCKS + ".");
        }
        Integer head = number(text, "initialHead");
        if (head == null) {
            return Result.fail("Falta \"initialHead\" (posición inicial del cabezal).");
        }
        if (head < 0 || head >= total) {
            return Result.fail("\"initialHead\" es " + head + ": debe estar entre 0 y " + (total - 1) + ".");
        }
        int entries = count(text, "\"path\"");
        if (entries == 0) {
            return Result.fail("\"fileSystem\" no tiene entradas (cada una necesita \"path\").");
        }
        Matcher start = Pattern.compile("\"startBlock\"\\s*:\\s*(-?\\d+)").matcher(text);
        while (start.find()) {
            int block = Integer.parseInt(start.group(1));
            if (block < 0 || block >= total) {
                return Result.fail("Hay un \"startBlock\" = " + block + " fuera del disco (0 a " + (total - 1) + ").");
            }
        }
        return new Result(true, total + " bloques · cabezal en " + head + " · " + entries + " entradas · "
                + usedBlocks(text) + " bloques usados");
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
        Integer cycle = number(text, "cycleDurationMs");
        if (cycle == null || cycle < SimulationConfig.MIN_CYCLE_MS || cycle > SimulationConfig.MAX_CYCLE_MS) {
            return Result.fail("\"cycleDurationMs\" debe ser un número entre " + SimulationConfig.MIN_CYCLE_MS
                    + " y " + SimulationConfig.MAX_CYCLE_MS + " ms.");
        }
        Integer swap = number(text, "swapSizeBlocks");
        if (swap == null || swap < 1 || swap > MAX_BLOCKS) {
            return Result.fail("\"swapSizeBlocks\" debe ser un número entre 1 y " + MAX_BLOCKS + ".");
        }
        String diskPolicy = string(text, "initialDiskPolicy");
        if (diskPolicy == null || !contains(DISK_POLICIES, diskPolicy)) {
            return Result.fail("\"initialDiskPolicy\" debe ser una de: " + String.join(", ", DISK_POLICIES) + ".");
        }
        Integer ram = number(text, "ramCapacity");
        if (ram == null || ram < 1) {
            return Result.fail("\"ramCapacity\" debe ser un número mayor que 0.");
        }
        String policy = string(text, "policy");
        try {
            PolicyType.fromLabel(policy);
        } catch (IllegalArgumentException e) {
            return Result.fail("\"policy\" (CPU) no es válida: " + policy + ".");
        }
        Integer quantum = number(text, "quantum");
        if (quantum == null || quantum < 0) {
            return Result.fail("\"quantum\" debe ser un número mayor o igual a 0.");
        }
        int processes = count(text, "\"name\"");
        Matcher memory = Pattern.compile("\"memory\"\\s*:\\s*(-?\\d+)").matcher(text);
        while (memory.find()) {
            int mb = Integer.parseInt(memory.group(1));
            if (mb < 1 || mb > ram) {
                return Result.fail("Un proceso pide " + mb + " de memoria: debe estar entre 1 y la RAM (" + ram + ").");
            }
        }
        Matcher operation = Pattern.compile("\"operation\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
        while (operation.find()) {
            String op = operation.group(1);
            if (!contains(new String[] {"CREATE", "READ", "UPDATE", "DELETE"}, op)) {
                return Result.fail("Operación desconocida: \"" + op + "\" (usa CREATE, READ, UPDATE o DELETE).");
            }
        }
        return new Result(true, processes + " procesos · RAM " + ram + " · CPU " + policy
                + (quantum > 0 ? " q=" + quantum : "") + " · disco " + diskPolicy + " · swap " + swap);
    }

    /**
     * Checks that the two files fit together: the swap area (processes JSON) and the files already on
     * the disk (file system JSON) must fit in the disk.
     *
     * @return null if they fit, or a warning to show
     */
    public static String crossCheck(File fileSystem, File processes) {
        try {
            String fs = read(fileSystem);
            String procs = read(processes);
            Integer total = number(fs, "totalBlocks");
            Integer swap = number(procs, "swapSizeBlocks");
            if (total == null || swap == null) {
                return null;
            }
            int used = usedBlocks(fs);
            if (swap >= total) {
                return "La swap (" + swap + " bloques) no cabe en un disco de " + total + " bloques.";
            }
            if (used + swap > total) {
                return "Los archivos usan " + used + " bloques y la swap pide " + swap + ": suman " + (used + swap)
                        + " y el disco tiene " + total + ". No quedará espacio para la swap o para archivos nuevos.";
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------- Helpers ----------

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

    /** First integer value of "key", or null if missing or not an integer. */
    private static Integer number(String text, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)\\s*[,}\\n]").matcher(text);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
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
    private static int usedBlocks(String text) {
        int used = 0;
        Matcher m = Pattern.compile("\"blocks\"\\s*:\\s*(\\d+)").matcher(text);
        while (m.find()) {
            used += Integer.parseInt(m.group(1));
        }
        return used;
    }

    private static boolean contains(String[] values, String value) {
        for (String v : values) {
            if (v.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
