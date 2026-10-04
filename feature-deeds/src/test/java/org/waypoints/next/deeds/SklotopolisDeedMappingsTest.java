package org.waypoints.next.deeds;

import org.junit.Test;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class SklotopolisDeedMappingsTest {
    @Test public void sharedGamePortIsDisambiguatedByExactWorldIdentity() {
        List<DeedProviderMapping> mappings = SklotopolisDeedMappings.defaults();
        assertEquals("https://web.game.sklotopolis.com/unlimited/4/deeds.json",
                source(mappings, identity("Caza")));
        assertEquals("https://web.game.sklotopolis.com/unlimited/5/deeds.json",
                source(mappings, identity("Infinity")));
        assertEquals("https://web.game.sklotopolis.com/unlimited/6/deeds.json",
                source(mappings, identity("Old Infinity")));
    }

    private static String source(List<DeedProviderMapping> mappings,
                                 ServerIdentity server) {
        for (DeedProviderMapping mapping : mappings) {
            if (mapping.getServer().matches(server)) return mapping.getSource().toString();
        }
        return "";
    }

    private static ServerIdentity identity(String name) {
        return ServerIdentity.of(ServerEndpoint.direct("176.9.149.249", 3724),
                name, name, ServerIdentity.Resolution.RESOLVED);
    }
}
