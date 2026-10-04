package org.waypoints.next.deeds;

import org.waypoints.next.model.ServerIdentity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/** Configurable CSV HTTPS or local-file provider for other servers. */
public final class GenericCsvDeedProvider implements DeedProvider {
    public static final String KEY = "generic-csv";
    private final CachingDeedProvider delegate;

    public GenericCsvDeedProvider(List<DeedProviderMapping> mappings,
                                  Path cacheDirectory, Logger logger) {
        List<DeedProviderMapping> checked = new ArrayList<DeedProviderMapping>();
        if (mappings != null) for (DeedProviderMapping mapping : mappings) {
            if (mapping.getFormat() != DeedFeedFormat.CSV) {
                throw new IllegalArgumentException(
                        "generic CSV mappings require CSV format");
            }
            checked.add(mapping);
        }
        delegate = new CachingDeedProvider(KEY, checked, cacheDirectory, true, logger);
    }

    @Override public String getProviderKey() { return KEY; }
    @Override public boolean supports(ServerIdentity server) { return delegate.supports(server); }
    @Override public DeedProviderSnapshot current(ServerIdentity server) { return delegate.current(server); }
    @Override public CompletableFuture<DeedProviderSnapshot> refresh(
            ServerIdentity server, boolean manual) { return delegate.refresh(server, manual); }
    @Override public void close() { delegate.close(); }
}
