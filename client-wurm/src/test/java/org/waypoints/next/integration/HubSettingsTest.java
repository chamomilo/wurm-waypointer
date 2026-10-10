package org.waypoints.next.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class HubSettingsTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
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
