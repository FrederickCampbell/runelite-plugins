package com.farminghelper;

import java.util.*;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class RunPresetTest
{
    private static FarmRunPatch p(String site,PatchType type){return new FarmRunPatch(site,"",type);}
    @Test public void groupedPresetsAreOnlySelections()
    {
        assertEquals(EnumSet.of(PatchType.TREE,PatchType.FRUIT_TREE,PatchType.HARDWOOD_TREE,PatchType.CALQUAT,PatchType.CELASTRUS,PatchType.CRYSTAL_TREE,PatchType.REDWOOD),RunPreset.TREE.types);
        assertEquals(EnumSet.of(PatchType.HERB,PatchType.ALLOTMENT,PatchType.FLOWER,PatchType.COMPOST_BIN),RunPreset.CROP.types);
        assertEquals(EnumSet.of(PatchType.HOPS,PatchType.BUSH),RunPreset.HOPS_BUSH.types);
        assertEquals(EnumSet.of(PatchType.CACTUS,PatchType.BELLADONNA,PatchType.MUSHROOM,PatchType.SEAWEED,PatchType.CORAL,PatchType.GRAPEVINE),RunPreset.SPECIALTY.types);
        assertFalse(RunPreset.TREE.types.contains(PatchType.SPIRIT_TREE));
        List<FarmRunPatch> patches=List.of(p("Falador",PatchType.HERB),p("Falador",PatchType.FLOWER),p("Kourend",PatchType.GRAPEVINE),p("Morytania",PatchType.MUSHROOM));
        assertEquals(Set.of(patches.get(0).key(),patches.get(1).key()),RunPreset.CROP.select(patches,(k,d)->d));
        assertEquals(Set.of(patches.get(3).key()),RunPreset.SPECIALTY.select(patches,(k,d)->d));
        assertEquals(Set.of(patches.get(1).key()),RunPreset.CROP.select(patches,(k,d)->k.equals(RunPreset.CROP.key(PatchType.FLOWER))));
        assertEquals(Set.of(patches.get(0).key()),RunPreset.CROP.select(patches,(k,d)->d&&!k.equals(RunPreset.CROP.key(PatchType.FLOWER))));
    }
    @Test public void guildFirstAndSharedSitesStayTogether()
    {
        FarmRunPatch guildTree=p("Farming Guild",PatchType.TREE),guildFruit=p("Farming Guild",PatchType.FRUIT_TREE),
            gnomeTree=p("Gnome Stronghold",PatchType.TREE),gnomeFruit=p("Gnome Stronghold",PatchType.FRUIT_TREE),varrock=p("Varrock",PatchType.TREE);
        List<FarmRunPatch> input=List.of(varrock,gnomeTree,guildFruit,gnomeFruit,guildTree);
        for(boolean optimize:List.of(false,true))
        {
            List<RunOrder.Group> groups=RunOrder.preset(input,new WorldPoint(3229,3459,0),RunPreset.TREE.reference,optimize);
            assertEquals("Farming Guild",groups.get(0).site);
            assertEquals(2,groups.get(0).patches.size());
            assertEquals(3,groups.size());
            assertEquals(new HashSet<>(input),new HashSet<>(RunOrder.flatten(groups)));
            assertEquals(input.size(),RunOrder.flatten(groups).size());
        }
        List<RunOrder.Group> fixed=RunOrder.preset(input,null,RunPreset.TREE.reference,false);
        assertEquals(List.of("Farming Guild","Gnome Stronghold","Varrock"),fixed.stream().map(g->g.site).collect(Collectors.toList()));
    }
    @Test public void bankAndCapacityAreAdvisory()
    {
        assertEquals(Preparation.GUILD,Preparation.choose(45,new WorldPoint(3092,3243,0),true,false).point);
        assertEquals(Preparation.DRAYNOR,Preparation.choose(44,new WorldPoint(3092,3243,0),true,false).point);
        assertEquals(Preparation.DRAYNOR,Preparation.choose(99,new WorldPoint(3092,3243,0),false,true).point);
        assertTrue(Preparation.storedShortage(Map.of(1,2),Map.of(1,1),Map.of(1,10)));
        assertFalse(Preparation.storedShortage(Map.of(1,2),Map.of(1,2),Map.of(1,10)));
        assertFalse(Preparation.storedShortage(Map.of(1,2),Map.of(),Map.of()));
        assertEquals(31,Preparation.slots(Map.of(1,30,2,100,3,1),Map.of(3,1),id->id==2));
    }
    @Test public void preservePermanentTreesAndFibreCrops()
    {
        assertEquals(StepEngine.Action.COMPLETE,StepEngine.action(PatchType.SPIRIT_TREE,com.farminghelper.queststate.CropState.HARVESTABLE,false,null));
        for(String name:List.of("Flax","Hemp","Cotton"))assertNotNull(CropCatalog.findByName(PatchType.HOPS,name));
        for(PatchType type:PatchType.values())assertNotNull(RunPreset.individualTitle(type));
    }
}
