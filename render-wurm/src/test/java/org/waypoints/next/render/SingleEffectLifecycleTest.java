package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SingleEffectLifecycleTest {
    @Test
    public void reconnectDoesNotDuplicateAndTransferRemovesOldEffect() {
        FakeAdapter adapter = new FakeAdapter();
        SingleEffectLifecycle<Object, Object> lifecycle =
                new SingleEffectLifecycle<Object, Object>(adapter);
        Object firstWorld = new Object();
        Object secondWorld = new Object();

        lifecycle.attach(firstWorld);
        lifecycle.attach(firstWorld);
        assertEquals(1, adapter.created);
        assertEquals(1, adapter.added);

        lifecycle.attach(secondWorld);
        assertEquals(2, adapter.created);
        assertEquals(2, adapter.added);
        assertEquals(1, adapter.removed);

        lifecycle.detach();
        assertEquals(2, adapter.removed);
        assertFalse(lifecycle.isAttached());
    }

    @Test
    public void failedAddDeletesUnregisteredEffect() {
        FakeAdapter adapter = new FakeAdapter();
        adapter.failAdd = true;
        SingleEffectLifecycle<Object, Object> lifecycle =
                new SingleEffectLifecycle<Object, Object>(adapter);

        try {
            lifecycle.attach(new Object());
        } catch (IllegalStateException expected) {
            assertEquals(1, adapter.deleted);
            assertFalse(lifecycle.isAttached());
            return;
        }
        throw new AssertionError("attach should fail");
    }

    @Test
    public void rendererClearInvalidationForcesFreshEffectWithoutDoubleDelete() {
        FakeAdapter adapter = new FakeAdapter();
        SingleEffectLifecycle<Object, Object> lifecycle =
                new SingleEffectLifecycle<Object, Object>(adapter);
        Object world = new Object();

        lifecycle.attach(world);
        lifecycle.invalidateAfterExternalClear();
        assertFalse(lifecycle.isAttached());
        assertEquals(0, adapter.removed);
        assertEquals(0, adapter.deleted);

        lifecycle.attach(world);
        assertEquals(2, adapter.created);
        assertEquals(2, adapter.added);
        assertTrue(lifecycle.isAttached());
    }

    @Test
    public void rendererReplacementReattachesEvenWhenWorldInstanceIsUnchanged() {
        FakeAdapter adapter = new FakeAdapter();
        SingleEffectLifecycle<Object, Object> lifecycle =
                new SingleEffectLifecycle<Object, Object>(adapter);
        Object world = new Object();
        Object firstRenderer = new Object();
        Object secondRenderer = new Object();

        lifecycle.attach(world, firstRenderer);
        lifecycle.attach(world, firstRenderer);
        assertEquals(1, adapter.created);
        assertEquals(1, adapter.added);
        assertTrue(lifecycle.isAttachedTo(world, firstRenderer));

        lifecycle.attach(world, secondRenderer);
        assertEquals(2, adapter.created);
        assertEquals(2, adapter.added);
        assertEquals(1, adapter.removed);
        assertTrue(lifecycle.isAttachedTo(world, secondRenderer));
        assertFalse(lifecycle.isAttachedTo(world, firstRenderer));
    }

    @Test
    public void failedRemoveFallsBackToDelete() {
        FakeAdapter adapter = new FakeAdapter();
        SingleEffectLifecycle<Object, Object> lifecycle =
                new SingleEffectLifecycle<Object, Object>(adapter);
        lifecycle.attach(new Object());
        adapter.failRemove = true;

        try {
            lifecycle.detach();
        } catch (IllegalStateException expected) {
            assertEquals(1, adapter.deleted);
            assertFalse(lifecycle.isAttached());
            return;
        }
        throw new AssertionError("detach should fail");
    }

    private static final class FakeAdapter
            implements SingleEffectLifecycle.Adapter<Object, Object> {
        private int created;
        private int added;
        private int removed;
        private int deleted;
        private boolean failAdd;
        private boolean failRemove;

        @Override public Object create(Object world) { created++; return new Object(); }
        @Override public void add(Object world, Object effect) {
            if (failAdd) throw new IllegalStateException("add");
            added++;
        }
        @Override public void remove(Object world, Object effect) {
            if (failRemove) throw new IllegalStateException("remove");
            removed++;
        }
        @Override public void delete(Object effect) { deleted++; }
    }
}
