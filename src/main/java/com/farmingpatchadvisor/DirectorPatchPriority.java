package com.farmingpatchadvisor;

import java.util.List;
import java.util.Map;

/** Single preference per physical crop type, independent of run selection. */
final class DirectorPatchPriority
{
    final boolean custom;
    final String csv;
    final String reserves;

    DirectorPatchPriority(boolean custom, String csv, String reserves)
    {
        this.custom = custom;
        this.csv = csv == null ? "" : csv;
        this.reserves = reserves == null ? "" : reserves;
    }

    static DirectorPatchPriority legacy(PatchType type, boolean custom, String csv, String reserves)
    {
        List<Crop> catalog = DirectorPriorityEditorModel.catalog(t -> t == type);
        Map<String,Integer> filtered = new java.util.LinkedHashMap<>();
        Map<String,Integer> old = DirectorPlanner.parseReserves(reserves);
        for (Crop crop : catalog)
        {
            String key = DirectorPlanner.normalize(crop.getName());
            if (old.containsKey(key)) filtered.put(key, old.get(key));
        }
        return new DirectorPatchPriority(custom,
            DirectorPriorityEditorModel.encode(DirectorPriorityEditorModel.selected(catalog, csv)),
            DirectorPriorityEditorModel.encodeReserves(filtered));
    }

    static int plantingLevel(PatchType type)
    {
        return CropCatalog.crops(type).stream().mapToInt(Crop::getLevel).min().orElse(0);
    }
}
