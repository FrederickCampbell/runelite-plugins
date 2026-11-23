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
import java.util.function.ToIntFunction;

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
        final boolean harvestOnly;
        final boolean protectionPlanned;

        Step(FarmRunPatch patch, Crop crop)
        {
            this(patch, crop, true, false);
        }

        Step(FarmRunPatch patch, Crop crop, boolean requiresPlanting)
        {
            this(patch, crop, requiresPlanting, false);
        }

        Step(FarmRunPatch patch, Crop crop, boolean requiresPlanting, boolean harvestOnly)
        {
            this(patch, crop, requiresPlanting, harvestOnly, false);
        }

        Step(FarmRunPatch patch, Crop crop, boolean requiresPlanting, boolean harvestOnly,
             boolean protectionPlanned)
        {
            this.patch = patch;
            this.crop = crop;
            this.requiresPlanting = requiresPlanting;
            this.harvestOnly = harvestOnly;
            this.protectionPlanned = protectionPlanned;
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
        return build(type, patches, farmingLevel, stock, customPriority,
            priorities, reserves, fixedToolSlots, teleportSlots,
            freeHarvestSlots, protectTrees, stackable, preplanted,
            Collections.emptySet(), Collections.emptyMap());
    }

    static Plan build(PatchType type, List<FarmRunPatch> patches, int farmingLevel,
                      Map<Integer, Integer> stock, boolean customPriority,
                      String priorities, String reserves, int fixedToolSlots,
                      int teleportSlots, int freeHarvestSlots, boolean protectTrees,
                      IntPredicate stackable, Set<String> preplanted,
                      Set<String> harvestOnlyPatches, Map<String, String> cropOverrides)
    {
        return build(type, patches, farmingLevel, stock, customPriority,
            priorities, reserves, fixedToolSlots, teleportSlots,
            freeHarvestSlots, protectTrees, stackable, preplanted,
            harvestOnlyPatches, cropOverrides, false);
    }

    static Plan build(PatchType type, List<FarmRunPatch> patches, int farmingLevel,
                      Map<Integer, Integer> stock, boolean customPriority,
                      String priorities, String reserves, int fixedToolSlots,
                      int teleportSlots, int freeHarvestSlots, boolean protectTrees,
                      IntPredicate stackable, Set<String> preplanted,
                      Set<String> harvestOnlyPatches, Map<String, String> cropOverrides,
                      boolean herbloreFocus)
    {
        return build(type, patches, farmingLevel, stock, customPriority,
            priorities, reserves, fixedToolSlots, teleportSlots, freeHarvestSlots,
            protectTrees, stackable, preplanted, harvestOnlyPatches,
            cropOverrides, herbloreFocus, Integer.MAX_VALUE);
    }

    static Plan build(PatchType type, List<FarmRunPatch> patches, int farmingLevel,
                      Map<Integer, Integer> stock, boolean customPriority,
                      String priorities, String reserves, int fixedToolSlots,
                      int teleportSlots, int freeHarvestSlots, boolean protectTrees,
                      IntPredicate stackable, Set<String> preplanted,
                      Set<String> harvestOnlyPatches, Map<String, String> cropOverrides,
                      boolean herbloreFocus, int maxStops)
    {
        return build(type, patches, farmingLevel, stock, customPriority,
            priorities, reserves, fixedToolSlots, teleportSlots, freeHarvestSlots,
            protectTrees, stackable, preplanted, harvestOnlyPatches,
            cropOverrides, herbloreFocus, maxStops, selected -> teleportSlots);
    }

    static Plan build(PatchType type, List<FarmRunPatch> patches, int farmingLevel,
                      Map<Integer, Integer> stock, boolean customPriority,
                      String priorities, String reserves, int fixedToolSlots,
                      int teleportSlots, int freeHarvestSlots, boolean protectTrees,
                      IntPredicate stackable, Set<String> preplanted,
                      Set<String> harvestOnlyPatches, Map<String, String> cropOverrides,
                      boolean herbloreFocus, int maxStops,
                      ToIntFunction<List<FarmRunPatch>> teleportSlotsForSelected)
    {
        return build(type, patches, farmingLevel, stock, customPriority, priorities,
            reserves, fixedToolSlots, teleportSlots, freeHarvestSlots, protectTrees,
            stackable, preplanted, harvestOnlyPatches, cropOverrides, herbloreFocus,
            maxStops, teleportSlotsForSelected, Collections.emptyMap());
    }

    /**
     * Per-category ordering is used for merged runs; unspecified categories
     * retain highest-eligible automatic selection rather than being rejected.
     */
    static Plan build(PatchType type, List<FarmRunPatch> patches, int farmingLevel,
                      Map<Integer, Integer> stock, boolean customPriority,
                      String priorities, String reserves, int fixedToolSlots,
                      int teleportSlots, int freeHarvestSlots, boolean protectTrees,
                      IntPredicate stackable, Set<String> preplanted,
                      Set<String> harvestOnlyPatches, Map<String, String> cropOverrides,
                      boolean herbloreFocus, int maxStops,
                      ToIntFunction<List<FarmRunPatch>> teleportSlotsForSelected,
                      Map<FarmRunType, String> perCategoryPriorities)
    {
        List<Step> result = new ArrayList<>();
        Map<Integer, Integer> used = new LinkedHashMap<>();
        Map<Integer, Integer> paymentItems = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> rank = priorityRanks(priorities);
        Map<FarmRunType, Map<String, Integer>> categoryRanks = new HashMap<>();
        perCategoryPriorities.forEach((category, csv) -> categoryRanks.put(category, priorityRanks(csv)));
        Map<String, Integer> herbloreRank = priorityRanks(
            "Ranarr, Snapdragon, Toadflax, Avantoe, Irit, Kwuarm, Cadantine, "
                + "Lantadyme, Huasca, Torstol, Dwarf weed, Harralander, Guam");
        Map<String, Integer> reservesByName = parseReserves(reserves);
        int budget = Math.max(0, INVENTORY_CAPACITY - freeHarvestSlots);

        for (FarmRunPatch patch : patches)
        {
            if (result.size() >= Math.max(1, maxStops))
            {
                warnings.add("Wave stop limit reached; remaining stops require another loadout.");
                break;
            }
            if (preplanted.contains(FarmRouteManager.key(patch)))
            {
                if (!fitsStop(type, result, patch, used, paymentItems, fixedToolSlots,
                    budget, stackable, teleportSlotsForSelected))
                {
                    warnings.add("Inventory limit reached near " + patch.getDisplayName());
                    if (result.isEmpty()) continue;
                    break;
                }
                boolean verify = protectTrees && DirectorStepEngine.supportsProtection(patch.getPatchType());
                result.add(new Step(patch, null, false, false, verify));
                if (verify)
                    warnings.add("Existing crop at " + patch.getDisplayName() +
                        ": verify protection manually; no payment amount inferred.");
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
            List<Crop> allowedForOverride = new ArrayList<>(eligible);
            Map<String, Integer> activeRank = categoryRanks.getOrDefault(patch.getFarmRunType(), rank);
            if (categoryRanks.containsKey(patch.getFarmRunType()) || customPriority)
            {
                eligible.removeIf(c -> !activeRank.containsKey(normalize(c.getName())));
                eligible.sort(Comparator.comparingInt(c -> activeRank.get(normalize(c.getName()))));
            }
            else if (herbloreFocus && patchType == PatchType.HERB)
            {
                eligible.sort(Comparator.comparingInt(c ->
                    herbloreRank.getOrDefault(normalize(c.getName()), 100 + 99 - c.getLevel())));
            }
            int count = Math.max(1, patch.getPatchCount());
            String override = cropOverrides.get(FarmRouteManager.key(patch));
            if (override != null && !override.isEmpty())
            {
                Crop desired = null;
                for (Crop candidate : allowedForOverride)
                {
                    if (normalize(candidate.getName()).equals(normalize(override)))
                    {
                        desired = candidate;
                        break;
                    }
                }
                if (desired != null)
                {
                    int reserve = reservesByName.getOrDefault(normalize(desired.getName()), 0);
                    int remaining = stock.getOrDefault(desired.getItemId(), 0) - reserve
                        - used.getOrDefault(desired.getItemId(), 0);
                    if (remaining >= count * desired.getQuantity())
                    {
                        choice = desired;
                    }
                }
                if (choice == null)
                {
                    warnings.add("Preferred crop " + override + " unavailable for "
                        + patch.getDisplayName() + "; trying alternatives.");
                }
            }
            for (Crop candidate : eligible)
            {
                if (choice != null) break;
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
                if (harvestOnlyPatches.contains(FarmRouteManager.key(patch)))
                {
                    if (!fitsStop(type, result, patch, used, paymentItems, fixedToolSlots,
                        budget, stackable, teleportSlotsForSelected))
                    {
                        warnings.add("Inventory limit reached near " + patch.getDisplayName());
                        if (result.isEmpty()) continue;
                        break;
                    }
                    result.add(new Step(patch, null, false, true));
                    warnings.add("Harvest-only stop at " + patch.getDisplayName() +
                        ": no replacement planting stock allocated.");
                }
                else
                {
                    warnings.add("Insufficient eligible planting stock for "
                        + patch.getDisplayName() + ".");
                }
                continue;
            }

            Map<Integer, Integer> nextSeeds = new LinkedHashMap<>(used);
            nextSeeds.merge(choice.getItemId(), count * choice.getQuantity(), Integer::sum);
            Map<Integer, Integer> nextPayments = new LinkedHashMap<>(paymentItems);
            Map<Integer, Integer> stopPayments = new LinkedHashMap<>();
            if (DirectorStepEngine.supportsProtection(patchType) && protectTrees)
            {
                for (ProtectionPayment payment : ProtectionPaymentCatalog.forCrop(choice))
                    stopPayments.merge(payment.getItemId(), count * payment.getQuantity(), Integer::sum);
            }
            boolean paymentFits = !stopPayments.isEmpty();
            if (paymentFits)
            {
                Map<Integer, Integer> singleSeed = Collections.singletonMap(choice.getItemId(),
                    count * choice.getQuantity());
                paymentFits = occupied(type, singleSeed, stopPayments, fixedToolSlots,
                    teleportSlotsForSelected.applyAsInt(Collections.singletonList(patch)), stackable) <= budget;
                for (Map.Entry<Integer, Integer> payment : stopPayments.entrySet())
                {
                    int id = payment.getKey();
                    int required = payment.getValue() + paymentItems.getOrDefault(id, 0) +
                        nextSeeds.getOrDefault(id, 0);
                    if (stock.getOrDefault(id, 0) < required) paymentFits = false;
                }
                if (paymentFits)
                    stopPayments.forEach((id, amount) -> nextPayments.merge(id, amount, Integer::sum));
                else
                    warnings.add("Protection payment unavailable or too large at " + patch.getDisplayName()
                        + "; preferred crop kept without planned payment. Use compost or protect manually.");
            }
            List<FarmRunPatch> selectedStops = new ArrayList<>();
            for (Step planned : result) selectedStops.add(planned.patch);
            selectedStops.add(patch);
            int selectedTeleportSlots = Math.max(0,
                teleportSlotsForSelected.applyAsInt(selectedStops));
            int occupied = occupied(type, nextSeeds, nextPayments, fixedToolSlots,
                selectedTeleportSlots, stackable);
            if (occupied > budget)
            {
                warnings.add("28-slot limit reached near " + patch.getDisplayName()
                    + ". Reduce supplies or bank mid-run.");
                // An impossible first stop must not prevent other categories
                // from being planned. It remains explicitly unplanned.
                if (result.isEmpty()) continue;
                break;
            }

            result.add(new Step(patch, choice, true, false, paymentFits));
            used = nextSeeds;
            paymentItems = nextPayments;
        }
        if (result.isEmpty() && warnings.isEmpty())
        {
            warnings.add("No eligible planting steps found.");
        }
        List<FarmRunPatch> selectedStops = new ArrayList<>();
        for (Step planned : result) selectedStops.add(planned.patch);
        int selectedTeleportSlots = Math.max(0,
            teleportSlotsForSelected.applyAsInt(selectedStops));
        int occupied = occupied(type, used, paymentItems, fixedToolSlots,
            selectedTeleportSlots, stackable);
        return new Plan(result, used, paymentItems, warnings, occupied,
            Math.max(0, freeHarvestSlots));
    }

    private static boolean fitsStop(PatchType type, List<Step> previous,
                                    FarmRunPatch patch, Map<Integer, Integer> seeds,
                                    Map<Integer, Integer> payments, int tools, int budget,
                                    IntPredicate stackable,
                                    ToIntFunction<List<FarmRunPatch>> teleportSlots)
    {
        List<FarmRunPatch> stops = new ArrayList<>();
        for (Step step : previous) stops.add(step.patch);
        stops.add(patch);
        return occupied(type, seeds, payments, tools,
            teleportSlots.applyAsInt(stops), stackable) <= budget;
    }

    private static int occupied(PatchType type, Map<Integer, Integer> seeds,
                                Map<Integer, Integer> payments, int tools,
                                int teleports, IntPredicate stackable)
    {
        int slots = Math.max(0, tools) + Math.max(0, teleports);
        Map<Integer, Integer> required = new HashMap<>(seeds);
        payments.forEach((id, count) -> required.merge(id, count, Integer::sum));
        for (Map.Entry<Integer, Integer> item : required.entrySet())
        {
            // A stackable item used for two purposes still occupies one slot.
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
