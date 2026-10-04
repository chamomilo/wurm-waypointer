package org.waypoints.next.deeds;

import org.junit.Test;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class DeedPickerTest {
    @Test public void fuzzySearchUsesMetadataAndServerFilter() {
        ServerIdentity red = server("red.test", "Red");
        ServerIdentity blue = server("blue.test", "Blue");
        DeedRecord haven = deed("h", "Northern Haven", "Alice", "Wolves");
        DeedRecord caza = deed("c", "Caza", "Bob", "Stars");
        DeedPicker picker = new DeedPicker()
                .add(red, snapshot(haven)).add(blue, snapshot(caza));

        assertEquals("Northern Haven", picker.search("nrth hvn", "", 10)
                .get(0).getDeed().getName());
        assertEquals("Northern Haven", picker.search("wolves",
                "red.test:3724", 10).get(0).getDeed().getName());
        assertEquals(0, picker.search("wolves", "blue.test:3724", 10).size());
    }

    private static DeedProviderSnapshot snapshot(DeedRecord... deeds) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new DeedProviderSnapshot("test", DeedProviderStatus.READY,
                Arrays.asList(deeds), now, now, "");
    }

    private static DeedRecord deed(String key, String name, String mayor,
                                   String alliance) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("mayor", mayor);
        metadata.put("alliance", alliance);
        return new DeedRecord(key, name, 1, 1, metadata);
    }

    private static ServerIdentity server(String host, String name) {
        return ServerIdentity.of(ServerEndpoint.direct(host, 3724), name, name,
                ServerIdentity.Resolution.RESOLVED);
    }
}
