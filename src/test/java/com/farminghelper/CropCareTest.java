package com.farminghelper;
import java.util.*;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;
import org.junit.Test;
import static org.junit.Assert.*;
public class CropCareTest
{
    private static Observation observation(CropState state,Source source,int raw){return new Observation(state,source,1,null,"",raw);}
    @Test public void waterOnlyDryLiveSupportedCrops()
    {
        assertTrue(CropCare.waterNeeded(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,37),5333));
        assertFalse(CropCare.waterNeeded(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,101),5333));
        assertTrue(CropCare.watered(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,101)));
        assertFalse(CropCare.watered(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,37)));
        assertTrue(CropCare.waterNeeded(PatchType.HOPS,observation(CropState.GROWING,Source.LIVE,104),5333));
        assertFalse(CropCare.waterNeeded(PatchType.HOPS,observation(CropState.GROWING,Source.LIVE,232),5333));
        assertTrue(CropCare.waterNeeded(PatchType.ALLOTMENT,observation(CropState.GROWING,Source.LIVE,63),5333));
        assertFalse(CropCare.waterNeeded(PatchType.ALLOTMENT,observation(CropState.GROWING,Source.LIVE,128),5333));
        assertFalse(CropCare.waterNeeded(PatchType.HERB,observation(CropState.GROWING,Source.LIVE,6),5333));
        assertFalse(CropCare.waterNeeded(PatchType.FLOWER,observation(CropState.GROWING,Source.TIME_TRACKING,37),5333));
        assertFalse(CropCare.waterNeeded(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,37),-1));
    }
    @Test public void diseaseRequiresLiveVerifiedObject()
    {
        assertTrue(CropCare.needsCure(observation(CropState.DISEASED,Source.LIVE,166),true));
        assertFalse(CropCare.needsCure(observation(CropState.DISEASED,Source.LIVE,166),false));
        assertFalse(CropCare.needsCure(observation(CropState.DISEASED,Source.TIME_TRACKING,166),true));
        assertTrue(CropCare.prunes(PatchType.BUSH));
    }
    @Test public void flowersUseWhiteLilyFirstThenStockedXpFallback()
    {
        FarmRunPatch p=new FarmRunPatch("Falador","",PatchType.FLOWER);
        Crop lily=CropCatalog.findByName(PatchType.FLOWER,"White lily"),limp=CropCatalog.findByName(PatchType.FLOWER,"Limpwurt");
        assertEquals(lily,SeedChoices.allocate(List.of(p),99,Map.of(lily.getItemId(),1,limp.getItemId(),10),t->"Limpwurt",q->"",Map.of(),true).get(p));
        assertEquals(limp,SeedChoices.allocate(List.of(p),99,Map.of(limp.getItemId(),10),t->"",q->"",Map.of(),true).get(p));
        assertTrue(RunPreset.CROP.defaultEnabled(PatchType.FLOWER));
        assertTrue(com.farminghelper.queststate.QuestFarmingStateReader.isLivingWhiteLily(observation(CropState.HARVESTABLE,Source.LIVE,41)));
        assertFalse(com.farminghelper.queststate.QuestFarmingStateReader.isLivingWhiteLily(observation(CropState.HARVESTABLE,Source.LIVE,32)));
        assertFalse(com.farminghelper.queststate.QuestFarmingStateReader.isLivingWhiteLily(observation(CropState.DISEASED,Source.LIVE,166)));
    }
}
