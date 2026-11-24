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
        List<RunOrder.Group> groups=RunOrder.groups(in,null);
        assertEquals(6,groups.size());
        assertEquals(List.of(herb,tree,hes),groups.get(0).patches);
        assertEquals(List.of(hard,sea),groups.get(1).patches);
        List<FarmRunPatch> out=RunOrder.flatten(RunOrder.plan(groups,new WorldPoint(1238,3726,0)));
        assertEquals(in.size(),out.size());assertEquals(new HashSet<>(in),new HashSet<>(out));
        assertEquals(out.indexOf(herb)+1,out.indexOf(tree));assertEquals(out.indexOf(tree)+1,out.indexOf(hes));
        assertNotEquals(RunOrder.site(falHerb),RunOrder.site(falTree));assertNotEquals(RunOrder.site(grapes),RunOrder.site(hos));
    }
    @Test public void exactOptimizerUsesDirectedCostsNotNearestOnly()
    {
        double[] first={1,2,3};double[][] legs={{0,100,1},{1,0,100},{100,1,0}};
        int[] order=RunOrder.optimize(first,legs);assertArrayEquals(new int[]{0,2,1},order);
        assertEquals(3,RunOrder.cost(order,first,legs),0.001);
    }
    @Test public void largeOptimizerReturnsEveryStopExactlyOnceAndIsDeterministic()
    {
        int n=36;double[] first=new double[n];double[][] legs=new double[n][n];
        Random rng=new Random(17);for(int i=0;i<n;i++){first[i]=rng.nextInt(100);for(int j=0;j<n;j++)legs[i][j]=rng.nextInt(100);}
        int[] order=RunOrder.optimize(first,legs);assertEquals(n,Arrays.stream(order).distinct().count());
        assertArrayEquals(order,RunOrder.optimize(first,legs));assertTrue(Double.isFinite(RunOrder.cost(order,first,legs)));
    }
    @Test public void nativeRefinementNeverMovesActiveClusterOrSpecialtyEntrances()
    {
        RunOrder.Group first=new RunOrder.Group("Falador",List.of(p("Falador",PatchType.HERB)));
        RunOrder.Group second=new RunOrder.Group("Catherby",List.of(p("Catherby",PatchType.HERB)));
        RunOrder.Group third=new RunOrder.Group("Ardougne",List.of(p("Ardougne",PatchType.HERB)));
        RunOrder.Group sea=new RunOrder.Group("Fossil",List.of(p("Seaweed",PatchType.SEAWEED)));
        List<RunOrder.Group> order=List.of(first,second,third,sea);
        assertEquals(List.of(second.entry(),third.entry()),RunOrder.candidates(order));
        List<RunOrder.Group> refined=RunOrder.preferNext(order,third.entry());
        assertSame(first,refined.get(0));assertSame(third,refined.get(1));assertEquals(new HashSet<>(order),new HashSet<>(refined));
        assertSame(order,RunOrder.preferNext(order,sea.entry()));
    }
}
