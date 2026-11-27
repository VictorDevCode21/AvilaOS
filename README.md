# ÁvilaOS

Simulador de un sistema operativo hecho en Java para la materia **Sistemas Operativos (BPTSP04)**
de la Universidad Metropolitana, trimestre 2627-1. Bibliografía: Stallings, *Sistemas Operativos*.

- **Proyecto 1:** un clúster de computadores sincronizados por un reloj global, cada uno con CPU,
  RAM, dispositivo de E/S y su propio kernel; políticas de planificación de CPU, procesos CPU bound
  e I/O bound, productores y consumidores con buffers acotados, deadlines y métricas.
- **Proyecto 2 (este repositorio, rama `develop`):** un solo computador con un **disco simulado**,
  un **sistema de archivos jerárquico** con asignación encadenada, **planificación de disco**
  (FIFO, SSTF, SCAN, C-SCAN, LOOK y C-LOOK), el **modelo de 7 estados** con planificador a mediano
  plazo (suspensión en el área de intercambio), **modos administrador y usuario** y carga y guardado
  en **JSON**. Lo del Proyecto 1 (políticas de CPU, memoria y métricas) sigue funcionando.

Equipo: Victor Rivas, Nicoll Pinzon y Donato Vodola.

## Cómo correrlo en NetBeans

Requisitos: **JDK 25** y **NetBeans** con soporte para Java 25 (trae Maven). La única dependencia
externa es **JFreeChart 1.5.5** (gráficas), que Maven descarga sola.

1. *File → Open Project…* y elegir la carpeta del repositorio (NetBeans la reconoce como proyecto
   Maven por el `pom.xml`).
2. *Clean and Build* (martillo con escoba).
3. *Run* (▶). La clase principal es `com.unimet.avilaos.AvilaOS` (`exec.mainClass` del `pom.xml`).

## Cómo cargar una simulación

Al arrancar se abre el diálogo **"Antes de iniciar"**: el enunciado exige **dos JSON** antes de
iniciar el reloj. Se elige un archivo para cada fila y se pulsa **Iniciar simulación**. Cada fila se
valida con el parser real: si algo está mal, dice la línea, la columna y el motivo, y no deja
iniciar. Los avisos que no impiden la carga (una ruta que no existe, un proceso más grande que el
swap…) se muestran en amarillo y quedan en el Log. Si se cierra el diálogo, **Iniciar** lo vuelve a
abrir.

**JSON del sistema de archivos** (`diskConfig` y `fileSystem`):

```json
{ "diskConfig": { "totalBlocks": 200, "initialHead": 50 },
  "fileSystem": [
    { "path": "/sistema", "type": "DIRECTORY", "owner": "admin", "permissions": "public" },
    { "path": "/sistema/boot.bin", "type": "FILE", "startBlock": 11, "blocks": 23,
      "owner": "admin", "permissions": "public" } ] }
```

**JSON de procesos y configuración** (`globalConfig`, `computer` y `processes`):

```json
{ "globalConfig": { "cycleDurationMs": 300, "swapSizeBlocks": 100, "initialDiskPolicy": "SSTF" },
  "computer": { "ramCapacity": 1024, "policy": "FCFS", "quantum": 0 },
  "processes": [
    { "name": "Proc_95", "memory": 16, "deadline": 500, "instructions": 2,
      "targetPath": "/sistema/config.sys", "operation": "READ" } ] }
```

Claves opcionales de `globalConfig`: `initialDirection` (UP o DOWN), `positionsPerCycle` y
`waitForInitialRequests` (true o false). La política de CPU acepta `FCFS`, `Round Robin` (o `RR`),
`EDF` y `Prioridades apropiativas`. Cada proceso puede llevar `priority` y, según la operación,
`newName` (UPDATE que renombra), `blocks`, `permissions`, `type` y `owner` (CREATE).

En [`samples/`](samples/README.md) hay pares listos para cargar: los casos P1 a P4 del enunciado, el
anexo (desplazamientos 518, 208, 332 y 192), un caso de poca RAM que provoca suspensiones y uno con
error.

