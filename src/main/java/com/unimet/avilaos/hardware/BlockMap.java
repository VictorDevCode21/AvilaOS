package com.unimet.avilaos.hardware;

import java.util.concurrent.Semaphore;

/**
 * Mapa de bloques del disco simulado: qué bloque está libre, quién lo ocupa y
 * cuál es el siguiente de su cadena. Es solo datos (no tiene cabezal ni tiempo):
 * el sistema de archivos lo usa para los archivos y el planificador a mediano
 * plazo para el área de intercambio.
 *
 * Asignación encadenada (lo pide el enunciado): cada archivo o proceso
 * suspendido es una lista enlazada de bloques y cada bloque guarda el número del
 * siguiente ({@link #END} en el último). Por eso un archivo no necesita bloques
 * contiguos: se toman los primeros bloques libres que haya (primer ajuste, de
 * menor a mayor), aunque estén salteados.
 *
 * El disco tiene dos zonas:
 * <ul>
 *   <li>archivos: bloques 0 a fileBlocks - 1;</li>
 *   <li>intercambio (swap): los swapBlocks bloques siguientes, al final.</li>
 * </ul>
 * El swap va DESPUÉS de los bloques de archivos (y no dentro de ellos) para que
 * el JSON del enunciado cargue tal cual: sus archivos llenan los 200 bloques y
 * además pide un swap de 100, así que el disco queda con 300 bloques.
 *
 * Se protege con un Semaphore propio y nunca llama código ajeno, así que quien
 * lo use puede tener tomado su propio mutex al llamarlo (siempre en ese orden).
 *
 * @author NicollPinzon
 */
public class BlockMap {

    /** Valor de "siguiente" en el último bloque de una cadena (y en los libres). */
    public static final int END = -1;

    /**
     * Copia de un bloque para la GUI.
     *
     * @param owner quién lo ocupa: la ruta del archivo o el proceso suspendido; null si está libre
     * @param swap  true si el bloque pertenece al área de intercambio
     * @param next  siguiente bloque de la cadena, o END
     */
    public record Block(String owner, boolean swap, int next) {

        public boolean isFree() {
            return owner == null;
        }
    }

    private final int fileBlocks;
    private final int swapBlocks;
    private final String[] owners;
    private final int[] next;
    private final boolean[] chainStart;   // true en el primer bloque de cada cadena
    private final Semaphore mutex = new Semaphore(1, true);

    private int freeFileBlocks;
    private int freeSwapBlocks;

    /**
     * @param fileBlocks bloques para archivos (1 o más)
     * @param swapBlocks bloques del área de intercambio (0 o más)
     * @throws IllegalArgumentException si algún tamaño no es válido
     */
    public BlockMap(int fileBlocks, int swapBlocks) {
        if (fileBlocks < 1) {
            throw new IllegalArgumentException("El disco necesita al menos 1 bloque para archivos");
        }
        if (swapBlocks < 0) {
            throw new IllegalArgumentException("El área de intercambio no puede tener bloques negativos");
        }
        this.fileBlocks = fileBlocks;
        this.swapBlocks = swapBlocks;
        this.owners = new String[fileBlocks + swapBlocks];
        this.next = new int[fileBlocks + swapBlocks];
        this.chainStart = new boolean[fileBlocks + swapBlocks];
        for (int i = 0; i < next.length; i++) {
            next[i] = END;
        }
        this.freeFileBlocks = fileBlocks;
        this.freeSwapBlocks = swapBlocks;
    }

    // ---------- Tamaños ----------

    public int getTotalBlocks() {
        return owners.length;
    }

    public int getFileBlocks() {
        return fileBlocks;
    }

    /** Primer bloque del área de intercambio (= cantidad de bloques para archivos). */
    public int getSwapStart() {
        return fileBlocks;
    }

    public int getSwapBlocks() {
        return swapBlocks;
    }

    // ---------- Asignar y liberar ----------

    /**
     * Asigna una cadena de bloques en la zona de archivos (primeros libres).
     * Quien la llama revisa antes {@link #getFreeFileBlocks()} (el sistema de
     * archivos lo hace en check): quedarse sin espacio no es un error del
     * programa, es un rechazo que se explica al usuario.
     *
     * @return el primer bloque de la cadena
     * @throws IllegalArgumentException si count < 1 o el dueño está vacío
     * @throws IllegalStateException    si no hay bloques libres suficientes
     */
    public int allocateFile(int count, String owner) {
        return allocate(0, fileBlocks, count, owner);
    }

    /**
     * Asigna una cadena de bloques en el área de intercambio (proceso suspendido).
     * El planificador a mediano plazo revisa antes {@link #getFreeSwapBlocks()}:
     * si no alcanza, no suspende a ese proceso (no es un error del programa).
     *
     * @return el primer bloque de la cadena
     * @throws IllegalArgumentException si count < 1 o el dueño está vacío
     * @throws IllegalStateException    si no hay bloques libres suficientes en el swap
     */
    public int allocateSwap(int count, String owner) {
        return allocate(fileBlocks, owners.length, count, owner);
    }

