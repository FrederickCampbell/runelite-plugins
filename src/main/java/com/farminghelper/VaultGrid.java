package com.farminghelper;

import java.util.*;

/** Pure native-slot layout. Coordinates are visual positions, never widget indices.
 * Each section uses only positions already occupied by the native grid. */
final class VaultGrid
{
    static final class Slot
    {
        final int key,x,y,rank;
        final boolean keep;
        Slot(int key,int x,int y,boolean keep,int rank){this.key=key;this.x=x;this.y=y;this.keep=keep;this.rank=rank;}
    }
    static final class Move
    {
        final int key,x,y;
        Move(int key,int x,int y){this.key=key;this.x=x;this.y=y;}
    }
    static final class Section
    {
        final int start,end;
        final List<Slot> slots;
        Section(int start,int end,List<Slot> slots){this.start=start;this.end=end;this.slots=slots;}
    }
    static final class Layout
    {
        final List<Move> moves;
        final boolean hideHeader;
        final int saved;
        Layout(List<Move> moves,boolean hideHeader,int saved){this.moves=moves;this.hideHeader=hideHeader;this.saved=saved;}
    }
    /** Caller verifies the native grid geometry and associated headers first. */
    static Layout layout(Section section,boolean compactSections,int shift)
    {
        List<Slot> slots=new ArrayList<>(section.slots);
        slots.sort(Comparator.comparingInt((Slot s)->s.y).thenComparingInt(s->s.x));
        List<Slot> selected=new ArrayList<>();
        for(Slot s:slots)if(s.keep)selected.add(s);
        selected.sort(Comparator.comparingInt((Slot s)->s.rank).thenComparingInt(s->s.y).thenComparingInt(s->s.x));
        List<Move> moves=new ArrayList<>();
        for(int i=0;i<selected.size();i++)
        {
            Slot position=slots.get(i);
            moves.add(new Move(selected.get(i).key,position.x,position.y-shift));
        }
        if(!compactSections)return new Layout(moves,false,0);
        if(selected.isEmpty())return new Layout(moves,true,Math.max(0,section.end-section.start));
        int saved=slots.get(slots.size()-1).y-slots.get(selected.size()-1).y;
        return new Layout(moves,false,Math.max(0,saved));
    }
    private VaultGrid(){}
}
