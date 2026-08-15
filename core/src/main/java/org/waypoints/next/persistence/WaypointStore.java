package org.waypoints.next.persistence;

import org.waypoints.next.validation.WaypointLimits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Locked, verified store with fsync, atomic replacement, backup and recovery. */
public final class WaypointStore {
    interface FileMover {
        void move(Path source, Path target, boolean atomic) throws IOException;
    }

    private static final Object JVM_FILE_LOCK = new Object();
    private static final FileMover DEFAULT_MOVER = new FileMover() {
        @Override public void move(Path source, Path target, boolean atomic) throws IOException {
            if (atomic) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    };

    private final Path file;
    private final WaypointFormatCodec codec;
    private final FileMover mover;
    private volatile boolean recoveredFromBackup;
    private volatile boolean usedAtomicFallback;
    private volatile Path loadedSource;

    public WaypointStore(Path file, WaypointFormatCodec codec) {
        this(file, codec, DEFAULT_MOVER);
    }

    WaypointStore(Path file, WaypointFormatCodec codec, FileMover mover) {
        if (file == null || codec == null || mover == null) {
            throw new IllegalArgumentException("file, codec and mover are required");
        }
        this.file = file;
        this.codec = codec;
        this.mover = mover;
    }

    public Path getFile() { return file; }
    public Path backupFile() { return file.resolveSibling(file.getFileName() + ".bak"); }
    public boolean wasRecoveredFromBackup() { return recoveredFromBackup; }
    public boolean usedAtomicFallback() { return usedAtomicFallback; }

    public WaypointDocument load() throws IOException {
        synchronized (JVM_FILE_LOCK) {
            ensureParent();
            try (FileChannel lockChannel = FileChannel.open(lockFile(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = lockChannel.lock()) {
                return readRecoveringUnlocked();
            }
        }
    }

    public void save(WaypointDocument document) throws IOException {
        byte[] bytes = codec.encode(document);
        synchronized (JVM_FILE_LOCK) {
            ensureParent();
            try (FileChannel lockChannel = FileChannel.open(lockFile(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = lockChannel.lock()) {
                saveUnlocked(document, bytes);
            }
        }
    }

    private WaypointDocument readRecoveringUnlocked() throws IOException {
        recoveredFromBackup = false;
        usedAtomicFallback = false;
        loadedSource = null;
        if (!Files.isRegularFile(file)) return WaypointDocument.empty();
        try {
            WaypointDocument document = read(file);
            loadedSource = file;
            return document;
        } catch (IOException | RuntimeException primary) {
            Path backup = backupFile();
            if (!Files.isRegularFile(backup)) throw asIo("unable to read waypoint store", primary);
            try {
                WaypointDocument document = read(backup);
                recoveredFromBackup = true;
                loadedSource = backup;
                return document;
            } catch (IOException | RuntimeException backupFailure) {
                IOException failure = asIo("unable to read waypoint store or backup", primary);
                failure.addSuppressed(backupFailure);
                throw failure;
            }
        }
    }

    private WaypointDocument read(Path source) throws IOException {
        long size = Files.size(source);
        if (size > WaypointLimits.MAX_FILE_BYTES) throw new IOException("waypoint store is too large");
        return codec.decode(Files.readAllBytes(source));
    }

    private void saveUnlocked(WaypointDocument document, byte[] bytes) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        Path backup = backupFile();
        boolean hadPrevious = Files.isRegularFile(file);
        boolean savingRecovered = recoveredFromBackup && loadedSource != null
                && Files.isRegularFile(loadedSource);
        usedAtomicFallback = false;
        writeForced(temp, bytes);
        try {
            // Never replace the known-good backup with the corrupt primary after recovery.
            if (hadPrevious && !savingRecovered) copyForced(file, backup);
            try {
                mover.move(temp, file, true);
            } catch (AtomicMoveNotSupportedException unsupported) {
                usedAtomicFallback = true;
                mover.move(temp, file, false);
            }
            WaypointDocument verified = read(file);
            byte[] verifiedBytes = codec.encode(verified);
            if (!java.util.Arrays.equals(bytes, verifiedBytes)) {
                throw new IOException("waypoint save verification failed");
            }
            recoveredFromBackup = false;
            loadedSource = file;
        } catch (IOException | RuntimeException failure) {
            try {
                if (hadPrevious && savingRecovered) copyForced(loadedSource, file);
                else if (hadPrevious && Files.isRegularFile(backup)) copyForced(backup, file);
                else if (!hadPrevious) Files.deleteIfExists(file);
            } catch (Throwable rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw asIo("unable to save waypoint store", failure);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void writeForced(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void copyForced(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private void ensureParent() throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
    }

    private Path lockFile() {
        return file.resolveSibling(file.getFileName() + ".lock");
    }

    private static IOException asIo(String message, Throwable cause) {
        return cause instanceof IOException ? (IOException) cause : new IOException(message, cause);
    }
}
