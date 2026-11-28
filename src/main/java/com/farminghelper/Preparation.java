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
    // The vault is immediately west of the guild bank chest. Use the known
    // bank approach rather than inventing an exact vault interaction tile.
    static final Bank SEED_VAULT=new Bank("Farming Guild Seed Vault",GUILD);
    private static final List<Bank> PUBLIC=List.of(new Bank("Draynor Village",DRAYNOR),
        new Bank("Falador east",new WorldPoint(3013,3355,0)),new Bank("Varrock east",new WorldPoint(3253,3420,0)),
        new Bank("Al Kharid",new WorldPoint(3269,3166,0)),new Bank("Edgeville",new WorldPoint(3094,3491,0)),
        new Bank("Catherby",new WorldPoint(2809,3441,0)),new Bank("Seers' Village",new WorldPoint(2724,3493,0)),
        new Bank("Castle Wars",new WorldPoint(2443,3083,0)),new Bank("Yanille",new WorldPoint(2613,3092,0)),
        new Bank("Ardougne south",new WorldPoint(2655,3283,0)),new Bank("Ardougne north",new WorldPoint(2618,3332,0)),
        new Bank("Falador west",new WorldPoint(2947,3368,0)));
    static Set<WorldPoint> bankCandidates(int level)
    {
        Set<WorldPoint> points=new LinkedHashSet<>();
        for(Bank bank:PUBLIC)points.add(bank.point);
        if(level>=45)points.add(GUILD);
        return Collections.unmodifiableSet(points);
    }
    static Bank choose(int level,WorldPoint from,boolean initial,boolean entrana)
    {
        return choose(level,from,initial,entrana,StartAt.GUILD,false);
    }
    /** One legal workflow state instead of a Cartesian product of preparation and Guild stages. */
    enum Workflow
    {
        UNCHECKED(false,false,false), ORDINARY_PREP(false,true,true), ORDINARY_ACTIVE(false,false,false),
        ORDINARY_RESUPPLY(false,true,false), GUILD_ARRIVAL(true,true,true), GUILD_SEEDLINGS(true,true,true),
        GUILD_LOCAL_PREP(true,true,true), GUILD_LOCAL(true,false,false), GUILD_LOCAL_RESUPPLY(true,true,false),
        GUILD_DEPARTURE(true,true,true), GUILD_EXTERNAL(true,false,false), GUILD_EXTERNAL_RESUPPLY(true,true,false);
        final boolean guild,preparing,initial;
        Workflow(boolean guild,boolean preparing,boolean initial)
        {this.guild=guild;this.preparing=preparing;this.initial=initial;}
        boolean local(){return this==GUILD_LOCAL||this==GUILD_LOCAL_PREP||this==GUILD_LOCAL_RESUPPLY;}
        boolean external(){return this==GUILD_EXTERNAL||this==GUILD_EXTERNAL_RESUPPLY;}
        boolean splitGuild(){return local()||external()||this==GUILD_DEPARTURE;}
        Workflow resume()
        {
            switch(this)
            {
                case GUILD_ARRIVAL:return this;
                case GUILD_SEEDLINGS:case GUILD_LOCAL_PREP:case GUILD_LOCAL_RESUPPLY:return GUILD_LOCAL;
                case GUILD_DEPARTURE:case GUILD_EXTERNAL_RESUPPLY:return GUILD_EXTERNAL;
                case UNCHECKED:case ORDINARY_PREP:case ORDINARY_RESUPPLY:return ORDINARY_ACTIVE;
                default:return this;
            }
        }
        Workflow resupply(){return preparing?this:local()?GUILD_LOCAL_RESUPPLY:external()?GUILD_EXTERNAL_RESUPPLY:ORDINARY_RESUPPLY;}
    }
    static boolean atGuild(WorldPoint point)
    {return point!=null&&point.getPlane()==0&&point.distanceTo2D(GUILD)<=32;}
    enum StartAt { GUILD("Farming Guild"), BANK("Bank");
        final String label; StartAt(String label){this.label=label;}
        @Override public String toString(){return label;}
        static StartAt load(String value){return "BANK".equals(value)?BANK:GUILD;}
    }
    static Bank choose(int level,WorldPoint from,boolean initial,boolean entrana,StartAt start,boolean vaultNeeded)
    {
        if(entrana)return PUBLIC.get(0);
        if(initial&&level>=45&&(start==StartAt.GUILD||vaultNeeded))return GUILD_BANK;
        if(from==null)return PUBLIC.get(0);
        Bank best=PUBLIC.get(0);
        int distance=best.point.distanceTo2D(from);
        for(Bank bank:PUBLIC)
        {
            int d=bank.point.distanceTo2D(from);
            if(d<distance){best=bank;distance=d;}
        }
        return !initial&&level>=45&&GUILD.distanceTo2D(from)<distance?GUILD_BANK:best;
    }
    /** Prefer a planting-material source over generic tools. All decisions remain advisory. */
    static Bank chooseSupplies(int level,WorldPoint from,boolean initial,StartAt preference,boolean bird,
        Map<Integer,Integer> planting,Map<Integer,Integer> carried,Map<Integer,Integer> bank,Map<Integer,Integer> vault)
    {
        if(level>=45&&vaultNeeded(planting,carried,bank,vault))return SEED_VAULT;
        if(bird&&!initial)return FOSSIL_BANK;
        return choose(level,from,initial,false,preference,false);
    }

    static String sourceNote(Map<Integer,Integer> planting,Map<Integer,Integer> carried,Map<Integer,Integer> bank,Map<Integer,Integer> vault,
        boolean bankObserved,boolean vaultObserved)
    {
        boolean inBank=false,inVault=false,unknown=false;
        for(var e:planting.entrySet())
        {
            int id=e.getKey(),missing=Math.max(0,e.getValue()-carried.getOrDefault(id,0));
            if(missing==0)continue;
            int b=Math.max(0,bank.getOrDefault(id,0)),v=Math.max(0,vault.getOrDefault(id,0));
            if(b>=missing&&v>=missing){inBank=true;inVault=true;}
            else if(b>=missing)inBank=true;
            else if(v>=missing)inVault=true;
            else {inBank|=b>0;inVault|=v>0;unknown|=(long)b+v<missing;}
        }
        StringJoiner note=new StringJoiner(" ");
        if(inBank&&inVault)note.add("Planting supplies observed in bank / Seed Vault.");
        else if(inVault)note.add("Planting supplies observed in Seed Vault.");
        else if(inBank)note.add("Planting supplies observed in bank.");
        if(unknown)note.add("Some planting stock is missing or unverified.");
        if((inBank&&!bankObserved)||(inVault&&!vaultObserved))note.add("Storage counts may be outdated.");
        return note.toString();
    }

    static boolean vaultNeeded(Map<Integer,Integer> required,Map<Integer,Integer> carried,Map<Integer,Integer> bank,Map<Integer,Integer> vault)
    {
        for(var e:required.entrySet())if(e.getValue()>(long)carried.getOrDefault(e.getKey(),0)+bank.getOrDefault(e.getKey(),0)&&vault.getOrDefault(e.getKey(),0)>0)return true;
        return false;
    }
    static boolean missing(Map<Integer,Integer> required,Map<Integer,Integer> carried)
    {return storedShortage(required,carried,null);}
    static boolean storedShortage(Map<Integer,Integer> required,Map<Integer,Integer> carried,Map<Integer,Integer> storage)
    {
        for(var e:required.entrySet())if(carried.getOrDefault(e.getKey(),0)<e.getValue()&&(storage==null||storage.getOrDefault(e.getKey(),0)>0))return true;
        return false;
    }
    /** Count a minimum supply load; stackability is supplied by RuneLite item definitions. */
    static int slots(Map<Integer,Integer> required,Map<Integer,Integer> equipped,java.util.function.IntPredicate stackable)
    {
        long n=0;for(var e:required.entrySet()) {int count=Math.max(0,e.getValue()-equipped.getOrDefault(e.getKey(),0));n+=count==0?0:stackable.test(e.getKey())?1:count;}
        return (int)Math.min(Integer.MAX_VALUE,n);
    }
    /** Actual occupied inventory plus additional required loose items. Equipment and
     * confirmed boxed seeds cover requirements without using additional slots. */
    static int loadedSlots(Map<Integer,Integer> bag,Map<Integer,Integer> required,Map<Integer,Integer> equipped,
        Map<Integer,Integer> box,java.util.function.IntPredicate stackable)
    {
        return loadedSlots(slots(bag,Map.of(),stackable),bag,required,equipped,box,stackable);
    }
    static int loadedSlots(int occupied,Map<Integer,Integer> bag,Map<Integer,Integer> required,Map<Integer,Integer> equipped,
        Map<Integer,Integer> box,java.util.function.IntPredicate stackable)
    {
        long count=occupied;
        for(var e:required.entrySet())
        {
            int id=e.getKey(),held=bag.getOrDefault(id,0);
            long missing=(long)e.getValue()-held-equipped.getOrDefault(id,0)-box.getOrDefault(id,0);
            if(missing>0)count+=stackable.test(id)?(held>0?0:1):missing;
        }
        return (int)Math.min(Integer.MAX_VALUE,count);
    }
    /** Starting inventory if a manual seed-box packing plan is followed. */
    static int packedSlots(int occupied,Map<Integer,Integer> bag,Map<Integer,Integer> required,
        Map<Integer,Integer> equipped,Map<Integer,Integer> confirmed,Map<Integer,Integer> planned,
        java.util.function.IntPredicate stackable)
    {
        if(planned.isEmpty()||planned.equals(confirmed))return loadedSlots(occupied,bag,required,equipped,confirmed,stackable);
        Map<Integer,Integer> loose=new HashMap<>(bag);
        int removed=0;
        for(var entry:planned.entrySet())
            if(entry.getValue()>confirmed.getOrDefault(entry.getKey(),0)&&stackable.test(entry.getKey())
                &&loose.getOrDefault(entry.getKey(),0)>0)
            {loose.remove(entry.getKey());removed++;}
        return loadedSlots(Math.max(0,occupied-removed),loose,required,equipped,planned,stackable);
    }
    static int workingSlots(List<FarmRunPatch> remaining)
    {
        int reserve=0;
        for(FarmRunPatch p:remaining)
        {
            switch(p.getPatchType())
            {
                case COMPOST_BIN:break;
                case ALLOTMENT:case HOPS:case SEAWEED:case GRAPEVINE:case FRUIT_TREE:reserve=Math.max(reserve,4);break;
                case BIRDHOUSE:reserve=Math.max(reserve,5);break;
                default:reserve=Math.max(reserve,2);break;
            }
        }
        return reserve;
    }
}
