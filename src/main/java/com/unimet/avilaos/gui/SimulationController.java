package com.unimet.avilaos.gui;

import com.unimet.avilaos.cluster.Cluster;
import com.unimet.avilaos.cluster.Computer;
import com.unimet.avilaos.config.DiskConfig;
import com.unimet.avilaos.config.FileSystemJson;
import com.unimet.avilaos.config.ProcessConfig;
import com.unimet.avilaos.config.ProcessesJson;
import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.config.SimulationLoader;
import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.events.LogEntry;
import com.unimet.avilaos.hardware.BlockMap;
import com.unimet.avilaos.hardware.Cpu;
import com.unimet.avilaos.hardware.Disk;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.IoOperation;
import com.unimet.avilaos.metrics.MetricsSnapshot;
import com.unimet.avilaos.os.Kernel;
import com.unimet.avilaos.os.disk.DiskPolicyType;
import com.unimet.avilaos.os.disk.DiskScheduler;
import com.unimet.avilaos.os.fs.FileSystem;
import com.unimet.avilaos.os.fs.FsOperation;
import com.unimet.avilaos.os.fs.FsResult;
import com.unimet.avilaos.os.fs.Permission;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.scheduling.PolicyType;
import com.unimet.avilaos.os.scheduling.Scheduler;
import java.io.File;
import java.io.IOException;
import javax.swing.JOptionPane;
import javax.swing.Timer;

/**
 * Une la ventana con el clúster. La ventana solo muestra y reenvía clics; el clúster
 * simula. Este controlador:
 *  - conecta los botones, el spinner de duración y la ventana de Planificación con el clúster,
 *  - abre la vista de configuración y, si se acepta, reinicia todo con la nueva,
 *  - refresca la vista con un javax.swing.Timer (REFRESH_MS) que LEE el estado:
 *    la simulación nunca espera a la GUI y la GUI nunca toca el SO.
 *
 * Todo aquí corre en el hilo de Swing. Los computadores corren en sus propios hilos;
 * lo que se lee de ellos son copias tomadas con el candado de su dueño.
 *
 * Project 2: it also connects the Disk view (block map, head, I/O queue in service order and the
 * disk policy selector), the Files view (tree and allocation table from the file system), the
 * user mode selector and the "Nuevo proceso de E/S" form, and shows the disk metrics.
 *
 * El enunciado pide que la interfaz EXIJA los dos JSON antes de iniciar el reloj global: "Iniciar"
 * abre el diálogo de carga mientras la simulación actual no salió de ellos, y su "Iniciar
 * simulación" la arma con SimulationLoader (los JSON mandan; la configuración solo da las claves
 * opcionales) y arranca el reloj. "Reiniciar" la vuelve a armar desde los mismos dos JSON.
 *
 * Reiniciar con procesos nuevos conservando el disco (punto del enunciado): con una simulación ya
 * cargada, el diálogo ofrece "Usar el disco actual"; los archivos y bloques de ahora (con los
 * cambios de la simulación) pasan a ser el sistema de archivos de la nueva, con el JSON de procesos
 * que se elija (FileSystemJson.snapshot, con el cabezal donde está).
 *
 * Modo usuario (enunciado): el usuario ve el disco, la cola de E/S, el log y las métricas (solo
 * lectura) y crea procesos de E/S, que el sistema de archivos revisa con su sesión (archivos
 * propios o públicos). Lo que cambia el sistema es del administrador: las políticas de CPU y de
 * disco, el quantum, los procesos de CPU, la configuración, cargar los JSON y reiniciar. En modo
 * usuario esas acciones se rechazan con el motivo (deniedInUserMode) y la simulación sigue.
 *
 * @author victor1102
 * @author donatovodola
 */
public final class SimulationController {

    /** Cada cuánto se refresca la pantalla (ms). Independiente de la duración del ciclo. */
    public static final int REFRESH_MS = 150;

    /**
     * Instructions of an I/O process created from the form: only its disk request (the statement
     * asks for the operation, not extra CPU work). The JSON parser can give other values.
     */
    static final int IO_PROCESS_INSTRUCTIONS = 1;

