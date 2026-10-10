package org.waypoints.next.tracking;
import org.junit.Test;
import static org.junit.Assert.*;

public class ManagedBmlParserTest {
    private String actionRow(String selector,String care,String brand,String tame){return selector+"label{text=\"Mare\"}label{text=\"horse\"}label{text=\"Yes\"}label{text=\"No\"}"+care+brand+tame;}
    private String action(String prefix,String id){return "harray{label{text=\"Yes\"}button{id=\""+prefix+id+"\";text=\"X\"}}";}
    @Test public void recoversAnimalIdFromNativeCareBrandAndTameButtonsWithoutSelector(){
        String id="9223372036854775806";
        for(int column=0;column<3;column++){
            String[] cells={"label{text=\"No\"}","label{text=\"No\"}","label{text=\"No\"}"};
            cells[column]=action(new String[]{"uncarefor","unbrand","untame"}[column],id);
            ManagedBmlParser.Result result=ManagedBmlParser.parse("Manage Animals",form(actionRow("label{text=\"\"}",cells[0],cells[1],cells[2])),ManagedKind.ANIMAL);
            assertNotNull(result);assertEquals(Long.parseLong(id),result.entries.get(0).id);assertTrue(result.canLocate);
        }
    }
    @Test public void rejectsConflictingDuplicatedAndMalformedActionIds(){
        String valid=actionRow("label{text=\"\"}",action("uncarefor","42"),action("unbrand","42"),action("untame","42"));
        assertEquals(42,ManagedBmlParser.parse("Manage Animals",form(valid),ManagedKind.ANIMAL).entries.get(0).id);
        assertNull(ManagedBmlParser.parse("Manage Animals",form(valid+valid),ManagedKind.ANIMAL));
        assertNull(ManagedBmlParser.parse("Manage Animals",form(valid.replace("unbrand42","unbrand43")),ManagedKind.ANIMAL));
        assertNull(ManagedBmlParser.parse("Manage Animals",form(valid.replace("label{text=\"\"}","radio{group=\"sel\";id=\"43\"}")),ManagedKind.ANIMAL));
        for(String bad:new String[]{"0","-42","42x","9223372036854775808"})
            assertNull(ManagedBmlParser.parse("Manage Animals",form(valid.replace("uncarefor42","uncarefor"+bad)),ManagedKind.ANIMAL));
    }
    private String form(String rows){return "border{center{passthrough{id=\"id\";text=\"173\"}table{rows=\"1\";cols=\"8\";label{text=\"\"}button{text=\"Name\";id=\"sort1\"}label{text=\"Animal Type\"}label{text=\"On Deed?\"}label{text=\"Hitched?\"}label{text=\"Cared For?\"}label{text=\"Branded?\"}label{text=\"Tamed?\"}"+rows+"}button{id=\"find\";text=\"Find\"}button{id=\"close\";text=\"Close\"}}}";}
    private String row(String id){return "radio{group=\"sel\";id=\""+id+"\"}label{text='Mare \"A\"'}label{text=\"horse\"}label{text=\"Yes\"}label{text=\"No\"}button{id=\"care\";text=\"Stop caring\"}label{text=\"Yes\"}label{text=\"No\"}";}
    @Test public void nativeSortArrowsAndBackslashesAreLiteralBmlText(){
        for(String heading:new String[]{"Name /\\","Name \\/","Name"}) {
            String bml=form(row("42")).replace("text=\"Name\"","text=\""+heading+"\"");
            ManagedBmlParser.Result parsed=ManagedBmlParser.parse("Alice's List of Animals",bml,ManagedKind.ANIMAL);
            assertNotNull("Native sorting header "+heading,parsed);assertEquals(42,parsed.entries.get(0).id);
        }
        ManagedBmlParser.Result parsed=ManagedBmlParser.parse("Alice's List of Animals",form(row("42")).replace("Mare \"A\"","Mare \\ Alpha"),ManagedKind.ANIMAL);
        assertEquals("Mare \\ Alpha",parsed.entries.get(0).name);
    }
    @Test public void readsNativeHeadersRowsAndQuestionIdWithoutInvokingSideEffectButtons(){ManagedBmlParser.Result parsed=ManagedBmlParser.parse("Manage Animals",form(row("42")),ManagedKind.ANIMAL);assertNotNull(parsed);assertEquals("173",parsed.questionId);assertEquals(42,parsed.entries.get(0).id);assertEquals("Mare \"A\"",parsed.entries.get(0).name);assertTrue(parsed.canLocate);assertNotNull(ManagedBmlParser.parse("Manage Animals",form(""),ManagedKind.ANIMAL));}
    @Test public void rejectsUnknownTruncatedDuplicateAndOversizedForms(){assertNull(ManagedBmlParser.parse("Manage Ships",form(row("42")),ManagedKind.ANIMAL));assertNull(ManagedBmlParser.parse("Manage Animals",form(row("42")+row("42")),ManagedKind.ANIMAL));assertNull(ManagedBmlParser.parse("Manage Animals",form(row("42")).replace("cols=\"8\"","cols=\"7\""),ManagedKind.ANIMAL));assertNull(ManagedBmlParser.parse("Manage Animals",form(row("42")).substring(0,80),ManagedKind.ANIMAL));assertNull(ManagedBmlParser.parse("Manage Animals",new String(new char[ManagedBmlParser.MAX_BYTES+1]),ManagedKind.ANIMAL));}
}
