package com.unimet.avilaos.metrics;

/**
 * Foto de las métricas (de un computador o del sistema) tomadas en el mismo instante.
 * La GUI lee esta foto en cada refresco, así todos los valores que muestra son
 * consistentes entre sí. Inmutable: segura para pasar entre hilos.
 *
 * IMPORTANTE: los promedios (respuesta, retorno, equidad y bloqueo en semáforos) y la tasa
 * de deadlines solo incluyen procesos TERMINADOS. Un proceso que sigue en el sistema no
 * cuenta hasta que termina. La utilización y el ciclo sí reflejan el instante actual.
 *
 * @param cycle                       último ciclo registrado
 * @param completedProcesses          procesos terminados (a tiempo, por deadline o fallidos)
 * @param throughput                  procesos terminados a tiempo por ciclo
 * @param cpuUtilization              ciclos ocupada / ciclos totales (0 a 1)
 * @param averageResponseTime         primer ciclo en CPU - llegada (promedio, en ciclos)
 * @param averageTurnaroundTime       fin - llegada (promedio, en ciclos)
 * @param deadlineComplianceRate      terminados a tiempo / terminados, sin los fallidos (0 a 1)
 * @param fairnessIndex               índice de Jain (1 = equidad perfecta)
 * @param averageSemaphoreBlockedTime ciclos bloqueado en semáforos (promedio de productores/consumidores)
 * @param failedProcesses             procesos de E/S que terminaron porque su operación no se pudo
 *                                    hacer (Proyecto 2): se muestran aparte; cuentan en terminados y en
 *                                    los tiempos, pero no en el rendimiento ni en el cumplimiento
 *
 * @author NicollPinzon
 */
public record MetricsSnapshot(long cycle, long completedProcesses, double throughput,
        double cpuUtilization, double averageResponseTime, double averageTurnaroundTime,
        double deadlineComplianceRate, double fairnessIndex, double averageSemaphoreBlockedTime,
        long failedProcesses) {
}
