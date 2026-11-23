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
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.NpcID;

/** Exact Quest Helper TreeRun and HerbRun ObjectStep/NpcStep definitions.
 * Adapted from Zoinkwiz/quest-helper @ b9b93706618a7b7fe7e5ea30e9a9ef35ba09673e.
 * Patch coordinates stay in Waypoints for travel. Identity is separate
 * from display names, crop state and proximity. */
final class QuestTargets
{
    static final class Definition
    {
        final int objectId, gardenerId;
        Definition(int objectId, int gardenerId) { this.objectId = objectId; this.gardenerId = gardenerId; }
    }
    private static final Map<WorldPoint, Definition> BY_POINT = new HashMap<>();
    private static final java.util.Set<Integer> OBJECT_IDS = new java.util.HashSet<>();
    static
    {
        add(3193, 3231, 0, ObjectID.FARMING_TREE_PATCH_4, NpcID.FARMING_GARDENER_TREE_4);
        add(3004, 3373, 0, ObjectID.FARMING_TREE_PATCH_2, NpcID.FARMING_GARDENER_TREE_2);
        add(2936, 3438, 0, ObjectID.FARMING_TREE_PATCH_1, NpcID.FARMING_GARDENER_TREE_1);
        add(3229, 3459, 0, ObjectID.FARMING_TREE_PATCH_3, NpcID.FARMING_GARDENER_TREE_3_02);
        add(2436, 3415, 0, ObjectID.FARMING_TREE_PATCH_5, NpcID.FARMING_GARDENER_TREE_GNOME);
        add(1232, 3736, 0, ObjectID.FARMING_TREE_PATCH_6, NpcID.FARMING_GARDENER_FARMGUILD_T2);
        add(1367, 3322, 0, ObjectID.FARMING_TREE_PATCH_7, NpcID.FARMING_GARDENER_TREE_7);
        add(2476, 3446, 0, ObjectID.FARMING_FRUIT_TREE_PATCH_1, NpcID.FARMING_GARDENER_FRUIT_1);
        add(2490, 3180, 0, ObjectID.FARMING_FRUIT_TREE_PATCH_2, NpcID.FARMING_GARDENER_FRUIT_2);
        add(2765, 3213, 0, ObjectID.FARMING_FRUIT_TREE_PATCH_3, NpcID.GARTH);
        add(2860, 3433, 0, ObjectID.FARMING_FRUIT_TREE_PATCH_4, NpcID.FARMING_GARDENER_FRUIT_4);
        add(2347, 3162, 0, ObjectID.FARMING_FRUIT_TREE_PATCH_5, NpcID.FARMING_GARDENER_FRUIT_TREE_5);
        add(1242, 3758, 0, ObjectID.FARMING_FRUIT_TREE_PATCH_6, NpcID.FARMING_GARDENER_FARMGUILD_T3);
        add(1350, 3057, 0, ObjectID.FARMING_FRUIT_TREE_PATCH_7, NpcID.FARMING_GARDENER_FRUIT_7);
        add(2795, 3102, 0, ObjectID.FARMING_CALQUAT_TREE_PATCH, NpcID.FARMING_GARDENER_CALQUAT);
        add(1366, 3033, 0, ObjectID.FARMING_CALQUAT_TREE_PATCH_2, NpcID.FARMING_GARDENER_CALQUAT_2);
        add(3129, 2406, 0, ObjectID.FARMING_CALQUAT_TREE_PATCH_3, NpcID.FARMING_GARDENER_CALQUAT_3);
        add(3702, 3837, 0, ObjectID.FARMING_HARDWOOD_TREE_PATCH_3, NpcID.FOSSIL_SQUIRREL_GARDENER3);
        add(3708, 3833, 0, ObjectID.FARMING_HARDWOOD_TREE_PATCH_2, NpcID.FOSSIL_SQUIRREL_GARDENER2);
        add(3715, 3835, 0, ObjectID.FARMING_HARDWOOD_TREE_PATCH_1, NpcID.FOSSIL_SQUIRREL_GARDENER1);
        add(1687, 2972, 0, ObjectID.FARMING_HARDWOOD_TREE_PATCH_4, NpcID.FROG_QUEST_MARCELLUS_FARMER);
        add(2470, 2704, 0, ObjectID.FARMING_HARDWOOD_TREE_PATCH_5, NpcID.FARMING_GARDENER_HARDWOOD_TREE_5);
        add(2670, 3374, 0, ObjectID.FARMING_HERB_PATCH_3, -1);
        add(2813, 3463, 0, ObjectID.FARMING_HERB_PATCH_2, -1);
        add(3058, 3311, 0, ObjectID.FARMING_HERB_PATCH_1, -1);
        add(1738, 3550, 0, ObjectID.FARMING_HERB_PATCH_6, -1);
        add(1238, 3726, 0, ObjectID.FARMING_HERB_PATCH_7, -1);
        add(3789, 2837, 0, ObjectID.FARMING_HERB_PATCH_5, -1);
        add(3605, 3529, 0, ObjectID.FARMING_HERB_PATCH_4, -1);
        add(2826, 3694, 0, ObjectID.MYARM_HERBPATCH, -1);
        add(2848, 3934, 0, ObjectID.MY2ARM_HERBPATCH, -1);
        add(1582, 3094, 0, ObjectID.FARMING_HERB_PATCH_8, -1);
    }
    private static void add(int x, int y, int plane, int object, int gardener)
    {
        BY_POINT.put(new WorldPoint(x,y,plane), new Definition(object,gardener));
        OBJECT_IDS.add(object);
    }
    static Definition get(FarmRunPatch patch)
    {
        if (patch.getPatchType() != PatchType.HERB && !StepEngine.isTreeType(patch.getPatchType())) return null;
        return BY_POINT.get(Waypoints.exactPoint(patch));
    }
    static int count() { return BY_POINT.size(); }
    static boolean matches(Client client, GameObject object, Definition definition)
    {
        if (object.getId() == definition.objectId) return true;
        ObjectComposition c = client.getObjectDefinition(object.getId());
        return c != null && c.getImpostorIds() != null && c.getImpostor() != null &&
            c.getImpostor().getId() == definition.objectId;
    }
    static boolean knownObject(Client client, GameObject object)
    {
        if (OBJECT_IDS.contains(object.getId())) return true;
        ObjectComposition c = client.getObjectDefinition(object.getId());
        return c != null && c.getImpostorIds() != null && c.getImpostor() != null && OBJECT_IDS.contains(c.getImpostor().getId());
    }
    static boolean atPoint(GameObject object, WorldPoint point)
    {
        WorldPoint p = object.getWorldLocation();
        if (point == null || p.getPlane() != point.getPlane()) return false;
        int x = p.getX() - (object.sizeX()-1)/2, y = p.getY() - (object.sizeY()-1)/2;
        return point.getX() >= x && point.getX() < x+object.sizeX() &&
            point.getY() >= y && point.getY() < y+object.sizeY();
    }
}
