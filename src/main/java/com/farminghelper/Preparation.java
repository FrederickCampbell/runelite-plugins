package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;

/** Advisory bank approaches, not a bank planner. Source: Shortest Path
 * destinations/game_features/bank.tsv (public surface banks, 2026-11-24). */
final class Preparation
{
    static final WorldPoint GUILD=new WorldPoint(1253,3741,0), DRAYNOR=new WorldPoint(3092,3243,0);
    static final class Bank
    {
        final String name;final WorldPoint point;
        Bank(String name,WorldPoint point){this.name=name;this.point=point;}
    }
    // Existing verified seaweed rowboat approach on the same small bank island.
    static final Bank FOSSIL_BANK=new Bank("Bank Chest-wreck (north Fossil Island)",SpecialRoutes.SEAWEED_ISLAND);
    private static final Bank GUILD_BANK=new Bank("Farming Guild",GUILD);
    private static final List<Bank> PUBLIC=List.of(new Bank("Draynor Village",DRAYNOR),
        new Bank("Falador east",new WorldPoint(3013,3355,0)),new Bank("Varrock east",new WorldPoint(3253,3420,0)),
        new Bank("Al Kharid",new WorldPoint(3269,3166,0)),new Bank("Edgeville",new WorldPoint(3094,3491,0)),
        new Bank("Catherby",new WorldPoint(2809,3441,0)),new Bank("Seers' Village",new WorldPoint(2724,3493,0)),
        new Bank("Castle Wars",new WorldPoint(2443,3083,0)));
    static Bank choose(int level,WorldPoint from,boolean initial,boolean entrana)
    {
        if(entrana)return PUBLIC.get(0);
        if(initial&&level>=45)return GUILD_BANK;
        List<Bank> choices=new ArrayList<>(PUBLIC);if(level>=45)choices.add(GUILD_BANK);
        if(from==null)return choices.get(0);
        return choices.stream().min(Comparator.comparingInt(b->Math.max(Math.abs(b.point.getX()-from.getX()),Math.abs(b.point.getY()-from.getY())))).orElse(choices.get(0));
    }
    enum StartAt { GUILD("Farming Guild"), BANK("Bank");
        final String label; StartAt(String label){this.label=label;}
        @Override public String toString(){return label;}
        static StartAt load(String value){return "BANK".equals(value)?BANK:GUILD;}
    }
    static Bank choose(int level,WorldPoint from,boolean initial,boolean entrana,StartAt start,boolean vaultNeeded)
    {
        if(!initial||entrana)return choose(level,from,initial,entrana);
        if(level>=45&&(start==StartAt.GUILD||vaultNeeded))return GUILD_BANK;
        if(from==null)return PUBLIC.get(0);
        return PUBLIC.stream().min(Comparator.comparingInt(b->Math.max(Math.abs(b.point.getX()-from.getX()),Math.abs(b.point.getY()-from.getY())))).orElse(PUBLIC.get(0));
    }
    static boolean vaultNeeded(Map<Integer,Integer> required,Map<Integer,Integer> carried,Map<Integer,Integer> bank,Map<Integer,Integer> vault)
    {return required.entrySet().stream().anyMatch(e->e.getValue()>carried.getOrDefault(e.getKey(),0)+bank.getOrDefault(e.getKey(),0)&&vault.getOrDefault(e.getKey(),0)>0);}
    static boolean missing(Map<Integer,Integer> required,Map<Integer,Integer> carried)
    {return required.entrySet().stream().anyMatch(e->carried.getOrDefault(e.getKey(),0)<e.getValue());}
    static boolean storedShortage(Map<Integer,Integer> required,Map<Integer,Integer> carried,Map<Integer,Integer> storage)
    {return required.entrySet().stream().anyMatch(e->carried.getOrDefault(e.getKey(),0)<e.getValue()&&storage.getOrDefault(e.getKey(),0)>0);}
    /** Count a minimum supply load; stackability is supplied by RuneLite item definitions. */
    static int slots(Map<Integer,Integer> required,Map<Integer,Integer> equipped,java.util.function.IntPredicate stackable)
    {
        long n=0;for(var e:required.entrySet()) {int count=Math.max(0,e.getValue()-equipped.getOrDefault(e.getKey(),0));n+=count==0?0:stackable.test(e.getKey())?1:count;}
        return (int)Math.min(Integer.MAX_VALUE,n);
    }
}
