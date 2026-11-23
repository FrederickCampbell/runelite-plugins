package com.farmingpatchadvisor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import com.farmingpatchadvisor.queststate.CropState;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorProtectionPolishTest
{
    @Test public void impossibleMagicPaymentDoesNotBlockTheSapling()
    {
        Crop magic = CropCatalog.findByName(PatchType.TREE, "Magic sapling");
        ProtectionPayment pay = ProtectionPaymentCatalog.forCrop(magic).get(0);
        Map<Integer, Integer> stock = new HashMap<>();
        stock.put(magic.getItemId(), 1);
        stock.put(pay.getItemId(), pay.getQuantity());
        DirectorPlanner.Plan plan = DirectorPlanner.build(PatchType.TREE,
            Collections.singletonList(new FarmRunPatch("Varrock", "", PatchType.TREE)),
            99, stock, false, "", "", 3, 0, 2, true, id -> false);
        assertEquals(1, plan.steps.size());
        assertFalse(plan.steps.get(0).protectionPlanned);
        assertTrue(plan.payments.isEmpty());
        assertTrue(plan.warnings.stream().anyMatch(s -> s.contains("Protection payment")));
    }

    @Test public void affordablePaymentIsIncludedAndControlsCompletion()
    {
        Crop oak = CropCatalog.findByName(PatchType.TREE, "Oak sapling");
        ProtectionPayment pay = ProtectionPaymentCatalog.forCrop(oak).get(0);
        Map<Integer, Integer> stock = new HashMap<>();
        stock.put(oak.getItemId(), 1);
        stock.put(pay.getItemId(), pay.getQuantity());
        DirectorPlanner.Plan plan = DirectorPlanner.build(PatchType.TREE,
            Collections.singletonList(new FarmRunPatch("Varrock", "", PatchType.TREE)),
            99, stock, false, "", "", 3, 0, 2, true, id -> false);
        assertTrue(plan.steps.get(0).protectionPlanned);
        assertEquals(pay.getQuantity(), plan.payments.get(pay.getItemId()).intValue());
        assertEquals(DirectorStepEngine.Action.PROTECT_TREE,
            DirectorStepEngine.actionForStep(plan.steps.get(0), CropState.GROWING, false));
    }

    @Test public void noReplacementNeverRequiresChoppingCheckedTree()
    {
        DirectorPlanner.Step step = new DirectorPlanner.Step(
            new FarmRunPatch("Varrock", "", PatchType.TREE), null, false, true);
        assertEquals(DirectorStepEngine.Action.COMPLETE,
            DirectorStepEngine.actionForStep(step, CropState.HARVESTABLE, null));
    }

    @Test public void adjustableHarvestReserveKeepsDefaultRunBehavior()
    {
        assertEquals(2, DirectorSupplies.harvestReserve(DirectorRunSelection.TREES, 8, -1));
        assertEquals(14, DirectorSupplies.harvestReserve(DirectorRunSelection.ALLOTMENTS, 8, -1));
        assertEquals(10, DirectorSupplies.harvestReserve(DirectorRunSelection.TREES, 8, 10));
        assertEquals(26, DirectorSupplies.harvestReserve(DirectorRunSelection.HERBS, 99, 99));
    }
}
