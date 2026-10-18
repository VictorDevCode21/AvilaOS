package com.unimet.avilaos.datastructures;

/**
 * Criterio para ordenar elementos de una LinkedList.
 * Propio del proyecto para no depender de java.util.Comparator.
 *
 * @author victor1102
 */
@FunctionalInterface
public interface ElementComparator<T> {

    /**
     * @return negativo si a va antes que b, 0 si empatan, positivo si a va después.
     */
    int compare(T a, T b);
}