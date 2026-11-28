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
    static final int[] REGULAR={WOAD,WEEDS,1942,1957,1965,5986,5504,5984};
    static final int[] SUPER={WATERMELON,PINEAPPLE,ItemID.SNAPE_GRASS};
    // Snape grass is a useful herblore secondary; never choose banked snape
    // grass automatically. It is available as an explicit preference.
    private static final int[] CHEAP_SUPER={WATERMELON,PINEAPPLE};
    enum Production
    {
        REGULAR("Compost"), SUPER("Supercompost"), ULTRA("Ultracompost");
        final String label;Production(String label){this.label=label;}
        static Production load(String value){try{return valueOf(value);}catch(IllegalArgumentException|NullPointerException e){return ULTRA;}}
        @Override public String toString(){return label;}
    }
    enum Material
    {
        AUTO("Automatic (cheap compatible produce)",0), WEEDS("Weeds",CompostRun.WEEDS), WOAD("Woad leaves",CompostRun.WOAD),
        WATERMELON("Watermelons",CompostRun.WATERMELON), PINEAPPLE("Pineapples",CompostRun.PINEAPPLE), SNAPE("Snape grass",ItemID.SNAPE_GRASS);
        final String label;final int id;Material(String label,int id){this.label=label;this.id=id;}
        static Material load(String value){try{return valueOf(value);}catch(IllegalArgumentException|NullPointerException e){return AUTO;}}
        @Override public String toString(){return label;}
    }
    static final class Plan
    {
        final String instruction;
        final Map<Integer,Integer> supplies;
        final boolean ready;
        Plan(String instruction,Map<Integer,Integer> supplies,boolean ready)
        {this.instruction=instruction;this.supplies=Map.copyOf(supplies);this.ready=ready;}
    }
    static Plan plan(FarmRunPatch patch,QuestFarmingStateReader.Observation o,Map<Integer,Integer> available)
    {return plan(patch,o,available,Map.of(),Production.ULTRA,Material.AUTO);}
    static Plan plan(FarmRunPatch patch,QuestFarmingStateReader.Observation o,Map<Integer,Integer> available,Map<Integer,Integer> carried,Production production,Material material)
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
            boolean useSuper=b.kind.equals("SUPERCOMPOST") || b.state==CropState.EMPTY && (material.id!=0?contains(SUPER,material.id):
                production!=Production.REGULAR&&(total(CHEAP_SUPER,available)>=missing||total(SUPER,carried)>0));
            int[] ordinary=useSuper?CHEAP_SUPER:REGULAR;
            int[] list=ordinary;
            if(material.id!=0&&contains(useSuper?SUPER:REGULAR,material.id))
            {
                list=new int[ordinary.length+1];list[0]=material.id;System.arraycopy(ordinary,0,list,1,ordinary.length);
            }
            else if(useSuper&&carried.getOrDefault(ItemID.SNAPE_GRASS,0)>0)
            {list=SUPER;}
            int left=missing;
            for(int id:list)
            {int n=Math.min(left,Math.min(carried.getOrDefault(id,0),available.getOrDefault(id,0)));if(n>0)needs.put(id,n);left-=n;}
            for(int id:list)
            {int n=Math.min(left,Math.max(0,(id==ItemID.SNAPE_GRASS&&material!=Material.SNAPE?carried:available).getOrDefault(id,0)-needs.getOrDefault(id,0)));if(n>0)needs.merge(id,n,Integer::sum);left-=n;}
            if(left>0)needs.merge(material.id!=0&&contains(useSuper?SUPER:REGULAR,material.id)?material.id:useSuper?PINEAPPLE:WOAD,left,Integer::sum);
            return new Plan("Add "+missing+" compatible "+(useSuper?"supercompostable":"regular compostable")+" items ("+b.count+"/"+cap+"). "+(useSuper?"Do not add ordinary produce.":"Use cheap produce; regular compost can be upgraded later.")+" Fill in batches if needed; close when full.",needs,true);
        }
        if(b.state!=CropState.HARVESTABLE)return new Plan("Inspect the bin to confirm its state.",needs,true);
        String prefix=b.closed?"Open the mature bin. ":"";
        if(b.kind.equals("COMPOST"))
        {
            if(production!=Production.REGULAR)needs.put(choosePotion(available),1);
            if(production==Production.ULTRA)needs.put(ASH,ash);
            collectNeeds(needs,available,b.count);
            String upgrade=production==Production.REGULAR?"":production==Production.SUPER?"Use one dose of compost potion to make supercompost. ":"Use one dose of compost potion to make supercompost, then "+ash+" volcanic ash to make ultracompost. ";
            return new Plan(prefix+upgrade+collection(available),needs,true);
        }
        if(b.kind.equals("SUPERCOMPOST"))
        {
            collectNeeds(needs,available,b.count);
            if(production!=Production.ULTRA)return new Plan(prefix+"Collect "+b.count+" supercompost. "+collection(available),needs,true);
            needs.put(ASH,ash);
            String partial=b.count*2<ash?" The bin costs the full "+ash+" ash even when partly empty; alternatively use 2 ash per withdrawn bucket.":"";
            return new Plan(prefix+"Use "+ash+" volcanic ash on the bin to make ultracompost."+partial+" "+collection(available),needs,true);
        }
        if(b.kind.equals("ULTRACOMPOST"))
        {collectNeeds(needs,available,b.count);return new Plan("Collect "+b.count+" ultracompost. "+collection(available)+" Refill if you want another batch; Skip to leave it empty.",needs,true);}
        return new Plan("Inspect the bin to confirm its contents.",needs,true);
    }
    enum Action { INSPECT, WAIT, OPEN, POTION, ASH, COLLECT, FILL, CLOSE, TOMATO }
    static final class Step
    {
        final Action action;final int item,quantity;final String instruction;
        Step(Action action,int item,int quantity,String instruction){this.action=action;this.item=item;this.quantity=quantity;this.instruction=instruction;}
        boolean missing(Map<Integer,Integer> bag){return item>0&&bag.getOrDefault(item,0)<quantity;}
    }
    /** Derive one immediate interaction from verified bin state; the plan remains the full supply budget. */
    static Step step(FarmRunPatch patch,QuestFarmingStateReader.Observation observation,Plan plan,Map<Integer,Integer> bag,int free)
    {
        BinContents b=QuestFarmingStateReader.binContents("Farming Guild".equals(patch.getLocation()),observation);
        if(b==null||plan==null)return new Step(Action.INSPECT,-1,0,"Inspect the compost bin to confirm its contents.");
        if(b.state==CropState.GROWING)return new Step(Action.WAIT,-1,0,"Compost is decomposing.");
        if(b.kind.equals("ROTTEN_TOMATO"))return new Step(Action.TOMATO,-1,0,"Remove rotten tomatoes manually, or skip this bin.");
        if(b.state==CropState.EMPTY||b.state==CropState.FILLING)
        {
            int need=b.capacity-b.count;
            if(need<=0)return new Step(Action.CLOSE,-1,0,"Close the full compost bin.");
            int item=plan.supplies.keySet().stream().filter(CompostRun::material).filter(id->bag.getOrDefault(id,0)>0).min(Integer::compareTo).orElse(-1);
            if(item<0)item=plan.supplies.keySet().stream().filter(CompostRun::material).min(Integer::compareTo).orElse(-1);
            return new Step(Action.FILL,item,1,"Add compatible produce to the bin ("+b.count+"/"+b.capacity+").");
        }
        if(b.state!=CropState.HARVESTABLE)return new Step(Action.INSPECT,-1,0,"Inspect the compost bin.");
        if(b.closed)return new Step(Action.OPEN,-1,0,"Open the mature compost bin.");
        if(b.kind.equals("COMPOST")&&Arrays.stream(POTIONS).anyMatch(plan.supplies::containsKey))
        {
            int potion=Arrays.stream(POTIONS).filter(id->bag.getOrDefault(id,0)>0).findFirst().orElseGet(()->choosePotion(plan.supplies));
            return new Step(Action.POTION,potion,1,"Use one dose of compost potion on the bin.");
        }
        if(b.kind.equals("SUPERCOMPOST")&&plan.supplies.containsKey(ASH))
            return new Step(Action.ASH,ASH,plan.supplies.get(ASH),"Use "+plan.supplies.get(ASH)+" volcanic ash on the bin.");
        int container=bucket(bag);
        if(bag.getOrDefault(container,0)==0)
            container=plan.supplies.keySet().stream().filter(id->id==BUCKET||id==BOTTOMLESS||id==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED).findFirst().orElse(BUCKET);
        return new Step(Action.COLLECT,container,1,"Collect "+b.count+" "+b.kind.toLowerCase(Locale.ROOT).replace('_',' ')+". "+(container==BUCKET?"Use empty buckets.":"Confirm your bottomless bucket has the correct compost type and capacity."));
    }
    static int heldRefill(FarmRunPatch patch,QuestFarmingStateReader.Observation observation,Map<Integer,Integer> bag,Production production,Material material)
    {
        BinContents b=QuestFarmingStateReader.binContents("Farming Guild".equals(patch.getLocation()),observation);
        if(b==null||b.state!=CropState.EMPTY&&b.state!=CropState.FILLING)return 0;
        int[] allowed=b.state==CropState.FILLING?(b.kind.equals("SUPERCOMPOST")?SUPER:REGULAR):
            material.id!=0?new int[]{material.id}:production==Production.REGULAR?REGULAR:SUPER;
        return total(allowed,bag);
    }
    static Set<Integer> stepItems(FarmRunPatch patch,QuestFarmingStateReader.Observation o,Plan plan,Map<Integer,Integer> bag)
    {
        BinContents b=QuestFarmingStateReader.binContents("Farming Guild".equals(patch.getLocation()),o);
        if(b==null||plan==null||b.closed||b.state==CropState.GROWING)return Collections.emptySet();
        if(b.state==CropState.EMPTY||b.state==CropState.FILLING)
            return plan.supplies.keySet().stream().filter(id->bag.getOrDefault(id,0)>0).sorted().findFirst().map(Set::of).orElse(Collections.emptySet());
        if(b.state!=CropState.HARVESTABLE)return Collections.emptySet();
        if(b.kind.equals("COMPOST")&&Arrays.stream(POTIONS).anyMatch(plan.supplies::containsKey))return Arrays.stream(POTIONS).filter(id->bag.getOrDefault(id,0)>0).boxed().findFirst().map(Set::of).orElse(Collections.emptySet());
        if(b.kind.equals("SUPERCOMPOST")&&plan.supplies.containsKey(ASH))return Set.of(ASH);
        if(b.kind.equals("ULTRACOMPOST")||b.kind.equals("SUPERCOMPOST")||b.kind.equals("COMPOST"))return Set.of(bucket(bag));
        return Collections.emptySet();
    }
    private static int total(int[] ids,Map<Integer,Integer> stock){int n=0;for(int id:ids)n+=stock.getOrDefault(id,0);return n;}
    private static int choosePotion(Map<Integer,Integer> stock)
    {for(int id:POTIONS)if(stock.getOrDefault(id,0)>0)return id;return POTIONS[0];}
    private static int bucket(Map<Integer,Integer> stock)
    {return stock.getOrDefault(BOTTOMLESS,0)>0?BOTTOMLESS:stock.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED,0)>0?ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED:BUCKET;}
    private static void collectNeeds(Map<Integer,Integer> needs,Map<Integer,Integer> stock,int count)
    {int id=bucket(stock);needs.put(id,id==BUCKET?count:1);}
    private static String collection(Map<Integer,Integer> stock)
    {return bucket(stock)==BUCKET?"Collect into empty buckets; bank in batches if needed.":"Use the bottomless bucket only if empty or already contains the compost being collected and has capacity; otherwise use empty buckets. Never empty a filled bucket just for this run.";}
    /** Aggregate consumables, reusing the same collection container and counting potion doses
     * across stops rather than recommending a full potion for every bin. */
    static void addSupplies(Map<Integer,Integer> required,List<FarmRunPatch> ready,Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations,Map<Integer,Integer> stock,Map<FarmRunPatch,Plan> plans)
    {addSupplies(required,ready,observations,stock,Map.of(),plans);}
    static void addSupplies(Map<Integer,Integer> required,List<FarmRunPatch> ready,Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations,
        Map<Integer,Integer> stock,Map<Integer,Integer> bag,Map<FarmRunPatch,Plan> plans)
    {
        addSupplies(required,ready,observations,stock,bag,plans,Production.ULTRA,Material.AUTO,true);
    }
    static void addSupplies(Map<Integer,Integer> required,List<FarmRunPatch> ready,Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations,
        Map<Integer,Integer> stock,Map<Integer,Integer> bag,Map<FarmRunPatch,Plan> plans,Production production,Material material,boolean localFirst)
    {
        // Preserve the ordinary no-bin fast path for all callers.
        for(FarmRunPatch p:ready)if(p.getPatchType()==PatchType.COMPOST_BIN)
        {addSupplies(new SupplyLedger(required,new HashMap<>()),ready,observations,stock,bag,plans,production,material,localFirst,false);return;}
    }
    static void addSupplies(SupplyLedger demands,List<FarmRunPatch> ready,Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations,
        Map<Integer,Integer> stock,Map<Integer,Integer> bag,Map<FarmRunPatch,Plan> plans,Production production,Material material,boolean localFirst,boolean splitGuild)
    {
        Map<Integer,Integer> left=null;
        int doses=0,guildDoses=0;
        for(FarmRunPatch p:ready)if(p.getPatchType()==PatchType.COMPOST_BIN)
        {
            if (left == null) left = new HashMap<>(stock);
            Plan plan=plan(p,observations.get(p),left,bag,production,material);
            if(localFirst&&localHarvest(p,ready,observations,plan))
            {
                plans.put(p,new Plan("Harvest the selected compatible local crop first, then use confirmed produce for this bin. Resupply only for the remainder.",Map.of(),true));
                continue;
            }
            plans.put(p,plan);
            boolean local=splitGuild&&"Farming Guild".equals(p.getLocation());
            for(Map.Entry<Integer,Integer> e:plan.supplies.entrySet())
            {
                int id=e.getKey(),n=e.getValue();
                if(contains(POTIONS,id)){doses++;if(local)guildDoses++;continue;}
                if(id==BUCKET||id==BOTTOMLESS||id==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED)demands.reuse(id,n,local);
                else {demands.consume(id,n,local);left.compute(id,(k,v)->Math.max(0,(v==null?0:v)-n));}
            }
        }
        for(int i=POTIONS.length-1;i>=0&&doses>0;i--)
        {
            int id=POTIONS[i],n=Math.min(stock.getOrDefault(id,0),(doses+i)/(i+1));
            if(n<=0)continue;
            int local=Math.min(n,(guildDoses+i)/(i+1));
            demands.consumeSplit(id,n,local);
            doses-=n*(i+1);guildDoses=Math.max(0,guildDoses-local*(i+1));
        }
        if(doses>0)demands.consumeSplit(POTIONS[0],doses,guildDoses);
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
            if(p.getPatchType()!=PatchType.ALLOTMENT||!RunOrder.site(p).equals(RunOrder.site(bin)))continue;
            var o=observations.get(p);
            if(o==null||o.source!=QuestFarmingStateReader.Source.LIVE||o.state!=CropState.HARVESTABLE)continue;
            var produce=QuestFarmingStateReader.livingProduce(p.getPatchType().name(),o);
            if(produce!=null&&(contains(superOnly?SUPER:REGULAR,produce.getItemID())||b.state==CropState.EMPTY&&material(produce.getItemID())))return true;
        }
        return false;
    }
    /** Move only a verified compatible Guild allotment before its empty/partial bin. */
    static FarmRunPatch guildRefillCrop(FarmRunPatch bin,List<FarmRunPatch> ready,Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations,
        Production production,Material material)
    {
        if(bin==null||bin.getPatchType()!=PatchType.COMPOST_BIN||!"Farming Guild".equals(bin.getLocation()))return null;
        BinContents b=QuestFarmingStateReader.binContents(true,observations.get(bin));
        if(b==null||b.state!=CropState.EMPTY&&b.state!=CropState.FILLING||b.count>=30)return null;
        for(FarmRunPatch p:ready)
        {
            if(p.getPatchType()!=PatchType.ALLOTMENT||!"Farming Guild".equals(p.getLocation()))continue;
            var o=observations.get(p);
            if(o==null||o.source!=QuestFarmingStateReader.Source.LIVE||o.state!=CropState.HARVESTABLE)continue;
            var produce=QuestFarmingStateReader.livingProduce(p.getPatchType().name(),o);
            if(produce==null)continue;
            int id=produce.getItemID();
            if(material.id!=0&&id!=material.id)continue;
            if(b.state==CropState.EMPTY?material(id)&&(production!=Production.REGULAR||contains(REGULAR,id)):
                contains(b.kind.equals("SUPERCOMPOST")?SUPER:REGULAR,id))return p;
        }
        return null;
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
