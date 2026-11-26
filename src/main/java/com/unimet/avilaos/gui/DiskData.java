package com.unimet.avilaos.gui;

/**
 * Read-only data that the Disk and Files views show.
 *
 * PROVISIONAL: these records only describe what the views need. When the contracts PR (Disk,
 * FileSystem, DiskRequest, IoOperation...) is merged, the controller converts the real copies into
 * these records, or the views switch to the real types. The views never change the simulation:
 * they only paint what they receive.
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
     * @param movingUp    true if the head moves toward higher block numbers
     * @param blocks      one entry per block; null = free
     */
    public record Snapshot(int totalBlocks, int swapStart, int head, boolean movingUp, Block[] blocks) {
    }

    /**
     * One request in the disk I/O queue.
     *
     * @param pid        process that made it (e.g. "P4")
     * @param operation  CREATE, READ, UPDATE, DELETE, SUSPEND or RESUME
     * @param target     file path, or "swap" for SUSPEND / RESUME
     * @param block      target block (where the head has to go)
     * @param waitCycles cycles it has been waiting in the queue
     */
    public record Request(String pid, String operation, String target, int block, long waitCycles) {
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
     * @param creator    process that created it (e.g. "P3"), or "JSON" if it was loaded
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
