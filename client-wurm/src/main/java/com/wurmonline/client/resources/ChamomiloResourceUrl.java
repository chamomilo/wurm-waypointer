package com.wurmonline.client.resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** Shared updater artwork embedded in every participating JAR. */
public final class ChamomiloResourceUrl extends ResourceUrl {
    private final String path;
    public ChamomiloResourceUrl(String path) { super(path); this.path = path; }
    @Override public ResourceUrl derive(String relative) {
        return new ChamomiloResourceUrl(path.substring(0, path.lastIndexOf('/') + 1) + relative);
    }
    @Override public ResourceUrl changeFilePath(String value) { return new ChamomiloResourceUrl(value); }
    @Override public InputStream openStream() throws IOException {
        InputStream input = ChamomiloResourceUrl.class.getResourceAsStream(path);
        if (input != null) return input;
        // Wurm's shared Javassist loader exposes classes but can hide embedded assets.
        // Read and close the ZIP eagerly so the mod JAR is not locked on Windows.
        if (!path.startsWith("/org/chamomilo/wurm/update/") || path.contains(".."))
            throw new IOException("Invalid updater artwork path: " + path);
        Path mods = Paths.get("mods").toAbsolutePath().normalize();
        Map<String, Path> descriptors = new TreeMap<String, Path>();
        if (Files.isDirectory(mods)) try (DirectoryStream<Path> configs = Files.newDirectoryStream(mods, "*.properties")) {
            for (Path config : configs) descriptors.put(config.getFileName().toString(), config);
            for (Map.Entry<String, Path> config : descriptors.entrySet()) {
                String mod = config.getKey().replaceFirst("\\.properties$", "");
                try (InputStream descriptor = Files.newInputStream(config.getValue())) {
                    Properties properties = new Properties(); properties.load(descriptor);
                    // Only active classpath JARs: old versioned JARs may remain after an upgrade.
                    for (String raw : properties.getProperty("classpath", "").split("[,;]")) {
                        String relative = raw.trim();
                        if (!relative.endsWith(".jar")) continue;
                        Path jar = mods.resolve(mod).resolve(relative).normalize();
                        if (!jar.startsWith(mods)) continue;
                        if (!Files.isRegularFile(jar)) jar = mods.resolve(relative).normalize();
                        if (!jar.startsWith(mods) || !Files.isRegularFile(jar)) continue;
                        try (ZipFile zip = new ZipFile(jar.toFile())) {
                            ZipEntry entry = zip.getEntry(path.substring(1));
                            if (entry == null || entry.isDirectory()) continue;
                            try (InputStream resource = zip.getInputStream(entry)) {
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                byte[] buffer = new byte[8192]; int count;
                                while ((count = resource.read(buffer)) != -1) {
                                    if (bytes.size() + count > 8 * 1024 * 1024)
                                        throw new IOException("Updater artwork exceeds 8 MiB");
                                    bytes.write(buffer, 0, count);
                                }
                                return new ByteArrayInputStream(bytes.toByteArray());
                            }
                        } catch (IOException ignored) { /* Try the next participating JAR. */ }
                    }
                } catch (IOException | IllegalArgumentException ignored) { /* One unreadable mod must not hide the frame. */ }
            }
        }
        throw new IOException("Missing shared artwork: " + path);
    }
    @Override public boolean exists() {
        try (InputStream input = openStream()) { return input != null; }
        catch (IOException failure) { return false; }
    }
    @Override public String getFilePath() { return path; }
    @Override public Map<String, String> getOverrides() { return Collections.emptyMap(); }
    @Override long getSize() { return 0; }
    @Override public boolean equals(Object other) {
        return other instanceof ChamomiloResourceUrl && path.equals(((ChamomiloResourceUrl) other).path);
    }
    @Override public String toString() { return "chamomilo-artwork:" + path; }
}
