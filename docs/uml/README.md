# Diagramas UML del Proyecto 2

| Diagrama | Fuente | Imagen |
|---|---|---|
| Clases: disco, sistema de archivos, planificador de disco y planificador a mediano plazo | `clases-proyecto2.puml` | `clases-proyecto2.png` |
| Secuencia: un proceso de E/S desde que pide el disco hasta que vuelve a Listo, con una suspensión | `secuencia-proceso-es.puml` | `secuencia-proceso-es.png` |

Los diagramas muestran solo los miembros importantes; los nombres de clases y métodos son los del
código (`src/main/java`).

## Cómo regenerar las imágenes

Con [PlantUML](https://plantuml.com) (1.2024.7, el jar de Maven Central). No hace falta Graphviz: el
diagrama de clases usa el motor de distribución interno de PlantUML (`!pragma layout smetana`).

```
java -jar plantuml.jar -charset UTF-8 -tpng docs/uml/*.puml
```
