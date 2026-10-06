package org.chamomilo.wurm.update;

/** One catalogue row, including current, absent and unchecked mods. */
public final class ModUpdate {
    private final String id;
    private final String displayName;
    private final String installedVersion;
    private final String latestVersion;
    private final String downloadUrl;
    private final boolean updateAvailable;
    private final String checkStatus;

    ModUpdate(String id, String displayName, String installedVersion,
              String latestVersion, String downloadUrl) {
        this(id, displayName, installedVersion, latestVersion, downloadUrl, true, "");
    }

    ModUpdate(String id, String displayName, String installedVersion,
              String latestVersion, String downloadUrl, boolean updateAvailable,
              String checkStatus) {
        this.id = id;
        this.displayName = displayName;
        this.installedVersion = installedVersion;
        this.latestVersion = latestVersion;
        this.downloadUrl = downloadUrl;
        this.updateAvailable = updateAvailable;
        this.checkStatus = checkStatus;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getInstalledVersion() { return installedVersion; }
    public String getLatestVersion() { return latestVersion; }
    public String getDownloadUrl() { return downloadUrl; }
    public boolean isInstalled() { return !installedVersion.isEmpty(); }
    public boolean isUpdateAvailable() { return updateAvailable; }

    public String getActionLabel() {
        if (latestVersion.isEmpty()) return "";
        if (!isInstalled()) return "INSTALL";
        return updateAvailable ? "UPDATE" : "";
    }

    public String getStatusText() {
        String installed = isInstalled() ? "Installed: " + installedVersion : "Not installed";
        if (!checkStatus.isEmpty()) return installed + " | " + checkStatus;
        return installed + " | Latest: " + latestVersion
                + (updateAvailable ? " | Update available" : isInstalled() ? " | Current" : "");
    }

    public String getNotificationText() {
        return "Wurm " + displayName + " Mod. Installed version: "
                + installedVersion + ". Available version: " + latestVersion
                + ". You can download here: " + downloadUrl;
    }
}
