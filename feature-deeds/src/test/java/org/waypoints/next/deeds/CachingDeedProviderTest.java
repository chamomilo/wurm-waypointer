package org.waypoints.next.deeds;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class CachingDeedProviderTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void malformedRefreshKeepsLastKnownGoodCatalog() throws Exception {
        Path source = temporary.newFile("deeds.json").toPath();
        Files.write(source, json(4, 5).getBytes(StandardCharsets.UTF_8));
        ServerIdentity server = ServerIdentity.of(
                ServerEndpoint.direct("example.test", 3724), "Test", "Test",
                ServerIdentity.Resolution.RESOLVED);
        DeedProviderMapping mapping = new DeedProviderMapping(
                GenericJsonDeedProvider.KEY,
                new ServerIdentityKey("example.test:3724", ""),
                source.toUri(), DeedFeedFormat.JSON, 100, 100);
        GenericJsonDeedProvider provider = new GenericJsonDeedProvider(
                Collections.singletonList(mapping), temporary.newFolder("cache").toPath(), null);
        try {
            DeedProviderSnapshot good = provider.refresh(server, true).get();
            assertEquals(DeedProviderStatus.READY, good.getStatus());
            assertEquals(4, good.getDeeds().get(0).getTileX());

            Files.write(source, "not json".getBytes(StandardCharsets.UTF_8));
            DeedProviderSnapshot retained = provider.refresh(server, true).get();
            assertEquals(DeedProviderStatus.CACHED, retained.getStatus());
            assertEquals(1, retained.getDeeds().size());
            assertEquals(4, retained.getDeeds().get(0).getTileX());
        } finally {
            provider.close();
        }
    }

    private static String json(int x, int y) {
        return "[{\"id\":\"one\",\"name\":\"One\",\"x\":"
                + x + ",\"y\":" + y + "}]";
    }
}
