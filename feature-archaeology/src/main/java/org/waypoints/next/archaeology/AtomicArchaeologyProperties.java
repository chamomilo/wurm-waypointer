package org.waypoints.next.archaeology;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Small UTF-8 properties store with forced temp writes, backup and recovery. */
final class AtomicArchaeologyProperties {
    private final Path file;
    private boolean recoveredFromBackup;

    AtomicArchaeologyProperties(Path file) {
        if (file == null) throw new IllegalArgumentException("store path is required");
        this.file = file;
    }

    Path getFile() { return file; }
    boolean wasRecoveredFromBackup() { return recoveredFromBackup; }

    Properties load() throws IOException {
        recoveredFromBackup = false;
        if (!Files.isRegularFile(file)) return new Properties();
        try {
            return read(file);
        } catch (IOException | RuntimeException primaryFailure) {
            Path backup = backupFile();
            if (!Files.isRegularFile(backup)) throw io("unable to read archaeology store", primaryFailure);
            try {
                Properties recovered = read(backup);
                recoveredFromBackup = true;
                return recovered;
            } catch (IOException | RuntimeException backupFailure) {
                IOException result = io("unable to read archaeology store or backup", primaryFailure);
                result.addSuppressed(backupFailure);
                throw result;
            }
        }
    }

    void save(Properties properties, String comment) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = absolute.resolveSibling(absolute.getFileName().toString()
                + "." + UUID.randomUUID() + ".tmp");
        Properties verified = new Properties();
        verified.putAll(properties);
        verified.setProperty("store.checksum", checksum(verified));
        byte[] bytes = serialize(verified, comment);
        try {
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (Files.isRegularFile(absolute) && !recoveredFromBackup) {
                Files.copy(absolute, backupFile(), StandardCopyOption.REPLACE_EXISTING);
                force(backupFile());
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            force(absolute);
            recoveredFromBackup = false;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Properties read(Path source) throws IOException {
        Properties values = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(source),
                StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        String expected = values.getProperty("store.checksum", "").trim();
        if (expected.isEmpty() || !expected.equals(checksum(values))) {
            throw new IOException("archaeology store checksum mismatch: " + source);
        }
        return values;
    }

    private static String checksum(Properties values) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<String> keys = new ArrayList<String>();
            for (Object key : values.keySet()) {
                String text = key.toString();
                if (!"store.checksum".equals(text)) keys.add(text);
            }
            Collections.sort(keys);
            for (String key : keys) {
                update(digest, key);
                digest.update((byte) 0);
                update(digest, values.getProperty(key, ""));
                digest.update((byte) '\n');
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) {
                result.append(String.format(java.util.Locale.ENGLISH,
                        "%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] serialize(Properties values, String comment)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
            values.store(writer, comment);
        }
        return bytes.toByteArray();
    }

    private Path backupFile() {
        Path absolute = file.toAbsolutePath().normalize();
        return absolute.resolveSibling(absolute.getFileName().toString() + ".bak");
    }

    private static void force(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static IOException io(String message, Throwable cause) {
        return cause instanceof IOException ? (IOException) cause
                : new IOException(message, cause);
    }
}