Con una simulación ya cargada:
- **Cargar JSON → Usar el disco actual** arma una simulación nueva con otro JSON de procesos sobre el
  disco actual, con sus cambios (reiniciar con procesos nuevos conservando el disco).
- **Archivos → Guardar JSON…** guarda el sistema de archivos con el formato del JSON de entrada.
- **Reiniciar** vuelve al ciclo 0 con los mismos dos JSON.

## Qué se ve en la interfaz

| Vista | Qué muestra |
|---|---|
| **Equipos** | CPU (proceso, modo usuario/SO, PC, MAR), memoria, las colas Nuevos, Listos, Bloqueados (con el motivo), Listos/Suspendidos, Bloqueados/Suspendidos y Terminados. |
| **Disco** | Mapa de bloques (archivos por color y procesos suspendidos en el swap), cabezal con su posición y dirección, solicitud en curso, cola de E/S en el orden en que se atendería y selector de política de disco (se cambia en caliente). |
| **Archivos** | Árbol del sistema de archivos (JTree) con nombre, tamaño, dueño, permiso y primer bloque, botones de operación y la tabla de asignación (color, archivo, bloques, primer bloque, dueño, proceso creador). |
| **Métricas** | Desplazamiento total, espera promedio en la cola, solicitudes atendidas por política, suspensiones y reactivaciones, la gráfica del desplazamiento acumulado y las métricas del Proyecto 1. |
| **Log** | Cada decisión del kernel, del disco ("Disco: atiende la solicitud de P5 en el bloque 62 (SSTF)") y del mediano plazo ("Se suspende al proceso P2 para admitir a P4"). |

**Modos.** En el riel izquierdo se elige el modo. El **administrador** puede hacer todo. El
**usuario** ve el disco, la cola, el log y las métricas, lee sus archivos o los públicos y crea
procesos de E/S sobre sus archivos. Lo que cambia el sistema (políticas, quantum, procesos de CPU,
Configuración, cargar JSON, reiniciar, archivos ajenos o del sistema) se rechaza con el motivo, sin
detener la simulación.

**Operaciones sobre archivos.** Las hace siempre un **proceso de E/S**: la interfaz solo lo crea
(**+ Proceso de E/S**), el proceso entra a Nuevos y, cuando obtiene la CPU, pide el disco. UPDATE
puede renombrar o reescribir el archivo en su lugar.

## Arquitectura

Capas, de arriba hacia abajo (cada una solo usa las de abajo):

```
gui  →  cluster  →  os  →  hardware  →  datastructures
            config · metrics · events (transversales)
```

- **`gui`**: Swing. Nadie importa `gui`. Un `javax.swing.Timer` (`SimulationController`, cada
  150 ms) lee **copias** del estado; la lógica del SO nunca vive en las ventanas.
- **`cluster`**: arma el computador (`Cluster`, `Computer`) y conecta el reloj.
- **`os`**: el kernel (`Kernel`), la memoria (`MemoryManager`), la planificación de CPU
  (`os.scheduling`), el planificador de disco (`os.disk`), el sistema de archivos (`os.fs`), los
  procesos (`os.process`) y los semáforos (`os.sync`).
- **`hardware`**: reloj global (`GlobalClock`), CPU, memoria principal, dispositivo de E/S, disco
  (`Disk`, `BlockMap`).
- **`datastructures`**: `LinkedList` y `LinkedQueue` propias (el enunciado prohíbe las colecciones
  de Java).
- **`config`**: configuración, CSV y los dos JSON (con un parser JSON propio). **`metrics`**: métricas
  del CPU y del disco. **`events`**: el log.

**Inversión de dependencias:** las interfaces se definen abajo y se implementan arriba.
`InterruptHandler` (la CPU avisa al kernel), `Interconnect` (el kernel usa al clúster) y
`SeekPlanSource` (el disco le pide la siguiente solicitud al planificador de disco).

