package org.chamomilo.wurm.update;

import org.gotti.wurmunlimited.modloader.interfaces.ModEntry;
import org.gotti.wurmunlimited.modloader.interfaces.ModListener;
import org.junit.Test;
import org.waypoints.next.WurmWaypointerMod;

import java.io.FileInputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

/** Exercises Waypointer's real release metadata with the shared protocol. */
public final class WaypointerUpdaterTest {
    @Test public void installedWaypointerRegistersWithItsRuntimeVersion() throws Exception {
        Properties metadata = metadata();
        metadata.setProperty("version", "0.0.0");
        WurmWaypointerMod mod = new WurmWaypointerMod();
        assertTrue(mod instanceof ModListener);

        UpdateTarget target = SharedUpdateCoordinator.targetFrom(entry(metadata, mod));

        assertNotNull(target);
        assertEquals("wurm-waypointer", target.getId());
        assertEquals("Waypointer", target.getDisplayName());
        assertEquals(WurmWaypointerMod.VERSION, target.getInstalledVersion());
        assertEquals("chamomilo/wurm-waypointer", target.getRepository());
    }

    @Test public void severalModsShareOneHostAndDuplicateCallbacksDoNotDuplicateTargets()
            throws Exception {
        SharedUpdateCoordinator.Registry registry = new SharedUpdateCoordinator.Registry();
        SharedUpdateCoordinator.Host waypointerHost = new SilentHost();
        assertTrue(registry.registerHost("wurm-waypointer", waypointerHost));
        assertFalse(registry.registerHost("keybinder", new SilentHost()));
        assertSame(waypointerHost, registry.owner());

        ModEntry<Object> waypointer = entry(metadata(), new WurmWaypointerMod());
        Properties keybinderMetadata = metadata();
        keybinderMetadata.setProperty("updateId", "keybinder");
        keybinderMetadata.setProperty("updateName", "Keybinder");
        keybinderMetadata.setProperty("updateRepo", "chamomilo/wurm-keybinder");
        keybinderMetadata.setProperty("updateAsset", "keybinder-{version}.zip");
        keybinderMetadata.setProperty("version", "0.9.1");
        ModEntry<Object> keybinder = entry(keybinderMetadata, new Object());
        registry.observe(waypointer);
        registry.observe(keybinder);
        registry.observe(waypointer);
        registry.observe(keybinder);
        registry.observe(entry(new Properties(), new Object()));

        List<UpdateTarget> targets = registry.snapshot();
        assertEquals(2, targets.size());
        assertEquals("wurm-waypointer", targets.get(0).getId());
        assertEquals("keybinder", targets.get(1).getId());
        assertEquals("0.9.1", targets.get(1).getInstalledVersion());
    }

    @Test public void newerReleaseChoosesWaypointerZipAmongOtherAssets() throws Exception {
        UpdateTarget target = target();
        String next = nextPatch(target.getInstalledVersion());
        String zip = "wurm-waypointer-" + next + ".zip";
        String expected = "https://github.com/chamomilo/wurm-waypointer/releases/download/v"
                + next + "/" + zip;
        GitHubReleaseClient client = new GitHubReleaseClient(repository ->
                "{\"tag_name\":\"v" + next + "\",\"assets\":["
                        + "{\"browser_download_url\":\"https://github.com/other/mod/releases/download/v"
                        + next + "/" + zip + "\"},"
                        + "{\"browser_download_url\":\"" + expected + ".sha256\"},"
                        + "{\"browser_download_url\":\"" + expected + "\"}]}");

        ModUpdate update = GitHubReleaseClient.findUpdate(
                target, client.readLatest(target.getRepository()));

        assertNotNull(update);
        assertEquals(next, update.getLatestVersion());
        assertEquals(expected, update.getDownloadUrl());
        assertTrue(update.getNotificationText().startsWith("Wurm Waypointer Mod."));
        assertFalse(update.getNotificationText().contains("Wurm Wurm"));
    }

    @Test public void currentOlderAndUnstableVersionsDoNotNotify() throws Exception {
        UpdateTarget target = target();
        for (String tag : new String[]{"v" + target.getInstalledVersion(), "v1.0.0",
                "nightly", "v" + nextPatch(target.getInstalledVersion()) + "-beta.1"}) {
            assertNull("Unexpected notification for " + tag,
                    GitHubReleaseClient.findUpdate(target, release(tag)));
        }
    }

    @Test public void absentZipOpensWaypointerReleasePage() throws Exception {
        UpdateTarget target = target();
        ModUpdate update = GitHubReleaseClient.findUpdate(
                target, release(nextPatch(target.getInstalledVersion())));

        assertNotNull(update);
        assertEquals("https://github.com/chamomilo/wurm-waypointer/releases/latest",
                update.getDownloadUrl());
    }

    @Test public void foreignProvidersIncompatibleProtocolsAndMissingMetadataAreIgnored()
            throws Exception {
        Properties foreign = metadata();
        foreign.setProperty("updateProvider", "other");
        assertNull(SharedUpdateCoordinator.targetFrom(entry(foreign, new WurmWaypointerMod())));
        Properties incompatible = metadata();
        incompatible.setProperty("updateCoordinatorProtocol", "2");
        assertNull(SharedUpdateCoordinator.targetFrom(entry(incompatible, new WurmWaypointerMod())));
        Properties incomplete = metadata();
        incomplete.remove("updateRepo");
        assertNull(SharedUpdateCoordinator.targetFrom(entry(incomplete, new WurmWaypointerMod())));
    }

    private static Properties metadata() throws Exception {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(
                System.getProperty("waypointerMetadataFile"))) {
            properties.load(input);
        }
        return properties;
    }

    private static UpdateTarget target() throws Exception {
        return SharedUpdateCoordinator.targetFrom(entry(metadata(), new WurmWaypointerMod()));
    }

    private static GitHubReleaseClient.ReleaseSnapshot release(String tag) {
        return GitHubReleaseClient.ReleaseSnapshot.fromPayload("{\"tag_name\":\"" + tag + "\"}");
    }

    private static String nextPatch(String version) {
        int dot = version.lastIndexOf('.');
        return version.substring(0, dot + 1) + (Integer.parseInt(version.substring(dot + 1)) + 1);
    }

    private static ModEntry<Object> entry(final Properties properties, final Object mod) {
        return new ModEntry<Object>() {
            @Override public String getName() { return properties.getProperty("updateId", "unrelated"); }
            @Override public Properties getProperties() { return properties; }
            @Override public Object getWurmMod() { return mod; }
        };
    }

    private static final class SilentHost implements SharedUpdateCoordinator.Host {
        @Override public void updatesReady(List<ModUpdate> updates) { }
        @Override public void checkFailed(String repository, Throwable failure) { }
    }
}
