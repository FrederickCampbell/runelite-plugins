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
        final int section; // 0 ordinary, 1 Guild, 2 external, 3 birdhouse reference
        ItemRow(int id,String name,int owned,int needed,int bank,int vault,boolean recommended)
        {this(id,name,owned,needed,bank,vault,recommended,0);}
        boolean satisfied(){return owned>=needed;}
    }
    /** Partition already-allocated quantities, reserving carried stock for local work first. */
    static List<ItemRow> guildSections(List<ItemRow> rows,Map<Integer,Integer> local)
    {
        List<ItemRow> result=new ArrayList<>(rows.size()+local.size());
        for(ItemRow row:rows)
        {
            if(row.recommended||row.section==3){result.add(row);continue;}
            int n=Math.min(row.needed,Math.max(0,local.getOrDefault(row.id,0)));
            int held=Math.min(n,row.owned),fromBank=Math.min(Math.max(0,n-held),row.bank);
            int fromVault=Math.min(Math.max(0,n-held-fromBank),row.vault);
            if(n>0)result.add(new ItemRow(row.id,row.name,held,n,fromBank,fromVault,false,1));
            if(row.needed>n)result.add(new ItemRow(row.id,row.name,Math.max(0,row.owned-held),row.needed-n,row.bank-fromBank,row.vault-fromVault,false,2));
        }
        return result;
    }
    /** Shared quantity semantics; overlays do not change native storage layouts. */
    static Set<Integer> missingItems(Map<Integer,Integer> required,Map<Integer,Integer> carried)
    {
        return missingItems(required,carried,Map.of());
    }
    static Set<Integer> missingItems(Map<Integer,Integer> required,Map<Integer,Integer> carried,Map<Integer,Integer> noteSources)
    {
        Set<Integer> missing=new HashSet<>();
        required.forEach((id,n)->{if(carried.getOrDefault(id,0)<n){missing.add(id);Integer base=noteSources.get(id);if(base!=null)missing.add(base);}});
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
        final boolean seedPreparation;
        final boolean guildSplit;
        final Set<Integer> preparationItems;
        Notes(boolean initial,String storageWarning,String preparationHelp,String details)
        {this(initial,storageWarning,preparationHelp,details,false,false,Set.of());}
        Notes(boolean initial,String storageWarning,String preparationHelp,String details,boolean seedPreparation,Set<Integer> preparationItems)
        {this(initial,storageWarning,preparationHelp,details,seedPreparation,false,preparationItems);}
    }
    final Notes notes;
    final Stats stats;
    final String title, message, preparation, supplyNote;
    final List<ItemRow> items;
    final List<Stop> stops;
    final FarmRunPatch active;
    final boolean hideSeedless,clearSeedless,compostStep,waterStep;
    GuideView(String title,String message,List<ItemRow> items,List<Stop> stops,FarmRunPatch active,String preparation,String supplyNote,boolean hideSeedless,boolean clearSeedless,boolean compostStep,Stats stats,Notes notes)
    {this(title,message,items,stops,active,preparation,supplyNote,hideSeedless,clearSeedless,compostStep,false,stats,notes);}
    GuideView(String title,String message,List<ItemRow> items,List<Stop> stops,FarmRunPatch active,String preparation,String supplyNote,boolean hideSeedless,boolean clearSeedless,boolean compostStep,boolean waterStep,Stats stats,Notes notes)
    { this.title=title;this.message=message;this.items=List.copyOf(items);this.stops=List.copyOf(stops);this.active=active;this.preparation=preparation;this.supplyNote=supplyNote;this.hideSeedless=hideSeedless;this.clearSeedless=clearSeedless;this.compostStep=compostStep;this.waterStep=waterStep;this.stats=stats;this.notes=notes; }
}
