package org.chamomilo.wurm.update;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import static org.junit.Assert.*;

public class UpdatePreferencesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void firstLaunchShowsWindowWithoutCreatingOrBundlingAPreference() {
        Path file = temporary.getRoot().toPath().resolve("user/.chamomilo/updater.properties");
        assertFalse(new UpdatePreferences(file).isSkipNextStart());
        assertFalse(Files.exists(file));
    }

    @Test public void checkedStateSurvivesRestartAndChangesToInstalledMods() throws Exception {
        Path file = temporary.getRoot().toPath().resolve("user/.chamomilo/updater.properties");
        new UpdatePreferences(file).setSkipNextStart(true);
        Path mods = temporary.newFolder("mods").toPath();
        ModCatalog.snapshot(mods, Collections.<UpdateTarget>emptyList());
        assertTrue(new UpdatePreferences(file).isSkipNextStart());
        Files.write(mods.resolve("keybinder.properties"), "version=0.10.0\n".getBytes(StandardCharsets.UTF_8));
        ModCatalog.snapshot(mods, Collections.singletonList(new UpdateTarget("keybinder", "Keybinder",
                "0.10.0", "chamomilo/wurm-keybinder", "keybinder-{version}.zip")));
        assertTrue(new UpdatePreferences(file).isSkipNextStart());
        Files.delete(mods.resolve("keybinder.properties"));
        assertTrue(new UpdatePreferences(file).isSkipNextStart());
        try (java.util.stream.Stream<Path> files = Files.list(file.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test public void uncheckingRestoresStartupDisplayAfterRestart() throws Exception {
        Path file = temporary.getRoot().toPath().resolve("updater.properties");
        UpdatePreferences first = new UpdatePreferences(file); first.setSkipNextStart(true);
        UpdatePreferences second = new UpdatePreferences(file); second.setSkipNextStart(false);
        assertFalse(new UpdatePreferences(file).isSkipNextStart());
    }

    @Test public void failedSaveDoesNotClaimTheSelectionWasRemembered() throws Exception {
        Path blocked = temporary.newFile("blocked-parent").toPath();
        UpdatePreferences preferences = new UpdatePreferences(blocked.resolve("updater.properties"));
        try { preferences.setSkipNextStart(true); fail("Expected a persistence failure"); }
        catch (IOException expected) { }
        assertFalse(preferences.isSkipNextStart());
    }
}
