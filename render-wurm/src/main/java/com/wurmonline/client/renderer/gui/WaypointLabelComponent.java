package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.WaypointScreenProjector;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import org.waypoints.next.render.WaypointDistanceLabel;
import org.waypoints.next.render.WaypointGroundHeight;
import org.waypoints.next.render.WaypointLabelOpacity;
import org.waypoints.next.render.WaypointRenderProfiler;

import java.util.List;
import java.util.logging.Logger;

/** Non-interactive top HUD label centered on a waypoint's projected beam. */
public final class WaypointLabelComponent extends StaticComponent {
    private static final Logger LOGGER = Logger.getLogger("WurmWaypointer.WaypointLabel");
    private static final int HORIZONTAL_PADDING = 3;
    private static final int VERTICAL_PADDING = 1;
    private static final int EDGE_MARGIN = 4;
    private static long nextCreationOrder;

    private final WaypointLabelLayoutCoordinator layout;
    private final long creationOrder;
    private final World world;
    private final float worldX;
    private final float worldY;
    private final float worldHeight;
    private final int targetLayer;
    private final boolean groundAnchored;
    private final String waypointName;
    private final long expiresAtEpochMillis;
    private final float textRed;
    private final float textGreen;
    private final float textBlue;
    private final float textAlpha;
    private final TextFont font;
    private int viewportWidth = 1;
    private int lastDistanceMetres = -1;
    private long lastRemainingSeconds = Long.MIN_VALUE;
    private String displayText;
    private int projectedCenter;
    private int drawnCenter;
    private int layoutOrder;
    private int measuredLeft;
    private int measuredWidth = 1;
    private int measuredHeight = 1;
    private boolean layoutReady;
    private boolean firstRenderLogged;

    private WaypointLabelComponent(WaypointLabelLayoutCoordinator layout,
                                   int layoutOrder,
                                   World world, float worldX, float worldY,
                                   float worldHeight, String waypointName,
                                   float red, float green, float blue, float alpha,
                                   long expiresAtEpochMillis,
                                   int targetLayer, boolean groundAnchored) {
        super("Wurm Waypointer waypoint label", 0, 0, 1, 1);
        this.layout = layout;
        this.layoutOrder = layoutOrder;
        this.creationOrder = claimCreationOrder();
        this.world = world;
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldHeight = worldHeight;
        this.targetLayer = targetLayer;
        this.groundAnchored = groundAnchored;
        this.waypointName = waypointName;
        this.expiresAtEpochMillis = expiresAtEpochMillis;
        // Pastel tint keeps the marker identity while the one-pixel black
        // shadow provides contrast without an eye-catching solid backdrop.
        this.textRed = 0.65f + red * 0.35f;
        this.textGreen = 0.65f + green * 0.35f;
        this.textBlue = 0.65f + blue * 0.35f;
        // Labels are navigation UI, not part of the translucent world effect.
        // Keep them fully legible regardless of the marker's configured alpha.
        this.textAlpha = WaypointLabelOpacity.textAlpha(alpha);
        this.font = TextFont.getFixedSizeText();
        this.displayText = waypointName;
    }

    public static WaypointLabelComponent attach(HeadsUpDisplay hud, World world,
                                                float worldX, float worldY,
                                                float worldHeight, String waypointName,
                                                float red, float green, float blue,
                                                float alpha) {
        return attachWithLayout(hud, new WaypointLabelLayoutCoordinator(), 0,
                world, worldX, worldY, worldHeight, waypointName,
                red, green, blue, alpha, 0L, 0, false);
    }

    public static WaypointLabelComponent attachManaged(
                                                HeadsUpDisplay hud,
                                                WaypointLabelLayoutCoordinator layout,
                                                int layoutOrder,
                                                World world,
                                                float worldX, float worldY,
                                                float worldHeight, String waypointName,
                                                float red, float green, float blue,
                                                float alpha) {
        return attachManaged(hud, layout, layoutOrder, world, worldX, worldY,
                worldHeight, waypointName, red, green, blue, alpha, 0L);
    }

