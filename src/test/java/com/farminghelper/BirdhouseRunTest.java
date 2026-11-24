package com.farminghelper;
import com.farminghelper.queststate.CropState;
import org.junit.Test;
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
}
