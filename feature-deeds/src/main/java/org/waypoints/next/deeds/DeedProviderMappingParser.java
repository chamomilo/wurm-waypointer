package org.waypoints.next.deeds;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parses custom mappings:
 * endpoint[|exact world]|&lt;JSON or CSV&gt;|https-or-file-source[|width|height].
 * Entries are separated by semicolons.
 */
public final class DeedProviderMappingParser {
    private DeedProviderMappingParser() { }

    public static List<DeedProviderMapping> parse(String value,
                                                  int defaultWidth,
                                                  int defaultHeight) {
        if (defaultWidth < 1 || defaultHeight < 1) throw new IllegalArgumentException(
                "default map bounds must be positive");
        List<DeedProviderMapping> result = new ArrayList<DeedProviderMapping>();
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        for (String raw : value.split(";")) {
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            String[] fields = entry.split("\\|", -1);
            int formatAt = formatIndex(fields);
            if (formatAt < 1 || formatAt > 2 || fields.length < formatAt + 2
                    || fields.length > formatAt + 4) {
                throw new IllegalArgumentException(
                        "invalid deed provider mapping: " + entry);
            }
            String endpoint = fields[0].trim();
            String world = formatAt == 2 ? fields[1].trim() : "";
            DeedFeedFormat format = format(fields[formatAt]);
            URI source = uri(fields[formatAt + 1]);
            int width = fields.length > formatAt + 2
                    ? positive(fields[formatAt + 2], "map width") : defaultWidth;
            int height = fields.length > formatAt + 3
                    ? positive(fields[formatAt + 3], "map height") : defaultHeight;
            String provider = format == DeedFeedFormat.CSV
                    ? GenericCsvDeedProvider.KEY : GenericJsonDeedProvider.KEY;
            DeedProviderMapping mapping = new DeedProviderMapping(provider,
                    new ServerIdentityKey(endpoint, world), source, format,
                    width, height);
            for (DeedProviderMapping old : result) {
                if (old.getServer().equals(mapping.getServer())) {
                    throw new IllegalArgumentException(
                            "duplicate custom deed mapping for "
                                    + mapping.getServer().externalForm());
                }
            }
            result.add(mapping);
        }
        return Collections.unmodifiableList(result);
    }

    private static int formatIndex(String[] fields) {
        if (fields.length > 1 && isFormat(fields[1])) return 1;
        if (fields.length > 2 && isFormat(fields[2])) return 2;
        return -1;
    }

    private static boolean isFormat(String value) {
        String clean = value == null ? "" : value.trim().toUpperCase(Locale.ENGLISH);
        return "JSON".equals(clean) || "CSV".equals(clean);
    }

    private static DeedFeedFormat format(String value) {
        return "CSV".equalsIgnoreCase(value.trim())
                ? DeedFeedFormat.CSV : DeedFeedFormat.JSON;
    }

    private static URI uri(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("deed source is required");
        if (clean.matches("^[A-Za-z]:[\\\\/].*")) {
            return Paths.get(clean).toAbsolutePath().normalize().toUri();
        }
        try {
            URI parsed = new URI(clean);
            if (!parsed.isAbsolute()) return Paths.get(clean).toAbsolutePath()
                    .normalize().toUri();
            return parsed;
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("invalid deed source URI: " + clean, invalid);
        }
    }

    private static int positive(String value, String label) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 1 || parsed > 65536) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(label + " must be in 1..65536", invalid);
        }
    }
}
