package org.waypoints.next.service;

import org.junit.Test;
import org.waypoints.next.model.CapturedServerSelection;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServerIdentityResolverTest {
    private final ServerIdentityResolver resolver = new ServerIdentityResolver();

    @Test
    public void resolvesFullSklotopolisNameAgainstWorldSuffix() {
        CapturedServerSelection selection = CapturedServerSelection.steamBrowser(
                "Sklotopolis - Novus", "203.0.113.4:3724", (short) 3724, (short) 27016);

        ServerIdentity identity = resolver.resolve("Novus", selection);

        assertTrue(identity.isResolved());
        assertEquals("Sklotopolis - Novus", identity.getFullName());
        assertEquals("Novus", identity.getShortName());
        assertEquals("203.0.113.4:3724", identity.getEndpointFingerprint());
    }

    @Test
    public void unsignedClientPortsArePreserved() {
        short gamePort = (short) 40000;
        short queryPort = (short) 50000;

        ServerEndpoint endpoint = ServerEndpoint.fromClient(
                "EXAMPLE.test:40000", gamePort, queryPort);

        assertEquals(40000, endpoint.getGamePort());
        assertEquals(Integer.valueOf(50000), endpoint.getQueryPort());
        assertEquals("example.test:40000", endpoint.fingerprint());
    }

    @Test
    public void equalShortSuffixNeverMergesDifferentEndpoints() {
        ServerIdentity first = resolver.resolve("Novus",
                CapturedServerSelection.steamBrowser(
                        "Cluster A - Novus", "192.0.2.10:3724", (short) 3724, (short) 1));
        ServerIdentity second = resolver.resolve("Novus",
                CapturedServerSelection.steamBrowser(
                        "Cluster B - Novus", "192.0.2.11:3724", (short) 3724, (short) 1));

        assertTrue(first.isResolved());
        assertTrue(second.isResolved());
        assertFalse(first.sameServer(second));
    }

    @Test
    public void directConnectRemainsExplicitlyUnresolved() {
        ServerIdentity identity = resolver.resolve("Novus",
                CapturedServerSelection.direct("192.0.2.25", 3724));

        assertEquals(ServerIdentity.Resolution.UNRESOLVED_DIRECT_CONNECT,
                identity.getResolution());
        assertEquals("192.0.2.25:3724", identity.getEndpointFingerprint());
        assertFalse(identity.isSafeForAutomaticRendering());
    }

    @Test
    public void transferEndpointResolvesOnlyAfterFreshWorldName() {
        CapturedServerSelection transfer = CapturedServerSelection.transfer(
                "198.51.100.9", 3725);

        ServerIdentity waiting = resolver.resolve("", transfer);
        ServerIdentity resolved = resolver.resolve("Liberty", transfer);

        assertEquals(ServerIdentity.Resolution.UNRESOLVED_WORLD_NAME,
                waiting.getResolution());
        assertFalse(waiting.isSafeForAutomaticRendering());
        assertTrue(resolved.isSafeForAutomaticRendering());
        assertEquals("Liberty", resolved.getShortName());
        assertEquals("198.51.100.9:3725", resolved.getEndpointFingerprint());
    }

    @Test
    public void missingSelectionDoesNotInventAnEndpointFromAName() {
        ServerIdentity identity = resolver.resolve("Novus", null);

        assertEquals(ServerIdentity.Resolution.UNRESOLVED_NO_ENDPOINT,
                identity.getResolution());
        assertNull(identity.getEndpoint());
        assertFalse(identity.isSafeForAutomaticRendering());
    }

    @Test
    public void nameMismatchIsNotAcceptedOptimistically() {
        ServerIdentity identity = resolver.resolve("Liberty",
                CapturedServerSelection.steamBrowser(
                        "Sklotopolis - Novus", "203.0.113.4:3724",
                        (short) 3724, (short) 27016));

        assertEquals(ServerIdentity.Resolution.UNRESOLVED_NAME_MISMATCH,
                identity.getResolution());
        assertFalse(identity.isSafeForAutomaticRendering());
    }

    @Test
    public void renameAliasDoesNotChangeEndpointIdentity() {
        ServerIdentity original = resolver.resolve("Novus",
                CapturedServerSelection.steamBrowser(
                        "Sklotopolis - Novus", "203.0.113.4:3724",
                        (short) 3724, (short) 27016));

        ServerIdentity renamed = original.withAlias("Sklotopolis - New Novus");

        assertTrue(original.sameServer(renamed));
        assertEquals(1, renamed.getAliases().size());
    }
}
