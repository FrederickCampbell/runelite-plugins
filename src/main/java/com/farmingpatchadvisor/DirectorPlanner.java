package com.farmingpatchadvisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Small quantity-aware planning layer over Farming Patch Advisor's existing crop,
 * patch and protection-payment catalogs. No new crop database is maintained here.
 */
final class DirectorPlanner
{
    static final int INVENTORY_CAPACITY = 28;

    static final class Step
    {
        final FarmRunPatch patch;
        final Crop crop;
        final boolean requiresPlanting;

        Step(FarmRunPatch patch, Crop crop)
        {
            this(patch, crop, true);
        }

        Step(FarmRunPatch patch, Crop crop, boolean requiresPlanting)
        {
            this.patch = patch;
            this.crop = crop;
            this.requiresPlanting = requiresPlanting;
        }
    }

    static final class Plan
    {
        final List<Step> steps;
        final Map<Integer, Integer> seeds;
        final Map<Integer, Integer> payments;
        final List<String> warnings;
        final int occupiedSlots;
        final int reservedSlots;

        Plan(List<Step> steps, Map<Integer, Integer> seeds,
             Map<Integer, Integer> payments, List<String> warnings,
             int occupiedSlots, int reservedSlots)
        {
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
            this.seeds = Collections.unmodifiableMap(new LinkedHashMap<>(seeds));
            this.payments = Collections.unmodifiableMap(new LinkedHashMap<>(payments));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
            this.occupiedSlots = occupiedSlots;
            this.reservedSlots = reservedSlots;
        }
    }

    private DirectorPlanner() {}

    /**
     * The stock map represents observed contents only. Callers must warn about
     * unopened or stale bank and seed-vault inventories.
     *
     * @param stackable the RuneLite item-composition stackability check
     */
    static Plan build(PatchType type, List<FarmRunPatch> patches, int farmingLevel,
                      Map<Integer, Integer> stock, boolean customPriority,
                      String priorities, String reserves, int fixedToolSlots,
                      int teleportSlots, int freeHarvestSlots, boolean protectTrees,
                      IntPredicate stackable)
    {
        return build(type, patches, farmingLevel, stock, customPriority, priorities,
            reserves, fixedToolSlots, teleportSlots, freeHarvestSlots,
            protectTrees, stackable, Collections.emptySet());
    }

    /**
     * A preplanted patch can still need gardener protection, but must not
     * consume a sapling that is already in the ground.
     */
    static Plan build(PatchType type, List<FarmRunPatch> patches, int farmingLevel,
                      Map<Integer, Integer> stock, boolean customPriority,
                      String priorities, String reserves, int fixedToolSlots,
                      int teleportSlots, int freeHarvestSlots, boolean protectTrees,
                      IntPredicate stackable, Set<String> preplanted)
    {
        List<Step> result = new ArrayList<>();
        Map<Integer, Integer> used = new LinkedHashMap<>();
        Map<Integer, Integer> paymentItems = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> rank = priorityRanks(priorities);
        Map<String, Integer> reservesByName = parseReserves(reserves);
        int budget = Math.max(0, INVENTORY_CAPACITY - freeHarvestSlots);

        for (FarmRunPatch patch : patches)
        {
            if (preplanted.contains(FarmRouteManager.key(patch)))
            {
                result.add(new Step(patch, null, false));
                continue;
            }
            Crop choice = null;
            PatchType patchType = patch.getPatchType();
            List<Crop> eligible = new ArrayList<>();
            for (Crop crop : CropCatalog.crops(patchType))
            {
                if (crop.getLevel() <= farmingLevel)
                {
                    eligible.add(crop);
                }
            }
            eligible.sort(Comparator.comparingInt(Crop::getLevel).reversed());
            if (customPriority)
            {
                eligible.removeIf(c -> !rank.containsKey(normalize(c.getName())));
                eligible.sort(Comparator.comparingInt(c -> rank.get(normalize(c.getName()))));
            }
            int count = Math.max(1, patch.getPatchCount());
            for (Crop candidate : eligible)
            {
                int reserve = reservesByName.getOrDefault(normalize(candidate.getName()), 0);
                int remaining = stock.getOrDefault(candidate.getItemId(), 0)
                    - reserve - used.getOrDefault(candidate.getItemId(), 0);
                if (remaining >= count * candidate.getQuantity())
                {
                    choice = candidate;
                    break;
                }
            }
            if (choice == null)
            {
                warnings.add("Insufficient eligible planting stock for " + patch.getDisplayName() + ".");
                continue;
            }

            Map<Integer, Integer> nextSeeds = new LinkedHashMap<>(used);
            nextSeeds.merge(choice.getItemId(), count * choice.getQuantity(), Integer::sum);
            Map<Integer, Integer> nextPayments = new LinkedHashMap<>(paymentItems);
            if (DirectorStepEngine.isTreeType(patchType) && protectTrees)
            {
                for (ProtectionPayment payment : ProtectionPaymentCatalog.forCrop(choice))
                {
                    nextPayments.merge(payment.getItemId(), count * payment.getQuantity(), Integer::sum);
                }
            }
            int occupied = occupied(type, nextSeeds, nextPayments, fixedToolSlots,
                teleportSlots, stackable);
            if (occupied > budget)
            {
                warnings.add("28-slot limit reached near " + patch.getDisplayName()
                    + ". Reduce supplies or bank mid-run.");
                break;
            }

            result.add(new Step(patch, choice));
            used = nextSeeds;
            paymentItems = nextPayments;
        }
        if (result.isEmpty() && warnings.isEmpty())
        {
            warnings.add("No eligible planting steps found.");
        }
        int occupied = occupied(type, used, paymentItems, fixedToolSlots,
            teleportSlots, stackable);
        return new Plan(result, used, paymentItems, warnings, occupied,
            Math.max(0, freeHarvestSlots));
    }

    private static int occupied(PatchType type, Map<Integer, Integer> seeds,
                                Map<Integer, Integer> payments, int tools,
                                int teleports, IntPredicate stackable)
    {
        int slots = Math.max(0, tools) + Math.max(0, teleports);
        for (Map.Entry<Integer, Integer> item : seeds.entrySet())
        {
            // Query RuneLite's real item composition for every patch category.
            slots += stackable.test(item.getKey()) ? 1 : item.getValue();
        }
        for (Map.Entry<Integer, Integer> item : payments.entrySet())
        {
            slots += stackable.test(item.getKey()) ? 1 : item.getValue();
        }
        return slots;
    }

    static Map<String, Integer> priorityRanks(String csv)
    {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        if (csv != null)
        {
            for (String term : csv.split(","))
            {
                String key = normalize(term);
                if (!key.isEmpty())
                {
                    ranks.putIfAbsent(key, ranks.size());
                }
            }
        }
        return ranks;
    }

    static Map<String, Integer> parseReserves(String csv)
    {
        Map<String, Integer> reserves = new HashMap<>();
        if (csv == null || csv.trim().isEmpty())
        {
            return reserves;
        }
        for (String pair : csv.split(","))
        {
            String[] halves = pair.split("[:=]", 2);
            if (halves.length != 2)
            {
                continue;
            }
            try
            {
                reserves.put(normalize(halves[0]), Math.max(0, Integer.parseInt(halves[1].trim())));
            }
            catch (NumberFormatException ignored)
            {
                // Bad user input does not crash run planning.
            }
        }
        return reserves;
    }

    static String normalize(String name)
    {
        if (name == null)
        {
            return "";
        }
        return name.trim().toLowerCase(Locale.ROOT)
            .replace(" sapling", "").replace(" seed", "").replaceAll("\\s+", " ");
    }
}
