package com.farminghelper;
import net.runelite.api.coords.WorldPoint;
import java.util.*;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;
import net.runelite.api.gameval.ItemID;
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
    @Test public void exactRopeAndDialogue()
    {
        WorldPoint underwater=new WorldPoint(3739,10276,1);
        RouteLeg exit=SpecialRoutes.birdTransfer(underwater,true,false,75);
        assertEquals(new WorldPoint(3731,10282,1),exit.point);
        assertEquals(RouteLeg.Interaction.SURFACE,exit.interaction);
        assertEquals(30948,SpecialRoutes.SEAWEED_ROPE_ID);
        assertNull(SpecialRoutes.seaweedExit(true).point);
        assertTrue(SpecialRoutes.onSeaweedIsland(new WorldPoint(3767,3899,0)));
        assertFalse(SpecialRoutes.onSeaweedIsland(new WorldPoint(3734,3893,0)));
        assertTrue(PatchHighlight.matchesRowChoice("Row to the camp."));
        assertTrue(PatchHighlight.matchesRowChoice("[1] <col=ffff00>Row to the camp.</col>"));
        assertFalse(PatchHighlight.matchesRowChoice("Row to the island."));
        assertEquals(RouteLeg.Interaction.NONE,SpecialRoutes.birdTransfer(SpecialRoutes.SEAWEED_ISLAND,true,true,75).interaction);
    }
    @Test public void sufficientCarriedKitBeatsBankTier()
    {
        List<FarmRunPatch> birds=BirdhouseRun.patches();
        Map<FarmRunPatch,Observation> states=new HashMap<>();
        for(FarmRunPatch p:birds)states.put(p,new Observation(CropState.HARVESTABLE,Source.LIVE,1,null,p.getName()));
        Map<Integer,Integer> bag=Map.of(1515,4,2347,1,1755,1,ItemID.POTATO_SEED,40);
        Map<Integer,Integer> stock=new HashMap<>(bag);stock.put(1513,4);
        assertSame(bag,BirdhouseRun.preferCarried(birds,states,bag,stock,Map.of(),78,80));
        assertSame(stock,BirdhouseRun.preferCarried(birds,states,Map.of(),stock,Map.of(),78,80));
    }
    @Test public void teleportUsesOrdinaryBankThenResumes()
    {
        WorldPoint lumbridge=new WorldPoint(3222,3218,0);
        assertNotNull(SpecialRoutes.birdTransfer(lumbridge,true,false,75).point);
        assertNull(SpecialRoutes.birdTransfer(lumbridge,false,false,75));
        assertNull(SpecialRoutes.birdTransfer(new WorldPoint(3720,3800,0),false,false,75));
    }
}
