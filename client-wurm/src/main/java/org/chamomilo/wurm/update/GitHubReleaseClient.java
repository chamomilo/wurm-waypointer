package org.chamomilo.wurm.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small dependency-free client for GitHub's latest stable Release endpoint. */
final class GitHubReleaseClient {
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final Pattern REPOSITORY = Pattern.compile(
            "^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern TAG_NAME = Pattern.compile(
            "\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern DOWNLOAD_URL = Pattern.compile(
            "\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"\\\\]+)\\\"");
    private static final Pattern VERSION = Pattern.compile(
            "^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)$");

    interface ReleaseSource {
        String read(String repository) throws IOException;
    }

    private final ReleaseSource source;

    GitHubReleaseClient() {
        this(new HttpReleaseSource());
    }

    GitHubReleaseClient(ReleaseSource source) {
        if (source == null) throw new IllegalArgumentException("Release source is missing");
        this.source = source;
    }

    ReleaseSnapshot readLatest(String repository) throws IOException {
        validateRepository(repository);
        return ReleaseSnapshot.fromPayload(source.read(repository));
    }

    static ModUpdate findUpdate(UpdateTarget target, ReleaseSnapshot release) {
        if (target == null || release == null) return null;
        String latestVersion = normalizedVersion(release.getTag());
        String installedVersion = normalizedVersion(target.getInstalledVersion());
        if (latestVersion == null || installedVersion == null
                || compareVersions(latestVersion, installedVersion) <= 0) return null;

        String expectedAsset = target.getAssetTemplate().replace("{version}", latestVersion);
        String repository = target.getRepository();
        String downloadUrl = latestReleasePage(repository);
        String requiredPrefix = "https://github.com/" + repository + "/releases/download/";
        String requiredSuffix = "/" + expectedAsset;
        for (String candidate : release.getDownloadUrls()) {
            if (candidate.startsWith(requiredPrefix) && candidate.endsWith(requiredSuffix)) {
                downloadUrl = candidate;
                break;
            }
        }
        return new ModUpdate(target.getId(), target.getDisplayName(),
                installedVersion, latestVersion, downloadUrl);
    }

    static String latestReleasePage(String repository) {
        validateRepository(repository);
        return "https://github.com/" + repository + "/releases/latest";
    }

    private static void validateRepository(String repository) {
        if (repository == null || !REPOSITORY.matcher(repository).matches())
            throw new IllegalArgumentException("Invalid GitHub repository: " + repository);
    }

    private static String normalizedVersion(String value) {
        Matcher matcher = VERSION.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) return null;
        return withoutLeadingZeroes(matcher.group(1)) + "."
                + withoutLeadingZeroes(matcher.group(2)) + "."
                + withoutLeadingZeroes(matcher.group(3));
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int index = 0; index < 3; index++) {
            if (a[index].length() != b[index].length())
                return a[index].length() < b[index].length() ? -1 : 1;
            int comparison = a[index].compareTo(b[index]);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static String withoutLeadingZeroes(String value) {
        int index = 0;
        while (index < value.length() - 1 && value.charAt(index) == '0') index++;
        return value.substring(index);
    }

    static final class ReleaseSnapshot {
        private final String tag;
        private final List<String> downloadUrls;

        private ReleaseSnapshot(String tag, List<String> downloadUrls) {
            this.tag = tag;
            this.downloadUrls = downloadUrls;
        }

        static ReleaseSnapshot fromPayload(String payload) {
            String json = payload == null ? "" : payload;
            Matcher tagField = TAG_NAME.matcher(json);
            if (!tagField.find()) return null;
            List<String> urls = new ArrayList<String>();
            Matcher urlField = DOWNLOAD_URL.matcher(json);
            while (urlField.find()) urls.add(urlField.group(1));
            return new ReleaseSnapshot(tagField.group(1).trim(),
                    Collections.unmodifiableList(urls));
        }

        String getTag() { return tag; }
        List<String> getDownloadUrls() { return downloadUrls; }
    }

    private static final class HttpReleaseSource implements ReleaseSource {
        @Override public String read(String repository) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) new URL(
                    "https://api.github.com/repos/" + repository + "/releases/latest")
                    .openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "Chamomilo-Wurm-Mod-Updater");
            try {
                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK)
                    throw new IOException("GitHub release check returned HTTP " + status);
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_RESPONSE_BYTES)
                            throw new IOException("GitHub release response is too large");
                        output.write(buffer, 0, read);
                    }
                    return new String(output.toByteArray(), StandardCharsets.UTF_8);
                }
            } finally {
                connection.disconnect();
            }
        }
    }
}
