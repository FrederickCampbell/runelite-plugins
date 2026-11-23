package com.farminghelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * One account-scoped global inclusion rule shared by every run type.
 * The legacy area toggles are consulted only until an explicit patch choice
 * has been saved; a user may re-enable a patch without changing older settings.
 */
@Singleton
final class PatchPreferences
{
    private static final String GROUP = "farming-helper";
    private final ConfigManager manager;
    private final FarmingHelperConfig legacy;

    @Inject
    PatchPreferences(ConfigManager manager, FarmingHelperConfig legacy)
    {
        this.manager = manager;
        this.legacy = legacy;
    }

    boolean enabled(FarmRunPatch patch)
    {
        String stored = manager.getRSProfileKey() == null ? null
            : manager.getRSProfileConfiguration(GROUP, "patchEnabled_" + FarmRouteManager.key(patch));
        return stored == null ? PatchLocationSelection.isEnabled(legacy, patch.getLocation())
            : Boolean.parseBoolean(stored);
    }

    boolean setEnabled(FarmRunPatch patch, boolean enabled)
    {
        if (manager.getRSProfileKey() == null) return false;
        manager.setRSProfileConfiguration(GROUP, "patchEnabled_" + FarmRouteManager.key(patch),
            Boolean.toString(enabled));
        return true;
    }

    /**
     * Expand grouped Advisor stops to Quest Helper identities BEFORE filtering.
     * Unmapped grouped stops remain a single explicitly unverified group rather
     * than inventing any physical patch identity.
     */
    static List<FarmRunPatch> expand(List<FarmRunPatch> grouped,
                                     Function<FarmRunPatch, List<String>> names)
    {
        List<FarmRunPatch> result = new ArrayList<>();
        for (FarmRunPatch patch : grouped)
        {
            List<FarmRunPatch> expanded = PatchNames.expand(patch, names.apply(patch));
            if (expanded.isEmpty())
            {
                result.add(patch);
            }
            else
            {
                result.addAll(expanded);
            }
        }
        return Collections.unmodifiableList(result);
    }

    static List<FarmRunPatch> included(List<FarmRunPatch> patches, Predicate<FarmRunPatch> enabled)
    {
        List<FarmRunPatch> result = new ArrayList<>();
        for (FarmRunPatch patch : patches)
        {
            if (enabled.test(patch)) result.add(patch);
        }
        return result;
    }
}
