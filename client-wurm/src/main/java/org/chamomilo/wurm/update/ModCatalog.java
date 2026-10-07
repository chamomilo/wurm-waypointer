package org.chamomilo.wurm.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/** Published Chamomilo mods and read-only discovery of installed, even disabled JARs. */
final class ModCatalog {
    static final String CATALOG_URL = "https://raw.githubusercontent.com/chamomilo/wurm-keybinder/main/chamomilo-mods.properties";
    private static final Logger LOGGER = Logger.getLogger("Chamomilo.UpdateCoordinator");
    private static final Pattern PUBLIC_REPO = Pattern.compile("\"full_name\"\\s*:\\s*\"(chamomilo/[A-Za-z0-9_.-]+)\"");
    private static final Pattern FILE_VERSION = Pattern.compile("-(\\d+\\.\\d+\\.\\d+)\\.jar$");
    private static final Definition[] MODS = {
        new Definition("keybinder", "Keybinder", "wurm-keybinder", "keybinder-{version}.zip",
                "org.keybinder.wurm.KeybinderMod"),
        new Definition("wurm-waypointer", "Waypointer", "wurm-waypointer", "wurm-waypointer-{version}.zip",
                "org.waypoints.next.WurmWaypointerMod"),
        new Definition("WU-third_person_view", "Third Person View", "WU-3rd-person-view",
                "WU-third_person_view-{version}.zip", "org.wuthirdpersonview.client.WUThirdPersonViewMod"),
        new Definition("wurm-highres", "High Res Icons", "wurm-high-res-icons",
                "WURM-High-Res-Icons-{version}.zip", "org.wurmhighres.client.WurmHighresMod"),
        new Definition("highres-hud", "HighRes HUD", "Wurm-HighRes-HUD", "highres-hud-{version}.zip",
                "org.highreshud.client.HighResHudMod"),
        new Definition("highres-startup", "HighRes Startup", "wurm-highres-startup",
                "highres-startup-{version}.zip", "org.highresstartup.client.HighResStartupMod"),
        new Definition("armor-material-colors", "Material Colors", "wurm-material-colors",
                "armor-material-colors-{version}.zip", "org.armormaterialcolors.client.ArmorMaterialColorsMod"),
        new Definition("idleanimations", "Idle Animations", "wurm-idle-animations",
                "idleanimations-{version}.zip", "org.wuidleanimations.client.IdleAnimationsMod")
    };

    private ModCatalog() { }

    interface Source {
        String catalogue() throws IOException;
        String publicRepositories(int page) throws IOException;
    }

    static final class PublicSource implements Source {
        public String catalogue() throws IOException { return GitHubReleaseClient.readPublic(CATALOG_URL); }
        public String publicRepositories(int page) throws IOException {
            // No authentication: this endpoint returns public repositories only.
            return GitHubReleaseClient.readPublic("https://api.github.com/users/chamomilo/repos?type=owner&per_page=100&page=" + page);
        }
    }

    static List<Definition> loadDefinitions(Source source, Path cache) {
        try {
            Properties properties = properties(source.catalogue());
            Set<String> publicRepositories = new HashSet<String>();
            for (int page = 1; page <= 5; page++) {
                Matcher matcher = PUBLIC_REPO.matcher(source.publicRepositories(page));
                int count = 0;
                while (matcher.find()) { publicRepositories.add(matcher.group(1)); count++; }
                if (count < 100) break;
            }
            List<Definition> definitions = parse(properties, publicRepositories);
            if (definitions.isEmpty()) throw new IOException("Public mod catalogue is empty");
            // Store only the validated public entries for offline startup.
            StringBuilder ids = new StringBuilder();
            for (Definition definition : definitions) {
                if (ids.length() != 0) ids.append(',');
                ids.append(definition.id);
            }
            properties.setProperty("mods", ids.toString());
            try {
                Files.createDirectories(cache.getParent());
                Path temporary = Files.createTempFile(cache.getParent(), "mod-catalog-", ".tmp");
                try {
                    try (OutputStream output = Files.newOutputStream(temporary)) { properties.store(output, "Verified public Chamomilo mods"); }
                    Files.move(temporary, cache, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temporary); }
            } catch (IOException failure) { LOGGER.fine("Cannot cache the public catalogue: " + failure); }
            LOGGER.info("Loaded public Chamomilo catalogue from GitHub: " + definitions.size() + " mods");
            return definitions;
        } catch (IOException | IllegalArgumentException failure) {
            LOGGER.warning("Cannot refresh public mod catalogue; using offline catalogue: " + failure);
            try (InputStream input = Files.newInputStream(cache)) {
                Properties properties = new Properties(); properties.load(input);
                List<Definition> definitions = parse(properties, null);
                if (!definitions.isEmpty()) return definitions;
            } catch (IOException | IllegalArgumentException ignored) { }
            return new ArrayList<Definition>(java.util.Arrays.asList(MODS));
        }
    }

