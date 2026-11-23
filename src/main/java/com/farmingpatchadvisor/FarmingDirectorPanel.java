package com.farmingpatchadvisor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import com.farmingpatchadvisor.queststate.CropState;
import com.farmingpatchadvisor.queststate.QuestFarmingStateReader;
import net.runelite.client.plugins.timetracking.Tab;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Quest Helper-style step-by-step farming sidebar, backed by Advisor's data.
 * The existing Advisor controls remain in the secondary tab for power users.
 * All client state is read on the RuneLite client thread.
 */
@Singleton
final class FarmingDirectorPanel extends PluginPanel
{
    private static final String GROUP = "ironman-farming-director";
    private static final long FRESH_MILLIS = 6L * 60 * 60 * 1000;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("MMM d, h:mm a")
        .withZone(ZoneId.systemDefault());
    private final Client client;
    private final ClientThread clientThread;
    private final EventBus eventBus;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final FarmingLoadout loadout;
    private final FarmingPatchAdvisorPlugin plugin;
    private final FarmingPatchAdvisorConfig config;
    private final FarmRouteManager routes;
    private final QuestFarmingStateReader stateReader;
    private final PatchTimerManager timerManager;

    private final JComboBox<DirectorRunSelection> runType =
        new JComboBox<>(DirectorRunSelection.values());
    private final JCheckBox customPriority = new JCheckBox("Use custom priority");
    private final JCheckBox protectTrees = new JCheckBox("Carry tree protection payments", true);
    private final JCheckBox autoNext = new JCheckBox("Auto-next after patch completion", true);
    private final JCheckBox nearestRoute = new JCheckBox("Nearest-stop order (approximate)", false);
    private final JButton replan = new JButton("Replan remaining");
    private final JButton overrideCrop = new JButton("Override this patch");
    private final JButton clearOverride = new JButton("Clear override");
    private final JLabel currentAction = new JLabel("Start a run to see context-aware steps");
    private final JTextField priorities = new JTextField();
    private final JTextField reserves = new JTextField();
    private final JLabel progress = new JLabel("Choose a run and press Start");
    private final JLabel storage = new JLabel("Open your bank and Seed Vault to scan stock");
    private final JLabel inventory = new JLabel("Inventory plan appears here");
    private final JPanel stepList = new JPanel();
    private final JButton start = new JButton("Start / Replan");
    private final JButton back = new JButton("Back");
    private final JButton next = new JButton("Next step");
    private final JButton pause = new JButton("Pause");
    private final JButton end = new JButton("End");
    private volatile DirectorPlanner.Plan currentPlan;
    private volatile int currentStep;
    private volatile boolean paused;
    private volatile boolean runFinished;
    private boolean observedInitialState;
    private boolean initiallyGrowing;
    private long activatedAtMillis;
    private QuestFarmingStateReader.Observation visibleObservation;
    private DirectorStepEngine.Action visibleAction = DirectorStepEngine.Action.INSPECT;
    private final Set<Integer> completedSteps = new HashSet<>();
    private final Set<String> completedPatchKeys = new HashSet<>();
    private long planningGeneration;
    private volatile long routeGeneration;
    private volatile boolean routeIssued;
    private boolean loadingPreferences;

    @Inject
    FarmingDirectorPanel(Client client, ClientThread clientThread, EventBus eventBus,
                         ConfigManager configManager, ItemManager itemManager, FarmingLoadout loadout,
                         FarmingPatchAdvisorPlugin plugin, FarmingPatchAdvisorConfig config,
                         FarmRouteManager routes, QuestFarmingStateReader stateReader, PatchTimerManager timerManager,
                         FarmingPatchPanel advisorPanel)
    {
        super(false);
        this.client = client;
        this.clientThread = clientThread;
        this.eventBus = eventBus;
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.loadout = loadout;
        this.plugin = plugin;
        this.config = config;
        this.routes = routes;
        this.stateReader = stateReader;
        this.timerManager = timerManager;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tabs.setForeground(Color.WHITE);
        tabs.addTab("Guide", createGuide());
        tabs.addTab("Advisor", advisorPanel);
        add(tabs, BorderLayout.CENTER);
        runType.addActionListener(e -> {
            if (!loadingPreferences)
            {
                stopRun();
                loadPreferences();
            }
        });
        loadPreferences();
        render();
    }

