package com.farminghelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PatchNamesTest
{
    @Test public void preservesEveryV1PhysicalConfigKeyAndItsOrder() throws Exception
    {
        List<String> actual=new ArrayList<>();
        var reader=new com.farminghelper.queststate.QuestFarmingStateReader(null,null);
        List<FarmRunPatch> patches=new ArrayList<>(FarmRunCatalog.patches());
        patches.addAll(BirdhouseRun.patches());
        for(FarmRunPatch p:patches)
        {
            var point=Waypoints.destination(p);
            String type=p.getPatchType().name();
            actual.add(p.key()+"|"+point.getX()+","+point.getY()+","+point.getPlane()+"|"
                +reader.patchVarbit(type,p.getLocation(),p.getName())+"|"+reader.gardenerId(type,p.getLocation(),p.getName()));
        }
        assertEquals(Files.readAllLines(Path.of(getClass().getResource("/v1-patch-keys.txt").toURI())),actual);
    }
}
