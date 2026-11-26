package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;
import net.runelite.api.ItemComposition;

@lombok.Getter
@lombok.RequiredArgsConstructor
final class ProtectionPayment
{
	private final String name;
	private final int itemId;
	private final int quantity;

    /** Only gardener payments may use the linked note. Never canonicalize general stock. */
    static int noteId(int id,IntFunction<ItemComposition> definitions)
    {
        ItemComposition item=definitions.apply(id);
        return item!=null&&item.getNote()==-1&&item.getLinkedNoteId()>0?item.getLinkedNoteId():-1;
    }
    static boolean carried(ProtectionPayment cost,Map<Integer,Integer> bag,IntFunction<ItemComposition> definitions)
    {
        int note=noteId(cost.itemId,definitions);
        return (long)bag.getOrDefault(cost.itemId,0)+(note>0?bag.getOrDefault(note,0):0)>=cost.quantity;
    }
    /** Reserve actual carried forms first, leaving loose stock reserved by other actions
     * alone. Withdraw additional noteable payments in noted form at a bank. */
    static void reserve(Map<Integer,Integer> required,Map<Integer,Integer> payments,Map<Integer,Integer> bag,
        IntFunction<ItemComposition> definitions,Map<Integer,Integer> noteSources)
    {
        for(var entry:payments.entrySet())
        {
            int id=entry.getKey(),need=entry.getValue(),note=noteId(id,definitions);
            if(note>0)
            {
                int noted=Math.min(need,Math.max(0,bag.getOrDefault(note,0)-required.getOrDefault(note,0)));
                int loose=Math.min(need-noted,Math.max(0,bag.getOrDefault(id,0)-required.getOrDefault(id,0)));
                if(loose>0)required.merge(id,loose,PhysicalStock::sum);
                if(need>loose)
                {
                    required.merge(note,need-loose,PhysicalStock::sum);
                    noteSources.put(note,id);
                }
            }
            else required.merge(id,need,PhysicalStock::sum);
        }
    }
}
