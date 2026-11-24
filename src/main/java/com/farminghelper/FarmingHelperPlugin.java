package com.farminghelper;

import com.google.inject.Provides;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.game.ItemManager;
import javax.swing.ImageIcon;

@PluginDescriptor(name = "Farming Helper", description = "Farming guides with stock-aware seed choices",
    tags = {"farming", "seed", "herb", "tree"})
public class FarmingHelperPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private PluginManager plugins;
    private NavigationBridge navigation;
    private SeedVaultViews vaultViews;
    private volatile long preferenceRevision;
    private volatile RouteLeg activeLeg;
    @Inject private ClientThread thread;
    @Inject private ConfigManager config;
    @Inject private FarmingHelperConfig legacy;
    @Inject private ClientToolbar toolbar;
    @Inject private OverlayManager overlays;
    @Inject private PatchHighlight highlight;
    @Inject private GuideItemHighlight itemHighlight;
    @Inject private EventBus bus;
    @Inject private QuestFarmingStateReader reader;

    private final List<FarmRunPatch> patches = new ArrayList<>();
    private final Set<FarmRunPatch> skipped = new HashSet<>(), completed = new HashSet<>();
    private final GuideProgress progress = new GuideProgress();
    private Crop activeCrop;
    private volatile StepEngine.Action activeAction = StepEngine.Action.INSPECT;
    private volatile boolean activeLive;
    private volatile Set<Integer> requiredItems = Collections.emptySet(), recommendedItems = Collections.emptySet();
    private volatile long session;
    private volatile boolean guideRequested;
    private final Map<Integer,Integer> bank = new HashMap<>(), vault = new HashMap<>();
    private String stockProfile;
    private volatile Map<Integer,ImageIcon> icons = Collections.emptyMap();
    private Set<FarmRunType> run = Collections.emptySet();
    private Set<String> manualKeys; // physical selections, never persisted as a session
    private String runLabel;
    private List<String> referenceOrder;
    private boolean optimizeManual, initialPrepChecked, entranaOffered;
    private volatile boolean preparing, hideSeedless;
    private boolean visitedStorage;
    private final Set<String> resupplyOffered=new HashSet<>();
    private Preparation.Bank preparationBank;
    private String preparationReason="";
    private List<RunOrder.Group> runOrder;
    private List<FarmRunPatch> pinned;
    private boolean orderProbeSent,orderFrozen;
    private String orderProbeId;
    private int orderTicks,orderProbeSequence;
    private boolean shortestPathAvailable;
    private FarmingHelperPanel panel;
    private NavigationButton nav;
    private volatile FarmRunPatch current;
    private String lastView;
    private volatile Map<String,String> accessNotes = Collections.emptyMap();
    private int lastAccessLevel=-1;
    private volatile boolean running;

    @Provides FarmingHelperConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(FarmingHelperConfig.class);
    }

    @Override protected void startUp()
    {
        running = true;
        navigation=new NavigationBridge(bus::post);
        if(itemManager!=null)vaultViews=new SeedVaultViews(client,itemManager,this);
        shortestPathAvailable=plugins.getPlugins().stream().anyMatch(p ->
            "Shortest Path".equals(p.getName()) && plugins.isPluginActive(p));
        navigation.enabled(shortestPathAvailable);
        for (FarmRunPatch group : FarmRunCatalog.patches())
            patches.addAll(PatchNames.expand(group,
                reader.patchNamesForType(group.getPatchType().name(), group.getLocation())));
        panel = new FarmingHelperPanel(this);
        nav = NavigationButton.builder().tooltip("Farming Helper").icon(createNavigationIcon())
            .panel(panel).priority(8).build();
        toolbar.addNavigation(nav);
        overlays.add(highlight);
        if (itemHighlight != null) overlays.add(itemHighlight);
        SwingUtilities.invokeLater(panel::home);
    }

    @Override protected void shutDown()
    {
        running = false;
        if(vaultViews!=null){SeedVaultViews view=vaultViews;vaultViews=null;thread.invokeLater(() -> {view.close();view.flushScrollbar();});}
        reset();
        toolbar.removeNavigation(nav);
        overlays.remove(highlight);
        if (itemHighlight != null) overlays.remove(itemHighlight);
        patches.clear();
        highlight.clear();
    }

    void start(Set<FarmRunType> types)
    {
        start(types,null,null,null,false);
    }

    void startPreset(RunPreset preset)
    { startSelected(preset.select(patches,this::flag),preset.title,preset.reference,false); }
    void startIndividual(PatchType type)
    {
        Set<String> keys=new LinkedHashSet<>();for(FarmRunPatch p:patches)if(p.getPatchType()==type)keys.add(p.key());
        startSelected(keys,RunPreset.individualTitle(type),RunPreset.individual(type),false);
    }
    void startCombinedShortcut(FarmRunType type)
    {
        Set<String> keys=new LinkedHashSet<>();for(FarmRunPatch p:patches)if(p.getFarmRunType()==type)keys.add(p.key());
        startSelected(keys,type.getDisplayName(),type==FarmRunType.ALLOTMENT_FLOWER?RunPreset.CROP.reference:RunPreset.TREE.reference,false);
    }
    void startManual(Set<String> keys)
    {startSelected(keys,"Manual Run",List.of(),true);}
    private void startSelected(Set<String> keys,String label,List<String> reference,boolean optimize)
    {
        Set<String> chosen=Set.copyOf(keys);
        Set<FarmRunType> types=EnumSet.noneOf(FarmRunType.class);
        for(FarmRunPatch p:patches)if(chosen.contains(p.key())&&enabled(p))types.add(p.getFarmRunType());
        if(types.isEmpty()) {SwingUtilities.invokeLater(()->panel.emptySelection());return;}
        start(types,chosen,label,reference,optimize);
    }

    private void start(Set<FarmRunType> types,Set<String> keys,String label,List<String> reference,boolean optimize)
    {
        if (types.isEmpty()) return;
        Set<FarmRunType> selected=EnumSet.copyOf(types);
        guideRequested = true;
        final long requested = ++session;
        thread.invokeLater(() -> {
            if (!running || !guideRequested || requested != session) return;
            clearRun();
            run = selected;
            manualKeys=keys;runLabel=label;referenceOrder=reference;optimizeManual=optimize;
            loadStock();
            sampleStorage();
            navigation.begin();
            update();
        });
    }

    void stop()
    {
        guideRequested = false;
        final long requested = ++session;
        thread.invokeLater(() -> { if (requested == session) end(); });
    }

    private void clearRun()
    {
        run = Collections.emptySet();
        hideSeedless=false;
        manualKeys=null;runLabel=null;referenceOrder=null;optimizeManual=false;
        initialPrepChecked=false;preparing=false;entranaOffered=false;visitedStorage=false;resupplyOffered.clear();preparationBank=null;preparationReason="";
        runOrder=null;pinned=null;orderProbeId=null;orderProbeSent=false;orderFrozen=false;orderTicks=0;
        current = null;
        skipped.clear();
        completed.clear();
        progress.reset();
        activeCrop = null;
        activeAction = StepEngine.Action.INSPECT;
        activeLive=false;
        requiredItems = Collections.emptySet();
        recommendedItems = Collections.emptySet();
        lastView = null;
        activeLeg=null;
        if(navigation!=null)navigation.stop();
    }

    private void end()
    {
        clearRun();
        final long ended = session;
        if (running) SwingUtilities.invokeLater(() -> { if (ended == session && !guideRequested) panel.home(); });
    }

    private void reset()
    {
        guideRequested = false;
        if(vaultViews!=null)vaultViews.close();
        ++session;
        lastAccessLevel=-1;
        accessNotes=Collections.emptyMap();
        bank.clear();
        vault.clear();
        stockProfile=null;
        end();
    }

    boolean hideSeedless(){return hideSeedless;}
    void setHideSeedless(boolean value)
    {
        final long id=session;
        thread.invokeLater(()->{
            if(id!=session||!guideRequested||run.isEmpty())return;
            hideSeedless=value;
            lastView=null;
            update();
        });
    }

    void skip(FarmRunPatch displayed)
    {
        thread.invokeLater(() -> {
            if (current != null && current == displayed) skipped.add(current);
            update();
        });
    }

    @Subscribe public void onPluginChanged(PluginChanged event)
    {
        if (!"Shortest Path".equals(event.getPlugin().getName())) return;
        thread.invokeLater(() -> {
            if (!running) return;
            shortestPathAvailable=event.isLoaded();
            navigation.enabled(shortestPathAvailable);
            if(!shortestPathAvailable){orderProbeId=null;orderFrozen=true;}
            if (!run.isEmpty()) update();
        });
    }

    @Subscribe public void onPluginMessage(PluginMessage event)
    {
        if(navigation!=null)navigation.message(event);
        if(!guideRequested||orderProbeId==null||!"shortestpath".equals(event.getNamespace())||
            !"result".equals(event.getName())||event.getData()==null||!orderProbeId.equals(event.getData().get("id")))return;
        orderProbeId=null;orderFrozen=true;
        Object target=event.getData().get("target"),cost=event.getData().get("cost");
        if(runOrder==null||runOrder.isEmpty()||current==null||!runOrder.get(0).patches.contains(current)||
            !Boolean.TRUE.equals(event.getData().get("reached"))||!(target instanceof WorldPoint)||
            !(cost instanceof Number)||((Number)cost).doubleValue()<0)return;
        // Refinement is allowed only while the first cluster is still being worked.
        // The native search compares candidate destinations directly, never mixes its
        // cost units with the fallback spatial proxy.
        runOrder=RunOrder.preferNext(runOrder,(WorldPoint)target);
        pinned=RunOrder.flatten(runOrder);
    }

    private void refineOrder()
    {
        if(preparing||orderFrozen||orderProbeSent||!shortestPathAvailable||runOrder==null||runOrder.isEmpty()||current==null)return;
        if(!runOrder.get(0).patches.contains(current)){orderFrozen=true;return;}
        if(++orderTicks<3 || navigation==null || !navigation.canProbeOrder())return;
        // Do not compete with the initial long-distance route. Probe while the
        // player is at the active site, before committing to the next cluster.
        WorldPoint here=client.getLocalPlayer()==null?null:client.getLocalPlayer().getWorldLocation();
        WorldPoint approach=RunOrder.surface(current);
        if(here==null||approach==null||here.getPlane()!=approach.getPlane()||here.distanceTo(approach)>18)return;
        orderProbeSent=true;
        List<WorldPoint> targets=RunOrder.candidates(runOrder);
        WorldPoint from=runOrder.get(0).exit();
        if(targets.isEmpty()||from==null){orderFrozen=true;return;}
        orderProbeId="farming-helper-order-"+(++orderProbeSequence);
        bus.post(new PluginMessage("shortestpath","query",Map.of("id",orderProbeId,"start",from,"target",targets)));
    }


    @Subscribe public void onProfileChanged(ProfileChanged event) { reset(); }
    @Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) { reset(); }
    @Subscribe public void onGameObjectSpawned(GameObjectSpawned event) { highlight.spawned(event.getGameObject()); }
    @Subscribe public void onGameObjectDespawned(GameObjectDespawned event) { highlight.despawned(event.getGameObject()); }
    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if(event.getGameState()==GameState.LOADING || event.getGameState()==GameState.LOGIN_SCREEN) highlight.clear();
        if (event.getGameState() == GameState.LOGIN_SCREEN ||
            event.getGameState() == GameState.HOPPING) reset();
    }

    @Subscribe public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        loadStock();
        sampleStorage();
        if (!run.isEmpty() && guideRequested)
        {
            if(navigation!=null && client.getLocalPlayer()!=null)navigation.tick(client.getLocalPlayer().getWorldLocation());
            update();
            refineOrder();
        }
        if(vaultViews!=null)vaultViews.tick();
        if(lastAccessLevel!=client.getRealSkillLevel(Skill.FARMING)) updateAccessNotes();
    }

    @Subscribe public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.BANKMAIN || event.getGroupId() == InterfaceID.SEED_VAULT)
            thread.invokeLater(() -> { if (running) sampleStorage(); });
    }

    @Subscribe public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() == InventoryID.BANK.getId() || event.getContainerId() == InventoryID.SEED_VAULT.getId())
            sampleStorage();
        if(vaultViews!=null&&event.getContainerId()==InventoryID.SEED_VAULT.getId())vaultViews.invalidate();
    }

    @Subscribe public void onScriptPreFired(ScriptPreFired event)
    {if(vaultViews!=null)vaultViews.beforeScript(event.getScriptId());}
    @Subscribe public void onScriptPostFired(ScriptPostFired event)
    {if(vaultViews!=null)vaultViews.afterScript(event.getScriptId());}
    @Subscribe public void onWidgetClosed(WidgetClosed event)
    {if(vaultViews!=null&&event.getGroupId()==SeedVaultViews.GROUP)vaultViews.close();}
    @Subscribe public void onMenuOptionClicked(MenuOptionClicked event)
    {if(vaultViews!=null)vaultViews.nativeMenu(event.getParam1());}

    // Null means ordinary vault browsing; an empty set is a live run with no planting supplies.
    Set<Integer> vaultRunItems()
    {
        if(!guideRequested||run.isEmpty())return null;
        Set<Integer> ids=new HashSet<>(requiredItems);ids.addAll(recommendedItems);
        return Set.copyOf(ids);
    }

    long preferenceRevision(){return preferenceRevision;}
    Set<PatchType> vaultAllowed(int level)
    {
        Set<PatchType> allowed=EnumSet.noneOf(PatchType.class);
        for(FarmRunPatch p:patches)if(enabled(p)&&Waypoints.accessIssue(client,p.getPatchType(),p.getLocation(),level,false)==null)
            allowed.add(p.getPatchType());
        return allowed;
    }
    Map<PatchType,Set<Crop>> vaultOverrides(int level)
    {
        Map<PatchType,Set<Crop>> result=new EnumMap<>(PatchType.class);
        for(FarmRunPatch p:patches)if(enabled(p)&&Waypoints.accessIssue(client,p.getPatchType(),p.getLocation(),level,false)==null)
        {
            Crop c=CropCatalog.findByName(p.getPatchType(),override(p));
            if(c!=null&&c.getLevel()<=level)result.computeIfAbsent(p.getPatchType(),t->new LinkedHashSet<>()).add(c);
        }
        return result;
    }
    private void loadStock()
    {
        String profile=config.getRSProfileKey();
        if(Objects.equals(profile,stockProfile))return;
        bank.clear();vault.clear();stockProfile=profile;
        if(profile!=null)
        {
            bank.putAll(StockSnapshot.decode(setting("observedBankV1","")));
            vault.putAll(StockSnapshot.decode(setting("observedVaultV1","")));
        }
    }

    private void sampleStorage()
    {
        if(client.getGameState()!=GameState.LOGGED_IN)return;
        loadStock();
        if(stockProfile==null)return;
        observeStorage(InterfaceID.Bankmain.UNIVERSE, InventoryID.BANK, bank,"observedBankV1");
        observeStorage(InterfaceID.SeedVault.UNIVERSE, InventoryID.SEED_VAULT, vault,"observedVaultV1");
    }

    private void observeStorage(int component, InventoryID id, Map<Integer,Integer> snapshot,String key)
    {
        if (client.getWidget(component) == null || client.getWidget(component).isHidden()) return;
        ItemContainer container = client.getItemContainer(id);
        if (container == null) return;
        Map<Integer,Integer> observed=new HashMap<>();
        addItems(observed,container);
        if(!observed.equals(snapshot))
        {
            snapshot.clear();snapshot.putAll(observed);
            if(Objects.equals(stockProfile,config.getRSProfileKey()))save(key,StockSnapshot.encode(snapshot));
        }
    }

    private static void addItems(Map<Integer,Integer> stock, ItemContainer container)
    {
        if (container == null) return;
        for (Item item : container.getItems())
            if (item.getId() > 0 && item.getQuantity() > 0)
                stock.merge(item.getId(), item.getQuantity(), Integer::sum);
    }

    static boolean actionable(PatchType type, CropState state)
    {
        if(type==PatchType.COMPOST_BIN)return state!=CropState.GROWING;
        return state != CropState.GROWING && state != CropState.FILLING &&
            !(state==CropState.HARVESTABLE && (type==PatchType.REDWOOD || type==PatchType.SPIRIT_TREE));
    }

    static int spiritCapacity(int level)
    {
        if (level >= 99) return Integer.MAX_VALUE;
        if (level >= 96) return 4;
        if (level >= 93) return 3;
        if (level >= 88) return 2;
        return level >= 83 ? 1 : 0;
    }

    private void updateAccessNotes()
    {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        int level = client.getRealSkillLevel(Skill.FARMING);
        lastAccessLevel=level;
        Map<String,String> notes = new HashMap<>();
        for (FarmRunPatch patch : patches)
        {
            String issue = Waypoints.accessIssue(client,patch.getPatchType(),patch.getLocation(),level,false);
            if (issue == null && patch.getPatchType()!=PatchType.COMPOST_BIN && CropCatalog.recommend(patch.getPatchType(),level) == null) issue = "Farming level too low";
            if (issue != null) notes.put(patch.key(),issue);
        }
        accessNotes = Collections.unmodifiableMap(notes);
    }

    private QuestFarmingStateReader.Observation observe(FarmRunPatch p)
    {
        return reader.observeType(p.getPatchType().name(),p.getLocation(),p.getName(),
            p.getPatchType()==PatchType.HESPORI ? null : Waypoints.destination(p));
    }

    private void update()
    {
        if (run.isEmpty() || !guideRequested) return;
        String title = runLabel!=null ? runLabel : run.size() == 1 ? run.iterator().next().getDisplayName() : "Combined run";
        boolean loggedIn = client.getLocalPlayer()!=null && client.getGameState()==GameState.LOGGED_IN;
        int level = client.getRealSkillLevel(Skill.FARMING);
        Map<Integer,Integer> bag=new HashMap<>(), carried=new HashMap<>();
        addItems(bag,client.getItemContainer(InventoryID.INVENTORY));
        Map<Integer,Integer> equipped=new HashMap<>();addItems(equipped,client.getItemContainer(InventoryID.EQUIPMENT));
        carried.putAll(bag);equipped.forEach((id,n)->carried.merge(id,n,Integer::sum));
        Map<Integer,Integer> storage=new HashMap<>(bank);vault.forEach((id,n)->storage.merge(id,n,Integer::sum));
        Map<Integer,Integer> stock=new HashMap<>(bag);storage.forEach((id,n)->stock.merge(id,n,Integer::sum));
        boolean protect=flag("directorProtectTrees",true), pay=flag("directorPayRemoval",true);
        CompostChoice compost=CompostChoice.load(setting("directorCompostChoice",""),flag("directorUseCompost",true));
        int compostId=compost.resolve(stock.containsKey(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED));
        List<FarmRunPatch> ready=new ArrayList<>(), plant=new ArrayList<>(), visible=new ArrayList<>();
        Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations=new LinkedHashMap<>();
        int eligible=0;
        int contract=loggedIn?client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_TYPE):0;
        int spiritSlots=spiritCapacity(level);
        if(loggedIn && run.contains(FarmRunType.SPECIALTY) && level<99)
            for(FarmRunPatch p:patches) if(p.getPatchType()==PatchType.SPIRIT_TREE)
            { CropState state=observe(p).state; if(state!=null && state!=CropState.EMPTY && state!=CropState.DEAD) spiritSlots--; }
        if(loggedIn) for(FarmRunPatch p:patches)
        {
            PatchType type=p.getPatchType();
            if(!ManualSelection.includes(p,run,manualKeys) || !enabled(p) || skipped.contains(p) ||
                Waypoints.accessIssue(client,type,p.getLocation(),level,false)!=null || (type!=PatchType.COMPOST_BIN && CropCatalog.recommend(type,level)==null)) continue;
            eligible++;
            if(pinned!=null && !pinned.contains(p))continue;
            QuestFarmingStateReader.Observation o=observe(p);
            if(o==null) continue;
            observations.put(p,o);
            if(type==PatchType.COMPOST_BIN)
            {
                if(completed.contains(p)){visible.add(p);continue;}
                if(actionable(type,o.state)){ready.add(p);visible.add(p);}
                else if(p==current){completed.add(p);visible.add(p);}
                continue;
            }
            // Leave living white lilies as permanent allotment protection.
            if((type==PatchType.FLOWER && QuestFarmingStateReader.isLivingWhiteLily(o)) ||
                ("Farming Guild".equals(p.getLocation()) && QuestFarmingStateReader.isLivingContractCrop(type.name(),o,contract)))
            {completed.add(p);continue;}
            if(completed.contains(p)) { visible.add(p); continue; }
            if(p==current)
            {
                progress.observe(o);
                boolean wants=protect && activeCrop!=null && StepEngine.supportsProtection(type) && !ProtectionPaymentCatalog.forCrop(activeCrop).isEmpty();
                boolean hasPayment=wants && ProtectionPaymentCatalog.forCrop(activeCrop).stream().allMatch(cost -> bag.getOrDefault(cost.getItemId(),0)>=cost.getQuantity());
                if(progress.finished(type,o,wants,hasPayment)) { completed.add(p); visible.add(p); continue; }
                // Only the active newly planted crop may remain for optional gardener payment.
                if(progress.planted() && o.state==CropState.GROWING && wants && hasPayment)
                { ready.add(p);visible.add(p);continue; }
                if(!actionable(type,o.state)) { completed.add(p);visible.add(p);continue; }
            }
            if(!actionable(type,o.state)) continue;
            if(type==PatchType.SPIRIT_TREE && !StepEngine.keepExisting(type,o.state) && spiritSlots<=0) continue;
            ready.add(p);visible.add(p);
            if(!StepEngine.keepExisting(type,o.state) && o.state!=CropState.DISEASED) { plant.add(p); if(type==PatchType.SPIRIT_TREE) spiritSlots--; }
        }
        // Plan only from initially actionable patches. A manual run keeps its selected
        // subset for the session, and its remaining order is pinned after the first cluster.
        if(loggedIn && pinned==null)
        {
            if(manualKeys!=null || run.size()>1)
            {
                WorldPoint start=client.getLocalPlayer().getWorldLocation();
                runOrder=referenceOrder!=null?RunOrder.preset(ready,start,referenceOrder,optimizeManual):RunOrder.plan(RunOrder.groups(ready,start),start);
                pinned=RunOrder.flatten(runOrder);
            }
            else pinned=new ArrayList<>(ready); // familiar individual shortcut order
        }
        if(pinned!=null)
        {
            Map<FarmRunPatch,Integer> rank=new HashMap<>();
            for(int i=0;i<pinned.size();i++)rank.put(pinned.get(i),i);
            for(List<FarmRunPatch> list:List.of(ready,plant,visible))
            {list.removeIf(p->!rank.containsKey(p));list.sort(Comparator.comparingInt(rank::get));}
        }
        // Keep the active step stable while the player is working, rather than jumping when another patch matures.
        if(current!=null && ready.remove(current)) ready.add(0,current);
        if(current!=null && plant.remove(current)) plant.add(0,current);
        Map<FarmRunPatch,Crop> committed=current!=null && activeCrop!=null && observations.get(current)!=null &&
            observations.get(current).source==QuestFarmingStateReader.Source.LIVE
            ? Map.of(current,activeCrop):Collections.emptyMap();
        Map<FarmRunPatch,Crop> seeds=SeedChoices.allocate(plant,level,stock,this::priority,this::override,committed,hideSeedless);
        int seedlessCount=0;
        if(hideSeedless)
        {
            Set<FarmRunPatch> seedless=new HashSet<>(plant);
            seedless.removeAll(seeds.keySet());
            seedlessCount=seedless.size();
            ready.removeAll(seedless);visible.removeAll(seedless);
        }
        FarmRunPatch next=ready.isEmpty()?null:ready.get(0);
        if(next!=current) { current=next;progress.reset();activeCrop=null; if(current!=null) progress.observe(observations.get(current)); }
        if(runOrder!=null&&!runOrder.isEmpty()&&(current==null||!runOrder.get(0).patches.contains(current)))
        {orderFrozen=true;orderProbeId=null;}
        if(current!=null && !progress.planted()) activeCrop=seeds.get(current);
        Map<Integer,Integer> required=Supplies.guideKit(ready,-1,false,false,
            loggedIn && client.getVarbitValue(VarbitID.FARMING_BLOCKWEEDS)==2,
            loggedIn && client.getVarbitValue(VarbitID.BRUT_FARMING_PLANTING)==3);
        Map<Integer,Integer> recommended=new LinkedHashMap<>();
        for(FarmRunPatch p:ready)
        {
            QuestFarmingStateReader.Observation state=observations.get(p);
            if(state!=null && state.state==CropState.DISEASED)
            {
                if(StepEngine.isTreeType(p.getPatchType()) || p.getPatchType()==PatchType.CORAL)
                    required.put(carried.containsKey(ItemID.FAIRY_ENCHANTED_SECATEURS)?ItemID.FAIRY_ENCHANTED_SECATEURS:ItemID.SECATEURS,1);
                else required.merge(ItemID.PLANT_CURE,1,Integer::sum);
            }
        }
        Map<FarmRunPatch,CompostRun.Plan> binPlans=new HashMap<>();
        CompostRun.addSupplies(required,ready,observations,stock,binPlans);
        int compostCount=0,coins=0;
        for(FarmRunPatch p:ready)
        {
            Crop c=seeds.get(p);PatchType type=p.getPatchType();
            if(c!=null) required.merge(c.getItemId(),c.getQuantity(),Integer::sum);
            if(c!=null && type.usesCompost()) compostCount++;
            if(pay && c!=null && StepEngine.isTreeType(type) && reader.gardenerId(type.name(),p.getLocation(),p.getName())>0) coins+=200;
            if(protect && (c!=null || (p==current && progress.planted() && activeCrop!=null)))
            {
                Crop paymentCrop=c!=null?c:activeCrop;
                if(StepEngine.supportsProtection(type)) for(ProtectionPayment cost:ProtectionPaymentCatalog.forCrop(paymentCrop))
                    recommended.merge(cost.getItemId(),cost.getQuantity(),Integer::sum);
            }
        }
        if(coins>0) recommended.merge(ItemID.COINS,coins,Integer::sum);
        int vines=(int)ready.stream().filter(p->p.getPatchType()==PatchType.GRAPEVINE&&seeds.containsKey(p)).count();
        if(vines>0){required.merge(Supplies.SALTPETRE,vines,Integer::sum);required.put(Supplies.GARDENING_TROWEL,1);}
        if(compostId>=0 && compostCount>0) recommended.merge(compostId,compostId==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED?1:compostCount,Integer::sum);
        if(ready.stream().anyMatch(p->p.getPatchType()==PatchType.HERB || p.getPatchType()==PatchType.CORAL) && !required.containsKey(ItemID.FAIRY_ENCHANTED_SECATEURS)) recommended.put(ItemID.FAIRY_ENCHANTED_SECATEURS,1);
        List<GuideView.ItemRow> items=new ArrayList<>();
        required.forEach((id,n)->items.add(itemRow(id,n,carried,false)));
        recommended.forEach((id,n)->items.add(itemRow(id,n+required.getOrDefault(id,0),carried,true)));
        requiredItems=Set.copyOf(required.keySet());recommendedItems=Set.copyOf(recommended.keySet());
        List<GuideView.Stop> stops=new ArrayList<>();
        String instruction=!loggedIn?"Log in to start this guide.":eligible==0?"No enabled, unlocked patches remain.":completed.isEmpty()?"No ready patches remain in this run. Close and start another when crops mature.":"Run complete. Close the helper to start another run.";
        activeAction=StepEngine.Action.INSPECT;
        activeLive=false;
        if(current!=null)
        {
            PatchType type=current.getPatchType();QuestFarmingStateReader.Observation o=observations.get(current);
            activeLive=o.source==QuestFarmingStateReader.Source.LIVE;
            StepEngine.Action action=StepEngine.action(type,o.state,false,o.protectedByFarmer);
            if(type==PatchType.CELASTRUS && o.rawValue==17) action=StepEngine.Action.REMOVE_TREE;
            if(progress.planted() && o.state==CropState.GROWING) action=StepEngine.Action.PROTECT_TREE;
            if(action==StepEngine.Action.REMOVE_TREE)
                action=StepEngine.refine(action,pay,reader.gardenerId(type.name(),current.getLocation(),current.getName())>0,false);
            if(action==StepEngine.Action.PLANT && o.source==QuestFarmingStateReader.Source.LIVE && reader.hasWeeds(type.name(),o.rawValue)) action=StepEngine.Action.RAKE;
            activeAction=action;
            instruction=type==PatchType.COMPOST_BIN?binPlans.get(current).instruction:StepEngine.instruction(action,activeCrop,type,compostId>=0);
        }
        for(FarmRunPatch p:visible)
        {
            Crop c=seeds.get(p);
            String line=p==current?instruction:completed.contains(p)?"Completed":binPlans.containsKey(p)?binPlans.get(p).instruction:c==null?"Check or harvest existing crop.":"Harvest / clear, then plant "+c.getName()+".";
            stops.add(new GuideView.Stop(p,line,p==current,completed.contains(p)));
        }
        WorldPoint actual=loggedIn?client.getLocalPlayer().getWorldLocation():null;
        WorldPoint player=actual;
        boolean instance=loggedIn && client.getLocalPlayer().getWorldView()!=null && client.getLocalPlayer().getWorldView().isInstance();
        if(instance && client.getLocalPlayer().getLocalLocation()!=null)
            player=WorldPoint.fromLocalInstance(client,client.getLocalPlayer().getLocalLocation());
        boolean aboard=loggedIn && client.getVarbitValue(VarbitID.SAILING_BOARDED_BOAT)!=0;
        boolean rowboat=loggedIn && client.getVarbitValue(SpecialRoutes.ANGLER_ROWBOAT_VARBIT)==1;
        WorldPoint localApproach=current!=null && activeLive && highlight!=null?highlight.approach(current):null;
        String supplyNote="";
        if(runLabel!=null && loggedIn && current!=null)
        {
            int slots=Preparation.slots(required,equipped,this::stackable);
            if(slots>28)supplyNote="Required supplies need at least "+slots+" inventory slots. Take a portion, note harvests and resupply as needed. No inventory limit blocks the guide.";
            if(!initialPrepChecked)
            {
                initialPrepChecked=true;resupplyOffered.add(RunOrder.site(current));
                if(Preparation.storedShortage(required,carried,storage))beginPreparation(true,false,"Gather the listed supplies at the bank / Seed Vault. Continue when ready; missing items never block the guide.");
            }
            if(!preparing&&resupplyOffered.add(RunOrder.site(current))&&activeCrop!=null&&
                bag.getOrDefault(activeCrop.getItemId(),0)<activeCrop.getQuantity()&&storage.getOrDefault(activeCrop.getItemId(),0)>0)
                beginPreparation(false,false,"Resupply: the next crop is short on planting items. Take what you need, or continue without banking.");
            if(!preparing && !entranaOffered && "Entrana".equals(current.getLocation()) && !reader.inPatchRegion(current.getPatchType().name(),current.getLocation(),current.getName(),player))
            {
                entranaOffered=true;
                if(potentialEntranaGear(carried))beginPreparation(false,true,"Entrana restricts weapons and armour. Bank potentially restricted equipment before boarding, then continue. The monks make the final access check.");
            }
        }
        if(seedlessCount>0)
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+seedlessCount+" patch"+(seedlessCount==1?"":"es")+" hidden: insufficient planting stock.";
        if(current==null)preparing=false;
        String prepMessage="";
        RouteLeg leg=SpecialRoutes.leg(current,player,aboard,rowboat,instance,localApproach);
        if(preparing && loggedIn && preparationBank!=null)
        {
            boolean atStorage=storageOpen();visitedStorage|=atStorage;
            // Existing bank or vault is sufficient: never require leaving an open bank for a specific one.
            prepMessage=preparationReason+"\n"+(visitedStorage?"Use the storage you visited; continue when ready.":"Suggested bank: "+preparationBank.name+" (or any accessible bank).");
            if(!Preparation.missing(required,carried)&&!preparationReason.startsWith("Entrana")&&!preparationReason.startsWith("Resupply"))
            {preparing=false;prepMessage="";}
            else
            {
                RouteLeg exit=SpecialRoutes.leg(null,player,aboard,rowboat,instance,null);
                if(visitedStorage)leg=RouteLeg.walk(null,false);
                else if(aboard||instance||SpecialRoutes.insideHespori(player)||SpecialRoutes.insideSeaweed(player)||SpecialRoutes.insideCoral(player))
                    leg=exit.instruction.isEmpty()?RouteLeg.manual("Leave the current instance before banking.",RouteLeg.Interaction.NONE):exit;
                else leg=RouteLeg.approach(preparationBank.point,"Travel to "+preparationBank.name+" or another accessible bank.",RouteLeg.Interaction.NONE);
                if(!leg.instruction.isEmpty())prepMessage+="\n"+leg.instruction;
            }
        }
        if(!preparing&&current!=null&&"Entrana".equals(current.getLocation()))instruction="Entrana: deposit prohibited weapons and armour before travel.\n"+instruction;
        activeLeg=leg;
        if(navigation!=null)navigation.request(actual,leg.point,leg.probe);
        if(!leg.instruction.isEmpty())instruction=leg.instruction+(current==null?"":"\n"+instruction);
        String routeNote=navigation==null?"":navigation.status();
        if(!routeNote.isEmpty())instruction+="\n"+routeNote;
        if(current!=null)
            for(int i=0;i<stops.size();i++) if(stops.get(i).active) stops.set(i,new GuideView.Stop(current,instruction,!preparing,false));
        if(preparing&&!routeNote.isEmpty())prepMessage+="\n"+routeNote;
        cacheIcons(items);
        GuideView view=new GuideView(title,instruction,items,stops,preparing?null:current,prepMessage,supplyNote);
        String signature=view.signature();
        if(!signature.equals(lastView))
        {
            lastView=signature;final long id=session;
            SwingUtilities.invokeLater(()->{ if(running && guideRequested && id==session) panel.guide(view); });
        }
    }

    private boolean stackable(int id)
    {
        if(itemManager==null)return false;
        ItemComposition item=itemManager.getItemComposition(id);return item!=null&&item.isStackable();
    }
    private boolean potentialEntranaGear(Map<Integer,Integer> carried)
    {
        if(itemManager==null)return false;
        for(int id:carried.keySet())
        {
            if(id==ItemID.FAIRY_ENCHANTED_SECATEURS||id==ItemID.SECATEURS)continue;
            var stats=itemManager.getItemStats(id);if(stats==null||stats.getEquipment()==null)continue;
            var e=stats.getEquipment();
            // Heuristic only: no separate Entrana item whitelist. Flag likely combat gear;
            // no automated deposits and no guarantee all items are allowed.
            if(e.getAstab()>0||e.getAslash()>0||e.getAcrush()>0||e.getAmagic()>0||e.getArange()>0||e.getDstab()>0||e.getDslash()>0||e.getDcrush()>0||e.getDmagic()>0||e.getDrange()>0||e.getStr()>0||e.getRstr()>0)return true;
        }
        return false;
    }
    private boolean storageOpen()
    {
        var b=client.getWidget(InterfaceID.Bankmain.UNIVERSE);var v=client.getWidget(InterfaceID.SeedVault.UNIVERSE);
        return b!=null&&!b.isHidden()||v!=null&&!v.isHidden();
    }
    private void beginPreparation(boolean initial,boolean entrana,String reason)
    {
        WorldPoint from=client.getLocalPlayer()==null?null:client.getLocalPlayer().getWorldLocation();
        preparationBank=Preparation.choose(client.getRealSkillLevel(Skill.FARMING),from,initial,entrana);
        preparing=true;visitedStorage=false;preparationReason=reason;
    }
    void continueRun()
    {
        long id=session;thread.invokeLater(()->{if(id!=session||!guideRequested)return;preparing=false;update();});
    }
    void resupply()
    {
        long id=session;thread.invokeLater(()->{if(id!=session||!guideRequested||current==null)return;beginPreparation(false,false,"Resupply at a nearby bank. Continue when ready.");update();});
    }

    private void cacheIcons(List<GuideView.ItemRow> rows)
    {
        if(itemManager==null)return;
        Map<Integer,ImageIcon> next=new HashMap<>(icons);
        for(GuideView.ItemRow row:rows) if(!next.containsKey(row.id))
            next.put(row.id,new ImageIcon(itemManager.getImage(row.id)));
        icons=Map.copyOf(next);
    }
    ImageIcon icon(int itemId) { return icons.get(itemId); }

    private GuideView.ItemRow itemRow(int id,int count,Map<Integer,Integer> carried,boolean recommended)
    {
        Crop crop=CropCatalog.findByItemId(id);
        return new GuideView.ItemRow(id,crop==null?Supplies.name(id):crop.getItemName(),carried.getOrDefault(id,0),count,
            bank.getOrDefault(id,0),vault.getOrDefault(id,0),recommended);
    }

    String override(FarmRunPatch p) { return setting("directorCrop_"+p.key(),""); }
    String accessNote(FarmRunPatch p) { return accessNotes.getOrDefault(p.key(),""); }
    StepEngine.Action activeAction() { return activeAction; }
    boolean activeLive() { return activeLive && guideRequested && !preparing; }
    int patchCount(FarmRunPatch p) { return reader.patchNamesForType(p.getPatchType().name(),p.getLocation()).size(); }
    Set<Integer> requiredItems() { return guideRequested ? requiredItems : Collections.emptySet(); }
    Set<Integer> recommendedItems() { return guideRequested ? recommendedItems : Collections.emptySet(); }
    int gardener(FarmRunPatch p) { return reader.gardenerId(p.getPatchType().name(),p.getLocation(),p.getName()); }

    RouteLeg activeLeg(){return guideRequested?activeLeg:null;}
    int patchVarbit(FarmRunPatch p){return reader.patchVarbit(p.getPatchType().name(),p.getLocation(),p.getName());}
    boolean inPatchRegion(FarmRunPatch p,WorldPoint point){return reader.inPatchRegion(p.getPatchType().name(),p.getLocation(),p.getName(),point);}

    FarmRunPatch activePatch() { return guideRequested && !preparing ? current : null; }

    List<FarmRunPatch> patches() { return Collections.unmodifiableList(patches); }

    boolean enabled(FarmRunPatch patch)
    {
        return flag("patchEnabled_" + patch.key(),PatchLocationSelection.isEnabled(legacy,patch.getLocation()));
    }

    String priority(PatchType type)
    {
        return flag("cropCustom_" + type.name(),false) ? setting("cropPriority_" + type.name(),"") : "";
    }

    boolean flag(String key, boolean fallback) { return Boolean.parseBoolean(setting(key,String.valueOf(fallback))); }
    String setting(String key, String fallback)
    {
        String value = config.getRSProfileKey() == null ? null : config.getRSProfileConfiguration("farming-helper",key);
        return value == null ? fallback : value;
    }
    boolean canSave() { return config.getRSProfileKey() != null; }
    void save(String key, String value)
    {
        if (canSave()) {config.setRSProfileConfiguration("farming-helper",key,value);if(!key.startsWith("observed"))preferenceRevision++;}
    }

	private static BufferedImage createNavigationIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(new Color(74, 45, 24));
		graphics.setStroke(new BasicStroke(2f));
		graphics.drawLine(8, 14, 8, 7);
		graphics.setColor(new Color(60, 190, 80));
		graphics.fillOval(2, 2, 7, 7);
		graphics.fillOval(7, 1, 7, 7);
		graphics.dispose();
		return image;
	}
}
