package com.farmingpatchadvisor;

import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
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
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
@Slf4j
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
    private final DirectorPatchPreferences patchPrefs;
    private final DirectorMergedPresets presets;
    private final CompostBinManager compostBins;

    private final JComboBox<DirectorRunSelection> runType =
        new JComboBox<>(new DirectorRunSelection[] {
            DirectorRunSelection.HERBS, DirectorRunSelection.ALLOTMENTS,
            DirectorRunSelection.TREES, DirectorRunSelection.FRUIT_TREES,
            DirectorRunSelection.HARDWOODS, DirectorRunSelection.HOPS,
            DirectorRunSelection.BUSHES, DirectorRunSelection.CACTUS,
            DirectorRunSelection.SPECIAL, DirectorRunSelection.COMPOST,
            DirectorRunSelection.MERGED, DirectorRunSelection.ALL
        });
    private final JCheckBox customPriority = new JCheckBox("Use custom priority");
    private final JCheckBox protectTrees = new JCheckBox("Carry tree protection payments", true);
    private final JCheckBox autoNext = new JCheckBox("Auto-next after patch completion", true);
    private final JCheckBox nearestRoute = new JCheckBox("Nearest-stop order (approximate)", false);
    private final JCheckBox herbloreFocus = new JCheckBox("Prioritize Herblore supplies (preset)", false);
    private final JCheckBox autoReplanBank = new JCheckBox("Replan after banking", false);
    private final JSpinner maxStopsPerWave = new JSpinner(new SpinnerNumberModel(8, 1, 28, 1));
    private final JComboBox<DirectorBankStops.Bank> bankChoice =
        new JComboBox<>(DirectorBankStops.Bank.values());
    private final JButton replan = new JButton("Replan remaining");
    private final JButton resume = new JButton("Resume saved run");
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
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final JPanel patchRows = new JPanel();
    private final JTextField patchSearch = new JTextField();
    private final JCheckBox disabledOnly = new JCheckBox("Show disabled only");
    private final JCheckBox readyOnly = new JCheckBox("Only include ready / actionable patches");
    private final JCheckBox allowUnverified = new JCheckBox("Include unverified-access locations");
    private final JPanel compostRows = new JPanel();
    private final JCheckBox guideDetails = new JCheckBox("Show planning details and warnings");
    private final JComboBox<String> presetChoice = new JComboBox<>();
    private final Map<FarmRunType, JCheckBox> mergedTypes = new EnumMap<>(FarmRunType.class);
    private final Set<FarmRunType> collapsedTypes = EnumSet.noneOf(FarmRunType.class);
    private final JPanel mergedControls = new JPanel();
    private final JPanel regularControls = new JPanel();
    private final List<PatchView> patchViews = new ArrayList<>();
    private DirectorRunSelection activeRunSelection;
    private String activePresetName = DirectorMergedPresets.DEFAULT_NAME;
    private boolean loadingPresets;
    private String visiblePage = "selection";

    private static final class PatchView
    {
        final FarmRunPatch patch;
        final String unlock;
        final String state;
        PatchView(FarmRunPatch patch, String unlock, String state)
        {
            this.patch = patch;
            this.unlock = unlock;
            this.state = state;
        }
    }
    private volatile DirectorPlanner.Plan currentPlan;
    private DirectorSessionPlan currentSession;
    private int waveIndex;
    private boolean awaitingBank;
    private boolean sawHarvestable;
    private DirectorBankStops.Bank suggestedBank;
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
    private final Map<String, String> temporaryOverrides = new HashMap<>();
    private long planningGeneration;
    private volatile int lastPlannedFarmingLevel;
    private volatile long routeGeneration;
    private volatile boolean routeIssued;
    private boolean loadingPreferences;

    @Inject
    FarmingDirectorPanel(Client client, ClientThread clientThread, EventBus eventBus,
                         ConfigManager configManager, ItemManager itemManager, FarmingLoadout loadout,
                         FarmingPatchAdvisorPlugin plugin, FarmingPatchAdvisorConfig config,
                         FarmRouteManager routes, QuestFarmingStateReader stateReader, PatchTimerManager timerManager,
                         DirectorPatchPreferences patchPrefs, DirectorMergedPresets presets,
                         CompostBinManager compostBins)
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
        this.patchPrefs = patchPrefs;
        this.presets = presets;
        this.compostBins = compostBins;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        buildPages();
        add(pages, BorderLayout.CENTER);
        runType.addActionListener(e ->
        {
            if (!loadingPreferences)
            {
                loadPreferences();
                showEditorMode();
            }
        });
        presetChoice.addActionListener(e -> { if (!loadingPresets) loadPreset(); });
        loadPreferences();
        reloadPresetNames();
        render();
        showPage("selection");
    }

    /** A single RuneLite-native sidebar with persistent navigation between pages. */
    private void buildPages()
    {
        pages.setBackground(ColorScheme.DARK_GRAY_COLOR);
        pages.add(createSelection(), "selection");
        pages.add(createGuide(), "guide");
        pages.add(createEditor(), "editor");
        pages.add(createPatchManager(), "patches");
        pages.add(createSettings(), "settings");
        pages.add(createCompostGuide(), "compost");
    }

    private JPanel vertical()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 9, 10, 9));
        return panel;
    }

    private JScrollPane scrolling(JPanel content)
    {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private void addFull(JPanel parent, Component child)
    {
        if (child instanceof javax.swing.JComponent)
        {
            javax.swing.JComponent component = (javax.swing.JComponent) child;
            component.setAlignmentX(Component.LEFT_ALIGNMENT);
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                child instanceof JButton ? 32 : child instanceof JTextField
                    || child instanceof JComboBox ? 28 : 1000));
        }
        parent.add(child);
        parent.add(Box.createVerticalStrut(7));
    }

    private JButton nav(String caption, String page)
    {
        JButton button = new JButton(caption);
        button.addActionListener(e -> showPage(page));
        return button;
    }

    private void showPage(String name)
    {
        visiblePage = name;
        pageLayout.show(pages, name);
        if ("patches".equals(name)) refreshPatchManager();
        if ("editor".equals(name)) showEditorMode();
        if ("compost".equals(name)) refreshCompostGuide();
    }

    private JPanel createSelection()
    {
        JPanel list = vertical();
        JLabel title = label("Ironman Farming Director", true);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        addFull(list, title);
        addFull(list, label("Choose a run", false));
        addFull(list, runType);
        start.setText("Start farming run");
        start.addActionListener(e -> beginRun());
        addFull(list, start);
        resume.addActionListener(e -> resumeSavedRun());
        addFull(list, resume);
        addFull(list, nav("Edit selected run", "editor"));
        addFull(list, nav("Manage farming patches", "patches"));
        addFull(list, nav("Global settings", "settings"));
        JButton guide = new JButton("Return to active guide");
        guide.addActionListener(e -> { if (currentPlan != null) showPage("guide"); });
        addFull(list, guide);
        addFull(list, label("Routes, crops and bank supplies are planned from your saved choices.", false));
        return new JPanel(new BorderLayout())
        {{
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            add(scrolling(list), BorderLayout.CENTER);
        }};
    }

    private JPanel createGuide()
    {
        JPanel guide = new JPanel(new BorderLayout(0, 7));
        guide.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel header = vertical();
        JPanel menu = new JPanel(new GridLayout(1, 3, 3, 0));
        menu.setBackground(ColorScheme.DARK_GRAY_COLOR);
        menu.add(nav("Runs", "selection"));
        menu.add(nav("Patches", "patches"));
        menu.add(nav("Settings", "settings"));
        addFull(header, menu);
        currentAction.setForeground(new Color(120, 205, 238));
        progress.setForeground(new Color(244, 197, 86));
        addFull(header, progress);
        addFull(header, currentAction);
        addFull(header, storage);
        addFull(header, inventory);
        replan.addActionListener(e -> beginRun(true));
        addFull(header, replan);
        JPanel overrides = new JPanel(new GridLayout(1, 2, 3, 0));
        overrides.setBackground(ColorScheme.DARK_GRAY_COLOR);
        overrideCrop.setText("Edit crop");
        overrideCrop.addActionListener(e -> chooseCropOverride());
        clearOverride.addActionListener(e -> clearCropOverride());
        overrides.add(overrideCrop);
        overrides.add(clearOverride);
        addFull(header, overrides);
        guideDetails.setForeground(Color.LIGHT_GRAY);
        guideDetails.setBackground(ColorScheme.DARK_GRAY_COLOR);
        guideDetails.addActionListener(e -> render());
        addFull(header, guideDetails);
        guide.add(header, BorderLayout.NORTH);
        stepList.setLayout(new BoxLayout(stepList, BoxLayout.Y_AXIS));
        stepList.setBackground(ColorScheme.DARK_GRAY_COLOR);
        stepList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        guide.add(scrolling(stepList), BorderLayout.CENTER);
        JPanel actions = new JPanel(new GridLayout(1, 4, 3, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(6, 7, 7, 7));
        actions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        back.addActionListener(e -> advance(-1));
        next.addActionListener(e -> advance(1));
        pause.addActionListener(e -> togglePause());
        end.addActionListener(e -> { stopRun(); showPage("selection"); });
        actions.add(back);
        actions.add(next);
        actions.add(pause);
        actions.add(end);
        guide.add(actions, BorderLayout.SOUTH);
        return guide;
    }

    private JPanel createEditor()
    {
        JPanel body = vertical();
        addFull(body, nav("← Back to runs", "selection"));
        addFull(body, label("Run Editor", true));
        regularControls.setLayout(new BoxLayout(regularControls, BoxLayout.Y_AXIS));
        regularControls.setBackground(ColorScheme.DARK_GRAY_COLOR);
        customPriority.setForeground(Color.WHITE);
        customPriority.setBackground(ColorScheme.DARK_GRAY_COLOR);
        addFull(regularControls, customPriority);
        addFull(regularControls, label("Crop priority list (first available wins)", false));
        priorities.setToolTipText("Example: Ranarr, Snapdragon, Toadflax");
        addFull(regularControls, priorities);
        addFull(regularControls, label("Reserved stock, e.g. Ranarr=10, Snapdragon=3", false));
        addFull(regularControls, reserves);
        JButton save = new JButton("Save this run's choices");
        save.addActionListener(e -> { savePreferences(); showPage("selection"); });
        addFull(regularControls, save);

        mergedControls.setLayout(new BoxLayout(mergedControls, BoxLayout.Y_AXIS));
        mergedControls.setBackground(ColorScheme.DARK_GRAY_COLOR);
        addFull(mergedControls, label("Reusable merged-run preset", false));
        addFull(mergedControls, presetChoice);
        for (FarmRunType type : FarmRunType.values())
        {
            JCheckBox check = new JCheckBox(type.getDisplayName());
            check.setBackground(ColorScheme.DARK_GRAY_COLOR);
            check.setForeground(Color.WHITE);
            mergedTypes.put(type, check);
            addFull(mergedControls, check);
        }
        JButton saveMerged = new JButton("Save merged preset");
        saveMerged.addActionListener(e -> saveMergedPreset());
        addFull(mergedControls, saveMerged);
        JButton newMerged = new JButton("Create new preset");
        newMerged.addActionListener(e -> createMergedPreset());
        addFull(mergedControls, newMerged);
        JButton cropOverride = new JButton("Per-category crop priority override");
        cropOverride.addActionListener(e -> chooseMergedPriorityOverride());
        addFull(mergedControls, cropOverride);
        addFull(mergedControls, label("By default, merged runs inherit each category's saved crop preferences.", false));
        addFull(body, regularControls);
        addFull(body, mergedControls);
        JButton route = new JButton("Edit patch order and teleport choices");
        route.addActionListener(e -> FarmRouteEditor.open(this, routes,
            runType.getSelectedItem() == DirectorRunSelection.MERGED
                ? FarmRunFilter.ALL : ((DirectorRunSelection) runType.getSelectedItem()).filter));
        addFull(body, route);
        return new JPanel(new BorderLayout())
        {{
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            add(scrolling(body), BorderLayout.CENTER);
        }};
    }

    private void showEditorMode()
    {
        boolean merged = runType.getSelectedItem() == DirectorRunSelection.MERGED;
        mergedControls.setVisible(merged);
        regularControls.setVisible(!merged);
        if (merged) reloadPresetNames();
        mergedControls.revalidate();
    }

    private void reloadPresetNames()
    {
        String previous = (String) presetChoice.getSelectedItem();
        if (previous == null) previous = profileValue("directorLastPreset",
            DirectorMergedPresets.DEFAULT_NAME);
        loadingPresets = true;
        presetChoice.removeAllItems();
        for (String name : presets.names()) presetChoice.addItem(name);
        presetChoice.setSelectedItem(previous);
        if (presetChoice.getSelectedIndex() < 0 && presetChoice.getItemCount() > 0)
            presetChoice.setSelectedIndex(0);
        loadingPresets = false;
        loadPreset();
    }

    private void loadPreset()
    {
        String name = (String) presetChoice.getSelectedItem();
        if (name == null) return;
        Set<FarmRunType> types = presets.categories(name);
        for (Map.Entry<FarmRunType, JCheckBox> row : mergedTypes.entrySet())
            row.getValue().setSelected(types.contains(row.getKey()));
    }

    private void saveMergedPreset()
    {
        String name = (String) presetChoice.getSelectedItem();
        EnumSet<FarmRunType> types = EnumSet.noneOf(FarmRunType.class);
        for (Map.Entry<FarmRunType, JCheckBox> row : mergedTypes.entrySet())
            if (row.getValue().isSelected()) types.add(row.getKey());
        if (types.isEmpty() || !presets.save(name, types))
        {
            JOptionPane.showMessageDialog(this,
                "Select at least one category and log into a character to save.");
            return;
        }
        configManager.setRSProfileConfiguration(GROUP, "directorLastPreset", name);
        showPage("selection");
    }

    private void createMergedPreset()
    {
        Object input = JOptionPane.showInputDialog(this,
            "Preset name (letters, numbers, spaces, _ or -):",
            "Create merged run", JOptionPane.PLAIN_MESSAGE);
        if (input == null) return;
        String name = input.toString().trim();
        if (!DirectorMergedPresets.validName(name))
        {
            JOptionPane.showMessageDialog(this, "Choose a name of 1–40 safe characters.");
            return;
        }
        EnumSet<FarmRunType> selected = EnumSet.noneOf(FarmRunType.class);
        for (Map.Entry<FarmRunType, JCheckBox> entry : mergedTypes.entrySet())
            if (entry.getValue().isSelected()) selected.add(entry.getKey());
        if (!presets.save(name, selected))
        {
            JOptionPane.showMessageDialog(this, "Log in before saving presets.");
            return;
        }
        reloadPresetNames();
        presetChoice.setSelectedItem(name);
    }

    private void chooseMergedPriorityOverride()
    {
        String name = (String) presetChoice.getSelectedItem();
        if (name == null || configManager.getRSProfileKey() == null) return;
        Object category = JOptionPane.showInputDialog(this, "Choose a category:",
            "Merged-run crop override", JOptionPane.PLAIN_MESSAGE, null,
            FarmRunType.values(), FarmRunType.HERB);
        if (!(category instanceof FarmRunType)) return;
        String key = "directorMergedPriority_" + name + "_" + ((FarmRunType)category).name();
        String prior = profileValue(key, "");
        String value = JOptionPane.showInputDialog(this,
            "Comma-separated crops. Leave blank to inherit this category's usual preferences.",
            prior);
        if (value == null) return;
        if (value.trim().isEmpty())
            configManager.unsetRSProfileConfiguration(GROUP, key);
        else
            configManager.setRSProfileConfiguration(GROUP, key, value.trim());
    }

    private JPanel createSettings()
    {
        JPanel settings = vertical();
        addFull(settings, nav("← Back to runs", "selection"));
        addFull(settings, label("Global Settings", true));
        for (JCheckBox toggle : new JCheckBox[] {protectTrees, autoNext,
            nearestRoute, herbloreFocus, autoReplanBank, readyOnly, allowUnverified})
        {
            toggle.setBackground(ColorScheme.DARK_GRAY_COLOR);
            toggle.setForeground(Color.WHITE);
            addFull(settings, toggle);
        }
        addFull(settings, label("Maximum stops per loadout", false));
        addFull(settings, maxStopsPerWave);
        addFull(settings, label("Bank between loadouts", false));
        bankChoice.addActionListener(e ->
        {
            if (awaitingBank && !paused) requestBankRoute();
        });
        addFull(settings, bankChoice);
        JButton save = new JButton("Save settings");
        save.addActionListener(e -> { savePreferences(); showPage("selection"); });
        addFull(settings, save);
        addFull(settings, label("Nearest-stop ordering is approximate; Shortest Path handles travel to each stop.", false));
        return new JPanel(new BorderLayout())
        {{
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            add(scrolling(settings), BorderLayout.CENTER);
        }};
    }

    private JPanel createCompostGuide()
    {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel top = vertical();
        addFull(top, nav("← Back to runs", "selection"));
        addFull(top, label("Compost Run", true));
        JButton refresh = new JButton("Refresh compost observations");
        refresh.addActionListener(e -> refreshCompostGuide());
        addFull(top, refresh);
        outer.add(top, BorderLayout.NORTH);
        compostRows.setLayout(new BoxLayout(compostRows, BoxLayout.Y_AXIS));
        compostRows.setBackground(ColorScheme.DARK_GRAY_COLOR);
        outer.add(scrolling(compostRows), BorderLayout.CENTER);
        return outer;
    }

    private void refreshCompostGuide()
    {
        clientThread.invoke(() ->
        {
            List<String> instructions = new ArrayList<>();
            CompostSupplyPlan supply = compostBins.supplyPlan();
            instructions.add("Estimated supplies · ingredients x" + supply.getIngredientQuantity()
                + ", volcanic ash x" + supply.getVolcanicAshQuantity()
                + ", compost potion doses x" + supply.getCompostPotionDoses()
                + ", buckets x" + supply.getBucketQuantity());
            for (CompostBinLocation location : compostBins.enabledLocations())
            {
                CompostBinState state = compostBins.getState(location);
                instructions.add(location.getDisplayName() + " · "
                    + compostBins.nextAction(location, state));
            }
            if (instructions.size() == 1)
                instructions.add("No compost bins enabled. Enable compost tracking in RuneLite plugin settings.");
            SwingUtilities.invokeLater(() ->
            {
                compostRows.removeAll();
                for (String line : instructions) addFull(compostRows, warning(line));
                compostRows.revalidate();
                compostRows.repaint();
            });
        });
    }

    private JPanel createPatchManager()
    {
        JPanel outer = new JPanel(new BorderLayout(0, 6));
        outer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel header = vertical();
        addFull(header, nav("← Back to runs", "selection"));
        addFull(header, label("Global Patch Manager", true));
        addFull(header, label("Disabled patches are excluded from every run.", false));
        patchSearch.setToolTipText("Filter patch names and locations");
        addFull(header, patchSearch);
        disabledOnly.setBackground(ColorScheme.DARK_GRAY_COLOR);
        disabledOnly.setForeground(Color.WHITE);
        addFull(header, disabledOnly);
        patchSearch.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { drawPatchRows(); }
            public void removeUpdate(DocumentEvent e) { drawPatchRows(); }
            public void changedUpdate(DocumentEvent e) { drawPatchRows(); }
        });
        disabledOnly.addActionListener(e -> drawPatchRows());
        outer.add(header, BorderLayout.NORTH);
        patchRows.setLayout(new BoxLayout(patchRows, BoxLayout.Y_AXIS));
        patchRows.setBackground(ColorScheme.DARK_GRAY_COLOR);
        patchRows.setBorder(BorderFactory.createEmptyBorder(5, 9, 8, 9));
        outer.add(scrolling(patchRows), BorderLayout.CENTER);
        return outer;
    }

    private void refreshPatchManager()
    {
        clientThread.invoke(() ->
        {
            List<PatchView> result = new ArrayList<>();
            int level = client.getGameState() == GameState.LOGGED_IN
                ? plugin.getFarmingLevel() : 1;
            List<FarmRunPatch> individual = DirectorPatchPreferences.expand(
                FarmRunCatalog.patches(),
                patch -> stateReader.patchNamesForType(patch.getPatchType().name(), patch.getLocation()));
            for (FarmRunPatch patch : individual)
            {
                String issue = client.getGameState() == GameState.LOGGED_IN
                    ? DirectorWaypoints.accessIssue(client, patch.getPatchType(),
                        patch.getLocation(), level, true) : null;
                String access = client.getGameState() != GameState.LOGGED_IN
                    ? "Unlock: unknown (log in)"
                    : issue != null ? "Locked: " + issue
                    : uncertainLocation(patch.getLocation())
                        ? "Unlock: unverified" : "Unlock: unverified (no known restriction)";
                QuestFarmingStateReader.Observation observation = observePatchState(patch);
                String state = !observation.known() ? "Ready: unverified"
                    : observation.state == CropState.GROWING ? "Growing"
                    : "Needs attention: " + observation.state.name().toLowerCase();
                result.add(new PatchView(patch, access, state));
            }
            SwingUtilities.invokeLater(() ->
            {
                patchViews.clear();
                patchViews.addAll(result);
                if ("patches".equals(visiblePage)) drawPatchRows();
            });
        });
    }

    private static boolean uncertainLocation(String location)
    {
        switch (location)
        {
            case "Harmony Island":
            case "Civitas illa Fortis":
            case "Auburnvale":
            case "Kastori":
            case "Great Conch":
            case "Avium Savannah":
            case "Anglers' Retreat":
                return true;
            default: return false;
        }
    }

    private void setCategoryEnabled(List<PatchView> rows, boolean enabled)
    {
        for (PatchView row : rows) patchPrefs.setEnabled(row.patch, enabled);
        drawPatchRows();
    }

    private void drawPatchRows()
    {
        patchRows.removeAll();
        String query = patchSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        boolean onlyOff = disabledOnly.isSelected();
        Map<FarmRunType, List<PatchView>> byType = new EnumMap<>(FarmRunType.class);
        for (PatchView patch : patchViews)
        {
            if (onlyOff && patchPrefs.enabled(patch.patch)) continue;
            if (!query.isEmpty() && !patch.patch.getDisplayName()
                .toLowerCase(java.util.Locale.ROOT).contains(query)
                && !patch.patch.getPatchType().getDisplayName()
                    .toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            byType.computeIfAbsent(patch.patch.getFarmRunType(), t -> new ArrayList<>()).add(patch);
        }
        for (Map.Entry<FarmRunType, List<PatchView>> group : byType.entrySet())
        {
            JPanel category = new JPanel(new GridLayout(1, 3, 3, 0));
            category.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            JButton fold = new JButton((collapsedTypes.contains(group.getKey()) ? "▸ " : "▾ ")
                + group.getKey().getDisplayName());
            fold.addActionListener(e ->
            {
                if (!collapsedTypes.add(group.getKey())) collapsedTypes.remove(group.getKey());
                drawPatchRows();
            });
            category.add(fold);
            JButton all = new JButton("All");
            all.addActionListener(e -> setCategoryEnabled(group.getValue(), true));
            category.add(all);
            JButton none = new JButton("None");
            none.addActionListener(e -> setCategoryEnabled(group.getValue(), false));
            category.add(none);
            addFull(patchRows, category);
            if (collapsedTypes.contains(group.getKey())) continue;

            Map<String, List<PatchView>> byLocation = new LinkedHashMap<>();
            for (PatchView patch : group.getValue())
                byLocation.computeIfAbsent(patch.patch.getLocation(),
                    loc -> new ArrayList<>()).add(patch);
            for (Map.Entry<String, List<PatchView>> location : byLocation.entrySet())
            {
                JPanel locationBar = new JPanel(new GridLayout(1, 3, 3, 0));
                locationBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
                locationBar.add(label(location.getKey(), true));
                JButton enable = new JButton("+");
                enable.setToolTipText("Enable all patches at this location in this category");
                enable.addActionListener(e -> setCategoryEnabled(location.getValue(), true));
                locationBar.add(enable);
                JButton disable = new JButton("−");
                disable.setToolTipText("Disable all patches at this location in this category");
                disable.addActionListener(e -> setCategoryEnabled(location.getValue(), false));
                locationBar.add(disable);
                addFull(patchRows, locationBar);
                for (PatchView patch : location.getValue())
                {
                    JPanel entry = vertical();
                    entry.setBorder(BorderFactory.createEmptyBorder(1, 8, 7, 2));
                    JCheckBox enabled = new JCheckBox(patch.patch.getName().isEmpty()
                        ? patch.patch.getPatchType().getDisplayName()
                        : patch.patch.getName() + " · " + patch.patch.getPatchType().getDisplayName(),
                        patchPrefs.enabled(patch.patch));
                    enabled.setForeground(Color.WHITE);
                    enabled.setBackground(ColorScheme.DARK_GRAY_COLOR);
                    enabled.setEnabled(configManager.getRSProfileKey() != null);
                    enabled.addActionListener(e -> patchPrefs.setEnabled(patch.patch,
                        enabled.isSelected()));
                    addFull(entry, enabled);
                    addFull(entry, label(patch.unlock + " · " + patch.state, false));
                    addFull(patchRows, entry);
                }
            }
        }
        if (patchViews.isEmpty()) patchRows.add(warning("Loading patch observations..."));
        else if (byType.isEmpty()) patchRows.add(warning("No patches match your filters."));
        patchRows.revalidate();
        patchRows.repaint();
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
        // This is a global setting, not a control tied to the visible run choice.
        protectTrees.setVisible(true);
        autoNext.setSelected(Boolean.parseBoolean(profileValue("directorAutoNext", "true")));
        nearestRoute.setSelected(Boolean.parseBoolean(profileValue("directorNearest", "false")));
        herbloreFocus.setSelected(Boolean.parseBoolean(profileValue("directorHerblore", "false")));
        autoReplanBank.setSelected(Boolean.parseBoolean(profileValue("directorAutoReplanBank", "false")));
        readyOnly.setSelected(Boolean.parseBoolean(profileValue("directorReadyOnly", "false")));
        allowUnverified.setSelected(Boolean.parseBoolean(profileValue("directorAllowUnverified",
            Boolean.toString(config.directorIncludeUnverifiedLocations()))));
        int cap = 8;
        try
        {
            cap = Integer.parseInt(profileValue("directorWaveStopLimit", "8"));
        }
        catch (NumberFormatException ignored)
        {
            // Keep safe default for corrupted or old preferences.
        }
        maxStopsPerWave.setValue(Math.max(1, Math.min(28, cap)));
        try
        {
            bankChoice.setSelectedItem(DirectorBankStops.Bank.valueOf(
                profileValue("directorBankStop", "AUTO")));
        }
        catch (IllegalArgumentException ignored)
        {
            bankChoice.setSelectedItem(DirectorBankStops.Bank.AUTO);
        }
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
        configManager.setRSProfileConfiguration(GROUP, "directorHerblore",
            Boolean.toString(herbloreFocus.isSelected()));
        configManager.setRSProfileConfiguration(GROUP, "directorAutoReplanBank",
            Boolean.toString(autoReplanBank.isSelected()));
        configManager.setRSProfileConfiguration(GROUP, "directorReadyOnly",
            Boolean.toString(readyOnly.isSelected()));
        configManager.setRSProfileConfiguration(GROUP, "directorAllowUnverified",
            Boolean.toString(allowUnverified.isSelected()));
        configManager.setRSProfileConfiguration(GROUP, "directorWaveStopLimit",
            maxStopsPerWave.getValue().toString());
        DirectorBankStops.Bank bank = (DirectorBankStops.Bank) bankChoice.getSelectedItem();
        configManager.setRSProfileConfiguration(GROUP, "directorBankStop",
            bank == null ? "AUTO" : bank.name());
    }

    private void beginRun()
    {
        beginRun(false);
    }

    /** Viewing another run in the editor must never change the active checkpoint. */
    static DirectorRunSelection replanSelection(DirectorRunSelection active,
                                                DirectorRunSelection viewed)
    {
        return active == null ? viewed : active;
    }

    /** Replan keeps completed patch IDs so we can safely refill at a bank. */
    private void beginRun(boolean preserveProgress)
    {
        if (!preserveProgress) savePreferences();
        final DirectorRunSelection selected = replanSelection(
            preserveProgress ? activeRunSelection : null,
            (DirectorRunSelection) runType.getSelectedItem());
        if (selected == null) return;
        if (selected == DirectorRunSelection.COMPOST)
        {
            showPage("compost");
            return;
        }
        final String name = selected.name();
        final boolean custom = selected != DirectorRunSelection.MERGED &&
            (preserveProgress
                ? Boolean.parseBoolean(profileValue("directorCustom" + name, "false"))
                : customPriority.isSelected());
        final String priorityText = preserveProgress
            ? profileValue("directorPriority" + name, "") : priorities.getText();
        final String reserveText = preserveProgress
            ? profileValue("directorReserve" + name, "") : reserves.getText();
        final String presetName = preserveProgress ? activePresetName
            : (String) presetChoice.getSelectedItem();
        final boolean onlyActionable = readyOnly.isSelected();
        final boolean includeUnverified = allowUnverified.isSelected();
        if (!preserveProgress) temporaryOverrides.clear();
        final Map<String, String> localOverrides = new HashMap<>(temporaryOverrides);
        final boolean protection = protectTrees.isSelected();
        final boolean proximity = nearestRoute.isSelected();
        final boolean herbPriority = herbloreFocus.isSelected();
        final int waveCap = (Integer) maxStopsPerWave.getValue();
        final Set<String> skip = preserveProgress
            ? new HashSet<>(completedPatchKeys) : Collections.emptySet();
        final long generation = ++planningGeneration;
        start.setEnabled(false);
        replan.setEnabled(false);
        clearRoute();
        clientThread.invoke(() ->
        {
            DirectorSessionPlan calculated;
            try
            {
                calculated = compute(selected, custom, priorityText,
                    reserveText, protection, proximity, herbPriority, waveCap, skip,
                    presetName, onlyActionable, includeUnverified, localOverrides);
            }
            catch (RuntimeException problem)
            {
                log.warn("Farming Director could not generate a run", problem);
                List<String> errors = Collections.singletonList(
                    "Could not generate this run. Check RuneLite logs and reopen storage before retrying.");
                calculated = new DirectorSessionPlan(
                    new DirectorWavePlanner.Result(Collections.emptyList(),
                        Collections.emptyList(), errors),
                    new DirectorStockLedger(Collections.emptyMap(), Collections.emptyMap(),
                        Collections.emptyMap(), false, false), errors);
            }
            final DirectorSessionPlan result = calculated;
            SwingUtilities.invokeLater(() ->
            {
                if (generation != planningGeneration) return;
                currentSession = result;
                activeRunSelection = selected;
                activePresetName = presetName == null
                    ? DirectorMergedPresets.DEFAULT_NAME : presetName;
                waveIndex = 0;
                awaitingBank = false;
                currentPlan = result.firstWave();
                currentStep = 0;
                paused = false;
                runFinished = false;
                completedSteps.clear();
                if (!preserveProgress) completedPatchKeys.clear();
                persistProgress();
                activateCurrentStep();
                start.setEnabled(true);
                replan.setEnabled(true);
                render();
                showPage("guide");
                if (!currentPlan.steps.isEmpty())
                {
                    requestRoute(currentPlan.steps.get(0));
                    clientThread.invoke(this::onClientTick);
                }
            });
        });
    }

    private DirectorSessionPlan compute(DirectorRunSelection selection,
        boolean custom, String priorityText, String reserveText,
        boolean protection, boolean proximity, boolean herbPriority, int waveCap,
        Set<String> skip, String presetName, boolean onlyActionable,
        boolean includeUnverified, Map<String, String> localOverrides)
    {
        List<String> warnings = new ArrayList<>();
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            warnings.add("Log into a character before planning a run.");
            return new DirectorSessionPlan(
                new DirectorWavePlanner.Result(Collections.emptyList(),
                    Collections.emptyList(), warnings),
                new DirectorStockLedger(Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), false, false), warnings);
        }
        int level = plugin.getFarmingLevel();
        lastPlannedFarmingLevel = level;
        List<FarmRunPatch> candidates = new ArrayList<>();
        List<FarmRunPatch> configured = routes.directorPatches(selection.filter);
        if (selection == DirectorRunSelection.MERGED)
        {
            configured = DirectorMergedPresets.selected(configured,
                presets.categories(presetName));
        }
        int unknownPatchDefinitions = 0;
        int approximateDestinations = 0;

        for (FarmRunPatch grouped : configured)
        {
            PatchType type = grouped.getPatchType();
            if (!selection.includes(type)) continue;
            String access = DirectorWaypoints.accessIssue(client, type, grouped.getLocation(),
                level, includeUnverified);
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
                if (skip.contains(FarmRouteManager.key(patch)) || !patchPrefs.enabled(patch))
                    continue;
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
        boolean bankFresh = fresh(bankAt, now);
        boolean vaultFresh = fresh(vaultAt, now);
        DirectorStockLedger stockLedger = new DirectorStockLedger(
            loadout.directorInventory(), loadout.directorBank(),
            loadout.directorVault(), bankFresh, vaultFresh);
        Map<Integer, Integer> stock = stockLedger.available();
        if (!bankFresh)
        {
            warnings.add(bankAt == 0 ? "Open your bank to scan planting stock."
                : "Bank snapshot is over 6 hours old: reopen bank to refresh.");
        }
        if (!vaultFresh)
        {
            warnings.add(vaultAt == 0 ? "Open the Farming Guild Seed Vault to scan seeds."
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
            if (onlyActionable && !observation.known())
            {
                warnings.add("Ready-only: omitted unverified " + patch.getDisplayName() + ".");
                continue;
            }
            if (observation.known() && observation.state == CropState.GROWING)
            {
                if (DirectorStepEngine.supportsProtection(patch.getPatchType())
                    && protection && !Boolean.TRUE.equals(observation.protectedByFarmer))
                {
                    preplanted.add(FarmRouteManager.key(patch));
                    actionable.add(patch);
                    warnings.add("Existing " + patch.getDisplayName() +
                        " needs a gardener-protection check; no new sapling allocated.");
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
            String override = localOverrides.getOrDefault(FarmRouteManager.key(patch),
                profileValue("directorCrop_" + FarmRouteManager.key(patch), ""));
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
        Map<String, RouteTeleport> teleportForPatch = new HashMap<>();
        for (FarmRunPatch patch : candidates)
        {
            for (FarmRunPatch configuredPatch : configured)
            {
                if (configuredPatch.getLocation().equals(patch.getLocation()) &&
                    configuredPatch.getPatchType() == patch.getPatchType())
                {
                    RouteTeleport teleport = routes.teleportPreferred(selection.filter, configuredPatch);
                    teleportForPatch.put(FarmRouteManager.key(patch), teleport);
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

        Map<FarmRunType, String> categoryPriority = new EnumMap<>(FarmRunType.class);
        if (selection == DirectorRunSelection.MERGED)
        {
            for (FarmRunType category : FarmRunType.values())
            {
                String base = individualMode(category);
                String saved = profileValue("directorCustom" + base, "false");
                if (Boolean.parseBoolean(saved))
                    categoryPriority.put(category, profileValue("directorPriority" + base, ""));
                String local = profileValue("directorMergedPriority_" + presetName
                    + "_" + category.name(), "");
                if (!local.isEmpty()) categoryPriority.put(category, local);
            }
        }

        Map<Integer, Integer> equippedItems = loadout.directorEquipment();
        DirectorWavePlanner.Result wavePlan = DirectorWavePlanner.split(candidates, level,
            stock, custom, priorityText, reserveText, toolSlots, distinctTeleports.size(),
            harvestReserve, protection, id ->
                itemManager.getItemComposition(id).isStackable(), preplanted,
                harvestOnly, cropOverrides, herbPriority, waveCap,
            selectedPatches ->
            {
                Set<RouteTeleport> carriedTeleports = new HashSet<>();
                for (FarmRunPatch selectedPatch : selectedPatches)
                {
                    RouteTeleport teleport = teleportForPatch.getOrDefault(
                        FarmRouteManager.key(selectedPatch), RouteTeleport.NONE);
                    if (teleport == RouteTeleport.NONE) continue;
                    boolean equipped = false;
                    for (int id : teleport.itemIds())
                    {
                        if (equippedItems.getOrDefault(id, 0) > 0)
                        {
                            equipped = true;
                            break;
                        }
                    }
                    if (!equipped) carriedTeleports.add(teleport);
                }
                return carriedTeleports.size();
            }, categoryPriority);

        List<Map<RouteTeleport, Integer>> travelRequirements =
            DirectorTeleportPlan.perWave(wavePlan, teleportForPatch);
        Map<Integer, Integer> travelStock = new HashMap<>(stock);
        equippedItems.forEach((id, amount) -> travelStock.merge(id, amount, Integer::sum));
        warnings.addAll(DirectorTeleportPlan.missingTravelItems(
            DirectorTeleportPlan.totalRequirements(travelRequirements), travelStock));

        // A manifest is calculated per loadout, not across the full itinerary.
        // Protection and teleport requirements may change as the run progresses.
        for (DirectorPlanner.Plan wave : wavePlan.waves)
        {
            DirectorStockLedger.Manifest manifest = stockLedger.manifest(wave);
            if (!manifest.missing.isEmpty())
            {
                warnings.add("Some wave items are not in currently scanned stock: "
                    + manifest.missing + ". Refresh bank/vault and replan.");
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
        warnings.addAll(wavePlan.warnings);
        return new DirectorSessionPlan(wavePlan, stockLedger, warnings,
            travelRequirements);
    }

    private static String individualMode(FarmRunType type)
    {
        switch (type)
        {
            case HERB: return DirectorRunSelection.HERBS.name();
            case ALLOTMENT_FLOWER: return DirectorRunSelection.ALLOTMENTS.name();
            case TREE: return DirectorRunSelection.TREES.name();
            case FRUIT_TREE_CALQUAT: return DirectorRunSelection.FRUIT_TREES.name();
            case HARDWOOD_TREE: return DirectorRunSelection.HARDWOODS.name();
            case HOPS: return DirectorRunSelection.HOPS.name();
            case BUSH: return DirectorRunSelection.BUSHES.name();
            case CACTUS: return DirectorRunSelection.CACTUS.name();
            default: return DirectorRunSelection.SPECIAL.name();
        }
    }

    private void persistProgress()
    {
        if (configManager.getRSProfileKey() == null) return;
        if (activeRunSelection == null) return;
        configManager.setRSProfileConfiguration(GROUP, "directorSavedMode", activeRunSelection.name());
        configManager.setRSProfileConfiguration(GROUP, "directorSavedPreset", activePresetName);
        configManager.setRSProfileConfiguration(GROUP, "directorCompletedKeys",
            DirectorRunPersistence.encode(completedPatchKeys));
    }

    private void clearSavedRun()
    {
        if (configManager.getRSProfileKey() == null) return;
        configManager.unsetRSProfileConfiguration(GROUP, "directorSavedMode");
        configManager.unsetRSProfileConfiguration(GROUP, "directorSavedPreset");
        configManager.unsetRSProfileConfiguration(GROUP, "directorCompletedKeys");
    }

    /** Called when a banking or seed-vault interface closes, on the client thread. */
    void onStorageClosed()
    {
        final boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
        SwingUtilities.invokeLater(() ->
        {
            if (autoReplanBank.isSelected() && currentPlan != null && !runFinished
                && loggedIn)
            {
                beginRun(true);
            }
        });
    }

    private void resumeSavedRun()
    {
        String mode = profileValue("directorSavedMode", "");
        if (mode.isEmpty()) return;
        try
        {
            DirectorRunSelection selected = DirectorRunSelection.valueOf(mode);
            loadingPreferences = true;
            runType.setSelectedItem(selected);
            loadingPreferences = false;
            loadPreferences();
            activeRunSelection = selected;
            activePresetName = profileValue("directorSavedPreset",
                DirectorMergedPresets.DEFAULT_NAME);
            completedPatchKeys.clear();
            completedPatchKeys.addAll(DirectorRunPersistence.decode(
                profileValue("directorCompletedKeys", "")));
            beginRun(true);
        }
        catch (IllegalArgumentException ignored)
        {
            clearSavedRun();
        }
    }

    private void chooseCropOverride()
    {
        if (currentPlan == null || runFinished || currentStep >= currentPlan.steps.size()) return;
        FarmRunPatch patch = currentPlan.steps.get(currentStep).patch;
        List<Crop> eligible = new ArrayList<>();
        for (Crop crop : CropCatalog.crops(patch.getPatchType()))
        {
            if (crop.getLevel() <= lastPlannedFarmingLevel) eligible.add(crop);
        }
        if (eligible.isEmpty()) return;
        String[] options = eligible.stream().map(Crop::getName).toArray(String[]::new);
        Object chosen = JOptionPane.showInputDialog(this,
            "Preferred crop at " + patch.getDisplayName() + ":",
            "Ironman Farming Director — crop override",
            JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (chosen != null)
        {
            Object persistence = JOptionPane.showInputDialog(this,
                "Apply this crop override:", "Crop preference scope",
                JOptionPane.PLAIN_MESSAGE, null,
                new String[]{"Current run only", "Save for future runs"},
                "Current run only");
            if (persistence == null) return;
            String key = FarmRouteManager.key(patch);
            if ("Current run only".equals(persistence))
            {
                temporaryOverrides.put(key, chosen.toString());
            }
            else if (configManager.getRSProfileKey() != null)
            {
                temporaryOverrides.remove(key);
                configManager.setRSProfileConfiguration(GROUP,
                    "directorCrop_" + key, chosen.toString());
            }
            else return;
            beginRun(true);
        }
    }

    private void clearCropOverride()
    {
        if (currentPlan == null || runFinished || currentStep >= currentPlan.steps.size()) return;
        FarmRunPatch patch = currentPlan.steps.get(currentStep).patch;
        temporaryOverrides.remove(FarmRouteManager.key(patch));
        if (configManager.getRSProfileKey() != null)
            configManager.unsetRSProfileConfiguration(GROUP,
                "directorCrop_" + FarmRouteManager.key(patch));
        beginRun(true);
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
            patch.getPatchType().name(), patch.getLocation(), patch.getName(),
            target.exact ? point : null);

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
            DirectorStepEngine.supportsProtection(step.patch.getPatchType()) && protectTrees.isSelected(),
            observation.protectedByFarmer);
        boolean changed = visibleObservation == null ||
            !Objects.equals(visibleObservation.state, observation.state) ||
            visibleObservation.source != observation.source ||
            !Objects.equals(visibleObservation.protectedByFarmer, observation.protectedByFarmer) ||
            action != visibleAction;
        visibleObservation = observation;
        visibleAction = action;
        if (observation.source == QuestFarmingStateReader.Source.LIVE &&
            (observation.state == CropState.HARVESTABLE ||
             observation.state == CropState.UNCHECKED ||
             observation.state == CropState.DEAD ||
             observation.state == CropState.DISEASED ||
             observation.state == CropState.STUMP))
        {
            sawHarvestable = true;
        }
        if (autoNext.isSelected() && DirectorStepEngine.canFinishHarvestOnly(
            step.harvestOnly, sawHarvestable, observation.state,
            observation.source == QuestFarmingStateReader.Source.LIVE))
        {
            moveStep(1, true);
            return;
        }
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
        sawHarvestable = false;
        visibleObservation = null;
        visibleAction = DirectorStepEngine.Action.INSPECT;
    }

    private void moveStep(int delta, boolean automatic)
    {
        if (currentPlan == null || paused) return;
        if (awaitingBank)
        {
            if (delta > 0)
            {
                beginNextWave();
            }
            else if (delta < 0 && !currentPlan.steps.isEmpty())
            {
                awaitingBank = false;
                currentStep = currentPlan.steps.size() - 1;
                completedSteps.remove(currentStep);
                completedPatchKeys.remove(FarmRouteManager.key(
                    currentPlan.steps.get(currentStep).patch));
                persistProgress();
                activateCurrentStep();
                render();
                requestRoute(currentPlan.steps.get(currentStep));
            }
            return;
        }
        if (currentPlan.steps.isEmpty()) return;
        int previous = currentStep;
        int nextIndex = Math.max(0, Math.min(currentPlan.steps.size(), currentStep + delta));
        if (previous == nextIndex) return;

        if (delta > 0)
        {
            completedSteps.add(previous);
            completedPatchKeys.add(FarmRouteManager.key(currentPlan.steps.get(previous).patch));
            persistProgress();
        }
        else
        {
            completedSteps.remove(nextIndex);
            completedPatchKeys.remove(FarmRouteManager.key(currentPlan.steps.get(nextIndex).patch));
            persistProgress();
        }
        currentStep = nextIndex;
        if (currentStep >= currentPlan.steps.size())
        {
            if (currentSession != null && waveIndex + 1 < currentSession.waves.size())
            {
                awaitingBank = true;
                suggestedBank = null;
                clearRoute();
                requestBankRoute();
                render();
                return;
            }
            runFinished = true;
            if (currentSession == null || currentSession.unplanned == 0)
            {
                clearSavedRun();
            }
            clearRoute();
            render();
            return;
        }
        runFinished = false;
        activateCurrentStep();
        render();
        requestRoute(currentPlan.steps.get(currentStep));
        clientThread.invoke(this::onClientTick);
    }

    /** Manual restocking checkpoint, never clicks the bank or withdraws items. */
    private void beginNextWave()
    {
        if (!awaitingBank || currentSession == null ||
            waveIndex + 1 >= currentSession.waves.size()) return;
        ++waveIndex;
        currentPlan = currentSession.waves.get(waveIndex);
        awaitingBank = false;
        suggestedBank = null;
        currentStep = 0;
        runFinished = false;
        completedSteps.clear();
        activateCurrentStep();
        render();
        if (!currentPlan.steps.isEmpty())
        {
            requestRoute(currentPlan.steps.get(0));
            clientThread.invoke(this::onClientTick);
        }
    }

    private void requestBankRoute()
    {
        final long generation = ++routeGeneration;
        final DirectorBankStops.Bank preferred =
            (DirectorBankStops.Bank) bankChoice.getSelectedItem();
        clientThread.invoke(() ->
        {
            if (generation != routeGeneration || !awaitingBank || paused ||
                client.getLocalPlayer() == null) return;
            WorldPoint player = client.getLocalPlayer().getWorldLocation();
            DirectorBankStops.Bank bank = DirectorBankStops.choose(
                preferred, player, plugin.getFarmingLevel());
            SwingUtilities.invokeLater(() ->
            {
                if (generation == routeGeneration && awaitingBank)
                {
                    suggestedBank = bank;
                    render();
                }
            });
            if (bank != null && bank.point != null)
            {
                Map<String, Object> data = new HashMap<>();
                data.put("start", player);
                data.put("target", bank.point);
                eventBus.post(new PluginMessage("shortestpath", "path", data));
                routeIssued = true;
            }
        });
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
        awaitingBank = false;
        currentStep = targetIndex;
        runFinished = false;
        if (targetIndex < previous)
        {
            completedSteps.remove(targetIndex);
            completedPatchKeys.remove(FarmRouteManager.key(currentPlan.steps.get(targetIndex).patch));
            persistProgress();
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
        else if (awaitingBank)
        {
            requestBankRoute();
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
        currentSession = null;
        activeRunSelection = null;
        waveIndex = 0;
        awaitingBank = false;
        suggestedBank = null;
        paused = false;
        currentStep = 0;
        runFinished = false;
        completedSteps.clear();
        completedPatchKeys.clear();
        temporaryOverrides.clear();
        clearSavedRun();
        activateCurrentStep();
        clearRoute();
        render();
    }

    void resetCharacter()
    {
        ++planningGeneration;
        SwingUtilities.invokeLater(() ->
        {
            currentPlan = null;
            currentSession = null;
            activeRunSelection = null;
            waveIndex = 0;
            awaitingBank = false;
            suggestedBank = null;
            currentStep = 0;
            paused = false;
            runFinished = false;
            completedSteps.clear();
            completedPatchKeys.clear();
            temporaryOverrides.clear();
            activateCurrentStep();
            loadPreferences();
            reloadPresetNames();
            showPage("selection");
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
        int waveCount = currentSession == null ? 0 : currentSession.waves.size();
        String wave = waveCount > 0 ? "Wave " + (waveIndex + 1) + " / " + waveCount : "No wave";
        progress.setText(!active ? "Configure a run and press Start"
            : awaitingBank ? "Bank checkpoint · " + wave
            : runFinished ? (currentSession != null && currentSession.unplanned > 0
                ? "Partial run · unplanned patches remain"
                : "All planned patches completed")
            : steps == 0 ? "No planting steps allocated"
            : wave + " · patch " + (currentStep + 1) + " / " + steps
                + (paused ? " (paused)" : ""));
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
            actionText = currentSession != null && currentSession.unplanned > 0
                ? "The last loadout is finished, but " + currentSession.unplanned
                    + " patch(es) could not be planned. Restock and choose Replan remaining."
                : "All planned patches completed. Choose Start for another run.";
        }
        else if (awaitingBank)
        {
            actionText = "Bank before the next loadout. "
                + (suggestedBank == null ? "Choose a bank, or use your preferred teleport."
                    : "Suggested bank area: " + suggestedBank.label + ".")
                + " Deposit harvested produce, prepare the next supplies, then choose "
                + "Next wave or Replan remaining after scanning storage.";
        }
        else
        {
            DirectorPlanner.Step current = currentPlan.steps.get(currentStep);
            actionText = current.harvestOnly && visibleAction == DirectorStepEngine.Action.PLANT
                ? "Harvest/clearing complete or patch empty. No replacement seed "
                    + "allocated; press Next, or restock and Replan remaining."
                : DirectorStepEngine.instruction(visibleAction, current.crop,
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
        next.setEnabled(active && !runFinished && (awaitingBank || currentStep < steps));
        next.setText(awaitingBank ? "Next wave" : "Next step");
        next.setToolTipText(awaitingBank
            ? "Continue after banking. No items are withdrawn automatically." : null);
        pause.setEnabled(active && steps > 0 && !runFinished);
        end.setEnabled(active);
        replan.setEnabled(active && currentSession != null);
        resume.setEnabled(!profileValue("directorSavedMode", "").isEmpty());
        overrideCrop.setEnabled(active && !runFinished && !awaitingBank && steps > 0);
        clearOverride.setEnabled(active && !runFinished && !awaitingBank && steps > 0);
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
                JPanel stepHeader = new JPanel(new BorderLayout());
                stepHeader.setBackground(item.getBackground());
                stepHeader.add(place, BorderLayout.CENTER);
                if (i == currentStep && !runFinished)
                {
                    JButton edit = new JButton("✎");
                    edit.setToolTipText("Set a permanent crop preference for this patch");
                    edit.addActionListener(e -> chooseCropOverride());
                    stepHeader.add(edit, BorderLayout.EAST);
                }
                item.add(stepHeader);
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
                        boolean exactTile = step.patch.getPatchType() == PatchType.HERB ||
                            DirectorStepEngine.isTreeType(step.patch.getPatchType());
                        item.add(label((exactTile ? "Patch: " : "Nearby farming area: ")
                            + point.getX() + ", " + point.getY()
                            + " · Shortest Path if installed", false));
                    }
                    else
                    {
                        item.add(label("Approximate area or observed patch; "
                            + "exact navigation may be unavailable.", false));
                    }
                }
                stepList.add(item);
            }
            if (awaitingBank && currentSession != null &&
                waveIndex + 1 < currentSession.waves.size())
            {
                DirectorPlanner.Plan nextWave = currentSession.waves.get(waveIndex + 1);
                stepList.add(warning("NEXT LOADOUT · " + nextWave.steps.size()
                    + " patch stops. Bank/Seed Vault withdrawal amounts are estimates "
                    + "until you reopen storage and Replan remaining."));
                stepList.add(warning("Next seeds and saplings: "
                    + formatItems(nextWave.seeds)));
                if (!nextWave.payments.isEmpty())
                {
                    stepList.add(warning("Potential gardener payments: "
                        + formatItems(nextWave.payments)));
                }
                if (waveIndex + 1 < currentSession.teleports.size())
                {
                    stepList.add(warning("Next wave travel items: "
                        + formatTeleports(currentSession.teleports.get(waveIndex + 1))));
                }
            }
            if (!currentPlan.seeds.isEmpty())
                stepList.add(warning("Required planting stock: " + formatItems(currentPlan.seeds)));
            if (guideDetails.isSelected())
            {
            if (!currentPlan.payments.isEmpty())
            {
                stepList.add(warning("Potential gardener payments: "
                    + formatItems(currentPlan.payments)));
            }
            if (currentSession != null && waveIndex < currentSession.teleports.size())
            {
                stepList.add(warning("Travel items for this wave (configured): "
                    + formatTeleports(currentSession.teleports.get(waveIndex))));
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
            if (currentSession != null && waveIndex == 0)
            {
                DirectorStockLedger.Manifest manifest =
                    currentSession.observedStock.manifest(currentPlan);
                if (!manifest.takeFromBank.isEmpty())
                {
                    stepList.add(warning("Bank withdrawals (last scan): "
                        + formatItems(manifest.takeFromBank)));
                }
                if (!manifest.takeFromVault.isEmpty())
                {
                    stepList.add(warning("Seed Vault withdrawals (last scan): "
                        + formatItems(manifest.takeFromVault)));
                }
                if (!manifest.missing.isEmpty())
                {
                    stepList.add(warning("Unaccounted supplies: "
                        + formatItems(manifest.missing)));
                }
            }
            for (String message : currentPlan.warnings)
            {
                stepList.add(warning(message));
            }
            if (currentSession != null)
            {
                if (currentSession.unplanned > 0)
                {
                    stepList.add(warning(currentSession.unplanned
                        + " patch(es) remain UNPLANNED; they have not been marked completed."));
                }
                int displayed = 0;
                for (String message : currentSession.warnings)
                {
                    if (displayed++ == 20)
                    {
                        stepList.add(warning("Additional warnings omitted from sidebar."));
                        break;
                    }
                    stepList.add(warning(message));
                }
            }
            }
        }
        else
        {
            stepList.add(warning("Choose a run on the Runs page to get guided steps. "
                + "The patch manager and settings can be opened without ending a run."));
        }
        stepList.revalidate();
        stepList.repaint();
    }

    private static String formatTeleports(Map<RouteTeleport, Integer> items)
    {
        if (items == null || items.isEmpty()) return "Use normal routes";
        StringBuilder details = new StringBuilder();
        for (Map.Entry<RouteTeleport, Integer> entry : items.entrySet())
        {
            if (details.length() > 0) details.append(", ");
            details.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        return details.toString();
    }

    private static String formatItems(Map<Integer, Integer> items)
    {
        if (items == null || items.isEmpty()) return "None";
        StringBuilder details = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : items.entrySet())
        {
            if (details.length() > 0) details.append(", ");
            Crop crop = CropCatalog.findByItemId(entry.getKey());
            details.append(crop == null ? "item #" + entry.getKey() : crop.getItemName());
            details.append(" x").append(entry.getValue());
        }
        return details.toString();
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
