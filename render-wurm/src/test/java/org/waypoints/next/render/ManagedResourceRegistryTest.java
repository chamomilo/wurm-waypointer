package org.waypoints.next.render;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ManagedResourceRegistryTest {
    @Test public void reconcileUpdateClearAndExternalClearOwnExactlyOnce() {
        CountingAdapter adapter = new CountingAdapter();
        ManagedResourceRegistry<String, Source, Resource> registry =
                new ManagedResourceRegistry<String, Source, Resource>(adapter);
        registry.reconcile(Arrays.asList(new Source("a", 1), new Source("b", 1)));
        registry.reconcile(Arrays.asList(new Source("a", 1), new Source("b", 1)));
        assertEquals(2, adapter.created);
        assertEquals(2, registry.size());

        registry.reconcile(Arrays.asList(new Source("a", 2), new Source("c", 1)));
        assertEquals(4, adapter.created);
        assertEquals(2, adapter.removed);
        assertEquals(0, adapter.deletedFallback);

        registry.clear();
        assertEquals(4, adapter.removed);
        registry.reconcile(Collections.singletonList(new Source("x", 1)));
        assertTrue(registry.anyResourceMatches(
                new ManagedResourceRegistry.ResourceCondition<Resource>() {
                    @Override public boolean matches(Resource resource) {
                        return "x".equals(resource.source.key);
                    }
                }));
        assertFalse(registry.anyResourceMatches(
                new ManagedResourceRegistry.ResourceCondition<Resource>() {
                    @Override public boolean matches(Resource resource) {
                        return "missing".equals(resource.source.key);
                    }
                }));
        registry.invalidateAfterExternalClear();
        assertEquals(0, registry.size());
        assertEquals(4, adapter.removed);
        assertEquals(0, adapter.deletedFallback);
    }

    @Test public void changedSourceCanRetargetAnOwnedResourceInPlace() {
        CountingAdapter adapter = new CountingAdapter();
        adapter.updateInPlace = true;
        ManagedResourceRegistry<String, Source, Resource> registry =
                new ManagedResourceRegistry<String, Source, Resource>(adapter);

        registry.reconcile(Collections.singletonList(new Source("moving", 1)));
        registry.reconcile(Collections.singletonList(new Source("moving", 2)));

        assertEquals(1, adapter.created);
        assertEquals(1, adapter.updated);
        assertEquals(0, adapter.removed);
        assertTrue(registry.anyResourceMatches(
                new ManagedResourceRegistry.ResourceCondition<Resource>() {
                    @Override public boolean matches(Resource resource) {
                        return resource.source.version == 2;
                    }
                }));
    }

    private static final class Source {
        private final String key;
        private final int version;
        private Source(String key, int version) { this.key = key; this.version = version; }
    }

    private static final class Resource {
        private Source source;
        private Resource(Source source) { this.source = source; }
    }

    private static final class CountingAdapter implements
            ManagedResourceRegistry.Adapter<String, Source, Resource> {
        private int created;
        private int updated;
        private int removed;
        private int deletedFallback;
        private boolean updateInPlace;
        @Override public String key(Source source) { return source.key; }
        @Override public boolean sameResource(Source previous, Source next) {
            return previous.version == next.version;
        }
        @Override public boolean updateResource(Resource resource,
                                                Source previous, Source next) {
            if (!updateInPlace) return false;
            resource.source = next;
            updated++;
            return true;
        }
        @Override public Resource create(Source source) {
            created++;
            return new Resource(source);
        }
        @Override public void add(Resource resource) { }
        @Override public void remove(Resource resource) { removed++; }
        @Override public void delete(Resource resource) { deletedFallback++; }
    }
}
