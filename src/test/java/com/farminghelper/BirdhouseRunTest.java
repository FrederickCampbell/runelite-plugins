package com.farminghelper;
import com.farminghelper.queststate.CropState;
import org.junit.Test;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
public class BirdhouseRunTest
{
    @Test public void nativeReadinessAndUnknown()
    {
        assertEquals(CropState.EMPTY,BirdhouseRun.decode(0,0,10000));
        assertEquals(CropState.FILLING,BirdhouseRun.decode(2,0,10000));
        assertNull(BirdhouseRun.decode(3,0,10000));
        assertEquals(CropState.GROWING,BirdhouseRun.decode(3,9000,10000));
        assertEquals(CropState.HARVESTABLE,BirdhouseRun.decode(3,7000,10000));
        assertEquals(4,BirdhouseRun.patches().size());
        for(FarmRunPatch p:BirdhouseRun.patches())assertNotNull(Waypoints.destination(p));
    }

    @Test public void malformedOrFutureHistoryCannotReportAnEmptyOrBuiltTrap()
    {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        FarmRunPatch patch = BirdhouseRun.patches().get(0);
        int varp = BirdhouseRun.site(patch).varp;
        for (String record : new String[]{"0:invalid", "1:0", "2:9223372036854775807", "1:9999999999", "2:1:"})
        {
            when(config.getRSProfileConfiguration("timetracking", "birdhouse." + varp)).thenReturn(record);
            assertFalse(BirdhouseRun.observe(client, config, patch, false).known());
        }
        when(client.getVarpValue(varp)).thenReturn(2);
        assertEquals(CropState.FILLING, BirdhouseRun.observe(client, config, patch, true).state);
        when(config.getRSProfileConfiguration("timetracking", "birdhouse." + varp)).thenReturn("0:1");
        assertEquals(CropState.EMPTY, BirdhouseRun.observe(client, config, patch, false).state);
    }
}
