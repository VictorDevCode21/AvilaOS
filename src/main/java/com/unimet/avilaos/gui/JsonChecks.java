package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.FileSystemJson;
import com.unimet.avilaos.config.ProcessesJson;
import com.unimet.avilaos.config.SimulationLoader;
import java.io.File;
import java.io.IOException;

/**
 * Checks of the two JSON files for the "Cargar la simulación" dialog, done with the real parsers
 * (FileSystemJson, ProcessesJson and SimulationLoader.crossCheck): what the dialog accepts is
 * exactly what the simulation can load. Nothing is loaded here.
 *
 * @author donatovodola
 */
public final class JsonChecks {

    private JsonChecks() {
    }

    /** The file system JSON: format, disk size, head and entries. On success, a short summary. */
    public static LoadDialog.Check checkFileSystem(File file) {
        try {
            return LoadDialog.Check.ok(summary(readFileSystem(file)));
        } catch (IOException e) {
            return LoadDialog.Check.fail(cannotRead(e));
        } catch (IllegalArgumentException e) {
            return LoadDialog.Check.fail(e.getMessage());
        }
    }

    /** The processes and configuration JSON: format, settings and every process. */
    public static LoadDialog.Check checkProcesses(File file) {
        try {
            return LoadDialog.Check.ok(summary(readProcesses(file)));
        } catch (IOException e) {
            return LoadDialog.Check.fail(cannotRead(e));
        } catch (IllegalArgumentException e) {
            return LoadDialog.Check.fail(e.getMessage());
        }
    }

    /**
     * The two files together (paths that don't exist, a file created where there is one, a process
     * bigger than the swap...). Only called when both files passed their own check.
     *
     * @return one warning per line, or null if they fit
     */
    public static String crossCheck(File fileSystem, File processes) {
        try {
            return SimulationLoader.crossCheck(readFileSystem(fileSystem), readProcesses(processes));
        } catch (IOException e) {
            return cannotRead(e);
        }
    }

    static FileSystemJson.Image readFileSystem(File file) throws IOException {
        return FileSystemJson.read(file.toPath());
    }

    static ProcessesJson.Image readProcesses(File file) throws IOException {
        return ProcessesJson.read(file.toPath());
    }

    static String cannotRead(IOException e) {
        return "No se pudo leer el archivo: " + e.getMessage();
    }

    /** "200 bloques · cabezal en 50 · 4 directorios y 9 archivos". */
    static String summary(FileSystemJson.Image image) {
        int directories = 0;
        for (int i = 0; i < image.entries().size(); i++) {
            if (image.entries().get(i).directory()) {
                directories++;
            }
        }
        int files = image.size() - directories;
        return image.disk().totalBlocks() + " bloques · cabezal en " + image.disk().initialHead() + " · "
                + directories + (directories == 1 ? " directorio y " : " directorios y ")
                + files + (files == 1 ? " archivo" : " archivos");
    }

    /** "8 procesos · FCFS · disco FIFO · swap de 100 bloques · 1024 MB de RAM". */
    static String summary(ProcessesJson.Image image) {
        ProcessesJson.Settings s = image.settings();
        int count = image.processes().size();
        return count + (count == 1 ? " proceso" : " procesos") + " · " + s.cpuPolicy().getLabel()
                + " · disco " + s.diskPolicy().getLabel() + " · swap de " + s.swapBlocks() + " bloques · "
                + s.ramCapacity() + " MB de RAM";
    }
}
