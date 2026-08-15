package org.waypoints.next.render;

/** Owns at most one effect and provides delete fallback when removal fails. */
public final class SingleEffectLifecycle<W, E> {
    public interface Adapter<W, E> {
        E create(W world);
        void add(W world, E effect);
        void remove(W world, E effect);
        void delete(E effect);
    }

    private final Adapter<W, E> adapter;
    private W world;
    private Object attachmentOwner;
    private E effect;

    public SingleEffectLifecycle(Adapter<W, E> adapter) {
        if (adapter == null) throw new IllegalArgumentException("adapter is required");
        this.adapter = adapter;
    }

    public synchronized void attach(W nextWorld) {
        attach(nextWorld, nextWorld);
    }

    /**
     * Attaches to a render owner whose identity may change while the Wurm
     * {@code World} instance stays the same during a server transfer.
     */
    public synchronized void attach(W nextWorld, Object nextAttachmentOwner) {
        if (nextWorld == null) throw new IllegalArgumentException("world is required");
        if (nextAttachmentOwner == null) {
            throw new IllegalArgumentException("attachment owner is required");
        }
        if (world == nextWorld && attachmentOwner == nextAttachmentOwner
                && effect != null) return;
        detach();
        E created = adapter.create(nextWorld);
        try {
            adapter.add(nextWorld, created);
            world = nextWorld;
            attachmentOwner = nextAttachmentOwner;
            effect = created;
        } catch (RuntimeException failure) {
            safeDelete(created, failure);
            throw failure;
        }
    }

    public synchronized void detach() {
        W oldWorld = world;
        E oldEffect = effect;
        world = null;
        attachmentOwner = null;
        effect = null;
        if (oldEffect == null) return;
        try {
            adapter.remove(oldWorld, oldEffect);
        } catch (RuntimeException failure) {
            safeDelete(oldEffect, failure);
            throw failure;
        }
    }

    public synchronized boolean isAttached() {
        return effect != null;
    }

    public synchronized boolean isAttachedTo(W expectedWorld, Object expectedOwner) {
        return effect != null && world == expectedWorld
                && attachmentOwner == expectedOwner;
    }

    public synchronized E attachedEffect() {
        return effect;
    }

    /**
     * For renderer-wide clear(): Wurm already deleted the effect, so forget
     * ownership without calling remove/delete again. The next attach creates
     * a fresh effect instead of mistaking a deleted object for a live one.
     */
    public synchronized void invalidateAfterExternalClear() {
        world = null;
        attachmentOwner = null;
        effect = null;
    }

    private void safeDelete(E value, RuntimeException original) {
        try {
            adapter.delete(value);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }
}
