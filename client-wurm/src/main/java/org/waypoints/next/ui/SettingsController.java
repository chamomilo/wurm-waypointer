package org.waypoints.next.ui;
import java.util.Properties;
import java.util.Map;
import java.util.Collections;
import org.waypoints.next.surroundings.CreatureHighlightRule;
public interface SettingsController {
    default int detectedMeteors(){return 0;}
    Properties values();
    void save(Properties values);
    String status();
    /** Immutable snapshot of currently loaded matches; independent of outline switches. */
    default Map<CreatureHighlightRule,Integer> detectedCreatures() { return Collections.emptyMap(); }
}
