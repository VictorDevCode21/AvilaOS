package com.unimet.avilaos.config;

import com.unimet.avilaos.cluster.Cluster;
import com.unimet.avilaos.cluster.Computer;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.BlockMap;
import com.unimet.avilaos.hardware.IoOperation;
import com.unimet.avilaos.os.fs.FileInfo;
import com.unimet.avilaos.os.fs.FileSystem;
import com.unimet.avilaos.os.fs.UserSession;
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
 *       sistema al iniciar, no un usuario).</li>
 * </ol>
 * El reloj no arranca: lo arranca quien llama, cuando la interfaz ya está lista.
 *
 * Reiniciar con procesos nuevos conservando el disco modificado:
 * {@code start(FileSystemJson.snapshot(fsViejo, cabezal), nuevosProcesos)}.
 *
 * crossCheck avisa (sin impedir la carga) de lo que hará fallar a un proceso: una
 * ruta que no existe, un archivo que se crea donde ya hay otro, etc. El proceso
 * igual se crea y, al pedir su operación, termina con el motivo en el log.
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

    /** La configuración completa que resulta de los dos JSON. */
    public static SimulationConfig buildConfig(FileSystemJson.Image fileSystem, ProcessesJson.Image processes) {
        requireBoth(fileSystem, processes);
        ProcessesJson.Settings s = processes.settings();
        SimulationConfig config = new SimulationConfig(s.cycleDurationMs(), s.quantum(), 0);   // sin red: 1 computador
        config.addComputer(s.ramCapacity(), s.cpuPolicy());
        config.setDisk(new DiskConfig(fileSystem.disk().totalBlocks(), s.swapBlocks(), s.diskPolicy(),
                fileSystem.disk().initialHead(), s.headDirection(), s.positionsPerCycle()));
        config.validate();
        return config;
    }

    /**
     * Arma la simulación con los dos JSON ya leídos (ver los pasos en la clase).
     *
     * @throws IllegalArgumentException si la configuración no es válida o el sistema de archivos no carga
     */
    public static Loaded start(FileSystemJson.Image fileSystem, ProcessesJson.Image processes) {
        SimulationConfig config = buildConfig(fileSystem, processes);
        Cluster cluster = Cluster.create(config);
        Computer computer = cluster.getComputer(1);
        FileSystemJson.load(fileSystem, computer.getFileSystem());
        String warnings = crossCheck(fileSystem, processes);
        UserSession admin = cluster.adminSession();
        LinkedList<ProcessesJson.ProcessSpec> all = processes.processes().copy();
        while (!all.isEmpty()) {
            ProcessesJson.ProcessSpec p = all.removeFirst();
            try {
                cluster.createIoProcess(p.name(), p.priority(), p.memory(), p.instructions(), p.deadline(),
                        p.toOperation(), admin);
            } catch (IllegalArgumentException e) {
                cluster.shutdown();
                throw new IllegalArgumentException("Proceso " + p.number() + " (" + p.name() + "): " + e.getMessage(), e);
            }
        }
        return new Loaded(cluster, config, warnings);
    }

    /** Lee los dos archivos y arma la simulación. */
    public static Loaded start(Path fileSystemFile, Path processesFile) throws IOException {
        return start(FileSystemJson.read(fileSystemFile), ProcessesJson.read(processesFile));
    }

    /**
     * Revisa los dos JSON juntos, sin cargarlos: lo que hará fallar a un proceso cuando
     * pida su operación (se revisa contra el disco del JSON, sin las operaciones de los
     * procesos anteriores). No impide la carga.
     *
     * @return un aviso por línea, o null si todo cuadra
     */
    public static String crossCheck(FileSystemJson.Image fileSystem, ProcessesJson.Image processes) {
        requireBoth(fileSystem, processes);
        int fileBlocks = fileSystem.disk().totalBlocks();
        FileSystem trial = new FileSystem(new BlockMap(fileBlocks, 0), Computer.ROOT_OWNER);
        FileSystemJson.load(fileSystem, trial);
        StringBuilder sb = new StringBuilder();
        LinkedList<ProcessesJson.ProcessSpec> all = processes.processes().copy();
        while (!all.isEmpty()) {
            ProcessesJson.ProcessSpec p = all.removeFirst();
            String problem = problemOf(p, trial);
            if (problem != null) {
                sb.append(sb.length() == 0 ? "" : "\n").append("Proceso ").append(p.number()).append(" (")
                        .append(p.name()).append("): ").append(problem);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String problemOf(ProcessesJson.ProcessSpec p, FileSystem fs) {
        if (p.operation() == IoOperation.CREATE) {
            int slash = p.path().lastIndexOf('/');
            String parent = slash == 0 ? "/" : p.path().substring(0, slash);
            FileInfo dir = fs.find(parent);
            if (dir == null || !dir.isDirectory()) {
                return "no existe el directorio " + parent + " donde crea " + p.path();
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
