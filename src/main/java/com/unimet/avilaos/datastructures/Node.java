package com.unimet.avilaos.datastructures;

/**
 * Nodo de una lista enlazada simple: guarda un dato y la referencia al siguiente.
 */
class Node<T> {

    private final T data;
    private Node<T> next;

    Node(T data) {
        this.data = data;
        this.next = null;
    }

    T getData() {
        return data;
    }

    Node<T> getNext() {
        return next;
    }

    void setNext(Node<T> next) {
        this.next = next;
    }
}