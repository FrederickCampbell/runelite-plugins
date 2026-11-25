package com.farminghelper;
import java.util.*;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
public class RouteAndReplacement41Test
{
    private static FarmRunPatch p(String s,PatchType t){return new FarmRunPatch(s,"",t);}
    private static Observation live(CropState s,int raw){return new Observation(s,Source.LIVE,1,null,"",raw);}
    @Test public void sharedRouteKeepsChampionsAndHopsTogether()
    {
        FarmRunPatch guild=p("Farming Guild",PatchType.BUSH),champ=p("Champions' Guild",PatchType.BUSH),
            hops=p("Lumbridge",PatchType.HOPS),entrana=p("Entrana",PatchType.HOPS),tree=p("Lumbridge",PatchType.TREE),
            fossil=p("Fossil Island",PatchType.HARDWOOD_TREE);
        List<FarmRunPatch> result=RunOrder.flatten(RunOrder.shared(List.of(hops,fossil,entrana,champ,tree,guild),null,true,false));
        assertEquals(guild,result.get(0));assertEquals(result.indexOf(champ)+1,result.indexOf(hops));
        assertNotEquals(RunOrder.site(tree),RunOrder.site(hops));
        assertEquals(entrana,result.get(result.size()-2));assertEquals(fossil,result.get(result.size()-1));
        assertEquals(result,RunOrder.flatten(RunOrder.shared(List.of(guild,tree,champ,entrana,fossil,hops),null,true,false)));
    }
    @Test public void bankPreferenceAndVaultFallback()
    {
        WorldPoint from=new WorldPoint(3092,3243,0);
        assertEquals(Preparation.DRAYNOR,Preparation.choose(80,from,true,false,Preparation.StartAt.BANK,false).point);
        assertEquals(Preparation.GUILD,Preparation.choose(80,from,true,false,Preparation.StartAt.BANK,true).point);
        assertTrue(Preparation.vaultNeeded(Map.of(1,4),Map.of(1,1),Map.of(1,2),Map.of(1,2)));
        assertFalse(Preparation.vaultNeeded(Map.of(1,4),Map.of(1,1),Map.of(1,3),Map.of(1,2)));
    }
    @Test public void poisonIvyFullCycleAndPreservation()
    {
        PatchType t=PatchType.BUSH;
        Observation check=live(CropState.UNCHECKED,255),berries=live(CropState.HARVESTABLE,209),depleted=live(CropState.GROWING,205);
        assertFalse(CropReplacement.preserve(t,"",""));assertTrue(CropReplacement.preserve(t,"","PRESERVE"));
        assertEquals(StepEngine.Action.CHECK_HEALTH,CropReplacement.action(t,check,false,true,false));
        assertEquals(StepEngine.Action.HARVEST,CropReplacement.action(t,berries,false,true,false));
        assertTrue(CropReplacement.actionable(t,depleted,false));
        assertEquals(StepEngine.Action.CLEAR_LIVING,CropReplacement.action(t,depleted,false,true,false));
        assertEquals(StepEngine.Action.COMPLETE,CropReplacement.action(t,depleted,false,false,false));
        assertEquals(StepEngine.Action.COMPLETE,CropReplacement.action(t,depleted,true,true,false));
        GuideProgress progress=new GuideProgress();progress.observe(check);progress.observe(berries);progress.observe(depleted);
        assertFalse(progress.finished(t,depleted,false,false,false));
        progress.observe(live(CropState.EMPTY,3));Observation planted=live(CropState.GROWING,197);progress.observe(planted);
        assertTrue(progress.finished(t,planted,false,false,false));
    }
    @Test public void cactusAndSpecialTreeRemoval()
    {
        assertTrue(CropReplacement.preserve(PatchType.CACTUS,"",""));
        assertEquals(StepEngine.Action.CLEAR_LIVING,CropReplacement.action(PatchType.CACTUS,live(CropState.GROWING,15),false,true,false));
        assertEquals(StepEngine.Action.CLEAR_LIVING,CropReplacement.action(PatchType.CALQUAT,live(CropState.HARVESTABLE,12),false,true,false));
        assertEquals(StepEngine.Action.PAY_REMOVE,CropReplacement.action(PatchType.REDWOOD,live(CropState.GROWING,18),false,true,false));
        assertFalse(CropReplacement.actionable(PatchType.REDWOOD,live(CropState.GROWING,18),true));
        assertTrue(CropReplacement.preserve(PatchType.SPIRIT_TREE,"REPLANT","REPLANT"));
    }
}