    public static WaypointLabelComponent attachManaged(
                                                HeadsUpDisplay hud,
                                                WaypointLabelLayoutCoordinator layout,
                                                int layoutOrder,
                                                World world,
                                                float worldX, float worldY,
                                                float worldHeight, String waypointName,
                                                float red, float green, float blue,
                                                float alpha,
                                                long expiresAtEpochMillis) {
        if (layout == null) throw new IllegalArgumentException(
                "label layout coordinator is required");
        return attachWithLayout(hud, layout, layoutOrder,
                world, worldX, worldY, worldHeight, waypointName,
                red, green, blue, alpha, expiresAtEpochMillis, 0, false);
    }

    public static WaypointLabelComponent attachManaged(
                                                HeadsUpDisplay hud,
                                                WaypointLabelLayoutCoordinator layout,
                                                int layoutOrder,
                                                World world,
                                                float worldX, float worldY,
                                                float worldHeight,
                                                int targetLayer,
                                                boolean groundAnchored,
                                                String waypointName,
                                                float red, float green, float blue,
                                                float alpha,
                                                long expiresAtEpochMillis) {
        if (layout == null) throw new IllegalArgumentException(
                "label layout coordinator is required");
        return attachWithLayout(hud, layout, layoutOrder,
                world, worldX, worldY, worldHeight, waypointName,
                red, green, blue, alpha, expiresAtEpochMillis,
                targetLayer, groundAnchored);
    }

    private static WaypointLabelComponent attachWithLayout(
                                                HeadsUpDisplay hud,
                                                WaypointLabelLayoutCoordinator layout,
                                                int layoutOrder,
                                                World world,
                                                float worldX, float worldY,
                                                float worldHeight, String waypointName,
                                                float red, float green, float blue,
                                                float alpha,
                                                long expiresAtEpochMillis,
                                                int targetLayer,
                                                boolean groundAnchored) {
        WaypointLabelComponent component = new WaypointLabelComponent(
                layout, layoutOrder, world, worldX, worldY, worldHeight,
                waypointName, red, green, blue, alpha, expiresAtEpochMillis,
                targetLayer, groundAnchored);
        component.updateViewport(hud.getWidth());
        // Prepare bounds before the component enters the HUD render list. The
        // pinned client establishes its scissor from those bounds before it
        // invokes renderComponent().
        layout.register(component);
        component.prepareNextFrame();
        component.attachTo(hud);
        return component;
    }

    public void updateViewport(int viewportWidth) {
        this.viewportWidth = Math.max(1, viewportWidth);
        if (width <= 1) {
            // Until the first projection, expose only a one-pixel hit area.
            setLocation(0, WaypointLabelLayoutCoordinator.TOP_OFFSET, 1,
                    font.getHeight() + VERTICAL_PADDING * 2);
        }
    }

    public boolean isAttachedTo(HeadsUpDisplay hud) {
        if (hud == null) return false;
        List<WurmComponent> components = hud.getComponents();
        synchronized (components) {
            return components.contains(this);
        }
    }

    public void detach(HeadsUpDisplay hud) {
        if (hud == null) return;
        layout.unregister(this);
        List<WurmComponent> components = hud.getComponents();
        synchronized (components) {
            components.remove(this);
        }
    }

    @Override
    protected void renderComponent(Queue queue, float ignoredAlpha) {
        long profileStartedNanos = System.nanoTime();
        if (!layoutReady) {
            prepareNextFrame();
            WaypointRenderProfiler.recordLabel(
                    System.nanoTime() - profileStartedNanos);
            return;
        }

        // Draw with the bounds that WurmComponent.render() used to establish
        // this frame's scissor. Re-project only after queuing the current
        // frame, so camera motion cannot move the drawing outside that clip.
        int left = x;
        int top = y;
        font.moveTo(left + HORIZONTAL_PADDING + 1,
                top + VERTICAL_PADDING + font.getAscent() + 1);
        font.paint(queue, displayText, 0.0f, 0.0f, 0.0f,
                Math.min(1.0f, textAlpha + 0.2f));
        font.moveTo(left + HORIZONTAL_PADDING,
                top + VERTICAL_PADDING + font.getAscent());
        font.paint(queue, displayText,
                textRed, textGreen, textBlue, textAlpha);

        if (!firstRenderLogged) {
            firstRenderLogged = true;
            LOGGER.info("Waypoint label rendered its first frame: name=\""
                    + displayText + "\", projectedScreenX=" + projectedCenter
                    + ", drawnCenterX=" + drawnCenter + ", hudWidth=" + viewportWidth
                    + ", topOffset=" + top + ", backdrop=none"
                    + ", textColor=" + textRed + "," + textGreen + ","
                    + textBlue + "," + textAlpha);
        }
        prepareNextFrame();
        WaypointRenderProfiler.recordLabel(System.nanoTime() - profileStartedNanos);
    }

