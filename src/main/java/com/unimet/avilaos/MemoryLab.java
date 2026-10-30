package com.unimet.avilaos.os.memory;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.MainMemory;
import com.unimet.avilaos.os.process.Pcb;

/**
 * Prueba temporal del MemoryManager. NO se sube a Git.
 *
 * @author victor1102
 */
public class MemoryLab {

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "OK    " : "FALLA ") + name);
    }

    private static Pcb process(String name, int mb) {
        return Pcb.cpuBound(name, 1, mb, 10, 100, 1, 0);
    }

    public static void main(String[] args) throws InterruptedException {
        MainMemory ram = new MainMemory(100);
        MemoryManager mm = new MemoryManager(1, ram);

        Pcb p1 = process("A", 60), p2 = process("B", 50), p3 = process("C", 30);
        mm.submit(p1);
        mm.submit(p2);
        mm.submit(p3);

        // 1. Admisión: P1 (60) cabe, P2 (50) no (quedan 40), P3 (30) sí
        LinkedList<Pcb> in = mm.admit();
        check("admite a P1 y P3, P2 no cabe", in.size() == 2 && in.get(0) == p1 && in.get(1) == p3);
        check("P2 sigue en Nuevos", mm.getNewCount() == 1 && mm.getNewProcesses().get(0) == p2);
        check("RAM usada = 90", ram.getUsedSize() == 90);
        check("sin RAM libre suficiente, admit() no admite a nadie", mm.admit().isEmpty());

        // 2. P1 termina y libera: ahora P2 sí cabe en el siguiente admit()
        p1.markReady(); p1.markRunning(1); p1.markTerminated(10, false);
        mm.release(p1);
        check("al liberar P1 quedan 30 usados", ram.getUsedSize() == 30);
        LinkedList<Pcb> in2 = mm.admit();
        check("P2 entra en el siguiente ciclo", in2.size() == 1 && in2.get(0) == p2 && ram.getUsedSize() == 80);
        p2.markReady();                   // lo que haría el kernel con cada admitido

        // 3. Errores del kernel
        try { mm.release(p1); check("rechaza liberar dos veces", false); }
        catch (IllegalStateException e) { check("rechaza liberar dos veces", true); }
        try { mm.submit(process("Gigante", 120)); check("rechaza proceso más grande que la RAM", false); }
        catch (IllegalArgumentException e) { check("rechaza proceso más grande que la RAM", true); }
        try { mm.submit(p2); check("rechaza un proceso que no está en Nuevo", false); }
        catch (IllegalArgumentException e) { check("rechaza un proceso que no está en Nuevo", true); }
        Pcb p5 = process("E", 10);
        mm.submit(p5);
        try { mm.submit(p5); check("rechaza enviar dos veces", false); }
        catch (IllegalStateException e) { check("rechaza enviar dos veces", true); }

        // 4. Deadline vencido esperando en Nuevos (contrato normal: removeFromNew)
        Pcb p6 = process("F", 90);
        mm.submit(p6);
        mm.admit();                       // entra P5 (10), P6 (90) no cabe
        check("P6 sigue esperando", mm.getNewProcesses().contains(p6));
        check("removeFromNew saca a P6", mm.removeFromNew(p6) && mm.getNewCount() == 0);
        check("removeFromNew de algo que no está devuelve false", !mm.removeFromNew(p6));

        // 5. NUEVO: terminado sin removeFromNew -> admit() lo descarta sin reservarle RAM
        MainMemory ram2 = new MainMemory(100);
        MemoryManager mm2 = new MemoryManager(1, ram2);
        Pcb big = process("Big", 80), dead = process("Dead", 40), alive = process("Vivo", 20);
        mm2.submit(big);
        mm2.admit();
        mm2.submit(dead);
        mm2.submit(alive);
        mm2.admit();                      // entra Vivo (20); Dead (40) no cabe: espera
        dead.markTerminated(3, true);     // el kernel "olvida" removeFromNew
        big.markReady(); big.markRunning(1); big.markTerminated(5, false);
        mm2.release(big);
        LinkedList<Pcb> in3 = mm2.admit();
        check("terminado en Nuevos: admit() NO lo admite", in3.isEmpty());
        check("terminado en Nuevos: no se le reserva RAM (usada = 20, la de Vivo)", ram2.getUsedSize() == 20);
        check("terminado en Nuevos: sale de la cola", mm2.getNewCount() == 0);

        // 6. NUEVO: un PCB de otro computador se rechaza
        try { mm.submit(Pcb.cpuBound("Otro", 1, 10, 10, 100, 2, 0)); check("rechaza PCB de otro computador", false); }
        catch (IllegalArgumentException e) { check("rechaza PCB de otro computador: " + e.getMessage(), true); }
        try { new MemoryManager(0, new MainMemory(10)); check("rechaza computador 0", false); }
        catch (IllegalArgumentException e) { check("rechaza computador 0", true); }

        // 7. NUEVO: buffers en la misma RAM
        MainMemory ram3 = new MainMemory(100);
        MemoryManager mm3 = new MemoryManager(1, ram3);
        check("reserva un buffer de 90 MB", mm3.reserveBuffer(90));
        check("la RAM usada incluye el buffer (barra de la GUI)", ram3.getUsedSize() == 90 && mm3.getBufferMemory() == 90);
        check("límite para procesos = 100 - 90 = 10", mm3.getProcessMemoryLimit() == 10);
        try { mm3.submit(process("P", 50)); check("rechaza proceso que nunca cabría por el buffer", false); }
        catch (IllegalArgumentException e) { check("rechaza proceso que nunca cabría por el buffer: " + e.getMessage(), true); }
        Pcb small = process("S", 10);
        mm3.submit(small);
        check("un proceso de 10 MB sí entra", mm3.admit().size() == 1 && ram3.getUsedSize() == 100);
        check("sin RAM libre ahora, el buffer no se crea (no puede esperar)", !mm3.reserveBuffer(5) && mm3.getBufferMemory() == 90);

        // 8. NUEVO: un buffer no puede dejar varado a un proceso que espera en Nuevos
        MainMemory ram5 = new MainMemory(1024);
        MemoryManager mm5 = new MemoryManager(1, ram5);
        Pcb holder = process("Ocupa", 300), bigWait = process("Big", 900);
        mm5.submit(holder);
        mm5.admit();                      // Ocupa tiene 300 MB
        mm5.submit(bigWait);              // Big (900) espera: 300 + 900 > 1024
        try { mm5.reserveBuffer(200); check("rechaza buffer que dejaría a Big sin entrar nunca", false); }
        catch (IllegalArgumentException e) { check("rechaza buffer que dejaría a Big sin entrar nunca: " + e.getMessage(), true); }
        check("  ... y no reservó nada", ram5.getUsedSize() == 300 && mm5.getBufferMemory() == 0);
        check("un buffer de 124 MB sí se permite (Big sigue cabiendo: 1024 - 124 = 900)", mm5.reserveBuffer(124));
        holder.markReady(); holder.markRunning(1); holder.markTerminated(5, false);
        mm5.release(holder);
        check("al liberarse la RAM, Big entra", mm5.admit().size() == 1 && ram5.getUsedSize() == 1024);
        MainMemory ram6 = new MainMemory(1024);
        MemoryManager mm6 = new MemoryManager(1, ram6);
        Pcb gone = process("Muerto", 900);
        mm6.submit(gone);
        gone.markTerminated(2, true);     // terminado sin removeFromNew: no cuenta
        check("un proceso ya terminado en la cola no bloquea el buffer", mm6.reserveBuffer(200));

        // 9. Concurrencia: la GUI crea procesos mientras el computador admite y libera
        MainMemory ram4 = new MainMemory(1000);
        MemoryManager mm4 = new MemoryManager(1, ram4);
        int total = 20_000;
        Thread gui = new Thread(() -> { for (int i = 0; i < total; i++) mm4.submit(process("G", 1 + i % 50)); });
        int[] finished = {0};
        Thread computer = new Thread(() -> {
            while (finished[0] < total) {
                LinkedList<Pcb> admitted = mm4.admit();
                while (!admitted.isEmpty()) {
                    mm4.release(admitted.removeFirst());
                    finished[0]++;
                }
            }
        });
        gui.start(); computer.start(); gui.join(); computer.join();
        check("concurrencia: 20.000 admitidos y liberados", finished[0] == total);
        check("concurrencia: la RAM queda en 0", ram4.getUsedSize() == 0 && mm4.getNewCount() == 0);
    }
}