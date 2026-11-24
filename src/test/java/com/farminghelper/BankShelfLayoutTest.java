package com.farminghelper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class BankShelfLayoutTest
{
    @Test public void sortedSlotsAndCategoryRowBoundaries()
    {
        Map<Integer,String> names=Map.of(1,"Graceful boots",2,"Graceful hood",3,"Ectophial",4,"Skills necklace(6)",5,"Law rune",6,"Spade",7,"Ultracompost",8,"Ranarr seed");
        int[] layout=BankShelfLayout.build(names.keySet(),names::get,id->id==1?10:id==2?0:-1,id->id==3?BankShelfLayout.PERMANENT:id==4?BankShelfLayout.CHARGED:-1);
        assertEquals(2,layout[0]);assertEquals(1,layout[1]);
        assertEquals(3,layout[8]);assertEquals(4,layout[16]);assertEquals(5,layout[24]);
        assertEquals(6,layout[32]);assertEquals(7,layout[40]);assertEquals(8,layout[48]);
        assertEquals(8,Arrays.stream(layout).filter(id->id>=0).count());
    }
    @Test public void noGhostItemsOrDuplicates()
    {
        assertArrayEquals(new int[0],BankShelfLayout.build(Set.of(),id->"",id->-1,id->-1));
        int[] layout=BankShelfLayout.build(Set.of(1755),id->"Chisel",id->-1,id->-1);
        assertArrayEquals(new int[]{1755},layout);
    }
}
