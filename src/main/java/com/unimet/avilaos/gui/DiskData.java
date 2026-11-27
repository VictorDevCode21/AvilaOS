package com.unimet.avilaos.gui;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.DiskRequest;
import com.unimet.avilaos.hardware.HeadDirection;
import com.unimet.avilaos.hardware.IoOperation;
import com.unimet.avilaos.hardware.SeekPlan;

/**
 * Read-only data that the Disk and Files views show.
 *
 * The disk types come from the disk contracts (IoOperation, HeadDirection, DiskRequest, SeekPlan);
 * {@link Request#from} and {@link Request#fromPlans} turn the scheduler's copies into rows for the
 * I/O queue. Block, file and session records are still GUI-side until Disk and FileSystem exist.
 * The views never change the simulation: they only paint what they receive.
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
     * @param averageWait      average cycles a request waited in the I/O queue
     * @param servedPerPolicy  requests served with each disk policy, in the same order as the
     *                         policy labels the Disk view uses
     * @param suspensions      processes suspended by the medium-term scheduler
     * @param reactivations    processes brought back to RAM
     */
    public record DiskMetrics(long totalSeek, double averageWait, long[] servedPerPolicy, int suspensions,
            int reactivations) {

        /** Requests served in total (all policies). */
        public long served() {
            long total = 0;
            for (long n : servedPerPolicy) {
                total += n;
            }
            return total;
        }
    }

    /**
     * Who is using the simulator and in which mode.
     *
     * PROVISIONAL: the mode will come from the UserMode enum (file system).
     *
     * @param admin true = administrator (every operation), false = user (restricted access)
     * @param user  user name of the session (e.g. "admin", "user1")
     */
    public record Session(boolean admin, String user) {

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
     * @param newName     CREATE: name of the new file or directory; UPDATE: the new name; else null
     * @param directory   CREATE only: true to create a directory instead of a file
     * @param sizeBlocks  CREATE of a file: size in blocks; else 0
     * @param isPublic    CREATE only: permission of the new file or directory
     * @param memory      memory the process needs (RAM units)
     * @param priority    priority (0 = highest)
     * @param deadline    deadline in cycles
     */
    public record IoProcess(String processName, IoOperation operation, String path, String newName, boolean directory,
            int sizeBlocks, boolean isPublic, int memory, int priority, int deadline) {
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
     * @param creator    name of the process that created it (e.g. "NuevoArchivo"), or "JSON" if it was loaded
     * @param chain      block numbers in chain order (empty for directories)
     */
    public record FileEntry(String path, boolean directory, int blocks, int firstBlock, String owner,
            boolean isPublic, String creator, int[] chain) {

        /** Last part of the path ("/" for the root). */
        public String name() {
            if ("/".equals(path)) {
                return "/";
            }
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }
}
