package com.farmingpatchadvisor;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectorPatchAndMergedTest
{
    @Test public void individualExclusionWinsForBothNormalAndMergedRuns()
    {
        FarmRunPatch troll = new FarmRunPatch("Troll Stronghold", "", PatchType.HERB);
        FarmRunPatch falador = new FarmRunPatch("Falador", "", PatchType.HERB);
        FarmRunPatch tree = new FarmRunPatch("Falador", "", PatchType.TREE);
        List<FarmRunPatch> all = Arrays.asList(troll, falador, tree);
        List<FarmRunPatch> enabled = DirectorPatchPreferences.included(all, p -> p != troll);
        List<FarmRunPatch> herbs = DirectorMergedPresets.selected(enabled, EnumSet.of(FarmRunType.HERB));
        List<FarmRunPatch> merged = DirectorMergedPresets.selected(enabled,
            EnumSet.of(FarmRunType.HERB, FarmRunType.TREE));
        assertEquals(Collections.singletonList(falador), herbs);
        assertEquals(Arrays.asList(falador, tree), merged);
    }

    @Test public void individualAllotmentsCanBeDisabledIndependently()
    {
        FarmRunPatch group = new FarmRunPatch("Falador", "Allotments", PatchType.ALLOTMENT, 2);
        List<FarmRunPatch> expanded = DirectorPatchPreferences.expand(
            Collections.singletonList(group), p -> Arrays.asList("Allotment 1", "Allotment 2"));
        assertEquals(2, expanded.size());
        assertEquals(1, DirectorPatchPreferences.included(expanded,
            p -> !p.getName().equals("Allotment 1")).size());
        assertNotEquals(FarmRouteManager.key(expanded.get(0)), FarmRouteManager.key(expanded.get(1)));
    }

    @Test public void unresolvedPatchNamesAreNotInvented()
    {
        FarmRunPatch group = new FarmRunPatch("Kourend", "Grapevines", PatchType.GRAPEVINE, 12);
        assertEquals(Collections.singletonList(group), DirectorPatchPreferences.expand(
            Collections.singletonList(group), p -> Collections.singletonList("unmatched")));
    }

    @Test public void mergedPresetsRoundTripAndTolerateOldCategories()
    {
        EnumSet<FarmRunType> selected = EnumSet.of(FarmRunType.HERB,
            FarmRunType.FRUIT_TREE_CALQUAT, FarmRunType.HARDWOOD_TREE);
        assertEquals(selected, DirectorMergedPresets.decode(DirectorMergedPresets.encode(selected)));
        assertEquals(EnumSet.of(FarmRunType.HERB),
            DirectorMergedPresets.decode("HERB,REMOVED_CATEGORY"));
        assertFalse(DirectorMergedPresets.validName("bad;name"));
    }
}
