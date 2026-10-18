package com.unimet.avilaos.datastructures;

/**
 * Lista enlazada simple con referencia al primero (head) y al último (tail).
 * Sustituye a java.util.LinkedList, prohibida en el proyecto.
 *
 * No es segura para hilos por sí sola: quien la comparta entre hilos
 * (el Kernel, la GUI) debe protegerla con un semáforo.
 *
 * @author victor1102
 */
public class LinkedList<T> {

    private Node<T> head;
    private Node<T> tail;
    private int size;

    // ---------- Consulta ----------

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    /** Devuelve el primero sin sacarlo; null si está vacía. */
    public T peekFirst() {
        return isEmpty() ? null : head.getData();
    }

    /** Devuelve el último sin sacarlo; null si está vacía. */
    public T peekLast() {
        return isEmpty() ? null : tail.getData();
    }

    /** Devuelve el elemento en la posición index (0 = primero). O(n). */
    public T get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Índice " + index + " fuera de rango (size=" + size + ")");
        }
        Node<T> current = head;
        for (int i = 0; i < index; i++) {
            current = current.getNext();
        }
        return current.getData();
    }

    /** true si el MISMO objeto está en la lista (compara referencias con ==). */
    public boolean contains(T data) {
        Node<T> current = head;
        while (current != null) {
            if (current.getData() == data) {
                return true;
            }
            current = current.getNext();
        }
        return false;
    }

    // ---------- Inserción ----------

    /** Inserta al final. O(1) gracias a tail. */
    public void addLast(T data) {
        Node<T> node = new Node<>(data);
        if (isEmpty()) {
            head = node;
            tail = node;
        } else {
            tail.setNext(node);
            tail = node;
        }
        size++;
    }

    /** Inserta al inicio. O(1). */
    public void addFirst(T data) {
        Node<T> node = new Node<>(data);
        if (isEmpty()) {
            head = node;
            tail = node;
        } else {
            node.setNext(head);
            head = node;
        }
        size++;
    }

    /**
     * Inserta respetando el orden dado por el comparador.
     * Si hay empate, el nuevo queda DESPUÉS de los iguales (respeta el orden de llegada).
     */
    public void insertSorted(T data, ElementComparator<T> comparator) {
        if (isEmpty() || comparator.compare(data, tail.getData()) >= 0) {
            addLast(data);
            return;
        }
        if (comparator.compare(data, head.getData()) < 0) {
            addFirst(data);
            return;
        }
        // Aquí: head <= data < tail, así que el recorrido siempre encuentra su lugar.
        Node<T> previous = head;
        while (comparator.compare(data, previous.getNext().getData()) >= 0) {
            previous = previous.getNext();
        }
        Node<T> node = new Node<>(data);
        node.setNext(previous.getNext());
        previous.setNext(node);
        size++;
    }

    // ---------- Extracción ----------

    /** Saca y devuelve el primero; null si está vacía. O(1). */
    public T removeFirst() {
        if (isEmpty()) {
            return null;
        }
        T data = head.getData();
        head = head.getNext();
        size--;
        if (head == null) {      // quedó vacía: tail también debe apuntar a null
            tail = null;
        }
        return data;
    }

    /** Saca el MISMO objeto de cualquier posición. true si lo encontró. O(n). */
    public boolean remove(T data) {
        Node<T> previous = null;
        Node<T> current = head;
        while (current != null) {
            if (current.getData() == data) {
                if (previous == null) {          // era el primero
                    head = current.getNext();
                } else {                         // estaba en medio o al final
                    previous.setNext(current.getNext());
                }
                if (current == tail) {           // era el último
                    tail = previous;
                }
                size--;
                return true;
            }
            previous = current;
            current = current.getNext();
        }
        return false;
    }

    /** Vacía la lista. */
    public void clear() {
        head = null;
        tail = null;
        size = 0;
    }

    // ---------- Orden y utilidades ----------

    /** Reordena toda la lista con el comparador (ordenamiento por inserción, estable). */
    public void sort(ElementComparator<T> comparator) {
        Node<T> current = head;
        clear();
        while (current != null) {
            Node<T> next = current.getNext();
            insertSorted(current.getData(), comparator);
            current = next;
        }
    }

    /** Copia superficial: lista nueva con los mismos objetos. Útil para que la GUI lea sin tocar la original. */
    public LinkedList<T> copy() {
        LinkedList<T> result = new LinkedList<>();
        Node<T> current = head;
        while (current != null) {
            result.addLast(current.getData());
            current = current.getNext();
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        Node<T> current = head;
        while (current != null) {
            sb.append(current.getData());
            if (current.getNext() != null) {
                sb.append(", ");
            }
            current = current.getNext();
        }
        return sb.append("]").toString();
    }
}