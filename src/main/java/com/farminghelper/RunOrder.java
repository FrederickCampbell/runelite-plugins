package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;

final class RunOrder
{
    static String site(FarmRunPatch p)
    {
        String loc=p.getLocation(); PatchType t=p.getPatchType();
        switch(loc)
        {
            case "Lumbridge": return t==PatchType.HOPS?"Lumbridge farm":"Lumbridge Castle";
            case "Morytania": return t==PatchType.MUSHROOM?"Canifis mushroom":"Morytania";
            case "Auburnvale": return t==PatchType.BELLADONNA?"Auburnvale belladonna":"Auburnvale";
            case "Falador": return t==PatchType.TREE?"Falador Park":"South Falador";
            case "Ardougne": return t==PatchType.BUSH?"Ardougne Monastery":"North Ardougne";
            case "Kourend": return t==PatchType.GRAPEVINE?"Hosidius Vinery":"Hosidius";
            case "Seaweed": return "Fossil Island";
            default: return loc;
        }
    }
    static WorldPoint surface(FarmRunPatch p)
    {
        switch(p.getPatchType())
        {
            case HESPORI:return SpecialRoutes.HESPORI_ENTRANCE;
            case SEAWEED:return SpecialRoutes.SEAWEED_ISLAND;
            case CORAL:return SpecialRoutes.CORAL_ENTRANCE;
            default:return Waypoints.destination(p);
        }
    }
    static final class Group
    {
        final String site;final List<FarmRunPatch> patches;
        Group(String site,List<FarmRunPatch> patches){this.site=site;this.patches=List.copyOf(patches);}
        WorldPoint entry(){return surface(patches.get(0));}
    }
    // Nearby sites are preferred. Distant sites have a bounded proxy because
    // map distance across continents is not a credible teleport/travel time.
    static double proxy(WorldPoint a,WorldPoint b)
    {
        if(a==null||b==null)return 110;
        int d=Math.max(Math.abs(a.getX()-b.getX()),Math.abs(a.getY()-b.getY()));
        return Math.min(100,d)+(a.getPlane()==b.getPlane()?0:10);
    }
    // Shared route selection: preset identity never changes the itinerary.
    // Physical site identities are retained; only known same-visit blocks are merged.
    private static final List<String> MIXED=List.of("Farming Guild","Gnome Stronghold","Tree Gnome Village",
        "Lumbridge Castle","Varrock","Champions' Guild","Al Kharid","Draynor Manor","South Falador",
        "Falador Park","Taverley","Rimmington","Port Sarim","North Ardougne","Ardougne Monastery",
        "Yanille","Seers' Village","Catherby","Brimhaven","Tai Bwo Wannai","Canifis mushroom","Morytania",
        "Harmony Island","Lletya","Prifddinas","Troll Stronghold","Weiss","Etceteria","Hosidius",
        "Hosidius Vinery","Civitas illa Fortis","Avium Savannah","Auburnvale","Auburnvale belladonna",
        "Kastori","Aldarin","Great Conch","Anglers' Retreat","Entrana","Fossil Island");
    private static String block(String site)
    {return "Lumbridge farm".equals(site)?"Champions' Guild":site;}
    private static int localOrder(FarmRunPatch p)
    {
        if(p.getPatchType()==PatchType.HESPORI||p.getPatchType()==PatchType.CORAL||p.getPatchType()==PatchType.SEAWEED)return 5;
        if(p.getPatchType()==PatchType.BIRDHOUSE)return 6;
        if("Champions' Guild".equals(p.getLocation())||"Gnome Stronghold".equals(p.getLocation())&&p.getPatchType()==PatchType.FRUIT_TREE)return 0;
        if(p.getPatchType()==PatchType.COMPOST_BIN)return 4;
        if(p.getPatchType().isTree())return 1;
        return 2;
    }
    static List<Group> shared(List<FarmRunPatch> patches,WorldPoint start,boolean guildFirst,Set<PatchType> carriedSaplings)
    {
        Set<PatchType> types=EnumSet.noneOf(PatchType.class);
        patches.forEach(p->types.add(p.getPatchType()));
        List<String> reference=MIXED;
        for(RunPreset preset:RunPreset.values())
            if(!types.isEmpty()&&preset.types.containsAll(types))
            {reference=preset.reference;break;}
        Map<String,List<FarmRunPatch>> byBlock=new LinkedHashMap<>();
        for(FarmRunPatch patch:patches)
            byBlock.computeIfAbsent(block(site(patch)),k->new ArrayList<>()).add(patch);
        List<Group> ordered=new ArrayList<>();
        byBlock.forEach((site,ps)->{
            // Birdhouse direction follows the actual island entry point when possible.
            // Off-island use the documented stable south-to-north catalogue direction.
            WorldPoint south=BirdhouseRun.Site.MEADOW_SOUTH.approach,north=BirdhouseRun.Site.VALLEY_NORTH.approach;
            boolean reverse=start!=null&&proxy(start,north)<100&&proxy(start,north)<proxy(start,south);
            ps.sort(Comparator.comparingInt(RunOrder::localOrder)
                .thenComparingInt(p->p.getPatchType()==PatchType.BIRDHOUSE?(reverse?-BirdhouseRun.order(p):BirdhouseRun.order(p)):0));
            ordered.add(new Group(site,ps));
        });
        final List<String> ref=reference;
        ordered.sort(Comparator.comparingInt((Group g)->{
            int n=ref.indexOf(g.site);
            if(n>=0)return n;
            int m=MIXED.indexOf(g.site);return ref.size()+(m<0?MIXED.size():m);
        }).thenComparing(g->g.site));
        Group guild=ordered.stream().filter(g->"Farming Guild".equals(g.site)).findFirst().orElse(null);
        if(guild!=null&&!guildFirst)ordered.remove(guild);
        // If Bank was chosen, prefer an actually nearby first block, not a guessed long-distance teleport.
        if(!guildFirst&&guild!=null)ordered.add(guild);
        if(!guildFirst&&start!=null)
        {
            Group near=ordered.stream().filter(g->!terminal(g)&&g.entry()!=null&&proxy(start,g.entry())<70)
                .min(Comparator.comparingDouble(g->proxy(start,g.entry()))).orElse(null);
            if(near!=null){ordered.remove(near);ordered.add(0,near);}
            else if(guild!=null){ordered.remove(guild);ordered.add(Math.min(1,ordered.size()),guild);}
        }
        // Full inventories benefit from an early carried-sapling block. Never split a site or move terminal legs.
        if(!carriedSaplings.isEmpty())
        {
            int first=guildFirst&&!ordered.isEmpty()&&"Farming Guild".equals(ordered.get(0).site)?1:0;
            for(int i=first;i<ordered.size();i++)
            {
                Group g=ordered.get(i);
                if(!terminal(g)&&g.patches.stream().anyMatch(p->carriedSaplings.contains(p.getPatchType())))
                {ordered.remove(i);ordered.add(first,g);break;}
            }
        }
        // Entrana comes before Fossil Island, both after ordinary surface work.
        ordered.sort(Comparator.comparingInt(g->terminalOrder(g.site)));
        return ordered;
    }
    /** Move only ready members of a nearby selected block. Keep the rest of the
     * itinerary stable and preserve the existing terminal-island ordering.
     * This is a bounded geographic preference, not a path cost estimate. */
    static void promoteNearby(List<FarmRunPatch> itinerary,List<FarmRunPatch> ready,WorldPoint here)
    {
        if(here==null||ready.isEmpty())return;
        Set<FarmRunPatch> allowed=new HashSet<>(ready);
        Map<String,List<FarmRunPatch>> groups=new LinkedHashMap<>();
        for(FarmRunPatch p:itinerary)
            if(allowed.contains(p)&&terminalOrder(site(p))==0)
                groups.computeIfAbsent(block(site(p)),k->new ArrayList<>()).add(p);
        List<FarmRunPatch> best=null;double distance=70;
        for(List<FarmRunPatch> group:groups.values())
        {
            group.sort(Comparator.comparingInt(RunOrder::localOrder));
            WorldPoint entry=surface(group.get(0));
            if(entry==null||entry.getPlane()!=here.getPlane())continue;
            double d=proxy(here,entry);
            if(d<distance){best=group;distance=d;}
        }
        if(best!=null)
        {
            itinerary.removeAll(best);itinerary.addAll(0,best);
            ready.removeAll(best);ready.addAll(0,best);
        }
    }
    static boolean arrival(WorldPoint before,WorldPoint after)
    {return before!=null&&after!=null&&before.distanceTo2D(after)>12;}
    private static boolean terminal(Group g){return "Entrana".equals(g.site)||"Fossil Island".equals(g.site);}
    /** Keep the established order. Insert newly ready mainland work before the terminal islands. */
    static void appendReady(List<FarmRunPatch> itinerary,List<FarmRunPatch> ready)
    {
        for(FarmRunPatch p:ready)if(!itinerary.contains(p))
        {
            int at=itinerary.size();
            while(at>0 && terminalOrder(site(itinerary.get(at-1)))>terminalOrder(site(p)))at--;
            itinerary.add(at,p);
        }
    }
    private static int terminalOrder(String site){return "Fossil Island".equals(site)?2:"Entrana".equals(site)?1:0;}

    static List<FarmRunPatch> flatten(List<Group> groups)
    {List<FarmRunPatch> result=new ArrayList<>();for(Group g:groups)result.addAll(g.patches);return result;}
}
