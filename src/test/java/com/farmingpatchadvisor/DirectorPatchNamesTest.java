package com.farmingpatchadvisor;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorPatchNamesTest
{
    @Test
    public void independentGrapevinesAndSeaweedKeepQuestHelperNames()
    {
        FarmRunPatch grapes = new FarmRunPatch("Kourend", "Grapevines",
            PatchType.GRAPEVINE, 2);
        assertEquals("East 1", DirectorPatchNames.expand(grapes,
            Arrays.asList("East 1", "East 2")).get(0).getName());
        assertEquals(2, DirectorPatchNames.expand(grapes,
            Arrays.asList("East 1", "East 2")).size());
        FarmRunPatch seaweed = new FarmRunPatch("Seaweed", "Seaweed patches",
            PatchType.SEAWEED, 2);
        assertEquals("North", DirectorPatchNames.expand(seaweed,
            Arrays.asList("North", "South")).get(0).getName());
    }

    @Test
    public void ambiguityDoesNotSilentlyMergeTwoPatches()
    {
        FarmRunPatch ambiguous = new FarmRunPatch("Kourend", "Grapevines",
            PatchType.GRAPEVINE, 12);
        assertTrue(DirectorPatchNames.expand(ambiguous,
            Arrays.asList("East 1", "East 2")).isEmpty());
        assertEquals(1, DirectorPatchNames.expand(
            new FarmRunPatch("Ardougne", "", PatchType.HERB),
            Collections.emptyList()).size());
    }
}
