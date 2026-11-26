package com.unimet.avilaos.gui;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Demo of the "Disco" and "Archivos" views with fake data (no kernel, no real disk).
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
    private static final String SESSION_USER = "user1";

    private final MainWindow window = new MainWindow(2);
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

    private DiskFsDemo() {
        loadFileSystem();
        window.getDiskPanel().showPolicy(policy);
        window.getDiskPanel().setPolicyListener(chosen -> {
            policy = chosen;
            window.getDiskPanel().showPolicy(policy);
            refresh();
        });
        window.getFileSystemPanel().setFileActionListener(this::onFileAction);
        String[][] first = {{"READ", "/sistema/config.sys"}, {"READ", "/usuarios/video.mp4"},
            {"UPDATE", "/usuarios/notas.txt"}, {"DELETE", "/usuarios/img1.png"}, {"READ", "/sistema/boot.bin"},
            {"UPDATE", "/usuarios/data.csv"}, {"READ", "/sistema/script.py"}, {"READ", "/sistema/core.dll"}};
        for (String[] r : first) {
            enqueue(r[0], r[1]);
        }
        refresh();
        new Timer(450, e -> tick()).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            DiskFsDemo demo = new DiskFsDemo();
            demo.window.showView(MainWindow.VIEW_DISK);
            demo.window.setVisible(true);
        });
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

    private void onFileAction(String operation, String path) {
        DiskData.FileEntry entry = find(path);
        FileSystemPanel view = window.getFileSystemPanel();
        if (entry == null) {
            view.showRejected("La ruta " + path + " ya no existe.");
            return;
        }
        boolean mine = entry.owner().equals(SESSION_USER);
        if ("READ".equals(operation) && !entry.isPublic() && !mine) {
            view.showRejected(entry.name() + " es privado de " + entry.owner() + " y la sesión es " + SESSION_USER
                    + ". La simulación sigue corriendo.");
            return;
        }
        if (!"READ".equals(operation) && !mine) {
            view.showRejected("En modo usuario solo puedes modificar tus propios archivos; " + entry.name()
                    + " es de " + entry.owner() + ".");
            return;
        }
        DiskData.Request request = enqueue(operation, path);
        view.showAccepted("Se creó " + request.pid() + " · " + operation + " " + path
                + ". Su solicitud entró a la cola del disco.");
        refresh();
    }

    // ---------- Fake disk ----------

    private DiskData.Request enqueue(String operation, String path) {
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
                head = target.block();
                serving = target;
                DiskData.FileEntry entry = find(target.target());
                servingLeft = entry == null ? 1 : Math.min(6, Math.max(1, entry.blocks()));
                servingDone = 0;
                remove(target);
            } else {
                movingUp = target.block() > head;
                head += movingUp ? STEP : -STEP;
            }
        } else if (cycle % 6 == 0) {                   // keep the demo busy
            String[] paths = {"/sistema/core.dll", "/usuarios/video.mp4", "/mbr.bin", "/usuarios/data.csv",
                "/sistema/config.sys", "/usuarios/user1/tarea.txt"};
            enqueue("READ", paths[(int) (cycle / 6 % paths.length)]);
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
        boolean up = movingUp || "C-SCAN".equals(policy);
        for (int k = 0; k < out.length; k++) {
            int pick = 0;
            switch (policy) {
                case "SSTF" -> pick = nearest(left, count, h, 0);
                case "C-SCAN" -> {
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

    private void refresh() {
        window.setCycle(cycle);
        window.getDiskPanel().showSnapshot(new DiskData.Snapshot(TOTAL, SWAP_START, head, movingUp, blocks.clone()));
        window.getDiskPanel().showQueue(serving, servingDone, order());
        window.getFileSystemPanel().showFiles(files);
    }
}
