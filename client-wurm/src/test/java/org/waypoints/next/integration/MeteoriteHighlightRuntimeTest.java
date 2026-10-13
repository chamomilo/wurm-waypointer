package org.waypoints.next.integration;

import org.junit.Test;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.tiles.TileHighlightSettings;
import java.util.Properties;
import static org.junit.Assert.*;

public class MeteoriteHighlightRuntimeTest {
    private static SurroundingEntry meteor(long id){return SurroundingEntry.builder().kind(SurroundingKind.ITEM).category("Resources").wurmId(id).name("meteorite").modelName("model.resource.meteorite").position(128,256,10).build();}
    @Test public void nativeOutlineUsesMeteorColorAndVanishesOnUnload() {
        com.wurmonline.client.renderer.PickableUnit pick=(com.wurmonline.client.renderer.PickableUnit)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{com.wurmonline.client.renderer.PickableUnit.class},(p,m,a)->m.getName().equals("getId")?701L:null);
        MeteoriteHighlightRuntime runtime=new MeteoriteHighlightRuntime();Properties settings=new Properties();settings.setProperty(TileHighlightSettings.METEORS,"true");settings.setProperty(TileHighlightSettings.METEOR_COLOR,"#12AB34");runtime.configure(settings);runtime.observe(meteor(701),pick);
        assertEquals(1,runtime.outlines().size());assertEquals(701,runtime.outlines().get(0).getId());
        assertEquals(18/255f,runtime.outlines().get(0).getOutlineColor().r,0);assertEquals(171/255f,runtime.outlines().get(0).getOutlineColor().g,0);
        runtime.remove(meteor(701).getKey());assertTrue(runtime.outlines().isEmpty());
    }
    @Test public void independentMarkAndChimeDoNotRepeatAfterUpsertsUnloadOrOutlineChanges() {
        MeteoriteHighlightRuntime runtime=new MeteoriteHighlightRuntime();SurroundingEntry first=meteor(701),second=meteor(702);Object object=new Object();
        Properties disabled=new Properties();disabled.setProperty(TileHighlightSettings.METEORS,"false");disabled.setProperty(TileHighlightSettings.METEOR_MARK,"false");disabled.setProperty(TileHighlightSettings.METEOR_CHIME,"false");runtime.configure(disabled);
        runtime.observe(first,object);assertEquals(1,runtime.count());assertTrue(runtime.outlines().isEmpty());assertTrue(runtime.pendingMarks().isEmpty());assertFalse(runtime.pollChime());
        Properties settings=new Properties();settings.setProperty(TileHighlightSettings.METEORS,"false");settings.setProperty(TileHighlightSettings.METEOR_MARK,"true");settings.setProperty(TileHighlightSettings.METEOR_CHIME,"true");runtime.configure(settings);
        assertTrue(runtime.outlines().isEmpty());
        assertEquals(1,runtime.pendingMarks().size());assertTrue(runtime.pollChime());assertFalse(runtime.pollChime());
        assertEquals("Creation can retry until confirmed",1,runtime.pendingMarks().size());runtime.marked(first.getKey());
        long revision=runtime.revision();runtime.observe(first,object);assertEquals(revision,runtime.revision());assertTrue(runtime.pendingMarks().isEmpty());assertFalse(runtime.pollChime());
        runtime.remove(first.getKey());assertEquals(0,runtime.count());runtime.observe(first,new Object());assertTrue(runtime.pendingMarks().isEmpty());assertFalse(runtime.pollChime());
        runtime.observe(second,new Object());assertEquals(1,runtime.pendingMarks().size());assertTrue(runtime.pollChime());assertFalse(runtime.pollChime());
        runtime.clearLoaded();assertEquals(0,runtime.count());runtime.observe(first,new Object());assertTrue(runtime.pendingMarks().isEmpty());assertFalse(runtime.pollChime());
        runtime.endSession();runtime.observe(first,new Object());assertEquals(1,runtime.pendingMarks().size());assertTrue(runtime.pollChime());
    }
    @Test public void meteorDefaultsEnableOutlineMarkAndOneChimeForANewDetection() {
        MeteoriteHighlightRuntime runtime=new MeteoriteHighlightRuntime();
        com.wurmonline.client.renderer.PickableUnit pick=(com.wurmonline.client.renderer.PickableUnit)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{com.wurmonline.client.renderer.PickableUnit.class},(p,m,a)->m.getName().equals("getId")?703L:null);
        runtime.observe(meteor(703),pick);assertEquals(1,runtime.outlines().size());assertEquals(1,runtime.pendingMarks().size());assertTrue(runtime.pollChime());assertFalse(runtime.pollChime());
    }
    @Test public void wrongKindsAndOrdinaryResourcesDoNotTriggerMeteorAlerts() {
        assertFalse(MeteoritePolicy.matches(SurroundingEntry.builder().kind(SurroundingKind.ANIMAL).category("Resources").wurmId(1).name("meteorite").modelName("model.creature.horse").position(4,4,0).build()));
        assertFalse(MeteoritePolicy.matches(SurroundingEntry.builder().kind(SurroundingKind.ITEM).category("Resources").wurmId(2).name("adamantine lump").modelName("model.resource.lump").position(4,4,0).build()));
        assertFalse(MeteoritePolicy.matches(SurroundingEntry.builder().kind(SurroundingKind.ITEM).category("Resources").wurmId(3).name("meteorite").modelName("model.decoration.lamp").position(4,4,0).build()));
        assertTrue(MeteoritePolicy.matches(SurroundingEntry.builder().kind(SurroundingKind.ITEM).category("Resources").wurmId(4).name("метеорит").modelName("model.resource.rock").position(4,4,0).build()));
    }
}
