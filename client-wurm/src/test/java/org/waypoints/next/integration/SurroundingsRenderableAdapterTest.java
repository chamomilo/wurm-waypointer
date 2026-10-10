package org.waypoints.next.integration;

import com.wurmonline.client.renderer.CreatureData;
import org.junit.Test;
import org.waypoints.next.surroundings.SurroundingEntry;
import org.waypoints.next.surroundings.SurroundingKind;
import org.waypoints.next.surroundings.SurroundingsClassifier;

import java.time.Instant;

import static org.junit.Assert.assertEquals;

public class SurroundingsRenderableAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

    @Test public void realClientCreatureCanEnterTheNearbyCatalog() throws Exception {
        CreatureData data = new CreatureData(705L,"model.creature.horse","horse",(byte)0,
                128f,256f,4f,0f,0,false,(byte)0,0L,(byte)0);
        java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        com.wurmonline.client.renderer.cell.CreatureCellRenderable renderable =
                (com.wurmonline.client.renderer.cell.CreatureCellRenderable) unsafe.allocateInstance(
                        com.wurmonline.client.renderer.cell.CreatureCellRenderable.class);
        java.lang.reflect.Field creatureField = renderable.getClass().getDeclaredField("creature");
        creatureField.setAccessible(true);creatureField.set(renderable,data);
        renderable.setAttitude(2);
        SurroundingEntry entry = SurroundingsRenderableAdapter.projectCreature(renderable,128,256,4,NOW);
        assertEquals(SurroundingKind.ANIMAL,entry.getKind());
        assertEquals(org.waypoints.next.surroundings.CreatureHostility.HOSTILE,entry.getHostility());
        SurroundingsRuntime runtime = new SurroundingsRuntime(java.util.logging.Logger.getAnonymousLogger());
        org.junit.Assert.assertNotNull(runtime.creatureMoved(renderable,128,256,4));
        assertEquals(1,runtime.snapshot(org.waypoints.next.surroundings.SurroundingsQuery.builder()
                .kind(SurroundingKind.ANIMAL).build(),128,256).getRows().size());
    }

    @Test public void mobileWagonIsProjectedAsVehicleContainer() throws Exception {
        SurroundingEntry entry = project(
                701L, "large wagon", "model.vehicle.wagon.large", true);

        assertEquals(SurroundingKind.CONTAINER, entry.getKind());
        assertEquals(SurroundingsClassifier.VEHICLES, entry.getCategory());
    }

    @Test public void hitchingPostIsProjectedAsItemInsteadOfAnimal() throws Exception {
        SurroundingEntry entry = project(702L, "hitching post",
                "model.structure.hitching.post", true);

        assertEquals(SurroundingKind.ITEM, entry.getKind());
        assertEquals(SurroundingsClassifier.OTHER_ITEMS, entry.getCategory());
    }

    @Test public void ordinaryCreatureRemainsAnAnimal() throws Exception {
        SurroundingEntry entry = project(703L, "old horse",
                "model.creature.quadraped.horse", false);

        assertEquals(SurroundingKind.ANIMAL, entry.getKind());
        assertEquals(SurroundingsClassifier.ANIMALS, entry.getCategory());
    }

    @Test public void receivedAttitudeAndTraitsAreIndependentOfCondition() throws Exception {
        CreatureData data = new CreatureData(704L,"model.creature.horse","champion horse",(byte)0,
                128f,256f,4f,0f,0,false,(byte)0,0L,(byte)0);
        data.setDescription("Traits: strong legs, fleeter movement");
        SurroundingEntry entry = SurroundingsRenderableAdapter.projectCreatureData(data,false,704L,
                "champion horse",0,128d,256d,4d,NOW,4);
        assertEquals(org.waypoints.next.surroundings.CreatureHostility.HOSTILE,entry.getHostility());
        assertEquals("strong legs, fleeter movement",entry.getTraits());
        assertEquals(org.waypoints.next.surroundings.CreatureModifier.CHAMPION,entry.getCreatureModifier());
        assertEquals(entry.getHostility(),entry.withDeedStatus(org.waypoints.next.surroundings.DeedStatus.ON_DEED).getHostility());
        assertEquals("",SurroundingsRenderableAdapter.receivedTraits("champion horse","bred horse"));
    }

    private static SurroundingEntry project(long id, String name, String model,
                                            boolean item) throws Exception {
        CreatureData data = new CreatureData(id, model, name, (byte) 0,
                128.0f, 256.0f, 4.0f, 0.0f, 0, false,
                (byte) 0, 0L, (byte) 0);
        return SurroundingsRenderableAdapter.projectCreatureData(
                data, item, id, name, 0, 128.0d, 256.0d, 4.0d, NOW);
    }
}
