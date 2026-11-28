package com.farminghelper;

import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompostRunTest
{
    private FarmRunPatch bin(String loc){return new FarmRunPatch(loc,"",PatchType.COMPOST_BIN);}
    private QuestFarmingStateReader.Observation obs(CropState state,int value)
    {return new QuestFarmingStateReader.Observation(state,QuestFarmingStateReader.Source.LIVE,0,null,"",value);}
    @Test public void emptyAndPartialBinsUseCompatibleProduce()
    {
        var p=CompostRun.plan(bin("Falador"),obs(CropState.EMPTY,0),Map.of(CompostRun.WOAD,15));
        assertEquals(Map.of(CompostRun.WOAD,15),p.supplies);
        p=CompostRun.plan(bin("Falador"),obs(CropState.FILLING,39),Map.of(CompostRun.WOAD,100,CompostRun.PINEAPPLE,8));
        assertEquals(Map.of(CompostRun.PINEAPPLE,8),p.supplies);
        p=CompostRun.plan(bin("Farming Guild"),obs(CropState.FILLING,77),Map.of());
        assertTrue(p.instruction.startsWith("Close"));assertTrue(p.supplies.isEmpty());
    }
    @Test public void stagesUpgradeWithoutWastingAdditionalPotion()
    {
        var p=CompostRun.plan(bin("Farming Guild"),obs(CropState.HARVESTABLE,93),Map.of(6470,1,CompostRun.BOTTOMLESS,1));
        assertEquals(Map.of(6470,1,CompostRun.ASH,50,CompostRun.BOTTOMLESS,1),p.supplies);
        assertTrue(p.instruction.startsWith("Open"));
        p=CompostRun.plan(bin("Falador"),obs(CropState.HARVESTABLE,48),Map.of());
        assertEquals(Map.of(CompostRun.ASH,25,CompostRun.BUCKET,1),p.supplies);
        assertTrue(p.instruction.contains("2 ash per"));
        p=CompostRun.plan(bin("Farming Guild"),obs(CropState.HARVESTABLE,205),Map.of(CompostRun.BOTTOMLESS,1));
        assertEquals(Map.of(CompostRun.BOTTOMLESS,1),p.supplies);
        assertTrue(p.instruction.contains("30 ultracompost"));
    }
    @Test public void productionTierDoesNotWasteUpgrades()
    {
        var b=bin("Farming Guild");var o=obs(CropState.HARVESTABLE,93);
        var stock=Map.of(6470,1,CompostRun.BOTTOMLESS,1);
        var regular=CompostRun.plan(b,o,stock,Map.of(),CompostRun.Production.REGULAR,CompostRun.Material.AUTO);
        assertEquals(Map.of(CompostRun.BOTTOMLESS,1),regular.supplies);
        var superior=CompostRun.plan(b,o,stock,Map.of(),CompostRun.Production.SUPER,CompostRun.Material.AUTO);
        assertEquals(Map.of(6470,1,CompostRun.BOTTOMLESS,1),superior.supplies);
        assertTrue(CompostRun.material(net.runelite.api.gameval.ItemID.SNAPE_GRASS));
    }
    @Test public void noWorkOnDecomposingAndUnknownIsNotEmpty()
    {
        assertFalse(CompostRun.plan(bin("Falador"),obs(CropState.GROWING,31),Map.of()).ready);
        assertTrue(CompostRun.plan(bin("Falador"),obs(CropState.GROWING,31),Map.of()).supplies.isEmpty());
        assertTrue(CompostRun.plan(bin("Falador"),obs(null,-1),Map.of()).instruction.startsWith("Inspect"));
        assertTrue(FarmingHelperPlugin.actionable(PatchType.COMPOST_BIN,CropState.FILLING));
        assertFalse(FarmingHelperPlugin.actionable(PatchType.COMPOST_BIN,CropState.GROWING));
    }
    @Test public void potionDosesAndAshAreAggregatedAcrossBins()
    {
        var a=bin("Falador");var b=bin("Farming Guild");Map<Integer,Integer> supplies=new HashMap<>();
        CompostRun.addSupplies(supplies,List.of(a,b),Map.of(a,obs(CropState.HARVESTABLE,30),b,obs(CropState.HARVESTABLE,92)),Map.of(6470,1,CompostRun.BOTTOMLESS,1),new HashMap<>());
        assertEquals(Map.of(6470,1,CompostRun.ASH,75,CompostRun.BOTTOMLESS,1),supplies);
    }
}
