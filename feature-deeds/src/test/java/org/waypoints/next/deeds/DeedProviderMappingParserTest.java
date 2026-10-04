package org.waypoints.next.deeds;

import org.junit.Test;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DeedProviderMappingParserTest {
    @Test public void parsesJsonCsvAndExactWorldMappings() {
        List<DeedProviderMapping> mappings = DeedProviderMappingParser.parse(
                "one.test:3724|JSON|https://one.test/deeds.json|2048|1024;"
                        + "shared.test:3724|Red|CSV|file:///C:/feeds/deeds.csv|4096|4096",
                100, 100);
        assertEquals(2, mappings.size());
        assertEquals(GenericJsonDeedProvider.KEY, mappings.get(0).getProviderKey());
        assertEquals(2048, mappings.get(0).getMapWidth());
        assertEquals(GenericCsvDeedProvider.KEY, mappings.get(1).getProviderKey());
        ServerIdentity red = identity("shared.test", "Red");
        ServerIdentity blue = identity("shared.test", "Blue");
        assertTrue(mappings.get(1).getServer().matches(red));
        assertFalse(mappings.get(1).getServer().matches(blue));
    }

    @Test public void unknownServerUsesExplicitNoProvider() {
        DeedProviderRegistry registry = new DeedProviderRegistry(null);
        try {
            assertEquals(DeedProviderStatus.NO_PROVIDER,
                    registry.current(identity("none.test", "None")).getStatus());
        } finally { registry.close(); }
    }

    private static ServerIdentity identity(String host, String name) {
        return ServerIdentity.of(ServerEndpoint.direct(host, 3724), name, name,
                ServerIdentity.Resolution.RESOLVED);
    }
}
