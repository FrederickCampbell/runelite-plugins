package com.farminghelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PatchAndMergedTest
{
    @Test public void individualExclusionWinsForBothNormalAndMergedRuns()
    {
        FarmRunPatch troll = new FarmRunPatch("Troll Stronghold", "", PatchType.HERB);
        FarmRunPatch falador = new FarmRunPatch("Falador", "", PatchType.HERB);
        FarmRunPatch tree = new FarmRunPatch("Falador", "", PatchType.TREE);
        List<FarmRunPatch> all = Arrays.asList(troll, falador, tree);
        List<FarmRunPatch> enabled = PatchPreferences.included(all, p -> p != troll);
        List<FarmRunPatch> herbs = MergedPresets.selected(enabled, EnumSet.of(FarmRunType.HERB));
        List<FarmRunPatch> merged = MergedPresets.selected(enabled,
            EnumSet.of(FarmRunType.HERB, FarmRunType.TREE));
        assertEquals(Collections.singletonList(falador), herbs);
        assertEquals(Arrays.asList(falador, tree), merged);
    }

    @Test public void individualAllotmentsCanBeDisabledIndependently()
    {
        FarmRunPatch group = new FarmRunPatch("Falador", "Allotments", PatchType.ALLOTMENT, 2);
        List<FarmRunPatch> expanded = PatchPreferences.expand(
            Collections.singletonList(group), p -> Arrays.asList("Allotment 1", "Allotment 2"));
        assertEquals(2, expanded.size());
        assertEquals(1, PatchPreferences.included(expanded,
            p -> !p.getName().equals("Allotment 1")).size());
        assertNotEquals(FarmRouteManager.key(expanded.get(0)), FarmRouteManager.key(expanded.get(1)));
    }

    @Test public void unresolvedPatchNamesAreNotInvented()
    {
        FarmRunPatch group = new FarmRunPatch("Kourend", "Grapevines", PatchType.GRAPEVINE, 12);
        assertEquals(Collections.singletonList(group), PatchPreferences.expand(
            Collections.singletonList(group), p -> Collections.singletonList("unmatched")));
    }

    @Test public void mergedPresetsRoundTripAndTolerateOldCategories()
    {
        EnumSet<FarmRunType> selected = EnumSet.of(FarmRunType.HERB,
            FarmRunType.FRUIT_TREE_CALQUAT, FarmRunType.HARDWOOD_TREE);
        assertEquals(selected, MergedPresets.decode(MergedPresets.encode(selected)));
        assertEquals(EnumSet.of(FarmRunType.HERB),
            MergedPresets.decode("HERB,REMOVED_CATEGORY"));
        assertFalse(MergedPresets.validName("bad;name"));
    }
}
