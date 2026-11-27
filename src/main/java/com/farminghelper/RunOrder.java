package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;

final class RunOrder
{
    private final List<FarmRunPatch> itinerary;
    private final boolean protectedManual;
    private final Map<FarmRunPatch,Integer> ranks = new HashMap<>();
    private final Comparator<FarmRunPatch> byRank = Comparator.comparingInt(ranks::get);

    RunOrder(List<Group> groups){this(groups,false);}
    RunOrder(List<Group> groups,boolean protectedManual)
    {
        this.protectedManual=protectedManual;
        itinerary = flatten(groups);
        index();
    }

    void order(List<FarmRunPatch> ready, List<FarmRunPatch> plant, List<FarmRunPatch> visible)
    {
        if (!ranks.keySet().containsAll(ready))
        {
            appendReady(itinerary, ready);
            if(protectedManual)itinerary.sort(Comparator.comparingInt(p->protectedRank(block(site(p)))));
            index();
        }
        for (List<FarmRunPatch> patches : List.of(ready, plant, visible))
        {
            patches.removeIf(p -> !ranks.containsKey(p));
            patches.sort(byRank);
        }
    }

    /** A verified local visit can supersede the next remote ordinary plot.
     * Never abandon a live active plot or reorder special transport legs.
     * Allocation and pinned itinerary remain unchanged. */
    static void preferLocal(List<FarmRunPatch> ready,List<FarmRunPatch> visible,FarmRunPatch current,
        boolean currentLive,java.util.function.ToIntFunction<FarmRunPatch> distance)
    {
        if(ready.isEmpty()||currentLive&&ready.contains(current))return;
        FarmRunPatch first=ready.get(0);
        if(!ordinary(first))return;
        FarmRunPatch best=first;
        int score=distance.applyAsInt(first);
        for(FarmRunPatch patch:ready)
        {
            if(patch==first||!ordinary(patch))continue;
            int d=distance.applyAsInt(patch);
            // Cross-site promotion requires actual arrival, not just a loaded scene.
            boolean sameVisit=site(first).equals(site(patch))||current!=null&&site(current).equals(site(patch));
            if(d>(sameVisit?15:5)||d>=score)continue;
            best=patch;score=d;
        }
        if(best!=first)
        {
            ready.remove(best);ready.add(0,best);
            if(visible.remove(best))visible.add(0,best);
        }
    }
    // Local promotion must never jump an ordinary protected region. Salve arrival is the deliberate exception.
    private static final WorldPoint SALVE_GRAVEYARD=new WorldPoint(3433,3461,0);
    static void preferProtectedLocal(List<FarmRunPatch> ready,List<FarmRunPatch> visible,FarmRunPatch current,
        boolean currentLive,WorldPoint player,java.util.function.ToIntFunction<FarmRunPatch> distance)
    {
        if(ready.isEmpty()||currentLive&&ready.contains(current)||player==null)return;
        FarmRunPatch first=ready.get(0),best=first;
        if("Farming Guild".equals(site(first)))return;
        if("Gnome Stronghold".equals(site(first)))
            for(FarmRunPatch candidate:ready)
                if("Gnome Stronghold".equals(site(candidate))&&distance.applyAsInt(candidate)<=15&&distance.applyAsInt(candidate)<distance.applyAsInt(best))best=candidate;
        if(!terminalOrderPositive(first)&&player.getPlane()==0&&player.distanceTo2D(SALVE_GRAVEYARD)<=5)
            for(FarmRunPatch candidate:ready)if(candidate.getPatchType()==PatchType.MUSHROOM){best=candidate;break;}
        if(best!=first){ready.remove(best);ready.add(0,best);if(visible.remove(best))visible.add(0,best);}
    }
    private static boolean ordinary(FarmRunPatch p)
    {
        return !terminalOrderPositive(p)&&p.getPatchType()!=PatchType.HESPORI&&p.getPatchType()!=PatchType.CORAL&&
            p.getPatchType()!=PatchType.SEAWEED&&p.getPatchType()!=PatchType.BIRDHOUSE;
    }
    private static boolean terminalOrderPositive(FarmRunPatch p){return terminalOrder(site(p))>0;}

