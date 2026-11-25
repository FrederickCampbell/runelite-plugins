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
    static Map<Integer,Integer> guideKit(List<FarmRunPatch> patches,boolean autoWeed,boolean barbarianPlanting)
    {
        Map<Integer,Integer> items=new LinkedHashMap<>();
        if(patches.stream().allMatch(p->p.getPatchType()==PatchType.COMPOST_BIN||p.getPatchType()==PatchType.BIRDHOUSE))return items;
        if(!autoWeed)items.put(ItemID.RAKE,1);
        items.put(ItemID.SPADE,1);
        if(!barbarianPlanting&&patches.stream().anyMatch(p->p.getPatchType().usesDibber()))items.put(ItemID.DIBBER,1);
        return items;
    }

    static void addCompost(Map<Integer,Integer> required,int item,int count)
    {
        if(item>=0 && count>0)required.merge(item,item==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED?1:count,Integer::sum);
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
                String payment = CropCatalog.paymentName(id);
                return payment == null ? "Item #" + id : payment;
        }
    }
}
