package com.farminghelper;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class BankStopsTest
{
    @Test
    public void picksNearestListedBankButManualChoiceWins()
    {
        assertEquals(BankStops.Bank.CATHERBY,
            BankStops.choose(BankStops.Bank.AUTO,
                new WorldPoint(2813, 3463, 0)));
        assertEquals(BankStops.Bank.VARROCK_WEST,
            BankStops.choose(BankStops.Bank.VARROCK_WEST,
                new WorldPoint(2813, 3463, 0)));
    }

    @Test
    public void farmingGuildBankOnlyRecommendedToEligibleAccounts()
    {
        WorldPoint outsideGuild = new WorldPoint(1250, 3720, 0);
        assertEquals(BankStops.Bank.FARMING_GUILD,
            BankStops.choose(BankStops.Bank.AUTO, outsideGuild, 45));
        assertNotEquals(BankStops.Bank.FARMING_GUILD,
            BankStops.choose(BankStops.Bank.AUTO, outsideGuild, 44));
    }

    @Test
    public void alwaysReturnsKnownBankRatherThanInventingDestination()
    {
        assertNotNull(BankStops.choose(null, null));
    }
}
