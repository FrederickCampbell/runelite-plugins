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
import javax.swing.JButton;
import javax.swing.JLabel;

@PluginDescriptor(name = "Farming Helper", description = "Farming guides with stock-aware seed choices",
    tags = {"farming", "seed", "herb", "tree"})
public class FarmingHelperPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private PluginManager plugins;
    private NavigationBridge navigation;
    private SeedVaultViews vaultViews;
    private ActiveRunBank runBank;
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
    private final Set<FarmRunPatch> skipped = new HashSet<>(), completed = new HashSet<>(), preserved = new HashSet<>();
    private final RunProgress runProgress=new RunProgress();
    private final GuideProgress progress = new GuideProgress();
    private final Set<FarmRunPatch> birdhouseTouched=new HashSet<>();
    private final Set<FarmRunPatch> wateredThisRun=new HashSet<>();
    private boolean activeCareOnly;
    private final DiseaseInterrupt diseasePriority=new DiseaseInterrupt();
    private volatile FarmRunPatch diseaseTarget;
    private volatile RouteLeg diseaseLeg;
    private volatile Set<Integer> diseaseItems=Collections.emptySet();
    private GuideView normalView;
    private boolean specialtyStaged,specialtyBirdPhase;
    private final Map<Integer,String> itemNamesCache=new HashMap<>();
    private Crop activeCrop;
    private volatile StepEngine.Action activeAction = StepEngine.Action.INSPECT;
    private volatile boolean activeLive;
    private volatile Set<Integer> requiredItems = Collections.emptySet();
    private volatile long session;
    private volatile boolean guideRequested;
    private final Map<Integer,Integer> bank = new HashMap<>(), vault = new HashMap<>();
    private String stockProfile;
    private boolean bankObservedThisSession,vaultObservedThisSession;
    private volatile Map<Integer,ImageIcon> icons = Collections.emptyMap();
    private Set<FarmRunType> run = Collections.emptySet();
    private Set<String> manualKeys; // physical selections, never persisted as a session
    private String runLabel;
    private String runPolicyKey;
    private volatile RunPolicy runPolicy=RunPolicy.BOTH;
    private volatile boolean runPayRemoval=true, runHasTrees;
    private RunPolicy temporaryCare;
    private Boolean temporaryPay;
    private SeedlessPolicy temporarySeedless;
    private boolean hopping;
    private String runProfile;
    private List<String> referenceOrder;
    private boolean optimizeManual, initialPrepChecked;
    private volatile boolean preparing, hideSeedless, clearSeedless;
    private boolean activeClearOnly, compostAcknowledged;
    private volatile Set<Integer> stepItems=Collections.emptySet();
    private boolean visitedStorage;
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
    private int lastAccessLevel=-1,accessTicks;
    // Quest.getState runs a clientscript; only compute this cache outside script callbacks.
    private Set<String> vaultAccessible=Collections.emptySet();
    private long vaultAccessRevision;
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
        if(itemManager!=null)runBank=new ActiveRunBank(client,plugins,itemManager);
        shortestPathAvailable=plugins.getPlugins().stream().anyMatch(p ->
            "Shortest Path".equals(p.getName()) && plugins.isPluginActive(p));
        navigation.enabled(shortestPathAvailable);
        for (FarmRunPatch group : FarmRunCatalog.patches())
            patches.addAll(PatchNames.expand(group,
                reader.patchNamesForType(group.getPatchType().name(), group.getLocation())));
        patches.addAll(BirdhouseRun.patches());
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
        if(runBank!=null){ActiveRunBank old=runBank;runBank=null;thread.invokeLater(old::close);}
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
        start(types,null,null,null,false,"legacy_"+types.toString());
    }

    void startPreset(RunPreset preset)
    { startSelected(preset.select(patches,this::flag),preset.title,preset.reference,false,"group_"+preset.name()); }
    void startIndividual(PatchType type)
    {
        Set<String> keys=new LinkedHashSet<>();for(FarmRunPatch p:patches)if(p.getPatchType()==type)keys.add(p.key());
        startSelected(keys,RunPreset.individualTitle(type),RunPreset.individual(type),false,"individual_"+type.name());
    }
    void startCombinedShortcut(FarmRunType type)
    {
        Set<String> keys=new LinkedHashSet<>();for(FarmRunPatch p:patches)if(p.getFarmRunType()==type)keys.add(p.key());
        startSelected(keys,type.getDisplayName(),type==FarmRunType.ALLOTMENT_FLOWER?RunPreset.CROP.reference:RunPreset.TREE.reference,false,"shortcut_"+type.name());
    }
    void startManual(Set<String> keys)
    {startSelected(keys,"Manual Run",List.of(),true,"manual");}
    private void startSelected(Set<String> keys,String label,List<String> reference,boolean optimize,String policyKey)
    {
        Set<String> chosen=Set.copyOf(keys);
        Set<FarmRunType> types=EnumSet.noneOf(FarmRunType.class);
        for(FarmRunPatch p:patches)if(chosen.contains(p.key())&&enabled(p))types.add(p.getFarmRunType());
        if(types.isEmpty()) {SwingUtilities.invokeLater(()->panel.emptySelection());return;}
        start(types,chosen,label,reference,optimize,policyKey);
    }

    private void start(Set<FarmRunType> types,Set<String> keys,String label,List<String> reference,boolean optimize,String policyKey)
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
            runPolicyKey=policyKey;
            specialtyStaged=SpecialtyStages.stageBirds(policyKey,run);
            runProfile=config.getRSProfileKey();
            refreshRunPreferences();
            updateAccessNotes();
            runHasTrees=patches.stream().anyMatch(p->ManualSelection.includes(p,run,manualKeys)&&enabled(p)&&accessNote(p).isEmpty()&&StepEngine.isTreeType(p.getPatchType()));
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
        if(runBank!=null)runBank.close();
        hideSeedless=false;clearSeedless=true;activeClearOnly=false;compostAcknowledged=false;stepItems=Collections.emptySet();runPolicyKey=null;runHasTrees=false;
        manualKeys=null;runLabel=null;referenceOrder=null;optimizeManual=false;
        initialPrepChecked=false;preparing=false;visitedStorage=false;preparationBank=null;preparationReason="";
        runOrder=null;pinned=null;orderProbeId=null;orderProbeSent=false;orderFrozen=false;orderTicks=0;
        current = null;
        birdhouseTouched.clear();wateredThisRun.clear();activeCareOnly=false;
        diseasePriority.reset();diseaseTarget=null;diseaseLeg=null;diseaseItems=Collections.emptySet();normalView=null;
        specialtyStaged=false;specialtyBirdPhase=false;
        skipped.clear();
        completed.clear();preserved.clear();runProgress.reset();
        temporaryCare=null;temporaryPay=null;temporarySeedless=null;hopping=false;runProfile=null;
        progress.reset();
        activeCrop = null;
        activeAction = StepEngine.Action.INSPECT;
        activeLive=false;
        requiredItems = Collections.emptySet();
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
        vaultAccessible=Collections.emptySet();vaultAccessRevision++;
        accessNotes=Collections.emptyMap();
        bank.clear();
        vault.clear();
        stockProfile=null;
        bankObservedThisSession=false;vaultObservedThisSession=false;
        end();
    }

    private void refreshRunPreferences()
    {
        // Old per-run saved overrides are intentionally ignored; all other preferences remain intact.
        runPolicy=temporaryCare!=null?temporaryCare:RunPolicy.load("",CompostChoice.load(setting("directorCompostChoice",""),flag("directorUseCompost",true))!=CompostChoice.NONE,flag("directorProtectTrees",true));
        runPayRemoval=temporaryPay!=null?temporaryPay:flag("directorPayRemoval",true);
        SeedlessPolicy choice=temporarySeedless!=null?temporarySeedless:SeedlessPolicy.load(setting("seedlessPolicyV1","GUIDE"));
        hideSeedless=choice.hide;clearSeedless=choice.hide?choice.clear:flag("clearSeedlessPreferenceV1",true);
    }
    RunPolicy runPolicy(){return runPolicy;}
    boolean runPayRemoval(){return runPayRemoval;}
    boolean runHasTrees(){return runHasTrees;}
    void setRunPolicy(RunPolicy policy,Boolean pay)
    {
        long id=session;thread.invokeLater(()->{
            if(id!=session||!guideRequested||runPolicyKey==null)return;
            if(policy!=null)temporaryCare=policy;
            if(pay!=null)temporaryPay=pay;
            refreshRunPreferences();
            lastView=null;update();
        });
    }
    private String observedItemName(int id)
    {if(itemManager==null)return "";return itemNamesCache.computeIfAbsent(id,i->{var item=itemManager.getItemComposition(i);return item==null||item.getNote()!=-1||item.getPlaceholderTemplateId()!=-1?"":item.getName();});}
    boolean hideSeedless(){return hideSeedless;}
    boolean clearSeedless(){return clearSeedless;}
    Set<Integer> activeStepItems(){return !guideRequested?Collections.emptySet():diseaseTarget!=null?diseaseItems:!preparing?stepItems:Collections.emptySet();}
    void setClearSeedless(boolean value)
    {
        long id=session;thread.invokeLater(()->{
            if(id!=session||!guideRequested||run.isEmpty())return;
            if(!hideSeedless)return;
            temporarySeedless=SeedlessPolicy.of(true,value);refreshRunPreferences();
            sampleStorage();lastView=null;update();
        });
    }
    void resetObservedStock()
    {
        long id=session;thread.invokeLater(()->{
            if(id!=session||!guideRequested||run.isEmpty())return;
            loadStock();bank.clear();vault.clear();bankObservedThisSession=false;vaultObservedThisSession=false;
            save("observedBankV1","");save("observedVaultV1","");
            sampleStorage();lastView=null;update();
        });
    }
    void acknowledgeCompost(FarmRunPatch patch)
    {
        long id=session;thread.invokeLater(()->{
            if(id!=session||!guideRequested||current!=patch||activeAction!=StepEngine.Action.COMPOST)return;
            compostAcknowledged=true;lastView=null;update();
        });
    }
    void setHideSeedless(boolean value)
    {
        final long id=session;
        thread.invokeLater(()->{
            if(id!=session||!guideRequested||run.isEmpty())return;
            temporarySeedless=SeedlessPolicy.of(value,clearSeedless);refreshRunPreferences();
            sampleStorage();
            lastView=null;
            update();
        });
    }

    void setSeedlessPolicy(SeedlessPolicy policy)
    {
        long id=session;thread.invokeLater(()->{if(id!=session||!guideRequested||run.isEmpty())return;temporarySeedless=policy;refreshRunPreferences();sampleStorage();lastView=null;update();});
    }
    void skip(FarmRunPatch displayed)
    {
        long id=session;thread.invokeLater(() -> {
            if(id!=session||!guideRequested)return;
            if(diseaseTarget!=null)
            {
                if(displayed==diseaseTarget){diseasePriority.skip(displayed);diseaseTarget=null;lastView=null;}
            }
            else if (current != null && current == displayed) skipped.add(current);
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
        if(diseaseTarget!=null||!guideRequested||orderProbeId==null||!"shortestpath".equals(event.getNamespace())||
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
        if(diseaseTarget!=null||preparing||orderFrozen||orderProbeSent||!shortestPathAvailable||runOrder==null||runOrder.isEmpty()||current==null)return;
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
    @Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) { if(!guideRequested||!Objects.equals(runProfile,config.getRSProfileKey()))reset(); }
    @Subscribe public void onGameObjectSpawned(GameObjectSpawned event) { highlight.spawned(event.getGameObject()); }
    @Subscribe public void onGameObjectDespawned(GameObjectDespawned event) { highlight.despawned(event.getGameObject()); }
    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if(event.getGameState()==GameState.LOADING || event.getGameState()==GameState.LOGIN_SCREEN) highlight.clear();
        if(event.getGameState()==GameState.LOGIN_SCREEN){reset();return;}
        if(event.getGameState()==GameState.HOPPING&&guideRequested)
        {
            // Preserve same-character run decisions, but release scene-local routes/widgets.
            hopping=true;highlight.clear();if(navigation!=null)navigation.stop();
            if(runBank!=null)runBank.close();if(vaultViews!=null)vaultViews.close();
            orderProbeId=null;orderProbeSent=false;activeLive=false;stepItems=Collections.emptySet();lastAccessLevel=-1;
        }
    }

    @Subscribe public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if(hopping){hopping=false;if(!Objects.equals(runProfile,config.getRSProfileKey()))reset();lastView=null;}
        loadStock();
        sampleStorage();
        // Re-evaluate quest, diary, Hunter and Sailing gates even when Farming is unchanged.
        if(lastAccessLevel!=client.getRealSkillLevel(Skill.FARMING)||++accessTicks>=5){accessTicks=0;updateAccessNotes();}
        if (!run.isEmpty() && guideRequested)
        {
            if(navigation!=null && client.getLocalPlayer()!=null)navigation.tick(LocationContext.capture(client).pathStart());
            update();
            refineOrder();
        }
        if(vaultViews!=null)vaultViews.tick();
        updateRunBank(false);

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

    @Subscribe public void onPostClientTick(PostClientTick event)
    {if(vaultViews!=null)vaultViews.postClientTick();}

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
        return requiredItems();
    }

    private boolean birdBankPhase(){return specialtyStaged&&specialtyBirdPhase||run.size()==1&&run.contains(FarmRunType.BIRDHOUSE);}
    private Set<Integer> bankRunItems(Map<Integer,Integer> owned)
    {
        Set<Integer> runItems=vaultRunItems();
        if(runItems==null)return null;
        Set<Integer> ids=new HashSet<>(runItems);
        boolean bird=birdBankPhase();
        boolean diving=!bird&&patches.stream().anyMatch(p->ManualSelection.includes(p,run,manualKeys)&&enabled(p)&&accessNote(p).isEmpty()&&(p.getPatchType()==PatchType.SEAWEED||p.getPatchType()==PatchType.CORAL));
        return CuratedBank.shelf(ids,owned,this::observedItemName,client,config,bird,!bird&&runHasTrees,diving);
    }
    private void updateRunBank(boolean manual)
    {
        if(runBank==null)return;
        boolean enabled=flag("activeRunBank",true);
        if(!enabled||!guideRequested||run.isEmpty()||!runBank.bankOpen())
        {runBank.tick(null,Collections.emptySet(),enabled,birdBankPhase());return;}
        Map<Integer,Integer> owned=runBank.ownedStock();
        Set<Integer> eligible=bankRunItems(owned);
        if(manual)runBank.show(eligible,owned.keySet(),birdBankPhase());
        else runBank.tick(eligible,owned.keySet(),enabled,birdBankPhase());
    }
    void showActiveRunBank(){thread.invokeLater(()->{if(runBank!=null&&guideRequested&&flag("activeRunBank",true)){sampleStorage();update();updateRunBank(true);}});}
    TransportHint currentTransport()
    {
        if(!guideRequested||diseaseTarget!=null||preparing||current==null||navigation==null||client.getLocalPlayer()==null)return null;
        return navigation.nearbyTransport(LocationContext.capture(client).pathStart());
    }
    long preferenceRevision(){return preferenceRevision;}
    long vaultAccessRevision(){return vaultAccessRevision;}
    boolean vaultAccessReady(int level){return lastAccessLevel==level;}
    Set<PatchType> vaultAllowed(int level)
    {
        Set<PatchType> allowed=EnumSet.noneOf(PatchType.class);
        for(FarmRunPatch p:patches)if(enabled(p)&&vaultAccessible.contains(p.key()))
            allowed.add(p.getPatchType());
        return allowed;
    }
    Map<PatchType,Set<Crop>> vaultOverrides(int level)
    {
        Map<PatchType,Set<Crop>> result=new EnumMap<>(PatchType.class);
        for(FarmRunPatch p:patches)if(enabled(p)&&vaultAccessible.contains(p.key()))
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
        bankObservedThisSession=false;vaultObservedThisSession=false;
        if(profile!=null)
        {
            bank.putAll(StockSnapshot.decode(setting("observedBankV1","")));
            vault.putAll(StockSnapshot.decode(setting("observedVaultV1","")));
            // Migrate older cached observations without discarding genuine stock.
            if(itemManager!=null)
            {
                sanitizeSnapshot(bank,"observedBankV1");
                sanitizeSnapshot(vault,"observedVaultV1");
            }
        }
    }

    private void sanitizeSnapshot(Map<Integer,Integer> snapshot,String key)
    {
        int before=snapshot.size();
        PhysicalStock.sanitize(snapshot,itemManager::getItemComposition);
        if(before!=snapshot.size())save(key,StockSnapshot.encode(snapshot));
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
        if(id==InventoryID.BANK)bankObservedThisSession=true;
        if(id==InventoryID.SEED_VAULT)vaultObservedThisSession=true;
        if(!observed.equals(snapshot))
        {
            snapshot.clear();snapshot.putAll(observed);
            if(Objects.equals(stockProfile,config.getRSProfileKey()))save(key,StockSnapshot.encode(snapshot));
        }
    }

    private void addItems(Map<Integer,Integer> stock, ItemContainer container)
    {
        if(itemManager!=null)PhysicalStock.collect(stock,container,itemManager::getItemComposition);
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
        Set<String> accessible=new HashSet<>();
        for (FarmRunPatch patch : patches)
        {
            String issue = Waypoints.accessIssue(client,patch.getPatchType(),patch.getLocation(),level,false);
            if(issue==null)accessible.add(patch.key());
            if (issue == null && patch.getPatchType()!=PatchType.COMPOST_BIN && patch.getPatchType()!=PatchType.BIRDHOUSE && CropCatalog.recommend(patch.getPatchType(),level) == null) issue = "Farming level too low";
            if (issue != null) notes.put(patch.key(),issue);
        }
        if(!vaultAccessible.equals(accessible)){vaultAccessible=Set.copyOf(accessible);vaultAccessRevision++;}
        Map<String,String> old=accessNotes;
        accessNotes = Collections.unmodifiableMap(notes);
        if(!old.equals(notes)&&panel!=null)SwingUtilities.invokeLater(()->{if(running&&panel!=null)panel.accessChanged();});
    }

    private QuestFarmingStateReader.Observation observe(FarmRunPatch p)
    {
        if(p.getPatchType()==PatchType.BIRDHOUSE)return BirdhouseRun.observe(client,config,p,highlight!=null&&highlight.hasLocalPatch(p));
        return reader.observeType(p.getPatchType().name(),p.getLocation(),p.getName(),
            p.getPatchType()==PatchType.HESPORI ? null : Waypoints.destination(p));
    }

    private void update()
    {
        if (run.isEmpty() || !guideRequested || hopping) return;
        refreshRunPreferences();
        String title = runLabel!=null ? runLabel : run.size() == 1 ? run.iterator().next().getDisplayName() : "Combined run";
        boolean loggedIn = client.getLocalPlayer()!=null && client.getGameState()==GameState.LOGGED_IN;
        LocationContext location=LocationContext.capture(client);
        int level = client.getRealSkillLevel(Skill.FARMING);
        Map<Integer,Integer> bag=new HashMap<>(), equipped=new HashMap<>();
        addItems(bag,client.getItemContainer(InventoryID.INVENTORY));
        addItems(equipped,client.getItemContainer(InventoryID.EQUIPMENT));
        Map<Integer,Integer> carried=PhysicalStock.combine(bag,equipped);
        // Run this before normal planning, even during banking or birdhouses. No
        // mutations to the normal guide cursor, progress, or preparation state.
        if(loggedIn&&showDiseaseInterrupt(title,bag,carried))return;
        Map<Integer,Integer> storage=PhysicalStock.combine(bank,vault);
        Map<Integer,Integer> stock=PhysicalStock.combine(bag,storage);
        int wateringCan=CropCare.wateringCan(bag,this::observedItemName);
        boolean protect=runPolicy.protection, pay=runPayRemoval;
        Map<Integer,Integer> aliases=TreePreparation.aliases(stock,this::observedItemName);
        Map<Integer,Integer> allocationStock=TreePreparation.allocationStock(stock,aliases);
        CompostChoice compost=CompostChoice.load(setting("directorCompostChoice",""),flag("directorUseCompost",true));
        if(runPolicy.compost&&compost==CompostChoice.NONE)compost=CompostChoice.BOTTOMLESS;
        int compostId=runPolicy.compost?compost.resolve(stock.containsKey(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED)):-1;
        List<FarmRunPatch> ready=new ArrayList<>(), plant=new ArrayList<>(), visible=new ArrayList<>();
        Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations=new LinkedHashMap<>();
        int contract=loggedIn?client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_TYPE):0;
        int spiritSlots=spiritCapacity(level);
        if(loggedIn && run.contains(FarmRunType.SPECIALTY) && level<99)
            for(FarmRunPatch p:patches) if(p.getPatchType()==PatchType.SPIRIT_TREE)
            { CropState state=observe(p).state; if(state!=null && state!=CropState.EMPTY && state!=CropState.DEAD) spiritSlots--; }
        if(loggedIn) for(FarmRunPatch p:patches)
        {
            PatchType type=p.getPatchType();
            if(!ManualSelection.includes(p,run,manualKeys)||!enabled(p)||!accessNote(p).isEmpty())continue;
            runProgress.include(List.of(p));
            if(!SpecialtyStages.includes(specialtyStaged,specialtyBirdPhase,type)||skipped.contains(p))continue;
            if(pinned!=null && !pinned.contains(p))continue;
            QuestFarmingStateReader.Observation o=observe(p);
            if(o==null) continue;
            observations.put(p,o);
            if(type==PatchType.BIRDHOUSE)
            {
                if(completed.contains(p)){visible.add(p);continue;}
                if(p==current&&o.source==QuestFarmingStateReader.Source.LIVE)
                {
                    if(o.rawValue>=0&&!BirdhouseRun.seeded(o.rawValue))birdhouseTouched.add(p);
                    if(BirdhouseRun.seeded(o.rawValue)&&birdhouseTouched.contains(p)){completed.add(p);visible.add(p);continue;}
                }
                if(o.state!=CropState.GROWING){ready.add(p);visible.add(p);}
                continue;
            }
            if(type==PatchType.COMPOST_BIN)
            {
                if(completed.contains(p)){visible.add(p);continue;}
                if(actionable(type,o.state)){ready.add(p);visible.add(p);}
                else if(p==current){completed.add(p);visible.add(p);}
                continue;
            }
            if(p==current){progress.observe(o);if(activeAction==StepEngine.Action.WATER&&CropCare.watered(type,o))wateredThisRun.add(p);}
            // Preserve lilies, but let a newly planted lily finish watering first.
            if((type==PatchType.FLOWER && QuestFarmingStateReader.isLivingWhiteLily(o) && !(p==current&&progress.planted())) ||
                ("Farming Guild".equals(p.getLocation()) && QuestFarmingStateReader.isLivingContractCrop(type.name(),o,contract)))
            {preserved.add(p);completed.add(p);continue;}
            if(completed.contains(p)) { visible.add(p); continue; }
            if(p==current)
            {
                if(activeCareOnly)
                {
                    // A local care detour must never turn into an ordinary harvest/replant.
                    // The local scanner below re-adds this detour only while the exact target is verified.
                    continue;
                }
                if(o.source==QuestFarmingStateReader.Source.LIVE&&progress.planted()&&CropCare.waterNeeded(type,o,wateringCan)&&!wateredThisRun.contains(p))
                {ready.add(p);visible.add(p);continue;}
                if(hideSeedless&&clearSeedless&&activeClearOnly&&o.source==QuestFarmingStateReader.Source.LIVE&&o.state==CropState.EMPTY)
                {completed.add(p);visible.add(p);continue;}
                boolean wants=protect && activeCrop!=null && StepEngine.supportsProtection(type) && !ProtectionPaymentCatalog.forCrop(activeCrop).isEmpty();
                boolean hasPayment=wants && ProtectionPaymentCatalog.forCrop(activeCrop).stream().allMatch(cost -> bag.getOrDefault(cost.getItemId(),0)>=cost.getQuantity());
                if(progress.finished(type,o,wants,hasPayment)) { completed.add(p); visible.add(p); continue; }
                // Only the active newly planted crop may remain for optional gardener payment.
                if(progress.planted() && o.state==CropState.GROWING && wants && hasPayment)
                { ready.add(p);visible.add(p);continue; }
                if(!actionable(type,o.state))
                {
                    // A remote prediction or already-growing crop is not evidence
                    // of completion. If we have not witnessed planting, simply
                    // defer this stop until a later actionable observation.
                    continue;
                }
            }
            if(!actionable(type,o.state)){continue;}
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
                if(SpecialtyStages.fossilLastRequired(runPolicyKey)&&runOrder.stream().anyMatch(g->"Fossil Island".equals(g.site)))
                {runOrder=SpecialtyStages.fossilLast(runOrder);orderFrozen=true;}
                if(specialtyStaged&&specialtyBirdPhase)orderFrozen=true;
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
        // A selected, local dry crop can be watered without a new planting allocation.
        // Disease is handled independently before the ordinary planner.
        Set<FarmRunPatch> careOnly=new LinkedHashSet<>();
        if(loggedIn&&!preparing&&highlight!=null&&!(specialtyStaged&&specialtyBirdPhase))
        {
            for(FarmRunPatch p:patches)
            {
                PatchType type=p.getPatchType();
                if(skipped.contains(p)||type==PatchType.BIRDHOUSE||type==PatchType.COMPOST_BIN||!reader.inPatchRegion(type.name(),p.getLocation(),p.getName(),location.normalized))continue;
                // Do not let routine watering pull Fossil Island ahead of remaining mainland work.
                // Verified disease interruptions remain independent and can still interrupt.
                if(SpecialtyStages.fossilLastRequired(runPolicyKey)&&SpecialtyStages.fossil(p)&&ready.stream().anyMatch(other->!SpecialtyStages.fossil(other)))continue;
                QuestFarmingStateReader.Observation o=observations.get(p);
                if(o==null)o=observe(p);
                boolean water=ManualSelection.includes(p,run,manualKeys)&&enabled(p)&&!wateredThisRun.contains(p)&&CropCare.waterNeeded(type,o,wateringCan)&&highlight.hasLocalPatch(p);
                if(p==current&&activeAction==StepEngine.Action.WATER&&CropCare.watered(type,o))wateredThisRun.add(p);
                if(!water||wateredThisRun.contains(p))continue;
                if(ready.contains(p)&&!(p==current&&activeCareOnly))continue;
                observations.put(p,o);plant.remove(p);ready.remove(p);visible.remove(p);
                careOnly.add(p);
            }
            ready.addAll(0,careOnly);
            for(FarmRunPatch p:careOnly)if(!visible.contains(p))visible.add(0,p);
        }
        // Ordinary watering does not interrupt work already underway.
        if(current!=null&&(careOnly.isEmpty()||observations.get(current)!=null&&observations.get(current).source==QuestFarmingStateReader.Source.LIVE)&&ready.remove(current))ready.add(0,current);
        if(current!=null && plant.remove(current)) plant.add(0,current);
        Map<FarmRunPatch,Crop> committed=current!=null && activeCrop!=null && observations.get(current)!=null &&
            observations.get(current).source==QuestFarmingStateReader.Source.LIVE
            ? Map.of(current,activeCrop):Collections.emptyMap();
        Map<FarmRunPatch,Crop> seeds=SeedChoices.allocate(plant,level,allocationStock,this::priority,this::override,committed,hideSeedless);
        int seedlessCount=0;
        Set<FarmRunPatch> seedless=new HashSet<>();
        Set<FarmRunPatch> clearOnly=new HashSet<>();
        if(hideSeedless)
        {
            seedless.addAll(plant);
            seedless.removeAll(seeds.keySet());
            if(clearSeedless)
            {
                for(FarmRunPatch p:seedless)if(ClearOnly.eligible(observations.get(p).state))clearOnly.add(p);
                seedless.removeAll(clearOnly);
            }
            seedlessCount=seedless.size();
            ready.removeAll(seedless);visible.removeAll(seedless);
        }
        if(loggedIn)runProgress.update(p->SpecialtyStages.includes(specialtyStaged,specialtyBirdPhase,p.getPatchType()),ready,completed,preserved,skipped,seedless);
        // Transition only after the remaining farming/seaweed work is exhausted.
        // A fresh pinned order is necessary because birdhouses were deliberately absent.
        if(loggedIn&&specialtyStaged&&!specialtyBirdPhase&&ready.isEmpty()&&run.contains(FarmRunType.BIRDHOUSE))
        {
            specialtyBirdPhase=true;current=null;activeCrop=null;progress.reset();activeCareOnly=false;activeClearOnly=false;
            preparing=false;visitedStorage=false;initialPrepChecked=true;runOrder=null;pinned=null;orderProbeId=null;orderFrozen=true;
            update();return;
        }
        FarmRunPatch next=ready.isEmpty()?null:ready.get(0);
        if(next!=current) { current=next;progress.reset();activeCrop=null;compostAcknowledged=false; if(current!=null) progress.observe(observations.get(current)); }
        activeClearOnly=clearOnly.contains(current);
        activeCareOnly=careOnly.contains(current);
        if(!activeCareOnly&&runOrder!=null&&!runOrder.isEmpty()&&(current==null||!runOrder.get(0).patches.contains(current)))
        {orderFrozen=true;orderProbeId=null;}
        if(current!=null && !progress.planted()) activeCrop=seeds.get(current);
        Map<Integer,Integer> required=Supplies.guideKit(ready.stream().filter(p->p.getPatchType()!=PatchType.BIRDHOUSE&&!careOnly.contains(p)).collect(java.util.stream.Collectors.toList()),-1,false,false,
            loggedIn && client.getVarbitValue(VarbitID.FARMING_BLOCKWEEDS)==2,
            loggedIn && client.getVarbitValue(VarbitID.BRUT_FARMING_PLANTING)==3);
        if(!clearOnly.isEmpty()&&seeds.isEmpty()) {required.remove(ItemID.DIBBER);required.remove(ItemID.RAKE);}
        for(FarmRunPatch p:ready)
        {
            QuestFarmingStateReader.Observation state=observations.get(p);
            if(state!=null && state.state==CropState.DISEASED)
            {
                if(CropCare.prunes(p.getPatchType()))
                    required.put(carried.containsKey(ItemID.FAIRY_ENCHANTED_SECATEURS)?ItemID.FAIRY_ENCHANTED_SECATEURS:ItemID.SECATEURS,1);
                else required.merge(ItemID.PLANT_CURE,1,Integer::sum);
            }
        }
        Map<FarmRunPatch,CompostRun.Plan> binPlans=new HashMap<>();
        CompostRun.addSupplies(required,ready,observations,stock,binPlans);
        int compostCount=0,coins=0,axeId=0;boolean needsAxe=false;
        for(FarmRunPatch p:ready)
        {
            Crop c=seeds.get(p);PatchType type=p.getPatchType();
            if(c!=null) required.merge(c.getItemId(),c.getQuantity(),Integer::sum);
            if(c!=null && type.usesCompost()) compostCount++;
            if((c!=null||clearOnly.contains(p))&&StepEngine.isTreeType(type)&&type!=PatchType.CRYSTAL_TREE)
            {
                var o=observations.get(p);
                if(o!=null&&(o.state==null||o.state==CropState.UNCHECKED||o.state==CropState.HARVESTABLE))
                {if(pay&&reader.gardenerId(type.name(),p.getLocation(),p.getName())>0)coins+=200;else needsAxe=true;}
            }
            if(protect && (c!=null || (p==current && progress.planted() && activeCrop!=null)))
            {
                Crop paymentCrop=c!=null?c:activeCrop;
                if(StepEngine.supportsProtection(type)) for(ProtectionPayment cost:ProtectionPaymentCatalog.forCrop(paymentCrop))
                    required.merge(cost.getItemId(),cost.getQuantity(),Integer::sum);
            }
        }
        if(coins>0) required.merge(ItemID.COINS,coins,Integer::sum);
        if(needsAxe){Map<Integer,Integer> axeStock=new HashMap<>(stock);equipped.forEach((id,n)->axeStock.merge(id,n,Integer::sum));int axe=TreeAxes.best(axeStock,client.getRealSkillLevel(Skill.WOODCUTTING),this::observedItemName);axeId=axe>0?axe:1351;required.put(axeId,1);}
        Map<Integer,Integer> prepSeeds=TreePreparation.preparation(seeds,stock,aliases);
        if(!prepSeeds.isEmpty())
        {
            prepSeeds.forEach((id,n)->required.merge(id,n,Integer::sum));
            required.put(TreePreparation.FILLED_POT,prepSeeds.values().stream().mapToInt(Integer::intValue).sum());
            required.merge(Supplies.GARDENING_TROWEL,1,Math::max);
            int can=stock.keySet().stream().filter(id->observedItemName(id).matches("(?i)watering can\\([1-8]\\)|Gricoller.s can")).findFirst().orElse(5333);
            required.merge(can,1,Math::max);
        }
        int vines=(int)ready.stream().filter(p->p.getPatchType()==PatchType.GRAPEVINE&&seeds.containsKey(p)).count();
        if(vines>0){required.merge(Supplies.SALTPETRE,vines,Integer::sum);required.put(Supplies.GARDENING_TROWEL,1);}
        if(compostId>=0 && compostCount>0) required.merge(compostId,compostId==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED?1:compostCount,Integer::sum);
        if(ready.stream().anyMatch(p->p.getPatchType()==PatchType.CORAL))
        {
            Map<Integer,Integer> diveStock=new HashMap<>(bank);carried.forEach((id,n)->diveStock.merge(id,n,Integer::sum));
            DivingKit.require(required,diveStock,carried);
        }
        Map<Integer,Integer> farmingRequirements=new LinkedHashMap<>(required);
        Map<Integer,Integer> birdStock=specialtyStaged&&specialtyBirdPhase?SpecialtyStages.birdStock(bag,bank):stock;
        Map<FarmRunPatch,String> birdhousePlans=BirdhouseRun.supplies(ready,observations,birdStock,required,client.getRealSkillLevel(Skill.HUNTER),client.getRealSkillLevel(Skill.CRAFTING));
        List<GuideView.ItemRow> items=new ArrayList<>();
        Map<Integer,Integer> pendingSaplings=new HashMap<>();
        prepSeeds.forEach((raw,n)->{Integer sapling=aliases.get(raw);if(sapling!=null)pendingSaplings.merge(sapling,n,Integer::sum);});
        required.forEach((id,n)->{
            GuideView.ItemRow row=itemRow(id,n,carried,false);
            int pending=pendingSaplings.getOrDefault(id,0);
            if(pending>0)row=new GuideView.ItemRow(row.id,row.name+" (prepare "+pending+")",row.owned,row.needed,row.bank,row.vault,row.recommended);
            items.add(row);
        });
        requiredItems=Set.copyOf(required.keySet());
        List<GuideView.Stop> stops=new ArrayList<>();
        GuideView.Stats summary=runProgress.stats();
        String instruction=!loggedIn?"Log in to start this guide.":summary.total==0?"No enabled, unlocked patches.":summary.deferred>0||summary.seedless>0?"No ready patches remain. Review deferred and missing-supply counts below.":"Run finished. Close the helper to start another run.";
        activeAction=StepEngine.Action.INSPECT;
        activeLive=false;
        Set<Integer> localStepItems=Collections.emptySet();
        if(current!=null)
        {
            PatchType type=current.getPatchType();QuestFarmingStateReader.Observation o=observations.get(current);
            activeLive=o.source==QuestFarmingStateReader.Source.LIVE;
            StepEngine.Action action=StepEngine.action(type,o.state,false,o.protectedByFarmer);
            if(type==PatchType.CELASTRUS && o.rawValue==17) action=StepEngine.Action.REMOVE_TREE;
            if(progress.planted() && o.state==CropState.GROWING) action=StepEngine.Action.PROTECT_TREE;
            if((progress.planted()||activeCareOnly)&&!wateredThisRun.contains(current)&&CropCare.waterNeeded(type,o,wateringCan))action=StepEngine.Action.WATER;
            if(activeCareOnly&&o.state==CropState.DISEASED)action=StepEngine.Action.CURE;
            if(action==StepEngine.Action.REMOVE_TREE)
                action=StepEngine.refine(action,pay,reader.gardenerId(type.name(),current.getLocation(),current.getName())>0,false);
            if(action==StepEngine.Action.PLANT && o.source==QuestFarmingStateReader.Source.LIVE && reader.hasWeeds(type.name(),o.rawValue)) action=StepEngine.Action.RAKE;
            if(action==StepEngine.Action.PLANT&&activeLive&&activeCrop!=null&&compostId>=0&&type.usesCompost()&&!activeClearOnly&&!compostAcknowledged)
            {
                int minimum=compostId==ItemID.BUCKET_ULTRACOMPOST?3:compostId==ItemID.BUCKET_SUPERCOMPOST?2:1;
                if(reader.compostRecorded(type.name(),current.getLocation(),current.getName(),minimum))compostAcknowledged=true;
                else action=StepEngine.Action.COMPOST;
            }
            activeAction=action;
            instruction=type==PatchType.BIRDHOUSE?birdhousePlans.get(current):type==PatchType.COMPOST_BIN?binPlans.get(current).instruction:StepEngine.instruction(action,activeCrop,type,false);
            if(activeClearOnly)instruction=ClearOnly.instruction(action,type);
            if(activeCareOnly&&action==StepEngine.Action.CURE)instruction="Nearby diseased crop — "+instruction+" Then resume the run without replanting.";
            if(action==StepEngine.Action.CURE&&!CropCare.prunes(type)&&bag.getOrDefault(ItemID.PLANT_CURE,0)==0)instruction+=" Obtain plant cure from a nearby farmer or Farming shop, if available; skip if you cannot cure it.";
            if(action==StepEngine.Action.WATER)instruction="Water this crop with the watering can you are carrying.";
            if(action==StepEngine.Action.COMPOST)instruction="Apply the selected compost to this patch. The next step is planting; use Compost applied / skip if the tracker cannot confirm it.";
            if(activeLive)
            {
                if(type==PatchType.COMPOST_BIN)localStepItems=CompostRun.stepItems(current,o,binPlans.get(current),bag);
                else if(type==PatchType.BIRDHOUSE)localStepItems=BirdhouseRun.stepItems(current,o,birdStock,bag,farmingRequirements,client.getRealSkillLevel(Skill.HUNTER),client.getRealSkillLevel(Skill.CRAFTING));
                else if(action==StepEngine.Action.WATER)localStepItems=Set.of(wateringCan);
                else localStepItems=StepItems.select(action,type,activeCrop,compostId,axeId,bag);
            }
            if(type==PatchType.BIRDHOUSE){activeAction=StepEngine.Action.INSPECT;instruction=current.getName()+": "+instruction;}
        }
        for(FarmRunPatch p:visible)
        {
            Crop c=seeds.get(p);
            String line=p==current?instruction:careOnly.contains(p)?(observations.get(p).state==CropState.DISEASED?"Nearby disease — cure only.":"Water the growing crop."):completed.contains(p)?(preserved.contains(p)?"Preserved":"Completed"):birdhousePlans.containsKey(p)?birdhousePlans.get(p):binPlans.containsKey(p)?binPlans.get(p).instruction:clearOnly.contains(p)?"Check health / harvest and clear; no replacement.":c==null?"Check or harvest existing crop.":"Harvest / clear, then plant "+c.getName()+".";
            stops.add(new GuideView.Stop(p,line,p==current,completed.contains(p)&&!careOnly.contains(p)));
        }
        // Show the selected birdhouse leg at the bottom without adding its supplies to the farming phase.
        if(loggedIn&&specialtyStaged&&!specialtyBirdPhase&&run.contains(FarmRunType.BIRDHOUSE))
        {
            List<FarmRunPatch> birds=new ArrayList<>();
            for(FarmRunPatch p:patches)if(p.getPatchType()==PatchType.BIRDHOUSE&&ManualSelection.includes(p,run,manualKeys)&&enabled(p)&&!skipped.contains(p)&&Waypoints.accessIssue(client,p.getPatchType(),p.getLocation(),level,false)==null)birds.add(p);
            birds.sort(Comparator.comparingInt(BirdhouseRun::order));
            for(FarmRunPatch p:birds)stops.add(new GuideView.Stop(p,"Final birdhouse stage — readiness and supplies checked after farming.",false,false));
        }
        WorldPoint player=loggedIn?location.normalized:null;
        boolean instance=loggedIn&&location.instance;
        boolean aboard=loggedIn && client.getVarbitValue(VarbitID.SAILING_BOARDED_BOAT)!=0;
        boolean rowboat=loggedIn && client.getVarbitValue(SpecialRoutes.ANGLER_ROWBOAT_VARBIT)==1;
        WorldPoint localApproach=current!=null && activeLive && highlight!=null?highlight.approach(current):null;
        String supplyNote=prepSeeds.isEmpty()?"":"Prepare "+prepSeeds.values().stream().mapToInt(Integer::intValue).sum()+" sapling(s): fill empty pots with soil if needed, plant seeds, water, then wait for growth.\n"+TreePreparation.evidence(prepSeeds,bag,bank,vault,this::observedItemName,bankObservedThisSession,vaultObservedThisSession);
        if(hideSeedless&&!birdBankPhase()&&(!bankObservedThisSession||!vaultObservedThisSession))
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Storage may be out of date. Open "+(!bankObservedThisSession&&!vaultObservedThisSession?"the bank and Seed Vault":!bankObservedThisSession?"the bank":"the Seed Vault")+" to verify stock this session.";
        if(specialtyStaged&&!specialtyBirdPhase&&run.contains(FarmRunType.BIRDHOUSE))
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Birdhouses are last. Bring their supplies or use Resupply when you choose.";
        if(specialtyStaged&&specialtyBirdPhase&&current!=null)
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Birdhouse stage: check logs or houses, hammer, chisel, bait and clockworks. Resupply is optional.";
        if(runLabel!=null && loggedIn && current!=null)
        {
            int slots=Preparation.slots(required,equipped,this::stackable);
            if(slots>28)supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Required supplies need at least "+slots+" inventory slots. Take a portion, note harvests and resupply as needed. No inventory limit blocks the guide.";
            if(!initialPrepChecked)
            {
                initialPrepChecked=true;
                if(!prepSeeds.isEmpty()||Preparation.storedShortage(required,carried,storage))beginPreparation(true,"Gather the listed supplies at the bank / Seed Vault. Start when ready; missing items never block the guide.");
            }
        }
        if(seedlessCount>0)
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+seedlessCount+" patch"+(seedlessCount==1?"":"es")+" hidden: insufficient planting stock.";
        if(!clearOnly.isEmpty())supplyNote+=(supplyNote.isEmpty()?"":"\n")+clearOnly.size()+" occupied seedless patch(es) retained for clearing.";
        if(current==null)preparing=false;
        // All processed patches remain visible in the completed history across stages.
        Set<FarmRunPatch> listed=new HashSet<>();for(GuideView.Stop stop:stops)listed.add(stop.patch);
        for(FarmRunPatch p:patches)if(completed.contains(p)&&!listed.contains(p))stops.add(new GuideView.Stop(p,preserved.contains(p)?"Preserved":"Completed",false,true));
        String prepMessage="";
        RouteLeg leg=SpecialRoutes.leg(current,player,aboard,rowboat,instance,localApproach);
        if(preparing && loggedIn && preparationBank!=null)
        {
            boolean atStorage=storageOpen();visitedStorage|=atStorage;
            // Existing bank or vault is sufficient: never require leaving an open bank for a specific one.
            prepMessage=preparationReason+"\n"+(visitedStorage?"Use the storage you visited; continue when ready.":"Suggested bank: "+preparationBank.name+" (or any accessible bank).");
            // Preparation is entered only at run start or by the Resupply button.
            // Keep the explicit Start/Continue action available even when supplies are ready.
            {
                RouteLeg exit=SpecialRoutes.leg(null,player,aboard,rowboat,instance,null);
                if(visitedStorage)leg=RouteLeg.walk(null,false);
                else if(aboard||instance&&!location.inHouse()||SpecialRoutes.insideHespori(player)||SpecialRoutes.insideSeaweed(player)||SpecialRoutes.insideCoral(player))
                    leg=exit.instruction.isEmpty()?RouteLeg.manual("Leave the current instance before banking.",RouteLeg.Interaction.NONE):exit;
                else leg=RouteLeg.approach(preparationBank.point,"Travel to "+preparationBank.name+" or another accessible bank.",RouteLeg.Interaction.NONE);
                if(!leg.instruction.isEmpty())prepMessage+="\n"+leg.instruction;
            }
        }
        if(!preparing&&current!=null&&current.getPatchType()==PatchType.CORAL)
            instruction="Wear the Medallion of the deep OR fishbowl helmet and diving apparatus before entering.\n"+instruction;
        if(!preparing&&current!=null&&"Entrana".equals(current.getLocation()))instruction="Entrana: deposit prohibited weapons and armour before travel.\n"+instruction;
        activeLeg=leg;
        WorldPoint actionPoint=localApproach!=null?localApproach:current==null?null:Waypoints.destination(current);
        boolean atAction=player!=null&&actionPoint!=null&&player.getPlane()==actionPoint.getPlane()&&player.distanceTo(actionPoint)<=5;
        stepItems=!preparing&&leg.interaction==RouteLeg.Interaction.NONE&&activeLive&&atAction?localStepItems:Collections.emptySet();
        if(navigation!=null)navigation.request(loggedIn?location.pathStart():null,leg.point,leg.probe);
        if(location.inHouse()&&!shortestPathAvailable&&current!=null&&!preparing)
            instruction="Use an available house teleport toward the next patch, or leave your house.\n"+instruction;
        if(!leg.instruction.isEmpty())instruction=leg.instruction+(current==null?"":"\n"+instruction);
        TransportHint transport=currentTransport();
        if(transport!=null)instruction=transport.instruction()+"\n"+instruction;
        String routeNote=navigation==null?"":navigation.status();
        if(!routeNote.isEmpty())instruction+="\n"+routeNote;
        if(current!=null)
            for(int i=0;i<stops.size();i++) if(stops.get(i).active) stops.set(i,new GuideView.Stop(current,instruction,!preparing,false));
        if(preparing&&!routeNote.isEmpty())prepMessage+="\n"+routeNote;
        cacheIcons(items);
        GuideView view=new GuideView(title,instruction,items,stops,preparing?null:current,prepMessage,supplyNote,hideSeedless,clearSeedless,!preparing&&activeAction==StepEngine.Action.COMPOST,runProgress.stats());
        normalView=view;
        publishView(view);
    }

    private void publishView(GuideView view)
    {
        String signature=view.signature();
        if(!signature.equals(lastView))
        {
            lastView=signature;final long id=session;
            SwingUtilities.invokeLater(()->{ if(running && guideRequested && id==session) panel.guide(view,id); });
        }
    }

    private boolean showDiseaseInterrupt(String title,Map<Integer,Integer> bag,Map<Integer,Integer> carried)
    {
        if(highlight==null)return false;
        LocationContext location=LocationContext.capture(client);
        WorldPoint here=location.actual;
        Map<FarmRunPatch,QuestFarmingStateReader.Observation> verified=new LinkedHashMap<>();
        Map<FarmRunPatch,WorldPoint> approaches=new HashMap<>();
        for(FarmRunPatch p:patches)
        {
            PatchType type=p.getPatchType();
            if(type==PatchType.BIRDHOUSE||type==PatchType.COMPOST_BIN||!reader.inPatchRegion(type.name(),p.getLocation(),p.getName(),location.normalized))continue;
            QuestFarmingStateReader.Observation o=observe(p);
            if(o==null||o.source!=QuestFarmingStateReader.Source.LIVE||o.state!=CropState.DISEASED||!highlight.hasVerifiedDisease(p))continue;
            verified.put(p,o);approaches.put(p,highlight.approach(p));
        }
        FarmRunPatch previous=diseaseTarget;
        FarmRunPatch target=diseasePriority.select(verified.keySet(),p->{WorldPoint a=approaches.get(p);return a==null?Integer.MAX_VALUE:here.distanceTo(a);});
        if(target==null)
        {
            if(previous!=null)lastView=null;
            diseaseTarget=null;diseaseLeg=null;diseaseItems=Collections.emptySet();return false;
        }
        if(previous!=target){orderProbeId=null;orderProbeSent=false;lastView=null;}
        // Record the disease transition only when the interrupted patch itself is
        // the diseased one, so its existing completion tracker can see recovery.
        if(current==target)progress.observe(verified.get(target));
        PatchType type=target.getPatchType();
        int cure=CropCare.prunes(type)?(carried.getOrDefault(ItemID.FAIRY_ENCHANTED_SECATEURS,0)>0?ItemID.FAIRY_ENCHANTED_SECATEURS:ItemID.SECATEURS):ItemID.PLANT_CURE;
        diseaseItems=Set.of(cure);
        WorldPoint approach=approaches.get(target);
        diseaseLeg=RouteLeg.walk(approach,false);
        diseaseTarget=target;
        if(navigation!=null)navigation.request(location.pathStart(),approach,false);
        String instruction=CropCare.prunes(type)?"Prune the diseased crop with secateurs.":"Use plant cure on the diseased crop.";
        if(carried.getOrDefault(cure,0)==0)instruction+=CropCare.prunes(type)?" Obtain secateurs or skip this cure.":" Obtain plant cure from a nearby farmer or shop if available, or skip this cure.";
        String resume=preparing?"Preparation / resupply":current==null?"The remaining run":current.getDisplayName();
        List<GuideView.ItemRow> items=new ArrayList<>();items.add(itemRow(cure,1,carried,false));
        List<GuideView.Stop> stops=new ArrayList<>();stops.add(new GuideView.Stop(target,instruction,false,false));
        stops.set(0,new GuideView.Stop(target,instruction,true,false));
        if(normalView!=null)for(GuideView.Stop stop:normalView.stops)if(stop.patch!=target)stops.add(new GuideView.Stop(stop.patch,stop.active?"Paused: "+stop.instruction:stop.instruction,false,stop.done));
        cacheIcons(items);
        publishView(new GuideView(title,"Disease priority",items,stops,target,"","Disease detected — current guide paused. Resume: "+resume+". Cure only; no harvest or replant.",hideSeedless,clearSeedless,false,normalView==null?GuideView.Stats.UNKNOWN:normalView.stats));
        return true;
    }

    private boolean stackable(int id)
    {
        if(itemManager==null)return false;
        ItemComposition item=itemManager.getItemComposition(id);return item!=null&&item.isStackable();
    }
    private boolean storageOpen()
    {
        var b=client.getWidget(InterfaceID.Bankmain.UNIVERSE);var v=client.getWidget(InterfaceID.SeedVault.UNIVERSE);
        return b!=null&&!b.isHidden()||v!=null&&!v.isHidden();
    }
    private void beginPreparation(boolean initial,String reason)
    {
        WorldPoint from=client.getLocalPlayer()==null?null:client.getLocalPlayer().getWorldLocation();
        preparationBank=Preparation.choose(client.getRealSkillLevel(Skill.FARMING),from,initial,false);
        preparing=true;visitedStorage=false;preparationReason=reason;
    }
    void continueRun()
    {
        long id=session;thread.invokeLater(()->{if(id!=session||!guideRequested)return;preparing=false;update();});
    }
    void resupply()
    {
        long id=session;thread.invokeLater(()->{if(id!=session||!guideRequested||diseaseTarget!=null||current==null)return;beginPreparation(false,"Resupply at a nearby bank. Continue when ready.");if(specialtyStaged&&specialtyBirdPhase)preparationBank=Preparation.FOSSIL_BANK;update();});
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
    /** Native cached, asynchronous item sprites; no custom downloads or client-thread blocking. */
    void decorateSprite(JButton button,int itemId)
    {
        if(itemManager==null||itemId<=0)return;
        var image=itemManager.getImage(itemId);if(image!=null)image.addTo(button);
    }
    void decorateSprite(JLabel label,int itemId)
    {
        if(itemManager==null||itemId<=0)return;
        var image=itemManager.getImage(itemId);if(image!=null)image.addTo(label);
    }

    private GuideView.ItemRow itemRow(int id,int count,Map<Integer,Integer> carried,boolean recommended)
    {
        Crop crop=CropCatalog.findByItemId(id);
        String name=crop==null?Supplies.name(id):crop.getItemName();
        if(name.startsWith("Item #")){String actual=observedItemName(id);if(!actual.isEmpty())name=actual;}
        return new GuideView.ItemRow(id,name,carried.getOrDefault(id,0),count,
            bank.getOrDefault(id,0),vault.getOrDefault(id,0),recommended);
    }

    String override(FarmRunPatch p) { return setting("directorCrop_"+p.key(),""); }
    String accessNote(FarmRunPatch p) { return accessNotes.getOrDefault(p.key(),""); }
    StepEngine.Action activeAction() { return diseaseTarget!=null?StepEngine.Action.CURE:activeAction; }
    boolean activeLive() { return guideRequested && (diseaseTarget!=null||activeLive&&!preparing); }
    int patchCount(FarmRunPatch p) { return reader.patchNamesForType(p.getPatchType().name(),p.getLocation()).size(); }
    Set<Integer> requiredItems() { return !guideRequested?Collections.emptySet():diseaseTarget!=null?diseaseItems:requiredItems; }
    Set<Integer> recommendedItems() { return Collections.emptySet(); } // compatibility: all live guide supplies are required
    int gardener(FarmRunPatch p) { return reader.gardenerId(p.getPatchType().name(),p.getLocation(),p.getName()); }

    RouteLeg activeLeg(){return !guideRequested?null:diseaseTarget!=null?diseaseLeg:activeLeg;}
    int patchVarbit(FarmRunPatch p){return reader.patchVarbit(p.getPatchType().name(),p.getLocation(),p.getName());}
    boolean inPatchRegion(FarmRunPatch p,WorldPoint point){return reader.inPatchRegion(p.getPatchType().name(),p.getLocation(),p.getName(),point);}

    FarmRunPatch activePatch() { return !guideRequested?null:diseaseTarget!=null?diseaseTarget:!preparing?current:null; }
    boolean diseaseInterruptActive(){return diseaseTarget!=null&&guideRequested;}

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
        String value = config==null||config.getRSProfileKey() == null ? null : config.getRSProfileConfiguration("farming-helper",key);
        return value == null ? fallback : value;
    }
    boolean integrationActive(String name){return plugins.getPlugins().stream().anyMatch(p->name.equals(p.getName())&&plugins.isPluginActive(p));}
    boolean canSave() { return config!=null&&config.getRSProfileKey() != null; }
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
