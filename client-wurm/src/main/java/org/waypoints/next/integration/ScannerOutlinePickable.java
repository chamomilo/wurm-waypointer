package org.waypoints.next.integration;

import com.wurmonline.client.renderer.Color;
import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.SubPickableUnit;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.backend.RenderState;
import com.wurmonline.client.resources.textures.Texture;
import org.waypoints.next.surroundings.ScannerColor;

import java.util.List;

/** Delegates Wurm's native picked-object geometry with a profile colour. */
public final class ScannerOutlinePickable implements PickableUnit {
    private final PickableUnit delegate;
    private final Color color;

    ScannerOutlinePickable(PickableUnit delegate, ScannerColor value) {
        if (delegate == null || value == null) {
            throw new IllegalArgumentException("delegate and color are required");
        }
        this.delegate = delegate;
        color = new Color(value.getRed(), value.getGreen(),
                value.getBlue(), value.getAlpha());
    }

    @Override public void getHoverDescription(PickData pickData) {
        delegate.getHoverDescription(pickData);
    }
    @Override public String getHoverName() { return delegate.getHoverName(); }
    @Override public void renderPicked(Queue queue, RenderState state, Color value) {
        delegate.renderPicked(queue, state, value);
    }
    @Override public long getId() { return delegate.getId(); }
    @Override public Color getOutlineColor() { return color; }
    @Override public void pick(Queue queue, boolean close) { delegate.pick(queue, close); }
    @Override public boolean targetMatches(int targetType) {
        return delegate.targetMatches(targetType);
    }
    @Override public List<SubPickableUnit> getSubPickableUnitList() {
        return delegate.getSubPickableUnitList();
    }
    @Override public Texture getIconTexture() { return delegate.getIconTexture(); }
    @Override public short getIconId() { return delegate.getIconId(); }
    @Override public void preparePick() { delegate.preparePick(); }
}
