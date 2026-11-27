# Archivos de ejemplo de ÁvilaOS

Cada simulación se arma con **dos JSON** que la interfaz exige antes de iniciar el reloj: el del
**sistema de archivos** (disco y archivos) y el de **procesos y configuración**. En la app, el
diálogo "Antes de iniciar" se abre solo al arrancar (o con **Cargar JSON** / **Iniciar**): se elige
un archivo para cada fila y se pulsa **Iniciar simulación**.

Todos los casos usan el disco del enunciado: 200 bloques de archivos + 100 de swap, cabezal en el
bloque 50, dirección hacia arriba (hacia los bloques mayores) y 10 posiciones por ciclo. Los JSON no
traen la dirección ni las posiciones por ciclo, así que salen de **Configuración** (por defecto, hacia
arriba y 10): si se cambia ahí la dirección, SCAN y C-SCAN dan otro orden. El orden de
atención se lee en el **Log**, en las líneas `Disco: atiende la solicitud de P… en el bloque N`; el
desplazamiento total, en **Métricas**.

Antes de su primera decisión, el disco espera a que los procesos del JSON pidan el disco ("cola
inicial completa"): por eso la primera decisión ve todas las solicitudes, igual que en las tablas
del enunciado. En el Log aparece como `Disco: espera a que la carga inicial pida el disco (N de M
solicitudes)` y luego `Disco: empieza a atender: M solicitudes en cola`. Se desactiva con
`"waitForInitialRequests": false` en `globalConfig`.

## Casos de prueba del enunciado (P1 a P4)

Sistema de archivos: `sistema-archivos.json`. Solicitudes, en el orden en que llegan: 95, 180, 34,
119, 11, 123, 62, 64.

| Procesos | Política | Orden de atención esperado | Desplazamiento |
|---|---|---|---|
| `procesos-p1-fifo.json` | FIFO | 95, 180, 34, 119, 11, 123, 62, 64 | 644 |
| `procesos-p2-sstf.json` | SSTF | 62, 64, 34, 11, 95, 119, 123, 180 | 236 |
| `procesos-p3-scan.json` | SCAN | 62, 64, 95, 119, 123, 180, (llega al 199 y regresa), 34, 11 | 337 |
| `procesos-p4-cscan.json` | C-SCAN | 62, 64, 95, 119, 123, 180, (salta al 0), 11, 34 | 183 |

El desplazamiento de C-SCAN no cuenta el salto del 199 al 0 (cuesta 1 ciclo y no mueve el cabezal
sobre los bloques), como en el anexo del enunciado.

## Anexo del enunciado

Sistema de archivos: `sistema-archivos-anexo.json`, con siete archivos en `/anexo` que empiezan en los
bloques 16, 17, 24, 43, 82, 140 y 190 (sin solaparse). Solicitudes, en el orden en que llegan: 82, 17,
43, 140, 24, 16, 190.

| Procesos | Política | Orden de atención esperado | Desplazamiento |
|---|---|---|---|
| `procesos-anexo-fifo.json` | FIFO | 82, 17, 43, 140, 24, 16, 190 | 518 |
| `procesos-anexo-sstf.json` | SSTF | 43, 24, 17, 16, 82, 140, 190 | 208 |
| `procesos-anexo-scan.json` | SCAN | 82, 140, 190, (llega al 199 y regresa), 43, 24, 17, 16 | 332 |
| `procesos-anexo-cscan.json` | C-SCAN | 82, 140, 190, (salta al 0), 16, 17, 24, 43 | 192 (sin el salto) |

## Otros ejemplos

| Archivo | Qué muestra |
|---|---|
| `procesos.json` | El JSON de procesos del enunciado tal cual (FIFO). Con `sistema-archivos.json` da el caso P1. |
| `procesos-poca-ram.json` | Seis procesos que no caben juntos en 100 MB de RAM: el planificador a mediano plazo suspende procesos bloqueados (los escribe en el swap) y los reactiva al liberarse RAM. Con `sistema-archivos.json`: suspensiones = reactivaciones, la RAM y el swap quedan libres al final. |
| `procesos-con-error.json` | Una política de disco que no existe (`ZIGZAG`): el diálogo de carga muestra el error con su línea y columna y no deja iniciar. |

## Cómo comprobar un caso

1. Cargar `sistema-archivos.json` (o `sistema-archivos-anexo.json`) y el JSON de procesos del caso, y
   pulsar **Iniciar simulación**.
2. Cuando todos los procesos terminan, la simulación se pausa sola.
3. En **Log**, seguir las líneas `atiende la solicitud … en el bloque N`: el orden es el de la tabla.
4. En **Métricas**, "Desplazamiento total" da el valor de la tabla; la gráfica del cabezal muestra el
   desplazamiento acumulado, coloreado por la política que eligió cada solicitud.
