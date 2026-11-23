package com.farminghelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/** Per-account reusable merged-run categories; never shared between RuneScape profiles. */
@Singleton
final class MergedPresets
{
    static final String DEFAULT_NAME = "My merged run";
    private static final String GROUP = "farming-helper";
    private static final String INDEX = "directorMergedPresetNames";
    private final ConfigManager manager;

    @Inject
    MergedPresets(ConfigManager manager)
    {
        this.manager = manager;
    }

    static String encode(Set<FarmRunType> types)
    {
        List<String> values = new ArrayList<>();
        for (FarmRunType type : FarmRunType.values())
        {
            if (types.contains(type)) values.add(type.name());
        }
        return String.join(",", values);
    }

    static EnumSet<FarmRunType> decode(String stored)
    {
        EnumSet<FarmRunType> types = EnumSet.noneOf(FarmRunType.class);
        if (stored != null)
        {
            for (String value : stored.split(","))
            {
                try { types.add(FarmRunType.valueOf(value.trim())); }
                catch (IllegalArgumentException ignored) { /* Ignore deprecated categories. */ }
            }
        }
        return types;
    }

    static boolean validName(String name)
    {
        return name != null && name.matches("[A-Za-z0-9 _-]{1,40}") && !name.trim().isEmpty();
    }

    List<String> names()
    {
        String raw = read(INDEX);
        List<String> names = new ArrayList<>();
        if (raw != null)
        {
            for (String name : raw.split(";"))
            {
                if (validName(name) && !names.contains(name)) names.add(name);
            }
        }
        if (names.isEmpty()) names.add(DEFAULT_NAME);
        return Collections.unmodifiableList(names);
    }

    Set<FarmRunType> categories(String name)
    {
        String stored = validName(name) ? read("directorMerged_" + name) : null;
        return stored == null ? EnumSet.of(FarmRunType.HERB, FarmRunType.TREE)
            : decode(stored);
    }

    boolean save(String name, Set<FarmRunType> categories)
    {
        if (!validName(name) || manager.getRSProfileKey() == null || categories == null ||
            categories.isEmpty()) return false;
        List<String> names = new ArrayList<>(names());
        if (!names.contains(name)) names.add(name);
        manager.setRSProfileConfiguration(GROUP, INDEX, String.join(";", names));
        manager.setRSProfileConfiguration(GROUP, "directorMerged_" + name, encode(categories));
        return true;
    }

    boolean delete(String name)
    {
        if (!validName(name) || manager.getRSProfileKey() == null) return false;
        List<String> names = new ArrayList<>(names());
        names.remove(name);
        manager.setRSProfileConfiguration(GROUP, INDEX, String.join(";", names));
        manager.unsetRSProfileConfiguration(GROUP, "directorMerged_" + name);
        resetRoute(name);
        for (FarmRunType category : FarmRunType.values())
        {
            manager.unsetRSProfileConfiguration(GROUP, "directorMergedPriority_" + name + "_" + category.name());
            manager.unsetRSProfileConfiguration(GROUP, "directorMergedReserve_" + name + "_" + category.name());
        }
        return true;
    }

    /** Explicit inclusion and order of stops belong to the named preset,
     * never to the user's separate ALL-enabled run. */
    List<FarmRunPatch> applyRoute(String name, List<FarmRunPatch> available)
    {
        if (!validName(name)) return new ArrayList<>(available);
        String included = read("directorMergedRouteIncluded_" + name);
        List<FarmRunPatch> patches = new ArrayList<>(available);
        if (included != null)
            patches = FarmRouteManager.included(patches, Arrays.asList(included.split(";", -1)));
        String order = read("directorMergedRouteOrder_" + name);
        return order == null ? patches :
            FarmRouteManager.order(patches, Arrays.asList(order.split(";", -1)));
    }

    boolean saveRoute(String name, List<FarmRunPatch> selected,
                      Map<String, RouteTeleport> travel)
    {
        if (!validName(name) || manager.getRSProfileKey() == null) return false;
        List<String> keys = new ArrayList<>();
        for (FarmRunPatch patch : selected)
            keys.add(FarmRouteManager.key(patch));
        manager.setRSProfileConfiguration(GROUP, "directorMergedRouteIncluded_" + name,
            String.join(";", keys));
        manager.setRSProfileConfiguration(GROUP, "directorMergedRouteOrder_" + name,
            String.join(";", keys));
        for (Map.Entry<String, RouteTeleport> entry : travel.entrySet())
            manager.setRSProfileConfiguration(GROUP,
                "directorMergedTeleport_" + name + "_" + entry.getKey(), entry.getValue().name());
        return true;
    }

    RouteTeleport teleport(String name, FarmRunPatch patch, RouteTeleport inherited)
    {
        String stored = validName(name) ? read("directorMergedTeleport_" +
            name + "_" + FarmRouteManager.key(patch)) : null;
        if (stored == null) return inherited;
        try
        {
            RouteTeleport selected = RouteTeleport.valueOf(stored);
            return selected.isViableFor(patch) ? selected : RouteTeleport.NONE;
        }
        catch (IllegalArgumentException exception)
        {
            return RouteTeleport.NONE;
        }
    }

    boolean resetRoute(String name)
    {
        if (!validName(name) || manager.getRSProfileKey() == null) return false;
        manager.unsetRSProfileConfiguration(GROUP, "directorMergedRouteIncluded_" + name);
        manager.unsetRSProfileConfiguration(GROUP, "directorMergedRouteOrder_" + name);
        for (FarmRunPatch patch : FarmRunCatalog.patches())
            manager.unsetRSProfileConfiguration(GROUP, "directorMergedTeleport_" + name + "_" + FarmRouteManager.key(patch));
        return true;
    }

    private String read(String key)
    {
        return manager.getRSProfileKey() == null ? null
            : manager.getRSProfileConfiguration(GROUP, key);
    }

    static List<FarmRunPatch> selected(List<FarmRunPatch> patches, Set<FarmRunType> categories)
    {
        List<FarmRunPatch> chosen = new ArrayList<>();
        for (FarmRunPatch patch : patches)
        {
            if (categories.contains(patch.getFarmRunType())) chosen.add(patch);
        }
        return chosen;
    }
}