    private static Properties properties(String payload) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8))) { properties.load(input); }
        return properties;
    }

    static List<Definition> parse(Properties properties, Set<String> publicRepositories) {
        if (!"1".equals(properties.getProperty("catalogVersion")))
            throw new IllegalArgumentException("Unsupported public catalogue format");
        Map<String, Definition> result = new LinkedHashMap<String, Definition>();
        for (String rawId : properties.getProperty("mods", "").split(",")) {
            String id = rawId.trim();
            String repo = properties.getProperty(id + ".repo", "").trim();
            String name = properties.getProperty(id + ".name", "").trim();
            String asset = properties.getProperty(id + ".asset", "").trim();
            String mainClass = properties.getProperty(id + ".class", "").trim();
            if (!id.matches("[A-Za-z0-9][A-Za-z0-9_.-]*") || !repo.matches("chamomilo/[A-Za-z0-9_.-]+")
                    || name.isEmpty() || name.length() > 80 || !asset.matches("[A-Za-z0-9_.{}-]+\\.zip")
                    || !asset.contains("{version}") || !mainClass.matches("[A-Za-z0-9_.$]+")) continue;
            if (publicRepositories != null && !publicRepositories.contains(repo)) continue;
            String description = properties.getProperty(id + ".description", descriptionFor(repo)).trim();
            if (description.isEmpty() || description.length() > 240
                    || description.indexOf('\n') >= 0 || description.indexOf('\r') >= 0)
                description = descriptionFor(repo);
            result.put(repo, new Definition(id, name, repo.substring("chamomilo/".length()),
                    asset, mainClass, description));
        }
        return new ArrayList<Definition>(result.values());
    }

    static List<UpdateTarget> onlineSnapshot(Path mods, List<UpdateTarget> loaded) {
        List<Definition> definitions = loadDefinitions(new PublicSource(),
                Paths.get(System.getProperty("user.home"), ".chamomilo", "mod-catalog.properties"));
        return snapshot(mods, loaded, definitions);
    }

    static UpdateTarget legacyTarget(String modName, Properties properties, String version) {
        Definition definition = definition(modName, properties, java.util.Arrays.asList(MODS));
        return definition == null ? null : definition.target(version.isEmpty() ? "unknown" : version);
    }

    static List<UpdateTarget> snapshot(Path mods, List<UpdateTarget> loaded) {
        return snapshot(mods, loaded, java.util.Arrays.asList(MODS));
    }

    static List<UpdateTarget> snapshot(Path mods, List<UpdateTarget> loaded, List<Definition> definitions) {
        Map<String, UpdateTarget> result = new LinkedHashMap<String, UpdateTarget>();
        for (Definition definition : definitions) result.put(definition.repository, definition.target(""));
        if (Files.isDirectory(mods)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(mods, "*.properties")) {
                for (Path config : files) {
                    try {
                        Properties properties = new Properties();
                        try (InputStream input = Files.newInputStream(config)) { properties.load(input); }
                        String name = config.getFileName().toString().replaceFirst("\\.properties$", "");
                        Definition definition = definition(name, properties, definitions);
                        if (definition == null) continue;
                        Path jar = installedJar(mods, name, properties.getProperty("classpath", ""));
                        if (jar == null) continue;
                        String version = properties.getProperty("version", "").trim();
                        if (version.isEmpty()) version = jarVersion(jar);
                        result.put(definition.repository, definition.target(version));
                    } catch (IOException | IllegalArgumentException ignored) {
                        // A broken optional mod must not hide the remaining catalogue.
                    }
                }
            } catch (IOException ignored) { }
        }
        // Runtime versions are authoritative, and repository identity avoids duplicate legacy IDs.
        for (UpdateTarget target : loaded) {
            // The remote public catalogue determines membership, not loaded private mod metadata.
            if (result.containsKey(target.getRepository())) {
                UpdateTarget catalogued = result.get(target.getRepository());
                String version = GitHubReleaseClient.normalizedVersion(target.getInstalledVersion()) == null
                        && GitHubReleaseClient.normalizedVersion(catalogued.getInstalledVersion()) != null
                        ? catalogued.getInstalledVersion() : target.getInstalledVersion();
                result.put(target.getRepository(), new UpdateTarget(catalogued.getId(),
                        catalogued.getDisplayName(), version, catalogued.getRepository(),
                        catalogued.getAssetTemplate(), catalogued.getDescription()));
            }
        }
        return new ArrayList<UpdateTarget>(result.values());
    }

    private static Definition definition(String name, Properties properties, List<Definition> definitions) {
        for (Definition definition : definitions) {
            if (definition.id.equalsIgnoreCase(name)
                    || definition.mainClass.equals(properties.getProperty("classname"))
                    || definition.repository.equalsIgnoreCase(properties.getProperty("updateRepo", "")))
                return definition;
        }
        return null;
    }

    private static Path installedJar(Path mods, String name, String classpath) {
        Path root = mods.toAbsolutePath().normalize();
        for (String entry : classpath.split("[,;]")) {
            String clean = entry.trim();
            if (!clean.endsWith(".jar")) continue;
            Path jar = root.resolve(name).resolve(clean).normalize();
            if (jar.startsWith(root) && Files.isRegularFile(jar)) return jar;
            jar = root.resolve(clean).normalize();
            if (jar.startsWith(root) && Files.isRegularFile(jar)) return jar;
            // The first JAR is the implementation; resource-only JARs cannot prove installation.
            return null;
        }
        return null;
    }

    private static String jarVersion(Path jar) {
        Matcher filename = FILE_VERSION.matcher(jar.getFileName().toString());
        if (filename.find()) return filename.group(1);
        try (JarFile file = new JarFile(jar.toFile())) {
            Manifest manifest = file.getManifest();
            String version = manifest == null ? null
                    : manifest.getMainAttributes().getValue("Implementation-Version");
            if (version != null && !version.trim().isEmpty()) return version.trim();
        } catch (IOException ignored) { }
        return "unknown";
    }

    static String descriptionFor(String repository) {
        if ("chamomilo/wurm-keybinder".equalsIgnoreCase(repository))
            return "Manage keybinds, action chains and your action queue.";
        if ("chamomilo/wurm-waypointer".equalsIgnoreCase(repository))
            return "Navigate with a minimap, waypoints and shared map markers.";
        if ("chamomilo/WU-3rd-person-view".equalsIgnoreCase(repository))
            return "Play with an adjustable third-person camera.";
        if ("chamomilo/wurm-high-res-icons".equalsIgnoreCase(repository))
            return "Replace item and tool icons with high-resolution artwork.";
        if ("chamomilo/Wurm-HighRes-HUD".equalsIgnoreCase(repository))
            return "Give the in-game interface sharper high-resolution textures.";
        if ("chamomilo/wurm-highres-startup".equalsIgnoreCase(repository))
            return "Refresh the startup and login screens with high-resolution artwork.";
        if ("chamomilo/wurm-material-colors".equalsIgnoreCase(repository))
            return "Show equipment materials through distinct colours and finishes.";
        if ("chamomilo/wurm-idle-animations".equalsIgnoreCase(repository))
            return "Add idle character animations while standing still.";
        if ("chamomilo/Wurm-avatar-2.0".equalsIgnoreCase(repository))
            return "Customize your character's body, appearance and animations.";
        return "A Wurm client mod; visit its project page for features and instructions.";
    }

    static final class Definition {
        final String id, name, repository, asset, mainClass, description;
        Definition(String id, String name, String repo, String asset, String mainClass) {
            this(id, name, repo, asset, mainClass, descriptionFor("chamomilo/" + repo));
        }
        Definition(String id, String name, String repo, String asset, String mainClass, String description) {
            this.id = id; this.name = name; this.repository = "chamomilo/" + repo;
            this.asset = asset; this.mainClass = mainClass;
            this.description = description;
        }
        UpdateTarget target(String version) {
            return new UpdateTarget(id, name, version, repository, asset, description);
        }
    }
}
