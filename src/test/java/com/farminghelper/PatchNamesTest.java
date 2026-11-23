package com.farminghelper;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class PatchNamesTest
{
    @Test
    public void independentGrapevinesAndSeaweedKeepQuestHelperNames()
    {
        FarmRunPatch grapes = new FarmRunPatch("Kourend", "Grapevines",
            PatchType.GRAPEVINE, 2);
        assertEquals("East 1", PatchNames.expand(grapes,
            Arrays.asList("East 1", "East 2")).get(0).getName());
        assertEquals(2, PatchNames.expand(grapes,
            Arrays.asList("East 1", "East 2")).size());
        FarmRunPatch seaweed = new FarmRunPatch("Seaweed", "Seaweed patches",
            PatchType.SEAWEED, 2);
        assertEquals("North", PatchNames.expand(seaweed,
            Arrays.asList("North", "South")).get(0).getName());
    }

    @Test
    public void ambiguityDoesNotSilentlyMergeTwoPatches()
    {
        FarmRunPatch ambiguous = new FarmRunPatch("Kourend", "Grapevines",
            PatchType.GRAPEVINE, 12);
        assertEquals(2, PatchNames.expand(ambiguous,
            Arrays.asList("East 1", "East 2")).size());
        assertTrue(PatchNames.expand(ambiguous, Collections.emptyList()).isEmpty());
        assertEquals(1, PatchNames.expand(
            new FarmRunPatch("Ardougne", "", PatchType.HERB),
            Collections.emptyList()).size());
    }
}