    private SimulationConfig config;      // al cargar los JSON se reemplaza por la que resulta de ellos
    /**
     * Disco de la configuración (diálogo o CSV) con que se abrió esta ventana: al cargar los JSON
     * da la dirección inicial, las posiciones por ciclo y la espera de la carga inicial si el JSON
     * de procesos no las trae (los JSON mandan en lo demás, ver SimulationLoader).
     */
    private final DiskConfig configuredDisk;
    private final MainWindow window;
    private final PlanningDialog planningDialog;
    private final Timer refreshTimer;
    private Cluster cluster;
    private long lastLogSequence = -1;
    private boolean hadWork;              // hubo procesos vivos desde el último arranque o reinicio
    /** Mode of use chosen in the rail; the file system checks every I/O process with it. */
    private DiskData.Session session = new DiskData.Session(true, Computer.ROOT_OWNER);
    private String usersKey = "";         // last list of users given to the session menu
    private int nextIoNumber = 1;         // for the default name of an I/O process ("IO_Read_3")
    /** Disk policy chosen in the Disk view that the kernel hasn't applied yet (next cycle), or null. */
    private DiskPolicyType pendingDiskPolicy;
    /** Los dos JSON de la última carga (llenan de antemano el diálogo de carga), o null. */
    private File fileSystemFile;
    private File processesFile;
    /** De dónde salió el disco de la simulación actual: el nombre del archivo o "disco conservado…". */
    private String fileSystemLabel;
    /** Los dos JSON ya leídos, si la simulación actual salió de ellos; si no, null. */
    private FileSystemJson.Image fileSystemImage;
    private ProcessesJson.Image processesImage;

    public SimulationController(SimulationConfig config) {
        this(config, null, null);
    }

