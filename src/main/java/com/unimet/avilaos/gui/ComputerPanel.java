package com.unimet.avilaos.gui;

import com.unimet.avilaos.datastructures.LinkedList;
import com.unimet.avilaos.hardware.CpuMode;
import com.unimet.avilaos.os.process.Pcb;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

/**
 * Estado de UN computador del clúster, como una tarjeta.
 *
 * Arriba: nombre y política (una "píldora"); el proceso en CPU con su modo (Usuario o SO)
 * y sus registros; la ocupación de la RAM. Abajo: las cuatro colas, cada una con su
 * color de estado y un contador.
 *
 * Se crea una instancia por computador (igual que la clase Computer del modelo), así
 * que mostrar 2, 3 o 4 computadores es solo crear más paneles. El panel no decide nada:
 * solo recibe datos con los métodos show*. La política se cambia en la ventana
 * "Planificación" (PlanningDialog).
 *
 * @author NicollPinzon
 */
public final class ComputerPanel extends UiKit.Card {

    private static final long serialVersionUID = 1L;

    private static final String NONE = "—";

    private final int computerId;

    // ---------- CPU (PCB del proceso en ejecución) ----------
    private final StatusDot cpuDot = new StatusDot();
    private final JLabel processLabel = Theme.label("Ociosa", new Font(Theme.BODY.getFamily(), Font.BOLD, 15), Theme.TEXT);
    private final UiKit.Pill modePill = new UiKit.Pill("", null, Theme.MUTED);
    private final JLabel detailLabel = Theme.label(" ", Theme.SMALL, Theme.MUTED);
    private final JLabel pcLabel = value();
    private final JLabel marLabel = value();
    private final JLabel remainingLabel = value();
    private final JLabel deadlineLabel = value();

    // ---------- Memoria ----------
    private final UiKit.UsageBar memoryBar = new UiKit.UsageBar();
    private final JLabel memoryText = Theme.label(NONE, Theme.SMALL, Theme.MUTED);

    // ---------- Política (solo lectura: se cambia en la ventana Planificación) ----------
    private final UiKit.Pill policyPill = new UiKit.Pill(NONE, Theme.ACCENT_SOFT, Theme.ACCENT_DARK);

    // ---------- Colas ----------
    private final ProcessTableModel newModel = new ProcessTableModel();
    private final ProcessTableModel readyModel = new ProcessTableModel();
    private final ProcessTableModel blockedModel = new ProcessTableModel();
    private final ProcessTableModel terminatedModel = new ProcessTableModel();
    private final UiKit.SectionHeader newHeader = new UiKit.SectionHeader("Nuevos", Theme.STATE_NEW);
    private final UiKit.SectionHeader readyHeader = new UiKit.SectionHeader("Listos", Theme.STATE_READY);
    private final UiKit.SectionHeader blockedHeader = new UiKit.SectionHeader("Bloqueados", Theme.STATE_BLOCKED);
    private final UiKit.SectionHeader terminatedHeader = new UiKit.SectionHeader("Terminados",
            Theme.STATE_TERMINATED);

