package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class Guide17Test
{
    private static FarmRunPatch p(String site,PatchType type){return new FarmRunPatch(site,"",type);}
    @Test public void manualSelectionSeparatesSpecialsAndRespectsPermanentExclusion()
    {
        FarmRunPatch herb=p("Falador",PatchType.HERB),mushroom=p("Morytania",PatchType.MUSHROOM),grapes=p("Kourend",PatchType.GRAPEVINE),flower=p("Falador",PatchType.FLOWER);
        List<FarmRunPatch> patches=List.of(herb,mushroom,grapes,flower);
        Set<String> keys=new LinkedHashSet<>();
        ManualSelection.select(keys,patches,x->x!=grapes,true);
        ManualSelection.select(keys,List.of(flower),x->true,false);
        assertEquals(Set.of(herb.key(),mushroom.key()),keys);
        assertEquals(keys,ManualSelection.decode(ManualSelection.encode(keys)+"\nobsolete",patches));
        assertTrue(ManualSelection.decode("",patches).isEmpty());
        assertTrue(ManualSelection.includes(mushroom,EnumSet.of(FarmRunType.SPECIALTY),keys));
        assertFalse(ManualSelection.includes(grapes,EnumSet.of(FarmRunType.SPECIALTY),keys));
        assertTrue(ManualSelection.includes(grapes,EnumSet.of(FarmRunType.SPECIALTY),null));
    }
    @Test public void groupsAcrossTypesAndPreservesPhysicalPatchIdentities()
    {
        FarmRunPatch herb=p("Farming Guild",PatchType.HERB),hes=p("Farming Guild",PatchType.HESPORI),tree=p("Farming Guild",PatchType.TREE),
            sea=p("Seaweed",PatchType.SEAWEED),hard=p("Fossil Island",PatchType.HARDWOOD_TREE),
            falHerb=p("Falador",PatchType.HERB),falTree=p("Falador",PatchType.TREE),
            grapes=p("Kourend",PatchType.GRAPEVINE),hos=p("Kourend",PatchType.HERB);
        List<FarmRunPatch> in=List.of(hes,herb,sea,falHerb,hard,tree,falTree,grapes,hos);
        List<RunOrder.Group> groups=RunOrder.shared(in,null,true,java.util.Set.of());
        assertEquals(6,groups.size());
        assertEquals(List.of(tree,herb,hes),groups.get(0).patches);
        assertEquals(List.of(hard,sea),groups.get(groups.size()-1).patches);
        List<FarmRunPatch> out=RunOrder.flatten(groups);
        assertEquals(in.size(),out.size());assertEquals(new HashSet<>(in),new HashSet<>(out));
        assertEquals(out.indexOf(tree)+1,out.indexOf(herb));assertEquals(out.indexOf(herb)+1,out.indexOf(hes));
        assertNotEquals(RunOrder.site(falHerb),RunOrder.site(falTree));assertNotEquals(RunOrder.site(grapes),RunOrder.site(hos));
    }
}
