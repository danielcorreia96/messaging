package org.fenixedu.messaging.core.ui;

import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Sorter {
    private Sorter(){
        // Utility classes should have private constructors to prevent instantiation
    }

    public static <T> SortedSet<T> uniqueSort(final Collection<T> collection) {
        return collection == null ? new TreeSet<>() : new TreeSet<>(collection);
    }

    public static <K, T> SortedMap<K, T> mapSort(final Map<K, T> map) {
        return map == null ? new TreeMap<>() : new TreeMap<>(map);
    }
}
