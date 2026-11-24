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
        Map<Integer,Integer> remaining = new HashMap<>(stock);
        Map<FarmRunPatch,Crop> result = new LinkedHashMap<>();
        // Reserve explicit choices first, even if the requested seed is missing.
        for (FarmRunPatch patch : patches)
        {
            Crop crop = CropCatalog.findByName(patch.getPatchType(), overrides.apply(patch));
            if (crop != null && crop.getLevel() <= level) assign(patch,crop,remaining,result);
        }
        for (FarmRunPatch patch : patches)
        {
            if (result.containsKey(patch)) continue;
            PatchType type = patch.getPatchType();
            List<Crop> candidates = new ArrayList<>();
            for (String name : priorities.apply(type).split(","))
            {
                Crop crop = CropCatalog.findByName(type,name.trim());
                if (crop != null && crop.getLevel() <= level && !candidates.contains(crop)) candidates.add(crop);
            }
            List<Crop> automatic = new ArrayList<>(CropCatalog.crops(type));
            automatic.sort(Comparator.comparingInt(Crop::getLevel).reversed());
            for (Crop crop : automatic)
                if (crop.getLevel() <= level && !candidates.contains(crop)) candidates.add(crop);
            if (candidates.isEmpty()) continue;
            Crop choice = candidates.stream().filter(c -> remaining.getOrDefault(c.getItemId(),0) >= c.getQuantity())
                .findFirst().orElse(candidates.get(0));
            assign(patch,choice,remaining,result);
        }
        Map<FarmRunPatch,Crop> ordered = new LinkedHashMap<>();
        for (FarmRunPatch patch : patches) if (result.containsKey(patch)) ordered.put(patch,result.get(patch));
        return ordered;
    }

    private static void assign(FarmRunPatch patch, Crop crop, Map<Integer,Integer> remaining,
        Map<FarmRunPatch,Crop> result)
    {
        remaining.compute(crop.getItemId(), (id,n) -> Math.max(0,(n == null ? 0 : n) - crop.getQuantity()));
        result.put(patch,crop);
    }
}
