package com.unimet.avilaos.config;

import com.unimet.avilaos.cluster.Cluster;
import com.unimet.avilaos.cluster.Computer;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.BlockMap;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.IoOperation;
import com.unimet.avilaos.os.fs.FileInfo;
import com.unimet.avilaos.os.fs.FileSystem;
import com.unimet.avilaos.os.fs.UserSession;
import com.unimet.avilaos.os.process.Pcb;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Arma una simulación lista para iniciar con los DOS JSON, que el enunciado pide
 * cargar antes de empezar: el del sistema de archivos (disco y archivos) y el de
 * procesos y configuración.
 *
 * Pasos de start:
 * <ol>
 *   <li>buildConfig: la configuración completa (ciclo, quantum, el computador y el
 *       disco = bloques y cabezal del JSON del sistema de archivos + swap, política,
 *       dirección y velocidad del JSON de procesos);</li>
 *   <li>Cluster.create con esa configuración (un computador con su disco vacío);</li>
 *   <li>FileSystemJson.load: los archivos y directorios en el disco del computador;</li>
 *   <li>los procesos de E/S, en el orden del JSON, como administrador (los carga el
 *       sistema al iniciar, no un usuario);</li>
 *   <li>si "waitForInitialRequests" está activo (por defecto sí), el disco espera a que esos
 *       procesos pidan el disco antes de su primera decisión, con el menor deadline de la carga
 *       como tope (Kernel.holdDiskForInitialLoad). Así la primera decisión ve la cola completa
 *       y los casos de prueba del enunciado salen en vivo igual que en la tabla.</li>
 * </ol>
 * El reloj no arranca: lo arranca quien llama, cuando la interfaz ya está lista.
 *
 * Precedencia de la configuración: los JSON mandan en todo lo que traen (bloques y cabezal
 * del sistema de archivos; ciclo, swap, política de disco, RAM, política de CPU y quantum de
 * procesos). La configuración del diálogo o del CSV (su DiskConfig) solo da el valor de las
 * claves opcionales que el JSON de procesos no trae: la dirección inicial del cabezal, las
 * posiciones por ciclo y si el disco espera a la carga inicial. Sin configuración, se usan las
 * de DiskConfig.defaults().
 *
 * Reiniciar con procesos nuevos conservando el disco modificado:
 * {@code start(FileSystemJson.snapshot(fsViejo, cabezal), nuevosProcesos)}.
 *
 * crossCheck avisa (sin impedir la carga) de lo que hará fallar a un proceso: una
 * ruta que no existe, un archivo que se crea donde ya hay otro, un proceso más grande
 * que el swap (nunca se podrá suspender), etc. El proceso
 * igual se crea y, al pedir su operación, termina con el motivo en el log. También
 * avisa de nombres repetidos (el enunciado no exige nombres únicos: en la tabla de
 * archivos se distinguen por el PID, "P5 (Proc_95)") y de una lista sin procesos
 * ("el disco y la RAM quedarán vacíos", como dice el enunciado).
 *
 * @author NicollPinzon
 */
public final class SimulationLoader {

    /**
     * La simulación armada.
     *
     * @param cluster    el clúster (un computador) con el disco cargado y los procesos entregados
     * @param config     la configuración con la que se armó
     * @param warnings   avisos de crossCheck, o null si no hay
     */
    public record Loaded(Cluster cluster, SimulationConfig config, String warnings) {

        /** El único computador de la simulación. */
        public Computer computer() {
            return cluster.getComputer(1);
        }
    }

    private SimulationLoader() {
    }

    /** La configuración completa que resulta de los dos JSON, con DiskConfig.defaults() para lo que no traen. */
    public static SimulationConfig buildConfig(FileSystemJson.Image fileSystem, ProcessesJson.Image processes) {
        return buildConfig(fileSystem, processes, DiskConfig.defaults());
    }

