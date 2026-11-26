package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RefactorParityTest
{
    @Test public void wateringCanPrefersChargesThenLowestPhysicalId()
    {
        Map<Integer,Integer> bag = new LinkedHashMap<>();
        bag.put(99, 1);
        bag.put(66, 1);
        bag.put(77, 1);
        bag.put(33, 0);
        Map<Integer,String> names = Map.of(99, "WATERING CAN(8)", 66, "Watering can  (8)",
            77, "Watering can(7)", 33, "Gricoller's can");
        assertEquals(66, CropCare.wateringCan(bag, names::get));
        bag.put(33, 1);
        assertEquals(33, CropCare.wateringCan(bag, names::get));
        assertEquals(-1, CropCare.wateringCan(bag, id -> "Watering can (0)"));
    }

    @Test public void regionMembershipChangesWithinOneMapSquareAndBetweenFloors()
    {
        QuestFarmingStateReader reader = new QuestFarmingStateReader(null, null);
        for (WorldPoint point : List.of(new WorldPoint(2839,3440,0), new WorldPoint(2840,3440,0),
            new WorldPoint(2839,3440,1), new WorldPoint(2839,3440,0)))
        {
            boolean farm = point.getX() == 2839 && point.getPlane() == 0;
            assertEquals(farm, reader.inPatchRegion("HERB", "Catherby", "", point));
            assertEquals(!farm, reader.inPatchRegion("FRUIT_TREE", "Catherby", "", point));
        }
    }

    @Test public void remoteTimestampParserPreservesExactFormatAndOverflowRules()
    {
        for (String invalid : List.of("", "4:3:", "4::3", " 4:3", "-1:3", "4:-1", "4:0",
            "2147483648:4", "0:9223372036854776"))
            assertEquals(invalid, 0L, QuestFarmingStateReader.timestampMillis(invalid));
        assertEquals(4000L, QuestFarmingStateReader.timestampMillis("+2:+4"));
        assertEquals(9223372036854775000L, QuestFarmingStateReader.timestampMillis("0:9223372036854775"));
    }

    @Test public void nonBirdWaveMakesNoChangesToExistingFarmingKit()
    {
        FarmRunPatch herb = new FarmRunPatch("Catherby", "", PatchType.HERB);
        Map<Integer,Integer> kit = new LinkedHashMap<>(Map.of(ItemID.DIBBER, 1));
        assertTrue(BirdhouseRun.supplies(List.of(herb), Map.of(), Map.of(ItemID.POTATO_SEED, 100), kit, 80, 80).isEmpty());
        assertEquals(Map.of(ItemID.DIBBER, 1), kit);
    }

    @Test public void transferOnlyCountsTheSelectedSeedAndKeepsPublishedSnapshotsStable()
    {
        int seed = ItemID.RANARR_SEED, other = ItemID.SNAPDRAGON_SEED;
        Map<Integer,Integer> bag = Map.of(ItemID.SEED_BOX, 1);
        SeedBoxStock box = new SeedBoxStock();
        box.chat("The seed box contains:", 1, name -> seed);
        box.chat("7 x Ranarr seed", 1, name -> seed);
        assertTrue(box.finishCheck(3));
        Map<Integer,Integer> original = box.carried(bag);
        box.begin(true, seed, Map.of(seed, 8, other, 6), 5);
        assertFalse(box.settle(Map.of(seed, 8), 6, id -> true));
        assertTrue(box.settle(Map.of(seed, 5), 7, id -> true));
        assertEquals(Map.of(seed, 10), box.carried(bag));
        assertEquals(Map.of(seed, 7), original);
    }
}
