package com.farminghelper;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;

public class RefactorContinuationTest
{
    @Test public void snapshotDecodingRetainsLegacyMalformedEntrySemantics()
    {
        assertEquals(Map.of(1,5,3,8,4,9),StockSnapshot.decode("1:2,,bad,1:5,2:0,3:+8,4:9,5:1:2,6:,7:2147483648, 8:1,-9:1,"));
        assertEquals(Map.of(),StockSnapshot.decode(null));
    }
    @Test public void supplyOrderIsStableAndClassifiesEachItemOnce()
    {
        Map<Integer,Integer> items=new LinkedHashMap<>();
        for(int id=10000;id<10128;id++)items.put(id,1);
        String[] names={"Watering can(7)","Oak seed","Plant cure","Coins"};
        List<Integer> reference=new ArrayList<>(items.keySet());
        reference.sort(Comparator.comparingInt(id->SupplyOrder.rank(id,i->names[i%4])));
        int[] calls={0};
        assertEquals(reference,SupplyOrder.ordered(items,id->{calls[0]++;return names[id%4];}));
        assertEquals(items.size(),calls[0]);
    }
    @Test public void guideKitExcludesCareOnlyWithoutChangingOrder()
    {
        FarmRunPatch herb=new FarmRunPatch("Catherby","",PatchType.HERB);
        FarmRunPatch bird=new FarmRunPatch("Fossil Island","",PatchType.BIRDHOUSE);
        FarmRunPatch tree=new FarmRunPatch("Varrock","",PatchType.TREE);
        for(boolean weeds:new boolean[]{false,true})for(boolean barbarian:new boolean[]{false,true})
        {
            var mixed=List.of(bird,herb,tree);
            assertEquals(Supplies.guideKit(List.of(bird,tree),weeds,barbarian),Supplies.guideKit(mixed,Set.of(herb),weeds,barbarian));
            assertEquals(Map.of(),Supplies.guideKit(mixed,Set.of(herb,tree),weeds,barbarian));
        }
    }
    @Test public void wateringRangesMatchEveryLegacyRawValue()
    {
        int[][][] dry={{{6,9},{13,16},{20,23},{27,30},{34,39},{43,48},{52,59},{63,69}},{{8,11},{13,16},{18,21},{23,26},{28,31},{37,40}},{{4,7},{14,18},{26,31},{40,46},{56,63},{74,77},{84,88},{96,98},{104,107},{114,118}}};
        int[][][] wet={{{70,73},{77,80},{84,87},{91,94},{98,103},{107,112},{116,123},{128,134}},{{72,75},{77,80},{82,85},{87,90},{92,95},{101,104}},{{132,135},{142,146},{154,159},{168,174},{184,191},{202,205},{212,216},{224,226},{232,235},{242,246}}};
        PatchType[] types={PatchType.ALLOTMENT,PatchType.FLOWER,PatchType.HOPS};
        for(int n=0;n<types.length;n++)for(int raw=-1;raw<=256;raw++)
        {
            Observation o=new Observation(CropState.GROWING,Source.LIVE,1,null,"",raw);
            assertEquals(types[n]+" dry "+raw,in(dry[n],raw),CropCare.waterNeeded(types[n],o,true));
            assertEquals(types[n]+" wet "+raw,in(wet[n],raw),CropCare.watered(types[n],o));
        }
    }
    private static boolean in(int[][] ranges,int raw)
    {for(int[] range:ranges)if(raw>=range[0]&&raw<=range[1])return true;return false;}
}
