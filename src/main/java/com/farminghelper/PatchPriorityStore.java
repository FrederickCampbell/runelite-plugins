package com.farminghelper;

import java.util.List;
import net.runelite.client.config.ConfigManager;

/** Single account-scoped preference store for physical patch types. */
final class PatchPriorityStore
{
    private static final String GROUP = "farming-helper";
    private final ConfigManager manager;

    PatchPriorityStore(ConfigManager manager) { this.manager = manager; }

    private String value(String key, String fallback)
    {
        String current = manager.getRSProfileConfiguration(GROUP, key);
        return current == null ? fallback : current;
    }

    PatchPriority get(PatchType type)
    {
        return new PatchPriority(Boolean.parseBoolean(value("cropCustom_" + type.name(),"false")),
            value("cropPriority_" + type.name(),""), value("cropReserve_" + type.name(),""));
    }

    void save(PatchType type, boolean custom, List<Crop> ranked, String reserved)
    {
        if (manager.getRSProfileKey() == null) return;
        manager.setRSProfileConfiguration(GROUP,"cropCustom_" + type.name(),Boolean.toString(custom));
        manager.setRSProfileConfiguration(GROUP,"cropPriority_" + type.name(),PriorityEditorModel.encode(ranked));
        manager.setRSProfileConfiguration(GROUP,"cropReserve_" + type.name(),reserved);
    }
}
