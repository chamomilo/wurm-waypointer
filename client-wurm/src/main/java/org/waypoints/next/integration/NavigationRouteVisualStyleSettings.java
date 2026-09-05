package org.waypoints.next.integration;

import org.waypoints.next.navigation.NavigationRouteVisualStyle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Line-preserving writer for the user-selectable route style. */
final class NavigationRouteVisualStyleSettings {
    static final String KEY = "navigationRouteVisualStyle";
    private final Path file;

    NavigationRouteVisualStyleSettings(Path file) {
        if (file == null) throw new IllegalArgumentException(
                "settings file is required");
        this.file = file;
    }

    static NavigationRouteVisualStyleSettings installed() {
        return new NavigationRouteVisualStyleSettings(Paths.get(
                "mods", "wurm-waypointer.config"));
    }

    synchronized void save(NavigationRouteVisualStyle style) throws IOException {
        if (style == null) throw new IllegalArgumentException(
                "navigation route visual style is required");
        Path destination = file.toAbsolutePath().normalize();
        Path directory = destination.getParent();
        if (directory == null) throw new IOException(
                "settings file has no parent directory: " + destination);
        Files.createDirectories(directory);

        List<String> source = Files.isRegularFile(destination)
                ? Files.readAllLines(destination, StandardCharsets.UTF_8)
                : Collections.<String>emptyList();
        List<String> updated = new ArrayList<String>(source.size() + 1);
        boolean replaced = false;
        String setting = KEY + "=" + style.name();
        for (String line : source) {
            if (isSettingLine(line)) {
                updated.add(setting);
                replaced = true;
            } else {
                updated.add(line);
            }
        }
        if (!replaced) {
            if (!updated.isEmpty() && !updated.get(updated.size() - 1).isEmpty()) {
                updated.add("");
            }
            updated.add(setting);
        }

        Path temporary = Files.createTempFile(directory,
                destination.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, updated, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static boolean isSettingLine(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")
                || trimmed.startsWith("!")) return false;
        if (!trimmed.startsWith(KEY)) return false;
        if (trimmed.length() == KEY.length()) return false;
        char delimiter = trimmed.charAt(KEY.length());
        return delimiter == '=' || delimiter == ':'
                || Character.isWhitespace(delimiter);
    }
}
