package com.unimet.avilaos.datastructures;

/**
 * Cola FIFO (primero en entrar, primero en salir).
 * Usa una LinkedList por composición y solo expone operaciones de cola,
 * para que nadie pueda reordenarla. Pensada para colas de semáforos
 * (semáforo fuerte, Stallings cap. 5) y la cola de nuevos.
 *
 * @author victor1102
 */
public class LinkedQueue<T> {

    private final LinkedList<T> items = new LinkedList<>();

    /** Encola al final. O(1). */
    public void enqueue(T data) {
        items.addLast(data);
    }

    /** Desencola y devuelve el primero; null si está vacía. O(1). */
    public T dequeue() {
        return items.removeFirst();
    }

    /** Devuelve el primero sin sacarlo; null si está vacía. */
    public T peek() {
        return items.peekFirst();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    /**
     * Saca el MISMO objeto de cualquier posición.
     * Caso real: un proceso bloqueado en un semáforo al que se le vence el deadline.
     */
    public boolean remove(T data) {
        return items.remove(data);
    }

    public boolean contains(T data) {
        return items.contains(data);
    }

    /** Elemento en la posición index (0 = el próximo en salir). Para que la GUI la recorra. */
    public T get(int index) {
        return items.get(index);
    }

    public void clear() {
        items.clear();
    }

    /** Copia con los mismos objetos y el mismo orden. */
    public LinkedQueue<T> copy() {
        LinkedQueue<T> result = new LinkedQueue<>();
        for (int i = 0; i < items.size(); i++) {
            result.enqueue(items.get(i));
        }
        return result;
    }

    @Override
    public String toString() {
        return items.toString();
    }
}