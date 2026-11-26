package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import net.runelite.api.coords.WorldPoint;

/** Run the same allocation fixtures against either checkout outside the plugin jar. */
public final class RefactorProbe
{
    private static volatile int sink;
    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    public static void main(String[] args) throws Exception
    {
        if (args.length == 2 && "pixels".equals(args[0]))
        {
            for (String name : List.of("farming-helper-preview.png", "farming-helper-home-preview.png", "farming-helper-preparation.png"))
            {
                var a = javax.imageio.ImageIO.read(new java.io.File(args[1] + "/build/reports", name));
                var b = javax.imageio.ImageIO.read(new java.io.File("build/reports", name));
                if (a.getWidth()!=b.getWidth() || a.getHeight()!=b.getHeight()) throw new AssertionError(name);
                int changed = 0;
                for (int y=0; y<a.getHeight(); y++) for (int x=0; x<a.getWidth(); x++)
                    if (a.getRGB(x,y)!=b.getRGB(x,y)) changed++;
                System.out.printf("%s: %dx%d, changed pixels=%d%n",name,a.getWidth(),a.getHeight(),changed);
                if (changed != 0) throw new AssertionError(name);
            }
            return;
        }
        if (args.length > 0 && "preview".equals(args[0]))
        {
            FarmingHelperPreviewTest preview = new FarmingHelperPreviewTest();
            preview.rendersHomeAtSidebarWidth();
            preview.rendersPreparationWithItsPinnedStartButton();
            preview.rendersGuideAtSidebarWidth();
            return;
        }

        THREADS.setThreadAllocatedMemoryEnabled(true);
        QuestFarmingStateReader reader = new QuestFarmingStateReader(null, null);
        List<FarmRunPatch> patches = FarmRunCatalog.patches();
        WorldPoint tile = new WorldPoint(2813, 3463, 0);
        Map<Integer,Integer> stock = new HashMap<>();
        for (int i = 10000; i < 10512; i++) stock.put(i, 1);
        List<FarmRunPatch> ordinary = List.of(new FarmRunPatch("Catherby", "", PatchType.HERB));

        measure("regions", () -> {
            int hits = 0;
            for (FarmRunPatch patch : patches)
                if (reader.inPatchRegion(patch.getPatchType().name(), patch.getLocation(), patch.getName(), tile)) hits++;
            return hits;
        });
        measure("watering", () -> CropCare.wateringCan(stock, id -> id == 10333 ? "Watering can(7)" : "Other item"));
        measure("ordinary-kit", () -> {
            Map<Integer,Integer> kit = new HashMap<>();
            CompostRun.addSupplies(kit, ordinary, Map.of(), stock, new HashMap<>());
            return kit.size() + BirdhouseRun.supplies(ordinary, Map.of(), stock, kit, 80, 80).size();
        });
        String encoded=StockSnapshot.encode(stock);
        measure("snapshot-decode", () -> StockSnapshot.decode(encoded).hashCode());
        Map<Integer,Integer> required=new java.util.LinkedHashMap<>();
        for(int id=10000;id<10128;id++)required.put(id,1);
        String[] names={"Watering can(7)","Oak seed","Plant cure","Coins"};
        measure("supply-order", () -> SupplyOrder.ordered(required,id->names[id%4]).hashCode());
        int[] calls={0};
        SupplyOrder.ordered(required,id->{calls[0]++;return names[id%4];});
        System.out.printf("NAME_LOOKUPS supply-order %d%n",calls[0]);
        measure("guide-kit", () -> Supplies.guideKit(patches,false,false).hashCode());
        measure("timestamps", () -> {
            int result = 1;
            for (String input : SAMPLES)
                result = 31 * result + Long.hashCode(QuestFarmingStateReader.timestampMillis(input));
            return result;
        });

        // Both revisions run these v1.10-compatible fixtures on the same runner.
        Map<Integer,Integer> bag=new HashMap<>(), demand=new HashMap<>();
        for(int i=1;i<=20;i++)bag.put(i,i%3==0?10:1);
        for(int i=1;i<=38;i++)demand.put(i,i%3==0?10:1);
        java.util.function.IntPredicate stackable=id->id%3==0;
        measure("loaded-slots",()->Preparation.loadedSlots(bag,demand,Map.of(30,1),Map.of(33,10),stackable));
        measure("prefix-search",()->{
            Preparation.Window window=new Preparation.Window(48);
            do {
                Map<Integer,Integer> req=new HashMap<>();
                req.put(1,window.limit());req.put(3,window.limit()*3);
                if(!window.advance(Preparation.loadedSlots(bag,req,Map.of(),Map.of(),stackable)<=24))break;
            }while(true);
            return window.limit();
        });
        measure("bank-choice",()->Preparation.choose(80,tile,false,false).name.hashCode());
        Map<Integer,Integer> other=new HashMap<>();
        for(int i=10400;i<10700;i++)other.put(i,2);
        measure("stock-merge",()->PhysicalStock.combine(stock,other).hashCode());
        Crop oak=CropCatalog.findByName(PatchType.TREE,"Oak sapling");
        List<FarmRunPatch> trees=List.of(new FarmRunPatch("Varrock","",PatchType.TREE),new FarmRunPatch("Falador","",PatchType.TREE),
            new FarmRunPatch("Farming Guild","",PatchType.TREE));
        Map<FarmRunPatch,Crop> treesAllocated=Map.of(trees.get(0),oak,trees.get(1),oak,trees.get(2),oak);
        Map<Integer,Integer> aliases=Map.of(60001,oak.getItemId(),60002,oak.getItemId(),60003,oak.getItemId());
        Map<Integer,Integer> materials=Map.of(60001,5,60002,5,60003,5);
        java.util.function.IntFunction<String> materialName=id->id==60001?"Oak seedling(w)":id==60002?"Oak seedling":"Acorn";
        measure("tree-aliases",()->TreePreparation.preparation(treesAllocated,materials,aliases,Map.of(60002,1),materialName).hashCode());
        measure("tree-no-aliases",()->TreePreparation.preparation(treesAllocated,Map.of(oak.getItemId(),10),Map.of(),Map.of(),materialName).hashCode());
        int[] lookups={0};
        TreePreparation.preparation(treesAllocated,materials,aliases,Map.of(60002,1),id->{lookups[0]++;return materialName.apply(id);});
        System.out.printf("LOOKUPS tree-aliases %d%n",lookups[0]);
        Map<Integer,Integer> cropStock=new HashMap<>();
        for(PatchType type:PatchType.values())for(Crop crop:CropCatalog.crops(type))cropStock.put(crop.getItemId(),40);
        measure("crop-allocation",()->{
            int result=1;
            for(var entry:SeedChoices.allocate(patches,85,cropStock,t->"",p->"",Map.of(),true,p->false).entrySet())
                result=31*result+entry.getKey().key().hashCode()+entry.getValue().getItemId();
            return result;
        });
        Map<Integer,List<String>> options=new java.util.LinkedHashMap<>();
        options.put(1,List.of("Falador tablet","Varrock tablet","Lumbridge tablet","Ardougne tablet","Catherby tablet","Camelot tablet"));
        options.put(2,List.of("Explorer's ring: Teleport","Skills necklace: Farming Guild","Ring of wealth: Falador Park"));
        options.put(3,List.of("Ectophial","Icy basalt","Stony basalt","Harmony Island tablet","Hosidius tablet"));
        options.put(4,List.of("Trinket of fairies: Tool Lep - Falador Farm","Trinket of fairies: Tool Lep - Falador Park","Trinket of fairies: Tool Lep - Farming Guild","Trinket of fairies: Tool Lep - Varrock"));
        for(int i=5;i<45;i++)options.put(i,List.of("Falador tablet","Varrock tablet","Catherby tablet"));
        Map<Integer,Integer> travelStock=new HashMap<>();for(int id:options.keySet())travelStock.put(id,5);
        measure("transport-budget",()->RunTeleports.select(patches,options,Map.of(1,1),travelStock,(id,n)->n).hashCode());
        int[] checks={0};
        RunTeleports.select(patches,options,Map.of(1,1),travelStock,(id,n)->{checks[0]++;return n;});
        System.out.printf("LOOKUPS transport-budget %d%n",checks[0]);
        measure("compost-batch",()->{
            Map<Integer,Integer> req=new HashMap<>(Map.of(CompostRun.WATERMELON,30,CompostRun.ASH,50));
            CompostRun.batch(req,Map.of(999,4),4,id->id==CompostRun.ASH);
            return req.hashCode();
        });
    }

    private static final List<String> SAMPLES = List.of("4:1234567890", "87:1700000000", "0:1", "34:1700102222");
    private static void measure(String name, IntSupplier operation)
    {
        final int iterations = 1024;
        int expected = operation.getAsInt();
        for (int i = 0; i < iterations; i++) sink = operation.getAsInt();
        long[] samples = new long[5], timings = new long[5];
        long thread = Thread.currentThread().getId();
        for (int batch = 0; batch < samples.length; batch++)
        {
            long before = THREADS.getThreadAllocatedBytes(thread), start=System.nanoTime();
            for (int i = 0; i < iterations; i++)
            {
                int result = operation.getAsInt();
                if (result != expected) throw new AssertionError(name);
                sink = result;
            }
            timings[batch]=(System.nanoTime()-start)/iterations;
            samples[batch] = (THREADS.getThreadAllocatedBytes(thread) - before) / iterations;
        }
        Arrays.sort(samples);
        System.out.printf("PROBE %s %d %d%n", name, expected, samples[2]);
        Arrays.sort(timings);
        System.out.printf("TIME %s %d%n",name,timings[2]);
    }
}