    private void index()
    {
        for (int i = 0; i < itinerary.size(); i++) ranks.put(itinerary.get(i), i);
    }

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
        "Lumbridge Castle","Varrock","Champions' Guild","Al Kharid","South Falador","Draynor Manor",
        "Falador Park","Taverley","Rimmington","Port Sarim","North Ardougne","Ardougne Monastery",
        "Yanille","Seers' Village","Catherby","Brimhaven","Tai Bwo Wannai","Canifis mushroom","Morytania",
        "Harmony Island","Lletya","Prifddinas","Troll Stronghold","Weiss","Etceteria","Hosidius",
        "Hosidius Vinery","Civitas illa Fortis","Avium Savannah","Auburnvale","Auburnvale belladonna",
        "Kastori","Aldarin","Great Conch","Anglers' Retreat","Entrana","Fossil Island");
    private static String block(String site)
    {return "Lumbridge farm".equals(site)?"Champions' Guild":site;}
    private static final List<String> PROTECTED=List.of("Farming Guild","Gnome Stronghold","Tree Gnome Village","Lumbridge Castle","Varrock","Falador Park","Taverley","Catherby","Brimhaven","Tai Bwo Wannai","South Falador","Draynor Manor","Kastori","Auburnvale","Auburnvale belladonna");
    private static int protectedRank(String site)
    {
        if(terminalOrder(site)>0)return 1000+terminalOrder(site);
        int n=PROTECTED.indexOf(site);if(n>=0)return n;
        int m=MIXED.indexOf(site);return PROTECTED.size()+(m<0?MIXED.size():m);
    }
    private static int localOrder(FarmRunPatch p)
    {
        if(p.getPatchType()==PatchType.HESPORI||p.getPatchType()==PatchType.CORAL||p.getPatchType()==PatchType.SEAWEED)return 5;
        if(p.getPatchType()==PatchType.BIRDHOUSE)return 6;
        if("Champions' Guild".equals(p.getLocation())||"Gnome Stronghold".equals(p.getLocation())&&p.getPatchType()==PatchType.FRUIT_TREE)return 0;
        if(p.getPatchType()==PatchType.COMPOST_BIN)return 4;
        if(p.getPatchType().isTree())return 1;
        return 2;
    }
    static List<Group> shared(List<FarmRunPatch> patches,WorldPoint start,boolean guildFirst,Set<PatchType> ignored)
    {return shared(patches,start,guildFirst,false);}
    static List<Group> shared(List<FarmRunPatch> patches,WorldPoint start,boolean guildFirst,boolean protectedManual)
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
        // Direction is run-wide, not different for every physical group.
        WorldPoint south=BirdhouseRun.Site.MEADOW_SOUTH.approach,north=BirdhouseRun.Site.VALLEY_NORTH.approach;
        double northDistance=proxy(start,north);
        boolean reverse=start!=null&&northDistance<100&&northDistance<proxy(start,south);
        byBlock.forEach((site,ps)->{
            // Mixed Catherby visits finish the selected farm plots before the fruit tree.
            boolean catherbyMixed=!protectedManual&&"Catherby".equals(site)&&ps.stream().anyMatch(p->p.getPatchType()==PatchType.FRUIT_TREE);
            ps.sort(Comparator.comparingInt((FarmRunPatch p)->catherbyMixed?
                    p.getPatchType()==PatchType.HERB?-2:
                    p.getPatchType()==PatchType.ALLOTMENT||p.getPatchType()==PatchType.FLOWER||p.getPatchType()==PatchType.COMPOST_BIN?-1:localOrder(p):localOrder(p))
                .thenComparingInt(p->p.getPatchType()==PatchType.BIRDHOUSE?(reverse?-BirdhouseRun.order(p):BirdhouseRun.order(p)):0));
            ordered.add(new Group(site,ps));
        });
        if(protectedManual)
        {
            ordered.sort(Comparator.comparingInt(g->protectedRank(g.site)));
            return ordered;
        }
        final List<String> ref=reference;
        ordered.sort(Comparator.comparingInt((Group g)->{
            int n=ref.indexOf(g.site);
            if(n>=0)return n;
            int m=MIXED.indexOf(g.site);return ref.size()+(m<0?MIXED.size():m);
        }).thenComparing(g->g.site));
        Group guild=ordered.stream().filter(g->"Farming Guild".equals(g.site)).findFirst().orElse(null);
        // If Bank was chosen, prefer an actually nearby first block, not a guessed long-distance teleport.
        if(!guildFirst&&guild!=null){ordered.remove(guild);ordered.add(guild);}
        if(!guildFirst&&start!=null)
        {
            Group near=ordered.stream().filter(g->!terminal(g)&&g.entry()!=null&&proxy(start,g.entry())<70)
                .min(Comparator.comparingDouble(g->proxy(start,g.entry()))).orElse(null);
            if(near!=null){ordered.remove(near);ordered.add(0,near);}
            else if(guild!=null){ordered.remove(guild);ordered.add(Math.min(1,ordered.size()),guild);}
        }
        // Preserve explicit selected pairs even when initial proximity or saplings promote a site.
        pair(ordered,"South Falador","Draynor Manor");
        pair(ordered,"Falador Park","Taverley");
        // Entrana comes before Fossil Island, both after ordinary surface work.
        ordered.sort(Comparator.comparingInt(g->terminalOrder(g.site)));
        return ordered;
    }
    private static void pair(List<Group> ordered,String first,String second)
    {
        Group a=ordered.stream().filter(g->first.equals(g.site)).findFirst().orElse(null);
        Group b=ordered.stream().filter(g->second.equals(g.site)).findFirst().orElse(null);
        if(a==null||b==null)return;
        int at=Math.min(ordered.indexOf(a),ordered.indexOf(b));
        ordered.remove(a);ordered.remove(b);
        ordered.add(at,a);ordered.add(at+1,b);
    }
    private static boolean terminal(Group g){return terminalOrder(g.site)>0;}
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
