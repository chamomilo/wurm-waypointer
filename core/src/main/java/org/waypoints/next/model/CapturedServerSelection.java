package org.waypoints.next.model;

/** Immutable snapshot captured before the Wurm game connection starts. */
public final class CapturedServerSelection {
    public enum Source {
        STEAM_BROWSER,
        DIRECT_CONNECT,
        SERVER_TRANSFER
    }

    private final Source source;
    private final String fullName;
    private final ServerEndpoint endpoint;

    private CapturedServerSelection(Source source, String fullName, ServerEndpoint endpoint) {
        if (source == null) throw new IllegalArgumentException("selection source is required");
        if (endpoint == null) throw new IllegalArgumentException("server endpoint is required");
        this.source = source;
        this.fullName = clean(fullName);
        this.endpoint = endpoint;
    }

    public static CapturedServerSelection steamBrowser(
            String fullName, String address, short gamePort, short queryPort) {
        return new CapturedServerSelection(Source.STEAM_BROWSER, fullName,
                ServerEndpoint.fromClient(address, gamePort, queryPort));
    }

    public static CapturedServerSelection direct(String host, int gamePort) {
        return new CapturedServerSelection(Source.DIRECT_CONNECT, "",
                ServerEndpoint.direct(host, gamePort));
    }

    public static CapturedServerSelection transfer(String host, int gamePort) {
        return new CapturedServerSelection(Source.SERVER_TRANSFER, "",
                ServerEndpoint.direct(host, gamePort));
    }

    public Source getSource() {
        return source;
    }

    public String getFullName() {
        return fullName;
    }

    public ServerEndpoint getEndpoint() {
        return endpoint;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
