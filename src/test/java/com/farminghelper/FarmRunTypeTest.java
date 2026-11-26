package com.farminghelper;

import java.util.EnumSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FarmRunTypeTest
{
	@Test
	public void categorizesEverySupportedPatchType()
	{
		EnumSet<PatchType> categorized = EnumSet.noneOf(PatchType.class);
		for (PatchType patchType : PatchType.values())
		{
			patchType.getRunType();
			categorized.add(patchType);
		}
		assertEquals(EnumSet.allOf(PatchType.class), categorized);
	}

	@Test
	public void usesExpectedFarmRunCategories()
	{
		assertEquals(FarmRunType.ALLOTMENT_FLOWER, PatchType.ALLOTMENT.getRunType());
		assertEquals(FarmRunType.ALLOTMENT_FLOWER, PatchType.FLOWER.getRunType());
		assertEquals(FarmRunType.HERB, PatchType.HERB.getRunType());
		assertEquals(FarmRunType.FRUIT_TREE_CALQUAT, PatchType.CALQUAT.getRunType());
		assertEquals(FarmRunType.SPECIALTY, PatchType.SEAWEED.getRunType());
		assertEquals(FarmRunType.SPECIALTY, PatchType.ANIMA.getRunType());
	}

	@Test
	public void usesCorrectCareFlagsForEveryPatchType()
	{
		EnumSet<PatchType> compostAndWater = EnumSet.of(
			PatchType.ALLOTMENT,
			PatchType.FLOWER,
			PatchType.HOPS);
		EnumSet<PatchType> compostOnly = EnumSet.of(
			PatchType.HERB,
			PatchType.BUSH,
			PatchType.TREE,
			PatchType.FRUIT_TREE,
			PatchType.HARDWOOD_TREE,
			PatchType.CACTUS,
			PatchType.MUSHROOM,
			PatchType.BELLADONNA,
			PatchType.CALQUAT,
			PatchType.SPIRIT_TREE,
			PatchType.SEAWEED,
			PatchType.CELASTRUS,
			PatchType.REDWOOD,
			PatchType.CRYSTAL_TREE);
		EnumSet<PatchType> noCareFlags = EnumSet.of(
			PatchType.GRAPEVINE,
			PatchType.HESPORI,
			PatchType.ANIMA,
			PatchType.CORAL, PatchType.COMPOST_BIN, PatchType.BIRDHOUSE);

		EnumSet<PatchType> audited = EnumSet.noneOf(PatchType.class);
		audited.addAll(compostAndWater);
		audited.addAll(compostOnly);
		audited.addAll(noCareFlags);
		assertEquals(EnumSet.allOf(PatchType.class), audited);

		for (PatchType patchType : PatchType.values())
		{
			assertEquals(compostAndWater.contains(patchType), patchType.canBeWatered());
			assertEquals(compostAndWater.contains(patchType) || compostOnly.contains(patchType),
				patchType.usesCompost());
		}
	}
}
