package com.farminghelper;

@lombok.RequiredArgsConstructor
enum PatchType
{
	ALLOTMENT("Allotment", true, FarmRunType.ALLOTMENT_FLOWER),
	FLOWER("Flower", true, FarmRunType.ALLOTMENT_FLOWER),
	HERB("Herb", true, FarmRunType.HERB),
	HOPS("Hops", true, FarmRunType.HOPS),
	BUSH("Bush", true, FarmRunType.BUSH),
	TREE("Tree", false, FarmRunType.TREE),
	FRUIT_TREE("Fruit tree", false, FarmRunType.FRUIT_TREE_CALQUAT),
	HARDWOOD_TREE("Hardwood tree", false, FarmRunType.HARDWOOD_TREE),
	CACTUS("Cactus", true, FarmRunType.CACTUS),
	MUSHROOM("Mushroom", true, FarmRunType.SPECIALTY),
	BELLADONNA("Belladonna", true, FarmRunType.SPECIALTY),
	CALQUAT("Calquat", false, FarmRunType.FRUIT_TREE_CALQUAT),
	SPIRIT_TREE("Spirit tree", false, FarmRunType.SPECIALTY),
	SEAWEED("Seaweed", true, FarmRunType.SPECIALTY),
	GRAPEVINE("Grapevine", true, FarmRunType.SPECIALTY),
	CELASTRUS("Celastrus", false, FarmRunType.SPECIALTY),
	REDWOOD("Redwood", false, FarmRunType.SPECIALTY),
	HESPORI("Hespori", true, FarmRunType.SPECIALTY),
	ANIMA("Anima", true, FarmRunType.SPECIALTY),
	CRYSTAL_TREE("Crystal tree", false, FarmRunType.SPECIALTY),
	CORAL("Coral", true, FarmRunType.SPECIALTY),
	COMPOST_BIN("Compost", false, FarmRunType.COMPOST),
	BIRDHOUSE("Birdhouse", false, FarmRunType.BIRDHOUSE);

	@lombok.Getter private final String displayName;
	private final boolean usesDibber;
	@lombok.Getter private final FarmRunType runType;

	boolean usesDibber()
	{
		return usesDibber;
	}

	boolean canBeWatered()
	{
		return this == ALLOTMENT || this == FLOWER || this == HOPS;
	}

    boolean isTree()
    {
        switch(this)
        {
            case TREE: case FRUIT_TREE: case HARDWOOD_TREE: case CALQUAT: case SPIRIT_TREE:
            case REDWOOD: case CELASTRUS: case CRYSTAL_TREE:return true;
            default:return false;
        }
    }

    boolean supportsProtection()
    {
        return (isTree() && this!=CRYSTAL_TREE) || this==CORAL || this==SEAWEED ||
            this==ALLOTMENT || this==HOPS || this==BUSH || this==CACTUS;
    }
	boolean usesCompost()
	{
		return this != GRAPEVINE && this != HESPORI && this != ANIMA && this != CORAL && this != COMPOST_BIN && this != BIRDHOUSE;
	}
}
