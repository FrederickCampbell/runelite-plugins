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
        String signature() { return id+":"+name+":"+owned+":"+needed+":"+bank+":"+vault+":"+recommended; }
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
    { this.title=title;this.message=message;this.items=List.copyOf(items);this.stops=List.copyOf(stops);this.active=active;this.preparation=preparation;this.supplyNote=supplyNote;this.hideSeedless=hideSeedless;this.clearSeedless=clearSeedless;this.compostStep=compostStep; }
    String signature()
    {
        StringBuilder b=new StringBuilder(title).append('|').append(message).append('|').append(preparation).append('|').append(supplyNote).append('|').append(hideSeedless).append('|').append(clearSeedless).append('|').append(compostStep);
        for(ItemRow r:items)b.append('|').append(r.signature());
        for(Stop s:stops)b.append('|').append(s.signature());
        return b.toString();
    }
}
