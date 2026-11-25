package com.farminghelper;

import java.util.*;
import java.util.function.Function;

/** Allocate observed stock without making missing supplies a condition of guidance. */
final class SeedChoices
{
    static Map<FarmRunPatch, Crop> allocate(List<FarmRunPatch> patches, int level,
        Map<Integer, Integer> stock, Function<PatchType, String> priorities)
    {
        return allocate(patches, level, stock, priorities, p -> "");
    }

    static Map<FarmRunPatch, Crop> allocate(List<FarmRunPatch> patches, int level,
        Map<Integer, Integer> stock, Function<PatchType, String> priorities,
        Function<FarmRunPatch, String> overrides)
    {
        return allocate(patches,level,stock,priorities,overrides,Collections.emptyMap());
    }

    static Map<FarmRunPatch,Crop> allocate(List<FarmRunPatch> patches,int level,Map<Integer,Integer> stock,
        Function<PatchType,String> priorities,Function<FarmRunPatch,String> overrides,Map<FarmRunPatch,Crop> committed)
    {
        return allocate(patches,level,stock,priorities,overrides,committed,false);
    }

    static Map<FarmRunPatch,Crop> allocate(List<FarmRunPatch> patches,int level,Map<Integer,Integer> stock,
        Function<PatchType,String> priorities,Function<FarmRunPatch,String> overrides,Map<FarmRunPatch,Crop> committed,boolean stockedOnly)
    {
        return allocate(patches,level,stock,priorities,overrides,committed,stockedOnly,p->true);
    }
    static Map<FarmRunPatch,Crop> allocate(List<FarmRunPatch> patches,int level,Map<Integer,Integer> stock,
        Function<PatchType,String> priorities,Function<FarmRunPatch,String> overrides,Map<FarmRunPatch,Crop> committed,
        boolean stockedOnly,java.util.function.Predicate<FarmRunPatch> autoLily)
    {
        Map<Integer,Integer> remaining = new HashMap<>(stock);
        Map<FarmRunPatch,Crop> result = new LinkedHashMap<>();
        // Reserve a live, already-selected crop first, but never force an exhausted crop.
        for (FarmRunPatch patch : patches)
        {
            Crop crop=committed.get(patch);
            if (available(patch,crop,level,remaining)) assign(patch,crop,remaining,result);
        }
        // White lilies are permanent allotment protection. Reserve stocked lilies
        // before ordinary flower XP preferences, without inventing unavailable stock.
        for(FarmRunPatch patch:patches)if(patch.getPatchType()==PatchType.FLOWER&&!result.containsKey(patch)&&autoLily.test(patch)&&overrides.apply(patch).isBlank())
        {
            Crop lily=CropCatalog.findByName(PatchType.FLOWER,"White lily");
            if(available(patch,lily,level,remaining))assign(patch,lily,remaining,result);
        }
        // Reserve available individual preferences before general priorities.
        for (FarmRunPatch patch : patches)
        {
            Crop crop = CropCatalog.findByName(patch.getPatchType(), overrides.apply(patch));
            if (!result.containsKey(patch) && available(patch,crop,level,remaining)) assign(patch,crop,remaining,result);
        }
        for (FarmRunPatch patch : patches)
        {
            if (result.containsKey(patch)) continue;
            PatchType type = patch.getPatchType();
            List<Crop> candidates = new ArrayList<>();
            Crop preferred=CropCatalog.findByName(type,overrides.apply(patch));
            if (preferred!=null && preferred.getLevel()<=level) candidates.add(preferred);
            for(Crop crop:CropCatalog.preferences(type,priorities.apply(type)))
                if(crop.getLevel()<=level&&!candidates.contains(crop))candidates.add(crop);
            for(Crop crop:CropXp.ranked(type,level))
                if(!candidates.contains(crop))candidates.add(crop);
            if (candidates.isEmpty()) continue;
            Crop choice = candidates.stream().filter(c -> remaining.getOrDefault(c.getItemId(),0) >= c.getQuantity())
                .findFirst().orElse(stockedOnly?null:candidates.get(0));
            if(choice==null)continue;
            assign(patch,choice,remaining,result);
        }
        Map<FarmRunPatch,Crop> ordered = new LinkedHashMap<>();
        for (FarmRunPatch patch : patches) if (result.containsKey(patch)) ordered.put(patch,result.get(patch));
        return ordered;
    }

    private static boolean available(FarmRunPatch patch,Crop crop,int level,Map<Integer,Integer> stock)
    {
        return crop!=null && crop.getPatchType()==patch.getPatchType() && crop.getLevel()<=level
            && stock.getOrDefault(crop.getItemId(),0)>=crop.getQuantity();
    }

    private static void assign(FarmRunPatch patch, Crop crop, Map<Integer,Integer> remaining,
        Map<FarmRunPatch,Crop> result)
    {
        remaining.compute(crop.getItemId(), (id,n) -> Math.max(0,(n == null ? 0 : n) - crop.getQuantity()));
        result.put(patch,crop);
    }
}
