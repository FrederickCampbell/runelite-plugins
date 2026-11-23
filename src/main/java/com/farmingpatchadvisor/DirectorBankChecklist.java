package com.farmingpatchadvisor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/** Immutable bridge between the Director session and Advisor's existing bank UI. */
final class DirectorBankChecklist
{
    final Map<Integer, Integer> seeds;
    final Map<Integer, Integer> supplies;
    final Map<RouteTeleport, Integer> travel;
    private final Set<Integer> itemIds;

    private DirectorBankChecklist(Map<Integer, Integer> seeds, Map<Integer, Integer> supplies,
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

    static DirectorBankChecklist remaining(DirectorPlanner.Plan wave, Map<Integer, Integer> kit,
                                           Map<RouteTeleport, Integer> travel, Set<Integer> done)
    {
        Map<Integer, Integer> seeds = new LinkedHashMap<>();
        Map<Integer, Integer> supplies = new LinkedHashMap<>(kit);
        int compost = 0;
        int coins = 0;
        boolean hasRemaining = false;
        for (int i = 0; i < wave.steps.size(); i++)
        {
            if (done.contains(i)) continue;
            DirectorPlanner.Step step = wave.steps.get(i);
            hasRemaining = true;
            int count = step.patch.getPatchCount();
            DirectorQuestTargets.Definition def = DirectorQuestTargets.get(step.patch);
            if (def != null && def.gardenerId >= 0) coins += 200 * count;
            if (step.patch.getPatchType().usesCompost()) compost += count;
            if (step.crop == null || !step.requiresPlanting) continue;
            seeds.merge(step.crop.getItemId(), count * step.crop.getQuantity(), Integer::sum);
            if (step.protectionPlanned)
                for (ProtectionPayment payment : ProtectionPaymentCatalog.forCrop(step.crop))
                    supplies.merge(payment.getItemId(), count * payment.getQuantity(), Integer::sum);
        }
        if (!hasRemaining) return new DirectorBankChecklist(Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap());
        if (supplies.containsKey(ItemID.COINS))
        {
            if (coins == 0) supplies.remove(ItemID.COINS);
            else supplies.put(ItemID.COINS, coins);
        }
        if (supplies.containsKey(ItemID.BUCKET_ULTRACOMPOST))
        {
            if (compost == 0) supplies.remove(ItemID.BUCKET_ULTRACOMPOST);
            else supplies.put(ItemID.BUCKET_ULTRACOMPOST, compost);
        }
        return new DirectorBankChecklist(seeds, supplies, travel);
    }

    Set<Integer> ids()
    {
        return itemIds;
    }

    boolean sameAs(DirectorBankChecklist other)
    {
        return other != null && seeds.equals(other.seeds) && supplies.equals(other.supplies)
            && travel.equals(other.travel);
    }
}
