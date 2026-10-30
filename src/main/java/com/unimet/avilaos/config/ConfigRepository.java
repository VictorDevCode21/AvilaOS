package com.unimet.avilaos.config;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.os.process.ProcessType;
import com.unimet.avilaos.os.scheduling.PolicyType;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Guarda y carga la configuración en un archivo CSV (formato permitido por el enunciado).
 * Hecho a mano, sin librerías: el formato es simple y así no hay que tocar el pom.
 *
 * Cada fila empieza con su tipo; las líneas con # son comentarios:
 * <pre>
 * setting,cycleDurationMs,500
 * setting,quantum,3
 * setting,networkLatency,2
 * computer,1,1024,ROUND_ROBIN
 * buffer,B1,1,3,64
 * process,Prod,PRODUCER,1,64,100,300,2,0,0,B1,2,5
 * </pre>
 *
 * Al leer, cualquier dato inválido produce IllegalArgumentException con el número
 * de línea, para que la GUI lo muestre sin detener el simulador.
 * No usa Files.readAllLines porque devuelve java.util.List (prohibido): lee línea a línea.
 *
 * @author donatovodola
 */
public class ConfigRepository {

    public static final String DEFAULT_FILE = "avilaos-config.csv";

    private static final String SETTING = "setting";
    private static final String COMPUTER = "computer";
    private static final String BUFFER = "buffer";
    private static final String PROCESS = "process";

    // ---------- Guardar ----------

