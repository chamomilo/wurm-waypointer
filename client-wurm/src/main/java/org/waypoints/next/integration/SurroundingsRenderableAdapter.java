package org.waypoints.next.integration;

import com.wurmonline.client.renderer.CreatureData;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.ObjectData;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.cell.PlayerCellRenderable;
import com.wurmonline.shared.util.MaterialUtilities;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.waypoints.next.surroundings.CreatureModifier;
import org.waypoints.next.surroundings.SurroundingEntry;
import org.waypoints.next.surroundings.SurroundingKind;
import org.waypoints.next.surroundings.SurroundingsClassifier;

import java.lang.reflect.Field;
import java.time.Instant;

/** Projects pinned Wurm renderables into the client-independent catalog model. */
final class SurroundingsRenderableAdapter {
    private static final Field GROUND_ITEM_DATA = groundItemDataField();
    private static final Field OBJECT_RAW_NAME = objectRawNameField();

    private SurroundingsRenderableAdapter() { }

    static SurroundingEntry project(Object value, Instant now)
            throws ReflectiveOperationException {
        if (value instanceof PlayerCellRenderable) return null;
        if (value instanceof CreatureCellRenderable) {
            CreatureCellRenderable renderable = (CreatureCellRenderable) value;
            return projectMobile(renderable, renderable.getXPos(),
                    renderable.getYPos(), renderable.getHPos(), now);
        }
        if (value instanceof GroundItemCellRenderable) {
            GroundItemCellRenderable renderable = (GroundItemCellRenderable) value;
            GroundItemData data = ReflectionUtil.getPrivateField(
                    renderable, GROUND_ITEM_DATA);
            if (data == null) return null;
            String name = displayName(data.getName(), renderable.getHoverName());
            String model = string(data.getModelName());
            boolean container = SurroundingsClassifier.isContainer(name, model);
            SurroundingKind kind = container
                    ? SurroundingKind.CONTAINER : SurroundingKind.ITEM;
            String category = container
                    ? SurroundingsClassifier.containerCategory(name, model)
                    : SurroundingsClassifier.itemCategory(name, model);
            return SurroundingEntry.builder().kind(kind).wurmId(renderable.getId())
                    .name(name).shortName(shortName(data)).modelName(model)
                    .description(data.getDescription())
                    .category(category).material(material(data.getMaterialId()))
                    .rarity(data.getRarity()).layer(renderable.getLayer())
                    .position(renderable.getXPos(), renderable.getYPos(),
                            renderable.getHPos())
                    .firstSeenAt(now).updatedAt(now).build();
        }
        return null;
    }

    static SurroundingEntry projectCreature(Object value, double worldX,
                                             double worldY, double height,
                                             Instant now)
            throws ReflectiveOperationException {
        if (value instanceof PlayerCellRenderable
                || !(value instanceof CreatureCellRenderable)) return null;
        CreatureCellRenderable renderable = (CreatureCellRenderable) value;
        return projectMobile(renderable, worldX, worldY, height, now);
    }

    private static SurroundingEntry projectMobile(
            CreatureCellRenderable renderable, double worldX,
            double worldY, double height, Instant now)
            throws ReflectiveOperationException {
        CreatureData data = renderable.getCreatureData();
        if (data == null) return null;
        return projectCreatureData(data, renderable.isItem(), renderable.getId(),
                renderable.getHoverName(), renderable.getLayer(), worldX,
                worldY, height, now);
    }

    static SurroundingEntry projectCreatureData(
            CreatureData data, boolean item, long wurmId, String hoverName,
            int layer, double worldX, double worldY, double height, Instant now)
            throws ReflectiveOperationException {
        if (data == null) return null;
        String name = displayName(data.getName(), hoverName);
        String model = string(data.getModelName());
        if (item) {
            boolean container = SurroundingsClassifier.isContainer(name, model);
            SurroundingKind kind = container
                    ? SurroundingKind.CONTAINER : SurroundingKind.ITEM;
            String category = container
                    ? SurroundingsClassifier.containerCategory(name, model)
                    : SurroundingsClassifier.itemCategory(name, model);
            return SurroundingEntry.builder().kind(kind)
                    .wurmId(wurmId).name(name).shortName(shortName(data))
                    .modelName(model).description(data.getDescription())
                    .category(category).material(material(data.getMaterialId()))
                    .rarity(data.getRarity()).layer(layer)
                    .position(worldX, worldY, height)
                    .firstSeenAt(now).updatedAt(now).build();
        }
        return SurroundingEntry.builder().kind(SurroundingKind.ANIMAL)
                .wurmId(wurmId).name(name).shortName(name)
                .modelName(model)
                .description(data.getDescription())
                .category(SurroundingsClassifier.ANIMALS)
                .material(material(data.getMaterialId()))
                .creatureModifier(CreatureModifier.fromWurmData(
                        data.getModifier(), name, hoverName))
                .uniqueCreature(SurroundingsClassifier.isUniqueCreature(
                        data.getName(), model))
                .rarity(data.getRarity()).layer(layer)
                .position(worldX, worldY, height)
                .firstSeenAt(now).updatedAt(now).build();
    }

    static Long renderableId(Object value) {
        if (value instanceof GroundItemCellRenderable) {
            return ((GroundItemCellRenderable) value).getId();
        }
        if (value instanceof CreatureCellRenderable
                && !(value instanceof PlayerCellRenderable)) {
            return ((CreatureCellRenderable) value).getId();
        }
        return null;
    }

    private static Field groundItemDataField() {
        try {
            return ReflectionUtil.getField(GroundItemCellRenderable.class, "item");
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Field objectRawNameField() {
        try {
            return ReflectionUtil.getField(ObjectData.class, "name");
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static String shortName(ObjectData data)
            throws ReflectiveOperationException {
        Object raw = ReflectionUtil.getPrivateField(data, OBJECT_RAW_NAME);
        return displayName(string(raw), data == null ? "" : data.getName());
    }

    private static String material(byte id) {
        String result = MaterialUtilities.getMaterialString(id);
        return result == null ? "" : result;
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String displayName(String preferred, String fallback) {
        String clean = preferred == null ? "" : preferred.trim();
        if (!clean.isEmpty()) return clean;
        clean = fallback == null ? "" : fallback.trim();
        return clean.isEmpty() ? "Unknown object" : clean;
    }
}
