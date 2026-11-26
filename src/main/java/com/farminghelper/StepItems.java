package com.farminghelper;

import java.util.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.ItemContainer;

/** Inventory guidance is deliberately separate from the full run shopping list. */
final class StepItems
{
    static Set<Integer> select(StepEngine.Action action,PatchType type,Crop crop,int compost,int axe,Map<Integer,Integer> bag)
    {
        switch (action)
        {
            case COMPOST: return compost >= 0 ? Set.of(compost) : Set.of();
            case PLANT: return crop == null ? Set.of() : Set.of(crop.getItemId());
            case RAKE: return Set.of(ItemID.RAKE);
            case DIG_STUMP: case CLEAR_DEAD: case CLEAR_LIVING: return Set.of(ItemID.SPADE);
            case PAY_REMOVE: return Set.of(ItemID.COINS);
            case CHOP_TREE: return axe > 0 ? Set.of(axe) : Set.of();
            case CURE: return Set.of(cure(type, bag));
            case PROTECT_TREE:
                Set<Integer> payment = new HashSet<>();
                for (ProtectionPayment cost : CropCatalog.protection(crop)) payment.add(cost.getItemId());
                return Set.copyOf(payment);
            default: return Set.of();
        }
    }

    static int cure(PatchType type, Map<Integer,Integer> carried)
    {
        if (!CropCare.prunes(type)) return ItemID.PLANT_CURE;
        return carried.getOrDefault(ItemID.FAIRY_ENCHANTED_SECATEURS,0)>0 ? ItemID.FAIRY_ENCHANTED_SECATEURS : ItemID.SECATEURS;
    }
    /** Pick one matching slot, in inventory order, irrespective of render traversal. */
    static boolean firstSlot(int id,int index,ItemContainer inventory)
    {return inventory!=null&&index>=0&&inventory.find(id)==index;}
    private StepItems(){}
}
