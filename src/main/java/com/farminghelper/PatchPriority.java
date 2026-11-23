package com.farminghelper;

import java.util.List;
import java.util.Map;

/** Single preference per physical crop type, independent of run selection. */
final class PatchPriority
{
    final boolean custom;
    final String csv;
    final String reserves;

    PatchPriority(boolean custom, String csv, String reserves)
    {
        this.custom = custom;
        this.csv = csv == null ? "" : csv;
        this.reserves = reserves == null ? "" : reserves;
    }

    static PatchPriority legacy(PatchType type, boolean custom, String csv, String reserves)
    {
        List<Crop> catalog = PriorityEditorModel.catalog(t -> t == type);
        Map<String,Integer> filtered = new java.util.LinkedHashMap<>();
        Map<String,Integer> old = RunPlanner.parseReserves(reserves);
        for (Crop crop : catalog)
        {
            String key = RunPlanner.normalize(crop.getName());
            if (old.containsKey(key)) filtered.put(key, old.get(key));
        }
        return new PatchPriority(custom,
            PriorityEditorModel.encode(PriorityEditorModel.selected(catalog, csv)),
            PriorityEditorModel.encodeReserves(filtered));
    }

    static int plantingLevel(PatchType type)
    {
        return CropCatalog.crops(type).stream().mapToInt(Crop::getLevel).min().orElse(0);
    }
}
