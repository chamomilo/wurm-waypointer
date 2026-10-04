package org.waypoints.next.deeds;

import org.waypoints.next.model.ServerIdentity;

import java.time.Instant;

/** Server-aware search result used by a UI without depending on Wurm classes. */
public final class DeedPickerEntry {
    private final ServerIdentity server;
    private final String providerKey;
    private final DeedRecord deed;
    private final Instant dataTimestamp;
    private final int score;

    DeedPickerEntry(ServerIdentity server, String providerKey, DeedRecord deed,
                    Instant dataTimestamp, int score) {
        this.server = server;
        this.providerKey = providerKey;
        this.deed = deed;
        this.dataTimestamp = dataTimestamp;
        this.score = score;
    }

    public ServerIdentity getServer() { return server; }
    public String getProviderKey() { return providerKey; }
    public DeedRecord getDeed() { return deed; }
    public Instant getDataTimestamp() { return dataTimestamp; }
    public int getScore() { return score; }
}
