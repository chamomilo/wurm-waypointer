package org.chamomilo.wurm.update;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class ModCatalogTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void remoteCatalogueAddsNewModsAndFiltersPrivateAndForeignRepositories() throws Exception {
        Path cache = temporary.getRoot().toPath().resolve("cache/catalog.properties");
        List<ModCatalog.Definition> definitions = ModCatalog.loadDefinitions(new ModCatalog.Source() {
            public String catalogue() { return catalogueText(); }
            public String publicRepositories(int page) { return "[{\"full_name\":\"chamomilo/new-public-mod\"}]"; }
        }, cache);
        assertEquals(1, definitions.size());
        List<UpdateTarget> rows = ModCatalog.snapshot(temporary.getRoot().toPath(),
                Collections.<UpdateTarget>emptyList(), definitions);
        assertEquals("New Public Mod", rows.get(0).getDisplayName());
        assertEquals("", rows.get(0).getInstalledVersion());
        assertTrue(Files.isRegularFile(cache));
        List<ModCatalog.Definition> offline = ModCatalog.loadDefinitions(new ModCatalog.Source() {
            public String catalogue() throws IOException { throw new IOException("Offline"); }
            public String publicRepositories(int page) { throw new AssertionError(); }
        }, cache);
        assertEquals(1, offline.size());
        assertEquals("chamomilo/new-public-mod", offline.get(0).repository);
    }

    @Test public void disabledAndUnloadedModIsInstalledOnlyWhenItsJarExists() throws Exception {
        Path mods = temporary.newFolder("mods").toPath();
        Path folder = Files.createDirectories(mods.resolve("highres-hud"));
        Files.write(folder.resolve("highres-hud-0.1.0.jar"), new byte[0]);
        Files.write(mods.resolve("highres-hud.properties"), (
                "classname=org.highreshud.client.HighResHudMod\nclasspath=highres-hud-0.1.0.jar\nenabled=false\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(mods.resolve("keybinder.properties"), "classpath=missing.jar\nversion=0.9.1\n".getBytes(StandardCharsets.UTF_8));
        List<UpdateTarget> rows = ModCatalog.snapshot(mods, Collections.<UpdateTarget>emptyList());
        assertEquals(6, rows.size());
        assertEquals("0.1.0", target(rows, "highres-hud").getInstalledVersion());
        assertEquals("", target(rows, "keybinder").getInstalledVersion());
    }

    @Test public void loadedVersionWinsButPrivateMetadataCannotJoinPublicCatalogue() throws Exception {
        Path mods = temporary.newFolder("mods").toPath();
        UpdateTarget runtime = new UpdateTarget("legacy-waypointer-id", "Old Name", "1.4.0",
                "chamomilo/wurm-waypointer", "old-asset-{version}.zip");
        UpdateTarget privateMod = new UpdateTarget("secret", "Secret", "1.0.0",
                "chamomilo/secret", "secret-{version}.zip");
        List<UpdateTarget> rows = ModCatalog.snapshot(mods, java.util.Arrays.asList(runtime, privateMod));
        assertEquals(6, rows.size());
        assertEquals("1.4.0", target(rows, "wurm-waypointer").getInstalledVersion());
        assertEquals("wurm-waypointer-{version}.zip", target(rows, "wurm-waypointer").getAssetTemplate());
    }

    @Test public void currentAbsentAndNewerDevelopmentVersionsHaveCorrectButtons() {
        assertEquals("UPDATE", row("0.9.1", "v0.10.0").getActionLabel());
        assertEquals("INSTALL", row("", "v0.10.0").getActionLabel());
        assertEquals("", row("0.10.0", "v0.10.0").getActionLabel());
        assertEquals("", row("0.11.0", "v0.10.0").getActionLabel());
        assertTrue(row("0.11.0", "v0.10.0").getStatusText().contains("Installed version is newer"));
        assertTrue(row("unknown", "v0.10.0").getStatusText().contains("version unknown"));
        assertFalse(row("", "v0.10.0").isInstalled());
        assertEquals("", row("0.9.1", "nightly").getActionLabel());
    }

    @Test public void allFailedChecksStillDeliverEveryCatalogueRowToTheWindowHost() throws Exception {
        List<UpdateTarget> targets = ModCatalog.snapshot(temporary.newFolder("mods").toPath(),
                Collections.<UpdateTarget>emptyList());
        AtomicReference<List<ModUpdate>> delivered = new AtomicReference<List<ModUpdate>>();
        SharedUpdateCoordinator.checkAll(new SharedUpdateCoordinator.Host() {
            public void updatesReady(List<ModUpdate> updates) { delivered.set(updates); }
            public void checkFailed(String repo, Throwable failure) { }
        }, targets, new GitHubReleaseClient(repo -> { throw new IOException("HTTP 403"); }));
        assertEquals(6, delivered.get().size());
        for (ModUpdate update : delivered.get()) {
            assertTrue(update.getStatusText().contains("Version check failed"));
            assertEquals("", update.getActionLabel());
        }
    }

    @Test public void successfulChecksDeliverCurrentAndNotInstalledModsToo() throws Exception {
        AtomicReference<List<ModUpdate>> delivered = new AtomicReference<List<ModUpdate>>();
        List<UpdateTarget> targets = java.util.Arrays.asList(
                new UpdateTarget("keybinder", "Keybinder", "0.10.0", "chamomilo/wurm-keybinder", "keybinder-{version}.zip"),
                new UpdateTarget("new", "New", "", "chamomilo/new", "new-{version}.zip"));
        SharedUpdateCoordinator.checkAll(new SharedUpdateCoordinator.Host() {
            public void updatesReady(List<ModUpdate> updates) { delivered.set(updates); }
            public void checkFailed(String repo, Throwable failure) { fail("Unexpected check error"); }
        }, targets, new GitHubReleaseClient(repo -> "{\"tag_name\":\"v0.10.0\"}"));
        assertEquals(2, delivered.get().size());
        assertEquals("", delivered.get().get(0).getActionLabel());
        assertEquals("INSTALL", delivered.get().get(1).getActionLabel());
    }

    private static ModUpdate row(String installed, String tag) {
        return GitHubReleaseClient.catalogueRow(new UpdateTarget("keybinder", "Keybinder", installed,
                "chamomilo/wurm-keybinder", "keybinder-{version}.zip"),
                GitHubReleaseClient.ReleaseSnapshot.fromPayload("{\"tag_name\":\"" + tag + "\"}"));
    }
    private static UpdateTarget target(List<UpdateTarget> targets, String id) {
        for (UpdateTarget target : targets) if (id.equals(target.getId())) return target;
        throw new AssertionError(id);
    }
    private static String catalogueText() {
        String result = "catalogVersion=1\nmods=new,secret,foreign\n";
        for (String id : new String[]{"new", "secret", "foreign"}) result += id + ".name=New Public Mod\n"
                + id + ".repo=" + ("foreign".equals(id) ? "another-owner/mod" : "chamomilo/" + ("new".equals(id) ? "new-public-mod" : "secret")) + "\n"
                + id + ".asset=new-{version}.zip\n" + id + ".class=org.example.NewMod\n";
        return result;
    }
}
