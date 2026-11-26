package com.farminghelper;

enum FarmRunType
{
	ALLOTMENT_FLOWER("Allotment & Flower Run"),
	HERB("Herb Run"),
	TREE("Tree Run"),
	FRUIT_TREE_CALQUAT("Fruit Tree & Calquat Run"),
	HARDWOOD_TREE("Hardwood Tree Run"),
	HOPS("Hops Run"),
	BUSH("Bush Run"),
	CACTUS("Cactus Run"),
	SPECIALTY("Specialty Patch Run"),
	COMPOST("Compost Run"),
	BIRDHOUSE("Birdhouse Run");

	private final String displayName;

	FarmRunType(String displayName)
	{
		this.displayName = displayName;
	}

	String getDisplayName()
	{
		return displayName;
	}

	static FarmRunType forPatchType(PatchType patchType)
	{
		return patchType.getRunType();
	}
}
