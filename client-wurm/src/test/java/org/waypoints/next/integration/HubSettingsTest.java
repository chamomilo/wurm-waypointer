package org.waypoints.next.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class HubSettingsTest {
    @Test public void queuedHubWriteCannotOverwriteANewerImmediateRegistryEdit() throws Exception {
        Path path=temporary.getRoot().toPath().resolve("settings.config");
        java.util.concurrent.CountDownLatch applied=new java.util.concurrent.CountDownLatch(2),release=new java.util.concurrent.CountDownLatch(1);
        HubSettings settings=new HubSettings(path,p->applied.countDown());settings.configure(new Properties());
        java.lang.reflect.Field field=HubSettings.class.getDeclaredField("writer");field.setAccessible(true);
        ((java.util.concurrent.ExecutorService)field.get(settings)).execute(()->{try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        Properties older=new Properties();older.setProperty("maximumCompassMarkers","32");settings.save(older);
        settings.saveImmediate("maximumCompassMarkers","48");release.countDown();
        assertTrue(applied.await(5,java.util.concurrent.TimeUnit.SECONDS));
        Properties saved=new Properties();try(java.io.InputStream in=Files.newInputStream(path)){saved.load(in);}
        assertEquals("48",saved.getProperty("maximumCompassMarkers"));assertEquals("48",settings.values().getProperty("maximumCompassMarkers"));
    }
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void updaterRegistryExposesEveryUserPreferenceIncludingDiseasedAndMeteors() {
        HubSettings controller=new HubSettings(temporary.getRoot().toPath().resolve("settings.config"),p->{});controller.configure(new Properties());
        java.util.Set<String> expected=new java.util.HashSet<String>();
        for(org.waypoints.next.ui.SettingSpec spec:org.waypoints.next.ui.SettingSpec.ALL)if(!spec.key.equals("language"))expected.add(spec.key);
        for(org.waypoints.next.surroundings.CreatureHighlightRule rule:org.waypoints.next.surroundings.CreatureHighlightRule.values()) {
            expected.add(rule.key("enabled"));expected.add(rule.key("color"));if(rule.supportsAutoMark())expected.add(rule.key("autoMark"));
        }
        expected.addAll(org.waypoints.next.tiles.TileHighlightSettings.KEYS);
        java.util.Set<String> actual=new java.util.HashSet<String>();WaypointerModSettings registry=new WaypointerModSettings(controller);
        for(org.chamomilo.wurm.update.ModSetting spec:registry.settings()){assertTrue(actual.add(spec.key));spec.validate(registry.value(spec.key));}
        assertEquals(expected,actual);assertTrue(actual.contains("creatureHighlight.diseased.autoMark"));assertTrue(actual.contains("tileHighlight.meteors.chime"));
    }
    @Test public void tilesSurviveGeneralSavesAndRegistryCommitsWithoutChangingCreatureRules() throws Exception {
        Path path=temporary.getRoot().toPath().resolve("settings.config");java.util.concurrent.CountDownLatch applied=new java.util.concurrent.CountDownLatch(2);
        HubSettings controller=new HubSettings(path,p->applied.countDown());controller.configure(new Properties());
        Properties tile=new Properties();tile.setProperty("tileHighlight.nonRideable.enabled","true");tile.setProperty("tileHighlight.nonRideable.color","#12AB34");controller.save(tile);
        controller.saveImmediate("tileHighlight.flowers.enabled","true");assertTrue(applied.await(5,java.util.concurrent.TimeUnit.SECONDS));
        Properties saved=new Properties();try(java.io.InputStream in=Files.newInputStream(path)){saved.load(in);}
        assertEquals("#12AB34",saved.getProperty("tileHighlight.nonRideable.color"));assertEquals("true",saved.getProperty("tileHighlight.flowers.enabled"));
        assertTrue(new org.waypoints.next.tiles.TileHighlightSettings(saved).nonRideable());assertFalse(HubSettings.generalChanged(new Properties(),saved));
        WaypointerModSettings registry=new WaypointerModSettings(controller);assertEquals("#12AB34",registry.value("tileHighlight.nonRideable.color"));
        registry.change("tileHighlight.nonRideable.enabled","false");assertFalse(new org.waypoints.next.tiles.TileHighlightSettings(controller.values()).nonRideable());
    }
    @Test public void highlightAndLanguageSavesDoNotReconfigureGeneralFeatures() {
        Properties initial=new Properties();Properties next=new Properties();
        for(org.waypoints.next.ui.SettingSpec spec:org.waypoints.next.ui.SettingSpec.ALL)next.setProperty(spec.key,spec.defaultValue);
        next.setProperty("creatureHighlight.champion.enabled","true");next.setProperty("language","ru");
        assertFalse(HubSettings.generalChanged(initial,next));
        next.setProperty("scannerProfile","uniques");assertTrue(HubSettings.generalChanged(initial,next));
        assertTrue(HubSettings.generalChanged(null,initial));
    }
    @Test public void newTileRulesCommitThroughRegistryAndSurviveOtherSaves() throws Exception {
        Path path=temporary.getRoot().toPath().resolve("settings.config");HubSettings controller=new HubSettings(path,p->{});controller.configure(new Properties());
        WaypointerModSettings registry=new WaypointerModSettings(controller);
        registry.change("tileHighlight.harvestable.enabled","true");registry.change("tileHighlight.harvestable.color","#12ab34");
        registry.change("tileHighlight.overaged.enabled","true");registry.change("tileHighlight.overaged.color","#aabbcc");registry.change("maximumCompassMarkers","48");
        Properties saved=new Properties();try(java.io.InputStream in=Files.newInputStream(path)){saved.load(in);}
        org.waypoints.next.tiles.TileHighlightSettings tiles=new org.waypoints.next.tiles.TileHighlightSettings(saved);
        assertTrue(tiles.harvestable());assertTrue(tiles.overaged());assertTrue(tiles.enabled());
        assertEquals("#12AB34",tiles.harvestableHex());assertEquals("#AABBCC",tiles.overagedHex());assertFalse(HubSettings.generalChanged(new Properties(),tileOnly(saved)));
        try{registry.change("tileHighlight.overaged.color","bad");fail("Invalid colour accepted");}catch(IllegalArgumentException expected){}
        assertEquals("#AABBCC",registry.value("tileHighlight.overaged.color"));
    }
    @Test public void launchDisablesSavedTerrainSwitchesButRetainsColorsAndMeteorPreferences() throws Exception {
        Properties source=new Properties();for(String key:Arrays.asList("tileHighlight.nonRideable.enabled","tileHighlight.flowers.enabled","tileHighlight.harvestable.enabled","tileHighlight.overaged.enabled"))source.setProperty(key,"true");
        source.setProperty("tileHighlight.harvestable.color","#12AB34");source.setProperty("tileHighlight.overaged.color","#AABBCC");
        HubSettings controller=new HubSettings(temporary.getRoot().toPath().resolve("settings.config"),p->{});controller.configure(source);
        org.waypoints.next.tiles.TileHighlightSettings tiles=new org.waypoints.next.tiles.TileHighlightSettings(controller.values());
        assertFalse(tiles.enabled());assertTrue(tiles.meteors());assertTrue(tiles.meteorMark());assertTrue(tiles.meteorChime());
        assertEquals("#12AB34",tiles.harvestableHex());assertEquals("#AABBCC",tiles.overagedHex());assertEquals("true",source.getProperty("tileHighlight.harvestable.enabled"));
        WaypointerModSettings registry=new WaypointerModSettings(controller);registry.change("tileHighlight.harvestable.enabled","true");
        registry.change("tileHighlight.meteors.enabled","false");registry.change("tileHighlight.meteors.autoMark","false");registry.change("tileHighlight.meteors.chime","false");
        assertTrue(new org.waypoints.next.tiles.TileHighlightSettings(controller.values()).harvestable());
        controller.configure(controller.values());tiles=new org.waypoints.next.tiles.TileHighlightSettings(controller.values());
        assertFalse(tiles.enabled());assertFalse(tiles.meteors());assertFalse(tiles.meteorMark());assertFalse(tiles.meteorChime());assertEquals("#12AB34",tiles.harvestableHex());
    }
    private static Properties tileOnly(Properties values){Properties tiles=new Properties();for(String key:org.waypoints.next.tiles.TileHighlightSettings.KEYS)tiles.setProperty(key,values.getProperty(key));return tiles;}
    @Test public void registryRadiusIsLiveBoundedAndDoesNotResetManualHighlightActivation() throws Exception {
        HubSettings controller=new HubSettings(temporary.getRoot().toPath().resolve("settings.config"),p->{});controller.configure(new Properties());WaypointerModSettings registry=new WaypointerModSettings(controller);
        registry.change("tileHighlight.harvestable.enabled","true");registry.change("tileHighlight.radiusTiles","3");Properties saved=controller.values();
        assertEquals(3,new org.waypoints.next.tiles.TileHighlightSettings(saved).radius());assertTrue(new org.waypoints.next.tiles.TileHighlightSettings(saved).harvestable());assertFalse(HubSettings.generalChanged(new Properties(),tileOnly(saved)));
        for(String invalid:Arrays.asList("0","17","3.5","bad"))try{registry.change("tileHighlight.radiusTiles",invalid);fail("Invalid radius accepted");}catch(IllegalArgumentException expected){}
        assertEquals("3",registry.value("tileHighlight.radiusTiles"));
        for(org.chamomilo.wurm.update.ModSetting spec:registry.settings())if(spec.key.equals("tileHighlight.radiusTiles")){assertTrue(spec.live);assertEquals(1,spec.minimum,0);assertEquals(16,spec.maximum,0);assertEquals("7",spec.defaultValue);assertTrue(spec.description.contains("15 x 15"));}
    }
    @Test public void highlightSwitchesAndColorsPersistAndSurviveGeneralSettingsSave() throws Exception {
        Path path=temporary.getRoot().toPath().resolve("settings.config");Files.write(path,"# Highlight preferences\ncustom=keep\n".getBytes(StandardCharsets.UTF_8));
        java.util.concurrent.CountDownLatch written=new java.util.concurrent.CountDownLatch(2);
        HubSettings settings=new HubSettings(path,p->written.countDown());Properties initial=new Properties();initial.setProperty("custom","keep");settings.configure(initial);
        Properties highlight=new Properties();highlight.setProperty("creatureHighlight.champion.enabled","true");highlight.setProperty("creatureHighlight.champion.color","#12AB34");highlight.setProperty("creatureHighlight.unique.autoMark","true");settings.save(highlight);
        Properties general=new Properties();general.setProperty("maximumCompassMarkers","32");settings.save(general);
        assertTrue(written.await(5,java.util.concurrent.TimeUnit.SECONDS));Properties saved=new Properties();try(java.io.InputStream in=Files.newInputStream(path)){saved.load(in);}
        assertEquals("true",saved.getProperty("creatureHighlight.champion.enabled"));assertEquals("#12AB34",saved.getProperty("creatureHighlight.champion.color"));assertEquals("true",saved.getProperty("creatureHighlight.unique.autoMark"));assertEquals("32",saved.getProperty("maximumCompassMarkers"));assertEquals("keep",saved.getProperty("custom"));
        assertTrue(Files.readAllLines(path,StandardCharsets.UTF_8).get(0).startsWith("# Highlight preferences"));
    }
    @Test public void sharedLanguageChangesImmediatelyAndPersistsInTheBackground() throws Exception {
        Path path = temporary.getRoot().toPath().resolve("settings.config");
        String before = "# User settings\nlanguage=en\ncustom=keep\n";
        Files.write(path, before.getBytes(StandardCharsets.UTF_8));
        java.util.concurrent.CountDownLatch applied = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Properties> pending = new java.util.concurrent.atomic.AtomicReference<>();
        HubSettings settings = new HubSettings(path, p -> { pending.set(p); applied.countDown(); });
        Properties initial = new Properties();
        initial.setProperty("language", "en");
        initial.setProperty("custom", "keep");
        settings.configure(initial);
        settings.setUserLanguage("ru");
        assertEquals("ru", org.waypoints.next.i18n.Messages.language());
        assertEquals("ru", settings.values().getProperty("language"));
        assertTrue("Language save did not finish", applied.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("ru", pending.get().getProperty("language"));
        assertEquals("ru", settings.values().getProperty("language"));
        assertEquals("keep", pending.get().getProperty("custom"));
        assertEquals("ru", org.waypoints.next.i18n.Messages.language());
        Properties saved = new Properties();
        try (java.io.InputStream in = Files.newInputStream(path)) { saved.load(in); }
        assertEquals("ru", saved.getProperty("language"));
        assertEquals("keep", saved.getProperty("custom"));
        assertEquals(before, new String(Files.readAllBytes(path.resolveSibling("settings.config.bak")), StandardCharsets.UTF_8));
    }
    @Test public void settingsDraftCannotOverrideTheUpdatersLatestLanguage() throws Exception {
        java.util.concurrent.CountDownLatch saved=new java.util.concurrent.CountDownLatch(2);
        Path path=temporary.getRoot().toPath().resolve("settings.config");
        HubSettings settings=new HubSettings(path,p->saved.countDown());
        Properties initial=new Properties();initial.setProperty("language","en");settings.configure(initial);
        Properties stale=settings.values();stale.setProperty("language","de");
        settings.setUserLanguage("ru");settings.save(stale);
        assertEquals("ru",org.waypoints.next.i18n.Messages.language());
        assertEquals("ru",settings.values().getProperty("language"));
        assertTrue(saved.await(5,java.util.concurrent.TimeUnit.SECONDS));
        Properties persisted=new Properties();try(java.io.InputStream input=Files.newInputStream(path)){persisted.load(input);}
        assertEquals("ru",persisted.getProperty("language"));
    }
    @Test public void unsupportedAndUnchangedSharedLanguagesKeepLocalChoice() {
        Path path = temporary.getRoot().toPath().resolve("settings.config");
        java.util.concurrent.atomic.AtomicInteger applied = new java.util.concurrent.atomic.AtomicInteger();
        HubSettings settings = new HubSettings(path, p -> applied.incrementAndGet());
        Properties initial = new Properties(); initial.setProperty("language", "en");
        settings.configure(initial);
        settings.setUserLanguage("fr"); settings.setUserLanguage(null); settings.setUserLanguage("en");
        assertEquals("en", settings.values().getProperty("language"));
        assertEquals(0, applied.get()); assertFalse(Files.exists(path));
    }
    @Test public void preservesCommentsUnknownValuesBackupAndLoaderCompatibleUnicode()throws Exception{
        Path path=temporary.getRoot().toPath().resolve("settings.config");
        String before="# Keep this comment\r\nlanguage: en\r\nscannerExcludedNames=horse,\\\r\n wolf\r\ncustom=value\\: unchanged\r\n";
        Files.write(path,before.getBytes(StandardCharsets.UTF_8));
        Properties values=new Properties();values.setProperty("language","ru");values.setProperty("scannerExcludedNames","bear");values.setProperty("waypointDataFile","data/точки.wpt");
        HubSettings.write(path,values);
        Properties loaded=new Properties();try(java.io.InputStream in=Files.newInputStream(path)){loaded.load(in);}
        assertEquals("ru",loaded.getProperty("language"));assertEquals("bear",loaded.getProperty("scannerExcludedNames"));assertEquals("data/точки.wpt",loaded.getProperty("waypointDataFile"));assertEquals("value: unchanged",loaded.getProperty("custom"));assertNull(loaded.getProperty("wolf"));
        assertTrue(new String(Files.readAllBytes(path),StandardCharsets.UTF_8).startsWith("# Keep this comment"));
        assertEquals(before,new String(Files.readAllBytes(path.resolveSibling("settings.config.bak")),StandardCharsets.UTF_8));
    }
    @Test public void rejectsInvalidDraftWithoutChangingCurrentPreferencesOrApplying(){
        java.util.concurrent.atomic.AtomicInteger applied=new java.util.concurrent.atomic.AtomicInteger();HubSettings settings=new HubSettings(temporary.getRoot().toPath().resolve("settings.config"),p->applied.incrementAndGet());
        Properties initial=new Properties();initial.setProperty("language","en");settings.configure(initial);
        Properties draft=settings.values();draft.setProperty("maximumCompassMarkers","garbage");
        try{settings.save(draft);fail("Bad numeric setting accepted");}catch(IllegalArgumentException expected){}
        assertEquals("en",settings.values().getProperty("language"));assertEquals(0,applied.get());assertFalse(Files.exists(temporary.getRoot().toPath().resolve("settings.config")));
    }
}
