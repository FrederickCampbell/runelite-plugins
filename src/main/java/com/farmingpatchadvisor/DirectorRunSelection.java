package com.farmingpatchadvisor;

import java.util.EnumSet;
import java.util.Set;

/** The guide's run modes, mapping back to Advisor's existing route filters. */
enum DirectorRunSelection
{
    HERBS("Herbs", FarmRunFilter.HERB, PatchType.HERB),
    ALLOTMENTS("Allotments", FarmRunFilter.ALLOTMENT_FLOWER, PatchType.ALLOTMENT),
    FLOWERS("Flowers", FarmRunFilter.ALLOTMENT_FLOWER, PatchType.FLOWER),
    TREES("Trees", FarmRunFilter.TREE, PatchType.TREE),
    FRUIT_TREES("Fruit trees & calquats", FarmRunFilter.FRUIT_TREE_CALQUAT,
        PatchType.FRUIT_TREE, PatchType.CALQUAT),
    HARDWOODS("Hardwoods", FarmRunFilter.HARDWOOD_TREE, PatchType.HARDWOOD_TREE),
    HOPS("Hops", FarmRunFilter.HOPS, PatchType.HOPS),
    BUSHES("Bushes", FarmRunFilter.BUSH, PatchType.BUSH),
    CACTUS("Cactus", FarmRunFilter.CACTUS, PatchType.CACTUS),
    SPECIAL("Special patches", FarmRunFilter.SPECIALTY, PatchType.MUSHROOM,
        PatchType.BELLADONNA, PatchType.SPIRIT_TREE, PatchType.SEAWEED,
        PatchType.GRAPEVINE, PatchType.CELASTRUS, PatchType.REDWOOD,
        PatchType.HESPORI, PatchType.ANIMA, PatchType.CRYSTAL_TREE,
        PatchType.CORAL),
    CORE("Herb + allotment + flower", FarmRunFilter.ALL,
        PatchType.HERB, PatchType.ALLOTMENT, PatchType.FLOWER),
    TREE_COMBO("All tree types", FarmRunFilter.ALL,
        PatchType.TREE, PatchType.FRUIT_TREE, PatchType.CALQUAT,
        PatchType.HARDWOOD_TREE, PatchType.SPIRIT_TREE, PatchType.CELASTRUS,
        PatchType.REDWOOD, PatchType.CRYSTAL_TREE),
    ALL("All enabled patches", FarmRunFilter.ALL, PatchType.values());

    private final String label;
    final FarmRunFilter filter;
    private final Set<PatchType> types;

    DirectorRunSelection(String label, FarmRunFilter filter, PatchType... types)
    {
        this.label = label;
        this.filter = filter;
        this.types = EnumSet.noneOf(PatchType.class);
        java.util.Collections.addAll(this.types, types);
    }

    boolean includes(PatchType type)
    {
        return types.contains(type);
    }

    @Override
    public String toString()
    {
        return label;
    }
}
