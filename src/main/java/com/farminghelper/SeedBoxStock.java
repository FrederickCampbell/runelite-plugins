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
    private boolean complete,orderKnown,adding,checkValid,nativeBaselineKnown;
    private int transferTick,checkTick,lastCheckLineTick,nativeTick=-1,revision,onlyId=-1;
    private Map<Integer,Integer> nativeAdditions=Map.of();
    static boolean isBox(int id){return id==ItemID.SEED_BOX||id==ItemID.SEED_BOX_OPEN;}
    static int boxId(Map<Integer,Integer> bag)
    {
        if(bag.getOrDefault(ItemID.SEED_BOX_OPEN,0)>0)return ItemID.SEED_BOX_OPEN;
        return bag.getOrDefault(ItemID.SEED_BOX,0)>0?ItemID.SEED_BOX:-1;
    }
    void clear(){cached=Map.of();forget();}
    void forget(){observed=Map.of();complete=false;orderKnown=false;nativeTick=-1;nativeBaselineKnown=false;nativeAdditions=Map.of();cancel();checking=null;revision++;}
    void restore(String snapshot){clear();cached=Map.copyOf(StockSnapshot.decode(snapshot));}
    int cachedTotal(){long total=0;for(int n:cached.values())total+=n;return (int)Math.min(Integer.MAX_VALUE,total);}
    String snapshot(){return StockSnapshot.encode(cached);}
    boolean known(){return complete||!observed.isEmpty();}
    boolean complete(){return complete;}
    boolean orderKnown(){return orderKnown;}
    void orderUncertain(){orderKnown=false;}
    int revision(){return revision;}
    void cancel(){before=null;onlyId=-1;}
    boolean pending(){return before!=null;}
    static boolean conflictingLooseAction(String option)
    {
        return option.equals("take")||option.equals("drop")||option.equals("destroy")||
            option.startsWith("deposit")||option.startsWith("withdraw")||option.startsWith("store")||
            option.startsWith("sell")||option.startsWith("offer")||option.startsWith("remove");
    }
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
        // More than one container update can precede chat in a single tick.
        Map<Integer,Integer> additions=tick>=0&&tick==nativeTick?new HashMap<>(nativeAdditions):new HashMap<>();
        nativeBaselineKnown=complete&&(tick!=nativeTick||nativeBaselineKnown);
        if(complete&&tick>=0)ordered.forEach((id,n)->{
            int delta=n-observed.getOrDefault(id,0);if(delta>0)additions.merge(id,delta,PhysicalStock::sum);
        });
        nativeAdditions=additions;replace(ordered,true,true);nativeTick=tick;cancel();checking=null;
        return true;
    }
    private void replace(Map<Integer,Integer> next,boolean full,boolean ordered)
    {
        // Every caller transfers a private, ordered snapshot here.
        observed=Collections.unmodifiableMap(next);
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
        Set<Integer> ids;
        if (onlyId > 0) ids = Collections.singleton(onlyId);
        else
        {
            ids = new HashSet<>(before.keySet());
            ids.addAll(loose.keySet());
        }
        Map<Integer,Integer> next = null;
        for (int id : ids)
        {
            int delta = before.getOrDefault(id, 0) - loose.getOrDefault(id, 0);
            if ((adding ? delta <= 0 : delta >= 0) || !accepts.test(id)) continue;
            if (next == null) next = new LinkedHashMap<>(observed);
            adjust(next, id, delta);
        }
        if (next == null) return false;
        cancel();
        checking = null;
        if (next.size() > 6) forget();
        else replace(next, complete, false);
        return true;
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
        boolean nativeNow=nativeTick==tick;
        Matcher pickup=PICKUP.matcher(message),stolen=STOLEN.matcher(message);
        boolean p=pickup.matches();
        if(p||stolen.matches())
        {
            int id=resolve.applyAsInt(p?pickup.group(2):stolen.group(1));
            int n=p?number(pickup.group(1)):1;
            if(id>0&&n>0)
            {
                if(nativeNow||nativeTick>=0&&tick-nativeTick==1)
                {
                    if(nativeAdditions.getOrDefault(id,0)>=n){adjust(nativeAdditions,id,-n);return false;}
                    // Without a previous snapshot, the message may describe seeds
                    // already counted by the native container. Keep only the lower bound.
                    if(!nativeBaselineKnown){complete=false;orderKnown=false;if(!nativeNow)checking=null;return false;}
                    if(nativeNow)return false;
                }
                checking=null;
                Map<Integer,Integer> next=new LinkedHashMap<>(observed);adjust(next,id,n);
                if(next.size()>6)forget();else replace(next,complete,false);
                return true;
            }
        }
        if(nativeNow)return false;
        if(message.equalsIgnoreCase("The seed box contains:"))
        {checking=new LinkedHashMap<>();checkTick=lastCheckLineTick=tick;checkValid=!pending();return false;}
        if(message.equalsIgnoreCase("The seed box is empty.")||message.equalsIgnoreCase("Your seed box is now empty."))
        {replace(Map.of(),true,true);cancel();checking=null;return true;}
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
}
