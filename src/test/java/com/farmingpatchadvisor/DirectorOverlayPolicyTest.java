package com.farmingpatchadvisor;
import org.junit.Test;
import static org.junit.Assert.*;
public class DirectorOverlayPolicyTest
{
    @Test public void onlyPreparingOrRestockingShowsBankItems()
    {
        assertFalse(DirectorOverlayPolicy.bank(false,true,false,false,false,false,false));
        assertFalse(DirectorOverlayPolicy.bank(true,false,false,false,false,false,false));
        assertTrue(DirectorOverlayPolicy.bank(true,true,false,false,false,false,false));
        assertTrue(DirectorOverlayPolicy.bank(true,false,true,false,false,false,false));
        assertFalse(DirectorOverlayPolicy.bank(true,true,false,true,false,false,false));
        assertFalse(DirectorOverlayPolicy.bank(true,true,false,false,true,false,false));
        assertFalse(DirectorOverlayPolicy.bank(true,true,false,false,false,true,false));
        assertFalse(DirectorOverlayPolicy.bank(true,true,false,false,false,false,true));
    }
    @Test public void worldGuidanceStopsWhileInactiveOrPreparing()
    {
        assertTrue(DirectorOverlayPolicy.world(false,false,false,false,false));
        assertFalse(DirectorOverlayPolicy.world(true,false,false,false,false));
        assertFalse(DirectorOverlayPolicy.world(false,true,false,false,false));
        assertFalse(DirectorOverlayPolicy.world(false,false,true,false,false));
        assertFalse(DirectorOverlayPolicy.world(false,false,false,true,false));
        assertFalse(DirectorOverlayPolicy.world(false,false,false,false,true));
    }
}
