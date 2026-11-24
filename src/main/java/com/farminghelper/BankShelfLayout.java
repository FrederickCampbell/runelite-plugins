package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

/** Native Bank Tags layout coordinates; no bank item movement or widget mutation.
 * Only actual stocked IDs are supplied by the caller. Each category begins on a new bank row.
 * Multiple variants in one slot remain together; long categories wrap naturally. */
final class BankShelfLayout
{
    static final int WIDTH=8;
    static final int PERMANENT=20,CHARGED=21,CONSUMABLE=22,TOOLS=23,COMPOST=24,SEEDS=25;
    private static final int[] SLOTS={0,1,2,3,4,5,7,9,10,12,13};
    private BankShelfLayout(){}

    static int category(int id,String name,int equipmentSlot,int transport)
    {
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        // Farming cape remains with other capes; active transport jewellery stays together.
        if(n.contains("rune pouch")||n.endsWith(" rune")||n.contains("tablet")||n.endsWith(" basalt")||n.contains("teleport scroll")||n.equals("master scroll book")||n.equals("tai bwo wannai teleport"))return CONSUMABLE;
        if(transport>=0&&(n.startsWith("ardougne cloak")||n.startsWith("explorer's ring")||n.startsWith("rada's blessing")))return PERMANENT;
        if(transport>=0&&!n.startsWith("farming cape")&&!n.startsWith("construction cape")&&!n.startsWith("crafting cape")&&!n.startsWith("achievement diary cape"))return transport;
        if(n.contains("sapling")||n.endsWith(" seed")||n.endsWith(" seeds")||n.endsWith(" spore")||n.endsWith(" spores")||n.equals("grape seed")||n.equals("seaweed spore")||CropCatalog.findByItemId(id)!=null)return SEEDS;
        if(n.endsWith(" logs")||n.equals("logs")||n.equals("clockwork")||n.contains("bird house")||n.contains("birdhouse"))return TOOLS;
        if(n.contains("compost")||n.contains("volcanic ash")||n.equals("coins")||n.equals("saltpetre"))return COMPOST;
        if(n.equals("dramen staff")||n.equals("lunar staff"))return PERMANENT;
        if(n.contains("staff")||n.contains("secateurs")||n.endsWith(" axe")||n.contains("axe (")||n.contains("hammer")||n.contains("chisel")||n.contains("watering can"))return equipmentSlot==3?3:TOOLS;
        for(int i=0;i<SLOTS.length;i++)if(equipmentSlot==SLOTS[i])return i;
        // Item stat lookup may be absent for nontradeable gear; explicit common slots.
        if(n.contains("hood")||n.contains("helmet")||n.contains("strawhat"))return 0;
        if(n.endsWith("cape")||n.equals("diving apparatus"))return 1;
        if(n.startsWith("amulet")||n.equals("medallion of the deep")||n.equals("strung rabbit foot"))return 2;
        if(n.startsWith("tome of"))return 5;
        if(n.contains("graceful top")||n.contains("farmer's jacket")||n.contains("farmer's shirt"))return 4;
        if(n.contains("graceful legs")||n.contains("trousers")||n.contains("breeches"))return 6;
        if(n.endsWith("gloves"))return 7;
        if(n.endsWith("boots")||n.equals("flippers"))return 8;
        if(n.endsWith("blessing"))return 10;
        if(n.contains("sack")||n.contains("seed box")||n.contains("spade")||n.contains("rake")||n.contains("dibber")||n.contains("trowel")||n.contains("plant cure")||n.contains("bucket")||n.contains("basket")||n.contains("gricoller"))return TOOLS;
        return COMPOST; // remaining explicit run supplies (payments, logs, clockwork)
    }

    static int[] build(Set<Integer> owned,IntFunction<String> names,IntUnaryOperator equipment,IntUnaryOperator transport)
    {
        List<Integer> ordered=new ArrayList<>(owned);
        ordered.sort(Comparator.comparingInt((Integer id)->category(id,names.apply(id),equipment.applyAsInt(id),transport.applyAsInt(id)))
            .thenComparing(id->Objects.toString(names.apply(id),""),String.CASE_INSENSITIVE_ORDER).thenComparingInt(Integer::intValue));
        List<Integer> result=new ArrayList<>();
        int previous=-1;
        for(int id:ordered)
        {
            int group=category(id,names.apply(id),equipment.applyAsInt(id),transport.applyAsInt(id));
            // All equipment slots form one contiguous block; every later category starts a fresh row.
            int section=group<PERMANENT?0:group;
            if(previous>=0&&section!=previous)while(result.size()%WIDTH!=0)result.add(-1);
            result.add(id);previous=section;
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }
}
