/*
 * Copyright (c) 2024, Zoinkwiz <https://github.com/Zoinkwiz/>
 * Copyright (c) 2024, Kerpackie <https://github.com/Kerpackie/>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.farminghelper;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import java.util.HashSet;
import java.util.Set;

/** Exact Quest Helper TreeRun and HerbRun ObjectStep/NpcStep definitions.
 * Adapted from Zoinkwiz/quest-helper @ b9b93706618a7b7fe7e5ea30e9a9ef35ba09673e.
 * Travel and exact-object verification use the same plot coordinates. */
final class QuestTargets
{
    static final class Definition
    {
        final int objectId;
        final WorldPoint point;
        Definition(int objectId,WorldPoint point){this.objectId=objectId;this.point=point;}
    }
    private static final Map<String,Definition> BY_PATCH=new HashMap<>();
    private static final Set<Integer> OBJECTS=new HashSet<>();
    static
    {
        add(PatchType.TREE, "Lumbridge", 3193, 3231, ObjectID.FARMING_TREE_PATCH_4);
        add(PatchType.TREE, "Falador", 3004, 3373, ObjectID.FARMING_TREE_PATCH_2);
        add(PatchType.TREE, "Taverley", 2936, 3438, ObjectID.FARMING_TREE_PATCH_1);
        add(PatchType.TREE, "Varrock", 3229, 3459, ObjectID.FARMING_TREE_PATCH_3);
        add(PatchType.TREE, "Gnome Stronghold", 2436, 3415, ObjectID.FARMING_TREE_PATCH_5);
        add(PatchType.TREE, "Farming Guild", 1232, 3736, ObjectID.FARMING_TREE_PATCH_6);
        add(PatchType.TREE, "Auburnvale", 1367, 3322, ObjectID.FARMING_TREE_PATCH_7);
        add(PatchType.FRUIT_TREE, "Gnome Stronghold", 2476, 3446, ObjectID.FARMING_FRUIT_TREE_PATCH_1);
        add(PatchType.FRUIT_TREE, "Tree Gnome Village", 2490, 3180, ObjectID.FARMING_FRUIT_TREE_PATCH_2);
        add(PatchType.FRUIT_TREE, "Brimhaven", 2765, 3213, ObjectID.FARMING_FRUIT_TREE_PATCH_3);
        add(PatchType.FRUIT_TREE, "Catherby", 2860, 3433, ObjectID.FARMING_FRUIT_TREE_PATCH_4);
        add(PatchType.FRUIT_TREE, "Lletya", 2347, 3162, ObjectID.FARMING_FRUIT_TREE_PATCH_5);
        add(PatchType.FRUIT_TREE, "Farming Guild", 1242, 3758, ObjectID.FARMING_FRUIT_TREE_PATCH_6);
        add(PatchType.FRUIT_TREE, "Kastori", 1350, 3057, ObjectID.FARMING_FRUIT_TREE_PATCH_7);
        add(PatchType.CALQUAT, "Tai Bwo Wannai", 2795, 3102, ObjectID.FARMING_CALQUAT_TREE_PATCH);
        add(PatchType.CALQUAT, "Kastori", 1366, 3033, ObjectID.FARMING_CALQUAT_TREE_PATCH_2);
        add(PatchType.CALQUAT, "Great Conch", 3129, 2406, ObjectID.FARMING_CALQUAT_TREE_PATCH_3);
        add(PatchType.HARDWOOD_TREE, "Fossil Island~West", 3702, 3837, ObjectID.FARMING_HARDWOOD_TREE_PATCH_3);
        add(PatchType.HARDWOOD_TREE, "Fossil Island~Middle", 3708, 3833, ObjectID.FARMING_HARDWOOD_TREE_PATCH_2);
        add(PatchType.HARDWOOD_TREE, "Fossil Island~East", 3715, 3835, ObjectID.FARMING_HARDWOOD_TREE_PATCH_1);
        add(PatchType.HARDWOOD_TREE, "Avium Savannah", 1687, 2972, ObjectID.FARMING_HARDWOOD_TREE_PATCH_4);
        add(PatchType.HARDWOOD_TREE, "Anglers' Retreat", 2470, 2704, ObjectID.FARMING_HARDWOOD_TREE_PATCH_5);
        add(PatchType.HERB, "Ardougne", 2670, 3374, ObjectID.FARMING_HERB_PATCH_3);
        add(PatchType.HERB, "Catherby", 2813, 3463, ObjectID.FARMING_HERB_PATCH_2);
        add(PatchType.HERB, "Falador", 3058, 3311, ObjectID.FARMING_HERB_PATCH_1);
        add(PatchType.HERB, "Kourend", 1738, 3550, ObjectID.FARMING_HERB_PATCH_6);
        add(PatchType.HERB, "Farming Guild", 1238, 3726, ObjectID.FARMING_HERB_PATCH_7);
        add(PatchType.HERB, "Harmony Island", 3789, 2837, ObjectID.FARMING_HERB_PATCH_5);
        add(PatchType.HERB, "Morytania", 3605, 3529, ObjectID.FARMING_HERB_PATCH_4);
        add(PatchType.HERB, "Troll Stronghold", 2826, 3694, ObjectID.MYARM_HERBPATCH);
        add(PatchType.HERB, "Weiss", 2848, 3934, ObjectID.MY2ARM_HERBPATCH);
        add(PatchType.HERB, "Civitas illa Fortis", 1582, 3094, ObjectID.FARMING_HERB_PATCH_8);
    }
    private static void add(PatchType type,String plot,int x,int y,int object)
    {
        BY_PATCH.put(type+"~"+plot,new Definition(object,new WorldPoint(x,y,0)));
        OBJECTS.add(object);
    }
    static boolean known(int id){return OBJECTS.contains(id);}
    static Definition get(FarmRunPatch patch)
    {
        String plot=patch.getLocation();
        if(patch.getPatchType()==PatchType.HARDWOOD_TREE && "Fossil Island".equals(plot))plot+="~"+patch.getName();
        return BY_PATCH.get(patch.getPatchType()+"~"+plot);
    }
}
