package org.waypoints.next.integration;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertNotNull;

/** Prevents a future build from silently compiling against a different client contract. */
public class PinnedClientContractTest {
    private static ClassPool classes;

    @BeforeClass
    public static void loadPinnedClient() throws Exception {
        String directory = System.getProperty("wurmClientLibDir");
        if (directory == null || directory.trim().isEmpty()) {
            throw new AssertionError("wurmClientLibDir test property is missing");
        }
        classes = new ClassPool(false);
        classes.appendSystemPath();
        classes.appendClassPath(new File(directory, "client-patched.jar").getAbsolutePath());
        classes.appendClassPath(new File(directory, "common.jar").getAbsolutePath());
    }

    @Test
    public void serverBrowserAndSteamSelectionSignaturesExist() throws Exception {
        CtClass browser = classes.get("com.wurmonline.client.startup.ServerBrowserFX");
        assertMethod(browser, "ConnectTo", "(Ljavafx/scene/control/TableView;)V");
        assertMethod(browser, "ConnectWithPassword",
                "(Ljavafx/scene/control/TableView;Ljava/lang/String;Ljava/lang/String;)V");
        assertMethod(browser, "ConnectWithIp",
                "(Ljava/lang/String;IJSSLjava/lang/String;Ljava/lang/String;ZLjava/lang/String;)V");

        CtClass direct = classes.get(
                "com.wurmonline.client.startup.ServerBrowserDirectConnect");
        assertMethod(direct, "saveOptions", "()V");

        CtClass steam = classes.get("com.wurmonline.client.steam.SteamServerFX");
        assertMethod(steam, "getServerName", "()Ljava/lang/String;");
        assertMethod(steam, "getIpAdress", "()Ljava/lang/String;");
        assertMethod(steam, "getPort", "()S");
        assertMethod(steam, "getQueryPort", "()S");
    }

    @Test
    public void hudWorldAndConnectionLifecycleSignaturesExist() throws Exception {
        CtClass hud = classes.get("com.wurmonline.client.renderer.gui.HeadsUpDisplay");
        assertMethod(hud, "init", "(II)V");
        assertMethod(hud, "gameTick", "()V");
        assertMethod(hud, "toggleComponent",
                "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)Z");
        assertMethod(hud, "hideComponent",
                "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)V");
        assertMethod(hud, "getWidth", "()I");
        assertMethod(hud, "getComponents", "()Ljava/util/List;");

        CtClass world = classes.get("com.wurmonline.client.game.World");
        assertMethod(world, "getServerName", "()Ljava/lang/String;");
        assertMethod(world, "setServerInformation", "(IZLjava/lang/String;)V");
        assertMethod(world, "getRenderOriginX", "()F");
        assertMethod(world, "getRenderOriginY", "()F");

        CtClass worldRender = classes.get("com.wurmonline.client.renderer.WorldRender");
        assertMethod(worldRender, "getScreenWidth", "()I");
        assertMethod(worldRender, "getScreenHeight", "()I");
        assertNotNull(worldRender.getDeclaredField("projectionMatrixWorld"));
        assertNotNull(worldRender.getDeclaredField("viewMatrixWorldRender"));

        CtClass matrix = classes.get("com.wurmonline.client.renderer.Matrix");
        assertMethod(matrix, "getBuffer", "()Ljava/nio/FloatBuffer;");

        CtClass connection = classes.get(
                "com.wurmonline.client.comm.SimpleServerConnectionClass");
        assertMethod(connection, "sendAction",
                "(J[JLcom/wurmonline/shared/constants/PlayerAction;)V");
        assertMethod(connection, "disconnect", "(Ljava/lang/String;)V");
        assertMethod(connection, "disconnectAndConnectTo", "(Ljava/lang/String;I)V");
    }

