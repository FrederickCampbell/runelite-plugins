package com.farminghelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import com.farminghelper.queststate.CropState;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProtectionPolishTest
{
    @Test public void impossibleMagicPaymentDoesNotBlockTheSapling()
    {
        Crop magic = CropCatalog.findByName(PatchType.TREE, "Magic sapling");
        ProtectionPayment pay = ProtectionPaymentCatalog.forCrop(magic).get(0);
        Map<Integer, Integer> stock = new HashMap<>();
        stock.put(magic.getItemId(), 1);
        stock.put(pay.getItemId(), pay.getQuantity());
        RunPlanner.Plan plan = RunPlanner.build(PatchType.TREE,
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
        RunPlanner.Plan plan = RunPlanner.build(PatchType.TREE,
            Collections.singletonList(new FarmRunPatch("Varrock", "", PatchType.TREE)),
            99, stock, false, "", "", 3, 0, 2, true, id -> false);
        assertTrue(plan.steps.get(0).protectionPlanned);
        assertEquals(pay.getQuantity(), plan.payments.get(pay.getItemId()).intValue());
        assertEquals(StepEngine.Action.PROTECT_TREE,
            StepEngine.actionForStep(plan.steps.get(0), CropState.GROWING, false));
    }

    @Test public void noReplacementNeverRequiresChoppingCheckedTree()
    {
        RunPlanner.Step step = new RunPlanner.Step(
            new FarmRunPatch("Varrock", "", PatchType.TREE), null, false, true);
        assertEquals(StepEngine.Action.COMPLETE,
            StepEngine.actionForStep(step, CropState.HARVESTABLE, null));
    }

    @Test public void adjustableHarvestReserveKeepsDefaultRunBehavior()
    {
        assertEquals(2, Supplies.harvestReserve(RunSelection.TREES, 8, -1));
        assertEquals(14, Supplies.harvestReserve(RunSelection.ALLOTMENTS, 8, -1));
        assertEquals(10, Supplies.harvestReserve(RunSelection.TREES, 8, 10));
        assertEquals(26, Supplies.harvestReserve(RunSelection.HERBS, 99, 99));
    }
}
