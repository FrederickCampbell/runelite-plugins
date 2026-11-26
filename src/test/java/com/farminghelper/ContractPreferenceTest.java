package com.farminghelper;

import java.util.*;
import net.runelite.client.plugins.timetracking.farming.Produce;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContractPreferenceTest
{
    private static final FarmRunPatch GUILD=new FarmRunPatch("Farming Guild","",PatchType.HERB);
    private static final FarmRunPatch OTHER=new FarmRunPatch("Catherby","",PatchType.HERB);
    private static final Crop SNAP=CropCatalog.findByName(PatchType.HERB,"Snapdragon");
    private static final Crop RANARR=CropCatalog.findByName(PatchType.HERB,"Ranarr");

    @Test public void allNativeContractTypesResolve()
    {
        for(Produce p:Produce.values())if(p.getContractVarbitValue()>0)
            assertNotNull(p.name(),CropCatalog.contractCrop(p.getContractVarbitValue()));
        assertNull(CropCatalog.contractCrop(0));assertNull(CropCatalog.contractCrop(999));
        assertSame(SNAP,CropCatalog.contractCrop(29));
        assertEquals("Magic sapling",CropCatalog.contractCrop(11).getName());
    }
    @Test public void reservesContractBeforeManualAndCommittedPreferences()
    {
        Map<FarmRunPatch,Crop> plan=SeedChoices.allocate(List.of(OTHER,GUILD),99,
            Map.of(SNAP.getItemId(),1,RANARR.getItemId(),1),t->"Ranarr",p->p==GUILD?"Ranarr":"Snapdragon",
            Map.of(OTHER,SNAP,GUILD,RANARR),true,p->false,SNAP);
        assertSame(SNAP,plan.get(GUILD));assertSame(RANARR,plan.get(OTHER));
    }
    @Test public void noStockOrDisabledFallsBackAndNeverAddsUnselectedGuildPatch()
    {
        for(Crop contract:Arrays.asList(SNAP,null))
            assertSame(RANARR,SeedChoices.allocate(List.of(GUILD),99,Map.of(RANARR.getItemId(),1),
                t->"Ranarr",p->"",Map.of(),true,p->false,contract).get(GUILD));
        assertSame(RANARR,SeedChoices.allocate(List.of(GUILD),99,Map.of(RANARR.getItemId(),1,SNAP.getItemId(),1),
            t->"Ranarr",p->"",Map.of(),true,p->false,null).get(GUILD));
        assertFalse(SeedChoices.allocate(List.of(OTHER),99,Map.of(RANARR.getItemId(),1,SNAP.getItemId(),1),
            t->"Ranarr",p->"",Map.of(),true,p->false,SNAP).containsKey(GUILD));
    }
    @Test public void oneAllotmentOnlyAndFullQuantityRequired()
    {
        FarmRunPatch a=new FarmRunPatch("Farming Guild","North",PatchType.ALLOTMENT);
        FarmRunPatch b=new FarmRunPatch("Farming Guild","South",PatchType.ALLOTMENT);
        Crop contract=CropCatalog.contractCrop(46),fallback=CropCatalog.contractCrop(40);
        for(int n:new int[]{2,6})
        {
            Map<FarmRunPatch,Crop> plan=SeedChoices.allocate(List.of(a,b),99,
                Map.of(contract.getItemId(),n,fallback.getItemId(),6),t->"Potato",p->"Potato",Map.of(),true,p->false,contract);
            assertSame(n==6?contract:fallback,plan.get(a));assertSame(fallback,plan.get(b));
        }
    }

    @Test public void sharedPriorityReadDoesNotChangeExplicitFallbackOrStockOrder()
    {
        FarmRunPatch a=new FarmRunPatch("Catherby","",PatchType.HERB),b=new FarmRunPatch("Weiss","",PatchType.HERB),
            c=new FarmRunPatch("Falador","",PatchType.HERB);
        var priorityReads=new java.util.concurrent.atomic.AtomicInteger();
        var overrideReads=new HashMap<FarmRunPatch,Integer>();
        Map<FarmRunPatch,Crop> plan=SeedChoices.allocate(List.of(a,b,c),99,Map.of(RANARR.getItemId(),1),
            t->{priorityReads.incrementAndGet();return "Ranarr";},p->{overrideReads.merge(p,1,Integer::sum);return p==c?"Snapdragon":"";},
            Map.of(),false,p->false,null);
        assertEquals(1,priorityReads.get());assertEquals(Map.of(a,1,b,1,c,1),overrideReads);
        assertSame(RANARR,plan.get(a));assertSame(RANARR,plan.get(b));assertSame(SNAP,plan.get(c));
        assertEquals(List.of(a,b,c),new ArrayList<>(plan.keySet()));
    }
}
