package com.farminghelper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;
import net.runelite.api.gameval.ItemID;
public class SpecialtyStagesTest
{
    @Test public void farmThenBirdsAndBankStockOnly()
    {
        assertTrue(SpecialtyStages.includes(true,false,PatchType.SEAWEED));
        assertFalse(SpecialtyStages.includes(true,false,PatchType.BIRDHOUSE));
        assertFalse(SpecialtyStages.includes(true,true,PatchType.SEAWEED));
        assertTrue(SpecialtyStages.includes(true,true,PatchType.BIRDHOUSE));
        assertTrue(SpecialtyStages.includes(false,false,PatchType.BIRDHOUSE));
        RunOrder.Group fossil=new RunOrder.Group("Fossil Island",List.of()),other=new RunOrder.Group("Great Conch",List.of());
        assertEquals(List.of(other,fossil),SpecialtyStages.fossilLast(List.of(fossil,other)));
        assertTrue(RunPreset.SPECIALTY.defaultEnabled(PatchType.BIRDHOUSE));
        assertEquals(Map.of(1,5,2,1),SpecialtyStages.birdStock(Map.of(1,2),Map.of(1,3,2,1)));
        assertEquals(SpecialRoutes.SEAWEED_ISLAND,Preparation.FOSSIL_BANK.point);
    }
    @Test public void readyFourBirdsUseBestCraftableLogsAndPotatoSeeds()
    {
        List<FarmRunPatch> birds=BirdhouseRun.patches();
        Map<FarmRunPatch,Observation> state=new HashMap<>();
        for(FarmRunPatch p:birds)state.put(p,new Observation(CropState.HARVESTABLE,Source.LIVE,0,null,p.getName(),3));
        Map<Integer,Integer> required=new HashMap<>();
        BirdhouseRun.supplies(birds,state,Map.of(1513,4,1515,4,ItemID.POTATO_SEED,40,ItemID.BARLEY_SEED,100),required,78,75);
        assertEquals(Integer.valueOf(4),required.get(1513));
        assertEquals(Integer.valueOf(40),required.get(ItemID.POTATO_SEED));
        assertEquals(Integer.valueOf(1),required.get(2347));
        assertEquals(Integer.valueOf(1),required.get(1755));
        assertFalse(required.containsKey(8792));
    }
}
