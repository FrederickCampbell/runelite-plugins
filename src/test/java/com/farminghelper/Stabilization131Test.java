package com.farminghelper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;
public class Stabilization131Test
{
    @Test public void priorityTravelGroupsByKindAndCharge()
    {
        Map<Integer,String> names=Map.of(1,"Law rune",2,"Ring of dueling(2)",3,"Tome of fire",4,"Air rune",5,"Amulet of glory(4)",6,"Ring of dueling(8)",7,"Skills necklace(6)");
        assertEquals(List.of(4,1,5,7,6,2,3),BankShelfLayout.priorityOrder(List.of(2,3,1,6,7,4,5),names::get,id->-1));
    }
    @Test public void compostResupplyReturnsToProductionNotOrdinaryFarming()
    {
        assertEquals(Preparation.Workflow.GUILD_COMPOST,Preparation.Workflow.GUILD_COMPOST_PREP.resume());
        assertEquals(Preparation.Workflow.GUILD_COMPOST,Preparation.Workflow.GUILD_COMPOST.resupply().resume());
        assertTrue(Preparation.Workflow.GUILD_COMPOST.splitGuild());
    }
    @Test public void futureBirdRowsDoNotGetAssignedToGuild()
    {
        GuideView.ItemRow row=new GuideView.ItemRow(42,"Yew logs",0,4,20,0,false,3);
        assertEquals(List.of(row),GuideView.guildSections(List.of(row),Map.of(42,1)));
        assertEquals(3,row.section);
    }
    @Test public void bucketsRespectDemandStockAndSpace()
    {
        assertEquals(7,LeprechaunWithdrawal.buckets(30,15,7).quantity);
        assertEquals(0,LeprechaunWithdrawal.buckets(30,15,0).quantity);
        assertNull(LeprechaunWithdrawal.buckets(30,0,28));
    }
    @Test public void unknownBinRequiresInspectionRatherThanPlanting()
    {
        FarmRunPatch bin=new FarmRunPatch("Farming Guild","",PatchType.COMPOST_BIN);
        Observation o=new Observation(null,Source.NONE,0,null,"");
        CompostRun.Plan plan=CompostRun.plan(bin,o,Map.of());
        assertEquals(CompostRun.Action.INSPECT,CompostRun.step(bin,o,plan,Map.of(),28).action);
    }
}