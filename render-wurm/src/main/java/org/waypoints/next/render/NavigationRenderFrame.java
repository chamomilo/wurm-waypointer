package org.waypoints.next.render;

import com.wurmonline.client.game.World;
import org.waypoints.next.navigation.NavigationSnapshot;

/** Stable client frame: live player state plus an atomically replaced core snapshot. */
public final class NavigationRenderFrame {
    private final World world;
    private volatile NavigationSnapshot snapshot;

    NavigationRenderFrame(World world, NavigationSnapshot snapshot) {
        this.world = world;
        this.snapshot = snapshot;
    }

    public World getWorld() { return world; }
    public NavigationSnapshot getSnapshot() { return snapshot; }

    void update(NavigationSnapshot value) {
        snapshot = value;
    }
}