**El reloj.** `GlobalClock` le da a cada computador su propio hilo y los sincroniza con una barrera
de semáforos: en cada ciclo, cada uno avanza exactamente un ciclo. Orden de un ciclo
(`Computer.onTick`): `io.onTick` → `disk.onTick` → `kernel.beforeCpu` (solicitudes de la GUI,
llegadas, despertares, mediano plazo, admisión, expropiación, despacho) → `cpu.onTick` →
`kernel.afterCpu` (efectos, deadlines, cola inicial del disco) → métricas.

**Concurrencia.** `java.util.concurrent.Semaphore` protege las estructuras compartidas: la cola de
E/S (`DiskScheduler`), el mapa de bloques (`BlockMap`), el sistema de archivos (`FileSystem`), el
disco, el kernel y las colas. Las políticas y el código ajeno se llaman fuera del mutex.

### Enums

| Enum | Valores | Para qué |
|---|---|---|
| `ProcessState` | Nuevo, Listo, Ejecución, Bloqueado, Listo/Suspendido, Bloqueado/Suspendido, Terminado | Modelo de 7 estados con sus transiciones válidas. |
| `IoOperation` | CREATE, READ, UPDATE, DELETE, SUSPEND, RESUME | Operación de una solicitud de disco (las dos últimas las genera el mediano plazo). |
| `DiskPolicyType` | FIFO, SSTF, SCAN, C-SCAN, LOOK, C-LOOK | Fábrica por constante de cada política de disco (sin switch). |
| `PolicyType` | FCFS, Round Robin, EDF, Prioridades apropiativas | Fábrica por constante de cada política de CPU. |
| `UserMode` | Administrador, Usuario | Modo de la sesión. |
| `Permission` | público, privado | Permiso de un archivo o directorio. |
| `ProcessType` | CPU bound, I/O bound, productor, consumidor, E/S de archivo | Tipo de proceso. |
| `BlockReason` | E/S, semáforo, red | Motivo de un bloqueo (se ve en la GUI). |
| `HeadDirection` | UP, DOWN | Dirección del cabezal. |
| `DiskPhase` | IDLE, SWEEP, JUMP, SEEK, TRANSFER | Qué hace el disco en este ciclo. |
| `CpuMode` | USER, KERNEL | Modo de la CPU. |
| `InterruptType` | IO_COMPLETION, DISK_COMPLETION | Interrupciones de fin de E/S. |
| `ExecutionStep` | instrucción de usuario, solicitud de E/S, solicitud al disco, operaciones de buffer | Próximo paso del programa de un proceso. |
| `SemaphoreType` | COUNTING, BINARY | Semáforos del productor-consumidor (Proyecto 1). |

### Interfaces

| Interfaz | La implementa | Para qué |
|---|---|---|
| `DiskSchedulingPolicy` | `FifoDiskPolicy`, `SstfDiskPolicy`, `ScanDiskPolicy`, `CScanDiskPolicy`, `LookDiskPolicy`, `CLookDiskPolicy` | Qué solicitud sigue. Agregar una política no modifica `DiskScheduler`. |
| `SchedulingPolicy` | las cuatro políticas de CPU | Orden de la cola de listos y expropiación. |
| `SeekPlanSource` | `DiskScheduler` | El disco (hardware) pide la siguiente solicitud sin depender del SO. |
| `ClockListener` | `Computer`, `Cpu`, `Disk`, `IoDevice` | Componentes que obedecen al reloj. |
| `InterruptHandler` | `Kernel` | La CPU entrega las interrupciones al kernel. |
| `Interconnect` | `Cluster` | El kernel usa al clúster (despertares, latencia). |
| `ElementComparator` | comparadores de las políticas | Orden de `LinkedList.insertSorted`. |
| `SimulationListener` | — (la GUI lee el log por copias cada 150 ms) | Observador del log y del fin de cada ciclo, disponible para quien lo necesite. |

## Decisiones del Proyecto 2

