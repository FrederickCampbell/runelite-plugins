package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class SupplyPolishTest
{
    private static final Crop PALM=CropCatalog.findByName(PatchType.FRUIT_TREE,"Palm sapling");
    private static final int SAPLING=PALM.getItemId(),RAW=100001,DRY=100002,WET=100003;
    private static final IntFunction<String> NAMES=id->id==RAW?"Palm tree seed":id==DRY?"Palm seedling":id==WET?"Palm seedling (w)":"";
    private static Map<FarmRunPatch,Crop> plan(int count)
    {
        Map<FarmRunPatch,Crop> result=new LinkedHashMap<>();
        for(int i=0;i<count;i++)result.put(new FarmRunPatch("Test",""+i,PatchType.FRUIT_TREE),PALM);
        return result;
    }

    @Test public void wateredAndDryAliasesUseExactSpecies()
    {
        assertEquals(PALM,VaultRanking.resolve(WET,"Palm seedling (w)"));
        assertEquals(PALM,VaultRanking.resolve(WET,"Palm seedling(w)"));
        assertEquals(PALM,VaultRanking.resolve(DRY,"Palm seedling"));
        assertNull(VaultRanking.resolve(WET,"Unrelated palm seedling (w)"));
        Map<Integer,Integer> stock=Map.of(SAPLING,3,WET,2,RAW,20);
        Map<Integer,Integer> aliases=TreePreparation.aliases(stock,NAMES);
        Map<Integer,Integer> prep=TreePreparation.preparation(plan(5),stock,aliases,Map.of(SAPLING,3,WET,2),NAMES);
        assertEquals(Map.of(WET,2),prep);
        assertEquals(0,TreePreparation.count(prep,aliases,NAMES,TreePreparation.Stage.SEED));
        assertEquals(0,TreePreparation.count(prep,aliases,NAMES,TreePreparation.Stage.SEEDLING));
        assertEquals(2,TreePreparation.count(prep,aliases,NAMES,TreePreparation.Stage.WATERED));
        assertEquals(Map.of(TreePreparation.Stage.WATERED,2),TreePreparation.counts(prep,aliases,NAMES));
        assertEquals("Wait for 2 seedling(s) to become plantable saplings.",TreePreparation.instruction(0,0,2));
        Map<Integer,Integer> pending=TreePreparation.pending(prep,aliases);
        Map<Integer,Integer> physical=TreePreparation.physicalRequirements(Map.of(SAPLING,5,WET,2),pending);
        assertEquals(Map.of(SAPLING,3,WET,2),physical);
        assertEquals(Map.of(SAPLING,2),pending);
        assertTrue(GuideView.missingItems(physical,Map.of(SAPLING,3,WET,2)).isEmpty());
        assertEquals(Set.of(WET),GuideView.missingItems(physical,Map.of(SAPLING,3,WET,1)));
        assertFalse(physical.containsKey(RAW));
    }

    @Test public void carriedSeedlingsBeforeBankMatureThenStoredStages()
    {
        Map<Integer,Integer> stock=Map.of(SAPLING,2,WET,2,DRY,2,RAW,10);
        Map<Integer,Integer> aliases=TreePreparation.aliases(stock,NAMES);
        assertEquals(Map.of(WET,1),TreePreparation.preparation(plan(3),stock,aliases,Map.of(WET,1),NAMES));
        assertEquals(Map.of(WET,2,DRY,1),TreePreparation.preparation(plan(5),stock,aliases,Map.of(),NAMES));
        assertEquals(Map.of(WET,2,DRY,2,RAW,1),TreePreparation.preparation(plan(7),stock,aliases,Map.of(),NAMES));
        assertEquals(Integer.valueOf(16),TreePreparation.allocationStock(stock,aliases).get(SAPLING));
    }

    @Test public void materialAccountingDoesNotTurnSeedlingsIntoPlantableSaplings()
    {
        Map<Integer,Integer> bag=Map.of(WET,2);
        Map<Integer,Integer> aliases=TreePreparation.aliases(bag,NAMES);
        Map<Integer,Integer> prep=TreePreparation.preparation(plan(2),bag,aliases,bag,NAMES);
        assertEquals(Map.of(SAPLING,2),TreePreparation.pending(prep,aliases));
        assertFalse(bag.containsKey(SAPLING));
        assertTrue(SeedChoices.allocate(new ArrayList<>(plan(1).keySet()),99,bag,t->"Palm sapling",p->"",Map.of(),true,patchChoice->true).isEmpty());
        assertTrue(GuideView.missingItems(Map.of(17,10),Map.of(17,10)).isEmpty());
        assertEquals(Set.of(17),GuideView.missingItems(Map.of(17,10),Map.of(17,9)));
    }

    @Test public void existingThemeAndPreferenceModes() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            JComboBox<String> combo=FarmingHelperPanel.replacementChoice("Use preferences","PRESERVE");
            assertEquals("PRESERVE",FarmingHelperPanel.replacementMode(combo));
            combo.setSelectedIndex(0);assertEquals("",FarmingHelperPanel.replacementMode(combo));
            combo.setSelectedIndex(1);assertEquals("REPLANT",FarmingHelperPanel.replacementMode(combo));
            assertEquals(FarmingTheme.INPUT,combo.getBackground());
            for(KeepCrop crop:KeepCrop.values())assertNotNull(crop.label,crop.crop());
        });
        Properties metadata=new Properties();
        try(java.io.InputStream stream=FarmingHelperPanel.class.getResourceAsStream("runelite-plugin.properties"))
        {
            assertNotNull(stream);metadata.load(stream);
        }
        String version=metadata.getProperty("version");
        assertNotNull(version);assertFalse(version.isBlank());
        assertEquals("Farming Helper · v"+version,FarmingHelperPanel.versionLabel());
    }
}
