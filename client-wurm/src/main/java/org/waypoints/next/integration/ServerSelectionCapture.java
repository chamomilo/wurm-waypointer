package org.waypoints.next.integration;

import org.waypoints.next.model.CapturedServerSelection;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reflection boundary around JavaFX and SteamServerFX selection state. */
public final class ServerSelectionCapture {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.ServerIdentity");

    private ServerSelectionCapture() {
    }

    public static void rememberSelectedServer(Object tableView) {
        try {
            if (tableView == null) return;
            Object selectionModel = invoke(tableView, "getSelectionModel");
            Object selected = invoke(selectionModel, "getSelectedItem");
            if (selected == null) return;
            String fullName = String.valueOf(invoke(selected, "getServerName"));
            String address = String.valueOf(invoke(selected, "getIpAdress"));
            short gamePort = ((Number) invoke(selected, "getPort")).shortValue();
            short queryPort = ((Number) invoke(selected, "getQueryPort")).shortValue();
            CapturedServerSelection snapshot = CapturedServerSelection.steamBrowser(
                    fullName, address, gamePort, queryPort);
            WurmWaypointerRuntime.capture(snapshot);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE,
                    "Unable to capture the selected Wurm Steam server; identity stays unresolved",
                    failure);
        }
    }

    public static void rememberDirect(String host, int gamePort) {
        try {
            WurmWaypointerRuntime.capture(CapturedServerSelection.direct(host, gamePort));
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Unable to capture direct-connect endpoint", failure);
        }
    }

    public static void rememberDirectAddress(String address) {
        try {
            if (address == null) return;
            String clean = address.trim();
            int colon = clean.lastIndexOf(':');
            if (colon <= 0 || colon + 1 >= clean.length()) return;
            int port = Integer.parseInt(clean.substring(colon + 1));
            rememberDirect(clean.substring(0, colon), port);
        } catch (Throwable failure) {
            LOGGER.log(Level.FINE, "Unable to parse direct-connect endpoint", failure);
        }
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        if (target == null) return null;
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }
}
