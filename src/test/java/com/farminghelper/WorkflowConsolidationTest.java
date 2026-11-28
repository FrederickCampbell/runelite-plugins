package com.farminghelper;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import net.runelite.api.ItemComposition;
import static org.mockito.Mockito.*;

public class WorkflowConsolidationTest
{
    @Test public void localAndExternalResupplyPreserveTheirResumeTarget()
    {
        var local=Preparation.Workflow.GUILD_LOCAL;
        var external=Preparation.Workflow.GUILD_EXTERNAL;
        assertTrue(local.resupply().preparing);
        assertEquals(local,local.resupply().resume());
        assertEquals(external,external.resupply().resume());
        assertEquals(Preparation.Workflow.GUILD_LOCAL,Preparation.Workflow.GUILD_SEEDLINGS.resume());
        assertEquals(external,Preparation.Workflow.GUILD_DEPARTURE.resume());
        assertEquals(Preparation.Workflow.GUILD_ARRIVAL,Preparation.Workflow.GUILD_ARRIVAL.resume());
    }
    @Test public void scopedLedgerUsesSumForConsumablesAndMaximumForSharedTools()
    {
        SupplyLedger s=new SupplyLedger();
        s.consume(1,2,true);s.consume(1,5,false);
        s.reuse(2,1,true);s.reuse(2,1,false);
        s.consume(3,9,false);
        assertEquals(Map.of(1,7,2,1,3,9),s.total);
        assertEquals(Map.of(1,2,2,1),s.guild);
        assertEquals(Map.of(1,2,2,1),s.withdrawals(s.total,Preparation.Workflow.GUILD_LOCAL_PREP,Set.of()));
        assertEquals(Map.of(3,9),s.withdrawals(s.total,Preparation.Workflow.GUILD_SEEDLINGS,Set.of(3)));
        assertEquals(s.total,s.withdrawals(s.total,Preparation.Workflow.GUILD_EXTERNAL,Set.of()));
    }
    @Test public void guildPaymentScopeDoesNotAttributeUnrelatedNotedStock()
    {
        ItemComposition def=mock(ItemComposition.class);
        when(def.getNote()).thenReturn(-1);when(def.getLinkedNoteId()).thenReturn(2);
        Map<Integer,Integer> required=new LinkedHashMap<>(Map.of(2,10)),local=new HashMap<>(),notes=new HashMap<>();
        ProtectionPayment.reserve(required,Map.of(1,5),Map.of(1,5,2,10),id->def,notes,Map.of(1,3),local);
        assertEquals(Map.of(2,10,1,5),required);
        assertEquals(Map.of(1,3),local);
    }
    @Test public void newlyReadyProtectedPatchesUseSameActivityRanksAsInitialRoute()
    {
        FarmRunPatch herb=new FarmRunPatch("Catherby","",PatchType.HERB),fruit=new FarmRunPatch("Catherby","",PatchType.FRUIT_TREE);
        FarmRunPatch bird=new FarmRunPatch("Fossil Island","",PatchType.BIRDHOUSE),wood=new FarmRunPatch("Fossil Island","West",PatchType.HARDWOOD_TREE);
        RunOrder order=new RunOrder(RunOrder.shared(List.of(herb,bird),null,true,true),true);
        List<FarmRunPatch> ready=new ArrayList<>(List.of(herb,bird,wood,fruit));
        order.order(ready,new ArrayList<>(ready),new ArrayList<>(ready));
        assertEquals(List.of(fruit,herb,wood,bird),ready);
    }
}
