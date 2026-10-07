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
    private final String projectUrl;
    private final String description;

    ModUpdate(String id, String displayName, String installedVersion,
              String latestVersion, String downloadUrl) {
        this(id, displayName, installedVersion, latestVersion, downloadUrl, true, "");
    }

    ModUpdate(String id, String displayName, String installedVersion,
              String latestVersion, String downloadUrl, boolean updateAvailable,
              String checkStatus) {
        this(id, displayName, installedVersion, latestVersion, downloadUrl,
                updateAvailable, checkStatus, projectUrlFrom(downloadUrl),
                ModCatalog.descriptionFor(repositoryFrom(downloadUrl)));
    }

    ModUpdate(String id, String displayName, String installedVersion,
              String latestVersion, String downloadUrl, boolean updateAvailable,
              String checkStatus, String projectUrl, String description) {
        this.id = id;
        this.displayName = displayName;
        this.installedVersion = installedVersion;
        this.latestVersion = latestVersion;
        this.downloadUrl = downloadUrl;
        this.updateAvailable = updateAvailable;
        this.checkStatus = checkStatus;
        this.projectUrl = projectUrl;
        this.description = description;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getInstalledVersion() { return installedVersion; }
    public String getLatestVersion() { return latestVersion; }
    public String getDownloadUrl() { return downloadUrl; }
    public boolean isInstalled() { return !installedVersion.isEmpty(); }
    public boolean isUpdateAvailable() { return updateAvailable; }
    public String getProjectUrl() { return projectUrl; }
    public String getDescription() { return description; }
    public String getInstalledText() {
        return isInstalled() ? "Installed: " + installedVersion : "not installed";
    }

    public boolean canDownload() { return !isInstalled() || updateAvailable; }

    public boolean isLatest() {
        String installed = GitHubReleaseClient.normalizedVersion(installedVersion);
        String latest = GitHubReleaseClient.normalizedVersion(latestVersion);
        return installed != null && latest != null
                && GitHubReleaseClient.compareVersions(installed, latest) >= 0;
    }

    public String getActionLabel() {
        if (canDownload()) return "DOWNLOAD";
        return isLatest() ? "LATEST" : "UNAVAILABLE";
    }

    public String getReleaseText() {
        if (!checkStatus.isEmpty()) return checkStatus;
        if (updateAvailable) return "New version available: " + latestVersion;
        return isInstalled() ? "" : latestVersion.isEmpty() ? "" : "Available: " + latestVersion;
    }

    public String getStatusText() {
        String installed = getInstalledText();
        if (!checkStatus.isEmpty()) return installed + " | " + checkStatus;
        return installed + " | Latest: " + latestVersion
                + (updateAvailable ? " | Update available" : isInstalled() ? " | Current" : "");
    }

    public String getNotificationText() {
        return "Wurm " + displayName + " Mod. Installed version: "
                + installedVersion + ". Available version: " + latestVersion
                + ". You can download here: " + downloadUrl;
    }

    private static String repositoryFrom(String url) {
        String prefix = "https://github.com/";
        if (url == null || !url.startsWith(prefix)) return "";
        String[] parts = url.substring(prefix.length()).split("/");
        return parts.length >= 2 ? parts[0] + "/" + parts[1] : "";
    }

    private static String projectUrlFrom(String url) {
        String repository = repositoryFrom(url);
        return repository.isEmpty() ? "" : "https://github.com/" + repository;
    }
}
