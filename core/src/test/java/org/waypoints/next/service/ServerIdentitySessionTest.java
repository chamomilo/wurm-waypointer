package org.waypoints.next.service;

import org.junit.Test;
import org.waypoints.next.model.CapturedServerSelection;
import org.waypoints.next.model.ServerIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServerIdentitySessionTest {
    @Test
    public void reconnectRechecksWorldNameWithoutLosingSelectedEndpoint() {
        ServerIdentitySession session = new ServerIdentitySession(new ServerIdentityResolver());
        session.capture(CapturedServerSelection.steamBrowser(
                "Sklotopolis - Novus", "198.51.100.8:3724",
                (short) 3724, (short) 27016));
        assertTrue(session.resolve("Novus").isResolved());

        session.reconnecting();

        assertNull(session.current());
        assertTrue(session.resolve("Novus").isResolved());
    }

    @Test
    public void shardTransferResolvesFreshWorldNameAgainstNewEndpoint() {
        ServerIdentitySession session = new ServerIdentitySession(new ServerIdentityResolver());
        session.capture(CapturedServerSelection.steamBrowser(
                "Cluster - Novus", "198.51.100.8:3724",
                (short) 3724, (short) 27016));
        ServerIdentity before = session.resolve("Novus");

        session.transfer("198.51.100.9", 3725);
        ServerIdentity after = session.resolve("Liberty");

        assertEquals("198.51.100.9:3725", after.getEndpointFingerprint());
        assertEquals(ServerIdentity.Resolution.RESOLVED, after.getResolution());
        assertTrue(!before.sameServer(after));
    }
}
