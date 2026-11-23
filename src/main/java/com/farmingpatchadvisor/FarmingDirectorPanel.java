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

    private final JComboBox<PatchType> runType =
        new JComboBox<>(new PatchType[]{PatchType.HERB, PatchType.TREE});
    private final JCheckBox customPriority = new JCheckBox("Use custom priority");
    private final JCheckBox protectTrees = new JCheckBox("Carry tree protection payments", true);
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
    private DirectorPlanner.Plan currentPlan;
    private int currentStep;
    private boolean paused;
    private volatile boolean routeIssued;
    private boolean loadingPreferences;

    @Inject
    FarmingDirectorPanel(Client client, ClientThread clientThread, EventBus eventBus,
                         ConfigManager configManager, ItemManager itemManager, FarmingLoadout loadout,
                         FarmingPatchAdvisorPlugin plugin, FarmingPatchAdvisorConfig config,
                         FarmRouteManager routes, FarmingPatchPanel advisorPanel)
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
        customPriority.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        customPriority.setForeground(Color.WHITE);
        controls.add(customPriority);
        controls.add(label("Priority order (comma-separated)", false));
        priorities.setToolTipText("Example: Ranarr, Snapdragon, Toadflax");
        controls.add(priorities);
        controls.add(Box.createVerticalStrut(5));
        controls.add(label("Reserved stock (example: Ranarr=10, Snapdragon=3)", false));
        reserves.setToolTipText("Reserved quantities will not be allocated to a run.");
        controls.add(reserves);
        controls.add(Box.createVerticalStrut(5));
        protectTrees.setForeground(Color.WHITE);
        protectTrees.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        controls.add(protectTrees);
        controls.add(Box.createVerticalStrut(8));
        start.addActionListener(e -> beginRun());
        start.setAlignmentX(Component.LEFT_ALIGNMENT);
        start.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        controls.add(start);
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
        PatchType type = (PatchType) runType.getSelectedItem();
        String name = type == PatchType.TREE ? "Tree" : "Herb";
        priorities.setText(profileValue("directorPriority" + name,
            type == PatchType.TREE ? "Magic, Yew, Maple, Willow, Oak"
                : "Ranarr, Snapdragon, Toadflax, Irit, Avantoe"));
        reserves.setText(profileValue("directorReserve" + name, ""));
        customPriority.setSelected(Boolean.parseBoolean(profileValue("directorCustom" + name, "false")));
        protectTrees.setSelected(Boolean.parseBoolean(profileValue("directorProtectTrees", "true")));
        protectTrees.setVisible(type == PatchType.TREE);
        loadingPreferences = false;
    }

    private void savePreferences()
    {
        if (configManager.getRSProfileKey() == null)
        {
            return;
        }
        String name = runType.getSelectedItem() == PatchType.TREE ? "Tree" : "Herb";
        configManager.setRSProfileConfiguration(GROUP, "directorPriority" + name, priorities.getText());
        configManager.setRSProfileConfiguration(GROUP, "directorReserve" + name, reserves.getText());
        configManager.setRSProfileConfiguration(GROUP, "directorCustom" + name,
            Boolean.toString(customPriority.isSelected()));
        configManager.setRSProfileConfiguration(GROUP, "directorProtectTrees",
            Boolean.toString(protectTrees.isSelected()));
    }

    private void beginRun()
    {
        savePreferences();
        final PatchType type = (PatchType) runType.getSelectedItem();
        final boolean custom = customPriority.isSelected();
        final String priorityText = priorities.getText();
        final String reserveText = reserves.getText();
        final boolean protection = protectTrees.isSelected();
        start.setEnabled(false);
        clientThread.invoke(() ->
        {
            DirectorPlanner.Plan result = compute(type, custom, priorityText, reserveText, protection);
            SwingUtilities.invokeLater(() ->
            {
                currentPlan = result;
                currentStep = 0;
                paused = false;
                start.setEnabled(true);
                render();
                if (!currentPlan.steps.isEmpty())
                {
                    requestRoute(currentPlan.steps.get(0));
                }
            });
        });
    }

    private DirectorPlanner.Plan compute(PatchType type, boolean custom, String priorityText,
                                         String reserveText, boolean protection)
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
        FarmRunFilter filter = type == PatchType.TREE ? FarmRunFilter.TREE : FarmRunFilter.HERB;
        for (FarmRunPatch patch : routes.activePatches(filter))
        {
            if (patch.getPatchType() != type)
            {
                continue;
            }
            if (DirectorWaypoints.point(type, patch.getLocation()) == null)
            {
                warnings.add("No verified waypoint for " + patch.getDisplayName() + ".");
                continue;
            }
            String access = DirectorWaypoints.accessIssue(client, type, patch.getLocation(), level,
                config.directorIncludeUnverifiedLocations());
            if (access != null)
            {
                warnings.add(access);
                continue;
            }
            candidates.add(patch);
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

        int toolSlots = type == PatchType.TREE ? 2 : 3; // Rake, spade, plus herb seed dibber.
        if (type == PatchType.HERB && stock.containsKey(ItemID.FAIRY_ENCHANTED_SECATEURS)
            && !loadout.directorEquipment().containsKey(ItemID.FAIRY_ENCHANTED_SECATEURS))
        {
            toolSlots++;
        }
        Set<RouteTeleport> distinctTeleports = new HashSet<>();
        for (FarmRunPatch patch : candidates)
        {
            RouteTeleport teleport = routes.teleport(filter, patch);
            if (teleport != RouteTeleport.NONE)
            {
                distinctTeleports.add(teleport);
            }
        }
        int teleportSlots = distinctTeleports.size();
        int harvestReserve = type == PatchType.HERB ? config.directorHarvestSlots() : 2;
        DirectorPlanner.Plan plan = DirectorPlanner.build(type, candidates, level, stock,
            custom, priorityText, reserveText, toolSlots, teleportSlots, harvestReserve,
            protection, id -> itemManager.getItemComposition(id).isStackable());

        if (type == PatchType.TREE && plan.steps.isEmpty())
        {
            // Raw tree seeds are not interchangeable with plantable saplings.
            int[] rawSeeds = {ItemID.ACORN, ItemID.WILLOW_SEED, ItemID.MAPLE_SEED,
                ItemID.YEW_SEED, ItemID.MAGIC_TREE_SEED};
            for (int rawId : rawSeeds)
            {
                if (stock.getOrDefault(rawId, 0) > 0)
                {
                    warnings.add("Tree seeds found but no suitable saplings were allocated. "
                        + "Prepare saplings in filled plant pots and let them grow.");
                    break;
                }
            }
        }
        for (Map.Entry<Integer, Integer> item : plan.payments.entrySet())
        {
            if (stock.getOrDefault(item.getKey(), 0) < item.getValue())
            {
                warnings.add("Tree protection payment missing: item " + item.getKey()
                    + ", need " + item.getValue() + ".");
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

    private static boolean fresh(long scannedAt, long now)
    {
        return scannedAt > 0 && scannedAt <= now && now - scannedAt < FRESH_MILLIS;
    }

    private void requestRoute(DirectorPlanner.Step step)
    {
        if (paused)
        {
            return;
        }
        WorldPoint destination = DirectorWaypoints.point(step.patch.getPatchType(),
            step.patch.getLocation());
        clientThread.invoke(() ->
        {
            if (client.getLocalPlayer() == null || destination == null)
            {
                return;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("start", client.getLocalPlayer().getWorldLocation());
            data.put("target", destination);
            // The receiver may not be installed: posting is a safe no-op then.
            eventBus.post(new PluginMessage("shortestpath", "path", data));
            routeIssued = true;
        });
    }

    private void clearRoute()
    {
        if (!routeIssued)
        {
            return;
        }
        clientThread.invoke(() ->
        {
            if (routeIssued)
            {
                eventBus.post(new PluginMessage("shortestpath", "clear"));
                routeIssued = false;
            }
        });
    }

    private void advance(int delta)
    {
        if (currentPlan == null || currentPlan.steps.isEmpty())
        {
            return;
        }
        currentStep = Math.max(0, Math.min(currentPlan.steps.size() - 1, currentStep + delta));
        render();
        requestRoute(currentPlan.steps.get(currentStep));
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
        currentPlan = null;
        paused = false;
        currentStep = 0;
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
            : "Step " + (currentStep + 1) + " / " + steps + (paused ? "  (paused)" : ""));
        inventory.setText(!active ? "28 inventory slots available"
            : currentPlan.occupiedSlots + " planned + " + currentPlan.reservedSlots
                + " reserved / 28 slots");
        storage.setText(!active ? "Bank & vault snapshots are checked at Start"
            : "Using observed stock; reopen storage if estimates are stale");
        back.setEnabled(active && currentStep > 0);
        next.setEnabled(active && currentStep < steps - 1);
        pause.setEnabled(active && steps > 0);
        end.setEnabled(active);
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
                JLabel place = label((i == currentStep ? "▶ " : "○ ")
                    + step.patch.getDisplayName(), true);
                place.setForeground(i == currentStep ? new Color(244, 197, 86) : Color.WHITE);
                item.add(place);
                item.add(Box.createVerticalStrut(4));
                item.add(label("Harvest / clear if needed; plant " + step.crop.getName()
                    + (step.patch.getPatchCount() > 1 ? " x" + step.patch.getPatchCount() : ""), false));
                WorldPoint point = DirectorWaypoints.point(step.patch.getPatchType(),
                    step.patch.getLocation());
                if (i == currentStep && point != null)
                {
                    item.add(label("Destination: " + point.getX() + ", " + point.getY()
                        + " · Shortest Path if installed", false));
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
            stepList.add(warning("Follow the numbered steps once a plan is generated. "
                + "The Advisor tab retains existing timers, checklists, "
                + "protection payments and route editing."));
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
