package com.farminghelper;

import com.farminghelper.queststate.CropState;
import java.util.*;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class FarmingGuideTest
{
    private static List<FarmRunPatch> herbs()
    {
        return FarmRunCatalog.patches().stream().filter(p -> p.getPatchType() == PatchType.HERB)
            .collect(Collectors.toList());
    }

    @Test public void matchesUpstreamHerbStepsAndCoordinates()
    {
        List<FarmRunPatch> patches = herbs();
        int[][] tiles = {{3058,3311},{2670,3374},{2813,3463},{3605,3529},{1738,3550},
            {2826,3694},{2848,3934},{1238,3726},{3789,2837},{1582,3094}};
        assertEquals(tiles.length,patches.size());
        for (int i=0; i<tiles.length; i++)
            assertEquals(new WorldPoint(tiles[i][0],tiles[i][1],0),Waypoints.exactPoint(patches.get(i)));
    }

    @Test public void emptyStockCannotRemoveAnUnknownOrReadyHerbPatch()
    {
        List<FarmRunPatch> patches = herbs();
        assertEquals(10,SeedChoices.allocate(patches,99,Map.of(),t -> "").size());
        assertEquals(10,patches.size());
        assertTrue(FarmingHelperPlugin.actionable(PatchType.HERB,null));
        for (CropState state : CropState.values())
            assertEquals(EnumSet.of(CropState.EMPTY,CropState.DEAD,CropState.UNCHECKED,CropState.HARVESTABLE,CropState.DISEASED,CropState.STUMP)
                .contains(state),FarmingHelperPlugin.actionable(PatchType.HERB,state));
    }

    @Test public void appliesRankingInGuideOrderThenUsesAutomaticFallback()
    {
        List<FarmRunPatch> patches = herbs().subList(0,4);
        Crop ranarr = CropCatalog.findByName(PatchType.HERB,"Ranarr");
        Crop snap = CropCatalog.findByName(PatchType.HERB,"Snapdragon");
        Crop irit = CropCatalog.findByName(PatchType.HERB,"Irit");
        Map<Integer,Integer> stock = Map.of(ranarr.getItemId(),1,snap.getItemId(),1,irit.getItemId(),2);
        Map<FarmRunPatch,Crop> plan = SeedChoices.allocate(patches,65,stock,
            t -> "Ranarr, Irit");
        assertEquals(List.of(ranarr,irit,irit,snap),patches.stream().map(plan::get).collect(Collectors.toList()));
    }

    @Test public void respectsLevelAndCompletePlantingQuantities()
    {
        FarmRunPatch patch = new FarmRunPatch("Falador","North",PatchType.ALLOTMENT);
        Crop high = CropCatalog.findByName(PatchType.ALLOTMENT,"Snape grass");
        Crop poor = CropCatalog.findByName(PatchType.ALLOTMENT,"Sweetcorn");
        Crop ok = CropCatalog.findByName(PatchType.ALLOTMENT,"Potato");
        Map<Integer,Integer> stock = Map.of(high.getItemId(),99,poor.getItemId(),2,ok.getItemId(),3);
        assertEquals(ok,SeedChoices.allocate(List.of(patch),30,stock,t -> "Sweetcorn").get(patch));
    }
}