    private void attachTo(HeadsUpDisplay hud) {
        List<WurmComponent> components = hud.getComponents();
        synchronized (components) {
            if (!components.contains(this)) components.add(this);
        }
    }

    private void prepareNextFrame() {
        layout.prepareNextFrame(this);
    }

    void measureForLayout() {
        float projectedHeight = groundAnchored
                ? WaypointGroundHeight.resolve(world, worldX, worldY,
                targetLayer, worldHeight) : worldHeight;
        float normalizedX = WaypointScreenProjector.projectNormalizedX(
                world, worldX, worldY, projectedHeight);
        if (Float.isNaN(normalizedX)) return;

        int distanceMetres = WaypointDistanceLabel.roundedMeters(
                world.getPlayerPosX(), world.getPlayerPosY(), worldX, worldY);
        long remainingSeconds = WaypointDistanceLabel.remainingSeconds(
                expiresAtEpochMillis, System.currentTimeMillis());
        if (distanceMetres != lastDistanceMetres
                || remainingSeconds != lastRemainingSeconds) {
            lastDistanceMetres = distanceMetres;
            lastRemainingSeconds = remainingSeconds;
            displayText = WaypointDistanceLabel.format(waypointName,
                    distanceMetres, remainingSeconds);
        }
        int textWidth = font.getWidth(displayText);
        int boxWidth = textWidth + HORIZONTAL_PADDING * 2;
        int boxHeight = font.getHeight() + VERTICAL_PADDING * 2;
        projectedCenter = Float.isInfinite(normalizedX)
                ? (normalizedX > 0.0f ? viewportWidth + 1 : -1)
                : Math.round(normalizedX * viewportWidth);
        int minimumCenter = boxWidth / 2 + EDGE_MARGIN;
        int maximumCenter = Math.max(minimumCenter,
                viewportWidth - boxWidth / 2 - EDGE_MARGIN);
        drawnCenter = Math.max(minimumCenter,
                Math.min(maximumCenter, projectedCenter));
        measuredLeft = drawnCenter - boxWidth / 2;
        measuredWidth = boxWidth;
        measuredHeight = boxHeight;
        layoutReady = true;
    }

    void applyMeasuredTop(int top) {
        if (!layoutReady) return;
        if (x != measuredLeft || y != top || width != measuredWidth
                || height != measuredHeight) {
            // The component's bounds must match the visible box. A full-width
            // transparent component can become HUD mouse focus and crash the
            // pinned client if getComponentAt returns null.
            setLocation(measuredLeft, top, measuredWidth, measuredHeight);
        }
    }

    public void updateLayoutOrder(int order) {
        layoutOrder = order;
    }

    int layoutOrder() { return layoutOrder; }
    long creationOrder() { return creationOrder; }
    int measuredLeft() { return measuredLeft; }
    int measuredWidth() { return measuredWidth; }
    int measuredHeight() { return measuredHeight; }

    private static synchronized long claimCreationOrder() {
        return nextCreationOrder++;
    }

    /**
     * The pinned HUD dereferences this result without a null check after it
     * has selected the component by bounds. Returning this is therefore the
     * only safe no-op focus behavior for the small visible label rectangle.
     */
    @Override
    public WaypointLabelComponent getComponentAt(int mouseX, int mouseY) {
        return this;
    }
}
