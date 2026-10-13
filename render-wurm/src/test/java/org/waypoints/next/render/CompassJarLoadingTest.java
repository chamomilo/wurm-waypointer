package org.waypoints.next.render;

import javassist.ClassPath;
import javassist.ClassPool;
import javassist.Loader;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

public class CompassJarLoadingTest {
    private static final String ADAPTER =
            "com.wurmonline.client.resources.WaypointerCompassResourceUrl";
    private static final String PREFIX = "org/waypoints/next/compass/";

    @Test
    public void javassistReadsBothLayersWithoutLooseFilesOrResourceVisibility() throws Exception {
        Path jar = Files.createTempFile("waypointer embedded compass ", ".jar");
        String[] layers = {PREFIX + "compass-hud-base.png", PREFIX + "compass-hud-needle.png"};
        byte[][] expected = {read(layers[0]), read(layers[1])};
        final byte[] adapterBytes = read(ADAPTER.replace('.', '/') + ".class");
        final URL adapterOrigin = new URL("jar:" + jar.toUri().toURL()
                + "!/" + ADAPTER.replace('.', '/') + ".class");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            put(output, ADAPTER.replace('.', '/') + ".class", adapterBytes);
            for (int i = 0; i < layers.length; i++) put(output, layers[i], expected[i]);
        }
        ClassPool pool = HookManager.getInstance().getClassPool();
        // Avoid Javassist's own class-byte URL cache retaining the fixture JAR;
        // the handle assertion below concerns artwork streams, not class loading.
        ClassPath fixture = pool.insertClassPath(new ClassPath() {
            @Override public InputStream openClassfile(String name) {
                return ADAPTER.equals(name) ? new ByteArrayInputStream(adapterBytes) : null;
            }
            @Override public URL find(String name) {
                return ADAPTER.equals(name) ? adapterOrigin : null;
            }
        });
        // Reproduce Wurm's class/resource split: the class pool sees the mod JAR,
        // while Loader resource lookup sees only its parent, with no compass PNGs.
        ClassLoader parent = new ClassLoader(getClass().getClassLoader()) {
            @Override public URL getResource(String name) {
                return name.startsWith(PREFIX) ? null : super.getResource(name);
            }
        };
        Loader loader = new Loader(parent, pool);
        loader.delegateLoadingOf("javassist.");
        loader.delegateLoadingOf("org.gotti.wurmunlimited.modloader.");
        try {
            assertNull(loader.getResource(layers[0]));
            assertNull(loader.getResource(layers[1]));
            Class<?> adapter = loader.loadClass(ADAPTER);
            assertNotSame(getClass().getClassLoader(), adapter.getClassLoader());
            assertNull(adapter.getResource('/' + layers[0]));
            for (int i = 0; i < layers.length; i++) {
                Object resource = adapter.getConstructor(String.class).newInstance('/' + layers[i]);
                try (InputStream input = (InputStream) adapter.getMethod("openStream").invoke(resource)) {
                    assertArrayEquals(expected[i], bytes(input));
                }
            }
        } finally {
            pool.removeClassPath(fixture);
            Files.delete(jar);
        }
        assertFalse("texture loading must not retain the JAR handle", Files.exists(jar));
    }

    private static byte[] read(String name) throws Exception {
        try (InputStream input = CompassJarLoadingTest.class.getResourceAsStream('/' + name)) {
            if (input == null) throw new IllegalStateException("Missing fixture input: " + name);
            return bytes(input);
        }
    }

    private static byte[] bytes(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static void put(JarOutputStream output, String name, byte[] bytes) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.write(bytes);
        output.closeEntry();
    }
}
