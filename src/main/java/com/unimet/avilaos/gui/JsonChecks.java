package com.unimet.avilaos.gui;

import com.unimet.avilaos.config.FileSystemJson;
import com.unimet.avilaos.config.ProcessesJson;
import com.unimet.avilaos.config.SimulationLoader;
import java.io.File;
import java.io.IOException;

/**
 * Revisión de los dos JSON para el diálogo "Cargar la simulación", con los parsers reales
 * (FileSystemJson, ProcessesJson y SimulationLoader.crossCheck): lo que el diálogo acepta es
 * exactamente lo que la simulación puede cargar. Aquí no se carga nada.
 *
 * @author donatovodola
 */
public final class JsonChecks {

    private JsonChecks() {
    }

    /** El JSON del sistema de archivos: formato, tamaño del disco, cabezal y entradas. Si está bien, un resumen. */
    public static LoadDialog.Check checkFileSystem(File file) {
        try {
            return LoadDialog.Check.ok(summary(readFileSystem(file)));
        } catch (IOException e) {
            return LoadDialog.Check.fail(cannotRead(e));
        } catch (IllegalArgumentException e) {
            return LoadDialog.Check.fail(e.getMessage());
        }
    }

    /** El JSON de procesos y configuración: formato, configuración y cada proceso. */
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
     * Los dos archivos juntos (rutas que no existen, un archivo que se crea donde ya hay otro, un
     * proceso más grande que el swap...). Solo se llama cuando los dos pasaron su propia revisión.
     *
     * @return un aviso por línea, o null si encajan
     */
    public static String crossCheck(File fileSystem, File processes) {
        try {
            return SimulationLoader.crossCheck(readFileSystem(fileSystem), readProcesses(processes));
        } catch (IOException e) {
            return cannotRead(e);
        }
    }

    /**
     * Como crossCheck, contra un sistema de archivos ya leído (el disco actual que se conserva).
     *
     * @return un aviso por línea, o null si encajan
     */
    public static String crossCheck(FileSystemJson.Image fileSystem, File processes) {
        try {
            return SimulationLoader.crossCheck(fileSystem, readProcesses(processes));
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
