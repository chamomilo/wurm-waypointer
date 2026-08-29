package org.waypoints.next.integration;

import com.wurmonline.shared.constants.PlayerAction;

/** Resolves both ordinary server action labels and built-in client binds. */
final class WurmPlayerActionName {
    private WurmPlayerActionName() {
    }

    static String resolve(PlayerAction action) {
        if (action == null) return null;
        String name = clean(action.getName());
        if (name.isEmpty()) name = clean(action.getBind());
        return name.isEmpty() ? null : name;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
