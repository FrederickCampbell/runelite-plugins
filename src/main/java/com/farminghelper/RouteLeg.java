package com.farminghelper;

import net.runelite.api.coords.WorldPoint;

/** A conditional approach, interaction or exit. No teleport or pathfinding logic. */
final class RouteLeg
{
    enum Interaction { NONE, HESPORI_ENTER, HESPORI_EXIT, DIVE, SURFACE, CORAL_ENTER, CORAL_EXIT, BOARD, MOOR }
    final WorldPoint point;
    final String instruction;
    final Interaction interaction;
    final boolean probe;
    RouteLeg(WorldPoint point,String instruction,Interaction interaction,boolean probe)
    {this.point=point;this.instruction=instruction;this.interaction=interaction;this.probe=probe;}
    static RouteLeg walk(WorldPoint point,boolean probe) {return new RouteLeg(point,"",Interaction.NONE,probe);}
    static RouteLeg manual(String text,Interaction interaction) {return new RouteLeg(null,text,interaction,false);}
    static RouteLeg approach(WorldPoint point,String text,Interaction interaction) {return new RouteLeg(point,text,interaction,true);}
}
