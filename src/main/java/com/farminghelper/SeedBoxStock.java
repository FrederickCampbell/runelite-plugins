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
    private int transferTick,checkTick,nativeTick=-1,revision,onlyId=-1;
    static boolean isBox(int id){return id==ItemID.SEED_BOX||id==ItemID.SEED_BOX_OPEN;}
    static int boxId(Map<Integer,Integer> bag)
    {
        if(bag.getOrDefault(ItemID.SEED_BOX_OPEN,0)>0)return ItemID.SEED_BOX_OPEN;
        return bag.getOrDefault(ItemID.SEED_BOX,0)>0?ItemID.SEED_BOX:-1;
    }
    void clear(){cached=Map.of();forget();}
    void forget(){observed=Map.of();complete=false;orderKnown=false;nativeTick=-1;cancel();checking=null;revision++;}
    void restore(String snapshot){clear();cached=Map.copyOf(StockSnapshot.decode(snapshot));}
    int cachedTotal(){return cached.values().stream().mapToInt(Integer::intValue).sum();}
    String snapshot(){return StockSnapshot.encode(cached);}
    boolean known(){return complete||!observed.isEmpty();}
    boolean complete(){return complete;}
    boolean orderKnown(){return orderKnown;}
    int revision(){return revision;}
    void cancel(){before=null;onlyId=-1;}
    boolean pending(){return before!=null;}
    void observe(ItemContainer container){observe(container,-1);}
    void observe(ItemContainer container,int tick)
    {
        if(container==null)return;
        Map<Integer,Integer> ordered=new LinkedHashMap<>();
        for(Item item:container.getItems())
            if(item!=null&&item.getId()>0&&item.getQuantity()>0)
                ordered.merge(item.getId(),item.getQuantity(),Integer::sum);
        replace(ordered,true,true);nativeTick=tick;cancel();checking=null;
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
        cancel();before=Map.copyOf(loose);adding=fill;onlyId=id;transferTick=tick;
    }
    boolean settle(Map<Integer,Integer> loose,int tick,IntPredicate accepts)
    {
        if(!pending())return false;
        if(tick-transferTick>8){cancel();return false;}
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
        if(changed){cancel();replace(next,complete,false);}
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
        adjust(next,crop.getItemId(),-consumed);replace(next,complete,orderKnown);return true;
    }
    boolean chat(String message,int tick,ToIntFunction<String> resolve)
    {
        if(nativeTick==tick)return false;
        if(message.equalsIgnoreCase("The seed box contains:"))
        {checking=new LinkedHashMap<>();checkTick=tick;checkValid=true;return false;}
        if(message.equalsIgnoreCase("The seed box is empty.")||message.equalsIgnoreCase("Your seed box is now empty."))
        {replace(Map.of(),true,true);cancel();checking=null;return true;}
        Matcher pickup=PICKUP.matcher(message),stolen=STOLEN.matcher(message);
        if(pickup.matches()||stolen.matches())
        {
            boolean p=pickup.matches();int id=resolve.applyAsInt(p?pickup.group(2):stolen.group(1));
            int n=p?number(pickup.group(1)):1;
            if(id>0&&n>0)
            {
                Map<Integer,Integer> next=new LinkedHashMap<>(observed);adjust(next,id,n);
                replace(next,complete,false);return true;
            }
        }
        if(checking!=null&&tick-checkTick<=2)
        {
            Matcher line=LINE.matcher(message);
            if(line.matches())
            {
                int id=resolve.applyAsInt(line.group(2)),n=number(line.group(1));
                if(id>0&&n>0)checking.put(id,n);else checkValid=false;
            }
        }
        return false;
    }
    private static int number(String text)
    {try{return Integer.parseInt(text.replace(",",""));}catch(NumberFormatException e){return 0;}}
    boolean finishCheck(int tick)
    {
        if(checking==null||tick<=checkTick)return false;
        Map<Integer,Integer> next=checking;checking=null;
        if(!checkValid||next.isEmpty()||tick-checkTick>3)return false;
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
        return Preparation.slots(loose,equipped,stackable)+(used?1:0);
    }
}
