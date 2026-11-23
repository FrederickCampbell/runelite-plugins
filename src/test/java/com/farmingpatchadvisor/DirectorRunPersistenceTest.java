package com.farmingpatchadvisor;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorRunPersistenceTest
{
    @Test
    public void completedPatchesAreReproducibleAndProfileSafe()
    {
        java.util.Set<String> keys = new HashSet<>(Arrays.asList(
            "Falador~HERB~", "Ardougne~ALLOTMENT~North"));
        String stored = DirectorRunPersistence.encode(keys);
        assertEquals(keys, DirectorRunPersistence.decode(stored));
        assertEquals(stored, DirectorRunPersistence.encode(
            DirectorRunPersistence.decode(stored)));
    }

    @Test
    public void ignoresCorruptCheckpointEntries()
    {
        assertTrue(DirectorRunPersistence.decode(null).isEmpty());
        assertTrue(DirectorRunPersistence.decode("garbage").isEmpty());
        assertEquals(1, DirectorRunPersistence.decode(
            "bad;Falador~HERB~").size());
    }
}
