package org.waypoints.next.integration;

import com.wurmonline.client.game.World;
import com.wurmonline.client.sound.SoundEngine;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Tick-driven short bell phrases; no sleeping or sound work occurs off the client thread. */
final class ArchaeologyChimePlayer {
    private static final long NOTE_INTERVAL_NANOS = 180_000_000L;
    private static final String[] READY = {
            "sound.bell.handbell", "sound.bell.dong.2", "sound.bell.dong.4"
    };
    private static final String[] SUCCESS = {
            "sound.bell.dong.1", "sound.bell.dong.3", "sound.bell.dong.5"
    };

    private final Logger logger;
    private final Queue<String[]> phrases = new ArrayDeque<String[]>();
    private String[] active;
    private int note;
    private long nextNoteNanos;

    ArchaeologyChimePlayer(Logger logger) { this.logger = logger; }

    synchronized void enqueue(ArchaeologyRuntime.SoundCue cue) {
        if (cue == null) return;
        phrases.add(cue == ArchaeologyRuntime.SoundCue.REPORT_READY
                ? READY : SUCCESS);
    }

    /** Reuses the melodic rising phrase for the final Loot Map dig stage. */
    synchronized void enqueueLootMapDig() {
        phrases.add(READY);
    }

    synchronized void tick(World world) {
        if (active == null) {
            active = phrases.poll();
            note = 0;
            nextNoteNanos = 0L;
        }
        if (active == null || world == null) return;
        long now = System.nanoTime();
        if (nextNoteNanos != 0L && now < nextNoteNanos) return;
        try {
            SoundEngine<?> sound = world.getSoundEngine();
            if (sound != null && SoundEngine.getPlayerPosition() != null) {
                sound.play(active[note], SoundEngine.getPlayerPosition(),
                        1.0f, 1.0f, 1.0f, false, false);
            }
        } catch (Throwable failure) {
            logger.log(Level.FINE, "Archaeology chime failed open", failure);
            active = null;
            return;
        }
        note++;
        if (note >= active.length) {
            active = null;
            nextNoteNanos = now + NOTE_INTERVAL_NANOS;
        } else {
            nextNoteNanos = now + NOTE_INTERVAL_NANOS;
        }
    }

    synchronized void clear() {
        phrases.clear();
        active = null;
        note = 0;
        nextNoteNanos = 0L;
    }
}
