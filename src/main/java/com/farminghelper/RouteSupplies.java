package com.farminghelper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/** Translate public route labels into conservative physical supply estimates.
 * Shortest Path owns route legality; no local pathfinding or hidden API access. */
final class RouteSupplies
{
    private static final Map<String,Map<String,Integer>> SPELLS=spells();
    private static final Set<String> FREE_LABELS=freeEntries("/farming-route-free-labels.tsv"),FREE_OBJECTS=freeEntries("/farming-route-free-objects.tsv");
    private static final int[] POUCHES={ItemID.BH_RUNE_POUCH,ItemID.BH_RUNE_POUCH_TROUVER,ItemID.DIVINE_RUNE_POUCH,ItemID.DIVINE_RUNE_POUCH_TROUVER};
    private static final int[] TYPES={VarbitID.RUNE_POUCH_TYPE_1,VarbitID.RUNE_POUCH_TYPE_2,VarbitID.RUNE_POUCH_TYPE_3,VarbitID.RUNE_POUCH_TYPE_4,VarbitID.RUNE_POUCH_TYPE_5,VarbitID.RUNE_POUCH_TYPE_6};
    private static final int[] AMOUNTS={VarbitID.RUNE_POUCH_QUANTITY_1,VarbitID.RUNE_POUCH_QUANTITY_2,VarbitID.RUNE_POUCH_QUANTITY_3,VarbitID.RUNE_POUCH_QUANTITY_4,VarbitID.RUNE_POUCH_QUANTITY_5,VarbitID.RUNE_POUCH_QUANTITY_6};
    private static final Map<String,List<String>> COMBOS=Map.of("mist",List.of("air","water"),"dust",List.of("air","earth"),"mud",List.of("water","earth"),
        "smoke",List.of("air","fire"),"steam",List.of("water","fire"),"lava",List.of("earth","fire"));
    static final String FAIRY="@fairy-ring",UNKNOWN="@unknown";
    private static Map<String,Map<String,Integer>> spells()
    {
        Map<String,Map<String,Integer>> result=new HashMap<>();
        try(InputStream in=RouteSupplies.class.getResourceAsStream("/farming-route-spells.tsv"))
        {
            if(in!=null)try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)))
            {
                for(String line;(line=r.readLine())!=null;)
                {
                    if(line.startsWith("#"))continue;String[] cols=line.split("\\t");if(cols.length!=2)continue;
                    Map<String,Integer> costs=new LinkedHashMap<>();
                    for(String term:cols[1].split(",")){String[] pair=term.split(":");costs.put(pair[0],Integer.parseInt(pair[1]));}
                    result.put(labelKey(cols[0]),Map.copyOf(costs));
                }
            }
        }catch(IOException|RuntimeException ignored){return Map.of();}
        return Map.copyOf(result);
    }
    private static Set<String> freeEntries(String resource)
    {
        Set<String> entries=new HashSet<>();
        try(InputStream in=RouteSupplies.class.getResourceAsStream(resource))
        {
            if(in!=null)try(BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)))
            {for(String line;(line=reader.readLine())!=null;)if(!line.isBlank()&&!line.startsWith("#"))entries.add(labelKey(line));}
        }catch(IOException ignored){}
        return Set.copyOf(entries);
    }
    static boolean freeObject(String action){return FREE_OBJECTS.contains(labelKey(action));}
    private static String labelKey(String label)
    {return Objects.toString(label,"").strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    /** Optional convenience only: never present a banked pouch as a verified dependency
     * while the public route response is missing. Require a usable rune-only spell budget. */
    static boolean optionalPouch(Set<String> sites,Map<String,List<String>> routes,int pouch,
        Map<Integer,Integer> pouchRunes,IntFunction<String> names)
    {
        if(pouch<=0||pouchRunes.isEmpty()||sites.stream().allMatch(routes::containsKey))return false;
        RunePool pool=new RunePool(pouchRunes,names);
        for(String label:List.of("Camelot Teleport","Teleport to House","Watchtower Teleport","Falador Teleport","Ardougne Teleport","Varrock Teleport","Lumbridge Teleport"))
        {
            Map<String,Integer> cost=SPELLS.get(labelKey(label));
            if(cost==null)continue;
            Map<String,Integer> need=new HashMap<>(cost);
            pool.pay(need,pool.remaining.clone());
            if(need.isEmpty())return true;
        }
        return false;
    }
    static int pouch(Map<Integer,Integer> carried,Map<Integer,Integer> available)
    {
        for(int id:POUCHES)if(carried.getOrDefault(id,0)>0)return id;
        for(int id:POUCHES)if(available.getOrDefault(id,0)>0)return id;
        return -1;
    }
    static Map<Integer,Integer> pouchContents(Client client,int pouch)
    {
        if(pouch<0)return Map.of();
        var values=client.getEnum(EnumID.RUNEPOUCH_RUNE);if(values==null)return Map.of();
        Map<Integer,Integer> result=new HashMap<>();
        for(int i=0;i<TYPES.length;i++)
        {
            int type=client.getVarbitValue(TYPES[i]),qty=client.getVarbitValue(AMOUNTS[i]);
            if(type>0&&qty>0){int id=values.getIntValue(type);if(id>0)result.merge(id,qty,PhysicalStock::sum);}
        }
        return result;
    }
    static Map<Integer,Integer> looseRunes(Client client,Map<Integer,Integer> available)
    {
        var values=client.getEnum(EnumID.RUNEPOUCH_RUNE);
        int[] ids=values==null?null:values.getIntVals();
        if(ids==null)return Map.of();
        Map<Integer,Integer> runes=new HashMap<>();
        for(int id:ids)if(available.getOrDefault(id,0)>0)runes.put(id,available.get(id));
        return runes;
    }
    static final class Plan
    {
        final Map<Integer,Set<String>> sites=new LinkedHashMap<>();
        final Map<Integer,Integer> quantities=new LinkedHashMap<>();
        final Set<String> unresolved=new LinkedHashSet<>();
        int optionalPouch=-1;
        void add(int id,int count,String site)
        {if(id>0&&count>0){sites.computeIfAbsent(id,k->new LinkedHashSet<>()).add(site);quantities.merge(id,count,Math::max);}}
    }
    static Plan plan(Set<String> sites,Map<String,List<String>> routes,Map<Integer,Set<String>> fallback,
        Map<Integer,List<String>> eligible,Map<Integer,Integer> available,Map<Integer,Integer> carried,Map<Integer,Integer> equipped,
        int pouch,Map<Integer,Integer> pouchRunes,Map<Integer,Integer> looseRunes,boolean elite,IntFunction<String> names,IntPredicate stackable)
    {
        Plan result=new Plan();
        Map<Integer,Integer> visits=new LinkedHashMap<>();
        Set<String> resolved=new HashSet<>();
        int staff=ownedStaff(carried,available);
        Set<String> staffElements=new HashSet<>();
        if(elite||sites.stream().noneMatch(site->routes.getOrDefault(site,List.of()).contains(FAIRY)))
            for(int id:equipped.keySet())staffElements.addAll(elements(names.apply(id)));

        RunePool pouchPool=null,loosePool=null;
        for(String site:sites)
        {
            List<String> labels=routes.get(site);
            if(labels==null){result.unresolved.add(site);continue;}
            if(labels.isEmpty()){resolved.add(site);continue;}

            // Preserve funded edges independently; roll back only the failed cast.
            Map<Integer,Integer> usedItems=new HashMap<>();
            int[] pouchLeft=null,looseLeft=null;
            boolean complete=true,usesPouch=false,usesStaff=false;
            for(String label:labels)
            {
                if(FAIRY.equals(label))
                {
                    if(!elite&&staff<=0){complete=false;continue;}
                    usesStaff=!elite;
                    continue;
                }
                if(FREE_LABELS.contains(labelKey(label)))continue;
                Map<String,Integer> spell=SPELLS.get(labelKey(label));
                if(spell!=null)
                {
                    if(pouchPool==null)
                    {
                        pouchPool=new RunePool(pouch>0?pouchRunes:Map.of(),names);
                        loosePool=new RunePool(looseRunes,names);
                    }
                    if(pouchLeft==null)
                    {
                        pouchLeft=pouchPool.remaining.clone();
                        looseLeft=loosePool.remaining.clone();
                    }
                    // Try the currently equipped rune source first. If a cast cannot
                    // be funded, try an owned elemental staff, never assume tome charges.
                    int[] beforePouch=pouchLeft.clone(),beforeLoose=looseLeft.clone();
                    Map<String,Integer> need=new HashMap<>(spell);
                    for(String element:staffElements)need.remove(element+" rune");
                    boolean paidByPouch=pouchPool.pay(need,pouchLeft);
                    loosePool.pay(need,looseLeft);
                    if(!need.isEmpty())
                    {
                        boolean funded=false;
                        List<Integer> candidates=new ArrayList<>(available.keySet());
                        candidates.sort(Comparator.comparingInt((Integer id)->carried.getOrDefault(id,0)>0?0:1).thenComparingInt(Integer::intValue));
                        for(int id:candidates)
                        {
                            List<String> source=elements(names.apply(id));
                            if(source.isEmpty()||source.stream().noneMatch(e->spell.containsKey(e+" rune")))continue;
                            need=new HashMap<>(spell);for(String element:source)need.remove(element+" rune");
                            int[] trialPouch=beforePouch.clone(),trialLoose=beforeLoose.clone();
                            boolean trialUsed=pouchPool.pay(need,trialPouch);loosePool.pay(need,trialLoose);
                            if(!need.isEmpty())continue;
                            pouchLeft=trialPouch;looseLeft=trialLoose;paidByPouch=trialUsed;
                            usedItems.put(id,1);funded=true;break;
                        }
                        if(!funded){pouchLeft=beforePouch;looseLeft=beforeLoose;complete=false;continue;}
                    }
                    usesPouch|=paidByPouch;
                    continue;
                }
                int id=bestItem(label,eligible,available,carried,visits,usedItems,names,stackable);
                if(id<0){complete=false;continue;}
                usedItems.merge(id,1,Integer::sum);
            }
            if(complete)resolved.add(site);else result.unresolved.add(site);
            if(usesStaff||usesPouch||!usedItems.isEmpty()||pouchLeft!=null&&!Arrays.equals(looseLeft,loosePool.remaining))resolved.add(site);
            if(usesStaff)result.add(staff,1,site);
            if(usesPouch)result.add(pouch,1,site);
            if(pouchLeft!=null)
            {
                pouchPool.remaining=pouchLeft;
                for(int i=0;i<looseLeft.length;i++)if(loosePool.remaining[i]!=looseLeft[i])
                {
                    int id=loosePool.ids[i];
                    result.add(id,looseRunes.get(id)-looseLeft[i],site);
                }
                loosePool.remaining=looseLeft;
            }
            usedItems.forEach((id,n)->{
                if(!elements(names.apply(id)).isEmpty()){result.add(id,1,site);return;}
                int total=visits.merge(id,n,Integer::sum);
                result.add(id,RunTeleports.quantity(id,total,names.apply(id),stackable.test(id)),site);
            });
        }
        // Concrete routes have first claim on stock. A fallback may only spend the remainder.
        fallback.forEach((id,where)->{
            for(String site:where)
            {
                if(!sites.contains(site)||resolved.contains(site))continue;
                int count=visits.getOrDefault(id,0)+1;
                int quantity=RunTeleports.quantity(id,count,names.apply(id),stackable.test(id));
                if(quantity>available.getOrDefault(id,0))break;
                visits.put(id,count);
                result.add(id,quantity,site);
            }
        });
        return result;
    }
    private static int ownedStaff(Map<Integer,Integer> carried,Map<Integer,Integer> available)
    {
        for(Map<Integer,Integer> stock:List.of(carried,available))
        {
            if(stock.getOrDefault(ItemID.DRAMEN_STAFF,0)>0)return ItemID.DRAMEN_STAFF;
            if(stock.getOrDefault(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF,0)>0)return ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF;
        }
        return -1;
    }
    private static int bestItem(String label,Map<Integer,List<String>> eligible,Map<Integer,Integer> available,
        Map<Integer,Integer> carried,Map<Integer,Integer> used,Map<Integer,Integer> staged,IntFunction<String> names,IntPredicate stackable)
    {
        int best=-1,rank=Integer.MAX_VALUE;
        for(var e:eligible.entrySet())
        {
            int id=e.getKey(),visits=used.getOrDefault(id,0)+staged.getOrDefault(id,0)+1;
            if(e.getValue().stream().noneMatch(v->labelKey(v).equals(labelKey(label)))||RunTeleports.quantity(id,visits,names.apply(id),stackable.test(id))>available.getOrDefault(id,0))continue;
            int score=carried.getOrDefault(id,0)>0?0:visits>1?1:2;
            if(score<rank||score==rank&&id<best){best=id;rank=score;}
        }
        return best;
    }
    private static List<String> elements(String raw)
    {
        String name=Objects.toString(raw,"").toLowerCase(Locale.ROOT);
        for(String e:List.of("air","water","earth","fire"))
            if(name.equals("staff of "+e)||name.equals(e+" battlestaff")||name.equals("mystic "+e+" staff"))return List.of(e);
        for(var c:COMBOS.entrySet())if(name.equals(c.getKey()+" battlestaff")||name.equals("mystic "+c.getKey()+" staff"))return c.getValue();
        return List.of();
    }
    /** Classify each physical rune once. A destination spends a private copy of the
     * balances and commits it only after every transport in that destination succeeds. */
    private static final class RunePool
    {
        final int[] ids;
        final String[] first,second;
        int[] remaining;

        RunePool(Map<Integer,Integer> stock,IntFunction<String> names)
        {
            ids=stock.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
            first=new String[ids.length];second=new String[ids.length];remaining=new int[ids.length];
            for(int i=0;i<ids.length;i++)
            {
                String name=Objects.toString(names.apply(ids[i]),"").toLowerCase(Locale.ROOT);
                if(!name.endsWith(" rune"))continue;
                List<String> pair=COMBOS.get(name.substring(0,name.length()-5));
                first[i]=pair==null?name:pair.get(0)+" rune";
                if(pair!=null)second[i]=pair.get(1)+" rune";
                remaining[i]=stock.get(ids[i]);
            }
        }

        boolean pay(Map<String,Integer> need,int[] balance)
        {
            boolean used=false;
            // A combination rune can pay for both elements of the same cast. Then
            // spend exact runes before using any remaining combinations for one element.
            for(int pass=0;pass<3&&!need.isEmpty();pass++)for(int i=0;i<ids.length;i++)
            {
                if(balance[i]<=0||(pass==1)!=(second[i]==null))continue;
                int a=need.getOrDefault(first[i],0);
                int b=second[i]==null?0:need.getOrDefault(second[i],0);
                int n=Math.min(balance[i],pass==0?Math.min(a,b):Math.max(a,b));
                if(n==0)continue;
                if(a>0)deduct(need,first[i],a,n);
                if(b>0)deduct(need,second[i],b,n);
                balance[i]-=n;used=true;
            }
            return used;
        }

        private static void deduct(Map<String,Integer> need,String rune,int wanted,int paid)
        {
            if(wanted<=paid)need.remove(rune);
            else need.put(rune,wanted-paid);
        }
    }
    private RouteSupplies(){}
}