    @Test
    public void compassGestureAndStabilityContractExists() throws Exception {
        CtClass compass = classes.get("com.wurmonline.client.renderer.gui.CompassComponent");
        assertMethod(compass, "isAvailable", "()Z");
        assertMethod(compass, "gameTick", "()V");
        assertMethod(compass, "leftPressed", "(III)V");
        assertMethod(compass, "mouseDragged", "(II)V");
        assertMethod(compass, "leftReleased", "(II)V");
        assertMethod(compass, "rightPressed", "(III)V");
        assertMethod(compass, "renderComponent",
                "(Lcom/wurmonline/client/renderer/backend/Queue;F)V");
        assertMethod(compass, "pick",
                "(Lcom/wurmonline/client/renderer/PickData;II)V");
        CtClass pickData = classes.get("com.wurmonline.client.renderer.PickData");
        assertMethod(pickData, "addText", "(Ljava/lang/String;)V");
        CtClass component = classes.get(
                "com.wurmonline.client.renderer.gui.WurmComponent");
        assertMethod(component, "mouseMoved", "(II)V");
        assertMethod(component, "mouseExited", "()V");
        assertNotNull(compass.getDeclaredField("stability"));
        assertNotNull(compass.getDeclaredField("fadeAlpha"));
        assertNotNull(compass.getDeclaredField("isMoving"));
    }

    @Test public void surroundingsRawItemNameContractExists() throws Exception {
        CtClass objectData = classes.get(
                "com.wurmonline.client.renderer.ObjectData");
        assertNotNull(objectData.getDeclaredField("name"));
        assertMethod(objectData, "getName", "()Ljava/lang/String;");
        CtClass creatureData = classes.get(
                "com.wurmonline.client.renderer.CreatureData");
        assertMethod(creatureData, "getModifier", "()B");
        CtClass creatureRenderable = classes.get(
                "com.wurmonline.client.renderer.cell.CreatureCellRenderable");
        assertMethod(creatureRenderable, "isItem", "()Z");
    }

    @Test
    public void nativeWorldMapRenderAndGestureContractsExist() throws Exception {
        CtClass map = classes.get("com.wurmonline.client.renderer.gui.WorldMap");
        assertMethod(map, "leftPressed", "(III)V");
        assertMethod(map, "leftReleased", "(II)V");
        assertMethod(map, "rightPressed", "(III)V");
        assertMethod(map, "mouseDragged", "(II)V");
        assertMethod(map, "mouseMoved", "(II)V");
        assertMethod(map, "openContextMenu", "()V");
        assertMethod(map, "reset", "()V");

        CtClass cluster = classes.get(
                "com.wurmonline.client.renderer.gui.maps.ClusterMap");
        assertMethod(cluster, "render",
                "(Lcom/wurmonline/client/renderer/backend/Queue;FFF)V");

        CtClass component = classes.get(
                "com.wurmonline.client.renderer.gui.WurmComponent");
        assertMethod(component, "mouseWheeled", "(III)V");

        CtClass textures = classes.get(
                "com.wurmonline.client.resources.textures.ResourceTextureLoader");
        assertMethod(textures, "prepareTexture",
                "(Lcom/wurmonline/client/resources/ResourceUrl;Ljava/lang/Object;Z)V");
        assertMethod(textures, "getPreparedTexture",
                "(Lcom/wurmonline/client/resources/ResourceUrl;Ljava/lang/Object;)"
                        + "Lcom/wurmonline/client/resources/textures/ResourceTexture;");
        assertMethod(textures, "getNearestTextureNonScaling",
                "(Ljava/lang/String;)"
                        + "Lcom/wurmonline/client/resources/textures/ResourceTexture;");
    }

