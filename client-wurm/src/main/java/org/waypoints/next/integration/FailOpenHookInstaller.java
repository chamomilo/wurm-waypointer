package org.waypoints.next.integration;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Installs independent client hooks without making a failed probe fatal. */
public final class FailOpenHookInstaller {
    public interface HookOperation {
        void install() throws Exception;
    }

    private final Logger logger;

    public FailOpenHookInstaller(Logger logger) {
        this.logger = logger;
    }

    public boolean install(String name, HookOperation operation) {
        try {
            operation.install();
            logger.info("Wurm Waypointer hook installed: " + name);
            return true;
        } catch (Throwable failure) {
            logger.log(Level.WARNING,
                    "Wurm Waypointer hook is unavailable and was disabled: " + name
                            + "; " + failure.getClass().getName() + ": "
                            + String.valueOf(failure.getMessage()),
                    failure);
            return false;
        }
    }
}
