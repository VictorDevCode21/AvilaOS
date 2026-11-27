package com.unimet.avilaos.os;

import com.unimet.avilaos.hardware.Disk;
import com.unimet.avilaos.os.disk.DiskScheduler;
import com.unimet.avilaos.os.fs.FileSystem;

/**
 * Lo que el kernel usa para los procesos de E/S de archivo (Proyecto 2): el disco
 * (hardware), su planificador (la cola de E/S) y el sistema de archivos. Los tres
 * comparten el mismo mapa de bloques; los arma el Computer.
 *
 * Es un record para que el constructor del kernel no crezca con un parámetro por pieza.
 *
 * @param disk          disco del computador
 * @param diskScheduler cola de E/S del disco y su política
 * @param fileSystem    árbol de directorios y archivos
 *
 * @author NicollPinzon
 */
public record Storage(Disk disk, DiskScheduler diskScheduler, FileSystem fileSystem) {

    public Storage {
        if (disk == null || diskScheduler == null || fileSystem == null) {
            throw new IllegalArgumentException("El almacenamiento necesita el disco, su planificador y el sistema de archivos");
        }
        if (disk.getBlockMap() != fileSystem.getBlockMap()) {
            throw new IllegalArgumentException("El disco y el sistema de archivos deben usar el mismo mapa de bloques");
        }
        if (diskScheduler.getDiskSize() != disk.getBlockMap().getTotalBlocks()) {
            throw new IllegalArgumentException("El planificador de disco es de " + diskScheduler.getDiskSize()
                    + " bloques y el disco tiene " + disk.getBlockMap().getTotalBlocks());
        }
    }
}
