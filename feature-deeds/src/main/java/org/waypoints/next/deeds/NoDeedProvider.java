package org.waypoints.next.deeds;

import org.waypoints.next.model.ServerIdentity;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/** Explicit, harmless state for worlds without a configured deed source. */
public final class NoDeedProvider implements DeedProvider {
    private final DeedProviderSnapshot snapshot =
            DeedProviderSnapshot.noProvider(Instant.now());
    @Override public String getProviderKey() { return "none"; }
    @Override public boolean supports(ServerIdentity server) { return true; }
    @Override public DeedProviderSnapshot current(ServerIdentity server) {
        return snapshot;
    }
    @Override public CompletableFuture<DeedProviderSnapshot> refresh(
            ServerIdentity server, boolean manual) {
        return CompletableFuture.completedFuture(current(server));
    }
    @Override public void close() { }
}
