package org.waypoints.next.deeds;

import org.waypoints.next.model.ServerIdentity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/** Configurable plain-JSON HTTPS or local-file provider for other servers. */
public final class GenericJsonDeedProvider implements DeedProvider {
    public static final String KEY = "generic-json";
    private final CachingDeedProvider delegate;

    public GenericJsonDeedProvider(List<DeedProviderMapping> mappings,
                                   Path cacheDirectory, Logger logger) {
        delegate = new CachingDeedProvider(KEY,
                checked(mappings, DeedFeedFormat.JSON), cacheDirectory, true, logger);
    }

    private static List<DeedProviderMapping> checked(
            List<DeedProviderMapping> mappings, DeedFeedFormat expected) {
        List<DeedProviderMapping> result = new ArrayList<DeedProviderMapping>();
        if (mappings != null) for (DeedProviderMapping mapping : mappings) {
            if (mapping.getFormat() != expected) throw new IllegalArgumentException(
                    "generic JSON mappings require JSON format");
            result.add(mapping);
        }
        return result;
    }

    @Override public String getProviderKey() { return KEY; }
    @Override public boolean supports(ServerIdentity server) { return delegate.supports(server); }
    @Override public DeedProviderSnapshot current(ServerIdentity server) { return delegate.current(server); }
    @Override public CompletableFuture<DeedProviderSnapshot> refresh(
            ServerIdentity server, boolean manual) { return delegate.refresh(server, manual); }
    @Override public void close() { delegate.close(); }
}
