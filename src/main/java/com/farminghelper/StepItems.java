package com.farminghelper;

import java.util.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Item;

/** Inventory guidance is deliberately separate from the full run shopping list. */
final class StepItems
{
    static Set<Integer> select(StepEngine.Action action,PatchType type,Crop crop,int compost,int axe,Map<Integer,Integer> bag)
    {
        Set<Integer> result=new LinkedHashSet<>();
        switch(action)
        {
            case COMPOST: if(compost>=0)result.add(compost);break;
            case PLANT: if(crop!=null)result.add(crop.getItemId());break;
            case RAKE: result.add(ItemID.RAKE);break;
            case DIG_STUMP: case CLEAR_DEAD: case CLEAR_LIVING: result.add(ItemID.SPADE);break;
            case PAY_REMOVE: result.add(ItemID.COINS);break;
            case CHOP_TREE: if(axe>0)result.add(axe);break;
            case CURE:
                if(CropCare.prunes(type))
                    result.add(bag.getOrDefault(ItemID.FAIRY_ENCHANTED_SECATEURS,0)>0?ItemID.FAIRY_ENCHANTED_SECATEURS:ItemID.SECATEURS);
                else result.add(ItemID.PLANT_CURE);
                break;
            case PROTECT_TREE:
                for(ProtectionPayment cost:CropCatalog.protection(crop))result.add(cost.getItemId());
                break;
            default:break;
        }
        return Set.copyOf(result);
    }
    /** Pick one matching slot, in inventory order, irrespective of render traversal. */
    static boolean firstSlot(int id,int index,Item[] inventory)
    {
        if(inventory==null||index<0||index>=inventory.length||inventory[index]==null||inventory[index].getId()!=id)return false;
        for(int i=0;i<index;i++)if(inventory[i]!=null&&inventory[i].getId()==id)return false;
        return true;
    }
    private StepItems(){}
}
