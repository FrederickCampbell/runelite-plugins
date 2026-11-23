package com.farmingpatchadvisor;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorBankStopsTest
{
    @Test
    public void picksNearestListedBankButManualChoiceWins()
    {
        assertEquals(DirectorBankStops.Bank.CATHERBY,
            DirectorBankStops.choose(DirectorBankStops.Bank.AUTO,
                new WorldPoint(2813, 3463, 0)));
        assertEquals(DirectorBankStops.Bank.VARROCK_WEST,
            DirectorBankStops.choose(DirectorBankStops.Bank.VARROCK_WEST,
                new WorldPoint(2813, 3463, 0)));
    }

    @Test
    public void alwaysReturnsKnownBankRatherThanInventingDestination()
    {
        assertNotNull(DirectorBankStops.choose(null, null));
    }
}
