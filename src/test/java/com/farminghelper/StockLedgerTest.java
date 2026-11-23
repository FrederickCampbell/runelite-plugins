package com.farminghelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class StockLedgerTest
{
    @Test
    public void conservativelyExcludesStaleBankAndVault()
    {
        Map<Integer,Integer> bag = Collections.singletonMap(100, 2);
        Map<Integer,Integer> bank = Collections.singletonMap(100, 10);
        StockLedger ledger = new StockLedger(bag, bank, bank,
            false, false);
        assertEquals(2, ledger.available().get(100).intValue());
        assertFalse(ledger.bankObserved);
        assertFalse(ledger.vaultObserved);
    }

    @Test
    public void splitsWithdrawalByCurrentInventoryBankAndVault()
    {
        Crop ranarr = CropCatalog.findByName(PatchType.HERB, "Ranarr");
        Map<Integer,Integer> carried = Collections.singletonMap(ranarr.getItemId(), 1);
        Map<Integer,Integer> bank = Collections.singletonMap(ranarr.getItemId(), 2);
        Map<Integer,Integer> vault = Collections.singletonMap(ranarr.getItemId(), 4);
        StockLedger ledger = new StockLedger(carried, bank,
            vault, true, true);
        Map<Integer,Integer> seeds = new HashMap<>();
        seeds.put(ranarr.getItemId(), 8);
        RunPlanner.Plan plan = new RunPlanner.Plan(
            Collections.emptyList(), seeds, Collections.emptyMap(),
            Collections.emptyList(), 1, 8);
        StockLedger.Manifest manifest = ledger.manifest(plan);
        assertEquals(1, manifest.carried.get(ranarr.getItemId()).intValue());
        assertEquals(2, manifest.takeFromBank.get(ranarr.getItemId()).intValue());
        assertEquals(4, manifest.takeFromVault.get(ranarr.getItemId()).intValue());
        assertEquals(1, manifest.missing.get(ranarr.getItemId()).intValue());
    }

    @Test
    public void paymentsCombineWithSeedRequirements()
    {
        Map<Integer,Integer> seed = Collections.singletonMap(1, 2);
        Map<Integer,Integer> payment = Collections.singletonMap(1, 3);
        StockLedger ledger = new StockLedger(
            Collections.singletonMap(1, 4), Collections.emptyMap(),
            Collections.emptyMap(), false, false);
        RunPlanner.Plan plan = new RunPlanner.Plan(
            Collections.emptyList(), seed, payment,
            Collections.emptyList(), 1, 2);
        assertEquals(1, ledger.manifest(plan).missing.get(1).intValue());
    }
}
