package org.waypoints.next.persistence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.model.VanillaLandmarkKind;
import org.waypoints.next.model.VanillaLandmarkVisibility;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VanillaLandmarkVisibilityStoreTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void missingChoicesDefaultOnAndRemainEndpointSpecific() {
        VanillaLandmarkVisibility state = new VanillaLandmarkVisibility();
        assertTrue(state.isEnabled("one.example:3724",
                VanillaLandmarkKind.WHITE_LIGHT));
        state = state.withEnabled("one.example:3724",
                VanillaLandmarkKind.WHITE_LIGHT, false);
        assertFalse(state.isEnabled("one.example:3724",
                VanillaLandmarkKind.WHITE_LIGHT));
        assertTrue(state.isEnabled("two.example:3724",
                VanillaLandmarkKind.WHITE_LIGHT));
        assertTrue(state.isEnabled("one.example:3724",
                VanillaLandmarkKind.RIFT));
    }

    @Test public void independentStateFileRoundTripsAndRejectsBadHeader()
            throws Exception {
        VanillaLandmarkVisibilityStore store = new VanillaLandmarkVisibilityStore(
                temporary.newFolder("state").toPath().resolve("vanilla.state"));
        VanillaLandmarkVisibility state = new VanillaLandmarkVisibility()
                .withEnabled("novus.example:3724", VanillaLandmarkKind.RIFT, false)
                .withEnabled("liberty.example:3724",
                        VanillaLandmarkKind.BLACK_LIGHT, true);
        store.save(state);
        VanillaLandmarkVisibility loaded = store.load();
        assertFalse(loaded.isEnabled("novus.example:3724",
                VanillaLandmarkKind.RIFT));
        assertTrue(loaded.isEnabled("liberty.example:3724",
                VanillaLandmarkKind.BLACK_LIGHT));

        Files.write(store.getFile(), "invalid".getBytes(StandardCharsets.UTF_8));
        boolean rejected = false;
        try { store.load(); }
        catch (java.io.IOException expected) { rejected = true; }
        assertTrue(rejected);
    }
}
