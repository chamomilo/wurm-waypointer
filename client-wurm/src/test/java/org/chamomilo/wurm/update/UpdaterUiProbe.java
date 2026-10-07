package org.chamomilo.wurm.update;

import javassist.*;

/** Runs real Wurm layout/input code, replacing only fonts, GPU drawing and HUD startup. */
public final class UpdaterUiProbe {
    public static void main(String[] args) throws Throwable {
        ClassPool pool = new ClassPool(true);
        String gui = "com.wurmonline.client.renderer.gui.";
        CtClass fonts = pool.get(gui + "text.TextFont");
        fonts.getClassInitializer().setBody("{}");
        fonts.getDeclaredMethod("getText", new CtClass[]{pool.get("java.lang.String")})
                .setBody("{ return new com.wurmonline.client.renderer.gui.UpdaterLayoutProbe.ProbeFont($1); }");
        CtClass options = pool.get("com.wurmonline.client.options.Options");
        options.getClassInitializer().setBody("{}");
        CtClass component = pool.get(gui + "WurmComponent");
        component.getClassInitializer().setBody("{}");
        component.getDeclaredMethod("fillRect").setBody("{ " + gui
                + "UpdaterLayoutProbe.rect($2,$3,$4,$5,$6,$7,$8,$9); }");
        CtClass hud = pool.get(gui + "HeadsUpDisplay");
        hud.getClassInitializer().setBody("{ scissor = new com.wurmonline.client.renderer.backend.ScissorControl(); }");
        CtClass scissor = pool.get("com.wurmonline.client.renderer.backend.ScissorControl");
        scissor.getDeclaredMethod("pushClip").setBody("{ return " + gui + "UpdaterLayoutProbe.clip($1,$2,$3,$4); }");
        scissor.getDeclaredMethod("popClip").setBody("{ " + gui + "UpdaterLayoutProbe.unclip(); }");
        CtClass window = pool.get(gui + "ChamomiloUpdateWindow");
        window.getDeclaredMethod("loadFrame").setBody("{ frameAttempted=true; frame=(com.wurmonline.client.resources.textures.ResourceTexture) "
                + gui + "UpdaterLayoutProbe.allocate(com.wurmonline.client.resources.textures.ResourceTexture.class); }");
        window.getDeclaredMethod("loadButtonSkin").setBody("{ buttonSkinAttempted=true; buttonSkin="
                + gui + "UpdaterLayoutProbe.buttonTexture(); }");
        CtClass renderer = pool.get(gui + "Renderer");
        if (renderer.getClassInitializer() != null) renderer.getClassInitializer().setBody("{}");
        for (CtMethod method : renderer.getDeclaredMethods("texturedQuadAlphaBlend"))
            if (method.getParameterTypes().length == 14)
                method.setBody("{ " + gui + "UpdaterLayoutProbe.texture($2,$3,$7,$8,$9,$10,$11,$12,$13,$14); }");
        pool.get(gui + "WButton").getDeclaredMethod("renderComponent")
                .setBody("{ " + gui + "UpdaterLayoutProbe.button(this); }");
        pool.get("org.chamomilo.wurm.update.SharedUpdateCoordinator").getDeclaredMethod("startOnce")
                .setBody("{ return false; }"); // Lifecycle checks must not launch a real online catalogue lookup.
        pool.get(gui + "MainMenu").getDeclaredMethod("registerComponent").instrument(new javassist.expr.ExprEditor() {
            @Override public void edit(javassist.expr.FieldAccess field) throws CannotCompileException {
                if (field.isReader() && field.getClassName().equals("com.wurmonline.client.options.Options")
                        && field.getFieldName().equals("guiSkins"))
                    field.replace("{ $_ = " + gui + "UpdaterLayoutProbe.guiSkin(); }");
            }
            @Override public void edit(javassist.expr.MethodCall call) throws CannotCompileException {
                if (call.getClassName().equals("com.wurmonline.client.resources.textures.ResourceTextureLoader")
                        && call.getMethodName().equals("getTexture")) call.replace("{ $_ = null; }");
            }
        });
        // Preserve the real HUD list/menu/toggle implementation; omit unrelated game/quickbar state.
        javassist.expr.ExprEditor hudState = new javassist.expr.ExprEditor() {
            @Override public void edit(javassist.expr.MethodCall call) throws CannotCompileException {
                String owner;
                String method;
                try {
                    owner = call.getClassName();
                    method = call.getMethodName();
                } catch (ClassCastException invokedynamic) {
                    // The bundled Javassist predates the client's Java 8 lambda call sites.
                    return;
                }
                if (owner.equals("com.wurmonline.client.options.BooleanOption")
                        && method.equals("value")) call.replace("{ $_ = false; }");
                if ((owner.equals(gui + "HudSettings")
                        || owner.equals(gui + "PlayerStateButtons"))
                        && method.equals("setEnabled")) call.replace("{}");
            }
        };
        hud.getDeclaredMethod("addComponent").instrument(hudState);
        hud.getDeclaredMethod("removeComponent").instrument(hudState);
        Loader loader = new Loader(pool);
        loader.run(gui + "UpdaterLayoutProbe", args);
    }
}
