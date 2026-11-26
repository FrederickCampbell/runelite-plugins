package com.farminghelper;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
public class SeaweedBirdTransferTest
{
    @Test public void islandBankAndBoat()
    {
        WorldPoint island=SpecialRoutes.SEAWEED_ISLAND;
        assertEquals(RouteLeg.Interaction.BIRD_BANK,SpecialRoutes.birdTransfer(island,true,false,75).interaction);
        assertEquals(RouteLeg.Interaction.BIRD_ROW,SpecialRoutes.birdTransfer(island,false,false,75).interaction);
        assertEquals(RouteLeg.Interaction.NONE,SpecialRoutes.birdTransfer(island,false,true,75).interaction);
    }
    @Test public void teleportUsesOrdinaryBankThenResumes()
    {
        WorldPoint lumbridge=new WorldPoint(3222,3218,0);
        assertNotNull(SpecialRoutes.birdTransfer(lumbridge,true,false,75).point);
        assertNull(SpecialRoutes.birdTransfer(lumbridge,false,false,75));
        assertNull(SpecialRoutes.birdTransfer(new WorldPoint(3720,3800,0),false,false,75));
    }
}
