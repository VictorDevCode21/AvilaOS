package com.unimet.avilaos.gui;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.CpuMode;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.IoOperation;
import com.unimet.avilaos.os.disk.DiskPolicyType;
import com.unimet.avilaos.os.process.BlockReason;
import com.unimet.avilaos.os.process.Pcb;
import java.io.File;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Demo of the Project 2 GUI with fake data (no kernel, no real disk): one computer with the seven
 * queues, the "Disco" and "Archivos" views, and the "Cargar JSON" dialog (try the files in samples/).
 *
 * It loads the file system of the Project 2 statement (plus a chained file and two suspended
 * processes), moves the head toward the requests with the selected policy, and lets you click the
 * file buttons as the user "user1": private files of other users and changes to files of others are
 * rejected, the rest go to the I/O queue.
 *
 * Only for trying the GUI while the contracts PR (Disk, FileSystem...) is not merged.
 *
 * @author donatovodola
 */
public final class DiskFsDemo {

    private static final int TOTAL = 256;
    private static final int SWAP_START = 224;
    private static final int STEP = 4;                 // head positions per cycle
    private DiskData.Session session = new DiskData.Session(false, "user1");

    private final MainWindow window = new MainWindow(1);
    private File loadedFs;
    private File loadedProcs;
    private final DiskData.Block[] blocks = new DiskData.Block[TOTAL];
    private DiskData.FileEntry[] files;
    private DiskData.Request[] queue = new DiskData.Request[0];
    private DiskData.Request serving;
    private int servingLeft;
    private int servingDone;
    private int head = 50;
    private boolean movingUp = true;
    private String policy = "SSTF";
    private int nextPid = 20;
    private long cycle;
    // Fake metrics
    private long totalSeek;
    private long waitSum;
    /** Same order as DiskPolicyType.values(), like the bars of the Metrics view. */
    private final long[] servedPerPolicy = new long[DiskPolicyType.values().length];

