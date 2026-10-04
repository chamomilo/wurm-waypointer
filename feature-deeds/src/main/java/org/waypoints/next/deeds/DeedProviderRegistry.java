package org.waypoints.next.deeds;

import org.waypoints.next.model.ServerIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Selects a configured provider; unknown servers intentionally get No provider. */
public final class DeedProviderRegistry implements AutoCloseable {
    private final List<DeedProvider> providers;
    private final NoDeedProvider none = new NoDeedProvider();

    public DeedProviderRegistry(List<DeedProvider> providers) {
        List<DeedProvider> copy = new ArrayList<DeedProvider>();
        if (providers != null) for (DeedProvider provider : providers) {
            if (provider != null && !(provider instanceof NoDeedProvider)) copy.add(provider);
        }
        this.providers = Collections.unmodifiableList(copy);
    }

    public DeedProvider provider(ServerIdentity server) {
        for (DeedProvider provider : providers) {
            if (provider.supports(server)) return provider;
        }
        return none;
    }

    public DeedProviderSnapshot current(ServerIdentity server) {
        return provider(server).current(server);
    }

    public CompletableFuture<DeedProviderSnapshot> refresh(
            ServerIdentity server, boolean manual) {
        return provider(server).refresh(server, manual);
    }

    @Override public void close() {
        for (DeedProvider provider : providers) provider.close();
    }
}
