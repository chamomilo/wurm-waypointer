package org.waypoints.next.persistence;

import org.waypoints.next.model.VanillaLandmarkVisibility;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Small independent state file; it never mutates waypoint storage or mod config. */
public final class VanillaLandmarkVisibilityStore {
    private static final String HEADER = "WURM-WAYPOINTER-VANILLA-STATE\t1";
    private static final long MAX_BYTES = 1024L * 1024L;
    private final Path file;

    public VanillaLandmarkVisibilityStore(Path file) {
        if (file == null) throw new IllegalArgumentException("state file is required");
        this.file = file;
    }

    public Path getFile() { return file; }

    public VanillaLandmarkVisibility load() throws IOException {
        if (!Files.isRegularFile(file)) return new VanillaLandmarkVisibility();
        if (Files.size(file) > MAX_BYTES) throw new IOException(
                "vanilla landmark state is too large");
        return decode(Files.readAllBytes(file));
    }

    public void save(VanillaLandmarkVisibility state) throws IOException {
        if (state == null) throw new IllegalArgumentException("state is required");
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        byte[] bytes = encode(state);
        Path temp = file.resolveSibling(file.getFileName() + "."
                + UUID.randomUUID() + ".tmp");
        writeForced(temp, bytes);
        try {
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            VanillaLandmarkVisibility verified = decode(Files.readAllBytes(file));
            if (!verified.entries().equals(state.entries())) {
                throw new IOException("vanilla landmark state verification failed");
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    byte[] encode(VanillaLandmarkVisibility state) {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        for (Map.Entry<String, Boolean> entry : state.entries().entrySet()) {
            String key = encoder.encodeToString(
                    entry.getKey().getBytes(StandardCharsets.UTF_8));
            text.append(key).append('\t')
                    .append(entry.getValue().booleanValue() ? '1' : '0')
                    .append('\n');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    VanillaLandmarkVisibility decode(byte[] bytes) throws IOException {
        String text = new String(bytes == null ? new byte[0] : bytes,
                StandardCharsets.UTF_8);
        String[] lines = text.split("\\r?\\n", -1);
        if (lines.length == 0 || !HEADER.equals(lines[0])) {
            throw new IOException("unsupported vanilla landmark state header");
        }
        LinkedHashMap<String, Boolean> values =
                new LinkedHashMap<String, Boolean>();
        Base64.Decoder decoder = Base64.getUrlDecoder();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            String[] fields = lines[i].split("\\t", -1);
            if (fields.length != 2 || !("0".equals(fields[1])
                    || "1".equals(fields[1]))) {
                throw new IOException("invalid vanilla landmark state line " + (i + 1));
            }
            try {
                String key = new String(decoder.decode(fields[0]),
                        StandardCharsets.UTF_8);
                if (key.trim().isEmpty()) throw new IllegalArgumentException();
                values.put(key, Boolean.valueOf("1".equals(fields[1])));
            } catch (RuntimeException invalid) {
                throw new IOException("invalid vanilla landmark key on line " + (i + 1),
                        invalid);
            }
        }
        return new VanillaLandmarkVisibility(values);
    }

    private static void writeForced(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }
}
