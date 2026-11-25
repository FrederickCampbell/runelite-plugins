package com.farminghelper;

import java.util.*;

/** Immutable snapshot crossing the client-thread / Swing-thread boundary. */
final class GuideView
{
    static final class ItemRow
    {
        final int id, owned, needed, bank, vault;
        final String name;
        final boolean recommended;
        ItemRow(int id, String name, int owned, int needed, int bank, int vault, boolean recommended)
        {
            this.id=id; this.name=name; this.owned=owned; this.needed=needed;
            this.bank=bank; this.vault=vault; this.recommended=recommended;
        }
        boolean satisfied(){return owned>=needed;}
        String signature() { return id+":"+name+":"+owned+":"+needed+":"+bank+":"+vault+":"+recommended; }
    }
    /** Shared quantity semantics; overlays do not change native storage layouts. */
    static Set<Integer> missingItems(Map<Integer,Integer> required,Map<Integer,Integer> carried)
    {
        Set<Integer> missing=new HashSet<>();
        required.forEach((id,n)->{if(carried.getOrDefault(id,0)<n)missing.add(id);});
        return Set.copyOf(missing);
    }

    static final class Stop
    {
        final FarmRunPatch patch;
        final String instruction;
        final boolean active, done;
        Stop(FarmRunPatch patch,String instruction,boolean active,boolean done)
        { this.patch=patch; this.instruction=instruction; this.active=active; this.done=done; }
        String signature() { return patch.key()+":"+instruction+":"+active+":"+done; }
    }
    /** Counts of existing planner decisions, not a reconstructed farming result. */
    static final class Stats
    {
        static final Stats UNKNOWN=new Stats(-1,-1,-1,-1);
        final int processed,skipped,deferred,seedless,total,pending,preserved;
        Stats(int processed,int skipped,int deferred,int seedless){this(processed,skipped,deferred,seedless,-1,-1,0);}
        Stats(int processed,int skipped,int deferred,int seedless,int total,int pending,int preserved){this.processed=processed;this.skipped=skipped;this.deferred=deferred;this.seedless=seedless;this.total=total;this.pending=pending;this.preserved=preserved;}
        String signature(){return processed+":"+skipped+":"+deferred+":"+seedless+":"+total+":"+pending+":"+preserved;}
    }
    final Stats stats;
    final String title, message, preparation, supplyNote;
    final List<ItemRow> items;
    final List<Stop> stops;
    final FarmRunPatch active;
    final boolean hideSeedless,clearSeedless,compostStep;
    GuideView(String title,String message,List<ItemRow> items,List<Stop> stops,FarmRunPatch active)
    { this(title,message,items,stops,active,"",""); }
    GuideView(String title,String message,List<ItemRow> items,List<Stop> stops,FarmRunPatch active,String preparation,String supplyNote)
    {this(title,message,items,stops,active,preparation,supplyNote,false,false,false);}
    GuideView(String title,String message,List<ItemRow> items,List<Stop> stops,FarmRunPatch active,String preparation,String supplyNote,boolean hideSeedless,boolean clearSeedless,boolean compostStep)
    {this(title,message,items,stops,active,preparation,supplyNote,hideSeedless,clearSeedless,compostStep,Stats.UNKNOWN);}
    GuideView(String title,String message,List<ItemRow> items,List<Stop> stops,FarmRunPatch active,String preparation,String supplyNote,boolean hideSeedless,boolean clearSeedless,boolean compostStep,Stats stats)
    { this.title=title;this.message=message;this.items=List.copyOf(items);this.stops=List.copyOf(stops);this.active=active;this.preparation=preparation;this.supplyNote=supplyNote;this.hideSeedless=hideSeedless;this.clearSeedless=clearSeedless;this.compostStep=compostStep;this.stats=stats; }
    String signature()
    {
        StringBuilder b=new StringBuilder(title).append('|').append(message).append('|').append(preparation).append('|').append(supplyNote).append('|').append(hideSeedless).append('|').append(clearSeedless).append('|').append(compostStep).append('|').append(stats.signature());
        for(ItemRow r:items)b.append('|').append(r.signature());
        for(Stop s:stops)b.append('|').append(s.signature());
        return b.toString();
    }
}
