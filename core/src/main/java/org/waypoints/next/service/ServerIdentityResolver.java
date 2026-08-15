package org.waypoints.next.service;

import org.waypoints.next.model.CapturedServerSelection;
import org.waypoints.next.model.ServerIdentity;

import java.util.Locale;

/** Reconciles the pre-login endpoint snapshot with World.getServerName(). */
public final class ServerIdentityResolver {
    public ServerIdentity resolve(
            String worldServerName, CapturedServerSelection selection) {
        String shortName = clean(worldServerName);
        if (selection == null) {
            return ServerIdentity.of(null, "", shortName,
                    ServerIdentity.Resolution.UNRESOLVED_NO_ENDPOINT);
        }
        if (selection.getSource() == CapturedServerSelection.Source.DIRECT_CONNECT) {
            return ServerIdentity.of(selection.getEndpoint(), selection.getFullName(), shortName,
                    ServerIdentity.Resolution.UNRESOLVED_DIRECT_CONNECT);
        }
        if (selection.getSource() == CapturedServerSelection.Source.SERVER_TRANSFER) {
            if (shortName.isEmpty()) {
                return ServerIdentity.of(selection.getEndpoint(), selection.getFullName(), "",
                        ServerIdentity.Resolution.UNRESOLVED_WORLD_NAME);
            }
            return ServerIdentity.of(selection.getEndpoint(), selection.getFullName(), shortName,
                    ServerIdentity.Resolution.RESOLVED);
        }
        if (shortName.isEmpty()) {
            return ServerIdentity.of(selection.getEndpoint(), selection.getFullName(), "",
                    ServerIdentity.Resolution.UNRESOLVED_WORLD_NAME);
        }
        ServerIdentity.Resolution resolution = matchesShard(selection.getFullName(), shortName)
                ? ServerIdentity.Resolution.RESOLVED
                : ServerIdentity.Resolution.UNRESOLVED_NAME_MISMATCH;
        return ServerIdentity.of(selection.getEndpoint(), selection.getFullName(), shortName,
                resolution);
    }

    public boolean matchesShard(String fullName, String shortName) {
        String full = clean(fullName).toLowerCase(Locale.ENGLISH);
        String shard = clean(shortName).toLowerCase(Locale.ENGLISH);
        if (full.isEmpty() || shard.isEmpty()) return false;
        return full.equals(shard) || full.endsWith(" - " + shard)
                || full.endsWith("-" + shard);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