    /**
     * Asigna bloques consecutivos desde un bloque dado, encadenados en orden. Es
     * para cargar el JSON del sistema de archivos, que indica dónde empieza cada
     * archivo ("startBlock").
     *
     * @return start
     * @throws IllegalArgumentException si se sale de la zona de archivos o algún bloque ya está ocupado
     */
    public int allocateFileAt(int start, int count, String owner) {
        checkRequest(count, owner);
        // Se compara count con lo que queda desde start (y no start + count) para
        // que un "blocks" enorme en el JSON no desborde el int.
        if (start < 0 || start >= fileBlocks || count > fileBlocks - start) {
            throw new IllegalArgumentException(count + (count == 1 ? " bloque" : " bloques") + " desde el " + start
                    + " se sale" + (count == 1 ? "" : "n") + " de la zona de archivos (bloques 0 a " + (fileBlocks - 1) + ")");
        }
        mutex.acquireUninterruptibly();
        try {
            for (int b = start; b < start + count; b++) {
                if (owners[b] != null) {
                    throw new IllegalArgumentException("El bloque " + b + " ya está ocupado por " + owners[b]);
                }
            }
            for (int b = start; b < start + count; b++) {
                owners[b] = owner;
                next[b] = b + 1 < start + count ? b + 1 : END;
            }
            chainStart[start] = true;
            freeFileBlocks -= count;
            return start;
        } finally {
            mutex.release();
        }
    }

    /**
     * Libera toda la cadena que empieza en first.
     *
     * @return cuántos bloques se liberaron
     * @throws IllegalArgumentException si first está fuera del disco, libre o no es el inicio de una cadena
     */
    public int free(int first) {
        mutex.acquireUninterruptibly();
        try {
            checkChainStart(first);
            chainStart[first] = false;
            int count = 0;
            int b = first;
            while (b != END) {
                int following = next[b];
                owners[b] = null;
                next[b] = END;
                if (b < fileBlocks) {
                    freeFileBlocks++;
                } else {
                    freeSwapBlocks++;
                }
                count++;
                b = following;
            }
            return count;
        } finally {
            mutex.release();
        }
    }

    /**
     * Cambia el dueño anotado en toda la cadena (al renombrar un archivo o
     * directorio, porque el dueño de un bloque es la ruta del archivo).
     */
    public void relabel(int first, String owner) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("El dueño de los bloques no puede estar vacío");
        }
        mutex.acquireUninterruptibly();
        try {
            checkChainStart(first);
            for (int b = first; b != END; b = next[b]) {
                owners[b] = owner;
            }
        } finally {
            mutex.release();
        }
    }

    // ---------- Consultas ----------

    /** Bloques de la cadena que empieza en first, en orden (copia). */
    public int[] chainOf(int first) {
        mutex.acquireUninterruptibly();
        try {
            checkChainStart(first);
            int length = 0;
            for (int b = first; b != END; b = next[b]) {
                length++;
            }
            int[] chain = new int[length];
            int i = 0;
            for (int b = first; b != END; b = next[b]) {
                chain[i++] = b;
            }
            return chain;
        } finally {
            mutex.release();
        }
    }

    public int getFreeFileBlocks() {
        mutex.acquireUninterruptibly();
        try {
            return freeFileBlocks;
        } finally {
            mutex.release();
        }
    }

    public int getFreeSwapBlocks() {
        mutex.acquireUninterruptibly();
        try {
            return freeSwapBlocks;
        } finally {
            mutex.release();
        }
    }

    /** Primer bloque libre de la zona de archivos, o END si está llena (donde empezaría un archivo nuevo). */
    public int firstFreeFileBlock() {
        mutex.acquireUninterruptibly();
        try {
            for (int b = 0; b < fileBlocks; b++) {
                if (owners[b] == null) {
                    return b;
                }
            }
            return END;
        } finally {
            mutex.release();
        }
    }

    /** Copia de todos los bloques, para la GUI (mapa de bloques). */
    public Block[] snapshot() {
        mutex.acquireUninterruptibly();
        try {
            Block[] copy = new Block[owners.length];
            for (int b = 0; b < owners.length; b++) {
                copy[b] = new Block(owners[b], b >= fileBlocks, next[b]);
            }
            return copy;
        } finally {
            mutex.release();
        }
    }

    // ---------- Internos ----------

    private int allocate(int from, int to, int count, String owner) {
        checkRequest(count, owner);
        String zone = from == 0 ? "la zona de archivos" : "el área de intercambio";
        mutex.acquireUninterruptibly();
        try {
            int free = from == 0 ? freeFileBlocks : freeSwapBlocks;
            if (free < count) {
                throw new IllegalStateException("No hay bloques libres suficientes en " + zone
                        + ": se piden " + count + " y quedan " + free);
            }
            int first = END;
            int previous = END;
            int taken = 0;
            for (int b = from; b < to && taken < count; b++) {
                if (owners[b] == null) {
                    owners[b] = owner;
                    next[b] = END;
                    if (previous == END) {
                        first = b;
                        chainStart[b] = true;
                    } else {
                        next[previous] = b;
                    }
                    previous = b;
                    taken++;
                }
            }
            if (from == 0) {
                freeFileBlocks -= count;
            } else {
                freeSwapBlocks -= count;
            }
            return first;
        } finally {
            mutex.release();
        }
    }

    private static void checkRequest(int count, String owner) {
        if (count < 1) {
            throw new IllegalArgumentException("Hay que asignar al menos 1 bloque");
        }
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Los bloques asignados necesitan un dueño");
        }
    }

    private void checkChainStart(int block) {
        if (block < 0 || block >= owners.length) {
            throw new IllegalArgumentException("El bloque " + block + " está fuera del disco (0 a "
                    + (owners.length - 1) + ")");
        }
        if (owners[block] == null) {
            throw new IllegalArgumentException("El bloque " + block + " está libre: no es el inicio de una cadena");
        }
        if (!chainStart[block]) {
            throw new IllegalArgumentException("El bloque " + block + " está en medio de la cadena de "
                    + owners[block] + ": hay que dar el primer bloque");
        }
    }
}
