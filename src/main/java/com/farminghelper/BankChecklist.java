package com.farminghelper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/** Immutable bridge between the Director session and Advisor's existing bank UI. */
final class BankChecklist
{
    final Map<Integer, Integer> seeds;
    final Map<Integer, Integer> supplies;
    final Map<RouteTeleport, Integer> travel;
    private final Set<Integer> itemIds;

    private BankChecklist(Map<Integer, Integer> seeds, Map<Integer, Integer> supplies,
                                  Map<RouteTeleport, Integer> travel)
    {
        this.seeds = Collections.unmodifiableMap(new LinkedHashMap<>(seeds));
        this.supplies = Collections.unmodifiableMap(new LinkedHashMap<>(supplies));
        this.travel = Collections.unmodifiableMap(new LinkedHashMap<>(travel));
        Set<Integer> ids = new LinkedHashSet<>(seeds.keySet());
        ids.addAll(supplies.keySet());
        for (RouteTeleport teleport : travel.keySet())
            for (int id : teleport.itemIds()) ids.add(id);
        itemIds = Collections.unmodifiableSet(ids);
    }

    static BankChecklist remaining(RunPlanner.Plan wave, Map<Integer, Integer> kit,
                                           Map<RouteTeleport, Integer> travel, Set<Integer> done)
    {
        return remaining(wave, kit, travel, done, -1);
    }

    static BankChecklist remaining(RunPlanner.Plan wave, Map<Integer, Integer> kit,
                                   Map<RouteTeleport, Integer> travel, Set<Integer> done, int planted)
    {
        Map<Integer, Integer> seeds = new LinkedHashMap<>();
        Map<Integer, Integer> supplies = new LinkedHashMap<>(kit);
        int compost = 0;
        int coins = 0;
        boolean hasRemaining = false;
        for (int i = 0; i < wave.steps.size(); i++)
        {
            if (done.contains(i)) continue;
            RunPlanner.Step step = wave.steps.get(i);
            hasRemaining = true;
            int count = step.patch.getPatchCount();
            QuestTargets.Definition def = QuestTargets.get(step.patch);
            if (def != null && def.gardenerId >= 0) coins += 200 * count;
            if (step.patch.getPatchType().usesCompost()) compost += count;
            if (step.crop == null || !step.requiresPlanting) continue;
            if (i != planted)
                seeds.merge(step.crop.getItemId(), count * step.crop.getQuantity(), Integer::sum);
            if (step.protectionPlanned)
                for (ProtectionPayment payment : ProtectionPaymentCatalog.forCrop(step.crop))
                    supplies.merge(payment.getItemId(), count * payment.getQuantity(), Integer::sum);
        }
        if (!hasRemaining) return new BankChecklist(Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap());
        if (supplies.containsKey(ItemID.COINS))
        {
            if (coins == 0) supplies.remove(ItemID.COINS);
            else supplies.put(ItemID.COINS, coins);
        }
        for (int id : new int[]{ItemID.BUCKET_COMPOST, ItemID.BUCKET_SUPERCOMPOST, ItemID.BUCKET_ULTRACOMPOST})
        {
            if (!supplies.containsKey(id)) continue;
            if (compost == 0) supplies.remove(id);
            else supplies.put(id, compost);
        }
        if (compost == 0) supplies.remove(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
        return new BankChecklist(seeds, supplies, travel);
    }

    Set<Integer> ids()
    {
        return itemIds;
    }

    boolean sameAs(BankChecklist other)
    {
        return other != null && seeds.equals(other.seeds) && supplies.equals(other.supplies)
            && travel.equals(other.travel);
    }
}
