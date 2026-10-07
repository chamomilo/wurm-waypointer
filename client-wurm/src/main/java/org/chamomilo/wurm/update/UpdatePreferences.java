package org.chamomilo.wurm.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A user preference, independent of catalogue membership, mod versions and ZIP contents. */
public final class UpdatePreferences {
    private static final Logger LOG = Logger.getLogger("Chamomilo.UpdateCoordinator");
    private final Path file;
    private boolean skipNextStart;

    private static final class Shared {
        static final UpdatePreferences INSTANCE = new UpdatePreferences(
                Paths.get(System.getProperty("user.home"), ".chamomilo", "updater.properties"));
    }

    public static UpdatePreferences shared() { return Shared.INSTANCE; }

    public UpdatePreferences(Path file) {
        this.file = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(this.file)) return;
        try (InputStream input = Files.newInputStream(this.file)) {
            Properties properties = new Properties(); properties.load(input);
            skipNextStart = Boolean.parseBoolean(properties.getProperty("dontShowOnNextStart", "false"));
        } catch (IOException | IllegalArgumentException failure) {
            LOG.log(Level.WARNING, "Cannot read mod update preference; showing the first-start window", failure);
        }
    }

    public synchronized boolean isSkipNextStart() { return skipNextStart; }

    public synchronized void setSkipNextStart(boolean value) throws IOException {
        if (value == skipNextStart) return;
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "updater-", ".tmp");
        try {
            Properties properties = new Properties();
            properties.setProperty("dontShowOnNextStart", Boolean.toString(value));
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Chamomilo mod updates: startup visibility");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailure) {
                // Windows can reject atomic replacement even when an ordinary replacement works.
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException failure) {
                    failure.addSuppressed(atomicFailure);
                    throw failure;
                }
            }
            skipNextStart = value;
        } finally { Files.deleteIfExists(temporary); }
    }
}
