package com.farminghelper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RunPolicyTest
{
    @Test public void choicesAndFallback()
    {
        assertEquals(RunPolicy.BOTH,RunPolicy.load("",true,true));
        assertEquals(RunPolicy.NONE,RunPolicy.load("NONE",true,true));
        assertFalse(RunPolicy.PROTECTION.compost);
        assertFalse(RunPolicy.COMPOST.protection);
    }
    @Test public void rawSeedsOnlyAugmentVirtualStockAndReserveOnce()
    {
        Crop crop=CropCatalog.findByName(PatchType.TREE,"Magic sapling");
        Map<Integer,Integer> stock=Map.of(5316,2,crop.getItemId(),1);
        Map<Integer,Integer> aliases=Map.of(5316,crop.getItemId());
        assertEquals(3,TreePreparation.allocationStock(stock,aliases).get(crop.getItemId()).intValue());
        assertEquals(1,stock.get(crop.getItemId()).intValue());
        Map<FarmRunPatch,Crop> plans=new LinkedHashMap<>();
        for(int i=0;i<3;i++)plans.put(new FarmRunPatch("P"+i,"",PatchType.TREE),crop);
        assertEquals(Map.of(5316,2),TreePreparation.preparation(plans,stock,aliases,Map.of(),id->"Magic seed"));
        assertEquals(Set.of(5316),VaultRanking.selectRun(List.of(new VaultRanking.Entry(5316,2,crop)),Set.of(crop.getItemId())));
    }
    @Test public void axesRespectSkillAndObservedStock()
    {
        assertEquals(2,TreeAxes.best(Map.of(1,1,2,1),60,id->id==1?"Dragon axe":"Rune axe"));
        assertEquals(1,TreeAxes.best(Map.of(1,1,2,1),61,id->id==1?"Dragon axe":"Rune axe"));
    }
}
