package com.farminghelper;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocationOrderTest
{
    @Test public void stableGroupingKeepsAllPhysicalPatches()
    {
        FarmRunPatch a = new FarmRunPatch("Catherby", "", PatchType.HERB);
        FarmRunPatch b = new FarmRunPatch("Falador", "", PatchType.HERB);
        FarmRunPatch c = new FarmRunPatch("Catherby", "", PatchType.FRUIT_TREE);
        FarmRunPatch d = new FarmRunPatch("Catherby", "North", PatchType.ALLOTMENT);
        assertEquals(Arrays.asList(a,c,d,b), LocationOrder.group(Arrays.asList(a,b,c,d)));
    }
}
