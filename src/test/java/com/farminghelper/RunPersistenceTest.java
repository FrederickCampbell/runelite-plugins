package com.farminghelper;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class RunPersistenceTest
{
    @Test
    public void completedPatchesAreReproducibleAndProfileSafe()
    {
        java.util.Set<String> keys = new HashSet<>(Arrays.asList(
            "Falador~HERB~", "Ardougne~ALLOTMENT~North"));
        String stored = RunPersistence.encode(keys);
        assertEquals(keys, RunPersistence.decode(stored));
        assertEquals(stored, RunPersistence.encode(
            RunPersistence.decode(stored)));
    }

    @Test
    public void ignoresCorruptCheckpointEntries()
    {
        assertTrue(RunPersistence.decode(null).isEmpty());
        assertTrue(RunPersistence.decode("garbage").isEmpty());
        assertEquals(1, RunPersistence.decode(
            "bad;Falador~HERB~").size());
    }
}
