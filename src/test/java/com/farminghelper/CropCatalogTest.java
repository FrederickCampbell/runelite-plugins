package com.farminghelper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CropCatalogTest
{
	@Test
	public void usesActualPerPatchQuantitiesForGroupedAndNewCrops()
	{
		assertEquals(1, CropCatalog.recommend(PatchType.GRAPEVINE, 99).getQuantity());
		assertEquals(3, CropCatalog.recommend(PatchType.HOPS, 99).getQuantity());
		assertEquals(1, CropCatalog.recommend(PatchType.SEAWEED, 99).getQuantity());
		assertEquals(1, CropCatalog.recommend(PatchType.ANIMA, 99).getQuantity());
	}

	@Test
	public void recommendsHighestUnlockedCrop()
	{
		assertEquals("Potato", CropCatalog.recommend(PatchType.ALLOTMENT, 1).getName());
		assertEquals("Watermelon", CropCatalog.recommend(PatchType.ALLOTMENT, 60).getName());
		assertEquals("Snape grass", CropCatalog.recommend(PatchType.ALLOTMENT, 61).getName());
		assertEquals("Torstol", CropCatalog.recommend(PatchType.HERB, 99).getName());
		assertEquals("Flax", CropCatalog.recommend(PatchType.HOPS, 18).getName());
		assertEquals("Attas", CropCatalog.recommend(PatchType.ANIMA, 76).getName());
	}

	@Test
	public void keepsExactInventoryItemNamesSeparateFromGrowingCropNames()
	{
		assertEquals("Ranarr", CropCatalog.findByName(PatchType.HERB, "Ranarr").getName());
		assertEquals("Ranarr seed", CropCatalog.findByName(PatchType.HERB, "Ranarr").getItemName());
		assertEquals("Seaweed spore", CropCatalog.recommend(PatchType.SEAWEED, 99).getItemName());
		assertEquals("Mushroom spore", CropCatalog.recommend(PatchType.MUSHROOM, 99).getItemName());
		assertEquals("Elkhorn frag", CropCatalog.findByName(PatchType.CORAL, "Elkhorn frag").getItemName());
		assertEquals("Maple sapling", CropCatalog.findByName(PatchType.TREE, "Maple sapling").getItemName());
		assertEquals("Attas seed", CropCatalog.findByName(PatchType.ANIMA, "Attas").getItemName());
	}

	@Test
	public void cropOverridesDisplayAndResolveExactItemNames()
	{
	}

	@Test
	public void returnsNullBelowPatchRequirement()
	{
		assertNull(CropCatalog.recommend(PatchType.REDWOOD, 89));
	}

	@Test
	public void keepsCorrectPlantingQuantity()
	{
		assertEquals(3, CropCatalog.recommend(PatchType.ALLOTMENT, 99).getQuantity());
		assertEquals(3, CropCatalog.recommend(PatchType.HOPS, 99).getQuantity());
		assertEquals(1, CropCatalog.recommend(PatchType.GRAPEVINE, 99).getQuantity());
	}

	@Test
	public void findsCropBySeedItem()
	{
		Crop crop = CropCatalog.recommend(PatchType.ALLOTMENT, 1);
		assertEquals(crop, CropCatalog.findByItemId(crop.getItemId()));
		assertNull(CropCatalog.findByItemId(-1));
	}
    @Test public void allReleasedCropDefinitionsAndXpInputsMatch() throws Exception
    {
        java.util.List<String> rows=new java.util.ArrayList<>();
        for(PatchType type:PatchType.values())for(Crop c:CropCatalog.crops(type))
        {
            CropXp.Score xp=c.getScore();
            rows.add(String.join("\t",type.name(),c.getName(),c.getItemName(),""+c.getLevel(),""+c.getItemId(),""+c.getQuantity(),
                ""+xp.planting,""+xp.health,""+xp.harvest,""+xp.yield,""+xp.estimated));
        }
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/v1-crops.tsv"),
            java.nio.charset.StandardCharsets.UTF_8)))
        {
            assertEquals(reader.lines().collect(java.util.stream.Collectors.toList()),rows);
        }
    }
}
