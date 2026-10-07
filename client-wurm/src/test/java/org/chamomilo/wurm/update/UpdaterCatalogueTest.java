package org.chamomilo.wurm.update;

import org.junit.Test;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class UpdaterCatalogueTest {
    @Test public void downloadIsEnabledOnlyForAbsentOrOlderInstallations() {
        for (String installed : new String[]{"", "0.9.9", "0.10.0"}) {
            ModUpdate row = row(installed, "v0.10.1");
            assertEquals("DOWNLOAD", row.getActionLabel());
            assertTrue(row.canDownload());
            assertFalse(row.isLatest());
        }
        for (String installed : new String[]{"0.10.1", "0.11.0", "1.0.0", "v0.010.001"}) {
            ModUpdate row = row(installed, "v0.10.1");
            assertEquals("LATEST", row.getActionLabel());
            assertFalse(row.canDownload());
            assertTrue(row.isLatest());
        }
        assertEquals("not installed", row("", "v0.10.1").getInstalledText());
        assertEquals("New version available: 0.10.1", row("0.10.0", "v0.10.1").getReleaseText());
    }

    @Test public void failedOrUnknownChecksNeverPretendAnInstalledModIsLatest() {
        ModUpdate failed = row("0.10.0", "nightly");
        assertFalse(failed.canDownload());
        assertFalse(failed.isLatest());
        assertEquals("UNAVAILABLE", failed.getActionLabel());
        assertEquals("Release version unavailable", failed.getReleaseText());
        assertEquals("UNAVAILABLE", row("unknown", "v0.10.1").getActionLabel());
        ModUpdate absent = GitHubReleaseClient.failedRow(target(""), "Version check failed");
        assertTrue(absent.canDownload());
        assertEquals("DOWNLOAD", absent.getActionLabel());
        assertEquals("https://github.com/chamomilo/wurm-keybinder/releases/latest", absent.getDownloadUrl());
    }

    @Test public void projectLinkIsDistinctFromTheExactZipDownload() {
        ModUpdate row = GitHubReleaseClient.catalogueRow(target("0.10.0"),
                GitHubReleaseClient.ReleaseSnapshot.fromPayload("{\"tag_name\":\"v0.10.1\",\"assets\":[{"
                        + "\"browser_download_url\":\"https://github.com/chamomilo/wurm-keybinder/releases/download/"
                        + "v0.10.1/keybinder-0.10.1.zip\"}]}"));
        assertEquals("https://github.com/chamomilo/wurm-keybinder", row.getProjectUrl());
        assertTrue(row.getDownloadUrl().endsWith("/keybinder-0.10.1.zip"));
        assertEquals("Manage keybinds, action chains and your action queue.", row.getDescription());
        ModUpdate failed = GitHubReleaseClient.failedRow(target(""), "Offline");
        assertEquals(row.getProjectUrl(), failed.getProjectUrl());
        assertEquals(row.getDescription(), failed.getDescription());
    }

    @Test public void remoteDescriptionsSurviveRuntimeVersionMergeAndOfflineCacheFormat() {
        Properties p = new Properties();
        p.setProperty("catalogVersion", "1"); p.setProperty("mods", "keybinder");
        p.setProperty("keybinder.name", "Keybinder");
        p.setProperty("keybinder.repo", "chamomilo/wurm-keybinder");
        p.setProperty("keybinder.asset", "keybinder-{version}.zip");
        p.setProperty("keybinder.class", "org.keybinder.wurm.KeybinderMod");
        p.setProperty("keybinder.description", "A description from the public catalogue.");
        List<ModCatalog.Definition> definitions = ModCatalog.parse(p, null);
        List<UpdateTarget> merged = ModCatalog.snapshot(Paths.get("build/nonexistent-catalogue-fixture"),
                Collections.singletonList(target("0.10.0")), definitions);
        assertEquals("0.10.0", merged.get(0).getInstalledVersion());
        assertEquals("A description from the public catalogue.", merged.get(0).getDescription());
        p.setProperty("keybinder.description", "Invalid\nmultiline");
        assertEquals(ModCatalog.descriptionFor("chamomilo/wurm-keybinder"),
                ModCatalog.parse(p, null).get(0).description);
    }

    private static UpdateTarget target(String installed) {
        return new UpdateTarget("keybinder", "Keybinder", installed,
                "chamomilo/wurm-keybinder", "keybinder-{version}.zip");
    }
    private static ModUpdate row(String installed, String tag) {
        return GitHubReleaseClient.catalogueRow(target(installed),
                GitHubReleaseClient.ReleaseSnapshot.fromPayload("{\"tag_name\":\"" + tag + "\"}"));
    }
}
