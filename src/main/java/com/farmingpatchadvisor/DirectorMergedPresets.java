package com.farmingpatchadvisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/** Per-account reusable merged-run categories; never shared between RuneScape profiles. */
@Singleton
final class DirectorMergedPresets
{
    static final String DEFAULT_NAME = "My merged run";
    private static final String GROUP = "ironman-farming-director";
    private static final String INDEX = "directorMergedPresetNames";
    private final ConfigManager manager;

    @Inject
    DirectorMergedPresets(ConfigManager manager)
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
        if (!validName(name) || manager.getRSProfileKey() == null) return false;
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