    @Test
    public void effectRegistrationAndCleanupContractExists() throws Exception {
        CtClass effect = classes.get("com.wurmonline.client.renderer.effects.Effect");
        assertMethod(effect, "delete", "()V");
        assertMethod(effect, "gameTick", "()Z");

        CtClass renderer = classes.get("com.wurmonline.client.renderer.effects.EffectRender");
        assertMethod(renderer, "addEffect",
                "(Lcom/wurmonline/client/renderer/effects/Effect;)V");
        assertMethod(renderer, "removeEffect",
                "(Lcom/wurmonline/client/renderer/effects/Effect;)V");
        assertMethod(renderer, "clear", "()V");
        CtClass serverListener = classes.get(
                "com.wurmonline.client.comm.ServerConnectionListenerClass");
        assertDeclaredMethod(serverListener, "addEffect",
                "(JSFFFILjava/lang/String;FF)V");
        assertDeclaredMethod(serverListener, "removeEffect", "(J)V");

        CtClass lightBeam = classes.get(
                "com.wurmonline.client.renderer.effects.LightBeamEffect");
        assertDeclaredConstructor(lightBeam,
                "(Lcom/wurmonline/client/game/World;FFFZ)V");
        CtClass rift = classes.get(
                "com.wurmonline.client.renderer.effects.RiftSpawnEffect");
        assertDeclaredConstructor(rift,
                "(Lcom/wurmonline/client/game/World;FFF)V");
        assertMethod(rift, "removed", "()V");

        CtClass world = classes.get("com.wurmonline.client.game.World");
        assertMethod(world, "getLightManager",
                "(I)Lcom/wurmonline/client/renderer/light/MasterLightManager;");
        CtClass lights = classes.get(
                "com.wurmonline.client.renderer.light.MasterLightManager");
        assertMethod(lights, "addLight",
                "(Lcom/wurmonline/client/renderer/light/LightSource;)V");

        CtClass material = classes.get("com.wurmonline.client.renderer.Material");
        assertMethod(material, "load",
                "(Ljava/lang/String;)Lcom/wurmonline/client/renderer/Material;");
        assertMethod(material, "instance",
                "()Lcom/wurmonline/client/renderer/MaterialInstance;");
        CtClass materialInstance = classes.get(
                "com.wurmonline.client.renderer.MaterialInstance");
        assertMethod(materialInstance, "destroy", "()V");
    }

    @Test
    public void phase1ConsoleAndHereCoordinateContractsExist() throws Exception {
        CtClass console = classes.get("com.wurmonline.client.console.WurmConsole");
        assertMethod(console, "handleDevInput", "(Ljava/lang/String;[Ljava/lang/String;)Z");
        CtClass world = classes.get("com.wurmonline.client.game.World");
        assertMethod(world, "getPlayerCurrentTileX", "()I");
        assertMethod(world, "getPlayerCurrentTileY", "()I");
        assertMethod(world, "getPlayerPosX", "()F");
        assertMethod(world, "getPlayerPosY", "()F");
        assertMethod(world, "getPlayerPosH", "()F");
        assertMethod(world, "getPlayerRotX", "()F");
        assertMethod(world, "getPlayerLayer", "()I");
        assertMethod(world, "getUsername", "()Ljava/lang/String;");
        CtClass playerAction = classes.get(
                "com.wurmonline.shared.constants.PlayerAction");
        assertMethod(playerAction, "getName", "()Ljava/lang/String;");
        CtClass chat = classes.get(
                "com.wurmonline.client.renderer.gui.ChatPanelComponent");
        assertMethod(chat, "addText",
                "(Ljava/lang/String;Ljava/lang/String;FFFZ)V");
        assertMethod(chat, "addText",
                "(Ljava/lang/String;Ljava/util/List;Z)V");
        CtClass inventories = classes.get(
                "com.wurmonline.client.game.inventory.InventoryMetaWindowManager");
        assertMethod(inventories, "addWindow", "(JLjava/lang/String;)V");
    }

