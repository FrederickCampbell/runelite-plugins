package com.farmingpatchadvisor;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DirectorProfilePersistenceTest
{
    @Test public void enabledPatchesAndMergedPresetsStayAccountScoped()
    {
        ConfigManager config = mock(ConfigManager.class);
        FarmingPatchAdvisorConfig legacy = mock(FarmingPatchAdvisorConfig.class);
        AtomicReference<String> profile = new AtomicReference<>("first");
        Map<String, String> stored = new HashMap<>();

        when(config.getRSProfileKey()).thenAnswer(invocation -> profile.get());
        when(config.getRSProfileConfiguration(anyString(), anyString())).thenAnswer(invocation ->
            stored.get(profile.get() + "/" + invocation.getArgument(1)));
        doAnswer(invocation ->
        {
            stored.put(profile.get() + "/" + invocation.getArgument(1),
                invocation.getArgument(2));
            return null;
        }).when(config).setRSProfileConfiguration(anyString(), anyString(), anyString());

        DirectorPatchPreferences patches = new DirectorPatchPreferences(config, legacy);
        DirectorMergedPresets presets = new DirectorMergedPresets(config);
        FarmRunPatch troll = new FarmRunPatch("Troll Stronghold", "", PatchType.HERB);
        assertTrue(patches.setEnabled(troll, false));
        assertTrue(presets.save("Herbs and trees",
            EnumSet.of(FarmRunType.HERB, FarmRunType.TREE)));
        assertFalse(patches.enabled(troll));
        assertEquals(EnumSet.of(FarmRunType.HERB, FarmRunType.TREE),
            presets.categories("Herbs and trees"));

        profile.set("second");
        assertTrue(patches.setEnabled(troll, true));
        assertTrue(patches.enabled(troll));
        assertFalse(presets.names().contains("Herbs and trees"));
        profile.set("first");
        assertFalse(patches.enabled(troll));
        assertTrue(presets.names().contains("Herbs and trees"));
    }
}
