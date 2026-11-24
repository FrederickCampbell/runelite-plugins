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
            boolean useSuper=b.kind.equals("SUPERCOMPOST") || b.state==CropState.EMPTY && total(SUPER,available)>=missing;
            int[] list=useSuper?SUPER:REGULAR;
            int left=missing;for(int id:list)
            {int n=Math.min(left,available.getOrDefault(id,0));if(n>0)needs.put(id,n);left-=n;if(left==0)break;}
            if(left>0)needs.merge(useSuper?PINEAPPLE:WOAD,left,Integer::sum);
            return new Plan("Add "+missing+" compatible "+(useSuper?"supercompostable":"regular compostable")+" items ("+b.count+"/"+cap+"). "+(useSuper?"Do not add ordinary produce.":"Use cheap produce; regular compost can be upgraded later.")+" Close when full.",needs,true);
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
    {
        Map<Integer,Integer> left=new HashMap<>(stock);int doses=0;
        for(FarmRunPatch p:ready)if(p.getPatchType()==PatchType.COMPOST_BIN)
        {
            Plan plan=plan(p,observations.get(p),left);plans.put(p,plan);
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
}
