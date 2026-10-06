package org.chamomilo.wurm.update;

import javassist.ClassPool;
import javassist.CtClass;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Bytecode-only startup integration for mods without their own updater HUD bridge. */
public final class SharedUpdateHooks {
    private static final Logger LOG = Logger.getLogger("Chamomilo.UpdateCoordinator");
    private static final Map<ClassPool, Boolean> INSTALLED = new WeakHashMap<ClassPool, Boolean>();
    private static volatile List<ModUpdate> pending;
    private SharedUpdateHooks() { }

    public static void install() {
        try { install(HookManager.getInstance().getClassPool()); }
        catch (Throwable failure) { LOG.log(Level.WARNING, "Cannot install updater HUD hooks", failure); }
    }

    static synchronized void install(ClassPool pool) throws Exception {
        if (INSTALLED.containsKey(pool)) return;
        CtClass hud = pool.get("com.wurmonline.client.renderer.gui.HeadsUpDisplay");
        hud.getDeclaredMethod("init", new CtClass[]{CtClass.intType, CtClass.intType}).insertAfter(
                "{ com.wurmonline.client.renderer.gui.ChamomiloUpdateBridge.hudReady($0); }");
        hud.getDeclaredMethod("gameTick", new CtClass[0]).insertAfter(
                "{ com.wurmonline.client.renderer.gui.ChamomiloUpdateBridge.tick($0); }");
        INSTALLED.put(pool, Boolean.TRUE);
    }

    public static void registerHost(String id) {
        SharedUpdateCoordinator.registerHost(id, new SharedUpdateCoordinator.Host() {
            public void updatesReady(List<ModUpdate> updates) { pending = updates; }
            public void checkFailed(String repository, Throwable failure) {
                // The coordinator logs the failure and retains a catalogue row.
            }
        });
    }
    public static List<ModUpdate> pending() { return pending; }
}
