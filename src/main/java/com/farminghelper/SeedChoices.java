package com.farminghelper;

import java.util.*;
import java.util.function.Function;

/** Allocate observed stock without making missing supplies a condition of guidance. */
final class SeedChoices
{
    static Map<FarmRunPatch,Crop> allocate(List<FarmRunPatch> patches,int level,Map<Integer,Integer> stock,
        Function<PatchType,String> priorities,Function<FarmRunPatch,String> overrides,Map<FarmRunPatch,Crop> committed,
        boolean stockedOnly,java.util.function.Predicate<FarmRunPatch> autoLily)
    {
        return allocate(patches,level,stock,priorities,overrides,committed,stockedOnly,autoLily,null);
    }

    static Map<FarmRunPatch,Crop> allocate(List<FarmRunPatch> patches,int level,Map<Integer,Integer> stock,
        Function<PatchType,String> priorities,Function<FarmRunPatch,String> overrides,Map<FarmRunPatch,Crop> committed,
        boolean stockedOnly,java.util.function.Predicate<FarmRunPatch> autoLily,Crop contract)
    {
        if(patches.isEmpty())return new LinkedHashMap<>();
        Map<Integer,Integer> remaining = new HashMap<>(stock);
        Map<FarmRunPatch,Crop> result = new LinkedHashMap<>();
        Map<FarmRunPatch,String> preferredNames=new HashMap<>();
        // One contract needs one eligible Guild patch, even for two allotments.
        // Reserve its actual available stock before commitments or manual priorities.
        for(FarmRunPatch patch:patches)
            if("Farming Guild".equals(patch.getLocation())&&available(patch,contract,level,remaining))
            {assign(patch,contract,remaining,result);break;}
        // Reserve a live, already-selected crop next, but never force an exhausted crop.
        for (FarmRunPatch patch : patches)
        {
            Crop crop=committed.get(patch);
            if (!result.containsKey(patch)&&available(patch,crop,level,remaining)) assign(patch,crop,remaining,result);
        }
        // White lilies are permanent allotment protection. Reserve stocked lilies
        // before ordinary flower XP preferences, without inventing unavailable stock.
        for(FarmRunPatch patch:patches)if(patch.getPatchType()==PatchType.FLOWER&&!result.containsKey(patch)&&autoLily.test(patch)&&preferredNames.computeIfAbsent(patch,overrides).isBlank())
        {
            Crop lily=CropCatalog.findByName(PatchType.FLOWER,"White lily");
            if(available(patch,lily,level,remaining))assign(patch,lily,remaining,result);
        }
        // Reserve available individual preferences before general priorities.
        for (FarmRunPatch patch : patches)
        {
            if(result.containsKey(patch))continue;
            Crop crop=CropCatalog.findByName(patch.getPatchType(),preferredNames.computeIfAbsent(patch,overrides));
            if(available(patch,crop,level,remaining))assign(patch,crop,remaining,result);
        }
        Map<PatchType,List<Crop>> byType=new EnumMap<>(PatchType.class);
        for(FarmRunPatch patch:patches)
        {
            if(result.containsKey(patch))continue;
            PatchType type=patch.getPatchType();
            List<Crop> order=byType.computeIfAbsent(type,t->{
                Set<Crop> crops=new LinkedHashSet<>();
                for(Crop crop:CropCatalog.preferences(t,priorities.apply(t)))if(crop.getLevel()<=level)crops.add(crop);
                crops.addAll(CropXp.ranked(t,level));return List.copyOf(crops);
            });
            Crop preferred=CropCatalog.findByName(type,preferredNames.get(patch));
            // An available explicit preference was already reserved above.
            // It remains the guide-anyway fallback even if its stock is exhausted.
            Crop fallback=preferred!=null&&preferred.getLevel()<=level?preferred:order.isEmpty()?null:order.get(0);
            Crop choice=stockedOnly?null:fallback;
            for(Crop c:order)if(remaining.getOrDefault(c.getItemId(),0)>=c.getQuantity()){choice=c;break;}
            if(choice!=null)assign(patch,choice,remaining,result);
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
