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
                    result.put(cols[0],Map.copyOf(costs));
                }
            }
        }catch(IOException|RuntimeException ignored){return Map.of();}
        return Map.copyOf(result);
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
    static final class Plan
    {
        final Map<Integer,Set<String>> sites=new LinkedHashMap<>();
        final Map<Integer,Integer> quantities=new LinkedHashMap<>();
        void add(int id,int count,Collection<String> destinations)
        {if(id>0&&count>0){sites.computeIfAbsent(id,k->new LinkedHashSet<>()).addAll(destinations);quantities.merge(id,count,Math::max);}}
    }
    static Plan plan(Set<String> sites,Map<String,List<String>> routes,Map<Integer,Set<String>> fallback,
        Map<Integer,List<String>> eligible,Map<Integer,Integer> available,Map<Integer,Integer> carried,Map<Integer,Integer> equipped,
        int pouch,Map<Integer,Integer> pouchRunes,boolean elite,IntFunction<String> names,IntPredicate stackable)
    {
        Plan result=new Plan();Set<String> unresolved=new HashSet<>();
        Map<Integer,Integer> itemUses=new LinkedHashMap<>();Map<Integer,Set<String>> itemSites=new LinkedHashMap<>();
        Map<String,Integer> runeCosts=new LinkedHashMap<>();Set<String> spellSites=new LinkedHashSet<>(),fairySites=new LinkedHashSet<>();
        for(String site:sites)
        {
            List<String> labels=routes.get(site);
            if(labels==null){unresolved.add(site);continue;}
            for(String label:labels)
            {
                if(FAIRY.equals(label)){if(!elite)fairySites.add(site);continue;}
                Map<String,Integer> spell=SPELLS.get(label);
                if(spell!=null){spell.forEach((k,v)->runeCosts.merge(k,v,PhysicalStock::sum));spellSites.add(site);continue;}
                int id=bestItem(label,eligible,available,carried,itemUses,names,stackable);
                if(id<0){unresolved.add(site);continue;}
                itemUses.merge(id,1,Integer::sum);itemSites.computeIfAbsent(id,k->new LinkedHashSet<>()).add(site);
            }
        }
        itemUses.forEach((id,n)->result.add(id,RunTeleports.quantity(id,n,names.apply(id),stackable.test(id)),itemSites.get(id)));
        if(!fairySites.isEmpty())
        {
            int staff=ownedStaff(carried,available);
            if(staff>0)result.add(staff,1,fairySites);else unresolved.addAll(fairySites);
        }
        // Only already-equipped, positively identified elemental staves are credited.
        // If a fairy staff is also needed, keep rune costs: the weapon slot can change.
        if(fairySites.isEmpty())for(int id:equipped.keySet())for(String element:elements(names.apply(id)))runeCosts.remove(element+" rune");
        if(!runeCosts.isEmpty())
        {
            Map<String,Integer> need=new LinkedHashMap<>(runeCosts);
            Map<Integer,Integer> loose=new LinkedHashMap<>();
            boolean usesPouch=pouch>0&&allocate(need,pouchRunes,names,null);
            allocate(need,available,names,loose);
            if(need.values().stream().anyMatch(v->v>0))unresolved.addAll(spellSites);
            else
            {
                if(usesPouch)result.add(pouch,1,spellSites);
                loose.forEach((id,n)->result.add(id,n,spellSites));
            }
        }
        fallback.forEach((id,where)->{
            Set<String> missing=new LinkedHashSet<>(where);missing.retainAll(unresolved);
            if(!missing.isEmpty())result.add(id,RunTeleports.quantity(id,missing.size(),names.apply(id),stackable.test(id)),missing);
        });
        return result;
    }
    private static int ownedStaff(Map<Integer,Integer> carried,Map<Integer,Integer> available)
    {
        for(int id:new int[]{772,9084})if(carried.getOrDefault(id,0)>0)return id;
        for(int id:new int[]{772,9084})if(available.getOrDefault(id,0)>0)return id;
        return -1;
    }
    private static int bestItem(String label,Map<Integer,List<String>> eligible,Map<Integer,Integer> available,
        Map<Integer,Integer> carried,Map<Integer,Integer> used,IntFunction<String> names,IntPredicate stackable)
    {
        int best=-1,rank=Integer.MAX_VALUE;
        for(var e:eligible.entrySet())
        {
            int id=e.getKey(),visits=used.getOrDefault(id,0)+1;
            if(!e.getValue().contains(label)||RunTeleports.quantity(id,visits,names.apply(id),stackable.test(id))>available.getOrDefault(id,0))continue;
            int score=carried.getOrDefault(id,0)>0?0:used.containsKey(id)?1:2;
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
    /** Allocate pure and combination runes without crediting the same physical
     * rune twice. Combined elements are conservatively budgeted separately. */
    private static boolean allocate(Map<String,Integer> need,Map<Integer,Integer> stock,IntFunction<String> names,Map<Integer,Integer> output)
    {
        boolean used=false;
        List<Integer> ids=new ArrayList<>(stock.keySet());Collections.sort(ids);
        for(int pass=0;pass<2;pass++)for(int id:ids)
        {
            String name=Objects.toString(names.apply(id),"").toLowerCase(Locale.ROOT);
            if(!name.endsWith(" rune"))continue;
            List<String> elements=COMBOS.get(name.substring(0,name.length()-5));
            if((elements==null)!=(pass==0))continue;
            int left=stock.getOrDefault(id,0);
            for(String rune:elements==null?List.of(name):List.of(elements.get(0)+" rune",elements.get(1)+" rune"))
            {
                int n=Math.min(left,need.getOrDefault(rune,0));if(n<=0)continue;
                need.put(rune,need.get(rune)-n);left-=n;used=true;if(output!=null)output.merge(id,n,Integer::sum);
            }
        }
        return used;
    }
    private RouteSupplies(){}
}
