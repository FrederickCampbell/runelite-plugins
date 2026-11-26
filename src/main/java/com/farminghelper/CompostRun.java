package com.farminghelper;

import java.util.*;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;
import com.farminghelper.queststate.QuestFarmingStateReader.BinContents;
import net.runelite.api.gameval.ItemID;

/** One advisory step per bin, driven by the existing native compost decoder.
 * Never mix supercompostable and regular produce, never auto-dump a bin. */
final class CompostRun
{
    static final int ASH=21622, BUCKET=1925, BOTTOMLESS=22994, WEEDS=6055, PINEAPPLE=2114, WATERMELON=5982, WOAD=1793;
    static final int[] POTIONS={6476,6474,6472,6470}; // 1 to 4 doses
    static final int[] REGULAR={WOAD,WEEDS,1942,1957,1965,5986};
    static final int[] SUPER={WATERMELON,PINEAPPLE};
    static final class Plan
    {
        final String instruction;
        final Map<Integer,Integer> supplies;
        final boolean ready;
        Plan(String instruction,Map<Integer,Integer> supplies,boolean ready)
        {this.instruction=instruction;this.supplies=Map.copyOf(supplies);this.ready=ready;}
    }
    static Plan plan(FarmRunPatch patch,QuestFarmingStateReader.Observation o,Map<Integer,Integer> available)
    {return plan(patch,o,available,Map.of());}
    private static Plan plan(FarmRunPatch patch,QuestFarmingStateReader.Observation o,Map<Integer,Integer> available,Map<Integer,Integer> carried)
    {
        boolean large="Farming Guild".equals(patch.getLocation());
        int cap=large?30:15,ash=large?50:25;
        BinContents b=QuestFarmingStateReader.binContents(large,o);
        Map<Integer,Integer> needs=new LinkedHashMap<>();
        if(b==null)return new Plan("Inspect the "+(large?"large ":"")+"compost bin. Verify its contents before filling or upgrading.",needs,true);
        if(b.state==CropState.GROWING)return new Plan("Compost is decomposing. Return when ready.",needs,false);
        if(b.kind.equals("ROTTEN_TOMATO"))return new Plan("This bin contains rotten tomatoes. Remove them manually if you want to make compost, or skip this bin. Do not mix them with produce.",needs,true);
        if(b.state==CropState.EMPTY || b.state==CropState.FILLING)
        {
            int missing=cap-b.count;
            if(missing<=0)return new Plan("Close the full compost bin to start decomposition.",needs,true);
            // Preserve partial bin type. For an empty bin prefer supercompostable stock
            // only if enough for the whole batch; otherwise use ordinary cheap produce.
            boolean useSuper=b.kind.equals("SUPERCOMPOST") || b.state==CropState.EMPTY && (total(SUPER,available)>=missing||total(SUPER,carried)>0);
            int[] list=useSuper?SUPER:REGULAR;
            int left=missing;
            for(int id:list)
            {int n=Math.min(left,Math.min(carried.getOrDefault(id,0),available.getOrDefault(id,0)));if(n>0)needs.put(id,n);left-=n;}
            for(int id:list)
            {int n=Math.min(left,Math.max(0,available.getOrDefault(id,0)-needs.getOrDefault(id,0)));if(n>0)needs.merge(id,n,Integer::sum);left-=n;}
            if(left>0)needs.merge(useSuper?PINEAPPLE:WOAD,left,Integer::sum);
            return new Plan("Add "+missing+" compatible "+(useSuper?"supercompostable":"regular compostable")+" items ("+b.count+"/"+cap+"). "+(useSuper?"Do not add ordinary produce.":"Use cheap produce; regular compost can be upgraded later.")+" Fill in batches if needed; close when full.",needs,true);
        }
        if(b.state!=CropState.HARVESTABLE)return new Plan("Inspect the bin to confirm its state.",needs,true);
        String prefix=b.closed?"Open the mature bin. ":"";
        if(b.kind.equals("COMPOST"))
        {
            int potion=choosePotion(available);needs.put(potion,1);needs.put(ASH,ash);
            collectNeeds(needs,available,b.count);
            return new Plan(prefix+"Use one dose of compost potion to make supercompost, then "+ash+" volcanic ash to make ultracompost. "+collection(available),needs,true);
        }
        if(b.kind.equals("SUPERCOMPOST"))
        {
            needs.put(ASH,ash);collectNeeds(needs,available,b.count);
            String partial=b.count*2<ash?" The bin costs the full "+ash+" ash even when partly empty; alternatively use 2 ash per withdrawn bucket.":"";
            return new Plan(prefix+"Use "+ash+" volcanic ash on the bin to make ultracompost."+partial+" "+collection(available),needs,true);
        }
        if(b.kind.equals("ULTRACOMPOST"))
        {collectNeeds(needs,available,b.count);return new Plan("Collect "+b.count+" ultracompost. "+collection(available)+" Refill if you want another batch; Skip to leave it empty.",needs,true);}
        return new Plan("Inspect the bin to confirm its contents.",needs,true);
    }
    static Set<Integer> stepItems(FarmRunPatch patch,QuestFarmingStateReader.Observation o,Plan plan,Map<Integer,Integer> bag)
    {
        BinContents b=QuestFarmingStateReader.binContents("Farming Guild".equals(patch.getLocation()),o);
        if(b==null||plan==null||b.closed||b.state==CropState.GROWING)return Collections.emptySet();
        if(b.state==CropState.EMPTY||b.state==CropState.FILLING)
            return plan.supplies.keySet().stream().filter(id->bag.getOrDefault(id,0)>0).sorted().findFirst().map(Set::of).orElse(Collections.emptySet());
        if(b.state!=CropState.HARVESTABLE)return Collections.emptySet();
        if(b.kind.equals("COMPOST"))return Arrays.stream(POTIONS).filter(id->bag.getOrDefault(id,0)>0).boxed().findFirst().map(Set::of).orElse(Collections.emptySet());
        if(b.kind.equals("SUPERCOMPOST"))return Set.of(ASH);
        if(b.kind.equals("ULTRACOMPOST"))return Set.of(bucket(bag));
        return Collections.emptySet();
    }
    private static int total(int[] ids,Map<Integer,Integer> stock){int n=0;for(int id:ids)n+=stock.getOrDefault(id,0);return n;}
    private static int choosePotion(Map<Integer,Integer> stock)
    {for(int id:POTIONS)if(stock.getOrDefault(id,0)>0)return id;return POTIONS[0];}
    private static int bucket(Map<Integer,Integer> stock)
    {return stock.getOrDefault(BOTTOMLESS,0)>0?BOTTOMLESS:stock.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED,0)>0?ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED:BUCKET;}
    private static void collectNeeds(Map<Integer,Integer> needs,Map<Integer,Integer> stock,int count)
    {int id=bucket(stock);needs.put(id,id==BUCKET?Math.min(count,28):1);}
    private static String collection(Map<Integer,Integer> stock)
    {return bucket(stock)==BUCKET?"Collect into empty buckets; bank in batches if needed.":"Use the bottomless bucket only if empty or already contains ultracompost and has capacity; otherwise use empty buckets. Never empty a filled bucket just for this run.";}
    /** Aggregate consumables, reusing the same collection container and counting potion doses
     * across stops rather than recommending a full potion for every bin. */
    static void addSupplies(Map<Integer,Integer> required,List<FarmRunPatch> ready,Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations,Map<Integer,Integer> stock,Map<FarmRunPatch,Plan> plans)
    {addSupplies(required,ready,observations,stock,Map.of(),plans);}
    static void addSupplies(Map<Integer,Integer> required,List<FarmRunPatch> ready,Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations,
        Map<Integer,Integer> stock,Map<Integer,Integer> bag,Map<FarmRunPatch,Plan> plans)
    {
        Map<Integer,Integer> left=null;
        int doses=0;
        for(FarmRunPatch p:ready)if(p.getPatchType()==PatchType.COMPOST_BIN)
        {
            if (left == null) left = new HashMap<>(stock);
            Plan plan=plan(p,observations.get(p),left,bag);
            if(localHarvest(p,ready,observations,plan))
            {
                plans.put(p,new Plan("Harvest the selected compatible local crop first, then use confirmed produce for this bin. Resupply only for the remainder.",Map.of(),true));
                continue;
            }
            plans.put(p,plan);
            for(Map.Entry<Integer,Integer> e:plan.supplies.entrySet())
            {
                int id=e.getKey(),n=e.getValue();
                if(Arrays.stream(POTIONS).anyMatch(i->i==id)){doses++;continue;}
                if(id==BUCKET||id==BOTTOMLESS||id==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED)required.merge(id,n,Math::max);
                else {required.merge(id,n,Integer::sum);left.compute(id,(k,v)->Math.max(0,(v==null?0:v)-n));}
            }
        }
        for(int i=POTIONS.length-1;i>=0&&doses>0;i--)
        {int id=POTIONS[i],n=Math.min(stock.getOrDefault(id,0),(doses+i)/(i+1));if(n>0){required.merge(id,n,Integer::sum);doses-=n*(i+1);}}
        if(doses>0)required.merge(POTIONS[0],doses,Integer::sum);
    }
    /** Only defer bank produce when a selected earlier local plot has a
     * verified compatible harvest. Never add its expected yield to stock. */
    static boolean localHarvest(FarmRunPatch bin,List<FarmRunPatch> ready,
        Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations,Plan plan)
    {
        BinContents b=QuestFarmingStateReader.binContents("Farming Guild".equals(bin.getLocation()),observations.get(bin));
        if(b==null||(b.state!=CropState.EMPTY&&b.state!=CropState.FILLING)||plan.supplies.isEmpty())return false;
        boolean superOnly=b.kind.equals("SUPERCOMPOST")||plan.supplies.keySet().stream().anyMatch(id->contains(SUPER,id));
        for(FarmRunPatch p:ready)
        {
            if(p.equals(bin))break;
            if(!RunOrder.site(p).equals(RunOrder.site(bin)))continue;
            var o=observations.get(p);
            if(o==null||o.source!=QuestFarmingStateReader.Source.LIVE||o.state!=CropState.HARVESTABLE)continue;
            var produce=QuestFarmingStateReader.livingProduce(p.getPatchType().name(),o);
            if(produce!=null&&(contains(superOnly?SUPER:REGULAR,produce.getItemID())||b.state==CropState.EMPTY&&material(produce.getItemID())))return true;
        }
        return false;
    }
    private static boolean contains(int[] ids,int id){for(int candidate:ids)if(candidate==id)return true;return false;}
    static boolean material(int id){return contains(SUPER,id)||contains(REGULAR,id);}
    static boolean batchItem(int id){return id==BUCKET||material(id);}
    /** Single bins can require 30 items. Request a feasible batch instead of
     * treating all 30 items as a single withdrawal. Keep non-batch supplies. */
    static void batch(Map<Integer,Integer> required,Map<Integer,Integer> bag,int occupied,java.util.function.IntPredicate stackable)
    {batch(required,bag,Map.of(),Map.of(),occupied,stackable);}
    static void batch(Map<Integer,Integer> required,Map<Integer,Integer> bag,Map<Integer,Integer> equipped,
        Map<Integer,Integer> box,int occupied,java.util.function.IntPredicate stackable)
    {
        Map<Integer,Integer> batches=new TreeMap<>();
        required.entrySet().removeIf(e->{
            int id=e.getKey();if(!batchItem(id))return false;
            batches.put(id,e.getValue());return true;
        });
        int free=Math.max(0,28-Preparation.loadedSlots(Math.max(occupied,Preparation.slots(bag,Map.of(),stackable)),
            bag,required,equipped,box,stackable));
        boolean selected=false;
        for(var e:batches.entrySet())
        {
            int id=e.getKey(),held=bag.getOrDefault(id,0),need=e.getValue();
            boolean stack=stackable.test(id);
            int n=stack?(held>0||free>0?need:0):Math.min(need,held+free);
            if(n<=0)continue;
            required.put(id,n);selected=true;
            free-=stack?(held>0?0:1):Math.max(0,n-held);
        }
        if(!selected&&!batches.isEmpty())required.put(batches.keySet().iterator().next(),1);
    }
}
