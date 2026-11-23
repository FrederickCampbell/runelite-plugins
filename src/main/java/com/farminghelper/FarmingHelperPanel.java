package com.farminghelper;

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
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;
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
 * Quest Helper-style step-by-step farming sidebar with a live supply checklist.
 * Existing farming data and storage observations remain shared with the planner.
 * All client state is read on the RuneLite client thread.
 */
@Singleton
@Slf4j
final class FarmingHelperPanel extends PluginPanel
{
    private static final String GROUP = "farming-helper";
    private static final long FRESH_MILLIS = 6L * 60 * 60 * 1000;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("MMM d, h:mm a")
        .withZone(ZoneId.systemDefault());
    private final Client client;
    private final ClientThread clientThread;
    private final EventBus eventBus;
    private final ConfigManager configManager;
    private final ItemManager itemManager;
    private final FarmingLoadout loadout;
    private final FarmingHelperPlugin plugin;
    private final FarmingHelperConfig config;
    private final FarmRouteManager routes;
    private final QuestFarmingStateReader stateReader;
    private final PatchTimerManager timerManager;
    private final PatchPreferences patchPrefs;
    private final PatchPriorityStore priorityStore;
    private final javax.swing.JCheckBox diagnosticsEnabled = new javax.swing.JCheckBox("Debug diagnostics");
    private final FarmingHelperDiagnostics diagnostics = new FarmingHelperDiagnostics();
    private final MergedPresets presets;
    private final CompostBinManager compostBins;
    private final FarmingContractManager contractManager;

    private final JComboBox<RunSelection> runType =
        new JComboBox<>(new RunSelection[] {
            RunSelection.HERBS, RunSelection.ALLOTMENTS,
            RunSelection.TREES, RunSelection.FRUIT_TREES,
            RunSelection.HARDWOODS, RunSelection.HOPS,
            RunSelection.BUSHES, RunSelection.CACTUS,
            RunSelection.SPECIAL, RunSelection.COMPOST,
            RunSelection.MERGED, RunSelection.ALL
        });
    private final JCheckBox payRemoval = new JCheckBox("Pay gardener to remove trees", true);
    private boolean activePayRemoval;
    private final JCheckBox protectTrees = new JCheckBox("Pay for tree protection when possible", true);
    private final JComboBox<CompostChoice> compostChoice = new JComboBox<>(CompostChoice.values());
    private final java.util.LinkedHashSet<StepEngine.Action> observedInteractions = new java.util.LinkedHashSet<>();
    private boolean nearCurrentTarget;
    private final JSpinner harvestSlots = new JSpinner(new SpinnerNumberModel(-1, -1, 26, 1));
    private final JCheckBox autoNext = new JCheckBox("Auto-next after patch completion", true);
    private final JCheckBox nearestRoute = new JCheckBox("Nearest-stop order (approximate)", false);
    private final JCheckBox herbloreFocus = new JCheckBox("Prioritize Herblore supplies (preset)", false);
    private final JCheckBox autoReplanBank = new JCheckBox("Replan after banking", false);
    private final JComboBox<BankStops.Bank> bankChoice =
        new JComboBox<>(BankStops.Bank.values());
    private final JButton replan = new JButton("Replan remaining");
    private final JButton resume = new JButton("Resume saved run");
    private final JButton overrideCrop = new JButton("Override this patch");
    private final JButton clearOverride = new JButton("Clear override");
    private final JLabel currentAction = new JLabel("Start a run to see context-aware steps");
    private final JLabel activeTitle = new JLabel("Active guide");
    private final javax.swing.JProgressBar progressBar = new javax.swing.JProgressBar(0, 1);
    private final JButton returnGuide = new JButton("Resume active guide");
    private final JLabel selectionStatus = new JLabel();
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
    private final JTextField patchSearch = new FarmingHelperComponents.SearchField();
    private final Set<PatchType> advancedPatchTypes = EnumSet.noneOf(PatchType.class);
    private final Map<PatchType, javax.swing.Icon> patchIcons = new EnumMap<>(PatchType.class);
    private JScrollPane patchScroll;
    private final javax.swing.Timer patchRefresh = new javax.swing.Timer(30000, e -> { if ("patches".equals(this.visiblePage)) refreshPatchManager(); });
    private final JCheckBox disabledOnly = new JCheckBox("Show disabled only");
    private final JCheckBox actionableOnly = new JCheckBox("Needs attention only");
    private final JLabel patchSummary = new JLabel();
    private final JLabel guideWarning = new JLabel();
    private final JCheckBox readyOnly = new JCheckBox("Only include ready / actionable patches");
    private final JCheckBox allowUnverified = new JCheckBox("Include unverified-access locations");
    private final JCheckBox shortestPathEnabled = new JCheckBox("Send navigation to Shortest Path", true);
    private volatile boolean navigationEnabled = true;
    private final JPanel compostRows = new JPanel();
    private final JLabel compostProgress = new JLabel("Compost Run");
    private final JLabel compostAction = new JLabel("Inspect a bin to begin.");
    private int compostIndex;
    private volatile long compostGeneration;
    private volatile boolean compostPageOpen;
    private List<CompostBinLocation> compostStops = Collections.emptyList();
    private final JPanel statusRows = new JPanel();
    private final JPanel loadoutRows = new JPanel();
    private final JPanel guideRequirements = new JPanel();
    private final JButton requirementsToggle = FarmingHelperComponents.row("▾ Supplies");
    private final JButton beginWithMissing = new JButton("Begin with current supplies");
    private String requirementsSignature = "";
    private boolean requirementsExpanded = true;
    private final JButton openLoadout = new JButton("View loadout & withdrawals");
    private final JLabel activeDestination = new JLabel();
    private final JLabel routeStatus = new JLabel();
    private final JButton routeOptions = new JButton("Navigation…");
    private final javax.swing.DefaultListModel<FarmRunPatch> routeModel =
        new javax.swing.DefaultListModel<>();
    private final javax.swing.JList<FarmRunPatch> routeList =
        new javax.swing.JList<>(routeModel);
    private final JComboBox<RouteTeleport> routeTeleportChoice = new JComboBox<>();
    private final Map<String, RouteTeleport> routeEdits = new LinkedHashMap<>();
    private FarmRunFilter routeEditFilter = FarmRunFilter.HERB;
    private boolean updatingRouteChoice;
    private final JCheckBox guideDetails = new JCheckBox("Show planning details and warnings");
    private final JComboBox<String> presetChoice = new JComboBox<>();
    private final Map<FarmRunType, JCheckBox> mergedTypes = new EnumMap<>(FarmRunType.class);
    private final Set<PatchType> collapsedTypes = EnumSet.allOf(PatchType.class);
    private final Set<PatchType> openPatchLocations = EnumSet.noneOf(PatchType.class);
    private int patchSettingsLevel;
    private boolean patchSettingsLoggedIn;
    private final Map<RunSelection, JPanel> runBodies = new EnumMap<>(RunSelection.class);
    private final Map<RunSelection, JButton> runHeaders = new EnumMap<>(RunSelection.class);
    private final Map<RunSelection, javax.swing.Icon> runIcons = new EnumMap<>(RunSelection.class);
    private final Set<String> expandedLocations = new HashSet<>();
    private RunSelection expandedRun;
    private JPanel inlineEditor;
    private boolean loadingMerged;
    private boolean settingsReady;
    private final JCheckBox worldHints = new JCheckBox("World and interaction hints", true);
    private final JPanel mergedControls = new JPanel();
    private final List<PatchView> patchViews = new ArrayList<>();
    private RunSelection activeRunSelection;
    private volatile String activeProfile;
    private String activePresetName = MergedPresets.DEFAULT_NAME;
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
    private volatile RunPlanner.Plan currentPlan;
    private volatile WorldGuide.Target worldTarget;
    private volatile boolean directorRunDisplayed;
    private volatile boolean preparingLoadout;
    private final JButton leaveLoadout = new JButton("Begin farming");
    private final JLabel loadoutReady = new JLabel();
    private final JPanel advancedGuideControls = new JPanel();
    private SessionPlan currentSession;
    private int waveIndex;
    private boolean awaitingBank;
    private boolean sawHarvestable;
    private boolean sawLiveEmpty;
    private BankStops.Bank suggestedBank;
    private volatile int currentStep;
    private volatile boolean paused;
    private volatile boolean runFinished;
    private boolean observedInitialState;
    private boolean initiallyGrowing;
    private volatile long activatedAtMillis;
    private QuestFarmingStateReader.Observation visibleObservation;
    private StepEngine.Action visibleAction = StepEngine.Action.INSPECT;
    private final Set<Integer> completedSteps = new HashSet<>();
    private final Set<String> completedPatchKeys = new HashSet<>();
    private final Map<String, String> temporaryOverrides = new HashMap<>();
    private volatile long planningGeneration;
    private volatile boolean planningInFlight;
    private volatile int lastPlannedFarmingLevel;
    private volatile long routeGeneration;
    private volatile boolean routeIssued;
    private boolean loadingPreferences;

    @Inject
    FarmingHelperPanel(Client client, ClientThread clientThread, EventBus eventBus,
                         ConfigManager configManager, ItemManager itemManager, FarmingLoadout loadout,
                         FarmingHelperPlugin plugin, FarmingHelperConfig config,
                         FarmRouteManager routes, QuestFarmingStateReader stateReader, PatchTimerManager timerManager,
                         PatchPreferences patchPrefs, MergedPresets presets,
                         CompostBinManager compostBins,
                         FarmingContractManager contractManager)
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
        this.priorityStore = new PatchPriorityStore(configManager);
        this.presets = presets;
        this.compostBins = compostBins;
        this.contractManager = contractManager;

