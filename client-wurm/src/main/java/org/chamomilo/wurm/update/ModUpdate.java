package org.chamomilo.wurm.update;

/** One installed Chamomilo mod for which GitHub has a newer stable release. */
public final class ModUpdate {
    private final String id;
    private final String displayName;
    private final String installedVersion;
    private final String latestVersion;
    private final String downloadUrl;

    ModUpdate(String id, String displayName, String installedVersion,
              String latestVersion, String downloadUrl) {
        this.id = id;
        this.displayName = displayName;
        this.installedVersion = installedVersion;
        this.latestVersion = latestVersion;
        this.downloadUrl = downloadUrl;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getInstalledVersion() { return installedVersion; }
    public String getLatestVersion() { return latestVersion; }
    public String getDownloadUrl() { return downloadUrl; }

    public String getNotificationText() {
        return "Wurm " + displayName + " Mod. Installed version: "
                + installedVersion + ". Available version: " + latestVersion
                + ". You can download here: " + downloadUrl;
    }
}
