package com.farminghelper;

import java.util.*;

/** Immutable snapshot crossing the client-thread / Swing-thread boundary. */
@lombok.EqualsAndHashCode
final class GuideView
{
    @lombok.EqualsAndHashCode
    @lombok.RequiredArgsConstructor
    static final class ItemRow
    {
        final int id;
        final String name;
        final int owned,needed,bank,vault;
        final boolean recommended;
        boolean satisfied(){return owned>=needed;}
    }
    /** Shared quantity semantics; overlays do not change native storage layouts. */
    static Set<Integer> missingItems(Map<Integer,Integer> required,Map<Integer,Integer> carried)
    {
        Set<Integer> missing=new HashSet<>();
        required.forEach((id,n)->{if(carried.getOrDefault(id,0)<n)missing.add(id);});
        return Set.copyOf(missing);
    }

    @lombok.EqualsAndHashCode
    @lombok.RequiredArgsConstructor
    static final class Stop
    {
        final FarmRunPatch patch;
        final String instruction;
        final boolean active, done;
    }
    /** Counts of existing planner decisions, not a reconstructed farming result. */
    @lombok.EqualsAndHashCode
    @lombok.RequiredArgsConstructor
    static final class Stats
    {
        static final Stats UNKNOWN=new Stats(-1,-1,-1,-1,-1,-1,0);
        final int processed,skipped,deferred,seedless,total,pending,preserved;
    }
    /** Optional help and warning text belongs to the presentation snapshot. */
    @lombok.EqualsAndHashCode
    @lombok.RequiredArgsConstructor
    static final class Notes
    {
        static final Notes EMPTY=new Notes(false,"","","");
        final boolean initial;
        final String storageWarning,preparationHelp,details;
    }
    final Notes notes;
    final Stats stats;
    final String title, message, preparation, supplyNote;
    final List<ItemRow> items;
    final List<Stop> stops;
    final FarmRunPatch active;
    final boolean hideSeedless,clearSeedless,compostStep;
    GuideView(String title,String message,List<ItemRow> items,List<Stop> stops,FarmRunPatch active,String preparation,String supplyNote,boolean hideSeedless,boolean clearSeedless,boolean compostStep,Stats stats,Notes notes)
    { this.title=title;this.message=message;this.items=List.copyOf(items);this.stops=List.copyOf(stops);this.active=active;this.preparation=preparation;this.supplyNote=supplyNote;this.hideSeedless=hideSeedless;this.clearSeedless=clearSeedless;this.compostStep=compostStep;this.stats=stats;this.notes=notes; }
}
