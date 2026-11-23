package com.farminghelper;
import org.junit.Test;
import static org.junit.Assert.*;
public class OverlayPolicyTest
{
    @Test public void onlyPreparingOrRestockingShowsBankItems()
    {
        assertFalse(OverlayPolicy.bank(false,true,false,false,false,false,false));
        assertFalse(OverlayPolicy.bank(true,false,false,false,false,false,false));
        assertTrue(OverlayPolicy.bank(true,true,false,false,false,false,false));
        assertTrue(OverlayPolicy.bank(true,false,true,false,false,false,false));
        assertFalse(OverlayPolicy.bank(true,true,false,true,false,false,false));
        assertFalse(OverlayPolicy.bank(true,true,false,false,true,false,false));
        assertFalse(OverlayPolicy.bank(true,true,false,false,false,true,false));
        assertFalse(OverlayPolicy.bank(true,true,false,false,false,false,true));
    }
    @Test public void worldGuidanceStopsWhileInactiveOrPreparing()
    {
        assertTrue(OverlayPolicy.world(false,false,false,false,false));
        assertFalse(OverlayPolicy.world(true,false,false,false,false));
        assertFalse(OverlayPolicy.world(false,true,false,false,false));
        assertFalse(OverlayPolicy.world(false,false,true,false,false));
        assertFalse(OverlayPolicy.world(false,false,false,true,false));
        assertFalse(OverlayPolicy.world(false,false,false,false,true));
    }
}
