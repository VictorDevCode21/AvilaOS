package com.unimet.avilaos;

import com.unimet.avilaos.config.ConfigRepository;
import com.unimet.avilaos.config.SimulationConfig;
import com.unimet.avilaos.gui.SimulationController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Punto de entrada de ÁvilaOS. Carga la configuración (avilaos-config.csv en la carpeta
 * del proyecto, o la de por defecto si no existe o tiene errores) y abre la ventana.
 *
 * @author victor1102
 */
public class AvilaOS {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimulationController(loadConfig()).show());
    }

    private static SimulationConfig loadConfig() {
        Path file = Path.of(ConfigRepository.DEFAULT_FILE);
        if (!Files.exists(file)) {
            return SimulationConfig.defaults();
        }
        try {
            return new ConfigRepository().load(file);
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(null, "No se pudo leer " + file + ":\n" + e.getMessage()
                    + "\n\nSe usará la configuración por defecto.", "Configuración", JOptionPane.WARNING_MESSAGE);
            return SimulationConfig.defaults();
        }
    }
}
