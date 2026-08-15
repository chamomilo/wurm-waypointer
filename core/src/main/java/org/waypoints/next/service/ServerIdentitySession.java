package org.waypoints.next.service;

import org.waypoints.next.model.CapturedServerSelection;
import org.waypoints.next.model.ServerIdentity;

/** Small synchronized lifecycle boundary for reconnect and shard transfer. */
public final class ServerIdentitySession {
    private final ServerIdentityResolver resolver;
    private CapturedServerSelection selection;
    private ServerIdentity current;

    public ServerIdentitySession(ServerIdentityResolver resolver) {
        if (resolver == null) throw new IllegalArgumentException("resolver is required");
        this.resolver = resolver;
    }

    public synchronized void capture(CapturedServerSelection value) {
        selection = value;
        current = null;
    }

    public synchronized ServerIdentity resolve(String worldServerName) {
        current = resolver.resolve(worldServerName, selection);
        return current;
    }

    public synchronized ServerIdentity current() {
        return current;
    }

    /** Reconnect to the same selected endpoint; names are rechecked after login. */
    public synchronized void reconnecting() {
        current = null;
    }

    public synchronized void transfer(String host, int gamePort) {
        selection = CapturedServerSelection.transfer(host, gamePort);
        current = null;
    }

    public synchronized void clear() {
        selection = null;
        current = null;
    }
}
