package org.waypoints.next.model;

import java.net.IDN;
import java.util.Locale;
import java.util.Objects;

/** Stable, DNS-free endpoint representation used as the server discriminator. */
public final class ServerEndpoint {
    private final String host;
    private final int gamePort;
    private final Integer queryPort;

    public ServerEndpoint(String host, int gamePort, Integer queryPort) {
        this.host = normalizeHost(host);
        this.gamePort = requirePort(gamePort, false, "game port");
        this.queryPort = queryPort == null
                ? null : requirePort(queryPort.intValue(), true, "query port");
    }

    public static ServerEndpoint fromClient(
            String address, short gamePort, short queryPort) {
        int unsignedGamePort = Short.toUnsignedInt(gamePort);
        int unsignedQueryPort = Short.toUnsignedInt(queryPort);
        return new ServerEndpoint(stripPort(address, unsignedGamePort),
                unsignedGamePort, Integer.valueOf(unsignedQueryPort));
    }

    public static ServerEndpoint direct(String host, int gamePort) {
        return new ServerEndpoint(stripPort(host, gamePort), gamePort, null);
    }

    public String getHost() {
        return host;
    }

    public int getGamePort() {
        return gamePort;
    }

    public Integer getQueryPort() {
        return queryPort;
    }

    public String fingerprint() {
        String printableHost = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        return printableHost + ":" + gamePort;
    }

    private static String normalizeHost(String value) {
        if (value == null) throw new IllegalArgumentException("server host is required");
        String clean = value.trim();
        if (clean.startsWith("[") && clean.endsWith("]") && clean.length() > 2) {
            clean = clean.substring(1, clean.length() - 1);
        }
        if (clean.isEmpty()) throw new IllegalArgumentException("server host is required");
        if (clean.indexOf(':') < 0) clean = IDN.toASCII(clean);
        return clean.toLowerCase(Locale.ENGLISH);
    }

    private static int requirePort(int value, boolean allowZero, String label) {
        int minimum = allowZero ? 0 : 1;
        if (value < minimum || value > 65535) {
            throw new IllegalArgumentException(label + " is outside " + minimum + "..65535");
        }
        return value;
    }

    private static String stripPort(String address, int knownPort) {
        if (address == null) return null;
        String clean = address.trim();
        if (clean.startsWith("[")) {
            int closing = clean.indexOf(']');
            if (closing > 0) return clean.substring(1, closing);
        }
        int lastColon = clean.lastIndexOf(':');
        if (lastColon > 0 && clean.indexOf(':') == lastColon) {
            String suffix = clean.substring(lastColon + 1);
            try {
                if (Integer.parseInt(suffix) == knownPort) {
                    return clean.substring(0, lastColon);
                }
            } catch (NumberFormatException ignored) {
                // The colon is part of an unexpected host representation.
            }
        }
        return clean;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerEndpoint)) return false;
        ServerEndpoint that = (ServerEndpoint) other;
        return gamePort == that.gamePort && host.equals(that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, gamePort);
    }

    @Override
    public String toString() {
        return fingerprint();
    }
}
