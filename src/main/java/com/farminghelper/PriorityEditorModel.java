package com.farminghelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Shared editor model; keeps the existing profile CSV format backwards compatible. */
final class PriorityEditorModel
{
    private PriorityEditorModel() {}

    static List<Crop> catalog(Predicate<PatchType> included)
    {
        List<Crop> result = new ArrayList<>();
        for (PatchType type : PatchType.values())
        {
            if (included.test(type))
            {
                List<Crop> crops = new ArrayList<>(CropCatalog.crops(type));
                crops.sort(Comparator.comparingInt(Crop::getLevel).reversed());
                result.addAll(crops);
            }
        }
        return result;
    }

    static List<Crop> selected(List<Crop> catalog, String csv)
    {
        Map<String, Crop> byName = new LinkedHashMap<>();
        for (Crop crop : catalog) byName.putIfAbsent(RunPlanner.normalize(crop.getName()), crop);
        List<Crop> result = new ArrayList<>();
        for (String name : RunPlanner.priorityRanks(csv).keySet())
        {
            Crop crop = byName.get(name);
            if (crop != null) result.add(crop);
        }
        return result;
    }

    static String encode(List<Crop> crops)
    {
        List<String> names = new ArrayList<>();
        for (Crop crop : crops) names.add(crop.getName());
        return String.join(", ", names);
    }

    static String encodeReserves(Map<String, Integer> reserves)
    {
        List<String> entries = new ArrayList<>();
        new java.util.TreeMap<>(reserves).forEach((name, amount) ->
        {
            if (amount > 0) entries.add(name + "=" + amount);
        });
        return String.join(", ", entries);
    }

    static boolean move(List<Crop> crops, int index, int direction)
    {
        int target = index + direction;
        if (index < 0 || index >= crops.size() || target < 0 || target >= crops.size()) return false;
        java.util.Collections.swap(crops, index, target);
        return true;
    }
}