    /**
     * La configuración completa que resulta de los dos JSON (ver la precedencia en la clase).
     *
     * @param fallback disco de la configuración del diálogo o del CSV: da la dirección inicial, las
     *                 posiciones por ciclo y la espera de la carga inicial si el JSON de procesos no
     *                 las trae
     */
    public static SimulationConfig buildConfig(FileSystemJson.Image fileSystem, ProcessesJson.Image processes,
            DiskConfig fallback) {
        requireBoth(fileSystem, processes);
        if (fallback == null) {
            throw new IllegalArgumentException("Falta la configuración del disco por defecto");
        }
        ProcessesJson.Settings s = processes.settings();
        HeadDirection direction = s.headDirection() != null ? s.headDirection() : fallback.initialDirection();
        int positions = s.positionsPerCycle() > 0 ? s.positionsPerCycle() : fallback.positionsPerCycle();
        boolean waitForInitial = s.waitForInitialRequests() != null ? s.waitForInitialRequests()
                : fallback.waitForInitialRequests();
        SimulationConfig config = new SimulationConfig(s.cycleDurationMs(), s.quantum(), 0);   // sin red: 1 computador
        config.addComputer(s.ramCapacity(), s.cpuPolicy());
        config.setDisk(new DiskConfig(fileSystem.disk().totalBlocks(), s.swapBlocks(), s.diskPolicy(),
                fileSystem.disk().initialHead(), direction, positions, waitForInitial));
        config.validate();
        return config;
    }

    /**
     * Arma la simulación con los dos JSON ya leídos (ver los pasos en la clase). Si algo
     * falla después de crear el clúster (el sistema de archivos no carga o un proceso no se
     * puede crear), se apaga ese clúster antes de lanzar el error.
     *
     * @throws IllegalArgumentException si la configuración no es válida o el sistema de archivos no carga
     */
    public static Loaded start(FileSystemJson.Image fileSystem, ProcessesJson.Image processes) {
        return start(fileSystem, processes, DiskConfig.defaults());
    }

    /**
     * Como start, con el disco de la configuración del diálogo o del CSV como valor por defecto
     * de la dirección inicial, las posiciones por ciclo y la espera de la carga inicial (ver la
     * precedencia en la clase).
     *
     * @throws IllegalArgumentException si la configuración no es válida o el sistema de archivos no carga
     */
    public static Loaded start(FileSystemJson.Image fileSystem, ProcessesJson.Image processes, DiskConfig fallback) {
        SimulationConfig config = buildConfig(fileSystem, processes, fallback);
        Cluster cluster = Cluster.create(config);
        try {
            FileSystemJson.load(fileSystem, cluster.getComputer(1).getFileSystem());
            String warnings = crossCheck(fileSystem, processes);
            LinkedList<Pcb> initial = createProcesses(cluster, processes);
            if (config.getDisk().waitForInitialRequests() && !initial.isEmpty()) {
                cluster.getComputer(1).getKernel().holdDiskForInitialLoad(initial, smallestDeadline(initial));
            }
            return new Loaded(cluster, config, warnings);
        } catch (RuntimeException e) {
            cluster.shutdown();          // falle lo que falle, no queda un clúster a medias
            throw e;
        }
    }

    /** Crea los procesos del JSON en su orden y los devuelve (la carga inicial). */
    private static LinkedList<Pcb> createProcesses(Cluster cluster, ProcessesJson.Image processes) {
        UserSession admin = cluster.adminSession();
        LinkedList<Pcb> created = new LinkedList<>();
        LinkedList<ProcessesJson.ProcessSpec> all = processes.processes().copy();
        while (!all.isEmpty()) {
            ProcessesJson.ProcessSpec p = all.removeFirst();
            try {
                created.addLast(cluster.createIoProcess(p.name(), p.priority(), p.memory(), p.instructions(),
                        p.deadline(), p.toOperation(), admin));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Proceso " + p.number() + " (" + p.name() + "): " + e.getMessage(), e);
            }
        }
        return created;
    }

    /** El menor deadline de la carga inicial: el tope de espera del disco. */
    private static long smallestDeadline(LinkedList<Pcb> initial) {
        long smallest = Long.MAX_VALUE;
        for (int i = 0; i < initial.size(); i++) {
            smallest = Math.min(smallest, initial.get(i).getInitialDeadline());
        }
        return smallest;
    }

