package org.waypoints.next.integration;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;

/** Keeps the in-game command surface English-only. */
public class PlayerFacingLanguageContractTest {
    private static final Pattern CYRILLIC = Pattern.compile("[\\u0400-\\u04ff]");

    @Test
    public void staticWaypointRuntimeContainsNoCyrillicMessages() throws IOException {
        assertClassContainsNoCyrillic(
                "org/waypoints/next/integration/StaticWaypointRuntime.class");
    }

    @Test
    public void nativeManagerContainsNoCyrillicMessages() throws IOException {
        assertClassContainsNoCyrillic(
                "com/wurmonline/client/renderer/gui/WaypointManagerWindow.class");
        assertClassContainsNoCyrillic(
                "com/wurmonline/client/renderer/gui/WaypointManagerWindowBridge.class");
        assertClassContainsNoCyrillic(
                "org/waypoints/next/ui/WaypointManagerController.class");
        assertClassContainsNoCyrillic(
                "org/waypoints/next/ui/MarkerStyleEditorState.class");
        assertClassContainsNoCyrillic(
                "org/waypoints/next/ui/WaypointManagerHelpText.class");
        assertClassContainsNoCyrillic(
                "com/wurmonline/client/renderer/gui/WaypointColorPicker.class");
        assertClassContainsNoCyrillic(
                "com/wurmonline/client/renderer/gui/WaypointStyleSlider.class");
    }

    @Test
    public void nativeClusterPickerContainsNoCyrillicMessages() throws IOException {
        assertClassContainsNoCyrillic(
                "com/wurmonline/client/renderer/gui/WaypointCompassMarkerBridge.class");
        assertClassContainsNoCyrillic(
                "com/wurmonline/client/renderer/gui/WaypointClusterPickerWindow.class");
        assertClassContainsNoCyrillic(
                "com/wurmonline/client/renderer/gui/WaypointClusterPickerWindowBridge.class");
    }

    private static void assertClassContainsNoCyrillic(String resource)
            throws IOException {
        InputStream input = PlayerFacingLanguageContractTest.class
                .getClassLoader().getResourceAsStream(resource);
        if (input == null) throw new IOException(resource + " is unavailable");
        DataInputStream classFile = new DataInputStream(input);
        try {
            if (classFile.readInt() != 0xCAFEBABE) {
                throw new IOException("invalid Java class header");
            }
            classFile.readUnsignedShort();
            classFile.readUnsignedShort();
            int entries = classFile.readUnsignedShort();
            for (int index = 1; index < entries; index++) {
                int tag = classFile.readUnsignedByte();
                if (tag == 1) {
                    String constant = classFile.readUTF();
                    assertFalse(resource + " must expose English-only messages: " + constant,
                            CYRILLIC.matcher(constant).find());
                } else if (tag == 3 || tag == 4 || tag == 9 || tag == 10
                        || tag == 11 || tag == 12 || tag == 17 || tag == 18) {
                    classFile.skipBytes(4);
                } else if (tag == 5 || tag == 6) {
                    classFile.skipBytes(8);
                    index++;
                } else if (tag == 7 || tag == 8 || tag == 16
                        || tag == 19 || tag == 20) {
                    classFile.skipBytes(2);
                } else if (tag == 15) {
                    classFile.skipBytes(3);
                } else {
                    throw new IOException("unsupported constant-pool tag " + tag);
                }
            }
        } finally {
            classFile.close();
        }
    }
}
