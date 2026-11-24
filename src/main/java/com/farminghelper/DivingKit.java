package com.farminghelper;
import java.util.*;
import net.runelite.api.gameval.ItemID;
/** Coral requires the complete pair or medallion; seaweed does not. */
final class DivingKit
{
    static final int HELMET=7534,APPARATUS=7535,MEDALLION=ItemID.MEDALLION_OF_THE_DEEP;
    static void require(Map<Integer,Integer> required,Map<Integer,Integer> owned,Map<Integer,Integer> carried)
    {
        if(carried.getOrDefault(MEDALLION,0)>0)required.put(MEDALLION,1);
        else if(carried.getOrDefault(HELMET,0)>0&&carried.getOrDefault(APPARATUS,0)>0)
        {required.put(HELMET,1);required.put(APPARATUS,1);}
        else if(owned.getOrDefault(MEDALLION,0)>0)required.put(MEDALLION,1);
        else{required.put(HELMET,1);required.put(APPARATUS,1);}
    }
}
