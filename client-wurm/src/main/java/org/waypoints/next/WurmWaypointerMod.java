package org.waypoints.next;

import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import org.chamomilo.wurm.update.SharedUpdateCoordinator;
import org.chamomilo.wurm.update.SharedUpdateHooks;
import org.chamomilo.wurm.update.SharedLanguageCoordinator;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ModEntry;
import org.gotti.wurmunlimited.modloader.interfaces.ModListener;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;
import org.waypoints.next.integration.CompassHookBridge;
import org.waypoints.next.integration.FailOpenHookInstaller;
import org.waypoints.next.integration.ServerSelectionCapture;
import org.waypoints.next.integration.WurmWaypointerRuntime;
import org.waypoints.next.integration.WaypointClientConfiguration;
import org.waypoints.next.render.BeamProbeConfiguration;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WurmWaypointerMod implements WurmClientMod, Configurable, PreInitable, Initable,
        ModListener, SharedLanguageCoordinator.Participant {
    public static final String VERSION = "1.7.6";

    @Override public String[] supportedLanguageCodes() {
        return org.waypoints.next.i18n.Messages.CODES.clone();
    }

    @Override public void setUserLanguage(String code) {
        WurmWaypointerRuntime.setUserLanguage(code);
    }

    @Override public void modInitialized(ModEntry<?> entry) {
        SharedUpdateCoordinator.modInitialized(entry);
    }
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer");
    private static volatile BeamProbeConfiguration configuration =
            BeamProbeConfiguration.disabled();
    private static volatile WaypointClientConfiguration waypointConfiguration =
            WaypointClientConfiguration.defaults();

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public void configure(Properties properties) {
        org.waypoints.next.integration.WaypointerDiagnosticLog.start(
                java.nio.file.Paths.get("wurm-waypointer-data"),VERSION);
        WurmWaypointerRuntime.configureHub(properties);
        try {
            configuration = BeamProbeConfiguration.from(properties);
        } catch (RuntimeException invalid) {
            configuration = BeamProbeConfiguration.disabled();
            LOGGER.log(Level.WARNING,
                    "Invalid Phase 0 beam configuration; the probe was disabled", invalid);
        }
        try {
            waypointConfiguration = WaypointClientConfiguration.from(properties,
                    warning -> LOGGER.warning("Invalid waypoint setting: " + warning));
        } catch (RuntimeException invalid) {
            waypointConfiguration = WaypointClientConfiguration.defaults();
            LOGGER.log(Level.WARNING,
                    "Waypoint configuration could not be read; defaults were selected",
                    invalid);
        }
        WurmWaypointerRuntime.configure(configuration, waypointConfiguration);
    }

    @Override
    public void preInit() {
        SharedUpdateHooks.install();
        final ClassPool pool = HookManager.getInstance().getClassPool();
        FailOpenHookInstaller hooks = new FailOpenHookInstaller(LOGGER);
        hooks.install("map texture GL lifecycle", new FailOpenHookInstaller.HookOperation() {
            @Override public void install() throws Exception { hookMapTextures(pool); }
        });
        hooks.install("native render diagnostics", new FailOpenHookInstaller.HookOperation() {
            @Override public void install() throws Exception { hookNativeRenderDiagnostics(pool); }
        });
        hooks.install("selected Steam server capture", new FailOpenHookInstaller.HookOperation() {
            @Override public void install() throws Exception { hookSelectedServer(pool); }
        });
        hooks.install("direct-connect endpoint capture", new FailOpenHookInstaller.HookOperation() {
            @Override public void install() throws Exception { hookDirectConnect(pool); }
        });
        hooks.install("HUD lifecycle", new FailOpenHookInstaller.HookOperation() {
            @Override public void install() { hookHud(); }
        });
        hooks.install("connection cleanup", new FailOpenHookInstaller.HookOperation() {
            @Override public void install() throws Exception { hookConnectionLifecycle(pool); }
        });
        hooks.install("received cave tile coordinates", new FailOpenHookInstaller.HookOperation() {
            @Override public void install() throws Exception { hookCaveTileCoverage(pool); }
        });
        hooks.install("fresh world server information",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception {
                        hookWorldServerInformation(pool);
                    }
                });
        hooks.install("server-owned native M map",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception {
                        hookWorldMap(pool);
                    }
                });
        hooks.install("server map mouse wheel",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception {
                        hookWorldMapWheel(pool);
                    }
                });
        hooks.install("borderless mini-map chrome",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception {
                        hookMiniMapChrome(pool);
                    }
                });
        hooks.install("always-active compass and click gesture",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception { hookCompass(pool); }
                });
        hooks.install("interactive compass waypoint marker",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception { hookCompassMarker(pool); }
                });
        hooks.install("waypoint console commands",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception { hookConsole(pool); }
                });
        hooks.install("Loot Map Event capture",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception { hookEventMessages(pool); }
                });
        hooks.install("Loot Map action correlation",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() { hookLootMapActions(); }
                });
        hooks.install("correlated vanilla Manage catalogues", new FailOpenHookInstaller.HookOperation() {
            @Override public void install() {
                HookManager.getInstance().registerHook(
                    "com.wurmonline.client.renderer.gui.HeadsUpDisplay", "showBml",
                    "(SLjava/lang/String;IIFFZZFFFLjava/lang/String;)V",
                    () -> (proxy, method, args) -> {
                        if (WurmWaypointerRuntime.interceptManagedBml((HeadsUpDisplay) proxy,
                                (String) args[1], (String) args[11])) return null;
                        return method.invoke(proxy,args);
                    });
            }
        });
        hooks.install("Loot Map opened-chest completion",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception {
                        hookInventoryWindows(pool);
                    }
                });
        hooks.install("Surroundings live object stream",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception {
                        hookSurroundings(pool);
                    }
                });
        hooks.install("bounded Scanner coloured outlines",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception {
                        hookScannerOutlines(pool);
                    }
                });
        hooks.install("effect renderer lifecycle and waypoint world pass",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception { hookEffectRenderer(pool); }
                });
        hooks.install("vanilla landmark capture and suppression",
                new FailOpenHookInstaller.HookOperation() {
                    @Override public void install() throws Exception {
                        hookVanillaLandmarks(pool);
                    }
                });
    }

    @Override
    public void init() {
        SharedUpdateHooks.registerHost("wurm-waypointer");
        LOGGER.info("Wurm Waypointer " + VERSION + " initialized; "
                + configuration.diagnosticSummary() + "; "
                + waypointConfiguration.diagnosticSummary());
    }

    private static void hookSelectedServer(ClassPool pool) throws Exception {
        CtClass browser = pool.getCtClass("com.wurmonline.client.startup.ServerBrowserFX");
        browser.getMethod("ConnectTo", "(Ljavafx/scene/control/TableView;)V")
                .insertBefore("org.waypoints.next.integration.ServerSelectionCapture.rememberSelectedServer($1);");
        browser.getMethod("ConnectWithPassword",
                "(Ljavafx/scene/control/TableView;Ljava/lang/String;Ljava/lang/String;)V")
                .insertBefore("org.waypoints.next.integration.ServerSelectionCapture.rememberSelectedServer($1);");
    }

    private static void hookDirectConnect(ClassPool pool) throws Exception {
        CtClass browser = pool.getCtClass("com.wurmonline.client.startup.ServerBrowserFX");
        browser.getMethod("ConnectWithIp",
                "(Ljava/lang/String;IJSSLjava/lang/String;Ljava/lang/String;ZLjava/lang/String;)V")
                .insertBefore("org.waypoints.next.integration.ServerSelectionCapture.rememberDirect($1, $2);");
        browser.getMethod("ConnectWithIpCheckForpassword",
                "(Ljava/lang/String;JZZSLjava/lang/String;)V")
                .insertBefore("org.waypoints.next.integration.ServerSelectionCapture.rememberDirectAddress($1);");

        // The pinned launcher's visible "Connect by IP" form does not call
        // either method above. After validating its fields it stores the
        // endpoint in WurmMain and calls this private save boundary.
        CtClass direct = pool.getCtClass(
                "com.wurmonline.client.startup.ServerBrowserDirectConnect");
        direct.getDeclaredMethod("saveOptions").insertAfter(
                "org.waypoints.next.integration.ServerSelectionCapture.rememberDirect("
                        + "com.wurmonline.client.launcherfx.WurmMain.serverIp, "
                        + "com.wurmonline.client.launcherfx.WurmMain.serverPort);");
    }

    private static void hookMapTextures(ClassPool pool) throws Exception {
        String bridge = "com.wurmonline.client.resources.textures.WaypointerCaveTexture.";
        CtClass queue = pool.get("com.wurmonline.client.renderer.backend.Queue");
        String before="if ($0.order == com.wurmonline.client.renderer.backend.Queue.QUEUE_HUD) { "
                +bridge+"flushUploads(); org.waypoints.next.render.WaypointRenderProfiler.recordHudQueue($0.getQueueCount()); }";
        queue.getDeclaredMethod("render").insertBefore(before);
        queue.getDeclaredMethod("renderModern").insertBefore(before);
        pool.get("com.wurmonline.client.renderer.backend.Backend")
                .getDeclaredMethod("endFrame").insertAfter(bridge + "finishFrame();");
    }

    /** Optional timings cannot prevent installation of texture retirement/upload hooks. */
    private static void hookNativeRenderDiagnostics(ClassPool pool) throws Exception {
        CtClass queue = pool.get("com.wurmonline.client.renderer.backend.Queue");
        String profiler = "org.waypoints.next.render.WaypointRenderProfiler.";
        for (String name : new String[]{"render", "renderModern"}) {
            CtMethod method = queue.getDeclaredMethod(name);
            method.addLocalVariable("__waypointerQueueStart", CtClass.longType);
            method.insertBefore("__waypointerQueueStart = System.nanoTime();");
            method.insertAfter(profiler+"recordNativeQueue(System.nanoTime()-__waypointerQueueStart);");
        }
        CtMethod output = pool.get("com.wurmonline.client.LwjglClient").getDeclaredMethod("update");
        output.addLocalVariable("__waypointerOutputStart", CtClass.longType);
        output.insertBefore("__waypointerOutputStart = System.nanoTime();");
        output.insertAfter(profiler+"recordFrameOutput(System.nanoTime()-__waypointerOutputStart);");
    }

    private static void hookHud() {
        HookManager.getInstance().registerHook(
                "com.wurmonline.client.renderer.gui.HeadsUpDisplay", "init", "(II)V",
                () -> (proxy, method, args) -> {
                    Object result = method.invoke(proxy, args);
                    WurmWaypointerRuntime.hudReady((HeadsUpDisplay) proxy);
                    return result;
                });
        HookManager.getInstance().registerHook(
                "com.wurmonline.client.renderer.gui.HeadsUpDisplay", "gameTick", "()V",
                () -> (proxy, method, args) -> {
                    Object result = method.invoke(proxy, args);
                    WurmWaypointerRuntime.hudTick((HeadsUpDisplay) proxy);
                    return result;
                });
        HookManager.getInstance().registerHook(
                "com.wurmonline.client.renderer.gui.HeadsUpDisplay", "toggleComponent",
                "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)Z",
                () -> (proxy, method, args) -> {
                    Object result = method.invoke(proxy, args);
                    WurmWaypointerRuntime.componentVisibilityChanged(
                            args[0], Boolean.TRUE.equals(result), "toggleComponent");
                    return result;
                });
        HookManager.getInstance().registerHook(
                "com.wurmonline.client.renderer.gui.HeadsUpDisplay", "hideComponent",
                "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)V",
                () -> (proxy, method, args) -> {
                    Object result = method.invoke(proxy, args);
                    WurmWaypointerRuntime.componentVisibilityChanged(
                            args[0], false, "hideComponent");
                    return result;
                });
    }

    private static void hookConnectionLifecycle(ClassPool pool) throws Exception {
        CtClass connection = pool.getCtClass(
                "com.wurmonline.client.comm.SimpleServerConnectionClass");
        connection.getMethod("disconnect", "(Ljava/lang/String;)V")
                .insertBefore("org.waypoints.next.integration.WurmWaypointerRuntime.connectionEnded();");
        connection.getMethod("disconnectAndConnectTo", "(Ljava/lang/String;I)V")
                .insertBefore("org.waypoints.next.integration.WurmWaypointerRuntime.connectionTransferred($1, $2);");
    }

    private static void hookCaveTileCoverage(ClassPool pool) throws Exception {
        CtClass cave = pool.getCtClass("com.wurmonline.client.game.CaveDataBuffer");
        CtMethod clear = cave.getMethod("clear", "()V");
        CtMethod strip = cave.getMethod("tileStrip", "(SSSS[[I[[S[[B)V");
        String coverage = "org.waypoints.next.integration.WurmCaveTileCoverage.";
        clear.insertBefore(coverage + "clear($0);");
        strip.insertBefore(coverage + "beforeStrip($0, $1, $2, $3, $4);");
        strip.insertAfter(coverage + "afterStrip($0, $1, $2, $3, $4);");
    }

    private static void hookWorldServerInformation(ClassPool pool) throws Exception {
        CtClass world = pool.getCtClass("com.wurmonline.client.game.World");
        world.getMethod("setServerInformation", "(IZLjava/lang/String;)V")
                .insertAfter("org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "serverInformationUpdated($0, $1, $3);");
    }

    private static void hookWorldMap(ClassPool pool) throws Exception {
        CtClass cluster = pool.getCtClass(
                "com.wurmonline.client.renderer.gui.maps.ClusterMap");
        cluster.getMethod("render",
                "(Lcom/wurmonline/client/renderer/backend/Queue;FFF)V")
                .insertBefore("if (com.wurmonline.client.renderer.gui."
                        + "ServerMapWindowBridge.render($1)) return;");

        CtClass map = pool.getCtClass(
                "com.wurmonline.client.renderer.gui.WorldMap");
        map.getMethod("pick",
                "(Lcom/wurmonline/client/renderer/PickData;II)V")
                .insertBefore("if (com.wurmonline.client.renderer.gui."
                        + "ServerMapWindowBridge.pick($0, $1, $2, $3)) return;");
        map.getMethod("leftPressed", "(III)V").insertBefore(
                "if (com.wurmonline.client.renderer.gui.ServerMapWindowBridge."
                        + "leftPressed($0, $1, $2)) return;");
        map.getMethod("leftReleased", "(II)V").insertBefore(
                "if (com.wurmonline.client.renderer.gui.ServerMapWindowBridge."
                        + "leftReleased($0, $1, $2)) return;");
        map.getMethod("mouseDragged", "(II)V").insertBefore(
                "if (com.wurmonline.client.renderer.gui.ServerMapWindowBridge."
                        + "mouseDragged($0, $1, $2)) return;");
        map.getMethod("rightPressed", "(III)V").insertBefore(
                "if (com.wurmonline.client.renderer.gui.ServerMapWindowBridge."
                        + "rightPressed($0, $1, $2)) return;");
        map.getMethod("mouseMoved", "(II)V").insertBefore(
                "com.wurmonline.client.renderer.gui.ServerMapWindowBridge."
                        + "mouseMoved($0, $1, $2);");
        map.getMethod("openContextMenu", "()V").insertBefore(
                "if (com.wurmonline.client.renderer.gui.ServerMapWindowBridge."
                        + "suppressVanillaContextMenu($0)) return;");
        map.getMethod("reset", "()V").insertAfter(
                "com.wurmonline.client.renderer.gui.ServerMapWindowBridge.reset($0);");

    }

    /** Route absolute HUD wheel coordinates before Wurm replaces them with 0,0. */
    private static void hookWorldMapWheel(ClassPool pool) throws Exception {
        CtClass hud = pool.getCtClass(
                "com.wurmonline.client.renderer.gui.HeadsUpDisplay");
        hud.getMethod("mouseWheeled", "(III)V").insertBefore(
                "if (com.wurmonline.client.renderer.gui."
                        + "SurroundingsWindowBridge.mouseWheeled($0, $1, $2, $3)) return;"
                        + "if (com.wurmonline.client.renderer.gui."
                        + "MiniMapWindowBridge.mouseWheeled($0, $1, $2, $3)) return;"
                        + "if (com.wurmonline.client.renderer.gui.ServerMapWindowBridge."
                        + "mouseWheeled($0.getWorldMap(), $1, $2, $3)) return;");
    }

    private static void hookMiniMapChrome(ClassPool pool) throws Exception {
        String miniMap = "this.parent instanceof "
                + "com.wurmonline.client.renderer.gui.MiniMapWindow";
        String customWorldMap = "this.parent instanceof "
                + "com.wurmonline.client.renderer.gui.WorldMap"
                + " && com.wurmonline.client.renderer.gui."
                + "ServerMapWindowBridge.usesCustomWindow()";
        String topBarGuard = "if (" + miniMap + ") { return; }"
                + "if (" + customWorldMap + ") {"
                + "com.wurmonline.client.renderer.gui.ServerMapWindowBridge."
                + "renderWindowTitle((com.wurmonline.client.renderer.gui."
                + "WorldMap)this.parent, $1); return; }";
        hideWindowShellPart(pool,
                "com.wurmonline.client.renderer.gui.WWindow$TopBar",
                topBarGuard);
        String transparentMiddle = "if (" + miniMap + " || "
                + customWorldMap + ") { super.renderComponent($1, $2); return; }";
        hideWindowShellPart(pool,
                "com.wurmonline.client.renderer.gui.WWindow$Middle",
                transparentMiddle);
        String hideGuard = "if (" + miniMap + " || "
                + customWorldMap + ") { return; }";
        hideWindowShellPart(pool,
                "com.wurmonline.client.renderer.gui.WWindow$SideBar",
                hideGuard);
        hideWindowShellPart(pool,
                "com.wurmonline.client.renderer.gui.WWindow$SouthBar",
                hideGuard);
    }

    private static void hideWindowShellPart(ClassPool pool, String className,
                                            String guard) throws Exception {
        renderMethod(pool.get(className)).insertBefore(guard);
    }

    private static CtMethod renderMethod(CtClass type)
            throws NotFoundException {
        for (CtMethod method : type.getDeclaredMethods("renderComponent")) {
            if ("(Lcom/wurmonline/client/renderer/backend/Queue;F)V"
                    .equals(method.getSignature())) return method;
        }
        throw new NotFoundException(type.getName() + ".renderComponent");
    }

    private static void hookCompass(ClassPool pool) throws Exception {
        CtClass compass = pool.getCtClass(
                "com.wurmonline.client.renderer.gui.CompassComponent");
        compass.getMethod("isAvailable", "()Z").insertAfter("{ $_ = true; }");
        compass.getMethod("gameTick", "()V").insertAfter(
                "{ $0.stability = 1.0f; $0.fadeAlpha = 1.0f; $0.isMoving = false; }");
        compass.getMethod("leftPressed", "(III)V").insertBefore(
                "org.waypoints.next.integration.CompassHookBridge.pressed($0, $1, $2);");
        compass.getMethod("mouseDragged", "(II)V").insertBefore(
                "org.waypoints.next.integration.CompassHookBridge.dragged($0, $1, $2);");
        compass.getMethod("leftReleased", "(II)V").insertAfter(
                "org.waypoints.next.integration.CompassHookBridge.released($0, $1, $2);");
        compass.getMethod("rightPressed", "(III)V").insertBefore(
                "if (org.waypoints.next.integration.CompassHookBridge."
                        + "rightPressed($0, $1, $2)) return;");
        CtClass hud = pool.getCtClass(
                "com.wurmonline.client.renderer.gui.HeadsUpDisplay");
        hud.getDeclaredMethod("showPopupComponent").insertBefore(
                "com.wurmonline.client.renderer.gui."
                        + "CompassSurroundingsPopupBridge.augment($1);");
    }

    private static void hookCompassMarker(ClassPool pool) throws Exception {
        CtClass compass = pool.getCtClass(
                "com.wurmonline.client.renderer.gui.CompassComponent");
        compass.getMethod("renderComponent",
                "(Lcom/wurmonline/client/renderer/backend/Queue;F)V").insertAfter(
                "com.wurmonline.client.renderer.gui.WaypointCompassMarkerBridge.render($0, $1);");
        compass.getMethod("pick",
                "(Lcom/wurmonline/client/renderer/PickData;II)V").insertAfter(
                "com.wurmonline.client.renderer.gui.WaypointCompassMarkerBridge."
                        + "pick($0, $1, $2, $3);");
    }

    private static void hookEffectRenderer(ClassPool pool) throws Exception {
        CtClass renderer = pool.getCtClass(
                "com.wurmonline.client.renderer.effects.EffectRender");
        renderer.getMethod("render",
                "(Lcom/wurmonline/client/renderer/backend/Queue;)V")
                .insertAfter("org.waypoints.next.render."
                        + "WaypointLatePassBridge.render($1);");
        renderer.getMethod("clear", "()V").insertAfter(
                "org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "effectRendererCleared($0);");
    }

    private static void hookVanillaLandmarks(ClassPool pool) throws Exception {
        CtClass listener = pool.getCtClass(
                "com.wurmonline.client.comm.ServerConnectionListenerClass");
        listener.getMethod("addEffect",
                "(JSFFFILjava/lang/String;FF)V").insertBefore(
                "if (org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "captureVanillaLandmark($1, $2, $3, $4, $5, $6)) return;");
        listener.getMethod("removeEffect", "(J)V").insertBefore(
                "org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "vanillaLandmarkRemoved($1);");
    }

    private static void hookConsole(ClassPool pool) throws Exception {
        CtClass console = pool.getCtClass("com.wurmonline.client.console.WurmConsole");
        console.getMethod("handleDevInput", "(Ljava/lang/String;[Ljava/lang/String;)Z")
                .insertBefore("if (org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "handleConsoleCommand($1, $2)) return true;");
    }

    private static void hookEventMessages(ClassPool pool) throws Exception {
        CtClass chat = pool.getCtClass(
                "com.wurmonline.client.renderer.gui.ChatPanelComponent");
        chat.getMethod("addText", "(Ljava/lang/String;Ljava/lang/String;FFFZ)V")
                .insertBefore("org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "observeEvent($1, $2);");
        chat.getMethod("addText",
                "(Ljava/lang/String;Ljava/util/List;Z)V")
                .insertBefore("org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "observeEventSegments($1, $2);");
    }

    private static void hookLootMapActions() {
        HookManager.getInstance().registerHook(
                "com.wurmonline.client.comm.SimpleServerConnectionClass",
                "sendAction", "(J[JLcom/wurmonline/shared/constants/PlayerAction;)V",
                () -> (proxy, method, args) -> {
                    WurmWaypointerRuntime.observeAction(
                            (long[]) args[1],
                            (com.wurmonline.shared.constants.PlayerAction) args[2]);
                    return method.invoke(proxy, args);
                });
    }

    private static void hookInventoryWindows(ClassPool pool) throws Exception {
        CtClass inventories = pool.getCtClass(
                "com.wurmonline.client.game.inventory.InventoryMetaWindowManager");
        inventories.getMethod("addWindow", "(JLjava/lang/String;)V")
                .insertAfter("org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "inventoryWindowOpened($1, $2);");
    }

    private static void hookSurroundings(ClassPool pool) throws Exception {
        CtClass cells = pool.getCtClass(
                "com.wurmonline.client.renderer.cell.CellRenderer");
        cells.getMethod("addRenderable",
                "(Lcom/wurmonline/client/renderer/cell/CellRenderable;)V")
                .insertAfter("org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "surroundingsRenderableUpserted($1);");
        cells.getMethod("renderableMoved",
                "(Lcom/wurmonline/client/renderer/cell/CellRenderable;)V")
                .insertAfter("org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "surroundingsRenderableUpserted($1);");
        cells.getMethod("updateRenderable",
                "(Lcom/wurmonline/client/renderer/cell/CellRenderable;)V")
                .insertAfter("org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "surroundingsRenderableUpserted($1);");
        cells.getMethod("removeRenderable",
                "(Lcom/wurmonline/client/renderer/cell/CellRenderable;Z)V")
                .insertAfter("org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "surroundingsRenderableRemoved($1, $2);");
        cells.getMethod("clear", "()V").insertAfter(
                "org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "surroundingsRenderablesCleared();");
        pool.getCtClass("com.wurmonline.client.renderer.cell.CellRenderable")
                .getMethod("setRemoveWhenDeadAnimIsDone", "(Z)V").insertAfter(
                "if ($1) org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "surroundingsCreatureDied($0);");

        // CellRenderer only reports cell changes. Creature.move receives every
        // fresh server target for creatures and mobile items, so use its
        // arguments instead of the interpolated render position.
        CtClass creatures = pool.getCtClass(
                "com.wurmonline.client.renderer.cell.CreatureCellRenderable");
        creatures.getMethod("move", "(FFFF)V").insertAfter(
                "org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "surroundingsCreatureMoved($0, $1, $2, $3);");
        creatures.getMethod("setPosImmediately", "(FFFZZ)V").insertAfter(
                "org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "surroundingsCreatureMoved($0, $1, $2, $3);");
        creatures.getMethod("setAttitude", "(I)V").insertAfter(
                "org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "surroundingsRenderableUpserted($0);");

        CtClass listener = pool.getCtClass(
                "com.wurmonline.client.comm.ServerConnectionListenerClass");
        listener.getDeclaredMethod("playDeadThenReplaceWithCorpse").insertBefore(
                "org.waypoints.next.integration.WurmWaypointerRuntime."
                        + "surroundingsCreatureReplacedByCorpse($1, $2);");
    }

    private static void hookScannerOutlines(ClassPool pool) throws Exception {
        CtClass worldRender = pool.getCtClass(
                "com.wurmonline.client.renderer.WorldRender");
        worldRender.addMethod(CtNewMethod.make(
                "private void waypointerRenderScannerOutline("
                        + "com.wurmonline.client.renderer.backend.Queue queue,"
                        + "com.wurmonline.client.renderer.PickableUnit unit){"
                        + "com.wurmonline.client.renderer.Color source=unit.getOutlineColor();"
                        + "if(source==null)return;"
                        + "float intensity=3.5f;"
                        + "com.wurmonline.client.renderer.Color color="
                        + "new com.wurmonline.client.renderer.Color(source);"
                        + "color.a=intensity*source.a;"
                        + "com.wurmonline.client.renderer.backend.RenderState fill="
                        + "new com.wurmonline.client.renderer.backend.RenderState();"
                        + "fill.alphaval=intensity;fill.twosided=false;"
                        + "fill.depthtest=com.wurmonline.client.renderer.backend."
                        + "Primitive.TestFunc.ALWAYS;fill.depthwrite=true;"
                        + "fill.customstate=this.customPickFill;fill.nolight=true;"
                        + "unit.renderPicked(queue,fill,color);"
                        + "color.a=intensity*0.25f*source.a;"
                        + "com.wurmonline.client.renderer.backend.RenderState outline="
                        + "new com.wurmonline.client.renderer.backend.RenderState();"
                        + "outline.alphaval=intensity*0.25f;outline.twosided=false;"
                        + "outline.depthtest=com.wurmonline.client.renderer.backend."
                        + "Primitive.TestFunc.LESS;outline.depthwrite=false;"
                        + "outline.blendmode=com.wurmonline.client.renderer.backend."
                        + "Primitive.BlendMode.ALPHABLEND;"
                        + "outline.customstate=this.customPickOutline;outline.nolight=true;"
                        + "unit.renderPicked(queue,outline,color);"
                        + "com.wurmonline.client.renderer.backend.RenderState restore="
                        + "new com.wurmonline.client.renderer.backend.RenderState();"
                        + "restore.customstate=this.customPickFillDepth;"
                        + "restore.depthtest=com.wurmonline.client.renderer.backend."
                        + "Primitive.TestFunc.ALWAYS;restore.nolight=true;"
                        + "unit.renderPicked(queue,restore,color);"
                        + "}", worldRender));
        worldRender.addMethod(CtNewMethod.make(
                "private void waypointerRenderScannerOutlines("
                        + "com.wurmonline.client.renderer.backend.Queue queue){"
                        + "if(this.itemPlacer.isActive())return;"
                        + "com.wurmonline.client.renderer.PickableUnit[] targets="
                        + "org.waypoints.next.integration.ScannerOutlineRenderBridge."
                        + "targets(queue);"
                        + "for(int i=0;i<targets.length;i++){"
                        + "try{if(targets[i]!=null)"
                        + "waypointerRenderScannerOutline(queue,targets[i]);}"
                        + "catch(java.lang.Throwable ignored){}}"
                        + "}", worldRender));
        worldRender.getDeclaredMethod("renderPickedItem").insertAfter(
                "$0.waypointerRenderScannerOutlines($1);");
    }
}
