package com.farminghelper.queststate;

import java.util.*;
import net.runelite.client.plugins.timetracking.Tab;
import org.junit.Test;
import static org.junit.Assert.*;

public class HealthCheckStateTest
{
    @Test public void allDocumentedHealthChecksAreActionable()
    {
        Map<PatchImplementation,int[]> cases=Map.of(
            PatchImplementation.FRUIT_TREE,new int[]{34,61,98,125,162,189,226,253},
            PatchImplementation.TREE,new int[]{12,21,32,45,60},
            PatchImplementation.HARDWOOD_TREE,new int[]{15,38,63,88,114},
            PatchImplementation.BUSH,new int[]{250,251,252,253,254,255},
            PatchImplementation.CALQUAT,new int[]{34},
            PatchImplementation.SPIRIT_TREE,new int[]{44},
            PatchImplementation.REDWOOD,new int[]{37},
            PatchImplementation.CELASTRUS,new int[]{13},
            PatchImplementation.CRYSTAL_TREE,new int[]{14},
            PatchImplementation.CACTUS,new int[]{31,58});
        for(var e:cases.entrySet())for(int raw:e.getValue())
            assertEquals(e.getKey()+" raw "+raw,CropState.UNCHECKED,QuestFarmingStateReader.normalizedState(e.getKey(),raw,e.getKey().forVarbitValue(raw)));
    }
    @Test public void palmGrowingCheckedStumpAndWeedsRemainDistinct()
    {
        for(int raw=200;raw<=205;raw++)assertEquals(CropState.GROWING,state(PatchImplementation.FRUIT_TREE,raw));
        for(int raw=206;raw<=212;raw++)assertEquals(CropState.HARVESTABLE,state(PatchImplementation.FRUIT_TREE,raw));
        assertEquals(CropState.UNCHECKED,state(PatchImplementation.FRUIT_TREE,226));
        assertEquals(CropState.STUMP,state(PatchImplementation.FRUIT_TREE,225));
        assertEquals(CropState.EMPTY,state(PatchImplementation.FRUIT_TREE,0));
        assertEquals(CropState.EMPTY,state(PatchImplementation.FLOWER,0));
    }
    private static CropState state(PatchImplementation t,int raw)
    {return QuestFarmingStateReader.normalizedState(t,raw,t.forVarbitValue(raw));}
    @Test public void neighbouringPatchIdentitiesCannotAlias()
    {
        FarmingWorld w=new FarmingWorld();
        FarmingPatch strongholdFruit=QuestFarmingStateReader.find(w,Tab.FRUIT_TREE,"Gnome Stronghold","",PatchImplementation.FRUIT_TREE);
        FarmingPatch strongholdTree=QuestFarmingStateReader.find(w,Tab.TREE,"Gnome Stronghold","",PatchImplementation.TREE);
        FarmingPatch villageFruit=QuestFarmingStateReader.find(w,Tab.FRUIT_TREE,"Tree Gnome Village","",PatchImplementation.FRUIT_TREE);
        FarmingPatch flower=QuestFarmingStateReader.find(w,PatchImplementation.FLOWER.getTab(),"Kastori","",PatchImplementation.FLOWER);
        FarmingPatch fruit=QuestFarmingStateReader.find(w,Tab.FRUIT_TREE,"Kastori","",PatchImplementation.FRUIT_TREE);
        FarmingPatch calquat=QuestFarmingStateReader.find(w,PatchImplementation.CALQUAT.getTab(),"Kastori","",PatchImplementation.CALQUAT);
        for(FarmingPatch p:List.of(strongholdFruit,strongholdTree,villageFruit,flower,fruit,calquat))assertNotNull(p);
        assertEquals(6,Set.of(strongholdFruit.configKey(),strongholdTree.configKey(),villageFruit.configKey(),flower.configKey(),fruit.configKey(),calquat.configKey()).size());
    }
}
