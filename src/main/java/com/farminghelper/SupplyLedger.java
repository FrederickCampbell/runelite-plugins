package com.farminghelper;

import java.util.*;

/** Scoped demand accumulator. Consumables add, reusable resources take a maximum.
 * Physical conversions (notes, seedlings, charges) remain explicit at their owning subsystem. */
final class SupplyLedger
{
    final Map<Integer,Integer> total,guild;
    SupplyLedger(){this(new LinkedHashMap<>(),new LinkedHashMap<>());}
    SupplyLedger(Map<Integer,Integer> total,Map<Integer,Integer> guild){this.total=total;this.guild=guild;}
    void consume(int id,int count,boolean local){add(id,count,local,PhysicalStock::sum);}
    void reuse(int id,int count,boolean local){add(id,count,local,Math::max);}
    void consumeSplit(int id,int count,int local)
    {
        int n=Math.min(Math.max(0,local),count);
        consume(id,n,true);consume(id,count-n,false);
    }
    /** Shared stored supplies are reserved for the first stage before external work. */
    void deduct(int id,int count)
    {
        if(count<=0)return;
        total.computeIfPresent(id,(k,n)->n>count?n-count:null);
        guild.computeIfPresent(id,(k,n)->n>count?n-count:null);
    }
    private void add(int id,int count,boolean local,java.util.function.BinaryOperator<Integer> combine)
    {
        if(count<=0)return;
        total.merge(id,count,combine);
        if(local)guild.merge(id,count,combine);
    }
    Map<Integer,Integer> withdrawals(Map<Integer,Integer> physical,Preparation.Workflow workflow,Set<Integer> prep)
    {
        if(workflow!=Preparation.Workflow.GUILD_SEEDLINGS&&!(workflow.local()&&workflow.preparing)&&physical.values().stream().allMatch(n->n>0))return physical;
        Map<Integer,Integer> result=new LinkedHashMap<>();
        physical.forEach((id,n)->{
            int count=workflow==Preparation.Workflow.GUILD_SEEDLINGS?(prep.contains(id)?n:0):
                workflow.local()&&workflow.preparing?Math.min(n,guild.getOrDefault(id,0)):n;
            if(count>0)result.put(id,count);
        });
        return result;
    }
}
