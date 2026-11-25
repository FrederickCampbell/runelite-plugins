package com.farminghelper;

import java.util.*;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class CropXpTest
{
    private static Crop crop(PatchType t,String n){return CropCatalog.findByName(t,n);}
    @Test public void everyExistingCropHasMetadata()
    {
        for(PatchType t:PatchType.values())for(Crop c:CropCatalog.crops(t))
            assertNotNull(c.getName(),c.getScore());
    }
    @Test public void exactCycleXpAndEstimatedYieldAreSeparate()
    {
        assertEquals(17895,CropXp.perSeed(crop(PatchType.FRUIT_TREE,"Dragonfruit sapling")),1e-9);
        assertEquals(141.5,CropXp.perSeed(crop(PatchType.FLOWER,"Limpwurt")),1e-9);
        assertEquals(292,CropXp.perSeed(crop(PatchType.FLOWER,"White lily")),1e-9);
        assertEquals(23352,CropXp.perSeed(crop(PatchType.HARDWOOD_TREE,"Rosewood sapling")),1e-9);
        assertTrue(crop(PatchType.HERB,"Torstol").getScore().estimated);
        assertTrue(crop(PatchType.CORAL,"Umbral fragment").getScore().unknownYield());
        assertEquals((82+82*37.5)/3,CropXp.perSeed(crop(PatchType.ALLOTMENT,"Snape grass")),1e-9);
        assertEquals(3,crop(PatchType.HOPS,"Jute").getQuantity());
        // Flax has more XP per seed than higher-level Krandorian.
        assertEquals("Flax",CropCatalog.recommend(PatchType.HOPS,21).getName());
    }
    @Test public void plannerUsesAllRanksAndReservesPreferences()
    {
        List<FarmRunPatch> patches=new ArrayList<>();
        for(int i=0;i<5;i++)patches.add(new FarmRunPatch("Falador","Test "+i,PatchType.HERB));
        Map<Integer,Integer> stock=Map.of(ItemID.TORSTOL_SEED,1,ItemID.DWARF_WEED_SEED,1,ItemID.LANTADYME_SEED,1,ItemID.CADANTINE_SEED,1,ItemID.RANARR_SEED,1);
        Map<FarmRunPatch,Crop> plan=SeedChoices.allocate(patches,99,stock,t->"Ranarr",p->"",java.util.Map.of(),false,p->true);
        assertEquals(List.of("Ranarr","Torstol","Dwarf weed","Lantadyme","Cadantine"),
            patches.stream().map(p->plan.get(p).getName()).collect(java.util.stream.Collectors.toList()));
    }
    @Test public void vaultTopNCountsCanonicalCropsAndDoesNotChangeStock()
    {
        Crop magic=crop(PatchType.TREE,"Magic sapling"),yew=crop(PatchType.TREE,"Yew sapling"),maple=crop(PatchType.TREE,"Maple sapling"),willow=crop(PatchType.TREE,"Willow sapling");
        int seedId=987654; // synthetic ID, resolver is tested against native names
        assertEquals(magic,VaultRanking.resolve(seedId,"Magic seed"));
        assertEquals(crop(PatchType.CRYSTAL_TREE,"Crystal sapling"),VaultRanking.resolve(seedId,"Crystal acorn"));
        List<VaultRanking.Entry> entries=List.of(new VaultRanking.Entry(seedId,2,magic),new VaultRanking.Entry(magic.getItemId(),1,magic),
            new VaultRanking.Entry(yew.getItemId(),1,yew),new VaultRanking.Entry(maple.getItemId(),1,maple),new VaultRanking.Entry(willow.getItemId(),1,willow));
        Set<PatchType> allowed=Set.of(PatchType.TREE);
        assertEquals(Set.of(seedId,magic.getItemId()),VaultRanking.select(VaultRanking.View.HIGHEST,entries,99,allowed,t->"",Map.of()));
        assertEquals(Set.of(seedId,magic.getItemId(),yew.getItemId(),maple.getItemId()),VaultRanking.select(VaultRanking.View.TOP_THREE,entries,99,allowed,t->"",Map.of()));
        assertEquals(Set.of(yew.getItemId()),VaultRanking.select(VaultRanking.View.HIGHEST,entries,60,allowed,t->"",Map.of()));
        assertEquals(Set.of(willow.getItemId(),yew.getItemId()),VaultRanking.select(VaultRanking.View.CUSTOM,entries,99,allowed,t->"Willow sapling",Map.of(PatchType.TREE,Set.of(yew))));
        assertTrue(VaultRanking.select(VaultRanking.View.TOP_THREE,entries,99,Set.of(),t->"",Map.of()).isEmpty());
    }
    @Test public void repeatedFavouriteDoesNotInflatePlantableStock()
    {
        Crop snape=crop(PatchType.ALLOTMENT,"Snape grass"),watermelon=crop(PatchType.ALLOTMENT,"Watermelon");
        List<VaultRanking.Entry> entries=List.of(new VaultRanking.Entry(snape.getItemId(),2,snape),new VaultRanking.Entry(snape.getItemId(),2,snape),new VaultRanking.Entry(watermelon.getItemId(),3,watermelon));
        assertEquals(Set.of(watermelon.getItemId()),VaultRanking.select(VaultRanking.View.HIGHEST,entries,99,Set.of(PatchType.ALLOTMENT),t->"",Map.of()));
    }
}
