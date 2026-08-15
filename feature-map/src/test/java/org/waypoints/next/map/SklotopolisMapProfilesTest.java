package org.waypoints.next.map;

import org.junit.Test;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class SklotopolisMapProfilesTest {
    @Test public void resolvesAllCurrentNamedWorldsIncludingCaza() {
        assertEquals(SklotopolisMapProfiles.LIBERTY,
                resolve("Liberty", 3725));
        assertEquals(SklotopolisMapProfiles.NOVUS,
                resolve("Novus", 3726));
        assertEquals(SklotopolisMapProfiles.CAZA,
                resolve("Caza", 3724));
        assertEquals(SklotopolisMapProfiles.INFINITY,
                resolve("Infinity", 3724));
        assertEquals(SklotopolisMapProfiles.OLD_INFINITY,
                resolve("Old Infinity", 3724));
    }

    @Test public void locksKnownPublishedBoundsAndBackendIds() {
        assertEquals(4, SklotopolisMapProfiles.CAZA.getBackendId());
        assertEquals(2048, SklotopolisMapProfiles.CAZA.getMapWidth());
        assertEquals(2048, SklotopolisMapProfiles.CAZA.getMapHeight());
        assertEquals("https://web.game.sklotopolis.com/unlimited/4/mapdump-flat.png",
                SklotopolisMapProfiles.CAZA.getSurfaceUrl());
        assertEquals(4096, SklotopolisMapProfiles.NOVUS.getMapWidth());
        assertEquals(2048, SklotopolisMapProfiles.INFINITY.getMapWidth());
    }

    @Test public void neverGuessesCazaFromSharedLoginPort() {
        assertNull(resolve("", 3724));
        assertNull(SklotopolisMapProfiles.resolve(ServerIdentity.of(
                ServerEndpoint.direct("example.test", 3724),
                "Caza", "Caza", ServerIdentity.Resolution.RESOLVED)));
    }

    private static ServerMapProfile resolve(String name, int port) {
        return SklotopolisMapProfiles.resolve(ServerIdentity.of(
                ServerEndpoint.direct("176.9.149.249", port), name, name,
                ServerIdentity.Resolution.RESOLVED));
    }
}
