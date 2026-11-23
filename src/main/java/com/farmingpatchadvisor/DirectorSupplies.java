package com.farmingpatchadvisor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import net.runelite.api.gameval.ItemID;

/** Conservative per-wave tools and compost using the existing Advisor patch traits. */
final class DirectorSupplies
{
    private DirectorSupplies() {}

    static Map<Integer, Integer> required(List<FarmRunPatch> patches,
                                          boolean compost, boolean filledBucket,
                                          boolean carriedSecateurs)
    {
        Map<Integer, Integer> items = new LinkedHashMap<>();
        if (patches.isEmpty()) return items;
        items.put(ItemID.RAKE, 1);
        items.put(ItemID.SPADE, 1);
        if (patches.stream().anyMatch(p -> p.getPatchType().usesDibber()))
            items.put(ItemID.DIBBER, 1);
        if (carriedSecateurs && patches.stream().anyMatch(p -> p.getPatchType() == PatchType.HERB))
            items.put(ItemID.FAIRY_ENCHANTED_SECATEURS, 1);
        int doses = patches.stream().filter(p -> p.getPatchType().usesCompost())
            .mapToInt(FarmRunPatch::getPatchCount).sum();
        if (compost && doses > 0)
        {
            if (filledBucket) items.put(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 1);
            else items.put(ItemID.BUCKET_ULTRACOMPOST, doses);
        }
        return items;
    }

    static int slots(List<FarmRunPatch> patches, Map<Integer, Integer> items,
                     IntPredicate stackable)
    {
        int result = 0;
        for (Map.Entry<Integer, Integer> entry : items.entrySet())
            result += stackable.test(entry.getKey()) ? 1 : entry.getValue();
        // An axe or manual removal alternative; no particular axe is assumed.
        if (patches.stream().anyMatch(p -> DirectorStepEngine.isTreeType(p.getPatchType())))
            result++;
        return result;
    }

    static int harvestReserve(DirectorRunSelection selection, int configured, int override)
    {
        if (override >= 0) return Math.min(26, override);
        int defaults = Math.max(0, Math.min(26, configured));
        if (selection == DirectorRunSelection.TREES || selection == DirectorRunSelection.FRUIT_TREES ||
            selection == DirectorRunSelection.HARDWOODS) return 2;
        if (selection == DirectorRunSelection.ALLOTMENTS) return Math.max(14, defaults);
        return defaults;
    }

    static String name(int id)
    {
        switch (id)
        {
            case ItemID.RAKE: return "Rake";
            case ItemID.SPADE: return "Spade";
            case ItemID.DIBBER: return "Seed dibber";
            case ItemID.FAIRY_ENCHANTED_SECATEURS: return "Magic secateurs";
            case ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED: return "Filled bottomless compost bucket";
            case ItemID.BUCKET_ULTRACOMPOST: return "Ultracompost";
            default:
                String payment = ProtectionPaymentCatalog.nameForItem(id);
                return payment == null ? "Item #" + id : payment;
        }
    }
}
