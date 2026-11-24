package com.farminghelper;

import java.util.*;
import javax.swing.JLabel;
import org.junit.Test;
import static org.junit.Assert.*;

public class SeedlessRunTest
{
    @Test public void scarceStockIsReservedOnlyOnceAndFallbackRemainsOptional()
    {
        FarmRunPatch a=new FarmRunPatch("A","",PatchType.ALLOTMENT);
        FarmRunPatch b=new FarmRunPatch("B","",PatchType.ALLOTMENT);
        Crop crop=CropCatalog.recommend(PatchType.ALLOTMENT,99);
        Map<Integer,Integer> stock=Map.of(crop.getItemId(),crop.getQuantity());
        assertEquals(Set.of(a),SeedChoices.allocate(List.of(a,b),99,stock,t->"",p->"",Map.of(),true).keySet());
        assertEquals(2,SeedChoices.allocate(List.of(a,b),99,stock,t->"",p->"",Map.of(),false).size());
        assertTrue(SeedChoices.allocate(List.of(a),99,Map.of(crop.getItemId(),crop.getQuantity()-1),t->"",p->"",Map.of(),true).isEmpty());
    }

    @Test public void satisfiedCountsUseIconAndShortagesRestoreNumbers()
    {
        JLabel label=new JLabel();
        FarmingHelperPanel.updateQuantity(label,new GuideView.ItemRow(1,"Coins",500,200,0,0,false));
        assertNotNull(label.getIcon());assertEquals("",label.getText());
        FarmingHelperPanel.updateQuantity(label,new GuideView.ItemRow(1,"Coins",200,200,0,0,false));
        assertNotNull(label.getIcon());
        FarmingHelperPanel.updateQuantity(label,new GuideView.ItemRow(1,"Coins",100,200,0,0,false));
        assertNull(label.getIcon());assertEquals("100/200",label.getText());
        assertTrue(new FarmingHelperPanel.GuideScrollContent().getScrollableTracksViewportWidth());
    }
}
