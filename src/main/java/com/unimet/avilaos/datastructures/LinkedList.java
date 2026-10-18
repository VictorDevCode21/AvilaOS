package com.unimet.avilaos.datastructures;

/**
 * Lista enlazada simple con referencia al primero (head) y al último (tail).
 * Sustituye a java.util.LinkedList, prohibida en el proyecto.
 */
public class LinkedList<T> {

    private Node<T> head;
    private Node<T> tail;
    private int size;

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

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

    /** Saca y devuelve el primero; null si está vacía. */
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

    /** Devuelve el primero sin sacarlo; null si está vacía. */
    public T peekFirst() {
        return isEmpty() ? null : head.getData();
    }
}