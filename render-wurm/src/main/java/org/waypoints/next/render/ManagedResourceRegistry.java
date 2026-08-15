package org.waypoints.next.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Centralized ownership for a bounded set of keyed renderer resources. */
public final class ManagedResourceRegistry<K, S, R> {
    public interface Adapter<K, S, R> {
        K key(S source);
        boolean sameResource(S previous, S next);
        R create(S source);
        void add(R resource);
        void remove(R resource);
        void delete(R resource);
    }

    public interface ResourceCondition<R> {
        boolean matches(R resource);
    }

    private final Adapter<K, S, R> adapter;
    private final Map<K, Owned<S, R>> owned = new LinkedHashMap<K, Owned<S, R>>();

    public ManagedResourceRegistry(Adapter<K, S, R> adapter) {
        if (adapter == null) throw new IllegalArgumentException("adapter is required");
        this.adapter = adapter;
    }

    public synchronized void reconcile(List<S> desired) {
        if (desired == null) throw new IllegalArgumentException("desired resources are required");
        Set<K> desiredKeys = new HashSet<K>();
        for (S source : desired) {
            K key = adapter.key(source);
            if (key == null || !desiredKeys.add(key)) {
                throw new IllegalArgumentException("resource keys must be unique and non-null");
            }
        }
        for (K key : new ArrayList<K>(owned.keySet())) {
            if (!desiredKeys.contains(key)) removeOwned(key);
        }
        for (S source : desired) {
            K key = adapter.key(source);
            Owned<S, R> current = owned.get(key);
            if (current != null && adapter.sameResource(current.source, source)) continue;
            if (current != null) removeOwned(key);
            R resource = adapter.create(source);
            try {
                adapter.add(resource);
                owned.put(key, new Owned<S, R>(source, resource));
            } catch (RuntimeException failure) {
                safeDelete(resource, failure);
                throw failure;
            }
        }
    }

    public synchronized void clear() {
        RuntimeException first = null;
        for (K key : new ArrayList<K>(owned.keySet())) {
            try { removeOwned(key); }
            catch (RuntimeException failure) {
                if (first == null) first = failure;
                else first.addSuppressed(failure);
            }
        }
        if (first != null) throw first;
    }

    /** The owner already deleted every resource; forget without double cleanup. */
    public synchronized void invalidateAfterExternalClear() {
        owned.clear();
    }

    public synchronized int size() {
        return owned.size();
    }

    public synchronized boolean anyResourceMatches(ResourceCondition<R> condition) {
        if (condition == null) throw new IllegalArgumentException(
                "resource condition is required");
        for (Owned<S, R> value : owned.values()) {
            if (condition.matches(value.resource)) return true;
        }
        return false;
    }

    private void removeOwned(K key) {
        Owned<S, R> value = owned.remove(key);
        if (value == null) return;
        try {
            adapter.remove(value.resource);
        } catch (RuntimeException failure) {
            safeDelete(value.resource, failure);
            throw failure;
        }
    }

    private void safeDelete(R resource, RuntimeException original) {
        try { adapter.delete(resource); }
        catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    private static final class Owned<S, R> {
        private final S source;
        private final R resource;

        private Owned(S source, R resource) {
            this.source = source;
            this.resource = resource;
        }
    }
}
