package com.farminghelper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VaultEligibilityCacheTest
{
    private static Field field(String name)throws Exception
    {Field f=FarmingHelperPlugin.class.getDeclaredField(name);f.setAccessible(true);return f;}
    @Test public void vaultReadsOnlyCachedEligibilityNotQuestScripts()throws Exception
    {
        FarmingHelperPlugin plugin=new FarmingHelperPlugin();
        Client client=mock(Client.class);
        field("client").set(plugin,client);
        field("legacy").set(plugin,new FarmingHelperConfig(){});
        FarmRunPatch herb=new FarmRunPatch("Falador","",PatchType.HERB);
        FarmRunPatch tree=new FarmRunPatch("Varrock","",PatchType.TREE);
        @SuppressWarnings("unchecked") List<FarmRunPatch> patches=(List<FarmRunPatch>)field("patches").get(plugin);
        patches.add(herb);patches.add(tree);
        field("vaultAccessible").set(plugin,Set.of(herb.key()));
        field("lastAccessLevel").setInt(plugin,99);
        assertTrue(plugin.vaultAccessReady(99));
        assertFalse(plugin.vaultAccessReady(98));
        assertEquals(Set.of(PatchType.HERB),plugin.vaultAllowed(99));
        assertTrue(plugin.vaultOverrides(99).isEmpty());
        verifyNoInteractions(client);
    }
}
