package com.farminghelper;

import java.util.*;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeepCrop42Test
{
    private static Observation live(CropState state,int raw){return new Observation(state,Source.LIVE,1,null,"",raw);}
    @Test public void actualSpeciesNotRecommendedSeed()
    {
        assertEquals(KeepCrop.WHITEBERRIES,KeepCrop.observed(PatchType.BUSH,live(CropState.HARVESTABLE,63)));
        assertEquals(KeepCrop.POISON_IVY,KeepCrop.observed(PatchType.BUSH,live(CropState.UNCHECKED,255)));
        assertEquals(KeepCrop.POISON_IVY,KeepCrop.observed(PatchType.BUSH,live(CropState.GROWING,205)));
        assertEquals(KeepCrop.POTATO_CACTUS,KeepCrop.observed(PatchType.CACTUS,live(CropState.HARVESTABLE,45)));
        assertNull(KeepCrop.observed(PatchType.BUSH,live(CropState.EMPTY,3)));
        assertNull(KeepCrop.observed(PatchType.BUSH,live(CropState.DEAD,217)));
    }
    @Test public void precedenceAndDefaults()
    {
        assertTrue(KeepCrop.resolve(PatchType.FLOWER,KeepCrop.WHITE_LILY,"","","",""));
        assertFalse(KeepCrop.resolve(PatchType.FLOWER,KeepCrop.WHITE_LILY,"","REPLANT","",""));
        assertTrue(KeepCrop.resolve(PatchType.BUSH,KeepCrop.WHITEBERRIES,"REPLANT","PRESERVE","",""));
        assertFalse(KeepCrop.resolve(PatchType.BUSH,KeepCrop.POISON_IVY,"REPLANT","","",""));
        assertFalse(KeepCrop.resolve(PatchType.BUSH,KeepCrop.WHITEBERRIES,"REPLANT","PRESERVE","REPLANT",""));
        assertTrue(KeepCrop.resolve(PatchType.BUSH,KeepCrop.WHITEBERRIES,"REPLANT","PRESERVE","REPLANT","PRESERVE"));
        assertTrue(KeepCrop.resolve(PatchType.CACTUS,KeepCrop.CACTUS,"","","",""));
        assertFalse(KeepCrop.resolve(PatchType.CACTUS,KeepCrop.POTATO_CACTUS,"","REPLANT","",""));
        assertTrue(KeepCrop.resolve(PatchType.SPIRIT_TREE,null,"REPLANT","REPLANT","REPLANT","REPLANT"));
    }
    @Test public void preservedBushHarvestsThenStops()
    {
        assertEquals(StepEngine.Action.HARVEST,CropReplacement.action(PatchType.BUSH,live(CropState.HARVESTABLE,63),true,false,false));
        assertEquals(StepEngine.Action.COMPLETE,CropReplacement.action(PatchType.BUSH,live(CropState.GROWING,59),true,true,false));
    }
    @Test public void configurableAutoLilyAndExplicitSeedPreference()
    {
        FarmRunPatch p=new FarmRunPatch("Falador","",PatchType.FLOWER);
        Crop lily=CropCatalog.findByName(PatchType.FLOWER,"White lily"),limp=CropCatalog.findByName(PatchType.FLOWER,"Limpwurt");
        Map<Integer,Integer> stock=Map.of(lily.getItemId(),3,limp.getItemId(),3);
        assertEquals(lily,SeedChoices.allocate(List.of(p),70,stock,t->"Limpwurt",x->"",Map.of(),true,x->true).get(p));
        assertEquals(limp,SeedChoices.allocate(List.of(p),70,stock,t->"Limpwurt",x->"",Map.of(),true,x->false).get(p));
        assertEquals(limp,SeedChoices.allocate(List.of(p),70,stock,t->"",x->"Limpwurt",Map.of(),true,x->true).get(p));
    }
}
