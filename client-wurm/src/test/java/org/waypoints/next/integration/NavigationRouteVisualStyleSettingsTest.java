package org.waypoints.next.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.navigation.NavigationRouteVisualStyle;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NavigationRouteVisualStyleSettingsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void replacesStyleAndPreservesOtherSettingsAndComments()
            throws Exception {
        File file = temporary.newFile("wurm-waypointer.config");
        Files.write(file.toPath(), Arrays.asList(
                "# Route presentation",
                "navigationRouteVisualStyle=PULSE",
                "navigationHighwaysEnabled=true"), StandardCharsets.UTF_8);

        new NavigationRouteVisualStyleSettings(file.toPath()).save(
                NavigationRouteVisualStyle.MOVING_DASHES);

        List<String> lines = Files.readAllLines(file.toPath(),
                StandardCharsets.UTF_8);
        assertEquals("# Route presentation", lines.get(0));
        assertEquals("navigationRouteVisualStyle=MOVING_DASHES", lines.get(1));
        assertEquals("navigationHighwaysEnabled=true", lines.get(2));
    }

    @Test public void appendsStyleWhenSettingIsMissing() throws Exception {
        File file = temporary.newFile("wurm-waypointer.config");
        Files.write(file.toPath(), Arrays.asList("other=true"),
                StandardCharsets.UTF_8);

        new NavigationRouteVisualStyleSettings(file.toPath()).save(
                NavigationRouteVisualStyle.SOLID);

        String saved = new String(Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8);
        assertTrue(saved.contains("other=true"));
        assertTrue(saved.contains("navigationRouteVisualStyle=SOLID"));
        assertFalse(saved.contains("navigationRouteVisualStyle=PULSE"));
    }

    @Test public void settingMatcherDoesNotReplaceCommentsOrLongerKeys() {
        assertTrue(NavigationRouteVisualStyleSettings.isSettingLine(
                " navigationRouteVisualStyle : PULSE"));
        assertFalse(NavigationRouteVisualStyleSettings.isSettingLine(
                "# navigationRouteVisualStyle=SOLID"));
        assertFalse(NavigationRouteVisualStyleSettings.isSettingLine(
                "navigationRouteVisualStyleBackup=SOLID"));
    }
}