- **Disco.** De 100 a 500 bloques de archivos más el área de intercambio, que va después (200 + 100
  = 300 en el ejemplo). El cabezal recorre N posiciones por ciclo (configurable) y cada bloque leído
  o escrito cuesta 1 ciclo. Atiende una solicitud a la vez. El salto de C-SCAN y C-LOOK cuesta 1
  ciclo y no cuenta como desplazamiento (como en el anexo).
- **Asignación encadenada.** `BlockMap` guarda el dueño y el siguiente bloque de cada bloque; un
  archivo nuevo toma los primeros bloques libres (la cadena puede quedar salteada). Eliminar libera
  la cadena completa; eliminar un directorio es recursivo. Si no hay bloques suficientes, la
  operación se rechaza.
- **Cola inicial completa.** Antes de su primera decisión, el disco espera a que los procesos de la
  carga inicial pidan el disco o ya no puedan pedirlo (terminaron, murieron o fallaron). Así la
  primera decisión ve todas las solicitudes y los casos del enunciado salen en vivo igual que en la
  tabla. Se suelta antes si se llega al menor deadline de la carga, si el mediano plazo necesita el
  disco o si los que faltan solo esperan RAM. Se desactiva con `"waitForInitialRequests": false`.
- **Suspensión (mediano plazo).**
  - **Cuándo suspende:** si un Nuevo no cabe en RAM, se suspenden Bloqueados por E/S, de menor
    prioridad y, si empatan, el que lleva más tiempo bloqueado, hasta juntar la RAM que falta (si no
    alcanza, no se suspende ninguno).
  - **Cómo:** cada suspensión es una solicitud SUSPEND al disco. Se escribe en el swap y después se
    libera la RAM, que queda reservada para ese Nuevo. Un proceso suspendido ocupa en el swap tantos
    bloques como unidades de memoria tiene.
  - **Prioridad del Nuevo:** no se compara la víctima con el Nuevo. Si el único candidato tiene más
    prioridad, igual se suspende: un Bloqueado no usa la CPU y, al ocurrir su evento, es el primero
    en volver.
  - **Reactivación:** cuando hay RAM, el Listo/Suspendido de mayor prioridad (empate: el que lleva
    más tiempo suspendido) vuelve con una solicitud RESUME. Un Bloqueado/Suspendido cuyo evento
    ocurre pasa a Listo/Suspendido.
- **Precedencia de la configuración.** Los JSON mandan en todo lo que traen. Configuración (diálogo
  o `avilaos-config.csv`) solo da las claves opcionales que el JSON de procesos no trae: la dirección
  y las posiciones por ciclo. La espera de la carga inicial viene activada si el JSON no dice otra
  cosa.
- **Operaciones del sistema de archivos.** CREATE: el administrador crea en cualquier parte y el
  usuario solo archivos en sus directorios. READ: archivos propios o públicos. UPDATE: renombrar (solo
  el administrador) o reescribir en su lugar (administrador o dueño). DELETE: el administrador
  cualquier cosa, el usuario sus archivos. Cada operación cuenta como una instrucción del proceso en
  modo SO.
- **Métricas del disco.** La espera promedio incluye las solicitudes SUSPEND y RESUME, porque pasan
  por la misma cola. Las solicitudes atendidas por política cuentan la política que **eligió** cada
  solicitud, aunque después se cambie en caliente.

Las decisiones del kernel del Proyecto 1 (D1 a D8) siguen vigentes: despachar cuesta 1 ciclo de SO
sin avanzar el PC, pedir E/S cuesta 1 ciclo de SO y luego el proceso queda Bloqueado, el trabajo del
SO no avanza el PC, si termina y vence en el mismo ciclo cuenta como a tiempo, y los despertares se
aplican al ciclo siguiente.

## Desarrollo

- Ramas por funcionalidad desde `develop`, Issues para tareas y errores, y Pull Requests revisados
  hacia `develop`; `master` tiene las entregas.
- Commits pequeños en inglés con el formato `type(scope): description`.
- Comentarios, javadoc y textos de la interfaz en español; nombres de clases y métodos en inglés.
