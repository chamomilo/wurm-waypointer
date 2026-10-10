package org.waypoints.next.map;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.model.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import static org.junit.Assert.*;

public class SklotopolisMapBrowsingTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void browsingLoadsSurfaceAndMatchingDeedsIndependentlyOfConnectedWorld() throws Exception {
        HttpServer http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicInteger requests=new AtomicInteger();
        AtomicInteger deedRequests=new AtomicInteger();
        for(int size:new int[]{16,32}){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB),"png",bytes);
            byte[] png=bytes.toByteArray();
            http.createContext("/"+size+"/mapdump-flat.png",exchange->{
                requests.incrementAndGet();exchange.sendResponseHeaders(200,png.length);
                try(java.io.OutputStream out=exchange.getResponseBody()){out.write(png);}
            });
            byte[] deeds=("[{\"name\":\"Deed "+size+"\",\"x\":3,\"y\":4,\"tilesNorth\":1,\"tilesSouth\":1,\"tilesEast\":1,\"tilesWest\":1}]").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            http.createContext("/"+size+"/deeds.json",exchange->{
                deedRequests.incrementAndGet();exchange.sendResponseHeaders(200,deeds.length);
                try(java.io.OutputStream out=exchange.getResponseBody()){out.write(deeds);}
            });
        }
        http.start();SklotopolisMapService connected=new SklotopolisMapService(Logger.getAnonymousLogger());
        SklotopolisMapService browser=new SklotopolisMapService(Logger.getAnonymousLogger());
        try {
            Path cache=temporary.getRoot().toPath();connected.configure(false,cache,30,false);
            connected.activate(ServerIdentity.of(new ServerEndpoint("sklotopolis.com",3726,27016),"Sklotopolis Novus","Novus",ServerIdentity.Resolution.RESOLVED));
            ServerMapSnapshot home=connected.current();assertEquals(SklotopolisMapProfiles.NOVUS,home.getProfile());
            browser.configure(true,cache,30,true);
            for(int size:new int[]{16,32}){
                ServerMapProfile profile=new ServerMapProfile("fixture-"+size,"Fixture "+size,size,size,size,"http://127.0.0.1:"+http.getAddress().getPort()+"/"+size);
                browser.browse(profile);
                long deadline=System.nanoTime()+5_000_000_000L;
                while((!browser.current().hasSurface()||browser.current().getDeeds().isEmpty())&&System.nanoTime()<deadline)Thread.sleep(10);
                ServerMapSnapshot snapshot=browser.current();assertTrue(snapshot.hasSurface());assertSame(profile,snapshot.getProfile());
                assertEquals(size,ImageIO.read(snapshot.getSurfaceImage().toFile()).getWidth());
                assertTrue(snapshot.getSurfaceImage().toAbsolutePath().startsWith(cache.toAbsolutePath()));
                assertEquals("Deed "+size,snapshot.getDeeds().get(0).getName());
                browser.browse(profile);assertSame(snapshot,browser.current());assertSame(home,connected.current());
            }
            assertEquals("Each selected surface loads once",2,requests.get());
            assertEquals("Each selected deed catalogue loads once",2,deedRequests.get());
            browser.deactivate();assertFalse(browser.current().hasSurface());assertSame(home,connected.current());
        } finally { browser.deactivate();connected.deactivate();http.stop(0); }
    }
}
