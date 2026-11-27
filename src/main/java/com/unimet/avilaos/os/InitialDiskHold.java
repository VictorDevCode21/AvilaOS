package com.unimet.avilaos.os;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.os.process.Pcb;
import com.unimet.avilaos.os.process.ProcessState;

/**
 * Regla "cola inicial completa": antes de su PRIMERA decisión, el disco espera a que los
 * procesos de E/S de la carga inicial (los del JSON de procesos) hayan pedido el disco o ya no
 * puedan pedirlo (terminaron, murieron por deadline o fallaron). Así la primera decisión ve la
 * cola completa y los casos del enunciado salen igual en vivo que en la tabla: sin esta regla,
 * los procesos piden el disco de a uno, a medida que obtienen la CPU, y la primera decisión
 * solo ve una solicitud.
 *
 * Esta clase guarda el estado de la espera; quien decide cuándo soltar el disco es el kernel
 * (Kernel.checkInitialHold), que además lo suelta antes si se cumple el tope de ciclos, si el
 * planificador a mediano plazo necesita el disco o si los que faltan solo pueden esperar RAM.
 *
 * Hilos: se crea en el hilo que arma la simulación (antes de iniciar el reloj) y, una vez
 * publicada, solo la usa el hilo del computador.
 *
 * @author victor1102
 */
final class InitialDiskHold {

    private final LinkedList<Pcb> initial;
    private final LinkedList<Pcb> requested = new LinkedList<>();
    private final long maxCycle;
    private int lastReported = -1;

    /**
     * @param initial  procesos de E/S de archivos de la carga inicial (se copia la lista)
     * @param maxCycle último ciclo de espera: en ese ciclo el disco se suelta aunque falten
     */
    InitialDiskHold(LinkedList<Pcb> initial, long maxCycle) {
        if (initial == null || initial.isEmpty()) {
            throw new IllegalArgumentException("La carga inicial no tiene procesos de E/S a los que esperar");
        }
        if (maxCycle < 1) {
            throw new IllegalArgumentException("El tope de espera del disco debe ser de al menos 1 ciclo");
        }
        this.initial = initial.copy();
        this.maxCycle = maxCycle;
    }

    /** El proceso pidió el disco (si es de la carga inicial, ya cuenta). */
    void requested(Pcb pcb) {
        if (initial.contains(pcb) && !requested.contains(pcb)) {
            requested.addLast(pcb);
        }
    }

    /** Cuántos procesos de la carga inicial hay (M). */
    int size() {
        return initial.size();
    }

    /** Cuántos ya pidieron el disco o ya no lo pedirán porque terminaron (N). */
    int generated() {
        int count = 0;
        for (int i = 0; i < initial.size(); i++) {
            Pcb pcb = initial.get(i);
            if (requested.contains(pcb) || pcb.getState() == ProcessState.TERMINATED) {
                count++;
            }
        }
        return count;
    }

    /** Cuántos de la carga inicial ya pidieron el disco (las solicitudes que el disco espera). */
    int requestCount() {
        return requested.size();
    }

    /** true si todos los que faltan por pedir el disco siguen en Nuevo (esperando RAM). */
    boolean missingOnlyWaitForRam() {
        for (int i = 0; i < initial.size(); i++) {
            Pcb pcb = initial.get(i);
            boolean missing = !requested.contains(pcb) && pcb.getState() != ProcessState.TERMINATED;
            if (missing && pcb.getState() != ProcessState.NEW) {
                return false;
            }
        }
        return true;
    }

    long getMaxCycle() {
        return maxCycle;
    }

    /** true si la cuenta cambió desde la última vez que se escribió en el log (y la recuerda). */
    boolean countChanged(int count) {
        if (count == lastReported) {
            return false;
        }
        lastReported = count;
        return true;
    }
}
