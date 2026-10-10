package org.waypoints.next.navigation;

import com.sun.net.httpserver.HttpServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.map.ServerMapProfile;
import org.waypoints.next.model.*;
import org.waypoints.next.integration.WaypointClientConfiguration;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import static org.junit.Assert.*;

public class SklotopolisHighwayBrowsingTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void selectedWorldRoadsUseSeparateCacheAndNeverReplaceNavigationRoads() throws Exception {
        HttpServer http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger requests=new AtomicInteger();
        for(int world:new int[]{1,2}){
            byte[] bytes=("[{\"startX\":"+world+",\"startY\":1,\"endX\":"+world+",\"endY\":3,\"type\":2}]").getBytes(StandardCharsets.UTF_8);
            http.createContext("/"+world+"/highways.json",exchange->{
                requests.incrementAndGet();exchange.sendResponseHeaders(200,bytes.length);
                try(java.io.OutputStream out=exchange.getResponseBody()){out.write(bytes);}
            });
        }
        http.start();
        SklotopolisHighwayService connected=new SklotopolisHighwayService(Logger.getAnonymousLogger());
        SklotopolisHighwayService browser=new SklotopolisHighwayService(Logger.getAnonymousLogger());
        try {
            Properties properties=new Properties();
            Path cache=temporary.getRoot().toPath();
            properties.setProperty("navigationHighwaysCacheDirectory",cache.toString());
            properties.setProperty("navigationHighwaysEnabled","false");
            connected.configure(WaypointClientConfiguration.from(properties));
            connected.activate(ServerIdentity.of(ServerEndpoint.direct("sklotopolis.com",3726),"Novus","Novus",ServerIdentity.Resolution.RESOLVED));
            HighwayTileIndex home=connected.current();long revision=connected.revision();
            properties.setProperty("navigationHighwaysEnabled","true");
            browser.configure(WaypointClientConfiguration.from(properties));
            Constructor<ServerMapProfile> constructor=ServerMapProfile.class.getDeclaredConstructor(String.class,String.class,int.class,int.class,int.class,String.class);
            constructor.setAccessible(true);
            for(int world:new int[]{1,2}){
                ServerMapProfile profile=constructor.newInstance("fixture-"+world,"Fixture "+world,world,16,16,"http://127.0.0.1:"+http.getAddress().getPort()+"/"+world);
                browser.browse(profile);
                long deadline=System.nanoTime()+5_000_000_000L;
                while(browser.current().isEmpty()&&System.nanoTime()<deadline)Thread.sleep(10);
                HighwayTileIndex roads=browser.current();assertFalse(roads.isEmpty());
                assertEquals(world,roads.getSegments().get(0).getStartX());
                assertEquals("Previous world's segments never leak",1,roads.getSegments().size());
                browser.browse(profile);assertSame("Repeated drawing reuses data",roads,browser.current());
                assertSame(home,connected.current());assertEquals(revision,connected.revision());
            }
            assertEquals(2,requests.get());
            try(java.util.stream.Stream<Path> files=Files.walk(cache)) {
                assertEquals("One validated cache per continent",2,files.filter(path->path.getFileName().toString().equals("highways.snapshot")).count());
            }
            browser.deactivate();assertTrue(browser.current().isEmpty());assertSame(home,connected.current());
        } finally {browser.deactivate();connected.deactivate();http.stop(0);}
    }
}
