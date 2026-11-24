package com.farminghelper;

import net.runelite.api.coords.WorldPoint;
import static com.farminghelper.RouteLeg.Interaction.*;

/** Transport legs from Shortest Path boats/transports.tsv and RuneLite MooringLocation.
 * Interior patch approach is derived from actual observed objects, not the world-map icon. */
final class SpecialRoutes
{
    static final WorldPoint HESPORI_ENTRANCE=new WorldPoint(1230,3730,0); // approximate; resolve cave object locally
    static final WorldPoint SEAWEED_ISLAND=new WorldPoint(3764,3899,0);
    static final WorldPoint CORAL_ENTRANCE=new WorldPoint(3271,2463,0);
    static final WorldPoint CORAL_EXIT_POINT=new WorldPoint(3288,8868,1);
    static final WorldPoint ANGLER_ROWBOAT=new WorldPoint(2543,2844,0);
    static final WorldPoint CORSAIR_MOORING=new WorldPoint(2579,2843,0);
    static final WorldPoint ANGLER_MOORING=new WorldPoint(2467,2721,0);
    static final int ANGLER_ROWBOAT_VARBIT=18355;
    static boolean insideHespori(WorldPoint p){return p!=null && p.getRegionID()==5021;}
    static boolean insideSeaweed(WorldPoint p){return p!=null && p.getRegionID()==15008;}
    static boolean insideCoral(WorldPoint p){return p!=null && p.getRegionID()==13194;}
    static boolean onAnglers(WorldPoint p){return p!=null && p.getRegionID()==9770;}
    static boolean anglers(FarmRunPatch p){return p!=null && "Anglers' Retreat".equals(p.getLocation());}
    static boolean isType(FarmRunPatch p,PatchType type){return p!=null && p.getPatchType()==type;}
    static RouteLeg leg(FarmRunPatch patch,WorldPoint player,boolean aboard,boolean rowboat,boolean instance,WorldPoint liveApproach)
    {
        if(insideHespori(player))
        {
            if(!isType(patch,PatchType.HESPORI))return RouteLeg.manual("Leave the Hespori cave through its exit.",HESPORI_EXIT);
            return RouteLeg.walk(null,false); // instance: local object guidance only
        }
        if(insideSeaweed(player))
        {
            if(!isType(patch,PatchType.SEAWEED))return RouteLeg.manual("Use the underwater exit to resurface at the bank island.",SURFACE);
            return RouteLeg.walk(instance?null:liveApproach,true);
        }
        if(insideCoral(player))
        {
            if(!isType(patch,PatchType.CORAL))return player.getPlane()==1 && !instance
                ? RouteLeg.approach(CORAL_EXIT_POINT,"Climb the steps to leave the Coral Nurseries.",CORAL_EXIT)
                : RouteLeg.manual("Return to the nursery exit steps and climb out.",CORAL_EXIT);
            return RouteLeg.walk(instance?null:liveApproach,true);
        }
        if(patch==null)return RouteLeg.walk(null,false);
        if(aboard)
        {
            if(anglers(patch))return new RouteLeg(ANGLER_MOORING,"Sail to Anglers' Retreat, moor and disembark.",MOOR,false);
            return RouteLeg.manual("Moor and disembark before continuing to the next farm.",MOOR);
        }
        if(instance)return RouteLeg.manual("Leave the current instance to resume travel.",NONE);
        if(isType(patch,PatchType.HESPORI))return RouteLeg.approach(HESPORI_ENTRANCE,"Enter the cave beside Arno in the west wing.",HESPORI_ENTER);
        if(isType(patch,PatchType.SEAWEED))return RouteLeg.approach(SEAWEED_ISLAND,"Travel to the bank island and choose Dive on the rowboat.",DIVE);
        if(isType(patch,PatchType.CORAL))return RouteLeg.approach(CORAL_ENTRANCE,"Climb down the steps at the eastern dock. Bring diving equipment or a Medallion of the deep.",CORAL_ENTER);
        if(anglers(patch) && !onAnglers(player))
        {
            if(!rowboat)return RouteLeg.approach(CORSAIR_MOORING,"Board your boat at Corsair Cove, sail to Anglers' Retreat and moor. Build the rowboat utility for future visits.",BOARD);
            return RouteLeg.approach(Waypoints.destination(patch),"Travel via the unlocked Anglers' Retreat rowboat.",NONE);
        }
        // If leaving the island without the permanent rowboat, do not ask a land pathfinder to cross sea.
        if(onAnglers(player) && !anglers(patch) && !rowboat)
            return RouteLeg.approach(ANGLER_MOORING,"Board your boat, sail to a mainland port and disembark.",BOARD);
        WorldPoint target=liveApproach==null?Waypoints.destination(patch):liveApproach;
        return RouteLeg.walk(target,QuestTargets.get(patch)==null || liveApproach!=null);
    }
}
