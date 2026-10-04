package org.waypoints.next.deeds;

import org.waypoints.next.model.ServerIdentity;

import java.util.concurrent.CompletableFuture;

/** Asynchronous server-scoped catalog SPI. Implementations must be fail-open. */
public interface DeedProvider extends AutoCloseable {
    String getProviderKey();
    boolean supports(ServerIdentity server);
    DeedProviderSnapshot current(ServerIdentity server);
    CompletableFuture<DeedProviderSnapshot> refresh(ServerIdentity server,
                                                     boolean manual);
    @Override void close();
}
