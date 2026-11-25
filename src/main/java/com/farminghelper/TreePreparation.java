package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;

/** Tree material aliases are allocation-only; actual saplings remain necessary to plant. */
final class TreePreparation
{
    static final int FILLED_POT=5354, WATERING_CAN=5331;
    enum Stage { WATERED, SEEDLING, SEED }

    /** Exact native names; tolerate the optional space before the watered suffix. */
    static Stage seedlingStage(String base,String name)
    {
        String n=name.trim().toLowerCase(Locale.ROOT).replace(" (w)","(w)");
        for(String prefix:List.of(base,base+" tree"))
        {
            if(n.equals(prefix+" seedling(w)")||n.equals(prefix+" sapling(w)"))return Stage.WATERED;
            if(n.equals(prefix+" seedling"))return Stage.SEEDLING;
        }
        return null;
    }

    static Stage stage(int id,int sapling,IntFunction<String> name)
    {
        Crop crop=CropCatalog.findByItemId(sapling);
        String n=name.apply(id);
        if(crop==null||n==null)return Stage.SEED;
        String base=crop.getName().substring(0,crop.getName().length()-8).toLowerCase(Locale.ROOT);
        Stage stage=seedlingStage(base,n);
        return stage==null?Stage.SEED:stage;
    }
    static Map<Integer,Integer> aliases(Map<Integer,Integer> stock,IntFunction<String> name)
    {
        Map<Integer,Integer> result=new TreeMap<>();
        for(int id:stock.keySet())if(stock.get(id)>0&&CropCatalog.findByItemId(id)==null)
        {
            Crop c=VaultRanking.resolve(id,name.apply(id));
            if(c!=null&&c.getName().endsWith(" sapling")&&c.getItemId()!=id)result.put(id,c.getItemId());
        }
        return result;
    }
    static Map<Integer,Integer> allocationStock(Map<Integer,Integer> stock,Map<Integer,Integer> aliases)
    {
        Map<Integer,Integer> result=new HashMap<>(stock);
        aliases.forEach((raw,sapling)->result.merge(sapling,stock.getOrDefault(raw,0),Integer::sum));
        return result;
    }
    /** Prefer already-carried seedlings, then mature stored saplings, then
     * stored seedlings, then raw seeds. Reserve each physical item at most once. */
    static Map<Integer,Integer> preparation(Map<FarmRunPatch,Crop> allocated,Map<Integer,Integer> stock,
        Map<Integer,Integer> aliases,Map<Integer,Integer> carried,IntFunction<String> name)
    {
        Map<Integer,Integer> need=new LinkedHashMap<>(), result=new LinkedHashMap<>();
        for(Crop c:allocated.values())if(c.getName().endsWith(" sapling"))need.merge(c.getItemId(),c.getQuantity(),Integer::sum);
        for(var entry:need.entrySet())
        {
            int sapling=entry.getKey();
            int remaining=Math.max(0,entry.getValue()-carried.getOrDefault(sapling,0));
            List<Integer> candidates=new ArrayList<>();
            aliases.forEach((id,target)->{if(target==sapling)candidates.add(id);});
            candidates.sort(Comparator.comparing((Integer id)->stage(id,sapling,name)).thenComparingInt(Integer::intValue));
            for(int id:candidates)if(stage(id,sapling,name)!=Stage.SEED)
            {
                int n=Math.min(remaining,Math.min(stock.getOrDefault(id,0),carried.getOrDefault(id,0)));
                if(n>0){result.put(id,n);remaining-=n;}
            }
            remaining=Math.max(0,remaining-Math.max(0,stock.getOrDefault(sapling,0)-carried.getOrDefault(sapling,0)));
            for(int id:candidates)
            {
                int n=Math.min(remaining,Math.max(0,stock.getOrDefault(id,0)-result.getOrDefault(id,0)));
                if(n>0){result.merge(id,n,Integer::sum);remaining-=n;}
            }
        }
        return result;
    }

    static Map<Integer,Integer> pending(Map<Integer,Integer> preparation,Map<Integer,Integer> aliases)
    {
        Map<Integer,Integer> pending=new HashMap<>();
        preparation.forEach((id,n)->{Integer sapling=aliases.get(id);if(sapling!=null)pending.merge(sapling,n,Integer::sum);});
        return pending;
    }

    /** Keep physical withdrawal requirements distinct from the final sapling total. */
    static Map<Integer,Integer> physicalRequirements(Map<Integer,Integer> required,Map<Integer,Integer> pending)
    {
        Map<Integer,Integer> result=new LinkedHashMap<>(required);
        pending.forEach((id,n)->result.computeIfPresent(id,(key,count)->count>n?count-n:null));
        return result;
    }

    static int count(Map<Integer,Integer> preparation,Map<Integer,Integer> aliases,IntFunction<String> name,Stage stage)
    {
        return preparation.entrySet().stream().filter(e->stage(e.getKey(),aliases.get(e.getKey()),name)==stage)
            .mapToInt(Map.Entry::getValue).sum();
    }

    static String instruction(int seeds,int seedlings,int watered)
    {
        StringJoiner note=new StringJoiner(" ");
        if(seeds>0)note.add("Prepare "+seeds+" seed(s): fill pots if needed and plant the seeds.");
        if(seeds+seedlings>0)note.add("Water "+(seeds+seedlings)+" seedling(s).");
        if(seeds+seedlings+watered>0)note.add("Wait for "+(seeds+seedlings+watered)+" seedling(s) to become plantable saplings.");
        return note.toString();
    }

    private TreePreparation(){}
}