    private JPanel createGuide()
    {
        JPanel guide = new JPanel(new BorderLayout(0, 8));
        guide.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        controls.setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));
        JLabel title = label("Ironman Farming Director", true);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        controls.add(title);
        controls.add(Box.createVerticalStrut(9));

        runType.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        runType.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(runType);
        controls.add(Box.createVerticalStrut(5));

        JPanel advanced = new JPanel();
        advanced.setLayout(new BoxLayout(advanced, BoxLayout.Y_AXIS));
        advanced.setAlignmentX(Component.LEFT_ALIGNMENT);
        advanced.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        advanced.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        customPriority.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        customPriority.setForeground(Color.WHITE);
        advanced.add(customPriority);
        advanced.add(label("Crop priorities (comma-separated)", false));
        priorities.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        priorities.setAlignmentX(Component.LEFT_ALIGNMENT);
        priorities.setToolTipText("Example: Ranarr, Snapdragon, Toadflax");
        advanced.add(priorities);
        advanced.add(Box.createVerticalStrut(5));
        advanced.add(label("Reserved stock (Ranarr=10, Snapdragon=3)", false));
        reserves.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        reserves.setAlignmentX(Component.LEFT_ALIGNMENT);
        reserves.setToolTipText("Reserved quantities will not be allocated to a run.");
        advanced.add(reserves);
        advanced.add(Box.createVerticalStrut(5));
        protectTrees.setForeground(Color.WHITE);
        protectTrees.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        advanced.add(protectTrees);
        autoNext.setForeground(Color.WHITE);
        autoNext.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        advanced.add(autoNext);
        nearestRoute.setForeground(Color.WHITE);
        nearestRoute.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        advanced.add(nearestRoute);
        advanced.setVisible(false);

        JButton advancedToggle = new JButton("Planner options ▸");
        advancedToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        advancedToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        advancedToggle.addActionListener(e ->
        {
            advanced.setVisible(!advanced.isVisible());
            advancedToggle.setText(advanced.isVisible() ? "Planner options ▾" : "Planner options ▸");
            controls.revalidate();
            controls.repaint();
        });
        controls.add(advancedToggle);
        controls.add(advanced);
        controls.add(Box.createVerticalStrut(7));
        currentAction.setForeground(new Color(120, 205, 238));
        currentAction.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(currentAction);
        controls.add(Box.createVerticalStrut(8));
        start.addActionListener(e -> beginRun());
        start.setAlignmentX(Component.LEFT_ALIGNMENT);
        start.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        controls.add(start);
        replan.addActionListener(e -> beginRun(true));
        replan.setAlignmentX(Component.LEFT_ALIGNMENT);
        replan.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
        controls.add(replan);
        JPanel overrides = new JPanel(new GridLayout(1, 2, 3, 0));
        overrides.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        overrides.setAlignmentX(Component.LEFT_ALIGNMENT);
        overrides.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        overrideCrop.addActionListener(e -> chooseCropOverride());
        clearOverride.addActionListener(e -> clearCropOverride());
        overrides.add(overrideCrop);
        overrides.add(clearOverride);
        controls.add(overrides);
        controls.add(Box.createVerticalStrut(7));
        progress.setForeground(new Color(244, 197, 86));
        progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(progress);
        controls.add(Box.createVerticalStrut(4));
        storage.setForeground(Color.LIGHT_GRAY);
        storage.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(storage);
        controls.add(Box.createVerticalStrut(4));
        inventory.setForeground(Color.LIGHT_GRAY);
        inventory.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(inventory);
        guide.add(controls, BorderLayout.NORTH);

        stepList.setLayout(new BoxLayout(stepList, BoxLayout.Y_AXIS));
        stepList.setBackground(ColorScheme.DARK_GRAY_COLOR);
        stepList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroller = new JScrollPane(stepList);
        scroller.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        guide.add(scroller, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 4, 3, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(6, 7, 7, 7));
        actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        back.addActionListener(e -> advance(-1));
        next.addActionListener(e -> advance(1));
        pause.addActionListener(e -> togglePause());
        end.addActionListener(e -> stopRun());
        actions.add(back);
        actions.add(next);
        actions.add(pause);
        actions.add(end);
        guide.add(actions, BorderLayout.SOUTH);
        return guide;
    }

    private static JLabel label(String text, boolean strong)
    {
        JLabel l = new JLabel(text);
        l.setForeground(strong ? Color.WHITE : Color.LIGHT_GRAY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private String profileValue(String key, String fallback)
    {
        String value = configManager.getRSProfileConfiguration(GROUP, key);
        return value == null ? fallback : value;
    }

    private void loadPreferences()
    {
        loadingPreferences = true;
        DirectorRunSelection selected = (DirectorRunSelection) runType.getSelectedItem();
        if (selected == null)
        {
            selected = DirectorRunSelection.HERBS;
        }
        String name = selected.name();
        String defaults = selected == DirectorRunSelection.TREES
            ? "Magic, Yew, Maple, Willow, Oak"
            : selected == DirectorRunSelection.ALLOTMENTS
                ? "Snape grass, Watermelon, Strawberry, Sweetcorn"
                : selected == DirectorRunSelection.HERBS
                    ? "Ranarr, Snapdragon, Toadflax, Irit, Avantoe" : "";
        priorities.setText(profileValue("directorPriority" + name, defaults));
        reserves.setText(profileValue("directorReserve" + name, ""));
        customPriority.setSelected(Boolean.parseBoolean(
            profileValue("directorCustom" + name, "false")));
        protectTrees.setSelected(Boolean.parseBoolean(
            profileValue("directorProtectTrees", "true")));
        protectTrees.setVisible(selected == DirectorRunSelection.TREES ||
            selected == DirectorRunSelection.FRUIT_TREES ||
            selected == DirectorRunSelection.HARDWOODS ||
            selected == DirectorRunSelection.TREE_COMBO ||
            selected == DirectorRunSelection.SPECIAL ||
            selected == DirectorRunSelection.ALL);
        autoNext.setSelected(Boolean.parseBoolean(profileValue("directorAutoNext", "true")));
        nearestRoute.setSelected(Boolean.parseBoolean(profileValue("directorNearest", "false")));
        loadingPreferences = false;
    }

    private void savePreferences()
    {
        if (configManager.getRSProfileKey() == null)
        {
            return;
        }
        DirectorRunSelection selected = (DirectorRunSelection) runType.getSelectedItem();
        String name = selected == null ? "HERBS" : selected.name();
        configManager.setRSProfileConfiguration(GROUP, "directorPriority" + name,
            priorities.getText());
        configManager.setRSProfileConfiguration(GROUP, "directorReserve" + name,
            reserves.getText());
        configManager.setRSProfileConfiguration(GROUP, "directorCustom" + name,
            Boolean.toString(customPriority.isSelected()));
        configManager.setRSProfileConfiguration(GROUP, "directorProtectTrees",
            Boolean.toString(protectTrees.isSelected()));
        configManager.setRSProfileConfiguration(GROUP, "directorAutoNext",
            Boolean.toString(autoNext.isSelected()));
        configManager.setRSProfileConfiguration(GROUP, "directorNearest",
            Boolean.toString(nearestRoute.isSelected()));
    }

    private void beginRun()
    {
        beginRun(false);
    }

    /** Replan keeps completed patch IDs so we can safely refill at a bank. */
    private void beginRun(boolean preserveProgress)
    {
        savePreferences();
        final DirectorRunSelection selected = (DirectorRunSelection) runType.getSelectedItem();
        if (selected == null) return;
        final boolean custom = customPriority.isSelected();
        final String priorityText = priorities.getText();
        final String reserveText = reserves.getText();
        final boolean protection = protectTrees.isSelected();
        final boolean proximity = nearestRoute.isSelected();
        final Set<String> skip = preserveProgress
            ? new HashSet<>(completedPatchKeys) : Collections.emptySet();
        final long generation = ++planningGeneration;
        start.setEnabled(false);
        replan.setEnabled(false);
        clearRoute();
        clientThread.invoke(() ->
        {
            DirectorPlanner.Plan result = compute(selected, custom, priorityText,
                reserveText, protection, proximity, skip);
            SwingUtilities.invokeLater(() ->
            {
                if (generation != planningGeneration) return;
                currentPlan = result;
                currentStep = 0;
                paused = false;
                runFinished = false;
                completedSteps.clear();
                if (!preserveProgress) completedPatchKeys.clear();
                activateCurrentStep();
                start.setEnabled(true);
                replan.setEnabled(true);
                render();
                if (!currentPlan.steps.isEmpty())
                {
                    requestRoute(currentPlan.steps.get(0));
                    clientThread.invoke(this::onClientTick);
                }
            });
        });
    }

    private DirectorPlanner.Plan compute(DirectorRunSelection selection,
        boolean custom, String priorityText, String reserveText,
        boolean protection, boolean proximity, Set<String> skip)
    {
        List<String> warnings = new ArrayList<>();
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            warnings.add("Log into a character before planning a run.");
            return new DirectorPlanner.Plan(Collections.emptyList(), Collections.emptyMap(),
                Collections.emptyMap(), warnings, 0, 0);
        }
        int level = plugin.getFarmingLevel();
        List<FarmRunPatch> candidates = new ArrayList<>();
        List<FarmRunPatch> configured = routes.activePatches(selection.filter);
        int unknownPatchDefinitions = 0;
        int approximateDestinations = 0;

        for (FarmRunPatch grouped : configured)
        {
            PatchType type = grouped.getPatchType();
            if (!selection.includes(type)) continue;
            String access = DirectorWaypoints.accessIssue(client, type, grouped.getLocation(),
                level, config.directorIncludeUnverifiedLocations());
            if (access != null)
            {
                warnings.add(access);
                continue;
            }
            List<String> canonical = stateReader.patchNamesForType(type.name(),
                grouped.getLocation());
            List<FarmRunPatch> separate = DirectorPatchNames.expand(grouped, canonical);
            if (separate.isEmpty())
            {
                unknownPatchDefinitions++;
                continue;
            }
            for (FarmRunPatch patch : separate)
            {
                if (skip.contains(FarmRouteManager.key(patch))) continue;
                DirectorTravelTarget target = DirectorTravelTarget.choose(patch,
                    stateReader, timerManager);
                if (!target.exact) approximateDestinations++;
                candidates.add(patch);
            }
        }
        if (unknownPatchDefinitions > 0)
        {
            warnings.add(unknownPatchDefinitions +
                " grouped patch definitions could not be matched to Quest Helper and were skipped.");
        }
        if (approximateDestinations > 0)
        {
            warnings.add(approximateDestinations + " destinations lack exact verified tiles. "
                + "These show area hints rather than pretending to have exact patch pathing.");
        }

        if (proximity && client.getLocalPlayer() != null)
        {
            candidates = DirectorNearestRoute.order(candidates,
                client.getLocalPlayer().getWorldLocation(), patch ->
                {
                    DirectorTravelTarget target = DirectorTravelTarget.choose(patch,
                        stateReader, timerManager);
                    return target.point;
                });
            warnings.add("Nearest-stop order is a tile-distance heuristic, not "
                + "Shortest Path travel-time optimization.");
        }

        long bankAt = loadout.directorBankSnapshotAt();
        long vaultAt = loadout.directorVaultSnapshotAt();
        long now = System.currentTimeMillis();
        Map<Integer, Integer> stock = new HashMap<>(loadout.directorInventory());
        if (fresh(bankAt, now))
        {
            loadout.directorBank().forEach((id, n) -> stock.merge(id, n, Integer::sum));
        }
        else
        {
            warnings.add(bankAt == 0 ? "Open your bank to scan seeds."
                : "Bank snapshot is over 6 hours old: reopen bank to refresh.");
        }
        if (fresh(vaultAt, now))
        {
            loadout.directorVault().forEach((id, n) -> stock.merge(id, n, Integer::sum));
        }
        else
        {
            warnings.add(vaultAt == 0 ? "Open the Farming Guild Seed Vault to scan its seeds."
                : "Seed Vault snapshot is over 6 hours old: reopen it to refresh.");
        }

        // Existing crops require neither a duplicate sapling nor a visit to
        // plant. Growing trees without observed protection remain eligible
        // protection stops; unknown patches remain unknown.
        List<FarmRunPatch> actionable = new ArrayList<>();
        Set<String> preplanted = new HashSet<>();
        Set<String> harvestOnly = new HashSet<>();
        Map<String, String> cropOverrides = new HashMap<>();
        int alreadyGrowing = 0;
        for (FarmRunPatch patch : candidates)
        {
            QuestFarmingStateReader.Observation observation = observePatchState(patch);
            if (observation.known() && observation.state == CropState.GROWING)
            {
                if (DirectorStepEngine.isTreeType(patch.getPatchType())
                    && protection && !Boolean.TRUE.equals(observation.protectedByFarmer))
                {
                    preplanted.add(FarmRouteManager.key(patch));
                    actionable.add(patch);
                }
                else
                {
                    alreadyGrowing++;
                }
            }
            else
            {
                actionable.add(patch);
                if (observation.known() &&
                    (observation.state == CropState.HARVESTABLE ||
                     observation.state == CropState.UNCHECKED ||
                     observation.state == CropState.DEAD ||
                     observation.state == CropState.DISEASED ||
                     observation.state == CropState.STUMP))
                {
                    harvestOnly.add(FarmRouteManager.key(patch));
                }
            }
            String override = profileValue("directorCrop_" + FarmRouteManager.key(patch), "");
            if (!override.isEmpty())
            {
                cropOverrides.put(FarmRouteManager.key(patch), override);
            }
        }
        if (alreadyGrowing > 0)
        {
            warnings.add("Omitted " + alreadyGrowing + " already-growing patches.");
        }
        candidates = actionable;

        // Tool allowance is conservative. Exact per-teleport consumables and
        // every equipment-slot alternative are not yet modeled.
        boolean needsDibber = candidates.stream()
            .anyMatch(p -> p.getPatchType().usesDibber());
        boolean needsAxe = candidates.stream()
            .anyMatch(p -> DirectorStepEngine.isTreeType(p.getPatchType()));
        boolean needsMagicSecateurs = candidates.stream()
            .anyMatch(p -> p.getPatchType() == PatchType.HERB);
        int toolSlots = 2 + (needsDibber ? 1 : 0) + (needsAxe ? 1 : 0);
        if (needsMagicSecateurs && stock.containsKey(ItemID.FAIRY_ENCHANTED_SECATEURS)
            && !loadout.directorEquipment().containsKey(ItemID.FAIRY_ENCHANTED_SECATEURS))
        {
            toolSlots++;
        }

        Set<RouteTeleport> distinctTeleports = new HashSet<>();
        for (FarmRunPatch patch : candidates)
        {
            for (FarmRunPatch configuredPatch : configured)
            {
                if (configuredPatch.getLocation().equals(patch.getLocation()) &&
                    configuredPatch.getPatchType() == patch.getPatchType())
                {
                    RouteTeleport teleport = routes.teleport(selection.filter, configuredPatch);
                    if (teleport != RouteTeleport.NONE) distinctTeleports.add(teleport);
                    break;
                }
            }
        }
        int harvestReserve = selection == DirectorRunSelection.TREES ||
            selection == DirectorRunSelection.FRUIT_TREES ||
            selection == DirectorRunSelection.HARDWOODS ? 2 :
            selection == DirectorRunSelection.ALLOTMENTS ? Math.max(14, config.directorHarvestSlots())
                : config.directorHarvestSlots();

        DirectorPlanner.Plan plan = DirectorPlanner.build(null, candidates, level, stock,
            custom, priorityText, reserveText, toolSlots, distinctTeleports.size(),
            harvestReserve, protection, id ->
                itemManager.getItemComposition(id).isStackable(), preplanted,
                harvestOnly, cropOverrides);

        for (Map.Entry<Integer, Integer> item : plan.payments.entrySet())
        {
            if (stock.getOrDefault(item.getKey(), 0) < item.getValue())
            {
                warnings.add("Gardener payment missing: item " + item.getKey() +
                    ", need " + item.getValue() + ".");
            }
        }
        for (RouteTeleport teleport : distinctTeleports)
        {
            boolean found = false;
            for (int id : teleport.itemIds())
            {
                found |= stock.getOrDefault(id, 0) > 0 ||
                    loadout.directorEquipment().getOrDefault(id, 0) > 0;
            }
            if (!found)
            {
                warnings.add("Configured teleport not in scanned supplies: " + teleport + ".");
            }
        }
        warnings.addAll(plan.warnings);
        return new DirectorPlanner.Plan(plan.steps, plan.seeds, plan.payments, warnings,
            plan.occupiedSlots, plan.reservedSlots);
    }

    private void chooseCropOverride()
    {
        if (currentPlan == null || runFinished || currentStep >= currentPlan.steps.size()) return;
        FarmRunPatch patch = currentPlan.steps.get(currentStep).patch;
        List<Crop> eligible = new ArrayList<>();
        for (Crop crop : CropCatalog.crops(patch.getPatchType()))
        {
            if (crop.getLevel() <= plugin.getFarmingLevel()) eligible.add(crop);
        }
        if (eligible.isEmpty()) return;
        String[] options = eligible.stream().map(Crop::getName).toArray(String[]::new);
        Object chosen = JOptionPane.showInputDialog(this,
            "Preferred crop at " + patch.getDisplayName() + ":",
            "Ironman Farming Director — crop override",
            JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (chosen != null && configManager.getRSProfileKey() != null)
        {
            configManager.setRSProfileConfiguration(GROUP,
                "directorCrop_" + FarmRouteManager.key(patch), chosen.toString());
            beginRun(true);
        }
    }

    private void clearCropOverride()
    {
        if (currentPlan == null || runFinished || currentStep >= currentPlan.steps.size()) return;
        FarmRunPatch patch = currentPlan.steps.get(currentStep).patch;
        if (configManager.getRSProfileKey() != null)
        {
            configManager.unsetRSProfileConfiguration(GROUP,
                "directorCrop_" + FarmRouteManager.key(patch));
            beginRun(true);
        }
    }

    private static boolean fresh(long scannedAt, long now)
    {
        return scannedAt > 0 && scannedAt <= now && now - scannedAt < FRESH_MILLIS;
    }


    /**
     * Use Quest Helper's live patch varbits first, falling back to its Time
     * Tracking prediction and then Advisor's observed timers. Never assume an
     * unobserved patch is empty. Do not map grouped allotments to an adjacent
     * Advisor timer because their individual tile positions differ.
     */
    private QuestFarmingStateReader.Observation observePatchState(FarmRunPatch patch)
    {
        DirectorTravelTarget target = DirectorTravelTarget.choose(patch,
            stateReader, timerManager);
        WorldPoint point = target.point;
        QuestFarmingStateReader.Observation observation = stateReader.observeType(
            patch.getPatchType().name(), patch.getLocation(), patch.getName(), point);

        // Advisor is an independent fallback when RuneLite Time Tracking has
        // not yet observed the patch. It NEVER turns missing data into empty.
        if (target.exact && point != null &&
            observation.source != QuestFarmingStateReader.Source.LIVE)
        {
            PatchTimer timer = timerManager.findTimer(point, patch.getPatchType(), 9);
            boolean advisorNewer = timer != null &&
                timer.getPlantedAt().toEpochMilli() > observation.observedAtMillis + 1500L;
            if (timer != null)
            {
                CropState state = null;
                if (timer.isDead())
                {
                    state = CropState.DEAD;
                }
                else if (timer.isDiseased())
                {
                    state = CropState.DISEASED;
                }
                else if (timer.isPlantedTimer() && !timer.isPicked())
                {
                    state = !Instant.now().isBefore(timer.getReadyAt())
                        ? (DirectorStepEngine.isTreeType(patch.getPatchType())
                            ? CropState.UNCHECKED : CropState.HARVESTABLE)
                        : CropState.GROWING;
                }
                if (state != null && (!observation.known() || advisorNewer))
                {
                    observation = new QuestFarmingStateReader.Observation(state,
                        QuestFarmingStateReader.Source.ADVISOR,
                        timer.getPlantedAt().toEpochMilli(),
                        observation.protectedByFarmer, "");
                }
            }
        }
        return observation;
    }

    /** Called by the plugin every game tick, strictly on the RuneLite client thread. */
    void onClientTick()
    {
        DirectorPlanner.Plan plan = currentPlan;
        int index = currentStep;
        if (plan == null || paused || runFinished || client.getGameState() != GameState.LOGGED_IN
            || index < 0 || index >= plan.steps.size())
        {
            return;
        }
        DirectorPlanner.Step step = plan.steps.get(index);
        QuestFarmingStateReader.Observation observation = observePatchState(step.patch);
        final QuestFarmingStateReader.Observation latest = observation;
        SwingUtilities.invokeLater(() -> acceptObservation(plan, index, latest));
    }

    /** Only the EDT owns live guide state; stale asynchronous ticks are dropped. */
    private void acceptObservation(DirectorPlanner.Plan plan, int index,
                                   QuestFarmingStateReader.Observation observation)
    {
        if (plan != currentPlan || index != currentStep || paused || runFinished)
        {
            return;
        }
        boolean fresh = observation.isFreshSince(activatedAtMillis);
        if (!observedInitialState)
        {
            observedInitialState = true;
            initiallyGrowing = observation.known() && observation.state == CropState.GROWING;
        }

        DirectorPlanner.Step step = plan.steps.get(index);
        DirectorStepEngine.Action action = DirectorStepEngine.action(
            step.patch.getPatchType(), observation.state,
            DirectorStepEngine.isTreeType(step.patch.getPatchType()) && protectTrees.isSelected(),
            observation.protectedByFarmer);
        boolean changed = visibleObservation == null ||
            !Objects.equals(visibleObservation.state, observation.state) ||
            visibleObservation.source != observation.source ||
            !Objects.equals(visibleObservation.protectedByFarmer, observation.protectedByFarmer) ||
            action != visibleAction;
        visibleObservation = observation;
        visibleAction = action;
        if (autoNext.isSelected() && DirectorStepEngine.canAutoAdvance(
            action, observation.known(), fresh, initiallyGrowing))
        {
            moveStep(1, true);
            return;
        }
        if (changed)
        {
            render();
        }
    }

    private void activateCurrentStep()
    {
        activatedAtMillis = System.currentTimeMillis();
        observedInitialState = false;
        initiallyGrowing = false;
        visibleObservation = null;
        visibleAction = DirectorStepEngine.Action.INSPECT;
    }

    private void moveStep(int delta, boolean automatic)
    {
        if (currentPlan == null || currentPlan.steps.isEmpty() || paused)
        {
            return;
        }
        int previous = currentStep;
        int nextIndex = Math.max(0, Math.min(currentPlan.steps.size(), currentStep + delta));
        if (previous == nextIndex)
        {
            return;
        }
        if (delta > 0)
        {
            completedSteps.add(previous);
            completedPatchKeys.add(FarmRouteManager.key(currentPlan.steps.get(previous).patch));
        }
        else
        {
            completedSteps.remove(nextIndex);
            completedPatchKeys.remove(FarmRouteManager.key(currentPlan.steps.get(nextIndex).patch));
        }
        currentStep = nextIndex;
        if (currentStep >= currentPlan.steps.size())
        {
            runFinished = true;
            clearRoute();
            render();
            return;
        }
        runFinished = false;
        activateCurrentStep();
        render();
        requestRoute(currentPlan.steps.get(currentStep));
        // First read resolves already-growing patches without the user
        // needing to click Next at every location.
        clientThread.invoke(this::onClientTick);
    }

    private void requestRoute(DirectorPlanner.Step step)
    {
        if (paused)
        {
            return;
        }
        // All path requests use observed or verified destinations. Uncertain
        // Quest Helper region centres are displayed but not routed to.
        final long generation = ++routeGeneration;
        clientThread.invoke(() ->
        {
            DirectorPlanner.Plan active = currentPlan;
            DirectorTravelTarget target = DirectorTravelTarget.choose(step.patch,
                stateReader, timerManager);
            if (generation != routeGeneration || active == null || paused || runFinished
                || currentStep >= active.steps.size() || active.steps.get(currentStep) != step)
            {
                return;
            }
            boolean routable = client.getLocalPlayer() != null && target.point != null
                && (target.exact || target.source.startsWith("Nearby herb"));
            if (!routable)
            {
                if (routeIssued)
                {
                    eventBus.post(new PluginMessage("shortestpath", "clear"));
                    routeIssued = false;
                }
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("start", client.getLocalPlayer().getWorldLocation());
            data.put("target", target.point);
            // The receiver may not be installed: posting is a safe no-op then.
            eventBus.post(new PluginMessage("shortestpath", "path", data));
            routeIssued = true;
        });
    }

    private void clearRoute()
    {
        final long generation = ++routeGeneration;
        clientThread.invoke(() ->
        {
            if (generation == routeGeneration && routeIssued)
            {
                eventBus.post(new PluginMessage("shortestpath", "clear"));
                routeIssued = false;
            }
        });
    }

    private void advance(int delta)
    {
        moveStep(delta, false);
    }

    private void jumpTo(int targetIndex)
    {
        if (currentPlan == null || paused || targetIndex < 0
            || targetIndex >= currentPlan.steps.size())
        {
            return;
        }
        int previous = currentStep;
        currentStep = targetIndex;
        runFinished = false;
        if (targetIndex < previous)
        {
            completedSteps.remove(targetIndex);
            completedPatchKeys.remove(FarmRouteManager.key(currentPlan.steps.get(targetIndex).patch));
        }
        activateCurrentStep();
        render();
        requestRoute(currentPlan.steps.get(currentStep));
        clientThread.invoke(this::onClientTick);
    }

    private void togglePause()
    {
        if (currentPlan == null)
        {
            return;
        }
        paused = !paused;
        if (paused)
        {
            clearRoute();
        }
        else if (currentStep < currentPlan.steps.size())
        {
            requestRoute(currentPlan.steps.get(currentStep));
        }
        render();
    }

    private void stopRun()
    {
        ++planningGeneration;
        currentPlan = null;
        paused = false;
        currentStep = 0;
        runFinished = false;
        completedSteps.clear();
        completedPatchKeys.clear();
        activateCurrentStep();
        clearRoute();
        render();
    }

    void resetCharacter()
    {
        SwingUtilities.invokeLater(() ->
        {
            currentPlan = null;
            currentStep = 0;
            paused = false;
            runFinished = false;
            completedSteps.clear();
            completedPatchKeys.clear();
            activateCurrentStep();
            loadPreferences();
            render();
        });
        clearRoute();
    }

    void requestRefresh()
    {
        SwingUtilities.invokeLater(() -> {
            if (currentPlan == null)
            {
                storage.setText("Storage updated. Start a run for a fresh plan.");
            }
        });
    }

    void shutDown()
    {
        resetCharacter();
    }

    private void render()
    {
        stepList.removeAll();
        boolean active = currentPlan != null;
        int steps = active ? currentPlan.steps.size() : 0;
        progress.setText(!active ? "Configure a run and press Start"
            : steps == 0 ? "No planting steps available"
            : runFinished ? "Run complete: " + steps + " stops visited / skipped"
            : "Step " + (currentStep + 1) + " / " + steps + (paused ? "  (paused)" : ""));
        inventory.setText(!active ? "28 inventory slots available"
            : currentPlan.occupiedSlots + " planned + " + currentPlan.reservedSlots
                + " reserved / 28 slots");
        storage.setText(!active ? "Bank & vault snapshots are checked at Start"
            : "Using observed stock; reopen storage if estimates are stale");
        String actionText;
        if (!active || steps == 0)
        {
            actionText = "Start a run to see state-driven instructions.";
        }
        else if (runFinished)
        {
            actionText = "Run complete. Replan when you are ready for another run.";
        }
        else
        {
            DirectorPlanner.Step current = currentPlan.steps.get(currentStep);
            actionText = DirectorStepEngine.instruction(visibleAction, current.crop,
                current.patch.getPatchType());
            if (visibleObservation != null)
            {
                String source = visibleObservation.source.name().replace('_', ' ');
                actionText += " (" + source + (visibleObservation.state == null
                    ? ": unknown)" : ": " + visibleObservation.state.name().toLowerCase() + ")");
            }
        }
        currentAction.setText("<html><body style='width:245px'>"
            + escape(actionText) + "</body></html>");
        back.setEnabled(active && currentStep > 0);
        next.setEnabled(active && !runFinished && currentStep < steps);
        pause.setEnabled(active && steps > 0 && !runFinished);
        end.setEnabled(active);
        replan.setEnabled(active && steps > 0);
        overrideCrop.setEnabled(active && !runFinished && steps > 0);
        clearOverride.setEnabled(active && !runFinished && steps > 0);
        pause.setText(paused ? "Resume" : "Pause");

        if (active)
        {
            for (int i = 0; i < steps; i++)
            {
                DirectorPlanner.Step step = currentPlan.steps.get(i);
                JPanel item = new JPanel();
                item.setLayout(new BoxLayout(item, BoxLayout.Y_AXIS));
                item.setBackground(i == currentStep
                    ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);
                item.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BORDER_COLOR),
                    BorderFactory.createEmptyBorder(8, 9, 8, 7)));
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                final int index = i;
                JButton place = new JButton((i == currentStep ? "▶ "
                    : completedSteps.contains(i) ? "✓ " : "○ ")
                    + step.patch.getDisplayName());
                place.setForeground(i == currentStep ? new Color(244, 197, 86) : Color.WHITE);
                place.setBackground(item.getBackground());
                place.setBorderPainted(false);
                place.setContentAreaFilled(false);
                place.setFocusPainted(false);
                place.setHorizontalAlignment(JButton.LEFT);
                place.setAlignmentX(Component.LEFT_ALIGNMENT);
                place.setToolTipText("Select this destination and navigate there");
                place.addActionListener(e -> jumpTo(index));
                item.add(place);
                item.add(Box.createVerticalStrut(4));
                String instruction = i == currentStep && !runFinished
                    ? DirectorStepEngine.instruction(visibleAction, step.crop,
                        step.patch.getPatchType())
                    : completedSteps.contains(i) ? "Completed / skipped"
                    : step.requiresPlanting ? "Harvest / clear / plant " + step.crop.getName()
                    : step.harvestOnly ? "Harvest-only: refill before replanting"
                    : "Existing tree: verify gardener protection";
                item.add(warning(instruction));
                if (i == currentStep && !runFinished)
                {
                    // The panel rendering is on EDT. Use already-known Quest
                    // Helper exact locations; observed/area hints are loaded by
                    // the live reader on the client thread and displayed there.
                    WorldPoint point = DirectorWaypoints.exactPoint(step.patch);
                    if (point != null)
                    {
                        item.add(label("Destination: " + point.getX() + ", " + point.getY()
                            + " · Quest Helper / Shortest Path", false));
                    }
                    else
                    {
                        item.add(label("Approximate area or observed patch; "
                            + "exact navigation may be unavailable.", false));
                    }
                }
                stepList.add(item);
            }
            if (!currentPlan.payments.isEmpty())
            {
                stepList.add(warning("Tree protection payments: " + currentPlan.payments));
            }
            if (!currentPlan.seeds.isEmpty())
            {
                StringBuilder req = new StringBuilder("Planting stock: ");
                for (Map.Entry<Integer, Integer> entry : currentPlan.seeds.entrySet())
                {
                    Crop crop = CropCatalog.findByItemId(entry.getKey());
                    req.append(crop == null ? entry.getKey() : crop.getItemName())
                        .append(" x").append(entry.getValue()).append("; ");
                }
                stepList.add(warning(req.toString()));
            }
            for (String message : currentPlan.warnings)
            {
                stepList.add(warning(message));
            }
        }
        else
        {
            stepList.add(warning("Quest Helper's farming-state decoder chooses the next action: "
                + "harvest, check health, clear, plant, or pay protection. "
                + "Use the Advisor tab for timers, checklists and route editing."));
        }
        stepList.revalidate();
        stepList.repaint();
    }

    private static JLabel warning(String message)
    {
        JLabel label = new JLabel("<html><body style='width:235px'>"
            + escape(message) + "</body></html>");
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        label.setForeground(Color.LIGHT_GRAY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static String escape(String raw)
    {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
