package com.farminghelper;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class BankShelfLayoutTest
{
    private static int position(int[] layout,int id){for(int i=0;i<layout.length;i++)if(layout[i]==id)return i;return -1;}
    @Test public void mannequinAndJewelleryAndRunes()
    {
        Map<Integer,String> names=Map.ofEntries(Map.entry(11850,"Graceful hood"),Map.entry(11860,"Graceful boots"),Map.entry(13646,"Farmer's strawhat"),Map.entry(4251,"Ectophial"),Map.entry(11105,"Skills necklace(4)"),Map.entry(11968,"Skills necklace(6)"),Map.entry(556,"Air rune"),Map.entry(555,"Water rune"),Map.entry(557,"Earth rune"),Map.entry(554,"Fire rune"),Map.entry(563,"Law rune"),Map.entry(952,"Spade"),Map.entry(21483,"Ultracompost"),Map.entry(5295,"Ranarr seed"));
        int[] layout=BankShelfLayout.build(names.keySet(),names::get,id->-1,id->id==4251?BankShelfLayout.PERMANENT:id==11105||id==11968?BankShelfLayout.CHARGED:-1);
        assertEquals(1,position(layout,11850));assertEquals(33,position(layout,11860));assertEquals(5,position(layout,13646));
        assertEquals(position(layout,11968)+1,position(layout,11105));
        int air=position(layout,556);assertEquals(0,air%8);assertEquals(air+1,position(layout,555));assertEquals(air+2,position(layout,557));assertEquals(air+3,position(layout,554));assertEquals(air+8,position(layout,563));
        assertTrue(position(layout,952)<position(layout,21483));assertTrue(position(layout,21483)<position(layout,5295));
        assertEquals(names.size(),Arrays.stream(layout).filter(id->id>=0).count());
    }
    @Test public void recommendationsLeadAndOtherFamiliesAreCompact()
    {
        Map<Integer,String> names=Map.of(11978,"Amulet of glory(6)",11976,"Amulet of glory(5)",11105,"Skills necklace(4)",952,"Spade",1755,"Chisel");
        int[] layout=BankShelfLayout.build(names.keySet(),names::get,id->-1,id->id==952||id==1755?-1:BankShelfLayout.CHARGED,List.of(11105,999,11105));
        assertEquals(11105,layout[0]);assertEquals(-1,position(layout,999));
        assertEquals(names.size(),Arrays.stream(layout).filter(id->id>=0).distinct().count());
        assertEquals(position(layout,11978)+1,position(layout,11976));
        assertEquals(names.size(),BankShelfLayout.build(names.keySet(),names::get,id->-1,id->id==952||id==1755?-1:BankShelfLayout.CHARGED).length);
    }
    @Test public void bankBagAndWornItemsShareOneStableShelf()
    {
        Map<Integer,String> names=Map.of(11850,"Graceful hood",952,"Spade",7409,"Magic secateurs");
        Set<Integer> owned=names.keySet();
        int[] layout=BankShelfLayout.build(owned,names::get,id->-1,id->-1);
        assertTrue(position(layout,11850)>=0);assertTrue(position(layout,952)>=0);assertTrue(position(layout,7409)>=0);
        assertArrayEquals(layout,BankShelfLayout.reconcile(layout,owned,owned,names::get,id->-1,id->-1));
    }
    @Test public void spentJewelleryChargeKeepsItsPosition()
    {
        Map<Integer,String> names=Map.of(11978,"Amulet of glory(6)",11976,"Amulet of glory(5)",952,"Spade");
        int[] initial=BankShelfLayout.build(Set.of(11978,952),names::get,id->-1,id->id==952?-1:BankShelfLayout.CHARGED);
        int[] changed=BankShelfLayout.reconcile(initial,Set.of(11976,952),Set.of(11976,952),names::get,id->-1,id->id==952?-1:BankShelfLayout.CHARGED);
        assertEquals(position(initial,11978),position(changed,11976));
        assertEquals(position(initial,952),position(changed,952));
        assertEquals(-1,position(changed,11978));
    }
    @Test public void noGhostItemsInitiallyAndStableWithdrawals()
    {
        assertArrayEquals(new int[0],BankShelfLayout.build(Set.of(),id->"",id->-1,id->-1));
        Map<Integer,String> names=Map.of(952,"Spade",1755,"Chisel",2347,"Hammer");
        int[] initial=BankShelfLayout.build(Set.of(952,1755),names::get,id->-1,id->-1);
        int[] withdrawn=BankShelfLayout.reconcile(initial,Set.of(1755),Set.of(1755),names::get,id->-1,id->-1);
        assertArrayEquals(initial,withdrawn);
        int[] deposited=BankShelfLayout.reconcile(withdrawn,Set.of(1755,2347),Set.of(1755,2347),names::get,id->-1,id->-1);
        assertEquals(position(initial,1755),position(deposited,1755));assertEquals(position(initial,952),position(deposited,952));assertTrue(position(deposited,2347)>=initial.length);
        int[] noLongerEligible=BankShelfLayout.reconcile(initial,Set.of(1755),Set.of(952,1755),names::get,id->-1,id->-1);
        assertEquals(-1,noLongerEligible[position(initial,952)]);
    }
}