        setLayout(new BorderLayout());
        setBackground(FarmingHelperTheme.BASE);
        buildPages();
        FarmingHelperTheme.style(this);
        add(pages, BorderLayout.CENTER);
        FarmingHelperTheme.style(pages);
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
        settingsReady = true;
        render();
        showPage("selection");
    }

    /** A single RuneLite-native sidebar with persistent navigation between pages. */
    private void buildPages()
    {
        pages.setBackground(FarmingHelperTheme.BASE);
        pages.add(createSelection(), "selection");
        pages.add(createGuide(), "guide");
        pages.add(createPatchManager(), "patches");
        pages.add(createSettings(), "settings");
        pages.add(createCompostGuide(), "compost");
        pages.add(createRouteEditor(), "route");
        pages.add(createStatusPage(), "status");
        pages.add(createLoadoutPage(), "loadout");
    }

    private JPanel vertical()
    {
        return FarmingHelperComponents.column(6);
    }

    private JScrollPane scrolling(JPanel content)
    {
        JScrollPane scroll = new JScrollPane(new SidebarViewport(content));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(FarmingHelperTheme.BASE);
        FarmingHelperTheme.style(scroll);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private void addFull(JPanel parent, Component child)
    {
        FarmingHelperComponents.addFull(parent, child);
    }

    private JButton nav(String caption, String page)
    {
        JButton button = FarmingHelperComponents.row(caption);
        button.addActionListener(e -> showPage(page));
        return button;
    }

    private void showPage(String name)
    {
        if ("editor".equals(name)) name = runType.getSelectedItem() == RunSelection.MERGED ? "selection" : "patches";
        boolean leavingCompost = compostPageOpen && !"compost".equals(name);
        compostPageOpen = "compost".equals(name);
        if (leavingCompost)
        {
            ++compostGeneration;
            clearRoute();
            if ("guide".equals(name) && currentPlan != null && !paused && !runFinished)
            {
                if (awaitingBank) requestBankRoute();
                else if (currentStep < currentPlan.steps.size())
                    requestRoute(currentPlan.steps.get(currentStep));
            }
        }
        visiblePage = name;
        if ("patches".equals(name)) patchRefresh.start(); else patchRefresh.stop();
        pageLayout.show(pages, name);
        if ("patches".equals(name)) refreshPatchManager();
        if ("selection".equals(name)) updateRunCards();
        if ("compost".equals(name)) refreshCompostGuide();
        if ("route".equals(name)) loadRouteEditor();
        if ("status".equals(name)) refreshStatusPage();
        publishWorldTarget();
        publishBankChecklist();
        if ("loadout".equals(name)) refreshLoadoutPage();
        if ("guide".equals(name)) refreshGuideRequirements();
    }

    private JPanel createSelection()
    {
        JPanel list = vertical();
        
        JButton cog = nav("⚙", "settings");
        cog.setToolTipText("Settings for every run");
        cog.setPreferredSize(new Dimension(31, 29));
        FarmingHelperTheme.role(cog,"quiet");
        addFull(list, FarmingHelperComponents.header("Farming Helper", null, cog));
        addFull(list, selectionStatus);
        resume.addActionListener(e -> resumeSavedRun());
        addFull(list, resume);
        returnGuide.addActionListener(e ->
        {
            if (currentPlan == null) return;
            showPage("guide");
            if (!paused && !runFinished)
            {
                if (awaitingBank) requestBankRoute();
                else if (currentStep < currentPlan.steps.size()) requestRoute(currentPlan.steps.get(currentStep));
            }
        });
        addFull(list, returnGuide);
        addFull(list, FarmingHelperComponents.caption("STANDARD RUNS"));
        inlineEditor = createEditorControls();
        for (int i = 0; i < runType.getItemCount(); i++)
        {
            RunSelection mode = runType.getItemAt(i);
            if (mode == RunSelection.MERGED) addFull(list, FarmingHelperComponents.caption("COMBINED RUNS"));
            JPanel card = new JPanel(new BorderLayout(0, 1));
            card.setBackground(FarmingHelperTheme.SURFACE);
            JButton fold = FarmingHelperComponents.row("");
            fold.setHorizontalAlignment(JButton.LEFT);
            fold.setToolTipText((mode == RunSelection.MERGED ? "Configure " : "Start ") + mode);
            fold.addActionListener(e ->
            {
                if (mode == RunSelection.MERGED) openRun(mode, true);
                else { runType.setSelectedItem(mode); beginRun(); }
            });
            runHeaders.put(mode, fold);
            card.add(fold, BorderLayout.NORTH);
            JPanel body = new JPanel(new BorderLayout());
            body.setBackground(FarmingHelperTheme.SURFACE);
            body.setVisible(false);
            runBodies.put(mode, body);
            card.add(body, BorderLayout.CENTER);
            addFull(list, card);
            int iconId = runIcon(mode);
            if (iconId > 0) clientThread.invoke(() ->
            {
                net.runelite.client.util.AsyncBufferedImage image = itemManager.getImage(iconId);
                if (image != null) image.onLoaded(() -> SwingUtilities.invokeLater(() ->
                {
                    runIcons.put(mode, FarmingHelperComponents.sprite(image));
                    fold.setIcon(runIcons.get(mode));
                }));
            });
        }
        addFull(list, new javax.swing.JSeparator());
        addFull(list, nav("Patch Settings", "patches"));
        addFull(list, nav("Timers & contracts", "status"));
        updateRunCards();
        JPanel page = new JPanel(new BorderLayout());
        page.add(scrolling(list), BorderLayout.CENTER);
        return page;
    }

    private static int runIcon(RunSelection mode)
    {
        switch (mode)
        {
            case HERBS: return ItemID.RANARR_SEED;
            case TREES: return ItemID.PLANTPOT_YEW_SAPLING;
            case FRUIT_TREES: return ItemID.PLANTPOT_PAPAYA_SAPLING;
            case HARDWOODS: return ItemID.PLANTPOT_TEAK_SAPLING;
            case ALLOTMENTS: return ItemID.SNAPE_GRASS_SEED;
            case HOPS: return ItemID.JUTE_SEED;
            case BUSHES: return ItemID.WHITEBERRY_BUSH_SEED;
            case CACTUS: return ItemID.CACTUS_SEED;
            case SPECIAL: return ItemID.MUSHROOM_SEED;
            case COMPOST: return ItemID.BUCKET_ULTRACOMPOST;
            default: return ItemID.DIBBER;
        }
    }

    private void openRun(RunSelection mode, boolean toggle)
    {
        if (toggle && expandedRun == mode) expandedRun = null;
        else
        {
            expandedRun = mode;
            runType.setSelectedItem(mode);
        }
        showEditorMode();
        updateRunCards();
    }

    private void updateRunCards()
    {
        if (inlineEditor == null) return;
        if (inlineEditor.getParent() != null) inlineEditor.getParent().remove(inlineEditor);
        for (Map.Entry<RunSelection, JPanel> e : runBodies.entrySet())
        {
            boolean open = e.getKey() == expandedRun;
            e.getValue().setVisible(open);
            JButton header = runHeaders.get(e.getKey());
            header.setText(e.getKey() == RunSelection.MERGED ? (open ? "▾ Custom run" : "▸ Custom run") : e.getKey().toString().replace(" Run", ""));
            header.putClientProperty("fh.primary", open);
            FarmingHelperTheme.role(header, "quiet");
            if (open) e.getValue().add(inlineEditor, BorderLayout.CENTER);
            e.getValue().revalidate();
        }
        revalidate();
        repaint();
    }

    private JPanel createGuide()
    {
        JPanel guide = new JPanel(new BorderLayout(0, 7));
        guide.setBackground(FarmingHelperTheme.BASE);
        JPanel header = vertical();
        JPanel menu = new JPanel(new GridLayout(1, 3, 3, 0));
        menu.setBackground(FarmingHelperTheme.BASE);
        menu.add(nav("Runs", "selection"));
        menu.add(nav("Patches", "patches"));
        menu.add(nav("Settings", "settings"));
        addFull(header, menu);
        activeTitle.setFont(activeTitle.getFont().deriveFont(Font.BOLD, 15f));
        currentAction.setForeground(FarmingHelperTheme.SUCCESS);
        progress.setForeground(FarmingHelperTheme.GOLD);
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(120, 173, 113));
        addFull(header, activeTitle);
        addFull(header, progressBar);
        addFull(header, progress);
        activeDestination.setFont(activeDestination.getFont().deriveFont(Font.BOLD, 13f));
        activeDestination.setForeground(Color.WHITE);
        addFull(header, activeDestination);
        addFull(header, currentAction);
        addFull(header, inventory);
        guideWarning.setForeground(FarmingHelperTheme.GOLD);
        addFull(header, guideWarning);
        replan.addActionListener(e -> beginRun(true));
        JPanel overrides = new JPanel(new GridLayout(1, 2, 3, 0));
        overrides.setBackground(FarmingHelperTheme.BASE);
        overrideCrop.setText("Edit crop");
        overrideCrop.addActionListener(e -> chooseCropOverride());
        clearOverride.addActionListener(e -> clearCropOverride());
        overrides.add(overrideCrop);
        overrides.add(clearOverride);
        guideDetails.setForeground(FarmingHelperTheme.MUTED);
        guideDetails.setBackground(FarmingHelperTheme.BASE);
        guideDetails.addActionListener(e -> render());
        guide.add(header, BorderLayout.NORTH);
        stepList.setLayout(new BoxLayout(stepList, BoxLayout.Y_AXIS));
        stepList.setBackground(FarmingHelperTheme.BASE);
        stepList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel content = vertical();
        openLoadout.addActionListener(e -> showPage("loadout"));
        guideRequirements.setLayout(new BoxLayout(guideRequirements, BoxLayout.Y_AXIS));
        guideRequirements.setBackground(FarmingHelperTheme.BASE);
        requirementsToggle.addActionListener(e ->
        {
            requirementsExpanded = !requirementsExpanded;
            refreshGuideRequirements();
        });
        addFull(content, requirementsToggle);
        addFull(content, guideRequirements);
        beginWithMissing.addActionListener(e -> continueFromLoadout());
        addFull(content, beginWithMissing);
        addFull(content, openLoadout);
        JButton navigate = new JButton("Route to current patch");
        navigate.addActionListener(e ->
        {
            if (awaitingBank) requestBankRoute();
            else if (currentPlan != null && currentStep < currentPlan.steps.size())
                requestRoute(currentPlan.steps.get(currentStep));
        });
        addFull(content, navigate);
        guideDetails.setText("More options & details");
        addFull(content, guideDetails);
        advancedGuideControls.setLayout(new BoxLayout(advancedGuideControls, BoxLayout.Y_AXIS));
        advancedGuideControls.setBackground(FarmingHelperTheme.BASE);
        routeOptions.addActionListener(e -> chooseRouteOption());
        addFull(advancedGuideControls, routeOptions);
        addFull(advancedGuideControls, routeStatus);
        addFull(advancedGuideControls, replan);
        addFull(advancedGuideControls, overrides);
        addFull(content, advancedGuideControls);
        addFull(content, stepList);
        guide.add(scrolling(content), BorderLayout.CENTER);
        JPanel actions = new JPanel(new GridLayout(2, 2, 4, 4));
        actions.setBorder(BorderFactory.createEmptyBorder(6, 7, 7, 7));
        actions.setBackground(FarmingHelperTheme.SURFACE);
        back.addActionListener(e -> advance(-1));
        next.addActionListener(e -> advance(1));
        pause.addActionListener(e -> togglePause());
        end.addActionListener(e ->
        {
            if (runFinished || JOptionPane.showConfirmDialog(this,
                "End this run and discard its saved checkpoint?", "End run",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
            {
                stopRun(); showPage("selection");
            }
        });
        actions.add(back);
        actions.add(next);
        actions.add(pause);
        actions.add(end);
        guide.add(actions, BorderLayout.SOUTH);
        return guide;
    }

    private JPanel createEditorControls()
    {
        JPanel body = vertical();
        body.setBorder(BorderFactory.createEmptyBorder(4, 5, 5, 5));
        mergedControls.setLayout(new BoxLayout(mergedControls, BoxLayout.Y_AXIS));
        mergedControls.setBackground(FarmingHelperTheme.BASE);
        addFull(mergedControls, label("Reusable merged-run preset", false));
        addFull(mergedControls, presetChoice);
        for (FarmRunType type : FarmRunType.values())
        {
            JCheckBox check = new JCheckBox(type.getDisplayName());
            check.setBackground(FarmingHelperTheme.BASE);
            check.setForeground(Color.WHITE);
            mergedTypes.put(type, check);
            check.addActionListener(e -> { if (!loadingMerged) saveMergedPreset(); });
            addFull(mergedControls, check);
        }
        JButton newMerged = new JButton("Create new preset");
        newMerged.addActionListener(e -> createMergedPreset());
        addFull(mergedControls, newMerged);
        JButton deleteMerged = new JButton("Delete selected preset");
        deleteMerged.addActionListener(e -> deleteMergedPreset());
        addFull(mergedControls, deleteMerged);
        // All merged runs inherit the actual patch-type preferences.
        addFull(mergedControls, label("Uses the preferences in Patch Settings.", false));
        addFull(body, mergedControls);
        JButton route = nav("Route & teleports", "route");
        route.setName("fh.route");
        addFull(body, route);
        start.setText("Start run");
        start.putClientProperty("fh.primary", true);
        start.addActionListener(e -> beginRun());
        addFull(body, start);
        return body;
    }

    private void showEditorMode()
    {
        boolean merged = runType.getSelectedItem() == RunSelection.MERGED;
        boolean compost = runType.getSelectedItem() == RunSelection.COMPOST;
        mergedControls.setVisible(merged);
        if (inlineEditor != null) for (Component c : inlineEditor.getComponents())
            if ("fh.route".equals(c.getName())) c.setVisible(!compost);
        if (merged) reloadPresetNames();
        mergedControls.revalidate();
    }

    private void reloadPresetNames()
    {
        String previous = (String) presetChoice.getSelectedItem();
        if (previous == null) previous = profileValue("directorLastPreset",
            MergedPresets.DEFAULT_NAME);
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
        loadingMerged = true;
        for (Map.Entry<FarmRunType, JCheckBox> row : mergedTypes.entrySet())
            row.getValue().setSelected(types.contains(row.getKey()));
        loadingMerged = false;
        if (settingsReady && configManager.getRSProfileKey() != null)
            configManager.setRSProfileConfiguration(GROUP, "directorLastPreset", name);
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
            loadPreset();
            return;
        }
        configManager.setRSProfileConfiguration(GROUP, "directorLastPreset", name);
    }

    private void createMergedPreset()
    {
        Object input = JOptionPane.showInputDialog(this,
            "Preset name (letters, numbers, spaces, _ or -):",
            "Create merged run", JOptionPane.PLAIN_MESSAGE);
        if (input == null) return;
        String name = input.toString().trim();
        if (!MergedPresets.validName(name))
        {
            JOptionPane.showMessageDialog(this, "Choose a name of 1–40 safe characters.");
            return;
        }
        if (presets.names().contains(name))
        {
            JOptionPane.showMessageDialog(this, "A preset with this name already exists.");
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
        configManager.setRSProfileConfiguration(GROUP, "directorLastPreset", name);
    }

    private void deleteMergedPreset()
    {
        String name = (String) presetChoice.getSelectedItem();
        if (name == null || configManager.getRSProfileKey() == null) return;
        if (activeRunSelection == RunSelection.MERGED && Objects.equals(activePresetName, name)
            && currentPlan != null && !runFinished)
        {
            JOptionPane.showMessageDialog(this, "End the active merged run before deleting its preset.");
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Delete preset " + name + " and its saved choices?",
            "Delete merged preset", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        if (presets.delete(name))
        {
            reloadPresetNames();
            configManager.setRSProfileConfiguration(GROUP, "directorLastPreset",
                (String) presetChoice.getSelectedItem());
        }
    }

    private JPanel createSettings()
    {
        JPanel settings = vertical();
        addFull(settings, FarmingHelperComponents.header("Global Settings", nav("‹", "selection"), null));
        JPanel farming = vertical();
        addFull(farming, label("Compost", false));
        addFull(farming, compostChoice);
        addFull(farming, payRemoval);
        addFull(farming, protectTrees);
        addFull(settings, foldSection("Farming", farming));
        addFull(settings, nav("Patch Settings ›", "patches"));
        JPanel guidance = vertical();
        addFull(guidance, autoNext);
        addFull(guidance, shortestPathEnabled);
        addFull(guidance, worldHints);
        JButton targetColor = new JButton("Highlight color…");
        targetColor.addActionListener(e ->
        {
            Color picked = javax.swing.JColorChooser.showDialog(this, "Active highlight color", config.directorTargetColor());
            if (picked != null) configManager.setConfiguration(GROUP, "directorTargetColor", picked);
        });
        addFull(guidance, targetColor);
        shortestPathEnabled.addActionListener(e ->
        {
            navigationEnabled = shortestPathEnabled.isSelected();
            if (!navigationEnabled) clearRoute();
            else if (settingsReady && !loadingPreferences && !paused && !runFinished)
            {
                if (compostPageOpen) requestCompostRoute();
                else if (awaitingBank) requestBankRoute();
                else if (currentPlan != null && !preparingLoadout && currentStep < currentPlan.steps.size()) requestRoute(currentPlan.steps.get(currentStep));
            }
        });
        worldHints.addActionListener(e ->
        {
            if (settingsReady && !loadingPreferences)
                configManager.setConfiguration(GROUP, "directorWorldHints", worldHints.isSelected());
        });
        addFull(settings, foldSection("Guidance & appearance", guidance));
        JPanel advanced = vertical();
        addFull(advanced, diagnosticsEnabled);
        diagnosticsEnabled.addActionListener(e -> diagnostics.setEnabled(diagnosticsEnabled.isSelected()));
        JButton copyDiagnostics = new JButton("Copy diagnostics");
        copyDiagnostics.addActionListener(e -> {
            java.awt.datatransfer.StringSelection data = new java.awt.datatransfer.StringSelection(diagnostics.snapshot());
            try { java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(data, null); }
            catch (IllegalStateException ex) { JOptionPane.showMessageDialog(this, "Clipboard busy; try again."); }
        });
        addFull(advanced, copyDiagnostics);
        for (JCheckBox toggle : new JCheckBox[]{nearestRoute, herbloreFocus, autoReplanBank, readyOnly, allowUnverified})
            addFull(advanced, toggle);
        addFull(advanced, label("Harvest reserve (-1 = automatic)", false));
        addFull(advanced, harvestSlots);
        addFull(advanced, label("Bank between loadouts", false));
        addFull(advanced, bankChoice);
        bankChoice.addActionListener(e -> { if (settingsReady && !loadingPreferences && awaitingBank && !paused) requestBankRoute(); });
        addFull(settings, foldSection("Advanced", advanced));
        for (JCheckBox toggle : new JCheckBox[]{payRemoval, protectTrees, autoNext, nearestRoute,
            herbloreFocus, autoReplanBank, readyOnly, allowUnverified, shortestPathEnabled})
            toggle.addActionListener(e -> autoSavePreferences());
        compostChoice.addActionListener(e -> autoSavePreferences());
        bankChoice.addActionListener(e -> autoSavePreferences());
        harvestSlots.addChangeListener(e -> autoSavePreferences());
        addFull(settings, label("Saved automatically per character.", false));
        JPanel page = new JPanel(new BorderLayout());
        page.add(scrolling(settings), BorderLayout.CENTER);
        return page;
    }

    private JPanel foldSection(String name, JPanel body)
    {
        JPanel section = new JPanel(new BorderLayout(0, 2));
        section.setBackground(FarmingHelperTheme.SURFACE);
        JButton toggle = FarmingHelperComponents.row("▸ " + name);
        toggle.setHorizontalAlignment(JButton.LEFT);
        body.setVisible(false);
        toggle.addActionListener(e ->
        {
            body.setVisible(!body.isVisible());
            toggle.setText((body.isVisible() ? "▾ " : "▸ ") + name);
            section.revalidate();
        });
        section.add(toggle, BorderLayout.NORTH);
        section.add(body, BorderLayout.CENTER);
        return section;
    }

    private void autoSavePreferences()
    {
        if (settingsReady && !loadingPreferences) savePreferences();
    }

    private JPanel createRouteEditor()
    {
        JPanel screen = new JPanel(new BorderLayout(0, 7));
        screen.setBackground(FarmingHelperTheme.BASE);
        JPanel top = vertical();
        addFull(top, nav("← Back", "editor"));
        addFull(top, label("Route order & teleports", true));
        addFull(top, label("Select a stop to choose an optional travel item.", false));
        routeList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        routeList.setBackground(FarmingHelperTheme.SURFACE);
        routeList.setForeground(Color.WHITE);
        routeList.setCellRenderer(new javax.swing.DefaultListCellRenderer()
        {
            @Override public Component getListCellRendererComponent(javax.swing.JList<?> list,
                Object value, int index, boolean selected, boolean focused)
            {
                FarmRunPatch patch = (FarmRunPatch) value;
                return super.getListCellRendererComponent(list,
                    wrapped((index + 1) + ". " + patch.getDisplayName() +
                        " · " + patch.getPatchType().getDisplayName(), 174),
                    index, selected, focused);
            }
        });
        routeList.addListSelectionListener(e ->
        {
            if (e.getValueIsAdjusting()) return;
            FarmRunPatch patch = routeList.getSelectedValue();
            updatingRouteChoice = true;
            routeTeleportChoice.setModel(new javax.swing.DefaultComboBoxModel<>(
                patch == null ? new RouteTeleport[] {RouteTeleport.NONE}
                    : RouteTeleport.viableValues(patch)));
            routeTeleportChoice.setSelectedItem(patch == null ? RouteTeleport.NONE :
                routeEdits.getOrDefault(FarmRouteManager.key(patch), RouteTeleport.NONE));
            updatingRouteChoice = false;
        });
        routeTeleportChoice.addActionListener(e ->
        {
            FarmRunPatch patch = routeList.getSelectedValue();
            if (!updatingRouteChoice && patch != null)
                routeEdits.put(FarmRouteManager.key(patch),
                    (RouteTeleport) routeTeleportChoice.getSelectedItem());
        });
        addFull(top, routeTeleportChoice);
        screen.add(top, BorderLayout.NORTH);
        screen.add(scrolling(new JPanel(new BorderLayout())
        {{
            setBackground(FarmingHelperTheme.BASE);
            add(routeList, BorderLayout.CENTER);
        }}), BorderLayout.CENTER);

        JPanel bottom = vertical();
        JPanel arrows = new JPanel(new GridLayout(1, 3, 3, 0));
        arrows.setBackground(FarmingHelperTheme.BASE);
        JButton up = new JButton("↑ Up");
        up.addActionListener(e -> moveRoute(-1));
        JButton down = new JButton("↓ Down");
        down.addActionListener(e -> moveRoute(1));
        JButton remove = new JButton("Skip stop");
        remove.addActionListener(e ->
        {
            int selected = routeList.getSelectedIndex();
            if (selected >= 0)
            {
                routeModel.remove(selected);
                if (!routeModel.isEmpty())
                    routeList.setSelectedIndex(Math.min(selected, routeModel.size() - 1));
            }
        });
        arrows.add(up); arrows.add(down); arrows.add(remove);
        addFull(bottom, arrows);
        JButton restore = new JButton("Add omitted stop…");
        restore.addActionListener(e -> restoreRouteStop());
        addFull(bottom, restore);
        JButton save = new JButton("Save route and teleports");
        save.addActionListener(e -> saveRouteEditor());
        addFull(bottom, save);
        JButton defaults = new JButton("Restore default route");
        defaults.addActionListener(e -> resetRouteEditor());
        addFull(bottom, defaults);
        screen.add(bottom, BorderLayout.SOUTH);
        return screen;
    }

    private void loadRouteEditor()
    {
        RunSelection selected = (RunSelection) runType.getSelectedItem();
        if (selected == null || selected == RunSelection.COMPOST) return;
        routeEditFilter = selected.filter;
        routeModel.clear();
        routeEdits.clear();
        List<FarmRunPatch> patches = selected == RunSelection.MERGED
            ? routes.directorAllPatches() : routes.directorPatches(routeEditFilter);
        if (selected == RunSelection.MERGED)
        {
            String name = (String) presetChoice.getSelectedItem();
            patches = presets.applyRoute(name, MergedPresets.selected(patches,
                presets.categories(name)));
        }
        for (FarmRunPatch patch : patches)
        {
            // A global exclusion is not a deletion from the saved route.
            // Retain disabled grouped stops so re-enabling a patch restores it.
            routeModel.addElement(patch);
            RouteTeleport inherited = routes.teleportPreferred(routeEditFilter, patch);
            routeEdits.put(FarmRouteManager.key(patch),
                selected == RunSelection.MERGED
                    ? presets.teleport((String) presetChoice.getSelectedItem(), patch, inherited)
                    : inherited);
        }
        if (!routeModel.isEmpty()) routeList.setSelectedIndex(0);
    }

    private void restoreRouteStop()
    {
        RunSelection selection = (RunSelection) runType.getSelectedItem();
        if (selection == null || selection == RunSelection.COMPOST) return;
        Set<String> present = new HashSet<>();
        for (int i = 0; i < routeModel.size(); i++) present.add(FarmRouteManager.key(routeModel.get(i)));
        List<FarmRunPatch> available = routes.directorAllPatches();
        if (selection == RunSelection.MERGED)
            available = MergedPresets.selected(available, presets.categories((String) presetChoice.getSelectedItem()));
        available.removeIf(p -> !selection.includes(p.getPatchType()) || present.contains(FarmRouteManager.key(p)));
        if (available.isEmpty())
        {
            JOptionPane.showMessageDialog(this, "All available stops are already in this route.");
            return;
        }
        String[] labels = available.stream().map(p -> p.getDisplayName() + " · " +
            p.getPatchType().getDisplayName()).toArray(String[]::new);
        Object choice = JOptionPane.showInputDialog(this, "Restore a stop to the route:", "Add stop",
            JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
        for (int i = 0; choice != null && i < labels.length; i++)
            if (labels[i].equals(choice))
            {
                FarmRunPatch patch = available.get(i);
                RouteTeleport inherited = routes.teleportPreferred(routeEditFilter, patch);
                routeEdits.putIfAbsent(FarmRouteManager.key(patch), selection == RunSelection.MERGED
                    ? presets.teleport((String) presetChoice.getSelectedItem(), patch, inherited) : inherited);
                routeModel.addElement(patch);
                routeList.setSelectedIndex(routeModel.size() - 1);
                break;
            }
    }

    private void moveRoute(int direction)
    {
        int index = routeList.getSelectedIndex();
        int destination = index + direction;
        if (index < 0 || destination < 0 || destination >= routeModel.size()) return;
        FarmRunPatch selected = routeModel.remove(index);
        routeModel.add(destination, selected);
        routeList.setSelectedIndex(destination);
    }

    private void saveRouteEditor()
    {
        List<FarmRunPatch> patches = new ArrayList<>();
        for (int i = 0; i < routeModel.size(); i++) patches.add(routeModel.get(i));
        boolean merged = runType.getSelectedItem() == RunSelection.MERGED;
        boolean saved = merged
            ? presets.saveRoute((String) presetChoice.getSelectedItem(), patches, routeEdits)
            : routes.save(routes.profile(), routeEditFilter, patches, routeEdits, true);
        if (!saved)
        {
            JOptionPane.showMessageDialog(this, "Log in to a character to save route choices.");
            return;
        }
        showPage("editor");
    }

    private void resetRouteEditor()
    {
        List<FarmRunPatch> defaults = new ArrayList<>(FarmRunCatalog.patches());
        defaults.removeIf(p -> !routeEditFilter.includes(p.getFarmRunType()));
        Map<String, RouteTeleport> emptyTravel = new LinkedHashMap<>();
        for (FarmRunPatch p : defaults)
            emptyTravel.put(FarmRouteManager.key(p), RouteTeleport.NONE);
        boolean merged = runType.getSelectedItem() == RunSelection.MERGED;
        boolean saved = merged
            ? presets.resetRoute((String) presetChoice.getSelectedItem())
            : routes.save(routes.profile(), routeEditFilter, defaults, emptyTravel, false);
        if (!saved)
        {
            JOptionPane.showMessageDialog(this, "Log in before changing route preferences.");
            return;
        }
        loadRouteEditor();
    }

    private JPanel createLoadoutPage()
    {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(FarmingHelperTheme.BASE);
        JPanel top = vertical();
        addFull(top, label("Get supplies", true));
        leaveLoadout.addActionListener(e -> continueFromLoadout());
        addFull(top, leaveLoadout);
        addFull(top, loadoutReady);
        JButton refresh = new JButton("Rescan / replan remaining");
        refresh.addActionListener(e -> beginRun(true));
        addFull(top, refresh);
        page.add(top, BorderLayout.NORTH);
        loadoutRows.setLayout(new BoxLayout(loadoutRows, BoxLayout.Y_AXIS));
        loadoutRows.setBackground(FarmingHelperTheme.BASE);
        loadoutRows.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        page.add(scrolling(loadoutRows), BorderLayout.CENTER);
        return page;
    }

    private void loadoutSection(String heading, Map<Integer, Integer> items, Color tint)
    {
        if (items.isEmpty()) return;
        addFull(loadoutRows, label(heading, true));
        for (Map.Entry<Integer, Integer> entry : items.entrySet())
        {
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setBackground(FarmingHelperTheme.SURFACE);
            row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
            Crop crop = CropCatalog.findByItemId(entry.getKey());
            JLabel name = new JLabel(wrapped(crop == null ? Supplies.name(entry.getKey()) : crop.getItemName(), 118));
            name.setForeground(Color.WHITE);
            JLabel quantity = new JLabel("×" + entry.getValue());
            quantity.setForeground(tint);
            quantity.setFont(quantity.getFont().deriveFont(Font.BOLD));
            row.add(name, BorderLayout.CENTER);
            row.add(quantity, BorderLayout.EAST);
            addFull(loadoutRows, row);
        }
    }

    private boolean suppliesMissing()
    {
        return loadout.directorChecklistRows().stream().anyMatch(item -> item.getOwned() < item.getNeeded()) ||
            loadout.directorTravelRows().stream().anyMatch(item -> item.getOwned() < item.getNeeded());
    }

    private void continueFromLoadout()
    {
        if (currentPlan == null || planningInFlight) return;
        if (awaitingBank)
        {
            beginNextWave();
            return;
        }
        if (preparingLoadout && suppliesMissing() && JOptionPane.showConfirmDialog(this,
            "Some supplies are still missing. Continue anyway?", "Check supplies",
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        preparingLoadout = false;
        showPage("guide");
        render();
        if (!paused && !runFinished && currentStep < currentPlan.steps.size())
        {
            requestRoute(currentPlan.steps.get(currentStep));
            clientThread.invoke(this::onClientTick);
        }
    }

    private void liveLoadoutRows(JPanel target, List<FarmingLoadout.ChecklistItem> rows)
    {
        for (FarmingLoadout.ChecklistItem item : rows)
        {
            boolean ready = item.getOwned() >= item.getNeeded();
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.setBackground(FarmingHelperTheme.SURFACE);
            row.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            JLabel name = new JLabel(wrapped((ready ? "✓ " : "○ ") + item.getName(), 119));
            JLabel amount = new JLabel(item.amount());
            Color color = ready ? new Color(143, 187, 136) : new Color(240, 144, 120);
            name.setForeground(color); amount.setForeground(color);
            row.add(name, BorderLayout.CENTER);
            row.add(amount, BorderLayout.EAST);
            addFull(target, row);
        }
    }

    /** Compact, live checklist shared with the detailed bank/loadout page. EDT only. */
    private void refreshGuideRequirements()
    {
        boolean active = currentPlan != null && !currentPlan.steps.isEmpty() && !runFinished && !planningInFlight;
        requirementsToggle.setVisible(active);
        beginWithMissing.setVisible(active && preparingLoadout && !paused);
        guideRequirements.setVisible(active && (requirementsExpanded || preparingLoadout));
        if (!active)
        {
            requirementsSignature = "";
            guideRequirements.removeAll();
            return;
        }
        List<FarmingLoadout.ChecklistItem> rows = new ArrayList<>(loadout.directorChecklistRows());
        rows.addAll(loadout.directorTravelRows());
        long ready = rows.stream().filter(row -> row.getOwned() >= row.getNeeded()).count();
        requirementsToggle.setText((guideRequirements.isVisible() ? "▾ " : "▸ ") +
            "Supplies · " + ready + "/" + rows.size() + " ready");
        StringBuilder signature = new StringBuilder();
        for (FarmingLoadout.ChecklistItem row : rows)
            signature.append(row.getName()).append('|').append(row.amount()).append('|').append(row.getNeeded()).append(';');
        if (signature.toString().equals(requirementsSignature)) return;
        requirementsSignature = signature.toString();
        guideRequirements.removeAll();
        liveLoadoutRows(guideRequirements, rows);
        FarmingHelperTheme.style(guideRequirements);
        guideRequirements.revalidate();
        guideRequirements.repaint();
    }

    /** Enter the run as soon as the planned checklist is carried. No game actions are automated. */
    private void startWhenSupplied()
    {
        if (!preparingLoadout || paused || planningInFlight || awaitingBank || runFinished ||
            currentPlan == null || currentPlan.steps.isEmpty() || compostPageOpen ||
            !Objects.equals(activeProfile, configManager.getRSProfileKey()) || suppliesMissing()) return;
        preparingLoadout = false;
        if ("loadout".equals(visiblePage)) showPage("guide");
        render();
        requestRoute(currentPlan.steps.get(currentStep));
        clientThread.invoke(this::onClientTick);
    }

    private void refreshLoadoutPage()
    {
        loadoutRows.removeAll();
        leaveLoadout.setText(awaitingBank ? "Replan after banking" : preparingLoadout ? "Begin farming" : "Return to guide");
        leaveLoadout.setEnabled(currentPlan != null && !planningInFlight);
        boolean missing = suppliesMissing();
        loadoutReady.setForeground(missing ? FarmingHelperTheme.GOLD : new Color(143, 187, 136));
        loadoutReady.setText(wrapped(missing ? "Withdraw the missing items below." :
            "Carried items ready. Check teleport charges manually.", 184));
        if (currentSession == null || currentPlan == null)
        {
            addFull(loadoutRows, warning("Start a run to plan supplies."));
        }
        else
        {
            int index = awaitingBank && waveIndex + 1 < currentSession.waves.size()
                ? waveIndex + 1 : waveIndex;
            RunPlanner.Plan wave = index < currentSession.waves.size()
                ? currentSession.waves.get(index) : currentPlan;
            Map<Integer, Integer> kit = index < currentSession.supplies.size()
                ? currentSession.supplies.get(index) : Collections.emptyMap();
            addFull(loadoutRows, label("WAVE " + (index + 1) + " · " + wave.steps.size() + " stops", true));
            liveLoadoutRows(loadoutRows, loadout.directorChecklistRows());
            liveLoadoutRows(loadoutRows, loadout.directorTravelRows());
            if (guideDetails.isSelected())
            {
            addFull(loadoutRows, label(wave.occupiedSlots + " planned slots + " + wave.reservedSlots
                + " free-space reserve / 28", false));
            if (awaitingBank)
                addFull(loadoutRows, warning("Upcoming wave is provisional. Replan after opening storage."));
            loadoutSection("Planting stock", wave.seeds, Color.WHITE);
            loadoutSection("Tools & compost", kit, Color.WHITE);
            loadoutSection("Potential gardener payments", wave.payments, Color.WHITE);
            if (index < currentSession.teleports.size() && !currentSession.teleports.get(index).isEmpty())
                addFull(loadoutRows, warning("Travel: " + formatTeleports(currentSession.teleports.get(index))));
            if (!awaitingBank && index == 0)
            {
                StockLedger.Manifest manifest = currentSession.observedStock.manifest(wave, kit);
                loadoutSection("Carried at planning", manifest.carried, new Color(143, 187, 136));
                loadoutSection("Withdraw from bank", manifest.takeFromBank, FarmingHelperTheme.GOLD);
                loadoutSection("Withdraw from Seed Vault", manifest.takeFromVault, FarmingHelperTheme.GOLD);
                loadoutSection("Missing / unobserved", manifest.missing, new Color(240, 144, 120));
            }
            if (!currentSession.observedStock.bankObserved) addFull(loadoutRows, warning("Bank snapshot missing or stale."));
            if (!currentSession.observedStock.vaultObserved) addFull(loadoutRows, warning("Seed Vault snapshot missing or stale."));
            addFull(loadoutRows, warning("Teleport charges, actual harvest yield and axe alternatives require manual verification."));
            }
            else
            {
                if (awaitingBank) addFull(loadoutRows, warning("Replan after banking to refresh the next loadout."));
                if (!currentSession.observedStock.bankObserved) addFull(loadoutRows, warning("Open your bank to refresh stock."));
                if (!currentSession.observedStock.vaultObserved && missing) addFull(loadoutRows, warning("Open the Seed Vault if seeds are stored there."));
            }
        }
        loadoutRows.revalidate();
        loadoutRows.repaint();
    }

    private JPanel createStatusPage()
    {
        JPanel screen = new JPanel(new BorderLayout(0, 7));
        screen.setBackground(FarmingHelperTheme.BASE);
        JPanel top = vertical();
        addFull(top, nav("← Back to runs", "selection"));
        addFull(top, label("Farming status", true));
        JButton refresh = new JButton("Refresh observed timers and contract");
        refresh.addActionListener(e -> refreshStatusPage());
        addFull(top, refresh);
        screen.add(top, BorderLayout.NORTH);
        statusRows.setLayout(new BoxLayout(statusRows, BoxLayout.Y_AXIS));
        statusRows.setBackground(FarmingHelperTheme.BASE);
        screen.add(scrolling(statusRows), BorderLayout.CENTER);
        return screen;
    }

    private void refreshStatusPage()
    {
        final String profile = configManager.getRSProfileKey();
        clientThread.invoke(() ->
        {
            List<String> descriptions = new ArrayList<>();
            FarmingContract contract = contractManager.getContract();
            descriptions.add(contract == null ? "Farming contract: none observed"
                : "Farming contract: " + contract.getName());
            List<PatchTimer> timers = new ArrayList<>(timerManager.getTimers());
            descriptions.add(timers.size() + " observed farming timer(s)");
            long now = System.currentTimeMillis();
            for (PatchTimer timer : timers)
            {
                String location = PatchLocationCatalog.name(timer.getPatchLocation());
                String crop = timer.getCrop() == null ? "Unknown crop" :
                    timer.getCrop().getName();
                String condition = timer.isDead() ? "Dead" :
                    timer.isDiseased() ? "Diseased" :
                    timer.isPicked() ? "Picked" :
                    timer.getReadyAt() == null ? "Readiness unverified" :
                    timer.getReadyAt().toEpochMilli() <= now ? "Predicted ready" :
                    "Estimated ready " + CLOCK.format(timer.getReadyAt());
                descriptions.add(location + " · " + timer.getPatchType().getDisplayName()
                    + " · " + crop + " · " + condition);
            }
            SwingUtilities.invokeLater(() ->
            {
                if (!Objects.equals(profile, configManager.getRSProfileKey())) return;
                statusRows.removeAll();
                for (String line : descriptions) addFull(statusRows, warning(line));
                statusRows.revalidate();
                statusRows.repaint();
            });
        });
    }

    private JPanel createCompostGuide()
    {
        JPanel outer = new JPanel(new BorderLayout(0, 6));
        outer.setBackground(FarmingHelperTheme.BASE);
        JPanel top = vertical();
        addFull(top, nav("← Back to runs", "selection"));
        addFull(top, compostProgress);
        addFull(top, compostAction);
        JPanel controls = new JPanel(new GridLayout(1, 3, 3, 0));
        JButton previous = new JButton("Back");
        JButton following = new JButton("Next");
        JButton navigate = new JButton("Route");
        previous.addActionListener(e -> moveCompost(-1));
        following.addActionListener(e -> moveCompost(1));
        navigate.addActionListener(e -> requestCompostRoute());
        controls.add(previous); controls.add(following); controls.add(navigate);
        addFull(top, controls);
        JButton refresh = new JButton("Refresh observations");
        refresh.addActionListener(e -> refreshCompostGuide());
        addFull(top, refresh);
        outer.add(top, BorderLayout.NORTH);
        compostRows.setLayout(new BoxLayout(compostRows, BoxLayout.Y_AXIS));
        compostRows.setBackground(FarmingHelperTheme.BASE);
        outer.add(scrolling(compostRows), BorderLayout.CENTER);
        return outer;
    }

    private void moveCompost(int delta)
    {
        if (compostStops.isEmpty()) return;
        compostIndex = Math.max(0, Math.min(compostStops.size(), compostIndex + delta));
        clearRoute();
        refreshCompostGuide();
        requestCompostRoute();
    }

    private void refreshCompostGuide()
    {
        final long generation = ++compostGeneration;
        final String profile = configManager.getRSProfileKey();
        final int index = compostIndex;
        clientThread.invoke(() ->
        {
            List<CompostBinLocation> locations = new ArrayList<>(compostBins.enabledLocations());
            int farming = plugin.getFarmingLevel();
            boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
            if (loggedIn)
                locations.removeIf(location -> Waypoints.accessIssue(client, PatchType.ALLOTMENT,
                    location.getDisplayName(), farming, false) != null);
            else locations.clear();
            List<String> instructions = new ArrayList<>();
            for (CompostBinLocation location : locations)
            {
                CompostBinState state = compostBins.getState(location);
                String remaining = CompostBinManager.remaining(state, Instant.now());
                instructions.add(location.getDisplayName() + " · "
                    + compostBins.nextAction(location, state)
                    + (remaining == null ? "" : " · " + remaining));
            }
            CompostSupplyPlan supply = index < locations.size()
                ? compostBins.supplyPlan(Collections.singletonList(locations.get(index)))
                : new CompostSupplyPlan(0, 0, 0, 0);
            SwingUtilities.invokeLater(() ->
            {
                if (generation != compostGeneration || !compostPageOpen ||
                    !Objects.equals(profile, configManager.getRSProfileKey())) return;
                compostStops = locations;
                compostIndex = Math.min(index, locations.size());
                compostProgress.setText(locations.isEmpty() ? "No compost bins enabled"
                    : compostIndex == locations.size() ? "Compost itinerary complete"
                    : "Compost Run · " + (compostIndex + 1) + " / " + locations.size());
                compostAction.setText(compostIndex < instructions.size()
                    ? wrapped(instructions.get(compostIndex), 184)
                    : "Choose Back or start another run.");
                compostRows.removeAll();
                if (compostIndex < locations.size())
                    addFull(compostRows, warning("Current-bin maximum/estimated supplies: "
                        + "ingredients x" + supply.getIngredientQuantity()
                        + ", ash x" + supply.getVolcanicAshQuantity()
                        + ", potion doses x" + supply.getCompostPotionDoses()
                        + ", buckets x" + supply.getBucketQuantity()
                        + ". Unknown bins must be inspected."));
                for (int i = 0; i < instructions.size(); i++)
                {
                    final int target = i;
                    JButton stop = new JButton((i == compostIndex ? "▶ " : i < compostIndex
                        ? "✓ " : "○ ") + locations.get(i).getDisplayName());
                    stop.addActionListener(e ->
                    {
                        compostIndex = target;
                        refreshCompostGuide();
                        requestCompostRoute();
                    });
                    addFull(compostRows, stop);
                    addFull(compostRows, warning(instructions.get(i)));
                }
                compostRows.revalidate();
                compostRows.repaint();
            });
        });
    }

    private void requestCompostRoute()
    {
        if (!navigationEnabled || !compostPageOpen || compostIndex >= compostStops.size()) return;
        CompostBinLocation location = compostStops.get(compostIndex);
        final long generation = ++routeGeneration;
        final String profile = configManager.getRSProfileKey();
        clientThread.invoke(() ->
        {
            if (!navigationEnabled || generation != routeGeneration || !compostPageOpen ||
                !Objects.equals(profile, configManager.getRSProfileKey()) ||
                client.getLocalPlayer() == null) return;
            WorldPoint target = null;
            for (net.runelite.api.GameObject object : compostBins.getObjects())
            {
                if (location.contains(object.getWorldLocation()))
                {
                    target = object.getWorldLocation();
                    break;
                }
            }
            // For ordinary farms use a verified nearby herb area. The Guild
            // and Prifddinas do not have a verified bin-area fallback here.
            if (target == null && location != CompostBinLocation.FARMING_GUILD &&
                location != CompostBinLocation.PRIFDDINAS)
                target = Waypoints.point(PatchType.HERB, location.getDisplayName());
            if (target == null)
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
            data.put("target", target);
            eventBus.post(new PluginMessage("shortestpath", "path", data));
            routeIssued = true;
        });
    }

    private JPanel createPatchManager()
    {
        JPanel outer = new JPanel(new BorderLayout(0, 6));
        outer.setBackground(FarmingHelperTheme.BASE);
        JPanel header = vertical();
        JButton filters = FarmingHelperComponents.row("≡");
        filters.setToolTipText("Filters and bulk actions");
        JPanel filterBody = vertical();
        filterBody.setVisible(false);
        filters.addActionListener(e -> { filterBody.setVisible(!filterBody.isVisible()); header.revalidate(); });
        addFull(header, FarmingHelperComponents.header("Patch Settings", nav("‹", "selection"), filters));
        addFull(header, patchSummary);
        patchSearch.setToolTipText("Filter patch names and locations");
        addFull(header, patchSearch);
        disabledOnly.setBackground(FarmingHelperTheme.BASE);
        disabledOnly.setForeground(Color.WHITE);
        addFull(filterBody, disabledOnly);
        actionableOnly.setBackground(FarmingHelperTheme.BASE);
        actionableOnly.setForeground(Color.WHITE);
        addFull(filterBody, actionableOnly);
        actionableOnly.addActionListener(e -> drawPatchRows());
        JPanel bulkAll = new JPanel(new GridLayout(1, 2, 4, 0));
        JButton enableAll = new JButton("Enable all"), disableAll = new JButton("Disable all");
        enableAll.addActionListener(e -> setCategoryEnabled(new ArrayList<>(patchViews), true));
        disableAll.addActionListener(e -> setCategoryEnabled(new ArrayList<>(patchViews), false));
        bulkAll.add(enableAll); bulkAll.add(disableAll);
        addFull(filterBody, bulkAll);
        disableAll.setToolTipText("Disable every registered patch, even when filters are active");
        enableAll.setToolTipText("Enable every registered patch; unlock requirements still apply");
        JButton refresh = new JButton("Refresh patch states");
        refresh.addActionListener(e -> refreshPatchManager());
        addFull(filterBody, refresh);
        addFull(header, filterBody);
        patchSearch.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { drawPatchRows(); }
            public void removeUpdate(DocumentEvent e) { drawPatchRows(); }
            public void changedUpdate(DocumentEvent e) { drawPatchRows(); }
        });
        disabledOnly.addActionListener(e -> drawPatchRows());
        outer.add(header, BorderLayout.NORTH);
        patchRows.setLayout(new BoxLayout(patchRows, BoxLayout.Y_AXIS));
        patchRows.setBackground(FarmingHelperTheme.BASE);
        patchRows.setBorder(BorderFactory.createEmptyBorder(5, 9, 8, 9));
        patchScroll = scrolling(patchRows);
        outer.add(patchScroll, BorderLayout.CENTER);
        return outer;
    }

    private void refreshPatchManager()
    {
        final String profile = configManager.getRSProfileKey();
        clientThread.invoke(() ->
        {
            List<PatchView> result = new ArrayList<>();
            int level = client.getGameState() == GameState.LOGGED_IN
                ? plugin.getFarmingLevel() : 1;
            List<FarmRunPatch> individual = PatchPreferences.expand(
                FarmRunCatalog.patches(),
                patch -> stateReader.patchNamesForType(patch.getPatchType().name(), patch.getLocation()));
            for (FarmRunPatch patch : individual)
            {
                Waypoints.Access gate = client.getGameState() == GameState.LOGGED_IN
                    ? Waypoints.access(client, patch.getPatchType(),
                        patch.getLocation(), level) : null;
                String access = gate == null ? "Unlock: unknown (log in)"
                    : gate.state == Waypoints.AccessState.LOCKED ? "Locked: " + gate.detail
                    : gate.state == Waypoints.AccessState.UNKNOWN ? "Unverified: " + gate.detail
                    : gate.detail;
                QuestFarmingStateReader.Observation observation = observePatchState(patch);
                int plantLevel = PatchPriority.plantingLevel(patch.getPatchType());
                if (plantLevel > level && gate != null && gate.state == Waypoints.AccessState.AVAILABLE)
                    access += " Planting requires " + plantLevel + " Farming.";
                String state = !observation.known() ? "Ready: unverified"
                    : observation.state == CropState.GROWING ? "Growing"
                    : "Needs attention: " + observation.state.name().toLowerCase();
                result.add(new PatchView(patch, access, state));
            }
            SwingUtilities.invokeLater(() ->
            {
                if (!Objects.equals(profile, configManager.getRSProfileKey())) return;
                boolean changed = patchSettingsLevel != level || patchSettingsLoggedIn != (profile != null) || patchViews.size() != result.size();
                if (!changed) for (int index = 0; index < result.size(); index++)
                {
                    PatchView a = patchViews.get(index), b = result.get(index);
                    if (!FarmRouteManager.key(a.patch).equals(FarmRouteManager.key(b.patch)) || !a.unlock.equals(b.unlock) || !a.state.equals(b.state)) { changed = true; break; }
                }
                if (!changed) return;
                patchSettingsLevel = level;
                patchSettingsLoggedIn = profile != null;
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
        if (configManager.getRSProfileKey() == null) return;
        for (PatchView row : rows) patchPrefs.setEnabled(row.patch, enabled);
        drawPatchRows();
    }

    private void drawPatchRows()
    {
        int scrollY = patchScroll == null ? 0 : patchScroll.getVerticalScrollBar().getValue();
        patchRows.removeAll();
        String query = patchSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        boolean onlyOff = disabledOnly.isSelected();
        Map<PatchType, List<PatchView>> byType = new LinkedHashMap<>();
        for (PatchType t : new PatchType[]{PatchType.HERB, PatchType.TREE, PatchType.FRUIT_TREE, PatchType.CALQUAT, PatchType.HARDWOOD_TREE, PatchType.ALLOTMENT, PatchType.FLOWER, PatchType.HOPS, PatchType.BUSH, PatchType.CACTUS}) byType.put(t, new ArrayList<>());
        int enabledCount = 0;
        for (PatchView patch : patchViews)
        {
            if (patchPrefs.enabled(patch.patch)) enabledCount++;
            if (actionableOnly.isSelected() && !patch.state.startsWith("Needs attention")) continue;
            if (onlyOff && patchPrefs.enabled(patch.patch)) continue;
            if (!query.isEmpty() && !patch.patch.getDisplayName()
                .toLowerCase(java.util.Locale.ROOT).contains(query)
                && !patch.patch.getPatchType().getDisplayName()
                    .toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            byType.computeIfAbsent(patch.patch.getPatchType(), t -> new ArrayList<>()).add(patch);
        }
        byType.entrySet().removeIf(e -> e.getValue().isEmpty());
        patchSummary.setText(enabledCount + " / " + patchViews.size() + " enabled");
        patchSummary.setForeground(FarmingHelperTheme.SUCCESS);
        patchSummary.setToolTipText(byType.values().stream().mapToInt(List::size).sum() + " shown; enabled is independent of access requirements");
        for (Map.Entry<PatchType, List<PatchView>> group : byType.entrySet())
        {
            JPanel category = new JPanel(new BorderLayout(3, 3));
            category.setBackground(FarmingHelperTheme.SURFACE);
            JButton fold = new JButton((collapsedTypes.contains(group.getKey()) ? "▸ " : "▾ ")
                + group.getKey().getDisplayName());
            fold.addActionListener(e ->
            {
                if (!collapsedTypes.add(group.getKey())) collapsedTypes.remove(group.getKey());
                drawPatchRows();
            });
            List<PatchView> entireGroup = new ArrayList<>();
            for (PatchView patch : patchViews) if (patch.patch.getPatchType() == group.getKey()) entireGroup.add(patch);
            long groupEnabled = entireGroup.stream().filter(v -> patchPrefs.enabled(v.patch)).count();
            fold.setText(fold.getText());
            FarmingHelperTheme.role(fold, "quiet");
            JLabel count = FarmingHelperComponents.caption(groupEnabled + "/" + entireGroup.size());
            count.setForeground(groupEnabled == 0 ? FarmingHelperTheme.MUTED : FarmingHelperTheme.SUCCESS);
            JPanel categoryHeader = new JPanel(new BorderLayout(3,0));
            categoryHeader.setBackground(FarmingHelperTheme.SURFACE);
            categoryHeader.add(fold, BorderLayout.CENTER);
            categoryHeader.add(count, BorderLayout.EAST);
            PatchType type = group.getKey();
            if (patchIcons.containsKey(type)) fold.setIcon(patchIcons.get(type));
            else if (!CropCatalog.crops(type).isEmpty())
            {
                int item = CropCatalog.crops(type).get(0).getItemId();
                clientThread.invoke(() -> {
                    net.runelite.client.util.AsyncBufferedImage image = itemManager.getImage(item);
                    if (image != null) image.onLoaded(() -> SwingUtilities.invokeLater(() -> { javax.swing.Icon icon = FarmingHelperComponents.sprite(image); patchIcons.put(type, icon); fold.setIcon(icon); }));
                });
            }
            fold.setHorizontalAlignment(JButton.LEFT);
            fold.putClientProperty("fh.primary", !collapsedTypes.contains(group.getKey()));
            category.add(categoryHeader, BorderLayout.NORTH);
            JPanel contents = vertical();
            contents.setBackground(FarmingHelperTheme.SURFACE);
            contents.setBorder(BorderFactory.createEmptyBorder(3,5,3,5));
            JPanel bulk = new JPanel(new GridLayout(1, 2, 3, 0));
            bulk.setBackground(FarmingHelperTheme.SURFACE);
            JButton all = new JButton("Enable all");
            all.addActionListener(e -> setCategoryEnabled(entireGroup, true));
            bulk.add(all);
            JButton none = new JButton("Disable all");
            none.addActionListener(e -> setCategoryEnabled(entireGroup, false));
            bulk.add(none);
            if (!collapsedTypes.contains(group.getKey())) category.add(contents, BorderLayout.CENTER);
            addFull(patchRows, category);
            if (collapsedTypes.contains(group.getKey())) continue;

            addFull(contents, patchPriorityEditor(group.getKey()));
            boolean showLocations = openPatchLocations.contains(group.getKey());
            JButton locations = new JButton((showLocations ? "▾ " : "▸ ") + "Farming locations");
            locations.setHorizontalAlignment(JButton.LEFT);
            locations.addActionListener(e ->
            {
                if (!openPatchLocations.add(group.getKey())) openPatchLocations.remove(group.getKey());
                drawPatchRows();
            });
            addFull(contents, locations);
            if (!showLocations) continue;
            addFull(contents, bulk);
            Map<String, List<PatchView>> byLocation = new LinkedHashMap<>();
            for (PatchView patch : group.getValue())
                byLocation.computeIfAbsent(patch.patch.getLocation(),
                    loc -> new ArrayList<>()).add(patch);
            for (Map.Entry<String, List<PatchView>> location : byLocation.entrySet())
            {
                if (location.getValue().size() == 1 && entireGroup.stream().filter(v -> v.patch.getLocation().equals(location.getKey())).count() == 1)
                {
                    PatchView patch = location.getValue().get(0);
                    JCheckBox single = new JCheckBox(location.getKey(), patchPrefs.enabled(patch.patch));
                    single.setEnabled(configManager.getRSProfileKey() != null);
                    single.setToolTipText(patch.unlock + " · " + patch.state);
                    single.addActionListener(e -> { patchPrefs.setEnabled(patch.patch, single.isSelected()); drawPatchRows(); });
                    addFull(contents, single);
                    if (patch.unlock.startsWith("Locked:") || patch.unlock.contains("Planting requires"))
                    {
                        JLabel info = label(patch.unlock, false);
                        info.setForeground(FarmingHelperTheme.GOLD);
                        addFull(contents, info);
                    }
                    continue;
                }
                JPanel locationBar = new JPanel(new BorderLayout(3, 0));
                locationBar.setBackground(FarmingHelperTheme.BASE);
                String locationKey = group.getKey().name() + "~" + location.getKey();
                boolean locationOpen = expandedLocations.contains(locationKey);
                List<PatchView> entireLocation = new ArrayList<>();
                for (PatchView patch : entireGroup) if (patch.patch.getLocation().equals(location.getKey())) entireLocation.add(patch);
                long locEnabled = entireLocation.stream().filter(v -> patchPrefs.enabled(v.patch)).count();
                JButton locationTitle = new JButton(wrapped((locationOpen ? "▾ " : "▸ ") + location.getKey() + " · " + locEnabled + "/" + entireLocation.size(), 108));
                locationTitle.setHorizontalAlignment(JButton.LEFT);
                locationTitle.addActionListener(e -> { if (!expandedLocations.add(locationKey)) expandedLocations.remove(locationKey); drawPatchRows(); });
                locationBar.add(locationTitle, BorderLayout.CENTER);
                JPanel locationActions = new JPanel(new GridLayout(1, 2, 2, 0));
                locationActions.setBackground(FarmingHelperTheme.BASE);
                JButton enable = new JButton("+");
                enable.setToolTipText("Enable all patches at this location in this category");
                enable.addActionListener(e -> setCategoryEnabled(entireLocation, true));
                enable.setMargin(new java.awt.Insets(2, 5, 2, 5));
                locationActions.add(enable);
                JButton disable = new JButton("−");
                disable.setToolTipText("Disable all patches at this location in this category");
                disable.addActionListener(e -> setCategoryEnabled(entireLocation, false));
                disable.setMargin(new java.awt.Insets(2, 5, 2, 5));
                locationActions.add(disable);
                locationBar.add(locationActions, BorderLayout.EAST);
                addFull(contents, locationBar);
                if (!locationOpen) continue;
                for (PatchView patch : location.getValue())
                {
                    JPanel entry = vertical();
                    entry.setBorder(BorderFactory.createEmptyBorder(1, 8, 7, 2));
                    JCheckBox enabled = new JCheckBox(patch.patch.getName().isEmpty()
                        ? patch.patch.getPatchType().getDisplayName()
                        : patch.patch.getName() + " · " + patch.patch.getPatchType().getDisplayName(),
                        patchPrefs.enabled(patch.patch));
                    enabled.setText(wrapped(enabled.getText(), 146));
                    enabled.setForeground(Color.WHITE);
                    enabled.setBackground(FarmingHelperTheme.BASE);
                    enabled.setEnabled(configManager.getRSProfileKey() != null);
                    enabled.addActionListener(e ->
                    {
                        patchPrefs.setEnabled(patch.patch, enabled.isSelected());
                        drawPatchRows();
                    });
                    addFull(entry, enabled);
                    JLabel info = label(patch.unlock + " · " + patch.state, false);
                    if (patch.unlock.startsWith("Locked:") || patch.unlock.contains("Planting requires")) info.setForeground(FarmingHelperTheme.GOLD);
                    else if (!patchPrefs.enabled(patch.patch)) info.setForeground(FarmingHelperTheme.MUTED);
                    addFull(entry, info);
                    addFull(contents, entry);
                }
            }
        }
        if (patchViews.isEmpty()) patchRows.add(warning("Loading patch observations..."));
        else if (byType.isEmpty()) patchRows.add(warning("No patches match your filters."));
        FarmingHelperTheme.style(patchRows);
        patchRows.revalidate();
        patchRows.repaint();
        if (patchScroll != null) SwingUtilities.invokeLater(() -> patchScroll.getVerticalScrollBar().setValue(scrollY));
    }

    private JPanel patchPriorityEditor(PatchType type)
    {
        return PatchPriorityEditor.build(type, priorityStore, configManager.getRSProfileKey() != null,
            patchSettingsLoggedIn, patchSettingsLevel, advancedPatchTypes, this::drawPatchRows, () ->
            {
                runType.setSelectedItem(RunSelection.valueOf(individualMode(FarmRunType.forPatchType(type))));
                showPage("route");
            });
    }

    private static JLabel label(String text, boolean strong)
    {
        return FarmingHelperComponents.label(text, strong);
    }

    private String profileValue(String key, String fallback)
    {
        String value = configManager.getRSProfileConfiguration(GROUP, key);
        return value == null ? fallback : value;
    }

    private void loadPreferences()
    {
        loadingPreferences = true;
        payRemoval.setSelected(Boolean.parseBoolean(profileValue("directorPayRemoval", "true")));
        protectTrees.setSelected(Boolean.parseBoolean(
            profileValue("directorProtectTrees", "true")));
        compostChoice.setSelectedItem(CompostChoice.load(profileValue("directorCompostChoice", ""),
            Boolean.parseBoolean(profileValue("directorUseCompost", Boolean.toString(config.includeCompost())))));
        int reserve = -1;
        try { reserve = Integer.parseInt(profileValue("directorFreeSlots", "-1")); }
        catch (NumberFormatException ignored) {}
        harvestSlots.setValue(Math.max(-1, Math.min(26, reserve)));
        // This is a global setting, not a control tied to the visible run choice.
        protectTrees.setVisible(true);
        autoNext.setSelected(Boolean.parseBoolean(profileValue("directorAutoNext", "true")));
        nearestRoute.setSelected(Boolean.parseBoolean(profileValue("directorNearest", "false")));
        herbloreFocus.setSelected(Boolean.parseBoolean(profileValue("directorHerblore", "false")));
        autoReplanBank.setSelected(Boolean.parseBoolean(profileValue("directorAutoReplanBank", "false")));
        readyOnly.setSelected(Boolean.parseBoolean(profileValue("directorReadyOnly", "false")));
        navigationEnabled = Boolean.parseBoolean(profileValue("directorNavigationEnabled", "true"));
        shortestPathEnabled.setSelected(navigationEnabled);
        allowUnverified.setSelected(Boolean.parseBoolean(profileValue("directorAllowUnverified",
            Boolean.toString(config.directorIncludeUnverifiedLocations()))));
        worldHints.setSelected(config.directorWorldHints());
        try
        {
            bankChoice.setSelectedItem(BankStops.Bank.valueOf(
                profileValue("directorBankStop", "AUTO")));
        }
        catch (IllegalArgumentException ignored)
        {
            bankChoice.setSelectedItem(BankStops.Bank.AUTO);
        }
        loadingPreferences = false;
    }

    private void savePreferences()
    {
        if (configManager.getRSProfileKey() == null)
        {
            return;
        }
        configManager.setRSProfileConfiguration(GROUP, "directorPayRemoval", Boolean.toString(payRemoval.isSelected()));
        configManager.setRSProfileConfiguration(GROUP, "directorProtectTrees",
            Boolean.toString(protectTrees.isSelected()));
        CompostChoice compost = (CompostChoice) compostChoice.getSelectedItem();
        if (compost == null) compost = CompostChoice.NONE;
        configManager.setRSProfileConfiguration(GROUP, "directorCompostChoice", compost.name());
        configManager.setRSProfileConfiguration(GROUP, "directorUseCompost", Boolean.toString(compost != CompostChoice.NONE));
        configManager.setRSProfileConfiguration(GROUP, "directorFreeSlots", harvestSlots.getValue().toString());
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
        configManager.setRSProfileConfiguration(GROUP, "directorNavigationEnabled",
            Boolean.toString(shortestPathEnabled.isSelected()));
        configManager.setRSProfileConfiguration(GROUP, "directorAllowUnverified",
            Boolean.toString(allowUnverified.isSelected()));
        BankStops.Bank bank = (BankStops.Bank) bankChoice.getSelectedItem();
        configManager.setRSProfileConfiguration(GROUP, "directorBankStop",
            bank == null ? "AUTO" : bank.name());
    }

    private void beginRun()
    {
        beginRun(false);
    }

    /** Viewing another run in the editor must never change the active checkpoint. */
    static RunSelection replanSelection(RunSelection active,
                                                RunSelection viewed)
    {
        return active == null ? viewed : active;
    }

    /** Replan keeps completed patch IDs so we can safely refill at a bank. */
    private void beginRun(boolean preserveProgress)
    {
        if (planningInFlight) return;
        if (!preserveProgress) savePreferences();
        final RunSelection selected = replanSelection(
            preserveProgress ? activeRunSelection : null,
            (RunSelection) runType.getSelectedItem());
        if (selected == null) return;
        if (!preserveProgress && selected != RunSelection.COMPOST &&
            currentPlan != null && !runFinished && JOptionPane.showConfirmDialog(this,
                "Replace the active run and its checkpoint?", "Start new run",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        if (selected == RunSelection.COMPOST)
        {
            compostIndex = 0;
            clearRoute();
            showPage("compost");
            return;
        }
        final String presetName = preserveProgress ? activePresetName
            : (String) presetChoice.getSelectedItem();
        final boolean onlyActionable = readyOnly.isSelected();
        final boolean includeUnverified = allowUnverified.isSelected();
        if (!preserveProgress) temporaryOverrides.clear();
        final Map<String, String> localOverrides = new HashMap<>(temporaryOverrides);
        final boolean protection = protectTrees.isSelected();
        final boolean payToRemove = payRemoval.isSelected();
        final CompostChoice useCompost = (CompostChoice) compostChoice.getSelectedItem();
        final int freeSlots = (Integer) harvestSlots.getValue();
        final boolean proximity = nearestRoute.isSelected();
        final boolean herbPriority = herbloreFocus.isSelected();
        final int waveCap = Integer.MAX_VALUE;
        final Set<String> skip = preserveProgress
            ? new HashSet<>(completedPatchKeys) : Collections.emptySet();
        final long generation = ++planningGeneration;
        final String planningProfile = configManager.getRSProfileKey();
        final boolean wasPaused = preserveProgress && paused;
        if (planningProfile == null)
        {
            JOptionPane.showMessageDialog(this, "Log in before planning a run.");
            return;
        }
        planningInFlight = true;
        diagnostics.record("Planning " + selected.name());
        final long planningStarted = System.nanoTime();
        publishWorldTarget();
        publishBankChecklist();
        guideWarning.setText(wrapped("Planning remaining patches…", 184));
        guideWarning.setVisible(true);
        start.setEnabled(false);
        replan.setEnabled(false);
        clearRoute();
        clientThread.invoke(() ->
        {
            SessionPlan calculated;
            try
            {
                calculated = compute(selected, protection, proximity, herbPriority, waveCap, skip,
                    presetName, onlyActionable, includeUnverified, localOverrides, useCompost, freeSlots, payToRemove);
            }
            catch (RuntimeException problem)
            {
                diagnostics.record("Planning failed: " + problem.getClass().getSimpleName());
                log.warn("Farming Helper could not generate a run", problem);
                List<String> errors = Collections.singletonList(
                    "Could not generate this run. Check RuneLite logs and reopen storage before retrying.");
                calculated = new SessionPlan(
                    new WavePlanner.Result(Collections.emptyList(),
                        Collections.emptyList(), errors),
                    new StockLedger(Collections.emptyMap(), Collections.emptyMap(),
                        Collections.emptyMap(), false, false), errors);
            }
            final SessionPlan result = calculated;
            diagnostics.record("Planning completed in " + ((System.nanoTime()-planningStarted)/1_000_000) + " ms");
            SwingUtilities.invokeLater(() ->
            {
                if (generation != planningGeneration ||
                    !Objects.equals(planningProfile, configManager.getRSProfileKey())) return;
                planningInFlight = false;
                currentSession = result;
                activeRunSelection = selected;
                activePayRemoval = payToRemove;
                activeProfile = planningProfile;
                activePresetName = presetName == null
                    ? MergedPresets.DEFAULT_NAME : presetName;
                waveIndex = 0;
                awaitingBank = false;
                currentPlan = result.firstWave();
                currentStep = 0;
                paused = wasPaused;
                preparingLoadout = !currentPlan.steps.isEmpty();
                requirementsExpanded = true;
                runFinished = false;
                completedSteps.clear();
                if (!preserveProgress) completedPatchKeys.clear();
                persistProgress();
                activateCurrentStep();
                start.setEnabled(true);
                replan.setEnabled(true);
                render();
                showPage("guide");
                startWhenSupplied();
            });
        });
    }

    private SessionPlan compute(RunSelection selection,
        boolean protection, boolean proximity, boolean herbPriority, int waveCap,
        Set<String> skip, String presetName, boolean onlyActionable,
        boolean includeUnverified, Map<String, String> localOverrides, CompostChoice useCompost,
        int freeSlots, boolean payToRemove)
    {
        List<String> warnings = new ArrayList<>();
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            warnings.add("Log into a character before planning a run.");
            return new SessionPlan(
                new WavePlanner.Result(Collections.emptyList(),
                    Collections.emptyList(), warnings),
                new StockLedger(Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), false, false), warnings);
        }
        int level = plugin.getFarmingLevel();
        lastPlannedFarmingLevel = level;
        List<FarmRunPatch> candidates = new ArrayList<>();
        List<FarmRunPatch> configured = selection == RunSelection.MERGED
            ? routes.directorAllPatches() : routes.directorPatches(selection.filter);
        if (selection == RunSelection.MERGED)
        {
            configured = presets.applyRoute(presetName,
                MergedPresets.selected(configured, presets.categories(presetName)));
        }
        int unknownPatchDefinitions = 0;
        int approximateDestinations = 0;

        for (FarmRunPatch grouped : configured)
        {
            PatchType type = grouped.getPatchType();
            if (!selection.includes(type)) continue;
            String access = Waypoints.accessIssue(client, type, grouped.getLocation(),
                level, includeUnverified);
            if (access != null)
            {
                warnings.add(access);
                continue;
            }
            List<String> canonical = stateReader.patchNamesForType(type.name(),
                grouped.getLocation());
            List<FarmRunPatch> separate = PatchNames.expand(grouped, canonical);
            if (separate.isEmpty())
            {
                unknownPatchDefinitions++;
                continue;
            }
            for (FarmRunPatch patch : separate)
            {
                if (skip.contains(FarmRouteManager.key(patch)) || !patchPrefs.enabled(patch))
                    continue;
                TravelTarget target = TravelTarget.choose(patch,
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

        // Default merged/all runs visit all patches at a farm together.
        // An explicitly saved route remains authoritative.
        if (!proximity && (selection == RunSelection.MERGED || selection == RunSelection.ALL)
            && (selection == RunSelection.MERGED
                ? profileValue("directorMergedRouteOrder_" + presetName, "").isEmpty()
                : !routes.isCustom(FarmRunFilter.ALL)))
            candidates = LocationOrder.group(candidates);

        if (proximity && client.getLocalPlayer() != null)
        {
            candidates = NearestRoute.order(candidates,
                client.getLocalPlayer().getWorldLocation(), patch ->
                {
                    TravelTarget target = routeTarget(patch);
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
        StockLedger stockLedger = new StockLedger(
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
            if (patch.getPatchType() == PatchType.SPIRIT_TREE && observation.known()
                && observation.state == CropState.UNCHECKED)
            {
                // Do not reserve another sapling simply to check an existing tree.
                preplanted.add(FarmRouteManager.key(patch));
                actionable.add(patch);
                continue;
            }
            if (observation.known() && observation.state == CropState.GROWING)
            {
                if (StepEngine.supportsProtection(patch.getPatchType())
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

        // Recalculate tools and compost for the actual selected stops of each wave.
        // A filled bottomless bucket is observed as an item; its charge count is not known.
        final boolean bottomless = stock.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, 0) > 0;
        final int compostItem = (useCompost == null ? CompostChoice.NONE : useCompost).resolve(bottomless);
        final boolean carrySecateurs = stock.containsKey(ItemID.FAIRY_ENCHANTED_SECATEURS)
            && !loadout.directorEquipment().containsKey(ItemID.FAIRY_ENCHANTED_SECATEURS);
        if (compostItem == ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED)
            warnings.add("Check the bottomless compost bucket's contents and charges.");
        if (candidates.stream().anyMatch(p -> StepEngine.isTreeType(p.getPatchType())))
            warnings.add("Bring an appropriate axe or coins for gardener removal; verify tool/equipment space.");

        Set<RouteTeleport> distinctTeleports = new HashSet<>();
        Map<String, RouteTeleport> teleportForPatch = new HashMap<>();
        for (FarmRunPatch patch : candidates)
        {
            for (FarmRunPatch configuredPatch : configured)
            {
                if (configuredPatch.getLocation().equals(patch.getLocation()) &&
                    configuredPatch.getPatchType() == patch.getPatchType())
                {
                    RouteTeleport inherited = routes.teleportPreferred(selection.filter,
                        configuredPatch);
                    RouteTeleport teleport = selection == RunSelection.MERGED
                        ? presets.teleport(presetName, configuredPatch, inherited) : inherited;
                    teleportForPatch.put(FarmRouteManager.key(patch), teleport);
                    if (teleport != RouteTeleport.NONE) distinctTeleports.add(teleport);
                    break;
                }
            }
        }
        int harvestReserve = Supplies.harvestReserve(selection, config.directorHarvestSlots(), freeSlots);

        Map<PatchType, PatchPriority> patchPriorities = new EnumMap<>(PatchType.class);
        for (PatchType type : PatchType.values())
            patchPriorities.put(type, priorityStore.get(type));

        Map<Integer, Integer> equippedItems = loadout.directorEquipment();
        WavePlanner.Result wavePlan = WavePlanner.split(candidates, level,
            stock, false, "", "", 0, 0,
            harvestReserve, protection, id ->
                itemManager.getItemComposition(id).isStackable(), preplanted,
                harvestOnly, cropOverrides, herbPriority, waveCap,
            selectedPatches ->
            {
                int travelSlots = TeleportPlan.inventorySlots(
                    FarmRouteManager.requirements(selectedPatches, teleportForPatch),
                    equippedItems, id -> itemManager.getItemComposition(id).isStackable());
                Map<Integer, Integer> kit = Supplies.required(selectedPatches,
                    compostItem, carrySecateurs, payToRemove);
                return travelSlots + Supplies.slots(selectedPatches, kit,
                    id -> itemManager.getItemComposition(id).isStackable(), payToRemove);
            }, Collections.emptyMap(), patchPriorities);

        List<Map<RouteTeleport, Integer>> travelRequirements =
            TeleportPlan.perWave(wavePlan, teleportForPatch);
        Map<Integer, Integer> travelStock = new HashMap<>(stock);
        equippedItems.forEach((id, amount) -> travelStock.merge(id, amount, Integer::sum));
        warnings.addAll(TeleportPlan.missingTravelItems(
            TeleportPlan.totalRequirements(travelRequirements), travelStock));

        // A manifest is calculated per loadout, not across the full itinerary.
        // Protection and teleport requirements may change as the run progresses.
        List<Map<Integer, Integer>> supplyRequirements = new ArrayList<>();
        int totalCompost = 0;
        for (RunPlanner.Plan wave : wavePlan.waves)
        {
            List<FarmRunPatch> stops = new ArrayList<>();
            for (RunPlanner.Step step : wave.steps) stops.add(step.patch);
            Map<Integer, Integer> kit = Supplies.required(stops,
                compostItem, carrySecateurs, payToRemove);
            supplyRequirements.add(kit);
            if (CompostChoice.isOrdinary(compostItem)) totalCompost += kit.getOrDefault(compostItem, 0);
            StockLedger.Manifest manifest = stockLedger.manifest(wave, kit);
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
        if (CompostChoice.isOrdinary(compostItem) && totalCompost > stock.getOrDefault(compostItem, 0))
            warnings.add("Total planned compost exceeds scanned stock; replenish or change compost settings.");
        warnings.addAll(wavePlan.warnings);
        return new SessionPlan(wavePlan, stockLedger, warnings,
            travelRequirements, supplyRequirements, teleportForPatch);
    }

    private static String individualMode(FarmRunType type)
    {
        switch (type)
        {
            case HERB: return RunSelection.HERBS.name();
            case ALLOTMENT_FLOWER: return RunSelection.ALLOTMENTS.name();
            case TREE: return RunSelection.TREES.name();
            case FRUIT_TREE_CALQUAT: return RunSelection.FRUIT_TREES.name();
            case HARDWOOD_TREE: return RunSelection.HARDWOODS.name();
            case HOPS: return RunSelection.HOPS.name();
            case BUSH: return RunSelection.BUSHES.name();
            case CACTUS: return RunSelection.CACTUS.name();
            default: return RunSelection.SPECIAL.name();
        }
    }

    private void persistProgress()
    {
        if (activeProfile == null || !Objects.equals(activeProfile, configManager.getRSProfileKey()))
            return;
        if (activeRunSelection == null) return;
        configManager.setRSProfileConfiguration(GROUP, "directorSavedMode", activeRunSelection.name());
        configManager.setRSProfileConfiguration(GROUP, "directorSavedPreset", activePresetName);
        configManager.setRSProfileConfiguration(GROUP, "directorCompletedKeys",
            RunPersistence.encode(completedPatchKeys));
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
        final String profile = configManager.getRSProfileKey();
        SwingUtilities.invokeLater(() ->
        {
            if (autoReplanBank.isSelected() && currentPlan != null && !runFinished
                && !planningInFlight && !compostPageOpen && !paused && loggedIn && Objects.equals(profile, configManager.getRSProfileKey()))
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
            RunSelection selected = RunSelection.valueOf(mode);
            loadingPreferences = true;
            runType.setSelectedItem(selected);
            loadingPreferences = false;
            loadPreferences();
            activeRunSelection = selected;
            activeProfile = configManager.getRSProfileKey();
            activePresetName = profileValue("directorSavedPreset",
                MergedPresets.DEFAULT_NAME);
            completedPatchKeys.clear();
            completedPatchKeys.addAll(RunPersistence.decode(
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
            "Farming Helper — crop override",
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
        TravelTarget target = TravelTarget.choose(patch,
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
                        ? (StepEngine.isTreeType(patch.getPatchType())
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
        RunPlanner.Plan plan = currentPlan;
        int index = currentStep;
        if (plan == null || planningInFlight || preparingLoadout || paused || runFinished || awaitingBank || compostPageOpen
            || !Objects.equals(activeProfile, configManager.getRSProfileKey())
            || client.getGameState() != GameState.LOGGED_IN
            || index < 0 || index >= plan.steps.size())
        {
            return;
        }
        RunPlanner.Step step = plan.steps.get(index);
        QuestFarmingStateReader.Observation observation = observePatchState(step.patch);
        final QuestFarmingStateReader.Observation latest = observation;
        boolean treated = false;
        if (step.requiresPlanting && observation.source == QuestFarmingStateReader.Source.LIVE &&
            observation.state == CropState.GROWING && step.crop != null && step.patch.getPatchType().usesCompost())
        {
            WorldPoint anchor = plugin.directorPatchAnchor(step.patch);
            if (anchor != null)
            {
                PatchTimer timer = timerManager.findTimer(anchor, step.patch.getPatchType(), 0);
                treated = timer != null && timer.isCompostApplied() &&
                    step.crop.equals(timer.getCrop()) && timer.getPlantedAt().toEpochMilli() >= activatedAtMillis;
            }
        }
        final boolean compostConfirmed = treated;
        final boolean weeds = plugin.directorPatchHasWeeds(step.patch);
        WorldPoint localPatch = plugin.directorPatchAnchor(step.patch);
        final boolean near = observation.source == QuestFarmingStateReader.Source.LIVE ||
            (localPatch != null && client.getLocalPlayer() != null && localPatch.distanceTo(client.getLocalPlayer().getWorldLocation()) <= 18);
        final long activation = activatedAtMillis;
        SwingUtilities.invokeLater(() -> { if (activation == activatedAtMillis) acceptObservation(plan, index, latest, compostConfirmed, weeds, near); });
    }

    /** Only the EDT owns live guide state; stale asynchronous ticks are dropped. */
    private void acceptObservation(RunPlanner.Plan plan, int index,
                                   QuestFarmingStateReader.Observation observation, boolean compostConfirmed, boolean weeds, boolean near)
    {
        if (plan != currentPlan || index != currentStep || planningInFlight || preparingLoadout || paused || runFinished || awaitingBank
            || compostPageOpen || !Objects.equals(activeProfile, configManager.getRSProfileKey()))
        {
            return;
        }
        boolean fresh = observation.isFreshSince(activatedAtMillis);
        if (!observedInitialState)
        {
            observedInitialState = true;
            initiallyGrowing = observation.known() && observation.state == CropState.GROWING;
        }

        RunPlanner.Step step = plan.steps.get(index);
        if (observation.source == QuestFarmingStateReader.Source.LIVE && observation.state == CropState.EMPTY)
            sawLiveEmpty = true;
        StepEngine.Action action = StepEngine.actionForStep(
            step, observation.state, observation.protectedByFarmer);
        action = StepEngine.withCompost(action, step, plannedCompostItem() >= 0,
            sawLiveEmpty || sawHarvestable && !initiallyGrowing, compostConfirmed);
        QuestTargets.Definition def = QuestTargets.get(step.patch);
        action = StepEngine.refine(action, activePayRemoval, def != null && def.gardenerId >= 0, weeds);
        if (action == StepEngine.Action.CHOP_TREE && step.patch.getPatchType() == PatchType.CALQUAT)
            action = StepEngine.Action.REMOVE_TREE;
        boolean changed = near != nearCurrentTarget || visibleObservation == null ||
            !Objects.equals(visibleObservation.state, observation.state) ||
            visibleObservation.source != observation.source ||
            !Objects.equals(visibleObservation.protectedByFarmer, observation.protectedByFarmer) ||
            action != visibleAction;
        if (near && nearCurrentTarget && visibleObservation != null &&
            visibleObservation.source == QuestFarmingStateReader.Source.LIVE && observation.source == QuestFarmingStateReader.Source.LIVE &&
            visibleAction != action && visibleAction != StepEngine.Action.INSPECT && visibleAction != StepEngine.Action.COMPLETE)
            observedInteractions.add(visibleAction);
        nearCurrentTarget = near;
        visibleObservation = observation;
        if (visibleAction != action) diagnostics.record("Interaction: " + visibleAction.name() + " → " + action.name());
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
        if (autoNext.isSelected() && StepEngine.canFinishHarvestOnly(
            step.harvestOnly, sawHarvestable, observation.state,
            observation.source == QuestFarmingStateReader.Source.LIVE))
        {
            moveStep(1, true);
            return;
        }
        if (autoNext.isSelected() && StepEngine.canAutoAdvance(
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
        routeStatus.setText(wrapped(navigationEnabled ? "Route not yet requested" : "Navigation disabled", 184));
        observedInitialState = false;
        initiallyGrowing = false;
        sawHarvestable = false;
        sawLiveEmpty = false;
        nearCurrentTarget = false;
        observedInteractions.clear();
        visibleObservation = null;
        visibleAction = StepEngine.Action.INSPECT;
    }

    private void moveStep(int delta, boolean automatic)
    {
        if (currentPlan == null || planningInFlight || preparingLoadout || paused || runFinished) return;
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
        int nextIndex = delta > 0
            ? Progress.nextUnfinished(currentPlan.steps.size(), previous, completedSteps)
            : Math.max(0, currentStep - 1);
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
        // A bank checkpoint is a fresh planning boundary. Reusing the old
        // wave could include a disabled patch or supplies already consumed.
        beginRun(true);
    }

    private void requestBankRoute()
    {
        final long generation = ++routeGeneration;
        final BankStops.Bank preferred =
            (BankStops.Bank) bankChoice.getSelectedItem();
        clientThread.invoke(() ->
        {
            if (!navigationEnabled || generation != routeGeneration || !awaitingBank || paused || compostPageOpen ||
                !Objects.equals(activeProfile, configManager.getRSProfileKey()) ||
                client.getLocalPlayer() == null) return;
            WorldPoint player = client.getLocalPlayer().getWorldLocation();
            BankStops.Bank bank = BankStops.choose(
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

    private TravelTarget routeTarget(FarmRunPatch patch)
    {
        WorldPoint pinned = WaypointPins.get(configManager, patch);
        return pinned == null ? TravelTarget.choose(patch, stateReader, timerManager)
            : TravelTarget.playerWaypoint(pinned);
    }

    private void chooseRouteOption()
    {
        if (currentPlan == null || runFinished || awaitingBank ||
            currentStep < 0 || currentStep >= currentPlan.steps.size() || planningInFlight) return;
        RunPlanner.Step step = currentPlan.steps.get(currentStep);
        String profile = configManager.getRSProfileKey();
        if (profile == null || !Objects.equals(profile, activeProfile)) return;
        Object choice = JOptionPane.showInputDialog(this,
            "Choose a navigation action for " + step.patch.getDisplayName() + ":",
            "Patch navigation", JOptionPane.PLAIN_MESSAGE, null,
            new String[]{"Request route again", "Save my current tile as waypoint", "Remove saved waypoint"},
            "Request route again");
        if (choice == null || !Objects.equals(profile, configManager.getRSProfileKey())) return;
        if ("Request route again".equals(choice)) requestRoute(step);
        else if ("Remove saved waypoint".equals(choice))
        {
            WaypointPins.clear(configManager, step.patch);
            requestRoute(step);
        }
        else
        {
            clientThread.invoke(() ->
            {
                if (!Objects.equals(profile, configManager.getRSProfileKey()) ||
                    client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
                boolean saved = WaypointPins.save(configManager, step.patch,
                    client.getLocalPlayer().getWorldLocation());
                SwingUtilities.invokeLater(() ->
                {
                    if (!Objects.equals(profile, configManager.getRSProfileKey())) return;
                    if (saved)
                    {
                        routeStatus.setText(wrapped("Saved your waypoint for this patch.", 184));
                        requestRoute(step);
                    }
                    else JOptionPane.showMessageDialog(this,
                        "Stand in the patch's farming area before saving a waypoint.");
                });
            });
        }
    }

    private void requestRoute(RunPlanner.Step step)
    {
        if (!navigationEnabled || preparingLoadout || paused || compostPageOpen || awaitingBank || runFinished || planningInFlight)
        {
            return;
        }
        // All path requests use observed or verified destinations. Uncertain
        // Quest Helper region centres are displayed but not routed to.
        final long generation = ++routeGeneration;
        clientThread.invoke(() ->
        {
            RunPlanner.Plan active = currentPlan;
            TravelTarget target = routeTarget(step.patch);
            if (!navigationEnabled || generation != routeGeneration || active == null || paused || runFinished
                || compostPageOpen || preparingLoadout || awaitingBank || planningInFlight || !Objects.equals(activeProfile, configManager.getRSProfileKey())
                || currentStep < 0 || currentStep >= active.steps.size() || active.steps.get(currentStep) != step)
            {
                return;
            }
            boolean routable = client.getLocalPlayer() != null && target.routable();
            diagnostics.record("Navigation: " + (routable ? "verified target" : "unverified target"));
            SwingUtilities.invokeLater(() ->
            {
                if (generation != routeGeneration) return;
                routeStatus.setText(wrapped(routable ? target.source + " · route requested" :
                    "No verified route tile. Visit and save a waypoint, or navigate manually.", 184));
            });
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
        if (currentPlan == null || planningInFlight || preparingLoadout || paused || targetIndex < 0
            || targetIndex >= currentPlan.steps.size())
        {
            return;
        }
        int previous = currentStep;
        awaitingBank = false;
        currentStep = targetIndex;
        runFinished = false;
        if (completedSteps.contains(targetIndex))
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
        else if (preparingLoadout)
        {
            startWhenSupplied();
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
        planningInFlight = false;
        String stoppedProfile = activeProfile;
        preparingLoadout = false;
        currentPlan = null;
        currentSession = null;
        activeRunSelection = null;
        activeProfile = null;
        waveIndex = 0;
        awaitingBank = false;
        suggestedBank = null;
        paused = false;
        currentStep = 0;
        runFinished = false;
        completedSteps.clear();
        completedPatchKeys.clear();
        temporaryOverrides.clear();
        if (stoppedProfile != null && Objects.equals(stoppedProfile, configManager.getRSProfileKey()))
            clearSavedRun();
        start.setEnabled(true);
        activateCurrentStep();
        clearRoute();
        render();
    }

    void resetCharacter()
    {
        ++planningGeneration;
        worldTarget = null;
        preparingLoadout = false;
        directorRunDisplayed = false;
        loadout.setDirectorBankGuidance(false);
        plugin.refreshDirectorBankFilter();
        planningInFlight = false;
        SwingUtilities.invokeLater(() ->
        {
            currentPlan = null;
            currentSession = null;
            activeRunSelection = null;
            activeProfile = null;
            compostStops = Collections.emptyList();
            compostIndex = 0;
            ++compostGeneration;
            waveIndex = 0;
            awaitingBank = false;
            suggestedBank = null;
            currentStep = 0;
            paused = false;
            runFinished = false;
            completedSteps.clear();
            completedPatchKeys.clear();
            temporaryOverrides.clear();
            patchViews.clear();
            collapsedTypes.clear(); collapsedTypes.addAll(EnumSet.allOf(PatchType.class));
            openPatchLocations.clear(); advancedPatchTypes.clear();
            expandedLocations.clear(); expandedRun = null;
            routeModel.clear();
            start.setEnabled(true);
            activateCurrentStep();
            loadPreferences();
            loadingPresets = true;
            presetChoice.removeAllItems();
            loadingPresets = false;
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
            else
            {
                if ("loadout".equals(visiblePage)) refreshLoadoutPage();
                if ("guide".equals(visiblePage)) refreshGuideRequirements();
                if ("guide".equals(visiblePage) || "loadout".equals(visiblePage)) startWhenSupplied();
            }
        });
    }

    void shutDown()
    {
        SwingUtilities.invokeLater(patchRefresh::stop);
        resetCharacter();
    }

    WorldGuide.Target activeWorldTarget()
    {
        WorldGuide.Target target = worldTarget;
        return target != null && OverlayPolicy.world(paused, preparingLoadout, planningInFlight, runFinished, compostPageOpen)
            && Objects.equals(target.profile, configManager.getRSProfileKey()) ? target : null;
    }

    boolean directorRunDisplayed()
    {
        return directorRunDisplayed || planningInFlight;
    }

    private int plannedCompostItem()
    {
        if (currentSession == null || waveIndex >= currentSession.supplies.size()) return -1;
        Map<Integer, Integer> kit = currentSession.supplies.get(waveIndex);
        for (CompostChoice choice : CompostChoice.values())
            if (choice.item >= 0 && kit.containsKey(choice.item)) return choice.item;
        return -1;
    }

    private void publishWorldTarget()
    {
        directorRunDisplayed = currentPlan != null || compostPageOpen;
        worldTarget = currentPlan != null && !paused && !preparingLoadout && !runFinished && !awaitingBank &&
            !planningInFlight && !compostPageOpen && currentStep >= 0 &&
            currentStep < currentPlan.steps.size() && activeProfile != null
            ? new WorldGuide.Target(currentPlan.steps.get(currentStep), visibleAction,
                activeProfile, plannedCompostItem() >= 0,
                plannedCompostItem(),
                currentSession == null ? RouteTeleport.NONE : currentSession.teleportByPatch.getOrDefault(
                    FarmRouteManager.key(currentPlan.steps.get(currentStep).patch), RouteTeleport.NONE),
                !nearCurrentTarget) : null;
    }

    private void publishBankChecklist()
    {
        BankChecklist next = null;
        if (!compostPageOpen && currentSession != null && currentPlan != null && !runFinished
            && !planningInFlight && activeProfile != null && Objects.equals(activeProfile, configManager.getRSProfileKey()))
        {
            int index = awaitingBank && waveIndex + 1 < currentSession.waves.size() ? waveIndex + 1 : waveIndex;
            RunPlanner.Plan wave = index < currentSession.waves.size() ? currentSession.waves.get(index) : currentPlan;
            Map<Integer, Integer> kit = index < currentSession.supplies.size() ? currentSession.supplies.get(index) : Collections.emptyMap();
            Set<Integer> done = awaitingBank ? Collections.emptySet() : completedSteps;
            List<FarmRunPatch> remaining = new ArrayList<>();
            for (int i = 0; i < wave.steps.size(); i++)
                if (!done.contains(i)) remaining.add(wave.steps.get(i).patch);
            Map<RouteTeleport, Integer> travel = FarmRouteManager.requirements(remaining, currentSession.teleportByPatch);
            next = BankChecklist.remaining(wave, kit, travel, done);
        }
        boolean changed = loadout.publishDirectorChecklist(next);
        boolean bankGuidance = OverlayPolicy.bank(next != null, preparingLoadout, awaitingBank,
            paused, runFinished, planningInFlight, compostPageOpen);
        if (loadout.setDirectorBankGuidance(bankGuidance) || changed) plugin.refreshDirectorBankFilter();
    }

    private void render()
    {
        stepList.removeAll();
        boolean active = currentPlan != null;
        int steps = active ? currentPlan.steps.size() : 0;
        int waveCount = currentSession == null ? 0 : currentSession.waves.size();
        String wave = waveCount > 0 ? "Wave " + (waveIndex + 1) + " / " + waveCount : "No wave";
        progress.setText(wrapped(!active ? "Configure a run and press Start"
            : awaitingBank ? "Bank checkpoint · " + wave
            : runFinished ? (currentSession != null && currentSession.unplanned > 0
                ? "Partial run · unplanned patches remain"
                : "All planned patches completed")
            : steps == 0 ? "No planting steps allocated"
            : wave + " · patch " + (currentStep + 1) + " / " + steps
                + (paused ? " (paused)" : ""), 184));
        inventory.setText(wrapped(!active ? "28 inventory slots available"
            : currentPlan.occupiedSlots + " planned + " + currentPlan.reservedSlots
                + " reserved / 28 slots", 184));
        storage.setText(!active ? "Bank & vault snapshots are checked at Start"
            : "Using observed stock; reopen storage if estimates are stale");
        String actionText;
        if (!active)
        {
            actionText = "Start a run to see state-driven instructions.";
        }
        else if (steps == 0)
        {
            actionText = "No actionable stops planned. Check planning details, scan storage or change your patch and crop choices.";
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
                + "Replan after scanning storage.";
        }
        else
        {
            RunPlanner.Step current = currentPlan.steps.get(currentStep);
            actionText = current.harvestOnly && visibleAction == StepEngine.Action.PLANT
                ? "Harvest/clearing complete or patch empty. No replacement seed "
                    + "allocated; press Next, or restock and Replan remaining."
                : StepEngine.instruction(visibleAction, current.crop,
                    current.patch.getPatchType(), plannedCompostItem() >= 0);
            if (visibleObservation != null)
            {
                String source = visibleObservation.source.name().replace('_', ' ');
                actionText += " (" + source + (visibleObservation.state == null
                    ? ": unknown)" : ": " + visibleObservation.state.name().toLowerCase() + ")");
            }
        }
        publishWorldTarget();
        if (active && !runFinished && !awaitingBank && currentStep < steps && !preparingLoadout && !guideDetails.isSelected())
        {
            RunPlanner.Step step = currentPlan.steps.get(currentStep);
            actionText = !nearCurrentTarget
                ? (worldTarget == null ? "Travel to " + step.patch.getLocation() : WorldGuide.travelInstruction(worldTarget)) + (navigationEnabled ? " · follow Shortest Path." : ".")
                : WorldGuide.instruction(visibleAction, step.crop, step.patch.getPatchType(),
                    plannedCompostItem() >= 0);
        }
        advancedGuideControls.setVisible(guideDetails.isSelected());
        progress.setVisible(guideDetails.isSelected() || awaitingBank || runFinished);
        inventory.setVisible(guideDetails.isSelected() || awaitingBank);
        if (preparingLoadout) actionText = "Gather the missing supplies below. Guidance starts when the checklist is ready.";
        currentAction.setText(wrapped(actionText, 184));
        activeDestination.setVisible(active && !runFinished && !awaitingBank && currentStep < steps);
        activeDestination.setText(active && currentStep < steps
            ? wrapped(currentPlan.steps.get(currentStep).patch.getDisplayName(), 184) : "");
        openLoadout.setEnabled(active);
        routeOptions.setEnabled(active && !planningInFlight && !runFinished && !awaitingBank && currentStep < steps);
        routeStatus.setVisible(guideDetails.isSelected() && active && !runFinished && !awaitingBank && currentStep < steps);
        if (!navigationEnabled) routeStatus.setText(wrapped("Shortest Path navigation disabled.", 184));
        activeTitle.setText(wrapped(activeRunSelection == null ? "Active guide" :
            activeRunSelection == RunSelection.MERGED ? activePresetName :
            activeRunSelection.toString(), 184));
        int complete = completedSteps.size();
        progressBar.setMaximum(Math.max(1, steps));
        progressBar.setValue(complete);
        progressBar.setString(complete + " / " + steps + " stops");
        int warningCount = currentSession == null ? 0 : currentSession.warnings.size();
        guideWarning.setVisible((guideDetails.isSelected() && warningCount > 0) || planningInFlight);
        guideWarning.setText(wrapped(planningInFlight ? "Planning…" :
            warningCount + " planning notices · see details", 184));
        if (active && currentSession != null && currentSession.unplanned > 0)
        {
            guideWarning.setText(wrapped(currentSession.unplanned + " unplanned stops · check details", 184));
            guideWarning.setVisible(true);
        }
        returnGuide.setEnabled(active && !runFinished);
        returnGuide.setVisible(active && !runFinished);
        selectionStatus.setText(wrapped(active && !runFinished ?
            "Run in progress · " + completedPatchKeys.size() + " completed" :
            "Choose a run to plan your next harvest.", 184));
        back.setEnabled(active && !runFinished && !paused && !preparingLoadout && !planningInFlight && currentStep > 0);
        next.setEnabled(active && !paused && !preparingLoadout && !planningInFlight && !runFinished && (awaitingBank || currentStep < steps));
        next.setText(awaitingBank ? "Replan" : "Next step");
        next.setToolTipText(awaitingBank
            ? "Continue after banking. No items are withdrawn automatically." : null);
        pause.setEnabled(active && !planningInFlight && steps > 0 && !runFinished);
        end.setEnabled(active);
        replan.setEnabled(active && !planningInFlight && currentSession != null);
        resume.setEnabled(!profileValue("directorSavedMode", "").isEmpty());
        resume.setVisible(resume.isEnabled() && !(active && !runFinished));
        overrideCrop.setEnabled(active && !planningInFlight && !runFinished && !awaitingBank && steps > 0);
        clearOverride.setEnabled(active && !planningInFlight && !runFinished && !awaitingBank && steps > 0);
        pause.setText(paused ? "Resume" : "Pause");

        if (active)
        {
            for (int i = 0; i < steps; i++)
            {
                if (!guideDetails.isSelected() && (awaitingBank || runFinished)) continue;
                RunPlanner.Step step = currentPlan.steps.get(i);
                JPanel item = new JPanel();
                item.setLayout(new BoxLayout(item, BoxLayout.Y_AXIS));
                boolean isCurrent = i == currentStep && !runFinished && !awaitingBank;
                boolean isDone = completedSteps.contains(i);
                item.setBackground(isCurrent ? FarmingHelperTheme.SELECTED : FarmingHelperTheme.SURFACE);
                item.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 1, 0, isCurrent
                        ? FarmingHelperTheme.GOLD : isDone ? new Color(120, 173, 113) : ColorScheme.BORDER_COLOR),
                    BorderFactory.createEmptyBorder(8, 9, 8, 7)));
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                final int index = i;
                JButton place = new JButton(wrapped((isCurrent ? "▶ "
                    : isDone ? "✓ " : "○ ") + step.patch.getDisplayName(), 128));
                place.setForeground(isCurrent ? FarmingHelperTheme.GOLD :
                    isDone ? new Color(143, 187, 136) : Color.WHITE);
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
                if (isCurrent && guideDetails.isSelected())
                {
                    JButton edit = new JButton("✎");
                    edit.setToolTipText("Set a permanent crop preference for this patch");
                    edit.addActionListener(e -> chooseCropOverride());
                    stepHeader.add(edit, BorderLayout.EAST);
                }
                item.add(stepHeader);
                if (isCurrent || guideDetails.isSelected()) item.add(Box.createVerticalStrut(4));
                String instruction = isCurrent
                    ? (step.crop == null ? step.patch.getPatchType().getDisplayName() :
                        step.crop.getName() + " · " + step.patch.getPatchType().getDisplayName())
                    : isDone ? "Completed / skipped"
                    : step.requiresPlanting ? "Harvest / clear / plant " + step.crop.getName()
                    : step.harvestOnly ? "Harvest-only: refill before replanting"
                    : "Existing crop: check condition or gardener protection";
                if (isCurrent && preparingLoadout) item.add(warning("Gather supplies before travelling."));
                else if (isCurrent && !guideDetails.isSelected())
                {
                    for (StepEngine.Action previous : observedInteractions)
                    {
                        JLabel finished = new JLabel(wrapped("✓ " + WorldGuide.instruction(previous, step.crop,
                            step.patch.getPatchType(), plannedCompostItem() >= 0), 157));
                        finished.setForeground(new Color(143, 187, 136));
                        item.add(finished);
                    }
                    JLabel actionLine = new JLabel(wrapped("▶ " + (worldTarget != null && worldTarget.travelling
                        ? WorldGuide.travelInstruction(worldTarget)
                        : WorldGuide.instruction(visibleAction, step.crop, step.patch.getPatchType(), plannedCompostItem() >= 0)), 157));
                    actionLine.setForeground(FarmingHelperTheme.SUCCESS);
                    item.add(actionLine);
                }
                else if (isCurrent || guideDetails.isSelected()) item.add(warning(instruction));
                if (isCurrent && guideDetails.isSelected() && step.protectionPlanned)
                    item.add(warning("Gardener protection planned / verify payment"));
                if (isCurrent && guideDetails.isSelected())
                {
                    // The panel rendering is on EDT. Use already-known Quest
                    // Helper exact locations; observed/area hints are loaded by
                    // the live reader on the client thread and displayed there.
                    WorldPoint point = Waypoints.exactPoint(step.patch);
                    if (point != null)
                    {
                        boolean exactTile = step.patch.getPatchType() == PatchType.HERB ||
                            StepEngine.isTreeType(step.patch.getPatchType());
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
            if (guideDetails.isSelected() && awaitingBank && currentSession != null &&
                waveIndex + 1 < currentSession.waves.size())
            {
                RunPlanner.Plan nextWave = currentSession.waves.get(waveIndex + 1);
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
                if (waveIndex + 1 < currentSession.supplies.size())
                    stepList.add(warning("Next tools and compost: " +
                        formatItems(currentSession.supplies.get(waveIndex + 1))));
                if (waveIndex + 1 < currentSession.teleports.size())
                {
                    stepList.add(warning("Next wave travel items: "
                        + formatTeleports(currentSession.teleports.get(waveIndex + 1))));
                }
            }
            if (guideDetails.isSelected() && !awaitingBank && currentSession != null && waveIndex == 0)
            {
                Map<Integer, Integer> kit = currentSession.supplies.isEmpty()
                    ? Collections.emptyMap() : currentSession.supplies.get(0);
                StockLedger.Manifest manifest = currentSession.observedStock.manifest(currentPlan, kit);
                if (!manifest.missing.isEmpty())
                    stepList.add(warning("Missing at planning: " + formatItems(manifest.missing)));
            }
            if (guideDetails.isSelected())
            {
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
                for (String message : new java.util.LinkedHashSet<>(currentSession.warnings))
                    if (!currentPlan.warnings.contains(message)) stepList.add(warning(message));
            }
            }
        }
        else
        {
            stepList.add(warning("Choose a run on the Runs page to get guided steps. "
                + "The patch manager and settings can be opened without ending a run."));
        }
        FarmingHelperTheme.style(stepList);
        stepList.revalidate();
        stepList.repaint();
        publishWorldTarget();
        publishBankChecklist();
        refreshGuideRequirements();
        if ("loadout".equals(visiblePage)) refreshLoadoutPage();
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
            details.append(crop == null ? Supplies.name(entry.getKey()) : crop.getItemName());
            details.append(" x").append(entry.getValue());
        }
        return details.toString();
    }

    private static JLabel warning(String message)
    {
        JLabel label = new JLabel(wrapped(message, 170));
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        label.setForeground(Color.LIGHT_GRAY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static String wrapped(String raw, int width)
    {
        return "<html><body style='font-family: sans-serif; width:" + Math.min(width, 170) + "px'>" + escape(raw) + "</body></html>";
    }

    private static String escape(String raw)
    {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
