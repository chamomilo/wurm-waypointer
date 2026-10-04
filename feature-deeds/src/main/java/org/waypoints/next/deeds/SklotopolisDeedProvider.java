package org.waypoints.next.deeds;

import org.waypoints.next.model.ServerIdentity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/** Sklotopolis adapter configured only by explicit server-identity mappings. */
public final class SklotopolisDeedProvider implements DeedProvider {
    public static final String KEY = "sklotopolis";
    private final CachingDeedProvider delegate;

    public SklotopolisDeedProvider(List<DeedProviderMapping> mappings,
                                   Path cacheDirectory, Logger logger) {
        List<DeedProviderMapping> checked = new ArrayList<DeedProviderMapping>();
        if (mappings != null) for (DeedProviderMapping mapping : mappings) {
            if (mapping.getFormat() != DeedFeedFormat.SKLOTOPOLIS_JSON) {
                throw new IllegalArgumentException(
                        "Sklotopolis mappings require SKLOTOPOLIS_JSON format");
            }
            checked.add(mapping);
        }
        delegate = new CachingDeedProvider(KEY, checked, cacheDirectory,
                false, logger);
    }

    @Override public String getProviderKey() { return KEY; }
    @Override public boolean supports(ServerIdentity server) { return delegate.supports(server); }
    @Override public DeedProviderSnapshot current(ServerIdentity server) {
        return delegate.current(server);
    }
    @Override public CompletableFuture<DeedProviderSnapshot> refresh(
            ServerIdentity server, boolean manual) {
        return delegate.refresh(server, manual);
    }
    @Override public void close() { delegate.close(); }
}