    private DiskFsDemo() {
        loadFileSystem();
        window.getDiskPanel().showPolicy(DiskPolicyType.fromLabel(policy));
        window.getDiskPanel().setPolicyListener(chosen -> {
            policy = chosen.getLabel();
            window.getDiskPanel().showPolicy(chosen);
            refresh();
        });
        window.getFileSystemPanel().setFileActionListener(this::onFileAction);
        window.setLoadJsonAction(e -> openLoadDialog());
        window.setUsers(new String[] {"user1", "user2"});
        window.showSession(session);
        window.setSessionListener(chosen -> {
            session = chosen;
            window.showSession(session);
            window.showNotice("Ahora estás en modo " + session.longText() + ".");
        });
        window.setCreateIoProcessAction(e -> openIoForm(null, window.getFileSystemPanel().getSelectedPath()));
        window.showLoadedJson(null, null);
        showComputer();
        String[][] first = {{"READ", "/sistema/config.sys"}, {"READ", "/usuarios/video.mp4"},
            {"UPDATE", "/usuarios/notas.txt"}, {"DELETE", "/usuarios/img1.png"}, {"READ", "/sistema/boot.bin"},
            {"UPDATE", "/usuarios/data.csv"}, {"READ", "/sistema/script.py"}, {"READ", "/sistema/core.dll"}};
        for (String[] r : first) {
            enqueue(IoOperation.valueOf(r[0]), r[1]);
        }
        refresh();
        new Timer(450, e -> tick()).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            DiskFsDemo demo = new DiskFsDemo();
            demo.window.showView(MainWindow.VIEW_COMPUTERS);
            demo.window.setVisible(true);
        });
    }

    // ---------- Load dialog ----------

    private void openLoadDialog() {
        LoadDialog.Selection files = new LoadDialog(window, JsonChecks::checkFileSystem,
                JsonChecks::checkProcesses, JsonChecks::crossCheck, loadedFs, loadedProcs).showDialog();
        if (files != null) {
            loadedFs = files.fileSystem();
            loadedProcs = files.processes();
            window.showLoadedJson(loadedFs.getName(), loadedProcs.getName());
        }
    }

    // ---------- Fake computer (seven-state queues) ----------

    private void showComputer() {
        ComputerPanel panel = window.getComputerPanel(1);
        Pcb running = Pcb.cpuBound("Proc_34", 2, 16, 2, 500, 1, 0);
        running.markReady();
        running.markRunning(0);
        panel.showRunning(running, CpuMode.KERNEL, 2, 1);
        panel.showMemory(208, 256);
        panel.showPolicy("Round Robin", 4);
        LinkedList<Pcb> newQueue = new LinkedList<>();
        newQueue.addLast(Pcb.cpuBound("Proc_200", 1, 64, 40, 900, 1, 0));
        LinkedList<Pcb> ready = new LinkedList<>();
        for (String name : new String[] {"Proc_180", "Proc_123", "Calculo_A"}) {
            Pcb p = Pcb.cpuBound(name, 3, 16, 20, 500, 1, 0);
            p.markReady();
            ready.addLast(p);
        }
        LinkedList<Pcb> blocked = new LinkedList<>();
        String[][] waits = {{"Proc_11", "IO", "disco · READ /sistema/boot.bin"},
            {"Proc_new", "IO", "disco · CREATE /usuarios/user1/tarea2.txt"},
            {"Sync_B", "SEMAPHORE", "mutex del log"}};
        for (String[] w : waits) {
            Pcb p = Pcb.cpuBound(w[0], 2, 16, 10, 500, 1, 0);
            p.markReady();
            p.markRunning(0);
            p.markBlocked(BlockReason.valueOf(w[1]), w[2]);
            blocked.addLast(p);
        }
        LinkedList<Pcb> done = new LinkedList<>();
        Pcb finished = Pcb.cpuBound("Proc_119", 2, 16, 1, 500, 1, 0);
        finished.markReady();
        finished.markRunning(0);
        finished.markTerminated(412, false);
        done.addLast(finished);
        panel.showQueues(newQueue, ready, blocked, done);
        // Suspendidos con sus estados reales: Bloqueado → Bloqueado/Suspendido (→ Listo/Suspendido)
        LinkedList<Pcb> readySuspended = new LinkedList<>();
        Pcb rs = Pcb.cpuBound("Proc_62", 3, 16, 8, 500, 1, 0);
        rs.markReady();
        rs.markRunning(0);
        rs.markBlocked(BlockReason.IO, "disco · READ /sistema/script.py");
        rs.markSuspended();
        rs.markReadySuspended();
        readySuspended.addLast(rs);
        LinkedList<Pcb> blockedSuspended = new LinkedList<>();
        Pcb bs = Pcb.cpuBound("Proc_95", 4, 16, 8, 500, 1, 0);
        bs.markReady();
        bs.markRunning(0);
        bs.markBlocked(BlockReason.IO, "disco · READ /sistema/config.sys (en swap 224–239)");
        bs.markSuspended();
        blockedSuspended.addLast(bs);
        panel.showSuspendedQueues(readySuspended, blockedSuspended);
        window.showOverview(1, running.toString(), 208, 256, 10, 3, 2, 1);
    }

    // ---------- Fake file system (the statement's example) ----------

    private void loadFileSystem() {
        files = new DiskData.FileEntry[] {
            dir("/", "admin"), dir("/sistema", "admin"), dir("/usuarios", "admin"), dir("/usuarios/user1", "user1"),
            file("/mbr.bin", 0, 11, "admin", true), file("/sistema/boot.bin", 11, 23, "admin", true),
            file("/usuarios/notas.txt", 34, 28, "user1", false), file("/sistema/script.py", 62, 2, "admin", true),
            file("/sistema/core.dll", 64, 31, "admin", true), file("/sistema/config.sys", 95, 24, "admin", true),
            file("/usuarios/img1.png", 119, 4, "user1", false), file("/usuarios/data.csv", 123, 57, "user2", true),
            file("/usuarios/video.mp4", 180, 20, "user1", true),
            chained("/usuarios/user1/tarea.txt", new int[] {200, 201, 205, 206, 207}, "user1", false, "P3")
        };
        for (int b = 224; b < 240; b++) {
            blocks[b] = new DiskData.Block("P7", true);
        }
        for (int b = 240; b < 248; b++) {
            blocks[b] = new DiskData.Block("P8", true);
        }
    }

    private static DiskData.FileEntry dir(String path, String owner) {
        return new DiskData.FileEntry(path, true, 0, -1, owner, true, "JSON", new int[0]);
    }

    private DiskData.FileEntry file(String path, int start, int size, String owner, boolean isPublic) {
        int[] chain = new int[size];
        for (int i = 0; i < size; i++) {
            chain[i] = start + i;
        }
        return chained(path, chain, owner, isPublic, "JSON");
    }

    private DiskData.FileEntry chained(String path, int[] chain, String owner, boolean isPublic, String creator) {
        for (int b : chain) {
            blocks[b] = new DiskData.Block(path, false);
        }
        return new DiskData.FileEntry(path, false, chain.length, chain[0], owner, isPublic, creator, chain);
    }

    private DiskData.FileEntry find(String path) {
        for (DiskData.FileEntry f : files) {
            if (f.path().equals(path)) {
                return f;
            }
        }
        return null;
    }

    // ---------- Buttons of the Files view (fake permission rules, as user1) ----------

    /** The file buttons open the form with the operation and the selected path already chosen. */
    private void onFileAction(IoOperation operation, String path) {
        openIoForm(operation, path);
    }

    private void openIoForm(IoOperation operation, String path) {
        int free = 0;
        for (int b = 0; b < SWAP_START; b++) {
            if (blocks[b] == null) {
                free++;
            }
        }
        DiskData.IoProcess request = IoProcessDialog.showDialog(window,
                new IoProcessDialog.Limits(files, free, 256), operation, path, nextPid);
        if (request != null) {
            submit(request);
        }
    }

    /** Fake permission rules of the user mode, then the request goes to the disk queue. */
    private void submit(DiskData.IoProcess request) {
        IoOperation operation = request.operation();
        String path = request.path();
        DiskData.FileEntry entry = find(path);
        FileSystemPanel view = window.getFileSystemPanel();
        if (entry == null) {
            view.showRejected("La ruta " + path + " ya no existe.");
            return;
        }
        String reason = session.admin() ? null : userRule(operation, entry);
        if (reason != null) {
            window.showRejected(reason);
            return;
        }
        DiskData.Request queued = enqueue(operation, path);
        view.showAccepted("Se creó " + queued.pid() + " (" + request.processName() + ") · " + operation + " " + path
                + (request.newName() == null ? "" : " \u2192 " + request.newName())
                + ". Su solicitud entró a la cola del disco.");
        refresh();
    }

    /** Why a user (not admin) cannot do it, or null if allowed. Fake rules, as in the statement. */
    private String userRule(IoOperation operation, DiskData.FileEntry entry) {
        String user = session.user();
        boolean mine = entry.owner().equals(user);
        if (operation == IoOperation.READ && !entry.isPublic() && !mine) {
            return entry.name() + " es privado de " + entry.owner() + " y la sesión es " + user + ".";
        }
        if (operation == IoOperation.CREATE && !mine) {
            return "En modo usuario solo puedes crear archivos dentro de tus directorios; " + entry.path()
                    + " es de " + entry.owner() + ".";
        }
        if (operation == IoOperation.UPDATE) {         // the statement gives renaming to the administrator
            return "En modo usuario no se puede cambiar el nombre de archivos ni directorios; solo el administrador.";
        }
        if (operation == IoOperation.DELETE && !mine) {
            return "En modo usuario no puedes modificar archivos del sistema ni de otros usuarios; "
                    + entry.name() + " es de " + entry.owner() + ".";
        }
        return null;
    }

    // ---------- Fake disk ----------

    private DiskData.Request enqueue(IoOperation operation, String path) {
        DiskData.FileEntry entry = find(path);
        int block = entry == null || entry.directory() ? 0 : entry.firstBlock();
        DiskData.Request request = new DiskData.Request("P" + nextPid++, operation, path, block, 0);
        DiskData.Request[] grown = new DiskData.Request[queue.length + 1];
        System.arraycopy(queue, 0, grown, 0, queue.length);
        grown[queue.length] = request;
        queue = grown;
        return request;
    }

    private void tick() {
        cycle++;
        DiskData.Request[] aged = new DiskData.Request[queue.length];
        for (int i = 0; i < queue.length; i++) {
            DiskData.Request r = queue[i];
            aged[i] = new DiskData.Request(r.pid(), r.operation(), r.target(), r.block(), r.waitCycles() + 1);
        }
        queue = aged;
        if (serving != null) {                          // reading / writing: 1 block per cycle
            servingDone++;
            if (--servingLeft <= 0) {
                serving = null;
            }
        } else if (queue.length > 0) {
            DiskData.Request target = order()[0];
            if (Math.abs(target.block() - head) <= STEP) {
                if (target.block() != head) {
                    movingUp = target.block() > head;
                }
                totalSeek += Math.abs(target.block() - head);
                head = target.block();
                waitSum += target.waitCycles();
                servedPerPolicy[policyIndex()]++;
                serving = target;
                DiskData.FileEntry entry = find(target.target());
                servingLeft = entry == null ? 1 : Math.min(6, Math.max(1, entry.blocks()));
                servingDone = 0;
                remove(target);
            } else {
                movingUp = target.block() > head;
                head += movingUp ? STEP : -STEP;
                totalSeek += STEP;
            }
        } else if (cycle % 6 == 0) {                   // keep the demo busy
            String[] paths = {"/sistema/core.dll", "/usuarios/video.mp4", "/mbr.bin", "/usuarios/data.csv",
                "/sistema/config.sys", "/usuarios/user1/tarea.txt"};
            enqueue(IoOperation.READ, paths[(int) (cycle / 6 % paths.length)]);
        }
        refresh();
    }

    private void remove(DiskData.Request done) {
        DiskData.Request[] smaller = new DiskData.Request[queue.length - 1];
        int j = 0;
        for (DiskData.Request r : queue) {
            if (!r.pid().equals(done.pid())) {
                smaller[j++] = r;
            }
        }
        queue = smaller;
    }

    /** Queue in the order the active policy would serve it, from the current head. */
    private DiskData.Request[] order() {
        if ("FIFO".equals(policy)) {
            return queue.clone();
        }
        DiskData.Request[] left = queue.clone();
        DiskData.Request[] out = new DiskData.Request[left.length];
        int count = left.length;
        int h = head;
        boolean up = movingUp || "C-SCAN".equals(policy) || "C-LOOK".equals(policy);
        for (int k = 0; k < out.length; k++) {
            int pick = 0;
            switch (policy) {
                case "SSTF" -> pick = nearest(left, count, h, 0);
                case "C-SCAN", "C-LOOK" -> {
                    pick = nearest(left, count, h, 1);
                    if (pick < 0) {
                        h = 0;
                        pick = nearest(left, count, 0, 1);
                    }
                }
                default -> {                            // SCAN and LOOK
                    pick = nearest(left, count, h, up ? 1 : -1);
                    if (pick < 0) {
                        up = !up;
                        pick = nearest(left, count, h, up ? 1 : -1);
                    }
                }
            }
            out[k] = left[pick];
            h = left[pick].block();
            left[pick] = left[count - 1];
            count--;
        }
        return out;
    }

    /** Index of the closest request (direction 1 = only at or above h, -1 = only at or below, 0 = any). */
    private static int nearest(DiskData.Request[] list, int count, int h, int direction) {
        int best = -1;
        for (int i = 0; i < count; i++) {
            int d = list[i].block() - h;
            if ((direction > 0 && d < 0) || (direction < 0 && d > 0)) {
                continue;
            }
            if (best < 0 || Math.abs(d) < Math.abs(list[best].block() - h)) {
                best = i;
            }
        }
        return best;
    }

    private int policyIndex() {
        return DiskPolicyType.fromLabel(policy).ordinal();
    }

    private void refresh() {
        window.setCycle(cycle);
        long served = 0;
        for (long n : servedPerPolicy) {
            served += n;
        }
        window.showDiskMetrics(DiskData.DiskMetrics.withCounts(totalSeek, served == 0 ? 0 : waitSum / (double) served,
                servedPerPolicy.clone(), 2, 1));
        window.addSeekSample(cycle, totalSeek, policy);
        window.getDiskPanel().showSnapshot(new DiskData.Snapshot(TOTAL, SWAP_START, head,
                movingUp ? HeadDirection.UP : HeadDirection.DOWN, blocks.clone()));
        window.getDiskPanel().showQueue(serving, servingDone + " bloques hechos", order());
        window.getFileSystemPanel().showFiles(files);
    }
}