    @Test
    public void nativeWaypointManagerWindowContractsExist() throws Exception {
        CtClass hud = classes.get("com.wurmonline.client.renderer.gui.HeadsUpDisplay");
        assertDeclaredMethod(hud, "addComponent",
                "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)Z");
        assertDeclaredMethod(hud, "removeComponent",
                "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)Z");
        assertMethod(hud, "setActiveWindow",
                "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)V");
        assertMethod(hud, "startTyping", "()V");
        assertMethod(hud, "stopTyping", "()V");
        assertMethod(hud, "addDynamicComponent",
                "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)V");
        assertMethod(hud, "removeDynamicComponent",
                "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)V");
        assertNotNull(hud.getDeclaredField("savePosManager"));

        CtClass positions = classes.get("com.wurmonline.client.settings.SavePosManager");
        assertMethod(positions, "registerAndRefresh",
                "(Lcom/wurmonline/client/renderer/gui/WindowSerializer;Ljava/lang/String;)V");

        CtClass window = classes.get("com.wurmonline.client.renderer.gui.WWindow");
        assertDeclaredMethod(window, "setComponent",
                "(Lcom/wurmonline/client/renderer/gui/FlexComponent;)V");
        assertDeclaredMethod(window, "closePressed", "()V");
        assertDeclaredMethod(window, "setTitle", "(Ljava/lang/String;)V");

        CtClass component = classes.get("com.wurmonline.client.renderer.gui.WurmComponent");
        assertMethod(component, "gameTick", "()V");
        assertMethod(component, "contains", "(II)Z");
        assertDeclaredMethod(component, "fillRect",
                "(Lcom/wurmonline/client/renderer/backend/Queue;FFFFIIII)V");
        assertDeclaredMethod(component, "fillInvertRect",
                "(Lcom/wurmonline/client/renderer/backend/Queue;FFFFIIII)V");
        assertDeclaredMethod(component, "hasInputField", "()Z");
        assertDeclaredMethod(component, "getInputField",
                "()Lcom/wurmonline/client/renderer/gui/WurmInputField;");
        CtClass label = classes.get("com.wurmonline.client.renderer.gui.WurmLabel");
        assertDeclaredConstructor(label,
                "(Ljava/lang/String;Ljava/lang/String;Z)V");
        assertDeclaredMethod(label, "setLabel", "(Ljava/lang/String;)V");

        CtClass input = classes.get(
                "com.wurmonline.client.renderer.gui.WurmInputField");
        assertDeclaredConstructor(input,
                "(Ljava/lang/String;Lcom/wurmonline/client/renderer/gui/InputFieldListener;II)V");
        assertNotNull(input.getDeclaredField("maxLines"));
        assertNotNull(input.getDeclaredField("maxInput"));
        assertNotNull(input.getDeclaredField("simpleInput"));
        assertNotNull(input.getDeclaredField("prompt"));

        CtClass confirm = classes.get("com.wurmonline.client.renderer.gui.ConfirmWindow");
        assertDeclaredConstructor(confirm,
                "(Lcom/wurmonline/client/renderer/gui/ConfirmListener;Ljava/lang/String;Ljava/lang/String;)V");
        assertMethod(confirm, "close", "()V");

        CtClass listener = classes.get("com.wurmonline.client.renderer.gui.ConfirmListener");
        assertMethod(listener, "confirmed", "()V");
        assertMethod(listener, "cancelled", "()V");

        CtClass font = classes.get("com.wurmonline.client.renderer.gui.text.TextFont");
        assertMethod(font, "getFixedSizeText",
                "()Lcom/wurmonline/client/renderer/gui/text/TextFont;");
        assertMethod(font, "moveTo", "(II)V");
        assertMethod(font, "paint",
                "(Lcom/wurmonline/client/renderer/backend/Queue;Ljava/lang/String;FFFF)I");
    }

    private static void assertMethod(CtClass owner, String name, String descriptor)
            throws Exception {
        assertNotNull(owner.getMethod(name, descriptor));
    }

    private static void assertDeclaredMethod(CtClass owner, String name,
                                             String descriptor) throws Exception {
        CtMethod match = null;
        for (CtMethod method : owner.getDeclaredMethods(name)) {
            if (descriptor.equals(method.getSignature())) {
                match = method;
                break;
            }
        }
        assertNotNull(match);
    }

    private static void assertDeclaredConstructor(CtClass owner,
                                                  String descriptor) {
        javassist.CtConstructor match = null;
        for (javassist.CtConstructor constructor : owner.getDeclaredConstructors()) {
            if (descriptor.equals(constructor.getSignature())) {
                match = constructor;
                break;
            }
        }
        assertNotNull(match);
    }
}
