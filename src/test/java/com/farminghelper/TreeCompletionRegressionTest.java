package com.farminghelper;
import java.util.*;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;
import org.junit.Test;
import static org.junit.Assert.*;
public class TreeCompletionRegressionTest
{
    private static Observation live(CropState s,int raw){return new Observation(s,Source.LIVE,1,null,"",raw);}
    @Test public void fruitTreeRequiresActualReplacement()
    {
        GuideProgress p=new GuideProgress();
        p.observe(live(CropState.UNCHECKED,226));
        assertEquals(StepEngine.Action.CHECK_HEALTH,StepEngine.action(PatchType.FRUIT_TREE,live(CropState.UNCHECKED,0),false,true,false,false));
        p.observe(live(CropState.HARVESTABLE,212));
        assertEquals(StepEngine.Action.HARVEST,StepEngine.action(PatchType.FRUIT_TREE,live(CropState.HARVESTABLE,212),false,true,false,false));
        p.observe(live(CropState.GROWING,205));
        assertFalse(p.finished(PatchType.FRUIT_TREE,live(CropState.GROWING,205),false,false));
        p.observe(live(CropState.STUMP,225));p.observe(live(CropState.EMPTY,3));
        Observation planted=live(CropState.GROWING,200);p.observe(planted);
        assertTrue(p.finished(PatchType.FRUIT_TREE,planted,false,false));
        assertFalse(p.finished(PatchType.FRUIT_TREE,new Observation(CropState.GROWING,Source.TIME_TRACKING,1,null,"",200),false,false));
    }
    @Test public void permanentBushHealthCheckDoesNotRequireReplacement()
    {
        GuideProgress p=new GuideProgress();p.observe(live(CropState.UNCHECKED,250));
        Observation growing=live(CropState.GROWING,13);p.observe(growing);
        assertTrue(p.finished(PatchType.BUSH,growing,false,false));
        assertEquals(StepEngine.Action.CHECK_HEALTH,StepEngine.action(PatchType.BUSH,live(CropState.UNCHECKED,0),false,true,false,false));
    }
    @Test public void strongholdFruitTreeFirst()
    {
        FarmRunPatch tree=new FarmRunPatch("Gnome Stronghold","",PatchType.TREE),fruit=new FarmRunPatch("Gnome Stronghold","",PatchType.FRUIT_TREE);
        assertEquals(List.of(fruit,tree),RunOrder.shared(List.of(tree,fruit),null,true,false).get(0).patches);
    }
}