    /** Lee los dos archivos y arma la simulación. */
    public static Loaded start(Path fileSystemFile, Path processesFile) throws IOException {
        return start(FileSystemJson.read(fileSystemFile), ProcessesJson.read(processesFile));
    }

    /**
     * Revisa los dos JSON juntos, sin cargarlos: lo que hará fallar a un proceso cuando
     * pida su operación (se revisa contra el disco del JSON, sin las operaciones de los
     * procesos anteriores) y un proceso que pide más memoria que bloques tiene el swap
     * (nunca se podrá suspender). No impide la carga.
     *
     * @return un aviso por línea, o null si todo cuadra
     */
    public static String crossCheck(FileSystemJson.Image fileSystem, ProcessesJson.Image processes) {
        requireBoth(fileSystem, processes);
        int fileBlocks = fileSystem.disk().totalBlocks();
        FileSystem trial = new FileSystem(new BlockMap(fileBlocks, 0), Computer.ROOT_OWNER);
        FileSystemJson.load(fileSystem, trial);
        StringBuilder sb = new StringBuilder();
        if (processes.processes().isEmpty()) {
            sb.append("No hay procesos: el disco y la RAM quedan vacíos hasta que se cree uno.");
        }
        int swap = processes.settings().swapBlocks();
        LinkedList<ProcessesJson.ProcessSpec> all = processes.processes().copy();
        LinkedList<String> seen = new LinkedList<>();
        while (!all.isEmpty()) {
            ProcessesJson.ProcessSpec p = all.removeFirst();
            String problem = problemOf(p, trial);
            if (problem != null) {
                warn(sb, p, problem);
            }
            if (p.memory() > swap) {                  // un bloque del swap por unidad de RAM
                warn(sb, p, "pide " + p.memory() + " de memoria y el swap tiene " + swap
                        + " bloques: nunca se podrá suspender");
            }
            if (containsText(seen, p.name())) {
                warn(sb, p, "el nombre se repite; en la tabla de archivos se distinguen por su PID, como \"P<PID> ("
                        + p.name() + ")\"");
            } else {
                seen.addLast(p.name());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void warn(StringBuilder sb, ProcessesJson.ProcessSpec p, String problem) {
        sb.append(sb.length() == 0 ? "" : "\n").append("Proceso ").append(p.number()).append(" (").append(p.name())
                .append("): ").append(problem);
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

    private static String problemOf(ProcessesJson.ProcessSpec p, FileSystem fs) {
        if (p.operation() == IoOperation.CREATE) {
            int slash = p.path().lastIndexOf('/');
            String parent = slash == 0 ? "/" : p.path().substring(0, slash);
            FileInfo dir = fs.find(parent);
            if (dir == null) {
                return "no existe el directorio " + parent + " donde crea " + p.path();
            }
            if (!dir.isDirectory()) {
                return parent + " es un archivo, no un directorio: no se puede crear " + p.path() + " dentro";
            }
            if (fs.exists(p.path())) {
                return "ya existe " + p.path();
            }
            if (!p.directory() && p.sizeBlocks() > fs.getBlockMap().getFreeFileBlocks()) {
                return "crea " + p.sizeBlocks() + " bloques y el disco tiene " + fs.getBlockMap().getFreeFileBlocks()
                        + " libres";
            }
            return null;
        }
        FileInfo target = fs.find(p.path());
        if (target == null) {
            return "no existe " + p.path();
        }
        if (p.operation() == IoOperation.UPDATE && p.newName() == null && target.isDirectory()) {
            return p.path() + " es un directorio: para actualizarlo hay que darle un nombre nuevo (\"newName\")";
        }
        return null;
    }

    private static void requireBoth(FileSystemJson.Image fileSystem, ProcessesJson.Image processes) {
        if (fileSystem == null || processes == null) {
            throw new IllegalArgumentException("Hacen falta los dos JSON: el del sistema de archivos y el de procesos");
        }
    }
}