    /**
     * @param fileSystemFile JSON del sistema de archivos elegido antes (solo llena el diálogo de carga), o null
     * @param processesFile  JSON de procesos elegido antes, o null
     */
    private SimulationController(SimulationConfig config, File fileSystemFile, File processesFile) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración no puede ser null");
        }
        this.config = config;
        this.configuredDisk = config.getDisk();
        this.fileSystemFile = fileSystemFile;
        this.processesFile = processesFile;
        this.window = new MainWindow(config.getComputerCount());
        this.planningDialog = new PlanningDialog(window, config.getComputerCount());
        this.refreshTimer = new Timer(REFRESH_MS, e -> refresh());
    }

    /** Arma la primera simulación, conecta la ventana y la muestra. Llamar desde el hilo de Swing. */
    public void show() {
        cluster = Cluster.create(config);
        connect();
        window.showSession(session);
        window.showLoadedJson(null, null);
        showInitialSettings();
        refresh();
        window.setVisible(true);
        refreshTimer.start();
    }

    // ---------- Conexiones (una sola vez: siempre usan el clúster actual) ----------

    private void connect() {
        window.setStartPauseAction(e -> startOrPause());
        window.setResetAction(e -> reset());
        window.setCreateProcessAction(e -> createProcess());
        window.setConfigAction(e -> configure());
        window.setLoadJsonAction(e -> loadJson());
        window.setPlanningAction(e -> openPlanning());
        window.setCreateIoProcessAction(e -> createIoProcess(null, window.getFileSystemPanel().getSelectedPath()));
        window.getFileSystemPanel().setFileActionListener(this::createIoProcess);
        window.setSessionListener(this::changeSession);
        window.getDiskPanel().setPolicyListener(this::chooseDiskPolicy);
        planningDialog.setPlanningListener(this::applyPlanning);
        window.getCycleDurationSpinner().addChangeListener(e -> cluster.setCycleDurationMs(
                ((Number) window.getCycleDurationSpinner().getValue()).longValue()));
    }

    /**
     * Pone en la vista los valores de la configuración. La política y el quantum de cada
     * computador los muestra refresh() leyendo el planificador; si la ventana de
     * Planificación está abierta, se recarga con los valores del clúster nuevo.
     */
    private void showInitialSettings() {
        window.getCycleDurationSpinner().setValue((int) config.getCycleDurationMs());
        if (planningDialog.isVisible()) {
            loadPlanning();
        }
    }

    // ---------- Acciones ----------

    private void startOrPause() {
        if (!cluster.isRunning() && !loadedFromJson()) {
            loadJson();                           // el reloj solo arranca con los dos JSON
            return;
        }
        if (!cluster.isRunning()) {
            hadWork = cluster.getTotalLoad() > 0;   // un proceso corto puede terminar antes del primer refresco
            cluster.start();
            window.setRunning(true);
        } else if (cluster.isPaused()) {
            hadWork = cluster.getTotalLoad() > 0;   // un proceso corto puede terminar antes del primer refresco
            cluster.resume();
            window.setRunning(true);
        } else {
            cluster.pause();
            window.setRunning(false);
        }
    }

    /**
     * Detiene la simulación y arma una nueva (ciclo 0, PID desde 1): desde los dos JSON si la
     * actual salió de ellos, o desde la configuración.
     *
     * El clúster nuevo se arma ANTES de apagar el actual: si la configuración no se puede
     * cargar, se muestra el error y la simulación actual sigue viva (antes quedaba apagada
     * y sin reemplazo, e Iniciar ya no hacía nada).
     */
    private void reset() {
        if (deniedInUserMode("reiniciar la simulación")) {
            return;
        }
        if (loadedFromJson()) {
            SimulationLoader.Loaded loaded;
            try {
                loaded = SimulationLoader.start(fileSystemImage, processesImage, configuredDisk);
            } catch (IllegalArgumentException ex) {
                showError("No se pudo reiniciar", ex);
                return;
            }
            replaceCluster(loaded.cluster(), loaded.config());
            logLoad(loaded.warnings());
            refresh();
            return;
        }
        Cluster next;
        try {
            next = Cluster.create(config);
        } catch (IllegalArgumentException ex) {
            showError("No se pudo reiniciar", ex);
            return;
        }
        replaceCluster(next, config);
    }

    /** Pone una simulación nueva en lugar de la actual y deja la vista como al inicio. */
    private void replaceCluster(Cluster next, SimulationConfig nextConfig) {
        cluster.shutdown();
        cluster = next;
        config = nextConfig;
        window.resetView();
        usersKey = "";
        pendingDiskPolicy = null;                 // the new cluster starts with the configured policy
        lastLogSequence = -1;
        hadWork = false;
        showInitialSettings();
        refresh();
    }

    /**
     * El proceso se agrega a la configuración (así se guarda y se repite al reiniciar)
     * y al clúster. El formulario ya validó con la misma configuración.
     */
    private void createProcess() {
        if (deniedInUserMode("crear procesos de CPU")) {
            return;
        }
        ProcessConfig process = ProcessFormDialog.showDialog(window, config);
        if (process == null) {
            return;
        }
        try {
            cluster.createProcess(process);
            config.addProcess(process);
        } catch (IllegalArgumentException ex) {
            showError("No se pudo crear el proceso", ex);
        }
        refresh();
    }

    /**
     * Abre la vista de configuración. Si el usuario acepta, se aplica reemplazando la
     * simulación completa: el número de computadores fija la ventana, así que se detiene
     * el clúster, se cierra esta ventana y se abre un controlador nuevo con la configuración.
     */
    private void configure() {
        if (deniedInUserMode("cambiar la configuración")) {
            return;
        }
        SimulationConfig newConfig = ConfigDialog.showDialog(window, config);
        if (newConfig == null) {
            return;
        }
        refreshTimer.stop();
        cluster.shutdown();
        planningDialog.dispose();
        window.dispose();
        new SimulationController(newConfig, fileSystemFile, processesFile).show();
    }

    // ---------- Los dos JSON (Proyecto 2) ----------

    private boolean loadedFromJson() {
        return fileSystemImage != null && processesImage != null;
    }

    /**
     * Abre "Cargar la simulación". Si el usuario pulsa "Iniciar simulación", los dos JSON (o el disco
     * actual más un JSON de procesos) pasan a ser la simulación (disco, sistema de archivos,
     * configuración y procesos) en lugar de la actual, y el reloj arranca. Cancelar deja todo como
     * estaba. Los avisos de crossCheck no impiden la carga: van al log, y cada proceso con un
     * problema termina con su motivo cuando se ejecuta.
     */
    private void loadJson() {
        if (deniedInUserMode("cargar los JSON de la simulación")) {
            return;
        }
        // El disco actual solo se ofrece si la simulación salió de los JSON (si no, está vacío)
        FileSystemJson.Image current = loadedFromJson() ? currentDisk() : null;
        LoadDialog.Selection chosen = new LoadDialog(window, JsonChecks::checkFileSystem,
                JsonChecks::checkProcesses,
                (fs, procs) -> fs != null ? JsonChecks.crossCheck(fs, procs) : JsonChecks.crossCheck(current, procs),
                fileSystemFile, processesFile, current == null ? null : JsonChecks.summary(current)).showDialog();
        if (chosen == null) {
            return;
        }
        boolean keepDisk = chosen.fileSystem() == null;
        FileSystemJson.Image fileSystem;
        ProcessesJson.Image processes;
        SimulationLoader.Loaded loaded;
        try {
            // Conservar el disco: su estado al pulsar "Iniciar simulación" (la simulación seguía corriendo)
            fileSystem = keepDisk ? currentDisk() : JsonChecks.readFileSystem(chosen.fileSystem());
            processes = JsonChecks.readProcesses(chosen.processes());
            loaded = SimulationLoader.start(fileSystem, processes, configuredDisk);
        } catch (IOException ex) {
            showError("No se pudo cargar la simulación", new IllegalArgumentException(JsonChecks.cannotRead(ex), ex));
            return;
        } catch (IllegalArgumentException ex) {
            showError("No se pudo cargar la simulación", ex);
            return;
        }
        // Solo ahora, con la carga hecha: si fallaba, la simulación y sus archivos seguían siendo los anteriores
        if (keepDisk) {
            fileSystemLabel = "disco conservado del ciclo " + cluster.getCurrentCycle();
        } else {
            fileSystemFile = chosen.fileSystem();
            fileSystemLabel = fileSystemFile.getName();
        }
        processesFile = chosen.processes();
        fileSystemImage = fileSystem;
        processesImage = processes;
        replaceCluster(loaded.cluster(), loaded.config());
        window.showLoadedJson(fileSystemLabel, processesFile.getName());
        int warnings = logLoad(loaded.warnings());
        if (warnings > 0) {
            window.showNotice("Simulación cargada con " + warnings + (warnings == 1 ? " aviso" : " avisos")
                    + " (están en el log).");
        }
        startOrPause();
    }

    /**
     * El sistema de archivos actual como si se hubiera leído de un JSON, con el cabezal donde está
     * (FileSystemJson.snapshot): es el disco que se conserva al cargar otros procesos.
     */
    private FileSystemJson.Image currentDisk() {
        return FileSystemJson.snapshot(computer().getFileSystem(), computer().getDisk().getHeadBlock());
    }

    /**
     * Escribe en el log (ciclo 0) de dónde salió la simulación (los dos JSON, o el disco conservado
     * y el JSON de procesos) y los avisos de crossCheck, uno por línea.
     *
     * @return cuántos avisos hubo
     */
    private int logLoad(String warnings) {
        cluster.getLog().log(0, "Carga", "sistema de archivos " + fileSystemLabel + " ("
                + JsonChecks.summary(fileSystemImage) + ") y procesos " + processesFile.getName() + " ("
                + JsonChecks.summary(processesImage) + ")");
        int count = 0;
        if (warnings != null) {
            for (String line : warnings.split("\n")) {
                cluster.getLog().log(0, "Carga", "aviso: " + line);
                count++;
            }
        }
        return count;
    }

    // ---------- Disk, file system and user mode (Project 2) ----------

    private Computer computer() {
        return cluster.getComputer(1);           // Project 2 simulates a single computer
    }

    /**
     * Opens the "Nuevo proceso de E/S" form. The interface only creates the process: the file system
     * checks the operation first with the active session, so a forbidden one is rejected right away
     * with its reason (the simulation keeps running); an allowed one becomes an I/O process that does
     * it when it gets the CPU and the disk serves its request.
     *
     * @param preset operation of the button the user clicked, or null
     * @param path   selected path in the tree, or null
     */
    private void createIoProcess(IoOperation preset, String path) {
        Computer computer = computer();
        FileSystem fileSystem = computer.getFileSystem();
        IoProcessDialog.Limits limits = new IoProcessDialog.Limits(DiskData.FileEntry.fromList(fileSystem.list()),
                fileSystem.getBlockMap().getFreeFileBlocks(), computer.getRam().getTotalSize(), userNames());
        DiskData.IoProcess request = IoProcessDialog.showDialog(window, limits, preset, path, nextIoNumber);
        if (request == null) {
            return;
        }
        nextIoNumber++;
        FileSystemPanel files = window.getFileSystemPanel();
        try {
            FsOperation operation = toOperation(request);
            FsResult check = fileSystem.check(session.toUserSession(), operation);
            if (!check.isOk()) {
                files.showRejected(check.getMessage());
                window.showRejected(check.getMessage());
                cluster.getLog().log(cluster.getCurrentCycle(), "Interfaz", "rechaza " + operation + " ("
                        + session.longText() + "): " + check.getMessage());
                return;
            }
            Pcb pcb = cluster.createIoProcess(request.processName(), request.priority(), request.memory(),
                    IO_PROCESS_INSTRUCTIONS, request.deadline(), operation, session.toUserSession());
            files.showAccepted("Se creó " + pcb + " (" + request.processName() + ") para " + operation
                    + ". Entra a Nuevos y, cuando tenga la CPU, pide el disco.");
        } catch (IllegalArgumentException ex) {
            files.showRejected(ex.getMessage());
            window.showRejected(ex.getMessage());
        }
        refresh();
    }

    /** The file system's operation for what the user filled in (the creator is the process name). */
    private static FsOperation toOperation(DiskData.IoProcess r) {
        String creator = r.processName();
        Permission permission = r.isPublic() ? Permission.PUBLIC : Permission.PRIVATE;
        return switch (r.operation()) {
            case CREATE -> r.directory()
                    ? FsOperation.createDirectory(r.path(), r.newName(), r.owner(), permission, creator)
                    : FsOperation.createFile(r.path(), r.newName(), r.sizeBlocks(), permission, creator);
            case READ -> FsOperation.read(r.path(), creator);
            case UPDATE -> FsOperation.rename(r.path(), r.newName(), creator);
            case DELETE -> FsOperation.delete(r.path(), creator);
            default -> throw new IllegalArgumentException(r.operation().getLabel()
                    + " no es una operación sobre archivos");
        };
    }

    /**
     * The kernel applies a new disk policy at the start of the next cycle, so until then (or while
     * paused) the Disk view marks the chosen one and says it is pending.
     */
    private void chooseDiskPolicy(DiskPolicyType policy) {
        if (deniedInUserMode("cambiar la política de disco")) {
            // El selector vuelve a marcar la política que de verdad usa el disco
            window.getDiskPanel().showPolicy(computer().getDiskScheduler().getPolicyType(), pendingDiskPolicy);
            return;
        }
        cluster.setDiskPolicy(policy);
        pendingDiskPolicy = policy;
        window.getDiskPanel().showPolicy(computer().getDiskScheduler().getPolicyType(), pendingDiskPolicy);
    }

    /**
     * The mode picked in the rail. The administrator (owner of the root) can't use user mode; the
     * file system would reject all its operations anyway, so it's refused here with the reason.
     */
    private void changeSession(DiskData.Session chosen) {
        if (!chosen.admin() && chosen.user().equals(computer().getFileSystem().getRootOwner())) {
            window.showRejected("El usuario " + chosen.user() + " es el administrador: solo puede usar el modo"
                    + " administrador.");
            return;
        }
        session = chosen;
        window.showSession(session);
        if (!session.admin()) {
            planningDialog.setVisible(false);         // abierta como administrador: el usuario no la puede usar
        }
        cluster.getLog().log(cluster.getCurrentCycle(), "Interfaz", "cambia al modo " + session.longText());
    }

    /** Users of the file system (owners, without the administrator), plus the active one. */
    private String[] userNames() {
        LinkedList<String> names = computer().getFileSystem().userNames();
        if (!session.admin() && !contains(names, session.user())) {
            names.addLast(session.user());
        }
        String[] result = new String[names.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = names.removeFirst();
        }
        return result;
    }

    private static boolean contains(LinkedList<String> list, String text) {
        LinkedList<String> walk = list.copy();
        while (!walk.isEmpty()) {
            if (walk.removeFirst().equals(text)) {
                return true;
            }
        }
        return false;
    }

    /** Disk view, Files view, session menu and disk metrics, read from the computer's copies. */
    private void refreshStorage() {
        Computer computer = computer();
        Disk disk = computer.getDisk();
        DiskScheduler scheduler = computer.getDiskScheduler();
        FileSystem fileSystem = computer.getFileSystem();
        BlockMap blocks = disk.getBlockMap();
        long cycle = cluster.getCurrentCycle();

        int head = disk.getHeadBlock();
        HeadDirection direction = disk.getHeadDirection();
        DiskPanel diskPanel = window.getDiskPanel();
        diskPanel.showSnapshot(DiskData.Snapshot.from(blocks.snapshot(), blocks.getSwapStart(), head, direction));
        DiskRequest serving = disk.getCurrentRequest();
        String status = serving != null ? disk.getPhase().getLabel()
                : scheduler.isHolding() ? "El disco espera a que la carga inicial pida el disco" : null;
        diskPanel.showQueue(serving == null ? null : DiskData.Request.from(serving, cycle), status,
                DiskData.Request.fromPlans(scheduler.previewServiceOrder(head, direction), cycle));
        DiskPolicyType current = scheduler.getPolicyType();
        if (pendingDiskPolicy == current) {
            pendingDiskPolicy = null;             // the kernel applied it
        }
        diskPanel.showPolicy(current, pendingDiskPolicy);

        window.getFileSystemPanel().showFiles(DiskData.FileEntry.fromList(fileSystem.list()));
        String[] users = userNames();
        String key = String.join(",", users);
        if (!key.equals(usersKey)) {
            usersKey = key;
            window.setUsers(users);
        }

        // Not measured yet (the view says "pendiente"): average wait (NaN), served per policy (null) and
        // suspensions / reactivations (-1, until the medium-term scheduler exists)
        window.showDiskMetrics(new DiskData.DiskMetrics(disk.getTotalSeek(), Double.NaN, disk.getServedRequests(),
                null, -1, -1));
        window.addSeekSample(cycle, disk.getTotalSeek(), scheduler.getPolicyType().getLabel());
    }

    // ---------- Planificación ----------

    /** Abre la ventana de Planificación con lo que tiene cada computador ahora. */
    private void openPlanning() {
        if (deniedInUserMode("cambiar las políticas de planificación ni el quantum")) {
            return;
        }
        if (!planningDialog.isVisible()) {          // si ya está abierta no se pisa lo que se está eligiendo
            loadPlanning();
        }
        planningDialog.setVisible(true);
        planningDialog.toFront();
    }

    private void loadPlanning() {
        PolicyType[] policies = new PolicyType[cluster.getComputerCount()];
        for (int id = 1; id <= policies.length; id++) {
            policies[id - 1] = cluster.getComputer(id).getKernel().getScheduler().getPolicyType();
        }
        planningDialog.load(policies, cluster.getComputer(1).getKernel().getScheduler().getQuantum());
    }

    /**
     * "Aplicar" de la ventana de Planificación. Cada kernel lo toma al inicio de su próximo
     * ciclo (y lo anota en el log); pedir la política que ya tiene no hace nada.
     * Igual que antes con el selector y el spinner, no cambia la configuración: al
     * reiniciar se vuelve a la política inicial de cada computador.
     */
    private void applyPlanning(PolicyType[] policies, int quantum) {
        if (deniedInUserMode("cambiar las políticas de planificación ni el quantum")) {
            return;
        }
        for (int id = 1; id <= policies.length; id++) {
            cluster.setPolicy(id, policies[id - 1]);
        }
        cluster.setQuantum(quantum);
    }

    /**
     * Modo usuario (enunciado): solo el administrador gestiona los procesos y cambia las políticas
     * y la configuración. Si la sesión es de usuario, muestra el motivo (sin detener la simulación),
     * lo deja en el log y devuelve true para que la acción no se haga.
     *
     * @param action lo que se intentó, en infinitivo (por ejemplo "cambiar la política de disco")
     */
    private boolean deniedInUserMode(String action) {
        String reason = session.toUserSession().adminOnly(action);
        if (reason == null) {
            return false;
        }
        window.showRejected(reason);
        cluster.getLog().log(cluster.getCurrentCycle(), "Interfaz", "rechaza " + action + " (" + session.longText()
                + "): solo el administrador");
        return true;
    }

    private void showError(String title, IllegalArgumentException ex) {
        JOptionPane.showMessageDialog(window, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    // ---------- Refresco ----------

    private void refresh() {
        pauseWhenIdle();
        window.setCycle(cluster.getCurrentCycle());
        int busy = 0;
        int usedMb = 0;
        int totalMb = 0;
        int live = 0;
        int blocked = 0;
        int terminated = 0;
        StringBuilder running = new StringBuilder();
        for (int id = 1; id <= cluster.getComputerCount(); id++) {
            Computer computer = cluster.getComputer(id);
            ComputerPanel panel = window.getComputerPanel(id);
            Kernel kernel = computer.getKernel();
            Cpu cpu = computer.getCpu();
            // Read the running process once: the card and the KPI strip must show the same one
            Pcb inCpu = kernel.getRunning();
            panel.showRunning(inCpu, cpu.getMode(), cpu.getProgramCounter(),
                    cpu.getMemoryAddressRegister());
            panel.showMemory(computer.getRam().getUsedSize(), computer.getRam().getTotalSize());
            LinkedList<Pcb> newQueue = kernel.getNewSnapshot();
            LinkedList<Pcb> readyQueue = kernel.getReadySnapshot();
            LinkedList<Pcb> blockedQueue = kernel.getBlockedSnapshot();
            LinkedList<Pcb> terminatedQueue = kernel.getTerminatedSnapshot();
            panel.showQueues(newQueue, readyQueue, blockedQueue, terminatedQueue);
            if (inCpu != null) {
                busy++;
                running.append(running.length() == 0 ? "" : ", ").append(inCpu);
            }
            usedMb += computer.getRam().getUsedSize();
            totalMb += computer.getRam().getTotalSize();
            live += newQueue.size() + readyQueue.size() + blockedQueue.size() + (inCpu == null ? 0 : 1);
            blocked += blockedQueue.size();
            terminated += terminatedQueue.size();
            Scheduler scheduler = kernel.getScheduler();
            PolicyType policy = scheduler.getPolicyType();
            panel.showPolicy(policy.getLabel(), policy.usesQuantum() ? scheduler.getQuantum() : 0);
            if (planningDialog.isVisible()) {
                planningDialog.showComputer(id, policy, scheduler.getQuantum(), scheduler.getReadyCount());
            }
        }
        window.showOverview(busy, running.toString(), usedMb, totalMb, live, blocked, terminated);
        MetricsSnapshot[] perComputer = new MetricsSnapshot[cluster.getComputerCount()];
        for (int id = 1; id <= perComputer.length; id++) {
            perComputer[id - 1] = cluster.getComputer(id).getMetrics().getSnapshot();
        }
        window.showMetrics(perComputer, cluster.getSystemMetrics().getSnapshot());
        window.showCpuHistory(cluster.getSystemMetrics().getComputers());
        refreshStorage();
        appendNewLogEntries();
    }

    /**
     * Pausa la simulación cuando todos los procesos terminaron. Si el reloj siguiera, el
     * throughput (terminados a tiempo / ciclos) bajaría con cada ciclo ocioso y no se
     * podrían comparar políticas entre corridas. Solo pausa si antes hubo trabajo: una
     * simulación sin procesos sigue corriendo (el usuario puede ir creándolos).
     *
     * Se revisa en cada refresco (cada REFRESH_MS), así que puede correr algún ciclo
     * ocioso de más antes de pausar. Iniciar la reanuda normalmente.
     */
    private void pauseWhenIdle() {
        if (!cluster.isRunning() || cluster.isPaused()) {
            return;
        }
        if (cluster.getTotalLoad() > 0) {
            hadWork = true;
        } else if (hadWork) {
            hadWork = false;
            cluster.pause();
            window.setRunning(false);
            cluster.getLog().log(cluster.getCurrentCycle(), "Clúster",
                    "todos los procesos terminaron: la simulación se pausa");
        }
    }

    /** Agrega solo las entradas que no se han mostrado (cada LogEntry trae su número de secuencia). */
    private void appendNewLogEntries() {
        LinkedList<LogEntry> entries = cluster.getLog().getEntries();
        while (!entries.isEmpty()) {
            LogEntry entry = entries.removeFirst();
            if (entry.sequence() > lastLogSequence) {
                window.appendLog(entry);
                lastLogSequence = entry.sequence();
            }
        }
    }

    public Cluster getCluster() {
        return cluster;
    }

    public MainWindow getWindow() {
        return window;
    }
}
