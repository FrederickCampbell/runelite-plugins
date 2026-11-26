package com.farminghelper;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;
import java.util.regex.*;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;

/** Read-only native snapshots and action-correlated seed transfers. Persisted
 * counts are historical hints, never silently promoted to confirmed stock. */
final class SeedBoxStock
{
    private static final Pattern PICKUP=Pattern.compile("You put ([\\d,]+) x (.+?) straight into your open seed box\\.",Pattern.CASE_INSENSITIVE);
    private static final Pattern STOLEN=Pattern.compile("You put the stolen (.+?) into your seed box\\.",Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE=Pattern.compile("([\\d,]+)\\s*x\\s*(.+?)\\.?$",Pattern.CASE_INSENSITIVE);
    private Map<Integer,Integer> observed=Map.of(),cached=Map.of(),before,checking;
    private boolean complete,orderKnown,adding,checkValid;
    private int transferTick,checkTick,lastCheckLineTick,nativeTick=-1,revision,onlyId=-1;
    private Map<Integer,Integer> nativeAdditions=Map.of();
    static boolean isBox(int id){return id==ItemID.SEED_BOX||id==ItemID.SEED_BOX_OPEN;}
    static int boxId(Map<Integer,Integer> bag)
    {
        if(bag.getOrDefault(ItemID.SEED_BOX_OPEN,0)>0)return ItemID.SEED_BOX_OPEN;
        return bag.getOrDefault(ItemID.SEED_BOX,0)>0?ItemID.SEED_BOX:-1;
    }
    void clear(){cached=Map.of();forget();}
    void forget(){observed=Map.of();complete=false;orderKnown=false;nativeTick=-1;nativeAdditions=Map.of();cancel();checking=null;revision++;}
    void restore(String snapshot){clear();cached=Map.copyOf(StockSnapshot.decode(snapshot));}
    int cachedTotal(){return (int)Math.min(Integer.MAX_VALUE,cached.values().stream().mapToLong(Integer::longValue).sum());}
    String snapshot(){return StockSnapshot.encode(cached);}
    boolean known(){return complete||!observed.isEmpty();}
    boolean complete(){return complete;}
    boolean orderKnown(){return orderKnown;}
    int revision(){return revision;}
    void cancel(){before=null;onlyId=-1;}
    boolean pending(){return before!=null;}
    void observe(ItemContainer container){observe(container,-1);}
    void observe(ItemContainer container,int tick){observe(container,tick,id->true);}
    boolean observe(ItemContainer container,int tick,IntPredicate accepts)
    {
        Item[] entries=container==null?null:container.getItems();
        if(entries==null)return false;
        Map<Integer,Integer> ordered=new LinkedHashMap<>();
        for(Item item:entries)
        {
            if(item==null||item.getId()<=0||item.getQuantity()<=0)continue;
            if(!accepts.test(item.getId())||ordered.containsKey(item.getId()))return false;
            ordered.put(item.getId(),item.getQuantity());
        }
        if(ordered.size()>6)return false;
        Map<Integer,Integer> additions=new HashMap<>();
        if(complete&&tick>=0)ordered.forEach((id,n)->{
            int delta=n-observed.getOrDefault(id,0);if(delta>0)additions.put(id,delta);
        });
        nativeAdditions=additions;replace(ordered,true,true);nativeTick=tick;cancel();checking=null;
        return true;
    }
    private void replace(Map<Integer,Integer> next,boolean full,boolean ordered)
    {
        observed=Collections.unmodifiableMap(new LinkedHashMap<>(next));
        cached=observed;complete=full;orderKnown=ordered;revision++;
    }
    Map<Integer,Integer> carried(Map<Integer,Integer> bag)
    {
        if(boxId(bag)<0)
        {
            if(known()||pending()||checking!=null)forget();
            return Map.of();
        }
        return observed;
    }
    /** Caller supplies loose inventory plus visible storage, so ordinary bank
     * movements cannot masquerade as transfers into the seed box. */
    void begin(boolean fill,int id,Map<Integer,Integer> loose,int tick)
    {
        if(pending())forget();
        checking=null;before=Map.copyOf(loose);adding=fill;onlyId=id;transferTick=tick;
    }
    boolean settle(Map<Integer,Integer> loose,int tick,IntPredicate accepts)
    {
        if(!pending())return false;
        if(tick-transferTick>8||tick<transferTick){cancel();return false;}
        if(tick==transferTick)return false;
        Set<Integer> ids=new HashSet<>(before.keySet());ids.addAll(loose.keySet());
        Map<Integer,Integer> next=new LinkedHashMap<>(observed);
        boolean changed=false;
        for(int id:ids)
        {
            if((onlyId>0&&id!=onlyId)||!accepts.test(id))continue;
            int delta=before.getOrDefault(id,0)-loose.getOrDefault(id,0);
            if(adding?delta<=0:delta>=0)continue;
            adjust(next,id,delta);changed=true;
        }
        if(changed){cancel();checking=null;replace(next,complete&&next.size()<=6,false);}
        return changed;
    }
    private static void adjust(Map<Integer,Integer> map,int id,int delta)
    {
        int n=(int)Math.max(0,Math.min(Integer.MAX_VALUE,(long)map.getOrDefault(id,0)+delta));
        if(n==0)map.remove(id);else map.put(id,n);
    }
    /** Actual planted species comes from the live patch, never the planned crop.
     * Subtract only the portion that did not disappear from loose inventory. */
    boolean planted(Crop crop,int looseBefore,int looseAfter,int originalRevision)
    {
        if(crop==null||originalRevision!=revision)return false;
        int consumed=Math.max(0,crop.getQuantity()-Math.max(0,looseBefore-looseAfter));
        if(consumed==0||observed.getOrDefault(crop.getItemId(),0)==0)return false;
        Map<Integer,Integer> next=new LinkedHashMap<>(observed);
        adjust(next,crop.getItemId(),-consumed);checking=null;replace(next,complete,orderKnown);return true;
    }
    boolean chat(String message,int tick,ToIntFunction<String> resolve)
    {
        if(nativeTick==tick)return false;
        if(message.equalsIgnoreCase("The seed box contains:"))
        {checking=new LinkedHashMap<>();checkTick=lastCheckLineTick=tick;checkValid=!pending();return false;}
        if(message.equalsIgnoreCase("The seed box is empty.")||message.equalsIgnoreCase("Your seed box is now empty."))
        {replace(Map.of(),true,true);cancel();checking=null;return true;}
        Matcher pickup=PICKUP.matcher(message),stolen=STOLEN.matcher(message);
        if(pickup.matches()||stolen.matches())
        {
            boolean p=pickup.matches();int id=resolve.applyAsInt(p?pickup.group(2):stolen.group(1));
            int n=p?number(pickup.group(1)):1;
            if(id>0&&n>0)
            {
                // A native increment immediately before its matching chat
                // message is the same observation, not a second pickup.
                if(nativeTick>=0&&tick-nativeTick==1&&nativeAdditions.getOrDefault(id,0)>=n)
                {
                    adjust(nativeAdditions,id,-n);return false;
                }
                checking=null;
                Map<Integer,Integer> next=new LinkedHashMap<>(observed);adjust(next,id,n);
                replace(next,complete&&next.size()<=6,false);return true;
            }
        }
        if(checking!=null&&tick-checkTick<=4)
        {
            Matcher line=LINE.matcher(message);
            if(line.matches())
            {
                int id=resolve.applyAsInt(line.group(2)),n=number(line.group(1));
                lastCheckLineTick=tick;
                if(id>0&&n>0&&!checking.containsKey(id)&&checking.size()<6)checking.put(id,n);else checkValid=false;
            }
        }
        return false;
    }
    private static int number(String text)
    {try{return Integer.parseInt(text.replace(",",""));}catch(NumberFormatException e){return 0;}}
    boolean finishCheck(int tick)
    {
        if(checking==null||tick-lastCheckLineTick<2)return false;
        Map<Integer,Integer> next=checking;checking=null;
        if(!checkValid||next.isEmpty()||tick-checkTick>6)return false;
        replace(next,true,false);cancel();return true;
    }
    static boolean direct(Crop target,Map<Integer,Integer> contents)
    {
        if(target==null)return false;
        for(var entry:contents.entrySet())
        {
            Crop crop=CropCatalog.findByItemId(entry.getKey());
            if(entry.getValue()>0&&crop!=null&&crop.getPatchType()==target.getPatchType())
                return crop.equals(target)&&entry.getValue()>=target.getQuantity();
        }
        return false;
    }
    static int slots(Map<Integer,Integer> required,Map<Integer,Integer> equipped,Map<Integer,Integer> box,
        java.util.function.IntPredicate stackable)
    {
        Map<Integer,Integer> loose=new HashMap<>(required);
        boolean used=false;
        for(var entry:box.entrySet())
        {
            int n=Math.min(loose.getOrDefault(entry.getKey(),0),entry.getValue());
            if(n>0){used=true;loose.computeIfPresent(entry.getKey(),(id,count)->count>n?count-n:null);}
        }
        int slots=Preparation.slots(loose,equipped,stackable);
        return used&&boxId(required)<0?PhysicalStock.sum(slots,1):slots;
    }
}
