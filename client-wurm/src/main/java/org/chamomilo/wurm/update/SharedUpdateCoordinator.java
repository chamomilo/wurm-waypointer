package org.chamomilo.wurm.update;

import org.gotti.wurmunlimited.modloader.interfaces.ModEntry;
import org.gotti.wurmunlimited.modloader.interfaces.Versioned;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Process-wide updater embedded verbatim in every Chamomilo client mod.
 * With sharedClassLoader=true, the first mod loaded owns this class and every
 * later copy resolves to the same static registry.
 */
public final class SharedUpdateCoordinator {
    public static final int PROTOCOL_VERSION = 1;
    public static final String PROVIDER = "chamomilo";
    private static final Logger LOGGER = Logger.getLogger("Chamomilo.UpdateCoordinator");
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9_.-]*$");
    private static final Pattern REPOSITORY = Pattern.compile(
            "^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Registry REGISTRY = new Registry();
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private SharedUpdateCoordinator() { }

    public interface Host {
        void updatesReady(List<ModUpdate> updates);
        void checkFailed(String repository, Throwable failure);
    }

    /** Returns true only for the first mod that claims the process-wide host role. */
    public static boolean registerHost(String modId, Host host) {
        return REGISTRY.registerHost(modId, host);
    }

    /** Feed this from ModListener.modInitialized; unrelated mods are ignored. */
    public static void modInitialized(ModEntry<?> entry) {
        REGISTRY.observe(entry);
    }

    /** Starts one aggregate lookup after every ModListener callback has run. */
    public static boolean startOnce() {
        final Host host = REGISTRY.owner();
        if (host == null || !STARTED.compareAndSet(false, true)) return false;
        final List<UpdateTarget> targets = REGISTRY.snapshot();
        Thread thread = new Thread(new Runnable() {
            @Override public void run() { checkAll(host, targets); }
        }, "Chamomilo mod update coordinator");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private static void checkAll(Host host, List<UpdateTarget> targets) {
        if (targets.isEmpty()) {
            deliver(host, Collections.<ModUpdate>emptyList());
            return;
        }

        final Map<String, List<UpdateTarget>> byRepository =
                new LinkedHashMap<String, List<UpdateTarget>>();
        for (UpdateTarget target : targets) {
            List<UpdateTarget> group = byRepository.get(target.getRepository());
            if (group == null) {
                group = new ArrayList<UpdateTarget>();
                byRepository.put(target.getRepository(), group);
            }
            group.add(target);
        }

        int workers = Math.min(4, byRepository.size());
        ExecutorService executor = Executors.newFixedThreadPool(workers, new ThreadFactory() {
            private int number;
            @Override public synchronized Thread newThread(Runnable operation) {
                Thread thread = new Thread(operation,
                        "Chamomilo GitHub update check " + (++number));
                thread.setDaemon(true);
                return thread;
            }
        });
        List<Future<List<ModUpdate>>> futures = new ArrayList<Future<List<ModUpdate>>>();
        for (final Map.Entry<String, List<UpdateTarget>> group : byRepository.entrySet()) {
            futures.add(executor.submit(new Callable<List<ModUpdate>>() {
                @Override public List<ModUpdate> call() throws Exception {
                    GitHubReleaseClient client = new GitHubReleaseClient();
                    GitHubReleaseClient.ReleaseSnapshot release =
                            client.readLatest(group.getKey());
                    List<ModUpdate> updates = new ArrayList<ModUpdate>();
                    for (UpdateTarget target : group.getValue()) {
                        ModUpdate update = GitHubReleaseClient.findUpdate(target, release);
                        if (update != null) updates.add(update);
                    }
                    return updates;
                }
            }));
        }
        executor.shutdown();

        List<ModUpdate> updates = new ArrayList<ModUpdate>();
        int index = 0;
        for (Map.Entry<String, List<UpdateTarget>> group : byRepository.entrySet()) {
            try {
                updates.addAll(futures.get(index).get());
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                reportFailure(host, group.getKey(), failure);
                break;
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                reportFailure(host, group.getKey(), cause);
            } catch (Throwable failure) {
                reportFailure(host, group.getKey(), failure);
            }
            index++;
        }
        Collections.sort(updates, new Comparator<ModUpdate>() {
            @Override public int compare(ModUpdate left, ModUpdate right) {
                int name = left.getDisplayName().compareToIgnoreCase(right.getDisplayName());
                return name != 0 ? name : left.getId().compareTo(right.getId());
            }
        });
        deliver(host, Collections.unmodifiableList(updates));
    }

    private static void deliver(Host host, List<ModUpdate> updates) {
        try {
            host.updatesReady(updates);
        } catch (Throwable failure) {
            LOGGER.log(Level.WARNING, "Update host rejected aggregate results", failure);
        }
    }

    private static void reportFailure(Host host, String repository, Throwable failure) {
        try {
            host.checkFailed(repository, failure);
        } catch (Throwable callbackFailure) {
            LOGGER.log(Level.FINE, "Update host rejected a failure report", callbackFailure);
        }
    }

    static UpdateTarget targetFrom(ModEntry<?> entry) {
        if (entry == null) return null;
        Properties properties = entry.getProperties();
        if (properties == null
                || !PROVIDER.equalsIgnoreCase(trim(properties.getProperty("updateProvider"))))
            return null;
        if (!Integer.toString(PROTOCOL_VERSION).equals(
                trim(properties.getProperty("updateCoordinatorProtocol")))) {
            LOGGER.warning("Ignoring update metadata with a different coordinator protocol: "
                    + entry.getName());
            return null;
        }

        String id = trim(properties.getProperty("updateId"));
        String repository = trim(properties.getProperty("updateRepo"));
        String asset = trim(properties.getProperty("updateAsset"));
        String name = trim(properties.getProperty("updateName"));
        String version = installedVersion(entry, properties);
        if (name.isEmpty()) name = trim(entry.getName());
        if (!ID.matcher(id).matches() || !REPOSITORY.matcher(repository).matches()
                || asset.isEmpty() || !asset.contains("{version}") || name.isEmpty()
                || version.isEmpty()) {
            LOGGER.warning("Ignoring incomplete update metadata for mod: " + entry.getName());
            return null;
        }
        return new UpdateTarget(id, name, version, repository, asset);
    }

    private static String installedVersion(ModEntry<?> entry, Properties properties) {
        Object mod = entry.getWurmMod();
        if (mod instanceof Versioned) {
            String value = trim(((Versioned) mod).getVersion());
            if (!value.isEmpty()) return value;
        }
        return trim(properties.getProperty("version"));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Registry {
        private final Map<String, UpdateTarget> targets =
                new LinkedHashMap<String, UpdateTarget>();
        private Host owner;
        private String ownerId;

        synchronized boolean registerHost(String modId, Host candidate) {
            if (candidate == null) throw new IllegalArgumentException("Update host is missing");
            String cleanId = trim(modId);
            if (!ID.matcher(cleanId).matches())
                throw new IllegalArgumentException("Invalid update host id: " + modId);
            if (owner != null) return false;
            owner = candidate;
            ownerId = cleanId;
            LOGGER.info("Update coordinator is owned by " + ownerId);
            return true;
        }

        synchronized void observe(ModEntry<?> entry) {
            UpdateTarget target = targetFrom(entry);
            if (target != null && !targets.containsKey(target.getId()))
                targets.put(target.getId(), target);
        }

        synchronized Host owner() { return owner; }

        synchronized List<UpdateTarget> snapshot() {
            return new ArrayList<UpdateTarget>(targets.values());
        }

        synchronized String ownerId() { return ownerId; }
    }
}
