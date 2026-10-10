package org.waypoints.next.integration;

import org.junit.Test;
import org.waypoints.next.tracking.*;
import com.wurmonline.shared.constants.PlayerAction;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.Assert.*;

public class ManagedCatalogueGatewayTest {
    private final Object owner=new Object();
    private long now=100_000;
    private final List<ManagedKind> requests=new ArrayList<ManagedKind>();
    private final List<Map<String,String>> replies=new ArrayList<Map<String,String>>();
    private int catalogues,directions;
    private boolean failReply;
    private AnimalBearing observed;
    private final ManagedCatalogueGateway gateway=new ManagedCatalogueGateway(Logger.getAnonymousLogger(),new ManagedCatalogueGateway.Receiver(){
        @Override public void catalogue(ManagedKind kind,ManagedBmlParser.Result result){catalogues++;}
        @Override public void direction(String key,String text,AnimalBearing bearing){assertEquals("42",key);directions++;observed=bearing;}
    },new ManagedCatalogueGateway.Transport(){
        @Override public Object owner(){return owner;}
        @Override public void request(ManagedKind kind){requests.add(kind);}
        @Override public void reply(Map<String,String> response,String title)throws Exception{if(failReply)throw new Exception("fixture failure");replies.add(new HashMap<String,String>(response));}
        @Override public double[] pose(){return new double[]{100,200,45};}
    },()->now);
    private static String form(String id){return "border{center{passthrough{id=\"id\";text=\""+id+"\"}table{cols=\"8\";label{text=\"\"}label{text=\"Name\"}label{text=\"Animal Type\"}label{text=\"On Deed?\"}label{text=\"Hitched?\"}label{text=\"Cared For?\"}label{text=\"Branded?\"}label{text=\"Tamed?\"}radio{group=\"sel\";id=\"42\"}label{text=\"Mare\"}label{text=\"horse\"}label{text=\"Yes\"}label{text=\"No\"}label{text=\"Yes\"}label{text=\"Yes\"}label{text=\"No\"}}button{id=\"find\";text=\"Find\"}button{id=\"close\";text=\"Close\"}}}";}
    @Test public void onlyClaimsMatchingOwnedReplyAndSendsNoManagementSideEffects(){
        assertFalse(gateway.intercept(owner,"Manage Animals",form("10")));
        gateway.refresh();gateway.tick(null,now);assertEquals(Collections.singletonList(ManagedKind.ANIMAL),requests);
        assertFalse(gateway.intercept(new Object(),"Manage Animals",form("10")));
        assertFalse(gateway.intercept(owner,"Manage Ships",form("10")));
        assertFalse(gateway.intercept(owner,"Manage Animals","unknown schema"));
        assertTrue(gateway.intercept(owner,"Manage Animals",form("10")));
        assertEquals(new HashSet<String>(Arrays.asList("id","close")),replies.get(0).keySet());assertEquals("10",replies.get(0).get("id"));assertEquals(1,catalogues);
        gateway.tick(null,now+1000);assertEquals(1,requests.size());now+=1500;gateway.tick(null,now);assertEquals(ManagedKind.VEHICLE,requests.get(1));
    }
    @Test public void directionUsesNewQuestionIdPoseAndExactRequestedAnimal(){
        gateway.locate("42","Mare");gateway.tick(null,now);assertTrue(gateway.intercept(owner,"Manage Animals",form("777")));
        assertEquals("777",replies.get(0).get("id"));assertEquals("42",replies.get(0).get("sel"));assertEquals("true",replies.get(0).get("find"));assertFalse(replies.get(0).containsKey("close"));
        gateway.event("Chat","The Mare is in front of you very close.",now);gateway.event("Event","The Other is in front of you very close.",now);assertEquals(0,directions);
        gateway.event(":Event","The Mare is in front of you very close.",now);assertEquals(1,directions);assertEquals(45,observed.bearing,0);assertEquals(100,observed.originX,0);assertEquals(200,observed.originY,0);
        gateway.event("Event","The Mare is behind you very close.",now);assertEquals(1,directions);
    }
    @Test public void timeoutManualManageAndFailedReplyLeaveNativeFormAvailable(){
        gateway.refresh();gateway.tick(null,now);now+=8001;assertFalse(gateway.intercept(owner,"Manage Animals",form("10")));gateway.tick(null,now);assertEquals(1,requests.size());
        gateway.refresh();gateway.tick(null,now);gateway.nativeAction(new PlayerAction("Manage",(short)663,0));assertFalse(gateway.intercept(owner,"Manage Animals",form("11")));
        now+=1500;gateway.refresh();gateway.tick(null,now);failReply=true;assertFalse(gateway.intercept(owner,"Manage Animals",form("12")));assertEquals(0,catalogues);
    }
    @Test public void nativeSortedAnimalCartAndShipListsStayInWaypointer() throws Exception {
        gateway.refresh();gateway.tick(null,now);
        String[] titles={"Alice's List of Animals","Alice's List of Small Carts, Large Carts, Wagons and Carriers","Alice's List of Ships"};
        ManagedKind[] kinds={ManagedKind.ANIMAL,ManagedKind.VEHICLE,ManagedKind.SHIP};
        for(int i=0;i<kinds.length;i++) {
            String bml=nativeForm(kinds[i],Integer.toString(800+i));
            assertNotNull("Pinned Wurm parser accepts the same fixture",com.wurmonline.client.bml.BParser.parse(bml));
            ManagedBmlParser.Result parsed=ManagedBmlParser.parse(titles[i],bml,kinds[i]);
            assertNotNull("Sorted native list "+kinds[i],parsed);
            assertEquals(42,parsed.entries.get(0).id);
            assertTrue("Owned native reply is consumed rather than shown as a BML window",gateway.intercept(owner,titles[i],bml));
            assertEquals(Integer.toString(800+i),replies.get(i).get("id"));
            assertEquals(new HashSet<String>(Arrays.asList("id","close")),replies.get(i).keySet());
            now+=1500;gateway.tick(null,now);
        }
        assertEquals(Arrays.asList(kinds),requests);assertEquals(3,catalogues);
        assertFalse("A later manual Manage reply remains native",gateway.intercept(owner,titles[0],nativeForm(ManagedKind.ANIMAL,"900")));
    }
    static String nativeForm(ManagedKind kind,String questionId) {
        StringBuilder bml=new StringBuilder("border{border{size=\"20,20\";null;null;label{type='bold';text=\"Your list\"};harray{button{text=\"Close\";id=\"close\"}};null;}null;scroll{vertical=\"true\";horizontal=\"false\";varray{rescale=\"true\";passthrough{id=\"id\";text=\"");
        bml.append(questionId).append("\"}table{rows=\"1\";cols=\"").append(kind==ManagedKind.ANIMAL?8:6).append("\";label{text=\"\"};button{text=\"Name /\\\";id=\"sort-1\"};");
        String[] headings=kind==ManagedKind.ANIMAL?new String[]{"Animal Type","On Deed?","Hitched?","Cared For?","Branded?","Tamed?"}:new String[]{"Type","Owner?","Locked?",""};
        for(String heading:headings)bml.append("label{text=\"").append(heading).append("\"};");
        bml.append("radio{group=\"sel\";id=\"42\";text=\"\"}label{text=\"Mare\"};label{text=\"horse\"};");
        for(int i=3;i<(kind==ManagedKind.ANIMAL?8:6);i++)bml.append("label{color=\"127,255,127\"text=\"true\"};");
        return bml.append("};radio{group=\"sel\";id=\"-10\";selected=\"true\";text=\"None\"};harray{button{text=\"Give direction to\";id=\"find\"}}}};null;null;}").toString();
    }
}
