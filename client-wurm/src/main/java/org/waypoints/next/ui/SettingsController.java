package org.waypoints.next.ui;
import java.util.Properties;
public interface SettingsController {
    Properties values();
    void save(Properties values);
    String status();
}
