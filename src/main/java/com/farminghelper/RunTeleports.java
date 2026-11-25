package com.farminghelper;

import java.util.*;

/** Advisory item destinations based on Farming runs / Farming patch locations,
 * using existing Shortest Path transport rows and their unlock checks.
 * Not a pathfinder, charge planner or mandatory inventory loadout. */
final class RunTeleports
{
    private static final Map<String,List<String>> REFERENCES=new HashMap<>();
    static
    {
        at("Farming Guild","Farming cape: Farming Guild","Max cape: Other Teleports: Farming Guild","Skills necklace: Farming Guild","Trinket of fairies: Tool Lep - Farming Guild","Trinket of fairies: Fairy Ring - CIR (South of Mount Karuulm)");
        at("South Falador","Explorer's ring: Teleport","Explorer's ring 2: Teleport","Trinket of fairies: Tool Lep - Falador Farm","Falador tablet");
        at("Falador Park","Ring of wealth: Falador Park","Trinket of fairies: Tool Lep - Falador Park","Falador tablet");
        at("North Ardougne","Ardougne cloak: Ardougne Farm","Trinket of fairies: Tool Lep - North of Ardougne","Skills necklace: Fishing Guild","Ardougne tablet");
        at("Ardougne Monastery","Ardougne cloak: Kandarin Monastery","Combat bracelet: Monastery","Trinket of fairies: Tool Lep - Ardougne Monastery");
        at("Catherby","Catherby tablet","Trinket of fairies: Tool Lep - Catherby","Camelot tablet");
        at("Morytania","Ectophial","Fenkenstrain's Castle tablet","Trinket of fairies: Tool Lep - Port Phasmatys");
        at("Canifis mushroom","Salve Graveyard tablet","Kharyrll tablet","Trinket of fairies: Tool Lep - West of Canifis");
        at("Hosidius","Xeric's talisman: 2. Xeric's Glade","Hosidius tablet","Book of the dead: Lunch by the Lancalliums","Kharedst's memoirs: Lunch by the Lancalliums","Trinket of fairies: Tool Lep - Hosidius Allotment");
        at("Hosidius Vinery","Trinket of fairies: Tool Lep - Hosidius Vinery","Trinket of fairies: Fairy Ring - AKR (Hosidius Vinery)");
        at("Troll Stronghold","Stony basalt: Roof","Stony basalt","Trinket of fairies: Tool Lep - Troll Stronghold","Trollheim tablet");
        at("Weiss","Icy basalt","Trinket of fairies: Tool Lep - Weiss");
        at("Harmony Island","Harmony Island tablet","Trinket of fairies: Tool Lep - Harmony Island");
        at("Civitas illa Fortis","Civitas illa Fortis tablet","Quetzal whistle: Civitas illa Fortis","Perfected quetzal whistle(i): Civitas illa Fortis","Trinket of fairies: Tool Lep - Ortus Farm");
        at("Prifddinas","Eternal teleport crystal: Prifddinas","Teleport crystal: Prifddinas","Prifddinas tablet","Construction cape: Prifddinas","Trinket of fairies: Tool Lep - Prifddinas");
        at("Lumbridge Castle","Lumbridge tablet","Trinket of fairies: Tool Lep - Lumbridge");
        at("Lumbridge farm","Chronicle","Combat bracelet: Champions' Guild","Lumbridge tablet");
        at("Auburnvale belladonna","Trinket of fairies: Tool Lep - Nemus Retreat","Pendant of ates: 6. Nemus Retreat","Quetzal whistle: Auburnvale","Perfected quetzal whistle(i): Auburnvale");
        at("Varrock","Varrock tablet","Trinket of fairies: Tool Lep - Varrock");
        at("Gnome Stronghold","Royal seed pod: Grand Tree","Grand seed pod: Grand Tree","Slayer ring: Stronghold","Trinket of fairies: Spirit Tree - Gnome Stronghold");
        at("Tree Gnome Village","Trinket of fairies: Spirit Tree - Tree Gnome Village","Trinket of fairies: Tool Lep - Tree Gnome Village");
        at("Taverley","Taverley tablet","Construction cape: Taverley","Trinket of fairies: Tool Lep - Taverley","Games necklace: Burthorpe");
        at("Auburnvale","Quetzal whistle: Auburnvale","Perfected quetzal whistle(i): Auburnvale","Pendant of ates: 6. Nemus Retreat","Trinket of fairies: Tool Lep - Auburnvale");
        at("Lletya","Eternal teleport crystal: Lletya","Teleport crystal: Lletya","Trinket of fairies: Tool Lep - Lletya");
        at("Brimhaven","Brimhaven tablet","Construction cape: Brimhaven","Trinket of fairies: Tool Lep - Brimhaven","Trinket of fairies: Spirit Tree - Brimhaven (planted)");
        at("Tai Bwo Wannai","Tai bwo wannai teleport","Master scroll book: Tai bwo wannai teleport","Trinket of fairies: Tool Lep - Tai Bwo Wannai");
        at("Kastori","Pendant of ates: 5. Kastori","Quetzal whistle: Kastori","Perfected quetzal whistle(i): Kastori","Trinket of fairies: Tool Lep - Kastori North","Trinket of fairies: Tool Lep - Kastori South");
        at("Fossil Island","Digsite pendant: Fossil Island","Trinket of fairies: Tool Lep - Fossil Island");
        at("Avium Savannah","Trinket of fairies: Fairy Ring - AJP (Avium Savannah)","Trinket of fairies: Tool Lep - Locus Oasis");
        at("Great Conch","Trinket of fairies: Fairy Ring - CJQ (The Great Conch)");
        at("Champions' Guild","Chronicle","Combat bracelet: Champions' Guild","Trinket of fairies: Tool Lep - Champions Guild");
        at("Seers' Village","Camelot tablet: Seers' Village","Camelot tablet","Trinket of fairies: Tool Lep - North of McGrubor's Wood");
        at("Yanille","Yanille tablet","Watchtower tablet: Yanille","Construction cape: Yanille","Trinket of fairies: Tool Lep - Yanille");
        at("Rimmington","Rimmington tablet","Construction cape: Rimmington","Trinket of fairies: Tool Lep - Rimmington");
        at("Etceteria","Ring of wealth: Miscellania","Trinket of fairies: Tool Lep - Etceteria","Trinket of fairies: Spirit Tree - Etceteria (planted)");
        at("Aldarin","Aldarin tablet","Quetzal whistle: Aldarin","Perfected quetzal whistle(i): Aldarin","Pendant of ates: 4. North Aldarin","Trinket of fairies: Tool Lep - Aldarin");
        at("Al Kharid","Amulet of glory: Al Kharid","Ring of dueling: Emir's Arena","Trinket of fairies: Tool Lep - Al Kharid");
        at("Draynor Manor","Draynor Manor tablet","Amulet of glory: Draynor Village","Trinket of fairies: Tool Lep - Draynor Manor");
        at("Port Sarim","Trinket of fairies: Tool Lep - Port Sarim","Trinket of fairies: Spirit Tree - Port Sarim (planted)");
        at("Entrana","Trinket of fairies: Tool Lep - Entrana");
    }
    private static void at(String site,String... destinations){REFERENCES.put(site,List.of(destinations));}
    /** One useful owned option per site. Prefer carried, then an already chosen
     * item, then the reference order. Deduplicate identical item IDs. */
    static Map<Integer,Set<String>> select(Collection<FarmRunPatch> ready,Map<Integer,List<String>> eligible,
        Map<Integer,Integer> carried)
    {
        Set<String> sites=new LinkedHashSet<>();for(FarmRunPatch p:ready)sites.add(RunOrder.site(p));
        Map<Integer,Set<String>> result=new LinkedHashMap<>();
        for(String site:sites)
        {
            List<String> refs=REFERENCES.getOrDefault(site,List.of());
            int best=-1,bestScore=Integer.MAX_VALUE;
            for(var entry:eligible.entrySet())
            {
                int rank=Integer.MAX_VALUE;
                for(String dest:entry.getValue()){int n=refs.indexOf(dest);if(n>=0)rank=Math.min(rank,n);}
                if(rank==Integer.MAX_VALUE)continue;
                int score=(carried.getOrDefault(entry.getKey(),0)>0?0:result.containsKey(entry.getKey())?100:200)+rank;
                if(score<bestScore||score==bestScore&&entry.getKey()<best){best=entry.getKey();bestScore=score;}
            }
            if(best>=0)result.computeIfAbsent(best,id->new LinkedHashSet<>()).add(site);
        }
        return result;
    }
    private RunTeleports(){}
}
