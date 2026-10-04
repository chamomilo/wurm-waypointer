package com.wurmonline.client.renderer.gui;

import org.waypoints.next.map.MiniMapState;
import org.waypoints.next.map.ServerMapProfile;
import com.wurmonline.client.settings.WindowPosition;

/** Always-on compact map with shared layers and a wheel-controlled range. */
final class MiniMapWindow extends MiniMapCanvas implements WindowSerializer {
    static final int MAP_SIZE = 300;
    static final int WINDOW_WIDTH = MAP_SIZE;
    static final int WINDOW_HEIGHT = MAP_SIZE;

    private String profileId = "";

    MiniMapWindow(MiniMapState settings) {
        super(settings, MAP_SIZE);
    }

    void sharedSettingsChanged() {
        settingsChanged();
    }

    void updateProfile(ServerMapProfile profile) {
        if (profile == null || profile.getId().equals(profileId)) return;
        profileId = profile.getId();
        String displayName = profile.getDisplayName() == null
                ? "" : profile.getDisplayName().trim().replace(' ', '-');
        setTitleLabel("Mini Map of: " + displayName);
    }

    @Override public WindowPosition createPositionHints() {
        return new WindowPosition(x, y, width, height, 0);
    }

    @Override public void restorePositionHints(WindowPosition position) {
        if (position == null) return;
        int nextX = position.x;
        int nextY = position.y;
        HeadsUpDisplay hud = WurmComponent.hud;
        if (hud != null) {
            int oldRightGap = hud.getWidth() - position.x - position.width;
            if (oldRightGap >= 0 && oldRightGap <= 32) {
                nextX = hud.getWidth() - width;
            }
            nextX = Math.max(0, Math.min(nextX, hud.getWidth() - width));
            nextY = Math.max(0, Math.min(nextY, hud.getHeight() - height));
        }
        setPosition(nextX, nextY);
    }
}
