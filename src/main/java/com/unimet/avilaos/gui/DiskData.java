package com.unimet.avilaos.gui;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.BlockMap;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.IoOperation;
import com.unimet.avilaos.hardware.SeekPlan;
import com.unimet.avilaos.os.fs.FileInfo;
import com.unimet.avilaos.os.fs.Permission;
import com.unimet.avilaos.os.fs.UserSession;

/**
 * Read-only data that the Disk and Files views show: the views' own small records, so they don't
 * depend on how the simulation stores things (and the demo can fill them with fake data).
 *
 * The {@code from} factories turn the simulation's copies into these records: BlockMap.Block
 * ({@link Snapshot#from}), DiskRequest and SeekPlan ({@link Request#from}, {@link Request#fromPlans}),
 * FileInfo ({@link FileEntry#from}) and UserSession ({@link Session#from}). The views never change
 * the simulation: they only paint what they receive.
 *
 * Arrays are used on purpose (java.util collections are not allowed in the project).
 *
 * @author donatovodola
 */
public final class DiskData {

    private DiskData() {
    }

    /**
     * Who uses a block of the disk.
     *
     * @param owner   file path (e.g. "/sistema/boot.bin") or process id (e.g. "P7")
     * @param process true if it is a suspended process in the swap area, false if it is a file
     */
    public record Block(String owner, boolean process) {
    }

    /**
     * Copy of the disk at one cycle.
     *
     * @param totalBlocks number of blocks (block numbers go from 0 to totalBlocks - 1)
     * @param swapStart   first block of the swap area (swap goes from swapStart to the end)
     * @param head        current head position (block number)
     * @param direction   direction the head is moving in
     * @param blocks      one entry per block; null = free
     */
    public record Snapshot(int totalBlocks, int swapStart, int head, HeadDirection direction, Block[] blocks) {

        /**
         * Copy of the real disk.
         *
         * @param blocks    BlockMap.snapshot(): the owner of a swap block is the suspended process
         * @param swapStart BlockMap.getSwapStart()
         * @param head      Disk.getHeadBlock()
         * @param direction Disk.getHeadDirection()
         */
        public static Snapshot from(BlockMap.Block[] blocks, int swapStart, int head, HeadDirection direction) {
            Block[] rows = new Block[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                BlockMap.Block b = blocks[i];
                rows[i] = b.isFree() ? null : new Block(b.owner(), b.swap());
            }
            return new Snapshot(blocks.length, swapStart, head, direction, rows);
        }
    }

    /**
     * One request in the disk I/O queue.
     *
     * @param pid        process that made it (e.g. "P4")
     * @param operation  operation of the request
     * @param target     file path, or DiskRequest.SWAP_TARGET for SUSPEND / RESUME
     * @param block      target block (where the head has to go)
     * @param waitCycles cycles it has been waiting in the queue
     */
    public record Request(String pid, IoOperation operation, String target, int block, long waitCycles) {

        /**
         * Row for a request of the disk scheduler.
         *
         * @param request      a request from DiskScheduler (getPendingRequests or a SeekPlan)
         * @param currentCycle current clock cycle, to compute how long it has been waiting
         */
        public static Request from(DiskRequest request, long currentCycle) {
            return new Request("P" + request.getProcessId(), request.getOperation(), request.getTarget(),
                    request.getTargetBlock(), Math.max(0, currentCycle - request.getArrivalCycle()));
        }

