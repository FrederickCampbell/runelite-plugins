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
        assertTrue(CropCare.waterNeeded(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,37),true));
        assertFalse(CropCare.waterNeeded(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,101),true));
        assertTrue(CropCare.watered(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,101)));
        assertFalse(CropCare.watered(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,37)));
        assertTrue(CropCare.waterNeeded(PatchType.HOPS,observation(CropState.GROWING,Source.LIVE,104),true));
        assertFalse(CropCare.waterNeeded(PatchType.HOPS,observation(CropState.GROWING,Source.LIVE,232),true));
        assertTrue(CropCare.waterNeeded(PatchType.ALLOTMENT,observation(CropState.GROWING,Source.LIVE,63),true));
        assertFalse(CropCare.waterNeeded(PatchType.ALLOTMENT,observation(CropState.GROWING,Source.LIVE,128),true));
        assertFalse(CropCare.waterNeeded(PatchType.HERB,observation(CropState.GROWING,Source.LIVE,6),true));
        assertFalse(CropCare.waterNeeded(PatchType.FLOWER,observation(CropState.GROWING,Source.TIME_TRACKING,37),true));
        assertFalse(CropCare.waterNeeded(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,37),false));
    }
    @Test public void storedCanBeforeBankAndWateringIndependentOfTool()
    {
        assertEquals(5333,CropCare.preferredCan(5333,13353,5340));
        assertEquals(13353,CropCare.preferredCan(-1,13353,5340));
        assertEquals(5340,CropCare.preferredCan(-1,-1,5340));
        assertTrue(CropCare.waterNeeded(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,37),true));
        assertFalse(CropCare.waterNeeded(PatchType.FLOWER,observation(CropState.GROWING,Source.LIVE,37),false));
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
        assertEquals(lily,SeedChoices.allocate(List.of(p),99,Map.of(lily.getItemId(),1,limp.getItemId(),10),t->"Limpwurt",q->"",Map.of(),true,patchChoice->true).get(p));
        assertEquals(limp,SeedChoices.allocate(List.of(p),99,Map.of(limp.getItemId(),10),t->"",q->"",Map.of(),true,patchChoice->true).get(p));
        assertTrue(RunPreset.CROP.defaultEnabled(PatchType.FLOWER));
        assertTrue(KeepCrop.WHITE_LILY==KeepCrop.observed(PatchType.FLOWER,observation(CropState.HARVESTABLE,Source.LIVE,41)));
        assertFalse(KeepCrop.WHITE_LILY==KeepCrop.observed(PatchType.FLOWER,observation(CropState.HARVESTABLE,Source.LIVE,32)));
        assertFalse(KeepCrop.WHITE_LILY==KeepCrop.observed(PatchType.FLOWER,observation(CropState.DISEASED,Source.LIVE,166)));
    }
}
