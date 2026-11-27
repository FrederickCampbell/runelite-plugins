package com.farminghelper;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StockPreparationRegressionTest
{
    @Test public void absentCalquatSeedAndSaplingExcludePatch()
    {
        FarmRunPatch p=new FarmRunPatch("Tai Bwo Wannai","",PatchType.CALQUAT);
        Crop c=CropCatalog.findByName(PatchType.CALQUAT,"Calquat sapling");
        int raw=5290;
        for(Map<Integer,Integer> observed:List.of(Map.<Integer,Integer>of(),Map.of(raw,0,c.getItemId(),0)))
        {
            Map<Integer,Integer> aliases=TreePreparation.aliases(observed,id->"Calquat tree seed");
            Map<Integer,Integer> allocation=TreePreparation.allocationStock(observed,aliases);
            assertTrue(SeedChoices.allocate(List.of(p),99,allocation,t->"",patch->"",Map.of(),true,patchChoice->true).isEmpty());
        }
    }
    @Test public void rawSeedReservesExactlyOnePatchAndReportsItsSource()
    {
        FarmRunPatch a=new FarmRunPatch("Tai Bwo Wannai","",PatchType.CALQUAT);
        FarmRunPatch b=new FarmRunPatch("Kastori","",PatchType.CALQUAT);
        int raw=5290;
        Map<Integer,Integer> physical=Map.of(raw,1);
        Map<Integer,Integer> aliases=TreePreparation.aliases(physical,id->"Calquat tree seed");
        Map<FarmRunPatch,Crop> plan=SeedChoices.allocate(List.of(a,b),99,TreePreparation.allocationStock(physical,aliases),t->"",p->"",Map.of(),true,patchChoice->true);
        assertEquals(1,plan.size());
        Map<Integer,Integer> prep=TreePreparation.preparation(plan,physical,aliases,Map.of(),id->"Calquat tree seed");
        assertEquals(Map.of(raw,1),prep);
        assertTrue(Preparation.sourceNote(prep,Map.of(),physical,Map.of(),false,true).contains("outdated"));
        Map<Integer,Integer> refreshed=StockSnapshot.decode("");
        assertTrue(SeedChoices.allocate(List.of(a,b),99,TreePreparation.allocationStock(refreshed,TreePreparation.aliases(refreshed,id->"Calquat tree seed")),t->"",p->"",Map.of(),true,patchChoice->true).isEmpty());
    }
    @org.junit.Test public void unchangedContainersReuseQuantitiesAndAContainerEventReplacesThem() throws Exception
    {
        var plugin=new FarmingHelperPlugin();
        var client=org.mockito.Mockito.mock(net.runelite.api.Client.class);
        var clientField=FarmingHelperPlugin.class.getDeclaredField("client");
        clientField.setAccessible(true);clientField.set(plugin,client);
        var first=plugin.stock();
        assertSame(first,plugin.stock());
        org.mockito.Mockito.verify(client).getItemContainer(net.runelite.api.gameval.InventoryID.INV);
        var event=org.mockito.Mockito.mock(net.runelite.api.events.ItemContainerChanged.class);
        org.mockito.Mockito.when(event.getContainerId()).thenReturn(net.runelite.api.gameval.InventoryID.INV);
        plugin.onItemContainerChanged(event);
        assertNotSame(first,plugin.stock());
        org.mockito.Mockito.verify(client,org.mockito.Mockito.times(2)).getItemContainer(net.runelite.api.gameval.InventoryID.INV);
    }
}
