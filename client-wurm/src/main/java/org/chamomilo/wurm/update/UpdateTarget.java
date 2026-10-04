package org.chamomilo.wurm.update;

/** Immutable installed-mod metadata collected before the HUD becomes ready. */
final class UpdateTarget {
    private final String id;
    private final String displayName;
    private final String installedVersion;
    private final String repository;
    private final String assetTemplate;

    UpdateTarget(String id, String displayName, String installedVersion,
                 String repository, String assetTemplate) {
        this.id = id;
        this.displayName = displayName;
        this.installedVersion = installedVersion;
        this.repository = repository;
        this.assetTemplate = assetTemplate;
    }

    String getId() { return id; }
    String getDisplayName() { return displayName; }
    String getInstalledVersion() { return installedVersion; }
    String getRepository() { return repository; }
    String getAssetTemplate() { return assetTemplate; }
}
