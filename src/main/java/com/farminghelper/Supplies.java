package com.farminghelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/** Conservative per-wave tools and compost using the existing Advisor patch traits. */
final class Supplies
{
    private Supplies() {}

    // Wiki item IDs: saltpetre and gardening trowel, used together at the Vinery.
    static final int SALTPETRE=13421, GARDENING_TROWEL=5325;
    static Map<Integer, Integer> required(List<FarmRunPatch> patches,
                                          boolean compost, boolean filledBucket,
                                          boolean carriedSecateurs)
    {
        return required(patches, compost, filledBucket, carriedSecateurs, false);
    }

    static Map<Integer, Integer> required(List<FarmRunPatch> patches, boolean compost,
                                          boolean filledBucket, boolean carriedSecateurs, boolean payRemoval)
    {
        return required(patches, compost ? (filledBucket ? ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED : ItemID.BUCKET_ULTRACOMPOST) : -1, carriedSecateurs, payRemoval);
    }

    static Map<Integer, Integer> required(List<FarmRunPatch> patches, int compostItem, boolean carriedSecateurs, boolean payRemoval)
    {
        Map<Integer, Integer> items = new LinkedHashMap<>();
        if (patches.isEmpty() || patches.stream().allMatch(p->p.getPatchType()==PatchType.COMPOST_BIN||p.getPatchType()==PatchType.BIRDHOUSE)) return items;
        items.put(ItemID.RAKE, 1);
        items.put(ItemID.SPADE, 1);
        if (patches.stream().anyMatch(p -> p.getPatchType().usesDibber()))
            items.put(ItemID.DIBBER, 1);
        if (carriedSecateurs && patches.stream().anyMatch(p -> p.getPatchType() == PatchType.HERB))
            items.put(ItemID.FAIRY_ENCHANTED_SECATEURS, 1);
        int doses = patches.stream().filter(p -> p.getPatchType().usesCompost())
            .mapToInt(FarmRunPatch::getPatchCount).sum();
        if (compostItem >= 0 && doses > 0)
            items.put(compostItem, compostItem == ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED ? 1 : doses);
        int coins = 0;
        if (payRemoval) for (FarmRunPatch patch : patches)
        {
            QuestTargets.Definition def = QuestTargets.get(patch);
            if (def != null && def.gardenerId >= 0) coins += 200 * patch.getPatchCount();
        }
        if (coins > 0) items.put(ItemID.COINS, coins);
        return items;
    }

    /** Match Quest Helper's unlock-dependent tools without changing the legacy planner API. */
    static Map<Integer, Integer> guideKit(List<FarmRunPatch> patches, int compostItem,
                                         boolean magicSecateurs, boolean payRemoval,
                                         boolean autoWeed, boolean barbarianPlanting)
    {
        Map<Integer, Integer> items = required(patches, compostItem, magicSecateurs, payRemoval);
        if (autoWeed) items.remove(ItemID.RAKE);
        if (barbarianPlanting) items.remove(ItemID.DIBBER);
        return items;
    }

    static String name(int id)
    {
        switch (id)
        {
            case CompostRun.ASH: return "Volcanic ash";
            case CompostRun.BUCKET: return "Empty bucket";
            case CompostRun.BOTTOMLESS: return "Empty bottomless compost bucket";
            case CompostRun.WEEDS: return "Weeds";
            case CompostRun.PINEAPPLE: return "Pineapple";
            case CompostRun.WATERMELON: return "Watermelon";
            case CompostRun.WOAD: return "Woad leaf";
            case 6470: return "Compost potion(4)";
            case 6472: return "Compost potion(3)";
            case 6474: return "Compost potion(2)";
            case 6476: return "Compost potion(1)";
            case SALTPETRE: return "Saltpetre";
            case GARDENING_TROWEL: return "Gardening trowel";
            case ItemID.COINS: return "Coins for tree removal";
            case ItemID.RAKE: return "Rake";
            case ItemID.SPADE: return "Spade";
            case ItemID.DIBBER: return "Seed dibber";
            case ItemID.FAIRY_ENCHANTED_SECATEURS: return "Magic secateurs";
            case ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED: return "Filled bottomless compost bucket";
            case ItemID.BUCKET_COMPOST: return "Compost";
            case ItemID.BUCKET_SUPERCOMPOST: return "Supercompost";
            case ItemID.BUCKET_ULTRACOMPOST: return "Ultracompost";
            default:
                String payment = ProtectionPaymentCatalog.nameForItem(id);
                return payment == null ? "Item #" + id : payment;
        }
    }
}
