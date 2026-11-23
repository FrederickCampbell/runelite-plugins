package com.farmingpatchadvisor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Source-aware stock model. Only observed inventories can be counted.
 * Bank/vault data is a cache, not an API that can read unopened storage.
 */
final class DirectorStockLedger
{
    final Map<Integer, Integer> carried;
    final Map<Integer, Integer> bank;
    final Map<Integer, Integer> vault;
    final boolean bankObserved;
    final boolean vaultObserved;

    DirectorStockLedger(Map<Integer, Integer> carried,
                        Map<Integer, Integer> bank,
                        Map<Integer, Integer> vault,
                        boolean bankObserved, boolean vaultObserved)
    {
        this.carried = copy(carried);
        this.bank = bankObserved ? copy(bank) : Collections.emptyMap();
        this.vault = vaultObserved ? copy(vault) : Collections.emptyMap();
        this.bankObserved = bankObserved;
        this.vaultObserved = vaultObserved;
    }

    private static Map<Integer, Integer> copy(Map<Integer, Integer> original)
    {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        if (original != null)
        {
            original.forEach((id, count) ->
            {
                if (id != null && id > 0 && count != null && count > 0)
                {
                    result.merge(id, count, Integer::sum);
                }
            });
        }
        return Collections.unmodifiableMap(result);
    }

    Map<Integer, Integer> available()
    {
        Map<Integer, Integer> result = new LinkedHashMap<>(carried);
        bank.forEach((id, amount) -> result.merge(id, amount, Integer::sum));
        vault.forEach((id, amount) -> result.merge(id, amount, Integer::sum));
        return result;
    }

    static final class Manifest
    {
        final Map<Integer, Integer> carried;
        final Map<Integer, Integer> takeFromBank;
        final Map<Integer, Integer> takeFromVault;
        final Map<Integer, Integer> missing;

        Manifest(Map<Integer, Integer> carried, Map<Integer, Integer> bank,
                 Map<Integer, Integer> vault, Map<Integer, Integer> missing)
        {
            this.carried = Collections.unmodifiableMap(carried);
            this.takeFromBank = Collections.unmodifiableMap(bank);
            this.takeFromVault = Collections.unmodifiableMap(vault);
            this.missing = Collections.unmodifiableMap(missing);
        }
    }

    Manifest manifest(DirectorPlanner.Plan wave)
    {
        return manifest(wave, Collections.emptyMap());
    }

    Manifest manifest(DirectorPlanner.Plan wave, Map<Integer, Integer> extras)
    {
        Map<Integer, Integer> required = new LinkedHashMap<>(wave.seeds);
        wave.payments.forEach((id, amount) -> required.merge(id, amount, Integer::sum));
        extras.forEach((id, amount) -> required.merge(id, amount, Integer::sum));
        Map<Integer, Integer> bag = new LinkedHashMap<>();
        Map<Integer, Integer> fromBank = new LinkedHashMap<>();
        Map<Integer, Integer> fromVault = new LinkedHashMap<>();
        Map<Integer, Integer> missing = new LinkedHashMap<>();

        for (int id : new TreeSet<>(required.keySet()))
        {
            int need = required.get(id);
            int inBag = Math.min(need, carried.getOrDefault(id, 0));
            if (inBag > 0) bag.put(id, inBag);
            need -= inBag;

            int fromB = Math.min(need, bank.getOrDefault(id, 0));
            if (fromB > 0) fromBank.put(id, fromB);
            need -= fromB;

            int fromV = Math.min(need, vault.getOrDefault(id, 0));
            if (fromV > 0) fromVault.put(id, fromV);
            need -= fromV;

            if (need > 0) missing.put(id, need);
        }
        return new Manifest(bag, fromBank, fromVault, missing);
    }
}
