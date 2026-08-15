package org.waypoints.next.navigation;

import org.junit.Test;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;

import static org.junit.Assert.assertEquals;

public final class SklotopolisHighwayServiceTest {
    @Test public void resolvesOnlyKnownSklotopolisServers() {
        assertEquals(
                "https://web.game.sklotopolis.com/unlimited/3/highways.json",
                SklotopolisHighwayService.sourceUrl(server(
                        "176.9.149.249", 3726, "Novus")));
        assertEquals(
                "https://web.game.sklotopolis.com/unlimited/2/highways.json",
                SklotopolisHighwayService.sourceUrl(server(
                        "176.9.149.249", 3725, "Liberty")));
        assertEquals(
                "https://web.game.sklotopolis.com/unlimited/4/highways.json",
                SklotopolisHighwayService.sourceUrl(server(
                        "176.9.149.249", 3724, "Caza")));
        assertEquals(
                "https://web.game.sklotopolis.com/unlimited/6/highways.json",
                SklotopolisHighwayService.sourceUrl(server(
                        "176.9.149.249", 3724, "Infinity")));
        assertEquals("", SklotopolisHighwayService.sourceUrl(server(
                "example.test", 3726, "Somewhere")));
    }

    @Test public void canResolveNamedClusterBehindAnotherHost() {
        assertEquals(
                "https://web.game.sklotopolis.com/unlimited/3/highways.json",
                SklotopolisHighwayService.sourceUrl(server(
                        "play.example.test", 40000,
                        "Sklotopolis Novus")));
    }

    private static ServerIdentity server(String host, int port, String name) {
        return ServerIdentity.of(ServerEndpoint.direct(host, port), name, name,
                ServerIdentity.Resolution.RESOLVED);
    }
}