        /**
         * Rows in service order, from DiskScheduler.previewServiceOrder(head, direction).
         *
         * @param plans        the preview (it is not modified)
         * @param currentCycle current clock cycle
         */
        public static Request[] fromPlans(LinkedList<SeekPlan> plans, long currentCycle) {
            LinkedList<SeekPlan> walk = plans.copy();         // removeFirst is O(1); get(i) would be O(n)
            Request[] rows = new Request[walk.size()];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = from(walk.removeFirst().getRequest(), currentCycle);
            }
            return rows;
        }
    }

    /**
     * Disk and suspension metrics at one moment (copies; the GUI only shows them).
     *
     * @param totalSeek        total head displacement in blocks (the C-SCAN jump does not count)
     * @param averageWait      average cycles a request waited in the I/O queue, or NaN if unknown
     * @param served           requests served in total (all policies)
     * @param servedPerPolicy  requests served with each disk policy, in the same order as the
     *                         policy labels the Disk view uses; null while the simulation doesn't
     *                         count them per policy
     * @param suspensions      processes suspended by the medium-term scheduler, or -1 if not counted
     * @param reactivations    processes brought back to RAM, or -1 if not counted
     */
    public record DiskMetrics(long totalSeek, double averageWait, long served, long[] servedPerPolicy,
            int suspensions, int reactivations) {

        /** Metrics where the per-policy counts add up to the total (e.g. the demo). */
        public static DiskMetrics withCounts(long totalSeek, double averageWait, long[] servedPerPolicy,
                int suspensions, int reactivations) {
            long total = 0;
            for (long n : servedPerPolicy) {
                total += n;
            }
            return new DiskMetrics(totalSeek, averageWait, total, servedPerPolicy, suspensions, reactivations);
        }
    }

    /**
     * Who is using the simulator and in which mode.
     *
     * @param admin true = administrator (every operation), false = user (restricted access)
     * @param user  user name of the session (e.g. "admin", "user1")
     */
    public record Session(boolean admin, String user) {

        /** The session as the file system sees it (it checks the permissions with it). */
        public UserSession toUserSession() {
            return admin ? UserSession.admin(user) : UserSession.user(user);
        }

        public static Session from(UserSession session) {
            return new Session(session.isAdmin(), session.getUserName());
        }

        /** Short text for the rail and the Files view, e.g. "Admin" or "user2". */
        public String shortText() {
            return admin ? "Admin" : user;
        }

        /** Full text, e.g. "Administrador (admin)" or "Usuario: user2". */
        public String longText() {
            return admin ? "Administrador (" + user + ")" : "Usuario: " + user;
        }
    }

    /**
     * What the user asked for in the "Nuevo proceso de E/S" form. The GUI only builds this request:
     * the simulation creates the I/O process (or rejects it) and the disk does the operation.
     *
     * @param processName name of the new I/O process
     * @param operation   CREATE, READ, UPDATE or DELETE (a file operation)
     * @param path        target: the file or directory (for CREATE, the directory where it goes)
     * @param newName     CREATE: name of the new file or directory; UPDATE: the new name, o null para
     *                    reescribir el archivo en su lugar; else null
     * @param directory   CREATE only: true to create a directory instead of a file
     * @param sizeBlocks  CREATE of a file: size in blocks; else 0
     * @param isPublic    CREATE only: permission of the new file or directory
     * @param owner       CREATE of a directory: its owner (e.g. "user1" for their folder); else null
     * @param memory      memory the process needs (RAM units)
     * @param priority    priority (0 = highest)
     * @param deadline    deadline in cycles
     */
    public record IoProcess(String processName, IoOperation operation, String path, String newName, boolean directory,
            int sizeBlocks, boolean isPublic, String owner, int memory, int priority, int deadline) {
    }

    /**
     * One node of the file system (file or directory).
     *
     * @param path       full path, e.g. "/usuarios/notas.txt" ("/" is the root)
     * @param directory  true for a directory
     * @param blocks     size in blocks (0 for directories)
     * @param firstBlock first block of the chain (-1 for directories)
     * @param owner      owner user (e.g. "admin", "user1")
     * @param isPublic   true = public, false = private
     * @param creator    process that created it, PID and name (e.g. "P5 (NuevoArchivo)"), or "JSON" if it was loaded
     * @param chain      block numbers in chain order (empty for directories)
     */
    public record FileEntry(String path, boolean directory, int blocks, int firstBlock, String owner,
            boolean isPublic, String creator, int[] chain) {

        /** Row for a node of the real file system (FileSystem.list()). */
        public static FileEntry from(FileInfo info) {
            return new FileEntry(info.getPath(), info.isDirectory(), info.getBlockCount(), info.getFirstBlock(),
                    info.getOwner(), info.getPermission() == Permission.PUBLIC, info.getCreator(), info.getChain());
        }

        /** Every node, in tree order (FileSystem.list() is not modified). */
        public static FileEntry[] fromList(LinkedList<FileInfo> infos) {
            LinkedList<FileInfo> walk = infos.copy();
            FileEntry[] rows = new FileEntry[walk.size()];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = from(walk.removeFirst());
            }
            return rows;
        }

        /** Last part of the path ("/" for the root). */
        public String name() {
            if ("/".equals(path)) {
                return "/";
            }
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }
}
