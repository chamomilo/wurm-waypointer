package org.waypoints.next.lootmap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.source.MapBounds;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

public class LootMapHuntLogTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void createsExactlyOneFlushedRelativeJsonlFilePerHunt()
            throws Exception {
        Instant now = Instant.parse("2026-08-08T12:00:00Z");
        LootMapObservation observation = new LootMapObservation(2345, 1234,
                0.0d, LootMapRelativeDirection.RIGHT,
                LootMapDistanceBand.ONE_TO_THREE, now);
        LootMapHuntSession hunt = new LootMapHuntSession(
                temporary.getRoot().toPath(), new MapBounds(4096, 4096), observation);
        hunt.observe(observation);
        hunt.event("chest_dug_up", now.plusSeconds(30));
        hunt.close("chest_opened", now.plusSeconds(60));

        assertEquals(1, temporary.getRoot().listFiles().length);
        List<String> lines = Files.readAllLines(hunt.getLogFile(),
                StandardCharsets.UTF_8);
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).contains("\"event\":\"hunt_started\""));
        assertTrue(lines.get(1).contains("\"originDx\":0.000000"));
        assertTrue(lines.get(1).contains("\"plannedWaypointDx\":"));
        assertTrue(lines.get(1).contains("\"landAdjusted\":false"));
        assertTrue(lines.get(2).contains("\"event\":\"chest_dug_up\""));
        assertTrue(lines.get(3).contains("\"event\":\"chest_opened\""));
        assertFalse(lines.get(1).contains("2345.000000"));
        assertFalse(lines.get(1).contains("1234.000000"));
    }
}
