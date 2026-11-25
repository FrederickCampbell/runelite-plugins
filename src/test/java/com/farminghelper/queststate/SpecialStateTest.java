package com.farminghelper.queststate;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class SpecialStateTest
{
    @Test public void knownSpecialHealthChecksAndExpiredAnimaAreActionable()
    {
        assertEquals(CropState.UNCHECKED, state(PatchImplementation.SPIRIT_TREE, 44));
        assertEquals(CropState.GROWING, state(PatchImplementation.SPIRIT_TREE, 20));
        assertEquals(CropState.UNCHECKED, state(PatchImplementation.REDWOOD, 37));
        assertEquals(CropState.UNCHECKED, state(PatchImplementation.CELASTRUS, 13));
        assertEquals(CropState.UNCHECKED, state(PatchImplementation.CRYSTAL_TREE, 14));
        assertEquals(CropState.DEAD, state(PatchImplementation.ANIMA, 16));
        assertEquals(CropState.DEAD, state(PatchImplementation.ANIMA, 25));
        assertEquals(CropState.DEAD, state(PatchImplementation.ANIMA, 34));
        assertEquals(CropState.EMPTY, state(PatchImplementation.ANIMA, 0));
    }

    @Test public void matureCoralIsHarvestable()
    {
        assertEquals(CropState.HARVESTABLE,state(PatchImplementation.CORAL,8));
        assertEquals(CropState.HARVESTABLE,state(PatchImplementation.CORAL,19));
        assertEquals(CropState.HARVESTABLE,state(PatchImplementation.CORAL,30));
    }

    @Test public void kastoriFlowerIsNotATreeAndWeedsAreEmpty()
    {
        FarmingWorld world=new FarmingWorld();
        FarmingPatch flower=QuestFarmingStateReader.lookup("FLOWER","Kastori","");
        assertNotNull(flower);
        assertEquals(net.runelite.api.gameval.VarbitID.FARMING_TRANSMIT_C,flower.getVarbit());
        assertTrue(QuestFarmingStateReader.matchesLiveName("Flower Patch",true));
        assertFalse(QuestFarmingStateReader.matchesLiveName("Calquat tree",true));
        assertFalse(QuestFarmingStateReader.matchesLiveName("Fruit tree",true));
        assertEquals(CropState.EMPTY,state(PatchImplementation.FLOWER,0));
        assertEquals(CropState.HARVESTABLE,state(PatchImplementation.COMPOST,94));
        assertEquals(CropState.HARVESTABLE,state(PatchImplementation.BIG_COMPOST,99));
    }

    @Test public void flowerVerificationUsesTheLiveFootprintTransformVarbitAndWorldView()
    {
        net.runelite.api.Client client=org.mockito.Mockito.mock(net.runelite.api.Client.class);
        var player=org.mockito.Mockito.mock(net.runelite.api.Player.class);
        var view=org.mockito.Mockito.mock(net.runelite.api.WorldView.class);
        var object=org.mockito.Mockito.mock(net.runelite.api.GameObject.class);
        var base=org.mockito.Mockito.mock(net.runelite.api.ObjectComposition.class);
        var actual=org.mockito.Mockito.mock(net.runelite.api.ObjectComposition.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldView()).thenReturn(view);
        when(object.getWorldView()).thenReturn(view);
        when(client.getObjectDefinition(0)).thenReturn(base);
        when(base.getImpostorIds()).thenReturn(new int[]{1});
        when(base.getImpostor()).thenReturn(actual);
        when(object.getSceneMinLocation()).thenReturn(new net.runelite.api.Point(10,10));
        when(object.sizeX()).thenReturn(3);
        FarmingPatch patch=QuestFarmingStateReader.lookup("FLOWER","Kastori","");
        when(base.getVarbitId()).thenReturn(patch.getVarbit());
        when(actual.getName()).thenReturn("Flower patch");
        var marker=new net.runelite.api.coords.WorldPoint(18,10,0);
        var reader=new QuestFarmingStateReader(client,org.mockito.Mockito.mock(net.runelite.client.config.ConfigManager.class));
        java.util.Set<net.runelite.api.GameObject> scene=new java.util.HashSet<>();
        reader.useSceneObjects(scene);scene.add(object);
        assertTrue(reader.confirmedObject(patch,marker,true));
        assertFalse(reader.confirmedObject(patch,new net.runelite.api.coords.WorldPoint(19,10,0),true));
        when(actual.getName()).thenReturn("Calquat tree");
        assertFalse(reader.confirmedObject(patch,marker,true));
        when(actual.getName()).thenReturn("Flowers");
        when(base.getVarbitId()).thenReturn(-1);
        assertFalse(reader.confirmedObject(patch,marker,true));
        when(base.getVarbitId()).thenReturn(patch.getVarbit());
        when(object.getWorldView()).thenReturn(null);
        assertFalse(reader.confirmedObject(patch,marker,true));
        when(object.getWorldView()).thenReturn(view);scene.clear();
        assertFalse(reader.confirmedObject(patch,marker,true));
        org.mockito.Mockito.verify(client,org.mockito.Mockito.never()).getScene();
    }

    private CropState state(PatchImplementation type, int value)
    {
        return QuestFarmingStateReader.normalizedState(type, value, type.forVarbitValue(value));
    }
}
