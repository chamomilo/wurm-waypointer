package org.waypoints.next.integration;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

/** Typed, bounded access to mod properties with optional per-setting recovery. */
final class ConfigurationProperties {
    private final Properties source;
    private final Consumer<String> warningSink;

    ConfigurationProperties(Properties source, Consumer<String> warningSink) {
        this.source = source == null ? new Properties() : source;
        this.warningSink = warningSink;
    }

    Path path(String key, String defaultValue) {
        String value = text(key, defaultValue);
        if (value.isEmpty() || value.length() > 1024
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return invalid(key, Paths.get(defaultValue), "a non-empty path of at most 1024 characters", null);
        }
        try {
            return Paths.get(value);
        } catch (InvalidPathException invalid) {
            return invalid(key, Paths.get(defaultValue), "a valid local path", invalid);
        }
    }

    boolean bool(String key, boolean defaultValue) {
        String value = text(key, Boolean.toString(defaultValue));
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return invalid(key, defaultValue, "true or false", null);
    }

    int integer(String key, int defaultValue, int minimum, int maximum) {
        String value = text(key, Integer.toString(defaultValue));
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException invalid) {
            return invalid(key, defaultValue, minimum + ".." + maximum, invalid);
        }
    }

    float decimal(String key, float defaultValue, float minimum, float maximum) {
        String value = text(key, Float.toString(defaultValue));
        try {
            float parsed = Float.parseFloat(value);
            if (!Float.isFinite(parsed) || parsed < minimum || parsed > maximum) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException invalid) {
            return invalid(key, defaultValue, minimum + ".." + maximum, invalid);
        }
    }

    <T extends Enum<T>> T enumeration(String key, T defaultValue, Class<T> type) {
        String value = text(key, defaultValue.name());
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalid) {
            return invalid(key, defaultValue, "one of " + Arrays.toString(type.getEnumConstants()), invalid);
        }
    }

    private String text(String key, String defaultValue) {
        String value = source.getProperty(key, defaultValue);
        return value == null ? "" : value.trim();
    }

    private <T> T invalid(String key, T defaultValue, String expected,
                          RuntimeException cause) {
        String message = key + " must be " + expected;
        if (warningSink != null) {
            warningSink.accept(message + "; using " + defaultValue);
            return defaultValue;
        }
        throw new IllegalArgumentException(message, cause);
    }
}