    public void save(SimulationConfig config, Path file) throws IOException {
        if (config == null || file == null) {
            throw new IllegalArgumentException("Se necesita una configuración y un archivo");
        }
        config.validate();
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            line(out, "# Configuración de ÁvilaOS");
            line(out, "# setting,clave,valor");
            line(out, SETTING + ",cycleDurationMs," + config.getCycleDurationMs());
            line(out, SETTING + ",quantum," + config.getQuantum());
            line(out, SETTING + ",networkLatency," + config.getNetworkLatency());

            line(out, "# computer,id,ramMB,politica (" + names(PolicyType.values()) + ")");
            LinkedList<ComputerConfig> computers = config.getComputers();
            for (int i = 0; i < computers.size(); i++) {
                ComputerConfig c = computers.get(i);
                line(out, COMPUTER + "," + c.id() + "," + c.ramSize() + "," + c.initialPolicy().name());
            }

            line(out, "# buffer,nombre,anfitrion,capacidad,memoriaMB");
            LinkedList<BufferConfig> buffers = config.getBuffers();
            for (int i = 0; i < buffers.size(); i++) {
                BufferConfig b = buffers.get(i);
                line(out, BUFFER + "," + b.name() + "," + b.hostComputerId() + "," + b.capacity() + "," + b.memorySize());
            }

            line(out, "# process,nombre,tipo (" + names(ProcessType.values()) + "),prioridad,memoriaMB,"
                    + "instrucciones,deadline,computador (0=automatico),intervaloES,duracionES,buffer,"
                    + "intervaloOperacion,elementosRequeridos");
            LinkedList<ProcessConfig> processes = config.getProcesses();
            for (int i = 0; i < processes.size(); i++) {
                ProcessConfig p = processes.get(i);
                line(out, PROCESS + "," + p.name() + "," + p.type().name() + "," + p.priority() + ","
                        + p.memorySize() + "," + p.instructions() + "," + p.deadline() + "," + p.computerId() + ","
                        + p.ioInterval() + "," + p.ioDuration() + ","
                        + (p.bufferName() == null ? "" : p.bufferName()) + ","
                        + p.operationInterval() + "," + p.itemsRequired());
            }
        }
    }

    // ---------- Cargar ----------

    /**
     * Lee el archivo y arma la configuración: primero ajustes y computadores,
     * luego buffers y al final procesos, sin importar el orden de las filas.
     *
     * @throws IOException              si el archivo no existe o no se puede leer
     * @throws IllegalArgumentException si algún dato es inválido (indica la línea)
     */
    public SimulationConfig load(Path file) throws IOException {
        LinkedList<Row> settings = new LinkedList<>();
        LinkedList<Row> computers = new LinkedList<>();
        LinkedList<Row> buffers = new LinkedList<>();
        LinkedList<Row> processes = new LinkedList<>();

        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String text;
            int number = 0;
            while ((text = in.readLine()) != null) {
                number++;
                String trimmed = text.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                Row row = new Row(number, trimmed.split(",", -1));
                switch (row.cell(0).toLowerCase()) {
                    case SETTING -> settings.addLast(row);
                    case COMPUTER -> computers.insertSorted(row, (a, b) -> Integer.compare(a.id(), b.id()));
                    case BUFFER -> buffers.addLast(row);
                    case PROCESS -> processes.addLast(row);
                    default -> throw row.error("tipo de fila desconocido '" + row.cell(0) + "'");
                }
            }
        }

        SimulationConfig config = buildBase(settings);
        for (int i = 0; i < computers.size(); i++) {
            Row r = computers.get(i);
            r.expect(4, "computer,id,ramMB,politica");
            int expectedId = i + 1;
            if (r.integer(1, "id") != expectedId) {
                throw r.error("los computadores deben numerarse 1, 2, 3... (falta el " + expectedId + ")");
            }
            r.apply(() -> config.addComputer(r.integer(2, "RAM"), parsePolicy(r, 3)));
        }
        for (int i = 0; i < buffers.size(); i++) {
            Row r = buffers.get(i);
            r.expect(5, "buffer,nombre,anfitrion,capacidad,memoriaMB");
            r.apply(() -> config.addBuffer(new BufferConfig(r.cell(1), r.integer(2, "anfitrión"),
                    r.integer(3, "capacidad"), r.integer(4, "memoria"))));
        }
        for (int i = 0; i < processes.size(); i++) {
            Row r = processes.get(i);
            r.expect(13, "process,nombre,tipo,prioridad,memoriaMB,instrucciones,deadline,computador,"
                    + "intervaloES,duracionES,buffer,intervaloOperacion,elementosRequeridos");
            r.apply(() -> config.addProcess(new ProcessConfig(r.cell(1), parseType(r, 2),
                    r.integer(3, "prioridad"), r.integer(4, "memoria"), r.integer(5, "instrucciones"),
                    r.number(6, "deadline"), r.integer(7, "computador"),
                    r.integer(8, "intervalo de E/S"), r.integer(9, "duración de E/S"),
                    r.cell(10).isEmpty() ? null : r.cell(10),
                    r.integer(11, "intervalo de operación"), r.integer(12, "elementos requeridos"))));
        }
        config.validate();
        return config;
    }

    // ---------- Internos ----------

    /** Crea la configuración con los 3 ajustes obligatorios. */
    private SimulationConfig buildBase(LinkedList<Row> settings) {
        long cycle = -1;
        int quantum = -1;
        int latency = -1;
        for (int i = 0; i < settings.size(); i++) {
            Row r = settings.get(i);
            r.expect(3, "setting,clave,valor");
            switch (r.cell(1)) {
                case "cycleDurationMs" -> cycle = r.number(2, "duración del ciclo");
                case "quantum" -> quantum = r.integer(2, "quantum");
                case "networkLatency" -> latency = r.integer(2, "latencia de red");
                default -> throw r.error("ajuste desconocido '" + r.cell(1) + "'");
            }
        }
        if (cycle < 0 || quantum < 0 || latency < 0) {
            throw new IllegalArgumentException(
                    "Faltan ajustes: el archivo debe tener cycleDurationMs, quantum y networkLatency");
        }
        return new SimulationConfig(cycle, quantum, latency);
    }

    private static PolicyType parsePolicy(Row r, int column) {
        String value = r.cell(column);
        try {
            return PolicyType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException notAName) {
            try {
                return PolicyType.fromLabel(value);     // también acepta "Round Robin"
            } catch (IllegalArgumentException notALabel) {
                throw r.error("política desconocida '" + value + "'");
            }
        }
    }

    private static ProcessType parseType(Row r, int column) {
        try {
            return ProcessType.valueOf(r.cell(column).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw r.error("tipo de proceso desconocido '" + r.cell(column) + "'");
        }
    }

    private static void line(BufferedWriter out, String text) throws IOException {
        out.write(text);
        out.newLine();
    }

    private static String names(Enum<?>[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            sb.append(i == 0 ? "" : "/").append(values[i].name());
        }
        return sb.toString();
    }

    /** Una fila del CSV con su número de línea, para dar errores precisos. */
    private record Row(int number, String[] cells) {

        String cell(int index) {
            return index < cells.length ? cells[index].trim() : "";
        }

        int id() {
            try {
                return Integer.parseInt(cell(1));
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;              // el error se reporta al validar
            }
        }

        void expect(int columns, String format) {
            if (cells.length != columns) {
                throw error("se esperaban " + columns + " columnas (" + format + ") y hay " + cells.length);
            }
        }

        long number(int index, String field) {
            String value = cell(index);
            if (value.isEmpty()) {
                return 0;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw error("'" + value + "' no es un número válido para " + field);
            }
        }

        int integer(int index, String field) {
            long value = number(index, field);
            if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                throw error("el valor de " + field + " es demasiado grande");
            }
            return (int) value;
        }

        /** Ejecuta un paso de armado y, si falla, le agrega el número de línea al mensaje. */
        void apply(Runnable step) {
            try {
                step.run();
            } catch (IllegalArgumentException e) {
                throw e.getMessage() != null && e.getMessage().startsWith("Línea ") ? e : error(e.getMessage());
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Línea " + number + ": " + message);
        }
    }
}