    /**
     * @param computerId número del computador (1, 2, ...)
     */
    public ComputerPanel(int computerId) {
        super(new BorderLayout(0, 12));
        this.computerId = computerId;
        setBorder(Theme.padding(16, 16, 16, 16));
        add(buildHeader(), BorderLayout.NORTH);

        // Each computer has its own color (the same as its line in the CPU chart). Inside the card,
        // two tints of that color show where the hardware ends and the processes begin.
        Color color = Theme.computerColor(computerId);
        colors(Theme.SURFACE, Theme.tint(color, Theme.ZONE_BORDER_ALPHA));
        Color border = Theme.tint(color, Theme.ZONE_BORDER_ALPHA);
        Color text = Theme.darker(color);
        JPanel body = Theme.transparent(new JPanel(new BorderLayout(0, 12)));
        body.add(zone("CPU y memoria", Theme.tint(color, Theme.ZONE_HARDWARE_ALPHA), border, text,
                buildHardwareSection()), BorderLayout.NORTH);
        body.add(zone("Procesos", Theme.tint(color, Theme.ZONE_PROCESSES_ALPHA), border, text,
                buildQueuesSection()), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        showIdle();
    }

    // ---------- Construcción de la vista ----------

    /** Card title: computer name and its CPU policy. */
    private JPanel buildHeader() {
        JPanel header = Theme.transparent(new JPanel(new BorderLayout(8, 0)));
        JPanel name = Theme.transparent(new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0)));
        name.add(new ColorMark(Theme.computerColor(computerId)));
        name.add(Theme.label("Computador " + computerId, Theme.CARD_TITLE, Theme.TEXT));
        header.add(name, BorderLayout.WEST);
        policyPill.setToolTipText("Se cambia con Planificación, en la barra superior");
        header.add(policyPill, BorderLayout.EAST);
        return header;
    }

    /**
     * Light, semi-transparent box with a small colored title, used to group a part of the card.
     *
     * @param title     text above the content (e.g. "Procesos")
     * @param fill      background tint (with alpha)
     * @param stroke    border tint (with alpha)
     * @param titleText color of the title
     * @param content   what goes inside
     */
    private static UiKit.Card zone(String title, Color fill, Color stroke, Color titleText, JComponent content) {
        UiKit.Card zone = new UiKit.Card(new BorderLayout(0, 10)).colors(fill, stroke);
        zone.setBorder(Theme.padding(10, 12, 12, 12));
        zone.add(Theme.label(title.toUpperCase(), Theme.SMALL_BOLD, titleText), BorderLayout.NORTH);
        zone.add(content, BorderLayout.CENTER);
        return zone;
    }

    private JPanel buildHardwareSection() {
        JPanel top = Theme.transparent(new JPanel());
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        // CPU: proceso, modo, detalle y registros (blanca, para que resalte sobre el tono de la zona)
        UiKit.Card cpu = new UiKit.Card(new BorderLayout(0, 8)).colors(Theme.SURFACE, Theme.BORDER);
        cpu.setBorder(Theme.padding(12, 12, 12, 12));
        JPanel first = Theme.transparent(new JPanel(new BorderLayout(8, 0)));
        JPanel name = Theme.transparent(new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0)));
        name.add(cpuDot);
        name.add(processLabel);
        first.add(name, BorderLayout.CENTER);
        first.add(modePill, BorderLayout.EAST);
        JPanel lines = Theme.transparent(new JPanel(new BorderLayout(0, 8)));
        lines.add(first, BorderLayout.NORTH);
        lines.add(detailLabel, BorderLayout.CENTER);
        cpu.add(lines, BorderLayout.NORTH);

        JPanel registers = Theme.transparent(new JPanel(new GridLayout(1, 4, 8, 0)));
        registers.add(register("PC", pcLabel, "Program counter: dirección de la PRÓXIMA instrucción"));
        registers.add(register("MAR", marLabel, "Memory address register: dirección de la instrucción de este ciclo (MAR ← PC)"));
        registers.add(register("Instr. rest.", remainingLabel, "Instrucciones que le faltan al proceso"));
        registers.add(register("Deadline", deadlineLabel, "Ciclos que le quedan antes de su deadline"));
        cpu.add(registers, BorderLayout.SOUTH);
        top.add(fullWidth(cpu));
        top.add(Box.createVerticalStrut(12));

        // Memoria
        JPanel memoryRow = Theme.transparent(new JPanel(new BorderLayout(8, 0)));
        memoryRow.add(Theme.label("Memoria principal", Theme.SMALL_BOLD, Theme.TEXT), BorderLayout.WEST);
        memoryRow.add(memoryText, BorderLayout.EAST);
        top.add(fullWidth(memoryRow));
        top.add(Box.createVerticalStrut(6));
        memoryBar.setPreferredSize(new Dimension(100, 8));
        memoryBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        memoryBar.setAlignmentX(LEFT_ALIGNMENT);
        top.add(memoryBar);
        return top;
    }

    private JPanel buildQueuesSection() {
        // Una cola debajo de la otra: cada tabla usa todo el ancho y el motivo no se corta
        JPanel queues = Theme.transparent(new JPanel(new GridLayout(4, 1, 0, 12)));
        queues.add(queue(newHeader, newModel));
        queues.add(queue(readyHeader, readyModel));
        queues.add(queue(blockedHeader, blockedModel));
        queues.add(queue(terminatedHeader, terminatedModel));
        return queues;
    }

    private static JPanel queue(UiKit.SectionHeader header, ProcessTableModel model) {
        JPanel panel = Theme.transparent(new JPanel(new BorderLayout()));
        panel.add(header, BorderLayout.NORTH);
        panel.add(wrapTable(model), BorderLayout.CENTER);
        return panel;
    }

    private static JComponent wrapTable(ProcessTableModel model) {
        JTable table = new JTable(model) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent e) {        // el PCB completo al pasar el mouse
                int row = rowAtPoint(e.getPoint());
                return row < 0 ? null : model.describe(row);
            }
        };
        Theme.styleTable(table);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        table.setTableHeader(new JTableHeader(table.getColumnModel()) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getToolTipText(MouseEvent e) {        // nombre completo de la columna
                int col = columnAtPoint(e.getPoint());
                return col < 0 ? null
                        : ProcessTableModel.COLUMN_TIPS[getColumnModel().getColumn(col).getModelIndex()];
            }
        });
        Theme.styleTable(table);                                 // otra vez: el encabezado es nuevo
        // Con 3 computadores lado a lado no caben 9 columnas: se ocultan Tipo, PC y MB (el modelo
        // las sigue teniendo) y el PCB completo sale en el tooltip de la fila.
        TableColumnModel columns = table.getColumnModel();
        for (int hidden : new int[] {7, 4, 2}) {                 // de atrás hacia adelante
            columns.removeColumn(columns.getColumn(hidden));
        }
        int[] widths = {60, 72, 34, 42, 44, 90};               // suman ~342: lo que mide una tarjeta con 3 computadores
        String[] headers = {"ID", "Nombre", "Pr", "Rest", "DL", "Estado"};   // cortos; el nombre completo va en el tooltip
        for (int i = 0; i < widths.length; i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
            columns.getColumn(i).setHeaderValue(headers[i]);
        }
        JScrollPane scroll = Theme.scroll(table);
        scroll.setPreferredSize(new Dimension(380, 88));        // las columnas se ajustan al ancho real
        return UiKit.rounded(scroll);
    }

    private static JPanel register(String name, JLabel value, String tip) {
        JPanel panel = Theme.transparent(new JPanel(new BorderLayout(0, 2)));
        JLabel title = Theme.label(name, Theme.SMALL, Theme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        panel.setToolTipText(tip);
        title.setToolTipText(tip);
        value.setToolTipText(tip);
        return panel;
    }

    private static JLabel value() {
        return Theme.label(NONE, new Font(Theme.MONO_FONT.getFamily(), Font.PLAIN, 14), Theme.TEXT);
    }

    private static <T extends JComponent> T fullWidth(T component) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }

    // ---------- Métodos que usa quien alimenta la vista ----------
    // Todos deben llamarse desde el hilo de Swing (SwingUtilities.invokeLater).

    /**
     * Muestra el proceso en CPU con los registros de la CPU.
     * PC y MAR vienen de la Cpu (no del PCB): el PCB solo guarda el PC al salir de la CPU.
     *
     * Por qué el MAR va uno detrás del PC (ej. PC = 5, MAR = 4): en cada ciclo la Cpu hace la
     * captación MAR ← PC y luego PC ← PC + 1. Al terminar el ciclo, el MAR tiene la dirección de
     * la instrucción que se acaba de ejecutar y el PC ya apunta a la siguiente. Los dos suben
     * exactamente 1 por ciclo, como pide el enunciado; solo están desfasados un paso.
     *
     * Excepción: justo después de un cambio de contexto (Cpu.loadContext) PC == MAR, porque
     * loadContext pone los dos en el PC guardado en el PCB; el desfase aparece desde el primer
     * ciclo. Por eso quien alimenta la vista debe pasar los valores reales de la Cpu y NUNCA
     * calcular el MAR como pc - 1.
     *
     * @param running proceso en CPU, o null si está ociosa
     * @param mode    modo de la CPU (usuario o kernel)
     * @param pc      Cpu.getProgramCounter()
     * @param mar     Cpu.getMemoryAddressRegister() (no pc - 1)
     */
    public void showRunning(Pcb running, CpuMode mode, int pc, int mar) {
        if (running == null) {
            showIdle();
            return;
        }
        String process = running + " · " + running.getName();
        setText(processLabel, process);
        processLabel.setToolTipText(process);                  // nombres largos se cortan: texto completo aquí
        setText(detailLabel, running.getType().getLabel() + " · prioridad " + running.getPriority()
                + " · " + running.getMemorySize() + " MB");
        setText(pcLabel, String.valueOf(pc));
        setText(marLabel, String.valueOf(mar));
        setText(remainingLabel, String.valueOf(running.getRemainingInstructions()));
        setText(deadlineLabel, String.valueOf(running.getRemainingDeadline()));
        cpuDot.setColor(Theme.STATE_RUNNING);
        boolean user = mode == CpuMode.USER;
        setText(modePill, user ? "Usuario" : "Modo SO");
        modePill.setColors(user ? Theme.USER_BG : Theme.KERNEL_BG, user ? Theme.USER_FG : Theme.KERNEL_FG);
    }

    /** CPU sin proceso: todo en "—". */
    public void showIdle() {
        setText(processLabel, "CPU ociosa");
        processLabel.setToolTipText(null);
        setText(detailLabel, "Esperando un proceso en Listos");
        for (JLabel label : new JLabel[] {pcLabel, marLabel, remainingLabel, deadlineLabel}) {
            setText(label, NONE);
        }
        cpuDot.setColor(Theme.BORDER_STRONG);
        setText(modePill, "");
        modePill.setColors(null, Theme.MUTED);
    }

    /** Muestra la ocupación de la RAM ("usada / libre", como pide el enunciado). */
    public void showMemory(int usedMb, int totalMb) {
        int percent = totalMb == 0 ? 0 : (int) Math.round(usedMb * 100.0 / totalMb);
        memoryBar.setPercent(percent);
        setText(memoryText, "Usada " + usedMb + " · Libre " + (totalMb - usedMb) + " MB");
        memoryBar.setToolTipText(percent + "% de " + totalMb + " MB en uso");
    }

    /**
     * Muestra las cuatro colas. El orden de cada tabla es el orden real de la cola.
     * Recibe COPIAS tomadas con el candado de su dueño (ver ProcessTableModel).
     */
    public void showQueues(LinkedList<Pcb> newQueue, LinkedList<Pcb> readyQueue,
            LinkedList<Pcb> blockedQueue, LinkedList<Pcb> terminatedQueue) {
        newModel.setProcesses(newQueue);
        readyModel.setProcesses(readyQueue);
        blockedModel.setProcesses(blockedQueue);
        terminatedModel.setProcesses(terminatedQueue);
        newHeader.setCount(newModel.getRowCount());
        readyHeader.setCount(readyModel.getRowCount());
        blockedHeader.setCount(blockedModel.getRowCount());
        terminatedHeader.setCount(terminatedModel.getRowCount());
    }

    /**
     * Muestra la política que tiene el planificador del computador ahora.
     *
     * @param label   etiqueta de la política (PolicyType.getLabel())
     * @param quantum quantum de Round Robin; se muestra solo si la política lo usa (0 = no mostrar)
     */
    public void showPolicy(String label, int quantum) {
        setText(policyPill, quantum > 0 ? label + " · q " + quantum : label);
    }

    /** Deja el panel como recién creado (ej. al reiniciar la simulación). */
    public void clear() {
        showIdle();
        showMemory(0, 0);
        setText(memoryText, NONE);
        newModel.clear();
        readyModel.clear();
        blockedModel.clear();
        terminatedModel.clear();
        for (UiKit.SectionHeader header : new UiKit.SectionHeader[] {newHeader, readyHeader, blockedHeader,
            terminatedHeader}) {
            header.setCount(0);
        }
    }

    public int getComputerId() {
        return computerId;
    }

    /** Cambia el texto solo si es distinto: el refresco corre cada 150 ms y así no se redibuja de más. */
    private static void setText(JLabel label, String text) {
        if (!text.equals(label.getText())) {
            label.setText(text);
        }
    }

    /** Punto de color del estado de la CPU (verde = ejecutando, gris = ociosa). */
    /** Small rounded square with the computer's color, next to its name. */
    private static final class ColorMark extends JComponent {

        private static final long serialVersionUID = 1L;
        private final Color color;

        ColorMark(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(12, 12));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(0, (getHeight() - 12) / 2, 12, 12, 5, 5);
            g2.dispose();
        }
    }

    private static final class StatusDot extends JComponent {

        private static final long serialVersionUID = 1L;
        private Color color = Theme.BORDER_STRONG;

        StatusDot() {
            setPreferredSize(new Dimension(10, 18));
        }

        void setColor(Color color) {
            if (!color.equals(this.color)) {
                this.color = color;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, getHeight() / 2 - 5, 10, 10);
            g2.dispose();
        }
    }
}
