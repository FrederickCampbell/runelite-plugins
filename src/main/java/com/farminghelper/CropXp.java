package com.farminghelper;

import java.util.*;

/** Healthy-cycle XP per planting item. Values from the user's 2026-11-24
 * normalized OSRS Wiki crop tables. Variable yields are the documented
 * high-level reference estimates, NOT a prediction for the player's level.
 * Missing yield data is represented by NaN; use planting/health XP only in
 * that case. No invented CTS constants or disease probabilities. */
final class CropXp
{
    static final class Score
    {
        final double planting,health,harvest,yield;
        final boolean estimated;
        Score(double p,double c,double h,double y,boolean estimate)
        {planting=p;health=c;harvest=h;yield=y;estimated=estimate;}
        double total(){return planting+health+(Double.isNaN(yield)?0:harvest*yield);}
        boolean unknownYield(){return Double.isNaN(yield);}
    }
    static double perSeed(Crop crop){return crop.getScore().total()/crop.getQuantity();}
    private static final Map<PatchType,Map<Integer,List<Crop>>> rankings=new java.util.concurrent.ConcurrentHashMap<>();
    static List<Crop> ranked(PatchType type,int level)
    {
        return rankings.computeIfAbsent(type,t->new java.util.concurrent.ConcurrentHashMap<>())
            .computeIfAbsent(Math.max(0,Math.min(99,level)),l->rank(type,l));
    }
    private static List<Crop> rank(PatchType type,int level)
    {
        List<Crop> out=new ArrayList<>();
        for(Crop c:CropCatalog.crops(type))if(c.getLevel()<=level)out.add(c);
        out.sort(Comparator.<Crop>comparingDouble(c->{double v=perSeed(c);return Double.isNaN(v)?-1:v;}).reversed()
            .thenComparing(Comparator.comparingInt(Crop::getLevel).reversed()));
        return List.copyOf(out);
    }
    private CropXp(){}
}
