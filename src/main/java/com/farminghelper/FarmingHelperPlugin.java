package com.farminghelper;

import com.google.inject.Provides;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;
import java.awt.*;
import net.runelite.client.util.ImageUtil;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.gameval.InventoryID;
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
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.game.ItemManager;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;

@PluginDependency(BankTagsPlugin.class)
@PluginDescriptor(name = "Farming Helper", description = "Quest Helper for your farm runs! Includes Shortest Path integration.",
    tags = {"farming", "seed", "herb", "tree"})
public class FarmingHelperPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private PluginManager plugins;
    private NavigationBridge navigation;
    private SeedVaultViews vaultViews;
    @Inject private BankTagsService bankTags;
    @Inject private TagManager tagManager;
    @Inject private BankSearch bankSearch;
    private ActiveRunBank runBank;
    private final AtomicLong preferenceRevision=new AtomicLong();
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

    private final List<FarmRunPatch> patches = new CopyOnWriteArrayList<>();
    private final RunProgress runProgress=new RunProgress();
    private GuideProgress progress = new GuideProgress();
    // Use the same transition tracker for selected plots worked ahead of the cursor.
    private final Map<FarmRunPatch,GuideProgress> aheadProgress=new HashMap<>();
    private final Set<FarmRunPatch> birdhouseTouched=new HashSet<>();
    private final Set<FarmRunPatch> wateredThisRun=new HashSet<>();
    private boolean activeCareOnly;
    private final DiseaseInterrupt diseasePriority=new DiseaseInterrupt();
    private volatile FarmRunPatch diseaseTarget;
    private volatile RouteLeg diseaseLeg;
    private volatile Set<Integer> diseaseItems=Collections.emptySet();
    private GuideView normalView;
    private boolean specialtyStaged,specialtyBirdPhase,seaweedBirdTransfer;
    private final Map<Integer,String> itemNamesCache=new HashMap<>();
    private Crop activeCrop;
    private volatile StepEngine.Action activeAction = StepEngine.Action.INSPECT;
    private volatile boolean activeLive,compostMissing;
    private int waterOfferTick=-1;
    private FarmRunPatch waterOfferPatch;
    private WorldPoint waterOfferOrigin;
    private volatile Set<Integer> requiredItems = Collections.emptySet();
    private volatile Set<Integer> missingStorageItems = Collections.emptySet();
    private volatile Map<Integer,String> withdrawalLabels=Map.of();
    private List<Integer> preparationBankPriority=List.of(),containerBankPriority=List.of();
    private FarmRunPatch lastLocalVisit;
    private volatile Set<Integer> missingTeleports = Collections.emptySet();
    private final AtomicLong session=new AtomicLong();
    private volatile long lifecycle;
    private volatile boolean runtimeReady;
    private volatile boolean guideRequested;
    private final Store bank = new Store(InventoryID.BANK, InterfaceID.Bankmain.UNIVERSE, "observedBankV1");
    private final Store vault = new Store(InventoryID.SEED_VAULT, InterfaceID.SeedVault.UNIVERSE, "observedVaultV1");
    private final List<Store> stores = List.of(bank, vault);
    private static final class StorageRead
    {
        final Map<Integer,Integer> items;
        final Set<Integer> placeholders;
        final long at;
        StorageRead(Map<Integer,Integer> items,Set<Integer> placeholders,long at)
        {this.items=items;this.placeholders=placeholders;this.at=at;}
    }
    private PhysicalStock quantities;
    private final SeedBoxStock seedBox=new SeedBoxStock();
    private volatile boolean seedBoxOwned,herbSackOwned,reagentPouchOwned;
    private Map<Integer,Integer> harvestBankSnapshot;
    private Map<HarvestContainers.Kind,HarvestContainers.Choice> bankHarvestContainers=Map.of(),carriedHarvestContainers=Map.of();
    private boolean boxEnabled=true;
    private FarmRunPatch seedBoxPlant;
    private Map<Integer,Integer> seedBoxPlantBag=Map.of();
    private int seedBoxPlantTick,seedBoxPlantRevision;
    private String seedBoxSaved="";
    private int seedBoxViewTick=-1,seedBoxViewRevision;
    private List<?> allocationInputs=List.of();
    private Map<FarmRunPatch,Crop> allocated=Map.of();
    private final Map<Set<Integer>,Map<Integer,List<String>>> eligibleTravel=new HashMap<>();
    private List<?> shelfInputs=List.of();
    private final BankRoutePlan bankRoutes=new BankRoutePlan();
    private List<FarmRunPatch> bankItinerary=List.of();
    private Map<Integer,Set<String>> bankFallback=Map.of();
    private List<?> routeSupplyInputs=List.of();
    private RouteSupplies.Plan routeSupplyPlan;
    private Set<Integer> shelfItems=Set.of(), shelfCandidates=Set.of(), bankPlaceholders=Set.of();
    private String stockProfile;
    private volatile Map<Integer,ImageIcon> icons = Collections.emptyMap();
    private volatile Set<FarmRunType> run = Collections.emptySet();
    private volatile Set<String> manualKeys; // physical selections, never persisted as a session
    private String runLabel;
    private String runPolicyKey;
    private volatile RunPolicy runPolicy=RunPolicy.BOTH;
    private volatile boolean runPayRemoval=true, runHasTrees;
    private RunPolicy temporaryCare;
    private Boolean temporaryPay,temporaryWater,temporaryLeprechaun;
    private volatile boolean runWater=true,runLeprechaun=true;
    private SeedlessPolicy temporarySeedless;
    private volatile Map<PatchType,String> temporaryReplacement=Collections.emptyMap();
    private boolean hopping;
    private String runProfile;
    private volatile Preparation.Workflow workflow=Preparation.Workflow.UNCHECKED;
    private PhysicalStock treeStock;
    private Map<FarmRunPatch,Crop> treeAllocation=Map.of();
    private TreePreparation.Plan treePlan=TreePreparation.Plan.EMPTY;
    private volatile boolean hideSeedless,clearSeedless;
    private boolean activeClearOnly, compostAcknowledged;
    private volatile Set<Integer> stepItems=Collections.emptySet();
    private volatile int spaceItem=-1;
    private boolean visitedStorage,explicitResupply;
    private volatile boolean preparationVault,preparationAtStorage;
    private WorldPoint preparationOrigin;
    private RunOrder pinned;
    private volatile LeprechaunWithdrawal leprechaunWithdrawal;
        private volatile FarmingHelperPanel panel;
    private NavigationButton nav;
    private volatile FarmRunPatch current;
    private volatile GuideView lastView;
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
        running=true;runtimeReady=false;
        long epoch=++lifecycle;
        // RuneLite starts/stops plugins on the EDT and replays scene events
        // on the client thread. Queue initialization before that replay.
        thread.invokeLater(()->{
            if(!running||epoch!=lifecycle)return;
            reader.useSceneObjects(highlight.objects());
            navigation=new NavigationBridge(bus::post);
            navigation.enabled(integrationActive("Shortest Path"));
            if(itemManager!=null){vaultViews=new SeedVaultViews(client,itemManager,this);runBank=new ActiveRunBank(client,plugins,itemManager,bankTags,tagManager,bankSearch);}
            runtimeReady=true;
        });
        patches.addAll(FarmRunCatalog.patches());
        patches.addAll(BirdhouseRun.patches());
        panel = new FarmingHelperPanel(this);
        nav = NavigationButton.builder().tooltip("Farming Helper").icon(ImageUtil.loadImageResource(FarmingHelperPlugin.class, "sidebar.png"))
            .panel(panel).priority(8).build();
        toolbar.addNavigation(nav);
        overlays.add(highlight);overlays.add(highlight.dialogue());
        if (itemHighlight != null) overlays.add(itemHighlight);
        panel.home();
    }

    @Override protected void shutDown()
    {
        running=false;runtimeReady=false;guideRequested=false;++lifecycle;session.incrementAndGet();
        toolbar.removeNavigation(nav);
        overlays.remove(highlight);overlays.remove(highlight.dialogue());
        if(itemHighlight!=null)overlays.remove(itemHighlight);
        patches.clear();panel=null;nav=null;
        // FIFO client-thread teardown precedes any subsequent enable. No
        // delayed cleanup can close a newly installed vault or bank view.
        thread.invokeLater(()->{
            resetClientState();
            if(vaultViews!=null)vaultViews.flushScrollbar();
            vaultViews=null;runBank=null;navigation=null;
            highlight.clear();itemNamesCache.clear();icons=Map.of();
        });
    }

    void startPreset(RunPreset preset)
    {
        startSelected(preset.select(patches,this::flag),preset.title,"group_"+preset.name());
    }
    void startIndividual(PatchType type)
    {
        startSelected(ManualSelection.keys(patches,p->p.getPatchType()==type),RunPreset.individualTitle(type),"individual_"+type.name());
    }
    void startCombinedShortcut(FarmRunType type)
    {
        startSelected(ManualSelection.keys(patches,p->p.getFarmRunType()==type),type.getDisplayName(),"shortcut_"+type.name());
    }
    void startManual(Set<String> keys)
    {startSelected(keys,"Manual Run","manual");}
    private void startSelected(Set<String> keys,String label,String policyKey)
    {
        Set<String> chosen=Set.copyOf(keys);
        Set<FarmRunType> types=EnumSet.noneOf(FarmRunType.class);
        for(FarmRunPatch p:patches)if(chosen.contains(p.key())&&enabled(p))types.add(p.getFarmRunType());
        if(types.isEmpty()) {FarmingHelperPanel target=panel;SwingUtilities.invokeLater(()->{if(running&&panel==target&&target!=null)target.emptySelection();});return;}
        Set<FarmRunType> selected=Set.copyOf(types);
        guideRequested = true;
        final long requested = session.incrementAndGet();
        thread.invokeLater(() -> {
            if (!running || !runtimeReady || !guideRequested || requested != session.get()) return;
            clearRun();
            run = selected;
            manualKeys=chosen;runLabel=label;
            runPolicyKey=policyKey;
            specialtyStaged=SpecialtyStages.stageBirds(policyKey,run)&&!("manual".equals(policyKey)&&startAt()==Preparation.StartAt.GUILD&&client.getRealSkillLevel(Skill.FARMING)>=45);
            runProfile=config.getRSProfileKey();
            updateAccessNotes();
            runHasTrees=patches.stream().anyMatch(p->eligible(p)&&p.getPatchType().isTree());
            loadStock();
            sampleStorage();
            update();
        });
    }

    void stop()
    {
        guideRequested = false;
        final long requested = session.incrementAndGet();
        thread.invokeLater(() -> { if (requested == session.get()) end(); });
    }

    private void clearRun()
    {
        run = Collections.emptySet();
        if(runBank!=null)runBank.close();
        bankRoutes.reset();bankItinerary=List.of();bankFallback=Map.of();routeSupplyInputs=List.of();routeSupplyPlan=null;
        hideSeedless=false;clearSeedless=true;activeClearOnly=false;compostAcknowledged=false;stepItems=Collections.emptySet();runPolicyKey=null;runHasTrees=false;
        manualKeys=null;runLabel=null;
        workflow=Preparation.Workflow.UNCHECKED;treeStock=null;treeAllocation=Map.of();treePlan=TreePreparation.Plan.EMPTY;visitedStorage=false;explicitResupply=false;preparationVault=false;preparationAtStorage=false;preparationOrigin=null;
        pinned=null;leprechaunWithdrawal=null;allocationInputs=List.of();allocated=Map.of();
        current = null;
        birdhouseTouched.clear();wateredThisRun.clear();activeCareOnly=false;clearWaterOffer();compostMissing=false;
        diseasePriority.reset();diseaseTarget=null;diseaseLeg=null;diseaseItems=Collections.emptySet();normalView=null;
        specialtyStaged=false;specialtyBirdPhase=false;seaweedBirdTransfer=false;
        runProgress.reset();
        temporaryCare=null;temporaryPay=null;temporaryWater=null;temporaryLeprechaun=null;temporarySeedless=null;temporaryReplacement=Collections.emptyMap();hopping=false;runProfile=null;
        progress.reset();aheadProgress.clear();spaceItem=-1;
        activeCrop = null;
        activeAction = StepEngine.Action.INSPECT;
        activeLive=false;
        requiredItems = Collections.emptySet();
        missingStorageItems = Collections.emptySet();
        withdrawalLabels=Map.of();preparationBankPriority=List.of();containerBankPriority=List.of();lastLocalVisit=null;
        missingTeleports = Collections.emptySet();
        lastView = null;
        activeLeg=null;
        if(navigation!=null)navigation.stop();
        icons=Map.of();
    }

    private void end(){clearRun();showHome();}
    private void showHome()
    {
        long ended=session.get();FarmingHelperPanel target=panel;
        if(running&&target!=null)SwingUtilities.invokeLater(()->{
            if(running&&panel==target&&ended==session.get()&&!guideRequested)target.home();
        });
    }
    private void reset()
    {
        guideRequested=false;session.incrementAndGet();
        resetClientState();showHome();
    }
    private void resetClientState()
    {
        if(vaultViews!=null)vaultViews.close();
        lastAccessLevel=-1;accessTicks=0;
        vaultAccessible=Collections.emptySet();vaultAccessRevision++;
        accessNotes=Collections.emptyMap();
        invalidateStock();
        stores.forEach(Store::clear);
        stockProfile=null;seedBox.clear();seedBoxOwned=false;herbSackOwned=false;reagentPouchOwned=false;
        harvestBankSnapshot=null;bankHarvestContainers=Map.of();carriedHarvestContainers=Map.of();seedBoxPlant=null;seedBoxSaved="";seedBoxViewTick=-1;
        seedBoxPlantBag=Map.of();shelfItems=Set.of();shelfCandidates=Set.of();
        clearRun();
    }

    CompostChoice compostChoice(){return CompostChoice.load(setting("directorCompostChoice",""),flag("directorUseCompost",true));}
    CompostRun.Production compostProduction(){return CompostRun.Production.load(setting("compostProductionV112","ULTRA"));}
    CompostRun.Material compostMaterial(){return CompostRun.Material.load(setting("compostMaterialV112","AUTO"));}
    SeedlessPolicy seedlessDefault(){return SeedlessPolicy.load(setting("seedlessPolicyV1","GUIDE"));}
    Preparation.StartAt startAt(){return Preparation.StartAt.load(setting("startAtV1","GUILD"));}
    private void refreshRunPreferences()
    {
        // Old per-run saved overrides are intentionally ignored; all other preferences remain intact.
        runPolicy=temporaryCare!=null?temporaryCare:RunPolicy.of(compostChoice()!=CompostChoice.NONE,flag("directorProtectTrees",true));
        runPayRemoval=temporaryPay!=null?temporaryPay:flag("directorPayRemoval",true);
        runWater=temporaryWater!=null?temporaryWater:flag("directorWater",true);
        runLeprechaun=temporaryLeprechaun!=null?temporaryLeprechaun:flag("directorLeprechaun",true);
        SeedlessPolicy choice=temporarySeedless!=null?temporarySeedless:seedlessDefault();
        hideSeedless=choice.hide;clearSeedless=choice.hide?choice.clear:flag("clearSeedlessPreferenceV1",true);
    }
    RunPolicy runPolicy(){return runPolicy;}
    boolean runPayRemoval(){return runPayRemoval;}
    boolean runHasTrees(){return runHasTrees;}
    boolean runWater(){return runWater;}
    boolean runLeprechaun(){return runLeprechaun;}
    /** All sidebar run mutations use one session/profile gate. */
    private void inRun(Runnable action)
    {
        long id=session.get();
        thread.invokeLater(()->{
            if(running&&runtimeReady&&guideRequested&&id==session.get()&&!run.isEmpty()&&
                Objects.equals(runProfile,config.getRSProfileKey()))action.run();
        });
    }
    void setRunSupplies(Boolean water,Boolean leprechaun)
    {
        inRun(()->{
            if(water!=null)temporaryWater=water;
            if(leprechaun!=null)temporaryLeprechaun=leprechaun;
            lastView=null;update();
        });
    }
    void setRunPolicy(RunPolicy policy,Boolean pay)
    {
        inRun(()->{
            if(policy!=null)temporaryCare=policy;
            if(pay!=null)temporaryPay=pay;
            lastView=null;update();
        });
    }
    private String observedItemName(int id)
    {if(itemManager==null)return "";return itemNamesCache.computeIfAbsent(id,i->{var item=itemManager.getItemComposition(i);return item==null||item.getNote()!=-1||item.getPlaceholderTemplateId()!=-1?"":item.getName();});}
    boolean hideSeedless(){return hideSeedless;}
    boolean clearSeedless(){return clearSeedless;}
    Set<Integer> activeStepItems(){return !guideRequested?Collections.emptySet():diseaseTarget!=null?diseaseItems:!preparing()?stepItems:Collections.emptySet();}
    void resetObservedStock()
    {
        inRun(()->{
            loadStock();
            invalidateStock();
            for (Store store : stores)
            {
                store.clear();
                save(store.key, "");
                save(store.key + "At", "");
            }
            seedBox.clear();seedBoxSaved="";save("observedSeedBoxV2","");seedBoxPlant=null;seedBoxViewTick=-1;
            sampleStorage();lastView=null;update();
        });
    }
    void acknowledgeCompost(FarmRunPatch patch)
    {
        inRun(()->{
            if(current!=patch||activeAction!=StepEngine.Action.COMPOST)return;
            compostAcknowledged=true;lastView=null;update();
        });
    }
    void setSeedlessPolicy(SeedlessPolicy policy)
    {
        inRun(()->{temporarySeedless=policy;sampleStorage();lastView=null;update();});
    }
    String runReplacement(PatchType type){return temporaryReplacement.getOrDefault(type,"");}
    void setRunReplacement(PatchType type,String mode)
    {
        inRun(()->{
            Map<PatchType,String> next=new EnumMap<>(PatchType.class);next.putAll(temporaryReplacement);
            if(KeepCrop.explicit(mode))next.put(type,mode);else next.remove(type);
            temporaryReplacement=Map.copyOf(next);lastView=null;update();
        });
    }
    void skipRemainingType(PatchType type)
    {
        inRun(()->{
            for(FarmRunPatch p:patches)if(p.getPatchType()==type&&selected(p)&&!runProgress.finished(p))runProgress.skip(p);
            lastView=null;update();
        });
    }
    List<PatchType> selectedRunTypes()
    {
        Set<PatchType> result=EnumSet.noneOf(PatchType.class);
        Set<FarmRunType> types=run;Set<String> keys=manualKeys;
        for(FarmRunPatch p:patches)if(ManualSelection.includes(p,types,keys)&&enabled(p))result.add(p.getPatchType());
        return new ArrayList<>(result);
    }
    void skip(FarmRunPatch displayed)
    {skip(displayed,activeAction()==StepEngine.Action.WATER,diseaseInterruptActive());}
    void skip(FarmRunPatch displayed,boolean water,boolean disease)
    {
        inRun(()->{
            if(disease)
            {
                if(displayed==diseaseTarget){diseasePriority.skip(displayed);diseaseTarget=null;lastView=null;}
            }
            else if(diseaseTarget==null&&current!=null&&current==displayed&&water==(activeAction==StepEngine.Action.WATER))
            {
                if(water)settleWater(current);
                else runProgress.skip(current);
            }
            update();
        });
    }

    private void clearWaterOffer()
    {waterOfferPatch=null;waterOfferOrigin=null;waterOfferTick=-1;}
    private void settleWater(FarmRunPatch patch)
    {wateredThisRun.add(patch);if(waterOfferPatch==patch)clearWaterOffer();}
    private void observeWater(FarmRunPatch patch,QuestFarmingStateReader.Observation o)
    {
        if(o==null||o.source!=QuestFarmingStateReader.Source.LIVE)return;
        if(o.state==CropState.EMPTY||o.state==CropState.DEAD)
        {
            wateredThisRun.remove(patch);
            if(waterOfferPatch==patch)clearWaterOffer();
        }
        else if(CropCare.watered(patch.getPatchType(),o))settleWater(patch);
    }

    /** Profile/config/plugin events may originate on the EDT or another worker. */
    private void onClient(Runnable action)
    {
        long epoch=lifecycle;
        Runnable guarded=()->{if(running&&runtimeReady&&epoch==lifecycle)action.run();};
        if(client.isClientThread())guarded.run();else thread.invokeLater(guarded);
    }
    @Subscribe public void onPluginChanged(PluginChanged event)
    {
        if(!"Shortest Path".equals(event.getPlugin().getName()))return;
        onClient(()->{
            navigation.enabled(event.isLoaded());
            bankRoutes.reset();
            if(!run.isEmpty())update();
        });
    }

    @Subscribe public void onPluginMessage(PluginMessage event)
    {
        if(!"shortestpath".equals(event.getNamespace())||
            !("result".equals(event.getName())||"currentTarget".equals(event.getName())))return;
        onClient(()->{
            if("result".equals(event.getName())&&event.getData()!=null&&bankRoutes.message(event.getData()))return;
            if(navigation.message(event))highlight.transportChanged();
        });
    }

    @Subscribe public void onProfileChanged(ProfileChanged event){onClient(this::reset);}
    @Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
    {onClient(()->{if(!guideRequested||!Objects.equals(runProfile,config.getRSProfileKey()))reset();});}
    @Subscribe public void onGameObjectSpawned(GameObjectSpawned event) { highlight.spawned(event.getGameObject()); }
    @Subscribe public void onGameObjectDespawned(GameObjectDespawned event) { highlight.despawned(event.getGameObject()); }
    @Subscribe public void onNpcSpawned(NpcSpawned event) { highlight.npcChanged(); }
    @Subscribe public void onNpcDespawned(NpcDespawned event) { highlight.npcChanged(); }
    @Subscribe public void onWorldEntityDespawned(WorldEntityDespawned event)
    {highlight.removeView(event.getWorldEntity().getWorldView());}
    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if(event.getGameState()==GameState.LOADING || event.getGameState()==GameState.LOGIN_SCREEN) highlight.clear();
        if(event.getGameState()==GameState.HOPPING || event.getGameState()==GameState.LOGGING_IN){seedBox.forget();seedBoxPlant=null;seedBoxViewTick=-1;invalidateStock();}
        if(event.getGameState()==GameState.LOGIN_SCREEN){reset();return;}
        if(event.getGameState()==GameState.HOPPING&&guideRequested)
        {
            // Preserve same-character run decisions, but release scene-local routes/widgets.
            hopping=true;clearWaterOffer();leprechaunWithdrawal=null;aheadProgress.clear();spaceItem=-1;highlight.clear();if(navigation!=null)navigation.stop();
            if(runBank!=null)runBank.close();if(vaultViews!=null)vaultViews.close();
            activeLive=false;stepItems=Collections.emptySet();lastAccessLevel=-1;
        }
    }

    @Subscribe public void onGameTick(GameTick event)
    {
        if(!running||!runtimeReady||client.getGameState()!=GameState.LOGGED_IN)return;
        if(!run.isEmpty()&&!Objects.equals(runProfile,config.getRSProfileKey()))reset();
        if(hopping){hopping=false;lastView=null;}
        sampleStorage();
        stock(); // refresh ownership even with no guide open; cached until inventory changes
        updateSeedBox();
        // Re-evaluate quest, diary, Hunter and Sailing gates even when Farming is unchanged.
        if(lastAccessLevel!=client.getRealSkillLevel(Skill.FARMING)||++accessTicks>=5){accessTicks=0;invalidateTravel();updateAccessNotes();}
        if (!run.isEmpty() && guideRequested)
        {
            // Re-evaluate supplies and local transfer legs before a pending route can refresh.
            update();
        }
        if(navigation!=null)navigation.tick(client.getLocalPlayer()==null?null:LocationContext.capture(client).pathStart());
        if(vaultViews!=null)vaultViews.tick();
        updateRunBank(false);

    }

    @Subscribe public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.BANKMAIN || event.getGroupId() == InterfaceID.SEED_VAULT)
        {
            (event.getGroupId()==InterfaceID.BANKMAIN?bank:vault).sampled=false;
            // The next game tick owns storage sampling. Opening a widget is
            // not proof that its item packet has arrived yet.
        }
    }

    @Subscribe public void onItemContainerChanged(ItemContainerChanged event)
    {
        int id=event.getContainerId();
        if(id==InventoryID.INV||id==InventoryID.WORN)quantities=null;
        if(config!=null&&client.getGameState()==GameState.LOGGED_IN)loadStock();
        if(id==InventoryID.INV&&event.getItemContainer()!=null&&itemManager!=null)
        {
            Map<Integer,Integer> bag=PhysicalStock.readChecked(event.getItemContainer(),itemManager::getItemComposition,null);
            if(bag!=null)
            {
                seedBox.carried(bag);
                if(SeedBoxStock.boxId(bag)<0)seedBoxPlant=null;
            }
        }
        if(id==InventoryID.SEED_BOX&&client.getGameState()==GameState.LOGGED_IN)
        {
            loadStock();
            if(stockProfile!=null&&itemManager!=null&&SeedBoxStock.boxId(readItems(client.getItemContainer(InventoryID.INV)))>0&&
                seedBox.observe(event.getItemContainer(),client.getTickCount(),this::seedItem))
            {seedBoxPlant=null;seedBoxViewTick=client.getTickCount();seedBoxViewRevision=-1;seedBoxChanged();}
        }
        if(id==InventoryID.BANK||id==InventoryID.SEED_VAULT)
        {
            Store store = id == bank.id ? bank : vault;
            store.sampled = false;
            // Keep a valid visible packet if the widget closes before the next game tick.
            if (stockProfile != null && client.getGameState() == GameState.LOGGED_IN && visibleWidget(store.widget))
            {
                StorageRead observation = store.read(event.getItemContainer());
                if (observation != null) store.pending = observation;
            }
            if(vaultViews!=null&&id==InventoryID.SEED_VAULT)vaultViews.invalidate();
        }
    }

    @Subscribe public void onVarbitChanged(VarbitChanged event)
    {
        highlight.transformed();
        if(!eligibleTravel.isEmpty()&&CuratedBank.travelChanged(event.getVarbitId(),event.getVarpId()))invalidateTravel();
    }
    @Subscribe public void onConfigChanged(net.runelite.client.events.ConfigChanged event)
    {
        if("shortestpath".equals(event.getGroup()))onClient(()->{invalidateTravel();bankRoutes.reset();routeSupplyInputs=List.of();});
        if("farming-helper".equals(event.getGroup())&&!event.getKey().startsWith("observed"))preferenceRevision.incrementAndGet();
    }

    @Subscribe public void onPostClientTick(PostClientTick event)
    {if(vaultViews!=null)vaultViews.postClientTick();}

    @Subscribe public void onScriptPreFired(ScriptPreFired event)
    {if(vaultViews!=null)vaultViews.beforeScript(event.getScriptId());}
    @Subscribe public void onScriptPostFired(ScriptPostFired event)
    {if(vaultViews!=null)vaultViews.afterScript(event.getScriptId());}
    @Subscribe public void onWidgetClosed(WidgetClosed event)
    {
        if(vaultViews!=null&&event.getGroupId()==SeedVaultViews.GROUP)vaultViews.close();
        if(event.getGroupId()==InterfaceID.BANKMAIN)
        {
            bank.sampled=false;
            if(runBank!=null)runBank.bankClosed();
            bankRoutes.closeVisit();
        }
        if(event.getGroupId()==InterfaceID.SEED_VAULT)vault.sampled=false;
    }
    @Subscribe public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if(vaultViews!=null)vaultViews.nativeMenu(event.getParam1());
        if(bankRoutes.started()&&runBank!=null&&runBank.bankOpen())
        {
            String option=event.getMenuOption().toLowerCase(Locale.ROOT);
            if(option.startsWith("withdraw")||option.startsWith("deposit"))bankRoutes.freeze();
        }
        if(client.getGameState()!=GameState.LOGGED_IN||itemManager==null||event.isConsumed())return;
        Map<Integer,Integer> bag=stock().bag;
        if(SeedBoxStock.boxId(bag)<0||stockProfile==null)return;
        if(event.getParam1()>>>16==InterfaceID.HOSIDIUS_SEEDBOX)seedBox.orderUncertain();
        // Walking, examining and ordinary menu clicks do not cancel a pending
        // transfer/plant. Reconcile only after the server's container updates.
        String option=event.getMenuOption().toLowerCase(Locale.ROOT).replace('-',' ').trim();
        String target=net.runelite.client.util.Text.removeTags(event.getMenuTarget()).toLowerCase(Locale.ROOT);
        int id=event.getItemId();
        boolean box=SeedBoxStock.isBox(id)||target.equals("seed box")||target.equals("seed box (open)");
        if(event.getMenuAction()==MenuAction.WIDGET_TARGET_ON_WIDGET)
        {
            var selected=client.getSelectedWidget();
            int selectedId=inventorySeedItem(selected,bag);
            int targetId=id>0?id:inventorySeedItem(event.getWidget(),bag);
            int seed=SeedBoxStock.isBox(selectedId)?targetId:SeedBoxStock.isBox(targetId)?selectedId:-1;
            if(seed>0&&seedItem(seed)){beginSeedTransfer(true,seed,bag);return;}
        }
        if(box||event.getParam1()>>>16==InterfaceID.HOSIDIUS_SEEDBOX)
        {
            if(option.equals("fill")||option.startsWith("deposit")){beginSeedTransfer(true,seedItem(id)?id:-1,bag);return;}
            else if(option.equals("empty")||option.equals("empty to bank")||option.startsWith("withdraw")||option.startsWith("remove"))
            {beginSeedTransfer(false,seedItem(id)?id:-1,bag);return;}
        }
        if((seedBox.pending()||seedBoxPlant!=null)&&SeedBoxStock.conflictingLooseAction(option)&&
            (seedItem(id)||option.equals("take")&&seedItem(event.getId())))
        {
            // Another seed-moving action can explain the loose quantity delta.
            // We cannot attribute it to the box; keep only historical hints.
            seedBox.forget();seedBoxPlant=null;seedBoxChanged();
        }
        if(event.getMenuAction()==MenuAction.WIDGET_TARGET_ON_GAME_OBJECT&&highlight!=null)
        {
            int selected=inventorySeedItem(client.getSelectedWidget(),bag);
            if(SeedBoxStock.isBox(selected)||seedItem(selected))
            {
                FarmRunPatch patch=highlight.clickedPatch(patches,event.getId(),event.getParam0(),event.getParam1());
                if(patch!=null)
                {
                    var observation=observe(patch);
                    if(observation.source==QuestFarmingStateReader.Source.LIVE&&observation.state==CropState.EMPTY)
                    {
                        if(seedBoxPlant!=null||seedBox.pending()){seedBox.forget();seedBoxChanged();}
                        seedBoxPlant=patch;seedBoxPlantBag=bag;seedBoxPlantTick=client.getTickCount();
                        seedBoxPlantRevision=seedBox.revision();
                    }
                }
            }
        }
    }

    private boolean seedItem(int id)
    {
        if(id<=0||SeedBoxStock.isBox(id))return false;
        if(id==ItemID.CORAL_ELKHORN_FRAG||id==ItemID.CORAL_PILLAR_FRAG||id==ItemID.CORAL_UMBRAL_FRAG)return true;
        String name=observedItemName(id).toLowerCase(Locale.ROOT);
        return name.endsWith(" seed")||name.endsWith(" seeds")||name.equals("acorn")||name.equals("crystal acorn")||name.equals("seaweed spore")||name.equals("mushroom spore");
    }
    private int inventorySeedItem(net.runelite.api.widgets.Widget widget,Map<Integer,Integer> bag)
    {
        if(widget==null)return -1;
        int group=widget.getId()>>>16,id=widget.getItemId();
        if(group!=InterfaceID.INVENTORY&&group!=InterfaceID.INVENTORY_NOOPS&&group!=InterfaceID.BANKSIDE)return -1;
        if(id<=0)
        {
            var inventory=client.getItemContainer(InventoryID.INV);
            int slot=widget.getIndex();
            Item item=inventory!=null&&slot>=0&&slot<inventory.size()?inventory.getItem(slot):null;
            if(item!=null)id=item.getId();
        }
        return bag.getOrDefault(id,0)>0?id:-1;
    }
    private int seedByName(String name)
    {
        for(PatchType type:PatchType.values())for(Crop crop:CropCatalog.crops(type))
            if(crop.getItemName().equalsIgnoreCase(name)&&seedItem(crop.getItemId()))return crop.getItemId();
        for(var item:itemManager.search(name))
            if(seedItem(item.getId())&&observedItemName(item.getId()).equalsIgnoreCase(name))return item.getId();
        return -1;
    }
    private boolean visibleWidget(int id)
    {var widget=client.getWidget(id);return widget!=null&&!widget.isHidden();}
    private Map<Integer,Integer> looseSeeds(Map<Integer,Integer> bag)
    {
        Map<Integer,Integer> result = bag;
        for (Store store : stores)
        {
            if (!store.inTransfer) continue;
            Map<Integer,Integer> observed = PhysicalStock.readChecked(client.getItemContainer(store.id), itemManager::getItemComposition, null);
            if (observed == null) return null;
            result = PhysicalStock.combine(result, observed);
        }
        return result;
    }
    private void beginSeedTransfer(boolean adding,int seed,Map<Integer,Integer> bag)
    {
        for (Store store : stores) store.inTransfer = visibleWidget(store.widget);
        Map<Integer,Integer> loose=looseSeeds(bag);
        if(seedBox.pending()||seedBoxPlant!=null){seedBox.forget();seedBoxPlant=null;seedBoxChanged();}
        if(loose!=null)seedBox.begin(adding,seed,loose,client.getTickCount());
        else {seedBox.forget();seedBoxChanged();}
    }
    private void seedBoxChanged()
    {
        quantities=null;
        String snapshot=seedBox.snapshot();
        if(!snapshot.equals(seedBoxSaved)&&stockProfile!=null&&Objects.equals(stockProfile,config.getRSProfileKey()))
        {seedBoxSaved=snapshot;save("observedSeedBoxV2",snapshot);}
    }
    private void updateSeedBox()
    {
        int tick=client.getTickCount();
        if(itemManager==null||stockProfile==null)return;
        if(visibleWidget(InterfaceID.HosidiusSeedbox.UNIVERSE))
        {
            if(seedBoxViewTick<0){seedBoxViewTick=tick;seedBoxViewRevision=seedBox.revision();}
            if(tick-seedBoxViewTick>=2&&seedBoxViewRevision==seedBox.revision()&&!seedBox.pending()&&seedBoxPlant==null)
            {
                var nativeBox=client.getItemContainer(InventoryID.SEED_BOX);
                if(SeedBoxStock.boxId(stock().bag)>0&&seedBox.observe(nativeBox,tick,this::seedItem))
                    seedBoxChanged();
            }
        }
        else seedBoxViewTick=-1;
        if(seedBox.finishCheck(tick))seedBoxChanged();
        if(!seedBox.pending()&&seedBoxPlant==null)return;
        Map<Integer,Integer> bag=stock().bag;
        if(SeedBoxStock.boxId(bag)<0){seedBox.carried(bag);seedBoxPlant=null;return;}
        if(seedBox.pending())
        {
            if(bank.transferClosed() || vault.transferClosed())
            {seedBox.forget();seedBoxChanged();}
            else
            {
                Map<Integer,Integer> loose=looseSeeds(bag);
                if(loose==null){seedBox.forget();seedBoxChanged();}
                else if(seedBox.settle(loose,tick,this::seedItem))seedBoxChanged();
            }
        }
        if(seedBoxPlant!=null)
        {
            // Native container observations already clear seedBoxPlant. A
            // different inferred mutation makes the old planting baseline unsafe.
            if(seedBox.revision()!=seedBoxPlantRevision){seedBoxPlant=null;seedBox.forget();seedBoxChanged();return;}
            if(tick<=seedBoxPlantTick)return;
            if(tick-seedBoxPlantTick>30){seedBoxPlant=null;return;}
            var observation=observe(seedBoxPlant);
            if(observation.source!=QuestFarmingStateReader.Source.LIVE)return;
            if(observation.state==CropState.GROWING)
            {
                var produce = QuestFarmingStateReader.livingProduce(seedBoxPlant.getPatchType().name(), observation);
                if (produce != null)
                {
                    Crop crop = CropCatalog.findByName(seedBoxPlant.getPatchType(), produce.getName());
                    if (crop != null && seedBox.planted(crop, seedBoxPlantBag.getOrDefault(crop.getItemId(), 0),
                        bag.getOrDefault(crop.getItemId(), 0), seedBoxPlantRevision)) seedBoxChanged();
                }
                seedBoxPlant=null;
            }
        }
    }
    @Subscribe public void onChatMessage(ChatMessage event)
    {
        if(itemManager==null||client.getGameState()!=GameState.LOGGED_IN||
            event.getType()!=ChatMessageType.GAMEMESSAGE&&event.getType()!=ChatMessageType.SPAM)return;
        String message=net.runelite.client.util.Text.removeTags(event.getMessage()).trim();
        if(!message.toLowerCase(Locale.ROOT).contains("seed box")&&!message.matches("^[\\d,]+\\s*x\\s*.+"))return;
        if(SeedBoxStock.boxId(stock().bag)<0||stockProfile==null)return;
        if(seedBox.chat(message,client.getTickCount(),this::seedByName))seedBoxChanged();
    }

    // Null means ordinary vault browsing; an empty set is a live run with no planting supplies.
    Set<Integer> vaultRunItems()
    {
        if(!guideRequested||run.isEmpty())return null;
        // The checklist covers the current preparation window, but the vault
        // must also reveal planting materials allocated to later windows.
        Set<Integer> ids=new HashSet<>(requiredItems());
        if(diseaseTarget==null)for(Crop crop:allocated.values())ids.add(crop.getItemId());
        return Set.copyOf(ids);
    }

    private boolean birdBankPhase(){return specialtyStaged&&specialtyBirdPhase||run.size()==1&&run.contains(FarmRunType.BIRDHOUSE);}
    private Set<Integer> bankRunItems(Map<Integer,Integer> owned)
    {
        if(!guideRequested||run.isEmpty())return null;
        Set<Integer> runItems=requiredItems();
        boolean bird=birdBankPhase();
        List<?> inputs=List.of(session.get(),quantities,bankPlaceholders,runItems,bankFallback.keySet(),bird,preferenceRevision.get(),vaultAccessRevision);
        if(inputs.equals(shelfInputs))return shelfItems;
        boolean diving=!bird&&patches.stream().anyMatch(p->eligible(p)&&(p.getPatchType()==PatchType.SEAWEED||p.getPatchType()==PatchType.CORAL));
        Set<Integer> candidates = new HashSet<>(owned.keySet());
        candidates.addAll(bankPlaceholders);
        shelfCandidates = Set.copyOf(candidates);
        // Preserve the broad shelf across preparation windows.
        Set<Integer> broad=new HashSet<>(runItems);
        broad.addAll(bankFallback.keySet());
        List<PatchType> selectedTypes=selectedRunTypes();
        for(PatchType type:selectedTypes)
            for(Crop crop:CropCatalog.crops(type))
                for(ProtectionPayment payment:CropCatalog.protection(crop))
                    if(shelfCandidates.contains(payment.getItemId()))broad.add(payment.getItemId());
        if(selectedTypes.contains(PatchType.COMPOST_BIN))
        {
            for(int id:CompostRun.REGULAR)if(shelfCandidates.contains(id))broad.add(id);
            for(int id:CompostRun.SUPER)if(shelfCandidates.contains(id))broad.add(id);
        }
        if(!bird)for(int id:shelfCandidates)
        {
            Crop crop=VaultRanking.resolve(id,observedItemName(id));
            if(crop!=null&&selectedTypes.contains(crop.getPatchType()))broad.add(id);
        }
        shelfItems=CuratedBank.shelf(broad,shelfCandidates,this::observedItemName,client,config,
            bird,!bird&&runHasTrees,diving,teleports(shelfCandidates).keySet());
        shelfInputs=inputs;
        return shelfItems;
    }
    private String updateRunBank(boolean manual)
    {
        if(runBank==null)return "The farming bank view is unavailable.";
        boolean enabled=flag("activeRunBank",true);
        if(!guideRequested||run.isEmpty()||!runBank.bankOpen())
        {bankRoutes.closeVisit();runBank.tick(null,Collections.emptySet(),enabled,birdBankPhase());return "Open a bank, then choose Show farming bank.";}
        // Shortest Path enables bank traversal in both bank inventory modes.
        boolean bankAware=navigation!=null&&navigation.enabled()&&BankRoutePlan.bankMode(config.getConfiguration("shortestpath","useTeleportationItems"));
        if(bank.sampled&&!bankRoutes.started())
            bankRoutes.begin(bankAware?LocationContext.capture(client).pathStart():null,bankItinerary);
        if(bankAware&&bankRoutes.started())bankRoutes.tick(navigation);
        if(!enabled){runBank.tick(null,Set.of(),false,birdBankPhase());return "";}
        Set<Integer> eligible=bankRunItems(stock().shelf);
        List<Integer> priority=bankRoutes.started()
            ?bankRoutes.priority(bankFallback,manual)
            :List.copyOf(bankFallback.keySet());
        LinkedHashSet<Integer> prioritized=new LinkedHashSet<>(preparationBankPriority);
        prioritized.addAll(priority);prioritized.addAll(containerBankPriority);priority=List.copyOf(prioritized);
        if(manual)return runBank.show(eligible,shelfCandidates,birdBankPhase(),priority);
        runBank.tick(eligible,shelfCandidates,enabled,birdBankPhase(),priority);return "";
    }
    void showActiveRunBank(java.util.function.Consumer<String> feedback)
    {
        long id=session.get();
        inRun(()->{
            if(!flag("activeRunBank",true))return;
            sampleStorage();bankRoutes.closeVisit();update();
            String message=updateRunBank(true);
            if(!message.isEmpty())SwingUtilities.invokeLater(()->{if(id==session.get()&&guideRequested)feedback.accept(message);});
        });
    }
    TransportHint currentTransport()
    {
        if(!guideRequested||diseaseTarget!=null||preparing()||current==null||navigation==null||client.getLocalPlayer()==null||activeLeg!=null&&activeLeg.interaction!=RouteLeg.Interaction.NONE)return null;
        return navigation.nearbyTransport(LocationContext.capture(client).pathStart());
    }
    long preferenceRevision(){return preferenceRevision.get();}
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
        invalidateStock();
        stockProfile = profile;
        seedBoxSaved=profile==null?"":setting("observedSeedBoxV2","");seedBox.restore(seedBoxSaved);seedBoxPlant=null;seedBoxViewTick=-1;
        long now = System.currentTimeMillis();
        for (Store store : stores)
        {
            store.clear();
            if (profile != null) store.restore(now);
        }
    }

    void sampleStorage()
    {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        loadStock();
        if (stockProfile != null) stores.forEach(Store::sample);
    }

    private final class Store
    {
        final int id;
        final int widget;
        final String key;
        Map<Integer,Integer> items = Map.of();
        long at;
        boolean sampled, inTransfer;
        StorageRead pending;

        Store(int id, int widget, String key)
        {
            this.id = id;
            this.widget = widget;
            this.key = key;
        }

        void clear()
        {
            items = Map.of();
            at = 0;
        }

        void restore(long now)
        {
            items = StockSnapshot.decode(setting(key, ""));
            at = StockSnapshot.time(setting(key + "At", ""), now);
            if (itemManager == null) return;
            int before = items.size();
            PhysicalStock.sanitize(items, itemManager::getItemComposition);
            if (items.size() != before) save(key, StockSnapshot.encode(items));
        }

        boolean transferClosed()
        {
            return inTransfer && !visibleWidget(widget);
        }

        StorageRead read(ItemContainer container)
        {
            if (itemManager == null) return null;
            Set<Integer> placeholders = id == InventoryID.BANK ? new HashSet<>() : null;
            Map<Integer,Integer> stock = PhysicalStock.readChecked(container, itemManager::getItemComposition, placeholders);
            return stock == null ? null : new StorageRead(stock, placeholders, System.currentTimeMillis());
        }

        void sample()
        {
            StorageRead latest = pending;
            pending = null;
            if (!visibleWidget(widget)) sampled = false;
            else if (!sampled || latest != null)
            {
                StorageRead live = read(client.getItemContainer(id));
                if (live != null)
                {
                    sampled = true;
                    latest = live;
                }
            }
            if (latest == null || !Objects.equals(stockProfile, config.getRSProfileKey())) return;
            if (latest.placeholders != null) bankPlaceholders = Set.copyOf(latest.placeholders);
            if (!items.equals(latest.items))
            {
                quantities = null;
                if(this==bank&&bankRoutes.started())bankRoutes.freeze();
                items = latest.items;
                save(key, StockSnapshot.encode(items));
            }
            at = latest.at;
            save(key + "At", Long.toString(at));
        }
    }

    private void invalidateTravel(){eligibleTravel.clear();shelfInputs=List.of();}

    private void invalidateStock()
    {
        quantities=null;bankPlaceholders=Set.of();invalidateTravel();
        for (Store store : stores)
        {
            store.sampled = false;
            store.pending = null;
        }
    }

    private Map<Integer,Integer> readItems(ItemContainer container)
    {return itemManager==null?Map.of():PhysicalStock.read(container,itemManager::getItemComposition);}

    PhysicalStock stock()
    {
        if(config!=null&&client.getGameState()==GameState.LOGGED_IN)loadStock();
        boolean enabled=flag("directorUseSeedBox",true);
        if(quantities==null||boxEnabled!=enabled)
        {
            boxEnabled=enabled;
            invalidateTravel();
            Map<Integer,Integer> bag=readItems(client.getItemContainer(InventoryID.INV));
            seedBoxOwned=SeedBoxStock.boxId(bag)>0||SeedBoxStock.boxId(bank.items)>0;
            carriedHarvestContainers=HarvestContainers.find(bag);
            if(harvestBankSnapshot!=bank.items)
            {harvestBankSnapshot=bank.items;bankHarvestContainers=HarvestContainers.find(bank.items);}
            herbSackOwned=carriedHarvestContainers.containsKey(HarvestContainers.Kind.HERB)||bankHarvestContainers.containsKey(HarvestContainers.Kind.HERB);
            reagentPouchOwned=carriedHarvestContainers.containsKey(HarvestContainers.Kind.REAGENT)||bankHarvestContainers.containsKey(HarvestContainers.Kind.REAGENT);
            Map<Integer,Integer> confirmed=seedBox.carried(bag);
            quantities=new PhysicalStock(bag,readItems(client.getItemContainer(InventoryID.WORN)),bank.items,vault.items,enabled?confirmed:Map.of(),this::observedItemName,
                id->itemManager==null?id:itemManager.canonicalize(id));
        }
        return quantities;
    }

    boolean seedBoxOwned(){return seedBoxOwned;}
    boolean herbSackOwned(){return herbSackOwned;}
    boolean reagentPouchOwned(){return reagentPouchOwned;}
    private boolean useHarvestContainer(HarvestContainers.Kind kind)
    {return flag(kind==HarvestContainers.Kind.HERB?"directorUseHerbSack":"directorUseReagentPouch",true);}
    private String harvestContainerRequirements(List<FarmRunPatch> remaining,Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations,
        Map<Integer,Integer> required)
    {
        StringJoiner advice=new StringJoiner("\n");
        for(HarvestContainers.Kind kind:HarvestContainers.Kind.values())
        {
            if(!useHarvestContainer(kind))continue;
            var carried=carriedHarvestContainers.get(kind);
            var stored=bankHarvestContainers.get(kind);
            if(kind==HarvestContainers.Kind.HERB&&!flag("directorUseOrdinaryHerbSack",false))
            {
                if(carried!=null&&carried.capacity<100)carried=null;
                if(stored!=null&&stored.capacity<100)stored=null;
            }
            if(carried==null&&stored==null)continue;
            Set<Integer> species=new HashSet<>();
            int harvests=0;
            for(FarmRunPatch patch:remaining)
            {
                var o=observations.get(patch);
                if(o==null||o.state!=CropState.HARVESTABLE&&o.state!=CropState.UNCHECKED)continue;
                var produce=QuestFarmingStateReader.livingProduce(patch.getPatchType().name(),o);
                if(HarvestContainers.eligible(kind,patch.getPatchType(),produce)){species.add(produce.getItemID());harvests++;}
            }
            var choice=HarvestContainers.required(kind,true,carried,stored,species.size(),harvests);
            if(choice==null)continue;
            required.put(choice.id,1);
            advice.add((carried==null?"Withdraw and check ":choice.open?"Check remaining space in ":"Open and check ")+
                choice.name+" ("+choice.capacity+" per type; contents not verified).");
        }
        return advice.toString();
    }
    private Map<Integer,List<String>> teleports(Set<Integer> candidates)
    {
        return eligibleTravel.computeIfAbsent(candidates,ids->
            CuratedBank.eligibleTeleports(ids,this::observedItemName,client,config));
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
        Map<Quest,QuestState> quests=new EnumMap<>(Quest.class);
        for (FarmRunPatch patch : patches)
        {
            String issue = Waypoints.accessIssue(client,patch.getPatchType(),patch.getLocation(),level,quests);
            if(issue==null)accessible.add(patch.key());
            if (issue == null && patch.getPatchType()!=PatchType.COMPOST_BIN && patch.getPatchType()!=PatchType.BIRDHOUSE && CropCatalog.recommend(patch.getPatchType(),level) == null) issue = "Farming level too low";
            if (issue != null) notes.put(patch.key(),issue);
        }
        if(!vaultAccessible.equals(accessible)){vaultAccessible=Set.copyOf(accessible);vaultAccessRevision++;}
        Map<String,String> old=accessNotes;
        accessNotes = Collections.unmodifiableMap(notes);
        FarmingHelperPanel target=panel;
        if(!old.equals(notes)&&target!=null)SwingUtilities.invokeLater(()->{if(running&&panel==target)target.accessChanged();});
    }

    private QuestFarmingStateReader.Observation observe(FarmRunPatch p)
    {
        if(p.getPatchType()==PatchType.BIRDHOUSE)return BirdhouseRun.observe(client,config,p,highlight!=null&&highlight.hasLocalPatch(p));
        return reader.observeType(p.getPatchType().name(),p.getLocation(),p.getName(),
            p.getPatchType()==PatchType.HESPORI ? null : Waypoints.destination(p));
    }

    private boolean selected(FarmRunPatch p)
    {return ManualSelection.includes(p,run,manualKeys)&&enabled(p);}
    private boolean eligible(FarmRunPatch p){return selected(p)&&accessNote(p).isEmpty();}
    private List<FarmRunPatch> localCrops(LocationContext location)
    {
        List<FarmRunPatch> local=new ArrayList<>();
        for(FarmRunPatch p:patches)
        {
            PatchType type=p.getPatchType();
            if(type!=PatchType.BIRDHOUSE&&type!=PatchType.COMPOST_BIN&&reader.inPatchRegion(type.name(),p.getLocation(),p.getName(),location.normalized))local.add(p);
        }
        return local;
    }
    private void update()
    {
        if(run.isEmpty()||!guideRequested)return;
        refreshRunPreferences();
        // A loading scene is not evidence that the active itinerary is empty.
        if(hopping||client.getGameState()==GameState.LOADING)return;
        String title=runLabel;
        boolean loggedIn = client.getLocalPlayer()!=null && client.getGameState()==GameState.LOGGED_IN;
        LocationContext location=LocationContext.capture(client);
        int level = client.getRealSkillLevel(Skill.FARMING);
        if(loggedIn&&workflow==Preparation.Workflow.UNCHECKED&&"manual".equals(runPolicyKey)&&startAt()==Preparation.StartAt.GUILD&&level>=45)
        {
            beginPreparation(Preparation.Workflow.GUILD_ARRIVAL);
        }
        if(workflow==Preparation.Workflow.GUILD_ARRIVAL&&Preparation.atGuild(location.normalized))workflow=Preparation.Workflow.GUILD_SEEDLINGS;
        PhysicalStock supplies=stock();
        Map<Integer,Integer> bag=supplies.bag,equipped=supplies.equipped,carried=supplies.carried,plantingBag=supplies.plantingBag;
        // Run this before normal planning, even during banking or birdhouses. No
        // mutations to the normal guide cursor, progress, or preparation state.
        Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations=new HashMap<>();
        List<FarmRunPatch> local=loggedIn&&highlight!=null?localCrops(location):List.of();
        if(preparing())aheadProgress.clear();
        else aheadProgress.keySet().retainAll(local);
        List<FarmRunPatch> localWater=new ArrayList<>();
        // Observe all selected, verified local plots, even if the player waters
        // ahead of the guide. Only live evidence can settle or reset a crop.
        for(FarmRunPatch p:local)
        {
            if(!p.getPatchType().canBeWatered()||!selected(p)||runProgress.is(p,RunProgress.Status.SKIPPED))continue;
            if(!highlight.hasLocalPatch(p))continue;
            observeWater(p,observations.computeIfAbsent(p,this::observe));
            localWater.add(p);
        }
        if(waterOfferPatch!=null)
        {
            if(waterOfferPatch!=current||preparing()||!runWater)clearWaterOffer();
            else if(CropCare.offerExpired(client.getTickCount()-waterOfferTick,
                waterOfferOrigin==null||location.actual==null?Integer.MAX_VALUE:waterOfferOrigin.distanceTo(location.actual)))
                settleWater(waterOfferPatch);
        }
        if(loggedIn&&showDiseaseInterrupt(title,supplies,location,observations,local)){clearWaterOffer();return;}
        List<FarmRunPatch> eligible=new ArrayList<>();
        if(loggedIn)for(FarmRunPatch patch:patches)if(eligible(patch))eligible.add(patch);
        Map<Integer,Integer> storage=supplies.storage,stock=supplies.stock;
        int estimatedOccupied=Preparation.slots(bag,Map.of(),this::stackable);
        int wateringCan=CropCare.wateringCan(bag,this::observedItemName);
        int storedCan=loggedIn&&runLeprechaun?CropCare.storedCan(client,this::observedItemName):-1;
        boolean protect=runPolicy.protection, pay=runPayRemoval;
        Map<Integer,Integer> aliases=supplies.aliases;
        CompostChoice compost=compostChoice();
        if(runPolicy.compost&&compost==CompostChoice.NONE)compost=CompostChoice.BOTTOMLESS;
        int storedUltra=loggedIn&&runPolicy.compost&&runLeprechaun?CompostChoice.stored(client,ItemID.BUCKET_ULTRACOMPOST):0;
        int storedBucket=loggedIn&&runPolicy.compost&&runLeprechaun?CompostChoice.stored(client,ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED):0;
        int compostId=runPolicy.compost?compost.plan(bag,stock,storedUltra):-1;
        if(runPolicy.compost&&(compost==CompostChoice.BOTTOMLESS||compost==CompostChoice.ULTRA)&&storedBucket>0)compostId=ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED;
        int storedCompost = compostId == ItemID.BUCKET_ULTRACOMPOST ? storedUltra :
            compostId == ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED ? storedBucket :
            loggedIn && runLeprechaun && runPolicy.compost ? CompostChoice.stored(client,compostId) : 0;
        List<FarmRunPatch> ready=new ArrayList<>(), plant=new ArrayList<>(), visible=new ArrayList<>();
        int contract=loggedIn?client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_TYPE):0;
        Crop contractCrop=preferContracts()?CropCatalog.contractCrop(contract):null;
        boolean livingContract=contractCrop!=null&&patches.stream().anyMatch(p->
            "Farming Guild".equals(p.getLocation())&&p.getPatchType()==contractCrop.getPatchType()&&
            QuestFarmingStateReader.isLivingContractCrop(p.getPatchType().name(),observations.computeIfAbsent(p,this::observe),contract));
        Crop preferredContract=livingContract?null:contractCrop;
        int spiritSlots=spiritCapacity(level);
        if(loggedIn && run.contains(FarmRunType.SPECIALTY) && level<99)
            for(FarmRunPatch p:patches) if(p.getPatchType()==PatchType.SPIRIT_TREE)
            { CropState state=observations.computeIfAbsent(p,this::observe).state; if(state!=null && state!=CropState.EMPTY && state!=CropState.DEAD) spiritSlots--; }
        for(FarmRunPatch p:eligible)
        {
            PatchType type=p.getPatchType();
            runProgress.include(p);
            if(!SpecialtyStages.includes(specialtyStaged,specialtyBirdPhase,type)||runProgress.is(p,RunProgress.Status.SKIPPED))continue;
            QuestFarmingStateReader.Observation o=observations.computeIfAbsent(p,this::observe);
            if(o==null) continue;
            if (type == PatchType.BIRDHOUSE || type == PatchType.COMPOST_BIN)
            {
                if (!runProgress.finished(p))
                {
                    if (type == PatchType.BIRDHOUSE && p == current && o.source == QuestFarmingStateReader.Source.LIVE)
                    {
                        if (o.rawValue >= 0 && !BirdhouseRun.seeded(o.rawValue)) birdhouseTouched.add(p);
                        if (BirdhouseRun.seeded(o.rawValue) && birdhouseTouched.contains(p)) runProgress.finish(p,false);
                    }
                    if (!runProgress.finished(p))
                    {
                        if (type == PatchType.BIRDHOUSE ? o.state != CropState.GROWING : actionable(type,o.state)) ready.add(p);
                        else if (type == PatchType.COMPOST_BIN && p == current) runProgress.finish(p,false);
                        else continue;
                    }
                }
                visible.add(p);
                continue;
            }
            // Verified loss supersedes historical completion/preservation, not an explicit skip.
            boolean reopened=runProgress.reopenForLiveLoss(p,o);
            if(reopened&&p==current){progress.reset();activeCrop=null;compostAcknowledged=false;}
            if (p == current && !preparing())
            {
                if(progress.startsNewCycle(o))compostAcknowledged=false;
                if(progress.observe(o)&&o.source==QuestFarmingStateReader.Source.LIVE&&o.state==CropState.DEAD)
                {compostAcknowledged=false;activeCrop=null;}
            }
            if(reopened){wateredThisRun.remove(p);if(waterOfferPatch==p)clearWaterOffer();}
            GuideProgress ahead=null;
            if(p!=current)
            {
                if(!preparing()&&o.source==QuestFarmingStateReader.Source.LIVE&&local.contains(p)&&highlight.hasLocalPatch(p)&&!runProgress.finished(p))
                {ahead=aheadProgress.computeIfAbsent(p,k->new GuideProgress());ahead.observe(o);}
                else aheadProgress.remove(p);
            }
            KeepCrop species=KeepCrop.observed(type,o);
            boolean preserveCrop=resolveReplacement(p,species);
            // Preserve selected flower species without picking them; newly planted flowers may finish care.
            if((type==PatchType.FLOWER && species!=null && preserveCrop && !(p==current&&progress.planted())) ||
                ("Farming Guild".equals(p.getLocation()) && QuestFarmingStateReader.isLivingContractCrop(type.name(),o,contract)))
            {runProgress.finish(p,true);continue;}
            if(runProgress.finished(p)) { visible.add(p); continue; }
            if(ahead!=null&&ahead.planted()&&o.state==CropState.GROWING)
            {
                Crop planted=observedCrop(type,o);
                boolean payment=protect&&!immune(p)&&type.supportsProtection()&&planted!=null&&!CropCatalog.protection(planted).isEmpty()&&
                    CropCatalog.protection(planted).stream().allMatch(cost->ProtectionPayment.carried(cost,bag,this::itemDefinition));
                if(!ahead.finished(type,o,payment,payment))
                {ready.add(p);visible.add(p);continue;}
                runProgress.finish(p,false);visible.add(p);aheadProgress.remove(p);continue;
            }
            if(p==current&&!preparing())
            {
                if(activeCareOnly)
                {
                    // A living crop remains a care-only detour. Verified death must
                    // re-enter ordinary planning, subject to the selected seedless policy.
                    if(o.source==QuestFarmingStateReader.Source.LIVE&&o.state==CropState.DEAD)
                        activeCareOnly=false;
                    else continue;
                }
                if(hideSeedless&&clearSeedless&&activeClearOnly&&o.source==QuestFarmingStateReader.Source.LIVE&&o.state==CropState.EMPTY)
                {runProgress.finish(p,false);visible.add(p);continue;}
                boolean wants=protect && !immune(p) && activeCrop!=null && type.supportsProtection() && !CropCatalog.protection(activeCrop).isEmpty();
                boolean hasPayment=wants && CropCatalog.protection(activeCrop).stream().allMatch(cost -> ProtectionPayment.carried(cost,bag,this::itemDefinition));

                // The player may carry a lower-ranked seed than the initially allocated bank crop.
                // Prefer a usable carried replacement for the active mature patch over preserving it prematurely.
                boolean mature=CropReplacement.configurable(type)&&CropReplacement.mature(type,o);
                if(mature&&!preserveCrop&&!progress.planted()&&
                    (activeCrop==null||plantingBag.getOrDefault(activeCrop.getItemId(),0)<activeCrop.getQuantity())&&
                    !(preferredContract!=null&&"Farming Guild".equals(p.getLocation())&&type==preferredContract.getPatchType()&&
                        level>=preferredContract.getLevel()&&supplies.allocation.getOrDefault(preferredContract.getItemId(),0)>=preferredContract.getQuantity()))
                {
                    Crop carriedReplacement=SeedChoices.allocate(List.of(p),level,plantingBag,this::priority,this::override,Collections.emptyMap(),true,this::autoLily).get(p);
                    if(carriedReplacement!=null)activeCrop=carriedReplacement;
                }
                if(mature&&preserveCrop&&o.source==QuestFarmingStateReader.Source.LIVE&&!CropReplacement.actionable(type,o,true))
                {runProgress.finish(p,true);visible.add(p);continue;}
                if(!mature&&progress.finished(type,o,wants,hasPayment,preserveCrop)) { runProgress.finish(p,false); visible.add(p); continue; }
                // Only the active newly planted crop may remain for optional gardener payment.
                if(progress.planted() && o.state==CropState.GROWING && wants && hasPayment)
                { ready.add(p);visible.add(p);continue; }
            }
            if(!CropReplacement.actionable(type,o,preserveCrop)){continue;}
            if(type==PatchType.SPIRIT_TREE && !StepEngine.keepExisting(type,o.state) && spiritSlots<=0) continue;
            ready.add(p);visible.add(p);
            if(!(CropReplacement.configurable(type)?preserveCrop&&CropReplacement.mature(type,o):StepEngine.keepExisting(type,o.state)) && o.state!=CropState.DISEASED) { plant.add(p); if(type==PatchType.SPIRIT_TREE) spiritSlots--; }
        }
        // Plan only from initially actionable patches. A manual run keeps its selected
        // subset for the session, and its remaining order is pinned after the first cluster.
        if(loggedIn && pinned==null)
        {
            WorldPoint start=location.normalized;
            boolean guildFirst=startAt()==Preparation.StartAt.GUILD&&level>=45;
            // Only manual Guild-start runs use the protected itinerary.
            boolean protectedManual=guildFirst&&"manual".equals(runPolicyKey);
            pinned=new RunOrder(RunOrder.shared(ready,start,guildFirst,protectedManual),protectedManual,start);
        }
        if (pinned != null) pinned.order(ready, plant, visible);
        // A selected, local dry crop can be watered without a new planting allocation.
        // Disease is handled independently before the ordinary planner.
        Set<FarmRunPatch> careOnly=new LinkedHashSet<>();
        if(loggedIn&&!preparing()&&highlight!=null&&!(specialtyStaged&&specialtyBirdPhase))
        {
            for(FarmRunPatch p:localWater)
            {
                PatchType type=p.getPatchType();
                if(workflow.guild&&!workflow.external()&&!"Farming Guild".equals(p.getLocation()))continue;
                if(workflow.external()&&current!=null&&!RunOrder.site(current).equals(RunOrder.site(p)))continue;
                // Do not let routine watering pull Fossil Island ahead of remaining mainland work.
                // Verified disease interruptions remain independent and can still interrupt.
                if(SpecialtyStages.fossilLastRequired(runPolicyKey)&&SpecialtyStages.fossil(p)&&ready.stream().anyMatch(other->!SpecialtyStages.fossil(other)))continue;
                QuestFarmingStateReader.Observation o=observations.computeIfAbsent(p,this::observe);
                if(wateredThisRun.contains(p)||immune(p)||!CropCare.waterNeeded(type,o,runWater))continue;
                if(ready.contains(p)&&!(p==current&&activeCareOnly))continue;
                plant.remove(p);ready.remove(p);visible.remove(p);
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
        List<?> inputs=List.of(List.copyOf(plant),level,supplies,preferenceRevision.get(),committed,hideSeedless,temporaryReplacement,contract,Optional.ofNullable(preferredContract));
        if(!inputs.equals(allocationInputs))
        {
            allocated=SeedChoices.allocate(plant,level,supplies.allocation,this::priority,this::override,committed,hideSeedless,this::autoLily,preferredContract);
            allocationInputs=inputs;
        }
        Map<FarmRunPatch,Crop> seeds=allocated;
        Set<FarmRunPatch> seedless=new HashSet<>();
        Set<FarmRunPatch> clearOnly=new HashSet<>();
        if(hideSeedless)
        {
            seedless.addAll(plant);
            seedless.removeAll(seeds.keySet());
            if(clearSeedless)
            {
                for(FarmRunPatch p:seedless)if(ClearOnly.eligible(observations.get(p).state)||CropReplacement.mature(p.getPatchType(),observations.get(p)))clearOnly.add(p);
                seedless.removeAll(clearOnly);
            }
            ready.removeAll(seedless);visible.removeAll(seedless);
        }
        if(loggedIn)runProgress.update(p->SpecialtyStages.includes(specialtyStaged,specialtyBirdPhase,p.getPatchType()),ready,seedless);
        // Transition only after the remaining farming/seaweed work is exhausted.
        // A fresh pinned order is necessary because birdhouses were deliberately absent.
        if(loggedIn&&specialtyStaged&&!specialtyBirdPhase&&ready.isEmpty()&&run.contains(FarmRunType.BIRDHOUSE))
        {
            seaweedBirdTransfer=SpecialRoutes.insideSeaweed(location.normalized)||SpecialRoutes.onSeaweedIsland(location.normalized)||
                patches.stream().anyMatch(p->p.getPatchType()==PatchType.SEAWEED&&runProgress.is(p,RunProgress.Status.COMPLETED));
            specialtyBirdPhase=true;clearWaterOffer();current=null;activeCrop=null;progress.reset();activeCareOnly=false;activeClearOnly=false;
            workflow=workflow.resume();visitedStorage=false;pinned=null;
            update();return;
        }
        if(loggedIn&&!preparing()&&highlight!=null&&location.actual!=null)
        {
            var activeObservation=observations.get(current);
            boolean currentLive=current!=null&&(progress.underway()||compostAcknowledged||
                activeObservation!=null&&activeObservation.source==QuestFarmingStateReader.Source.LIVE&&
                localDistance(current,location.actual,observations)<=2);
            if("manual".equals(runPolicyKey)&&startAt()==Preparation.StartAt.GUILD&&level>=45)
                RunOrder.preferProtectedLocal(ready,visible,current,currentLive,location.actual,p->localDistance(p,location.actual,observations));
            else RunOrder.preferLocal(ready,visible,current,currentLive,p->localDistance(p,location.actual,observations));
        }
        if(workflow.local()&&!preparing()&&ready.stream().noneMatch(p->"Farming Guild".equals(p.getLocation())))
        {
            if(ready.isEmpty())workflow=Preparation.Workflow.GUILD_EXTERNAL;
            else beginPreparation(Preparation.Workflow.GUILD_DEPARTURE);
        }
        if(workflow.local()&&!preparing()&&!ready.isEmpty()&&flag("compostLocalProduceV112",true))
        {
            FarmRunPatch refill=CompostRun.guildRefillCrop(ready.get(0),ready,observations,compostProduction(),compostMaterial());
            if(refill!=null){ready.remove(refill);ready.add(0,refill);}
        }
        if(!bankItinerary.equals(ready))bankItinerary=List.copyOf(ready);
        FarmRunPatch next=ready.isEmpty()?null:ready.get(0);
        if(next!=current)
        {
            clearWaterOffer();current=next;progress=aheadProgress.remove(next);
            if(progress==null)progress=new GuideProgress();
            compostAcknowledged=false;activeCrop=null;
            if(current!=null)
            {
                var o=observations.get(current);
                progress.observe(o);
                if(progress.planted())activeCrop=observedCrop(current.getPatchType(),o);
            }
        }
        activeClearOnly=clearOnly.contains(current);
        activeCareOnly=careOnly.contains(current);
        if(current!=null && !progress.planted()) activeCrop=seeds.get(current);
        // Keep the complete actionable itinerary independent of inventory capacity.
        ItemContainer inventory=client.getItemContainer(InventoryID.INV);
        int actualOccupied=Math.max(estimatedOccupied,inventory==null?0:inventory.count());
        boolean autoWeed=loggedIn&&client.getVarbitValue(VarbitID.FARMING_BLOCKWEEDS)==2;
        boolean barbarian=loggedIn&&client.getVarbitValue(VarbitID.BRUT_FARMING_PLANTING)==3;
        boolean storedMagic=loggedIn&&runLeprechaun&&LeprechaunWithdrawal.magicStored(client);
        int hunter=client.getRealSkillLevel(Skill.HUNTER),crafting=client.getRealSkillLevel(Skill.CRAFTING);
        String localSite=loggedIn&&current!=null&&location.actual!=null&&highlight!=null&&localDistance(current,location.actual,observations)<70?RunOrder.site(current):null;
        Map<Integer,List<String>> travelOptions=loggedIn&&config!=null&&itemManager!=null&&!ready.isEmpty()?teleports(supplies.travel.keySet()):Map.of();
        int pouch=RouteSupplies.pouch(carried,supplies.travel);
        Map<String,List<String>> bankRouteSnapshot=bankRoutes.routes();
        Map<Integer,Integer> pouchRunes=!loggedIn||bankRouteSnapshot.isEmpty()?Map.of():RouteSupplies.pouchContents(client,pouch);
        boolean elite=loggedIn&&client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE)==1;
        // Keep later specialty supplies in the complete plan while preserving the
        // existing seaweed exit and birdhouse interaction stage.
        final List<FarmRunPatch> remaining=new ArrayList<>(ready);
        if(specialtyStaged&&!specialtyBirdPhase)for(FarmRunPatch p:eligible)
        {
            if(p.getPatchType()!=PatchType.BIRDHOUSE||runProgress.finished(p))continue;
            var observed=observations.computeIfAbsent(p,this::observe);
            if(observed!=null&&observed.state!=CropState.GROWING)remaining.add(p);
        }
        Map<FarmRunPatch,Crop> remainingSeeds=new LinkedHashMap<>();
        boolean yieldTool=false,needsWater=false,diving=false;
        int vines=0;
        for(FarmRunPatch p:remaining)
        {
            Crop crop=seeds.get(p);PatchType type=p.getPatchType();
            if(crop!=null){remainingSeeds.put(p,crop);if(type==PatchType.GRAPEVINE)vines++;}
            yieldTool|=!careOnly.contains(p)&&Supplies.secateursUseful(type,observations.get(p));
            needsWater|=runWater&&!immune(p)&&type.canBeWatered()&&(crop!=null||!wateredThisRun.contains(p)&&CropCare.waterNeeded(type,observations.get(p),true));
            diving|=type==PatchType.CORAL;
        }
        SupplyLedger payments=new SupplyLedger();
        Map<Integer,Integer> paymentNeeds=payments.total,paymentNotes=new HashMap<>();
        boolean splitGuild=workflow.splitGuild();
        List<FarmRunPatch> guildReady=new ArrayList<>();
        if(splitGuild)for(FarmRunPatch p:remaining)if("Farming Guild".equals(p.getLocation()))guildReady.add(p);
        SupplyLedger demands=new SupplyLedger(Supplies.guideKit(remaining,careOnly,autoWeed,barbarian),Supplies.guideKit(guildReady,careOnly,autoWeed,barbarian));
        Map<Integer,Integer> required=demands.total,guildNeeds=demands.guild;
        int guildCompost=0;
        if(!clearOnly.isEmpty()&&remainingSeeds.isEmpty()) {required.remove(ItemID.DIBBER);required.remove(ItemID.RAKE);}
        if(loggedIn&&runLeprechaun)required.entrySet().removeIf(e->carried.getOrDefault(e.getKey(),0)==0&&LeprechaunWithdrawal.stored(client,e.getKey())>=e.getValue());
        for(FarmRunPatch p:remaining)
        {
            QuestFarmingStateReader.Observation state=observations.get(p);
            if(state!=null && state.state==CropState.DISEASED)
            {
                if(CropCare.prunes(p.getPatchType()))
                    required.put(carried.containsKey(ItemID.FAIRY_ENCHANTED_SECATEURS)?ItemID.FAIRY_ENCHANTED_SECATEURS:ItemID.SECATEURS,1);
                else required.merge(ItemID.PLANT_CURE,1,Integer::sum);
            }
        }
        int magicSecateurs=ItemID.FAIRY_ENCHANTED_SECATEURS;
        // A yield bonus is advisory, not a prerequisite for planting or harvesting.
        // Known bank stock is shown in the normal checklist; unknown or leprechaun
        // stock must not create a mandatory banking route.
        if(yieldTool&&(carried.getOrDefault(magicSecateurs,0)>0||!storedMagic&&stock.getOrDefault(magicSecateurs,0)>0))required.put(magicSecateurs,1);
        Map<FarmRunPatch,CompostRun.Plan> binPlans=new HashMap<>();
        int compostCount=0,axeId=0;boolean needsAxe=false;
        for(FarmRunPatch p:remaining)
        {
            Crop c=seeds.get(p);PatchType type=p.getPatchType();
            boolean guildPatch=splitGuild&&"Farming Guild".equals(p.getLocation());
            if(c!=null)demands.consume(c.getItemId(),c.getQuantity(),guildPatch);
            if(CropCare.usesCompost(c,immune(p))){compostCount++;if(guildPatch)guildCompost++;}
            if((c!=null||clearOnly.contains(p))&&type.isTree()&&type!=PatchType.CRYSTAL_TREE&&type!=PatchType.CALQUAT)
            {
                var o=observations.get(p);
                if(o!=null&&(o.state==null||CropReplacement.mature(type,o)))
                {int cost=type==PatchType.REDWOOD?2000:pay&&reader.gardenerId(type.name(),p.getLocation(),p.getName())>0?200:0;
                    if(cost>0)demands.consume(ItemID.COINS,cost,guildPatch);else needsAxe=true;}
            }
            if(protect && (c!=null || (p==current && progress.planted() && activeCrop!=null)))
            {
                Crop paymentCrop=c!=null?c:activeCrop;
                if(!immune(p)&&type.supportsProtection()) for(ProtectionPayment cost:CropCatalog.protection(paymentCrop))
                    payments.consume(cost.getItemId(),cost.getQuantity(),guildPatch);
            }
        }
        // Reserve actual crop and gardener-payment materials before choosing bin produce.
        if(remaining.stream().anyMatch(p->p.getPatchType()==PatchType.COMPOST_BIN))
        {
            Map<Integer,Integer> compostStock=new HashMap<>(stock),compostBag=new HashMap<>(bag);
            paymentNeeds.forEach((id,n)->{compostStock.computeIfPresent(id,(k,v)->Math.max(0,v-n));compostBag.computeIfPresent(id,(k,v)->Math.max(0,v-n));});
            CompostRun.addSupplies(required,remaining,observations,compostStock,compostBag,binPlans,compostProduction(),compostMaterial(),flag("compostLocalProduceV112",true));
        }
        if(splitGuild)for(FarmRunPatch p:guildReady)if(binPlans.containsKey(p))binPlans.get(p).supplies.forEach((id,n)->guildNeeds.merge(id,n,Integer::sum));
        if(needsAxe){Map<Integer,Integer> axeStock=new HashMap<>(stock);equipped.forEach((id,n)->axeStock.merge(id,n,Integer::sum));int axe=TreeAxes.best(axeStock,client.getRealSkillLevel(Skill.WOODCUTTING),this::observedItemName);axeId=axe>0?axe:1351;required.put(axeId,1);}
        if(treeStock!=supplies||!treeAllocation.equals(remainingSeeds))
        {
            treePlan=TreePreparation.plan(remainingSeeds,stock,aliases,bag,this::observedItemName);
            treeStock=supplies;treeAllocation=Map.copyOf(remainingSeeds);
        }
        Map<Integer,Integer> prepSeeds=treePlan.materials;
        var stages=treePlan.stages;
        int rawSeeds=stages.getOrDefault(TreePreparation.Stage.SEED,0),seedlings=stages.getOrDefault(TreePreparation.Stage.SEEDLING,0),
            watered=stages.getOrDefault(TreePreparation.Stage.WATERED,0);
        prepSeeds.forEach((id,n)->required.merge(id,n,Integer::sum));
        if(rawSeeds>0)
        {
            required.put(TreePreparation.FILLED_POT,rawSeeds);
            required.merge(Supplies.GARDENING_TROWEL,1,Math::max);
        }
        boolean needsCan=rawSeeds+seedlings>0||needsWater;
        if(needsCan&&wateringCan<=0&&storedCan<=0)
            required.put(CropCare.preferredCan(wateringCan,storedCan,CropCare.wateringCan(stock,this::observedItemName)),1);
        if(vines>0){required.merge(Supplies.SALTPETRE,vines,Integer::sum);required.put(Supplies.GARDENING_TROWEL,1);}
        int storedEmptyBuckets=loggedIn&&runLeprechaun&&!binPlans.isEmpty()?LeprechaunWithdrawal.stored(client,CompostRun.BUCKET):0;
        if(storedEmptyBuckets>0)required.computeIfPresent(CompostRun.BUCKET,(id,n)->Math.max(0,n-storedEmptyBuckets));
        if(loggedIn&&runLeprechaun)
        {
            for(int tool:new int[]{Supplies.GARDENING_TROWEL,ItemID.PLANT_CURE,ItemID.SECATEURS})
                if(carried.getOrDefault(tool,0)==0&&required.getOrDefault(tool,0)>0&&LeprechaunWithdrawal.stored(client,tool)>=required.get(tool))required.remove(tool);
        }
        required.values().removeIf(n->n<=0);
        Set<Integer> preparationIds=new HashSet<>(prepSeeds.keySet());
        if(rawSeeds>0){preparationIds.add(TreePreparation.FILLED_POT);preparationIds.add(Supplies.GARDENING_TROWEL);}
        if(rawSeeds+seedlings>0&&needsCan&&wateringCan<=0&&storedCan<=0)preparationIds.add(CropCare.preferredCan(wateringCan,storedCan,CropCare.wateringCan(stock,this::observedItemName)));
        Map<Integer,Integer> wateredCarried=treePlan.wateredCarried;
        if(workflow==Preparation.Workflow.GUILD_SEEDLINGS&&rawSeeds+seedlings==0&&wateredCarried.isEmpty()&&(!runHasTrees||bank.at>0&&vault.at>0))
        {
            workflow=Preparation.Workflow.GUILD_LOCAL;
            if(ready.stream().anyMatch(p->"Farming Guild".equals(p.getLocation())))beginPreparation(Preparation.Workflow.GUILD_LOCAL_PREP);
            current=null;activeCrop=null;progress.reset();update();return;
        }
        if(compostId!=ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED||storedBucket==0)
            CompostChoice.addRequired(required,compostId,compostId==ItemID.BUCKET_ULTRACOMPOST?compostCount:Math.max(0,compostCount-storedCompost),storedUltra,stock,bag);
        if(diving)DivingKit.require(required,supplies.travel,carried);
        Map<Integer,Integer> farmingRequirements=SpecialRoutes.isType(current,PatchType.BIRDHOUSE)?new LinkedHashMap<>(required):Map.of();
        Map<Integer,Integer> birdStock=specialtyStaged&&specialtyBirdPhase?supplies.bankAndBag:stock;
        if(specialtyStaged&&specialtyBirdPhase)
            birdStock=BirdhouseRun.preferCarried(remaining,observations,plantingBag,birdStock,required,hunter,crafting);
        Map<FarmRunPatch,String> birdhousePlans=BirdhouseRun.supplies(remaining,observations,birdStock,required,hunter,crafting);
        ProtectionPayment.reserve(required,paymentNeeds,bag,this::itemDefinition,paymentNotes,payments.guild,guildNeeds);
        String harvestContainers=harvestContainerRequirements(remaining,observations,required);
        if(splitGuild&&!guildReady.isEmpty())
        {
            if(compostId!=ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED||storedBucket==0)
                CompostChoice.addRequired(guildNeeds,compostId,guildCompost==0?0:compostId==ItemID.BUCKET_ULTRACOMPOST?guildCompost:Math.max(0,guildCompost-storedCompost),storedUltra,stock,bag);
            for(int id:required.keySet())if(SupplyOrder.tool(observedItemName(id))||observedItemName(id).toLowerCase(Locale.ROOT).contains("sack")||observedItemName(id).toLowerCase(Locale.ROOT).contains("pouch"))guildNeeds.putIfAbsent(id,1);
        }
        Map<Integer,Integer> pendingSaplings=treePlan.pending;
        Map<Integer,Integer> physicalRequired=pendingSaplings.isEmpty()?required:TreePreparation.physicalRequirements(required,pendingSaplings);
        java.util.function.IntPredicate boxEligible=id->seedItem(id)&&stackable(id)&&!prepSeeds.containsKey(id)&&id!=ItemID.HESPORI_SEED;
        int requiredBox=boxEnabled?SeedBoxStock.requiredBox(physicalRequired,bag,bank.items,storage,supplies.seedBox,seedBox.complete(),boxEligible):-1;
        if(requiredBox>0){demands.reuse(requiredBox,1,!guildReady.isEmpty());physicalRequired.put(requiredBox,1);}
        List<Integer> prepPriority=new ArrayList<>();
        if(preparing()&&(rawSeeds+seedlings>0||workflow==Preparation.Workflow.GUILD_SEEDLINGS))
            for(int id:SupplyOrder.ordered(required,this::observedItemName))if(preparationIds.contains(id))prepPriority.add(id);
        preparationBankPriority=List.copyOf(prepPriority);
        List<Integer> containers=new ArrayList<>();
        if(requiredBox>0)containers.add(requiredBox);
        for(int id:required.keySet())if(id!=requiredBox&&(id==DivingKit.HELMET||id==DivingKit.APPARATUS||id==DivingKit.MEDALLION||observedItemName(id).toLowerCase(Locale.ROOT).contains("herb sack")||observedItemName(id).toLowerCase(Locale.ROOT).contains("reagent pouch")))containers.add(id);
        containerBankPriority=List.copyOf(containers);
        Map<Integer,Integer> paymentStorage=paymentNotes.isEmpty()?storage:new HashMap<>(storage);
        paymentNotes.forEach((note,base)->paymentStorage.merge(note,
            Math.max(0,bank.items.getOrDefault(base,0)-Math.max(0,physicalRequired.getOrDefault(base,0)-bag.getOrDefault(base,0))),PhysicalStock::sum));
        // Travel remains advisory; do not force a bank trip solely for optional teleports.
        List<FarmRunPatch> travelRemaining=remaining;
        if(localSite!=null){travelRemaining=new ArrayList<>(remaining);travelRemaining.removeIf(p->localSite.equals(RunOrder.site(p)));}
        Set<String> travelSites=new LinkedHashSet<>();
        for(FarmRunPatch patch:travelRemaining)travelSites.add(RunOrder.site(patch));
        List<?> routeInputs=List.of(List.copyOf(travelSites),bankRouteSnapshot,supplies,travelOptions,pouch,pouchRunes,elite);
        if(!routeInputs.equals(routeSupplyInputs))
        {
            Map<Integer,Set<String>> fallbackTravel=RunTeleports.selectSites(travelSites,travelOptions,carried,supplies.travel,
                (id,visits)->RunTeleports.quantity(id,visits,observedItemName(id),stackable(id)));
            routeSupplyPlan=RouteSupplies.plan(travelSites,bankRouteSnapshot,fallbackTravel,travelOptions,supplies.travel,carried,equipped,
                pouch,pouchRunes,!loggedIn||bankRouteSnapshot.isEmpty()?Map.of():RouteSupplies.looseRunes(client,supplies.travel),elite,this::observedItemName,this::stackable);
            routeSupplyInputs=routeInputs;
        }
        Map<Integer,Set<String>> teleports=routeSupplyPlan.sites;
        Map<Integer,Integer> travelCounts=routeSupplyPlan.quantities,load=new LinkedHashMap<>(physicalRequired);
        travelCounts.forEach((id,n)->load.merge(id,n,Math::max));
        if(remaining.size()==1&&remaining.get(0).getPatchType()==PatchType.COMPOST_BIN)
        {
            CompostRun.batch(load,bag,equipped,supplies.seedBox,actualOccupied,this::stackable);
            // Only the immediate feasible load is batched; full requirements remain visible.
        }
        int looseSlots=Preparation.loadedSlots(actualOccupied,bag,load,equipped,supplies.seedBox,this::stackable);
        Map<Integer,Integer> packing=boxEnabled&&SeedBoxStock.boxId(bag)>0&&seedBox.complete()&&!seedBox.pending()&&seedBoxPlant==null
            ?SeedBoxStock.packing(physicalRequired,bag,storage,supplies.seedBox,true,
                boxEligible)
            :Map.of();
        int packed=Preparation.packedSlots(actualOccupied,bag,load,equipped,supplies.seedBox,packing,this::stackable);
        boolean requiresPacking=packed<looseSlots;
        int slots=requiresPacking?packed:looseSlots;
        int reserve=Preparation.workingSlots(remaining);
        boolean fits=slots<=28-reserve;
        List<GuideView.ItemRow> items=new ArrayList<>(required.size()+teleports.size());
        for(int id:SupplyOrder.ordered(required,this::observedItemName))
        {
            int n=required.get(id);
            if((CompostChoice.isOrdinary(id)||id==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED)&&bag.getOrDefault(id,0)>=n&&binPlans.isEmpty())continue;
            if(!workflow.guild&&prepSeeds.containsKey(id)&&aliases.containsKey(id)&&TreePreparation.stage(id,aliases.get(id),this::observedItemName)==TreePreparation.Stage.WATERED)continue;
            int pending=pendingSaplings.getOrDefault(id,0);
            Integer base=paymentNotes.get(id);
            if(base!=null)
                items.add(new GuideView.ItemRow(id,observedItemName(base)+" (noted — withdraw as note)",
                    bag.getOrDefault(id,0),n,paymentStorage.getOrDefault(id,0),0,false));
            else
            {
                GuideView.ItemRow row=itemRow(id,n,supplies,false,pending>0?" ("+pending+" to prepare)":"");
                if(!workflow.guild&&wateredCarried.containsKey(id))
                    row=new GuideView.ItemRow(id,row.name,Math.min(n,PhysicalStock.sum(row.owned,wateredCarried.get(id))),n,row.bank,row.vault,false);
                items.add(row);
            }
        }
        if(yieldTool&&!required.containsKey(magicSecateurs))
            items.add(itemRow(magicSecateurs,1,supplies,false," (optional yield boost)"));
        Set<Integer> shelfRequirements=new HashSet<>(required.keySet());
        shelfRequirements.addAll(paymentNotes.values());
        if(workflow==Preparation.Workflow.GUILD_SEEDLINGS)shelfRequirements.retainAll(preparationIds);
        requiredItems=Set.copyOf(shelfRequirements);
        Map<Integer,Integer> storageNeeds=demands.withdrawals(physicalRequired,workflow,preparationIds);
        missingStorageItems=GuideView.missingItems(storageNeeds,carried,paymentNotes);
        if(workflow==Preparation.Workflow.GUILD_SEEDLINGS){Set<Integer> prepMissing=new HashSet<>(missingStorageItems);prepMissing.retainAll(preparationIds);missingStorageItems=Set.copyOf(prepMissing);}
        Map<Integer,String> labels=new HashMap<>();
        storageNeeds.forEach((id,n)->{int missing=n-carried.getOrDefault(id,0);if(missing>0&&missingStorageItems.contains(id)){
            int base=paymentNotes.getOrDefault(id,id);String label="Withdraw "+missing+" "+observedItemName(base)+(base==id?"":" (noted)");labels.put(id,label);labels.put(base,label);}});
        withdrawalLabels=Map.copyOf(labels);
        Map<Integer,Integer> plantingRequired=SupplyOrder.plantingRequirements(storageNeeds,this::observedItemName);
        Set<Integer> missingTravel=new HashSet<>();
        teleports.forEach((id,sites)->{
            GuideView.ItemRow row=itemRow(id,travelCounts.get(id),supplies,true," — "+String.join(", ",sites));
            items.add(row);
            if(!row.satisfied()&&row.bank>0)missingTravel.add(id);
        });
        missingTeleports=Set.copyOf(missingTravel);
        bankFallback=teleports;
        List<GuideView.Stop> stops=new ArrayList<>();
        GuideView.Stats summary=runProgress.stats();
        String instruction=!loggedIn?"Log in to start this guide.":summary.total==0?"No enabled, unlocked patches.":summary.deferred>0||summary.seedless>0?"No ready patches remain.":"Run finished.";
        activeAction=StepEngine.Action.INSPECT;
        activeLive=false;compostMissing=false;leprechaunWithdrawal=null;spaceItem=-1;
        Set<Integer> localStepItems=Collections.emptySet();
        if(current!=null)
        {
            PatchType type=current.getPatchType();QuestFarmingStateReader.Observation o=observations.get(current);
            activeLive=o.localGuidance();
            boolean replacementCarried=activeCrop!=null&&plantingBag.getOrDefault(activeCrop.getItemId(),0)>=activeCrop.getQuantity();
            boolean keep=preserveReplacement(current,o);
            int stepCompost=CompostChoice.localItem(compostId,bag,storedUltra);
            int compostTier=0;
            if(activeLive&&CropCare.usesCompost(activeCrop,immune(current))&&!activeClearOnly&&o.state==CropState.EMPTY)
            {
                int liveTier=highlight==null?-1:highlight.liveCompostTier(current);
                compostTier=liveTier>=0?liveTier:reader.compostTier(type.name(),current.getLocation(),current.getName());
            }
            StepEngine.Action action=guidanceAction(type,o,keep,replacementCarried,stepCompost,compostTier);
            activeAction=action;
            if(action!=StepEngine.Action.WATER||preparing())clearWaterOffer();
            else if(waterOfferPatch!=current)
            {waterOfferPatch=current;waterOfferTick=client.getTickCount();waterOfferOrigin=location.actual;}
            if(activeLive&&CropCare.usesCompost(activeCrop,immune(current))&&!activeClearOnly&&(action==StepEngine.Action.COMPOST||action==StepEngine.Action.PLANT))
                compostMissing=compostTier==0;
            instruction=type==PatchType.BIRDHOUSE?birdhousePlans.get(current):type==PatchType.COMPOST_BIN?binPlans.get(current).instruction:
                StepEngine.instruction(action,activeCrop,type,keep,activeClearOnly,o.state==CropState.EMPTY);
            if(activeLive&&!preparing()&&highlight!=null&&highlight.hasLocalPatch(current)&&highlight.leprechaun(current)!=null)lastLocalVisit=current;
            if(type==PatchType.COMPOST_BIN&&storedEmptyBuckets>0&&binPlans.get(current).supplies.getOrDefault(CompostRun.BUCKET,0)>bag.getOrDefault(CompostRun.BUCKET,0))
                instruction="Withdraw empty buckets from the tool leprechaun as needed ("+storedEmptyBuckets+" stored); collect in batches. "+instruction;
            if(activeLive&&!preparing()&&type!=PatchType.BIRDHOUSE&&type!=PatchType.COMPOST_BIN)
                leprechaunWithdrawal=contextualWithdrawal(current,action,stepCompost,bag,carried,wateringCan,needsCan,estimatedOccupied);
            if(activeLive&&!preparing()&&action==StepEngine.Action.HARVEST&&Supplies.secateursUseful(type,o)&&
                carried.getOrDefault(magicSecateurs,0)==0&&storedMagic&&leprechaunWithdrawal==null&&
                highlight!=null&&highlight.hasLocalPatch(current)&&highlight.leprechaun(current)!=null)
            {
                int free=Math.max(0,28-estimatedOccupied);
                if(free>0)leprechaunWithdrawal=LeprechaunWithdrawal.plan(magicSecateurs,false,1,free,"Magic secateurs");
            }
            if(activeLive&&!preparing()&&runLeprechaun&&highlight!=null&&
                (leprechaunWithdrawal!=null||visibleWidget(InterfaceID.FarmingTools.UNIVERSE)))
                leprechaunWithdrawal=localCollection(leprechaunWithdrawal,current,remaining,seeds,observations,bag,carried,plantingBag,wateringCan,stepCompost,estimatedOccupied,barbarian);
            if(leprechaunWithdrawal!=null)
                instruction=leprechaunWithdrawal.instruction()+", then "+instruction;
            else if(action==StepEngine.Action.WATER&&wateringCan<=0)
                instruction="Obtain a filled watering can, then water this patch, or skip.";

            if(activeCareOnly&&action==StepEngine.Action.CURE)instruction="Nearby diseased crop — "+instruction+" Then resume the run without replanting.";
            if(leprechaunWithdrawal==null&&action==StepEngine.Action.CURE&&!CropCare.prunes(type)&&bag.getOrDefault(ItemID.PLANT_CURE,0)==0)instruction+=" Obtain plant cure from a nearby farmer or Farming shop, if available; skip if you cannot cure it.";
            if(activeLive)
            {
                if(type==PatchType.COMPOST_BIN)localStepItems=CompostRun.stepItems(current,o,binPlans.get(current),bag);
                else if(type==PatchType.BIRDHOUSE)localStepItems=BirdhouseRun.stepItems(current,o,birdStock,plantingBag,farmingRequirements,hunter,crafting);
                else if(action==StepEngine.Action.WATER&&wateringCan>0)localStepItems=Set.of(wateringCan);
                else if(leprechaunWithdrawal==null)
                {
                    localStepItems=StepItems.select(action,type,activeCrop,stepCompost,axeId,bag);
                    if(action==StepEngine.Action.PROTECT_TREE)
                    {
                        Set<Integer> actualPayments=new HashSet<>(localStepItems);
                        for(ProtectionPayment cost:CropCatalog.protection(activeCrop))
                        {
                            int note=ProtectionPayment.noteId(cost.getItemId(),this::itemDefinition);
                            if(note>0&&bag.getOrDefault(note,0)>0)actualPayments.add(note);
                        }
                        localStepItems=Set.copyOf(actualPayments);
                    }
                }
            }
            if(leprechaunWithdrawal==null&&activeLive&&SeedBoxStock.boxId(bag)>0)
            {
                if(action==StepEngine.Action.PLANT&&activeCrop!=null&&
                    bag.getOrDefault(activeCrop.getItemId(),0)<activeCrop.getQuantity()&&supplies.seedBox.getOrDefault(activeCrop.getItemId(),0)>0)
                {
                    localStepItems=Set.of(SeedBoxStock.boxId(bag));
                    instruction=seedBox.orderKnown()&&SeedBoxStock.direct(activeCrop,supplies.seedBox)?
                        "Use the seed box on this patch to plant "+activeCrop.getName()+".":
                        "Withdraw "+activeCrop.getName()+" from the seed box manually before planting.";
                }
                else if(type==PatchType.BIRDHOUSE&&localStepItems.stream().anyMatch(id->supplies.seedBox.getOrDefault(id,0)>0&&bag.getOrDefault(id,0)<10))
                {localStepItems=Set.of(SeedBoxStock.boxId(bag));instruction+=" Withdraw the required bait seeds from the seed box manually.";}
            }
            if(activeLive&&!preparing()&&runLeprechaun&&highlight!=null&&itemManager!=null&&
                (action==StepEngine.Action.HARVEST||leprechaunWithdrawal!=null)&&type!=PatchType.HESPORI&&
                type!=PatchType.BIRDHOUSE&&type!=PatchType.COMPOST_BIN&&highlight.hasLocalPatch(current)&&highlight.leprechaun(current)!=null)
            {
                int free=Math.max(0,28-actualOccupied);
                boolean localBin=ready.stream().anyMatch(p->p.getPatchType()==PatchType.COMPOST_BIN&&RunOrder.site(p).equals(RunOrder.site(current)));
                var produce=QuestFarmingStateReader.livingProduce(type.name(),o);
                boolean existingStack=produce!=null&&bag.getOrDefault(produce.getItemID(),0)>0&&stackable(produce.getItemID());
                var kind=type==PatchType.HERB?HarvestContainers.Kind.HERB:HarvestContainers.Kind.REAGENT;
                var container=carriedHarvestContainers.get(kind);
                boolean canTryContainer=action==StepEngine.Action.HARVEST&&leprechaunWithdrawal==null&&container!=null&&container.open&&
                    useHarvestContainer(kind)&&HarvestContainers.eligible(kind,type,produce);
                if(free==0&&canTryContainer)
                    instruction="Harvest into your open "+container.name+" if it has room; otherwise note produce or free a slot. "+instruction;
                else if(free==0&&((action==StepEngine.Action.HARVEST&&!existingStack)||leprechaunWithdrawal!=null&&leprechaunWithdrawal.quantity==0))
                {
                    Map<Integer,Integer> reserved=required;
                    if(localBin)
                    {
                        reserved=new HashMap<>(required);
                        for(int id:CompostRun.REGULAR)reserved.putIfAbsent(id,1);
                        for(int id:CompostRun.SUPER)reserved.putIfAbsent(id,1);
                    }
                    int note=HarvestSpace.best(bag,reserved,current.getLocation(),itemManager::getItemComposition);
                    if(note>0)
                    {
                        instruction="Use "+observedItemName(note)+" on the tool leprechaun to note it and free space, then "+instruction;
                        spaceItem=note;localStepItems=Set.of(note);
                    }
                    else
                    {
                        int buckets=localBin?0:HarvestSpace.depositBuckets(bag,required,client.getVarbitValue(VarbitID.FARMING_TOOLS_BUCKETS),
                            client.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRABUCKETS),client.getVarbitValue(VarbitID.FARMING_TOOLS_EXTRA2BUCKETS));
                        if(buckets>0)
                        {
                            instruction="Store "+buckets+" empty bucket(s) with the tool leprechaun to free space, then "+instruction;
                            spaceItem=CompostRun.BUCKET;localStepItems=Set.of(spaceItem);
                        }
                        else if(free==0&&leprechaunWithdrawal==null)instruction="Inventory full — free a slot before harvesting. "+instruction;
                    }
                }
            }
            if(type==PatchType.BIRDHOUSE){activeAction=StepEngine.Action.INSPECT;instruction=current.getName()+": "+instruction;}
        }
        for(FarmRunPatch p:visible)
        {
            Crop c=seeds.get(p);
            String line=p==current?instruction:careOnly.contains(p)?(observations.get(p).state==CropState.DISEASED?"Nearby disease — cure only.":"Water the growing crop."):runProgress.finished(p)?(runProgress.is(p,RunProgress.Status.PRESERVED)?"Preserved":"Completed"):birdhousePlans.containsKey(p)?birdhousePlans.get(p):binPlans.containsKey(p)?binPlans.get(p).instruction:clearOnly.contains(p)?"Check health / harvest and clear; no replacement.":c==null?"Check or harvest existing crop.":"Harvest / clear, then plant "+c.getName()+".";
            stops.add(new GuideView.Stop(p,line,p==current,runProgress.finished(p)&&!careOnly.contains(p)));
        }
        // Show the selected birdhouse leg at the bottom; full requirements include its supplies.
        if(loggedIn&&specialtyStaged&&!specialtyBirdPhase&&run.contains(FarmRunType.BIRDHOUSE))
        {
            List<FarmRunPatch> birds=new ArrayList<>();
            for(FarmRunPatch p:eligible)if(p.getPatchType()==PatchType.BIRDHOUSE&&!runProgress.is(p,RunProgress.Status.SKIPPED))birds.add(p);
            birds.sort(Comparator.comparingInt(BirdhouseRun::order));
            for(FarmRunPatch p:birds)stops.add(new GuideView.Stop(p,"Birdhouses — after farming.",false,false));
        }
        WorldPoint player=loggedIn?location.normalized:null;
        boolean instance=loggedIn&&location.instance;
        boolean aboard=loggedIn && client.getVarbitValue(VarbitID.SAILING_BOARDED_BOAT)!=0;
        boolean rowboat=loggedIn && client.getVarbitValue(SpecialRoutes.ANGLER_ROWBOAT_VARBIT)==1;
        WorldPoint localApproach=current!=null && activeLive && highlight!=null?highlight.approach(current):null;
        String supplyNote=TreePreparation.instruction(rawSeeds,seedlings,watered);
        if(requiredBox>0&&SeedBoxStock.boxId(bag)>0)
        {
            if(!seedBox.complete())
                supplyNote+=(supplyNote.isEmpty()?"":"\n")+(seedBox.cachedTotal()>0&&!seedBox.known()?
                    "Seed box: "+seedBox.cachedTotal()+" last seen; check to confirm.":"Check the box to confirm any untracked contents.");
            else if(!supplies.seedBox.isEmpty())
                supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Seed box counts included. Withdraw tree seeds and birdhouse bait manually when needed.";
        }
        if(requiresPacking)
        {
            StringJoiner packedNames=new StringJoiner(", ");
            for(var e:packing.entrySet())if(e.getValue()>supplies.seedBox.getOrDefault(e.getKey(),0))packedNames.add(observedItemName(e.getKey()));
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Fill seed box manually with "+packedNames+" (projected "+(looseSlots-slots)+" fewer slots).";
        }
        else if(requiredBox>0&&SeedBoxStock.boxId(bag)<0)
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Withdraw the seed box and check its contents before packing eligible seeds.";
        if(!harvestContainers.isEmpty())supplyNote+=(supplyNote.isEmpty()?"":"\n")+harvestContainers;
        if(yieldTool&&storedMagic&&carried.getOrDefault(magicSecateurs,0)==0)
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Magic secateurs stored with tool leprechauns; carry or equip for the optional harvest bonus.";
        if(rawSeeds>0&&runLeprechaun&&LeprechaunWithdrawal.stored(client,Supplies.GARDENING_TROWEL)>0&&bag.getOrDefault(Supplies.GARDENING_TROWEL,0)==0)
            supplyNote+="\nCollect the stored gardening trowel from a tool leprechaun.";
        if(!preparing()&&runLeprechaun&&lastLocalVisit!=null&&current!=null&&!RunOrder.site(lastLocalVisit).equals(RunOrder.site(current))&&highlight!=null&&highlight.leprechaun(lastLocalVisit)!=null)
            supplyNote+="\nBefore leaving this area, return surplus farming tools and compost to the nearby leprechaun; keep tools needed at destinations without accessible storage.";
        if(rawSeeds+seedlings>0&&wateringCan<=0&&storedCan>0)supplyNote+="\nCollect the stored watering can from a tool leprechaun before watering seedlings.";
        if(compostCount>0&&compostId==ItemID.BUCKET_ULTRACOMPOST&&storedUltra>0)
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Ultracompost: "+storedUltra+" stored with tool leprechauns; withdraw as needed.";
        if(compostCount>0&&(compostId==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED||required.containsKey(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED)))
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Check the bottomless bucket's compost type and charges.";
        StringJoiner details=new StringJoiner("\n");
        long now=System.currentTimeMillis();
        boolean bankStale=GuidePresentation.stale(bank.at,now);
        boolean vaultStale=GuidePresentation.stale(vault.at,now);
        String storageWarning=loggedIn?GuidePresentation.storageWarning(bankStale,level>=45&&!birdBankPhase()&&vaultStale):"";
        String prepHelp=GuidePresentation.PREPARATION_HELP;
        if(specialtyStaged&&!specialtyBirdPhase&&run.contains(FarmRunType.BIRDHOUSE))
            details.add("Birdhouses are last. Resupply after seaweed if needed.");
        if(specialtyStaged&&specialtyBirdPhase&&current!=null)
            details.add("Birdhouse supplies: logs or houses, hammer, chisel, bait and clockworks.");
        if(loggedIn && current!=null)
        {
            if(!fits)supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Full run exceeds available inventory space. Carry what you need first and resupply when convenient.";
            details.add("Estimated occupied slots: "+slots+" / 28; working space: "+reserve+".");
            if(workflow==Preparation.Workflow.UNCHECKED)
            {
                workflow=Preparation.Workflow.ORDINARY_ACTIVE;
                if(rawSeeds+seedlings>0||requiresPacking||Preparation.storedShortage(physicalRequired,carried,paymentStorage)){
                    beginPreparation(Preparation.Workflow.ORDINARY_PREP);
                }
            }
        }
        if(!seedless.isEmpty())details.add(seedless.size()+" patch(es) skipped for missing seeds.");
        if(!clearOnly.isEmpty())details.add(clearOnly.size()+" patch(es) to harvest / clear without replanting.");
        if(current==null&&preparing()&&workflow!=Preparation.Workflow.GUILD_ARRIVAL&&workflow!=Preparation.Workflow.GUILD_SEEDLINGS)workflow=workflow.resume();
        // All processed patches remain visible in the completed history across stages.
        Set<FarmRunPatch> listed=new HashSet<>();for(GuideView.Stop stop:stops)listed.add(stop.patch);
        for(FarmRunPatch p:patches)if(runProgress.finished(p)&&!listed.contains(p))stops.add(new GuideView.Stop(p,runProgress.is(p,RunProgress.Status.PRESERVED)?"Preserved":"Completed",false,true));
        String prepMessage="";
        Set<WorldPoint> bankCandidates=Set.of();
        preparationVault=false;preparationAtStorage=false;
        RouteLeg leg=SpecialRoutes.leg(current,player,aboard,rowboat,instance,localApproach);
        if(preparing() && loggedIn)
        {
            Preparation.Bank preparationBank=Preparation.chooseSupplies(level,preparationOrigin,workflow.initial,
                startAt(),birdBankPhase(),plantingRequired,carried,bank.items,vault.items);
            boolean atStorage=storageOpen();visitedStorage|=atStorage;preparationAtStorage=atStorage;
            boolean vaultOutstanding=level>=45&&Preparation.vaultNeeded(plantingRequired,carried,bank.items,vault.items);
            boolean vaultTarget=preparationBank==Preparation.SEED_VAULT;
            // A known vault shortage may still remain after visiting a regular
            // bank. Only the explicit preparation session can suggest it.
            boolean bankOutstanding=Preparation.storedShortage(storageNeeds,carried,bank.items);
            boolean routeToStorage=(!missingStorageItems.isEmpty()||explicitResupply) &&
                (!visitedStorage||!atStorage&&(vaultTarget?vaultOutstanding:bankOutstanding));
            preparationVault=vaultTarget&&vaultOutstanding;
            boolean flexible=routeToStorage&&!vaultTarget&&preparationBank!=Preparation.FOSSIL_BANK&&
                !(workflow.initial&&startAt()==Preparation.StartAt.GUILD&&level>=45)&&navigation!=null&&navigation.enabled();
            prepMessage=GuidePresentation.preparation(routeToStorage,flexible?"a reachable bank":preparationBank.name,missingStorageItems.isEmpty()&&!routeToStorage);
            String sources=Preparation.sourceNote(plantingRequired,carried,bank.items,vault.items,!bankStale,!vaultStale);
            if(!sources.isEmpty())prepHelp+="\n"+sources;
            if(vaultOutstanding)prepHelp+="\nThe Seed Vault is west of the Guild bank chest.";
            if(physicalRequired.entrySet().stream().anyMatch(e->e.getValue()>carried.getOrDefault(e.getKey(),0)&&SupplyOrder.tool(observedItemName(e.getKey()))))
                prepHelp+="\nCheck a tool leprechaun for missing tools.";
            // Preparation is entered only at run start or by the Resupply button.
            // Keep the explicit Start/Continue action available even when supplies are ready.
            if (!routeToStorage) leg=RouteLeg.walk(null,false);
            else if (aboard || instance && !location.inHouse() || SpecialRoutes.insideHespori(player) ||
                SpecialRoutes.insideSeaweed(player) || SpecialRoutes.insideCoral(player))
            {
                RouteLeg exit=SpecialRoutes.leg(null,player,aboard,rowboat,instance,null);
                leg=exit.instruction.isEmpty()?RouteLeg.manual("Leave the current instance before banking.",RouteLeg.Interaction.NONE):exit;
                prepMessage+="\n"+leg.instruction;
            }
            else
            {
                leg=RouteLeg.approach(preparationBank.point,vaultTarget?"Travel to the Guild bank approach, then use the Seed Vault immediately west of the chest.":flexible?"Follow the route to a reachable bank, or use any accessible bank.":"Travel to "+preparationBank.name+" or another accessible bank.",RouteLeg.Interaction.NONE);
                if(flexible)bankCandidates=Preparation.bankCandidates(level);
            }
        }
        if(workflow==Preparation.Workflow.GUILD_ARRIVAL||workflow==Preparation.Workflow.GUILD_SEEDLINGS)
        {
            boolean arrived=Preparation.atGuild(player);
            prepMessage=arrived?"Prepare outstanding seeds and seedlings. Bank watered seedlings while they mature, then continue with the Guild.":"Travel to the Farming Guild.";
            if(arrived&&(bank.at==0||vault.at==0)&&runHasTrees)prepMessage+=" Open the bank and Seed Vault to confirm planting stock.";
            if(!arrived)leg=RouteLeg.approach(Preparation.GUILD,"Travel to the Farming Guild bank approach.",RouteLeg.Interaction.NONE);
            bankCandidates=Set.of();
        }
        if(workflow==Preparation.Workflow.GUILD_DEPARTURE&&preparing())prepMessage="Farming Guild activities complete. Deposit unnecessary local tools and harvests. Collect remaining supplies — continue when ready.";
        if(loggedIn&&SpecialRoutes.isType(current,PatchType.BIRDHOUSE)&&
            (SpecialRoutes.onSeaweedIsland(player)||!preparing()&&seaweedBirdTransfer))
        {
            boolean missing=(!workflow.guild||preparing())&&Preparation.missing(physicalRequired,plantingBag);
            RouteLeg transfer=SpecialRoutes.insideSeaweed(player)?SpecialRoutes.seaweedExit(instance):
                aboard||instance&&!location.inHouse()?leg:SpecialRoutes.birdTransfer(player,missing,storageOpen(),level);
            if(transfer!=null)
            {
                leg=transfer;
                if(preparing())prepMessage=transfer.instruction;
            }
            else seaweedBirdTransfer=false;
        }
        if(!preparing()&&current!=null&&current.getPatchType()==PatchType.CORAL)
            instruction="Wear the Medallion of the deep OR fishbowl helmet and diving apparatus before entering.\n"+instruction;
        if(!preparing()&&current!=null&&"Entrana".equals(current.getLocation()))instruction="Entrana: deposit prohibited weapons and armour before travel.\n"+instruction;
        activeLeg=leg;
        WorldPoint actionPoint=localApproach!=null?localApproach:current==null?null:Waypoints.destination(current);
        boolean atAction=player!=null&&actionPoint!=null&&player.getPlane()==actionPoint.getPlane()&&player.distanceTo(actionPoint)<=5;
        stepItems=!preparing()&&leg.interaction==RouteLeg.Interaction.NONE&&activeLive&&(atAction||spaceItem>0)?localStepItems:Collections.emptySet();
        if(navigation!=null)
        {
            if(!bankCandidates.isEmpty()&&leg.interaction==RouteLeg.Interaction.NONE)
                navigation.requestBanks(location.pathStart(),leg.point,bankCandidates);
            else navigation.request(loggedIn?location.pathStart():null,leg.point,leg.probe);
        }
        if(location.inHouse()&&(navigation==null||!navigation.enabled())&&current!=null&&!preparing())
            instruction="Use an available house teleport toward the next patch, or leave your house.\n"+instruction;
        if(!leg.instruction.isEmpty())instruction=leg.instruction+(current==null?"":"\n"+instruction);
        TransportHint transport=currentTransport();
        if(transport!=null)instruction=transport.instruction()+"\n"+instruction;
        String routeNote=navigation==null?"":navigation.status();
        String routeWarning=GuidePresentation.routeWarning(routeNote);
        if(!routeWarning.isEmpty()&&!preparing())instruction+="\n"+routeWarning;
        if(!routeNote.isEmpty()&&!routeNote.equals("Checking route…"))details.add(routeNote);
        if(current!=null)
            for(int i=0;i<stops.size();i++) if(stops.get(i).active) stops.set(i,new GuideView.Stop(current,instruction,!preparing(),false));
        if(preparing()&&!routeWarning.isEmpty())prepMessage+="\n"+routeWarning;
        List<GuideView.ItemRow> viewItems=splitGuild?GuideView.guildSections(items,guildNeeds):items;
        cacheIcons(viewItems);
        GuideView view=new GuideView(title,instruction,viewItems,stops,preparing()?null:current,prepMessage,supplyNote,hideSeedless,clearSeedless,!preparing()&&activeAction==StepEngine.Action.COMPOST,!preparing()&&activeAction==StepEngine.Action.WATER,summary,
            new GuideView.Notes(workflow.initial&&workflow!=Preparation.Workflow.GUILD_DEPARTURE,storageWarning,prepHelp,details.toString(),workflow==Preparation.Workflow.GUILD_SEEDLINGS,splitGuild,Set.copyOf(preparationIds)));
        normalView=view;
        publishView(view);
    }

    private boolean immune(FarmRunPatch p)
    {
        return p!=null&&CropCare.siteImmune(p,
            "Falador".equals(p.getLocation())&&client.getVarbitValue(VarbitID.FALADOR_DIARY_ELITE_COMPLETE)==1,
            "Kourend".equals(p.getLocation())&&client.getVarbitValue(VarbitID.KOUREND_DIARY_EASY_COMPLETE)==1);
    }
    private static Crop observedCrop(PatchType type,QuestFarmingStateReader.Observation o)
    {
        var produce=QuestFarmingStateReader.livingProduce(type.name(),o);
        return produce==null?null:CropCatalog.findByName(type,produce.getName()+(type.isTree()?" sapling":""));
    }
    boolean spaceRelief(){return guideRequested&&!preparing()&&diseaseTarget==null&&spaceItem>0;}

    private LeprechaunWithdrawal localCollection(LeprechaunWithdrawal primary,FarmRunPatch patch,List<FarmRunPatch> ready,
        Map<FarmRunPatch,Crop> seeds,Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations,
        Map<Integer,Integer> bag,Map<Integer,Integer> carried,Map<Integer,Integer> plantingBag,int wateringCan,int compost,int occupied,boolean barbarian)
    {
        if(primary!=null&&primary.quantity==0)return primary;
        int free=Math.max(0,26-occupied);
        if(free<=0||!highlight.hasLocalPatch(patch))return primary;
        var npc=highlight.leprechaun(patch);
        if(npc==null)return primary;
        int compostDemand=0;
        String site=RunOrder.site(patch);
        for(FarmRunPatch other:ready)
        {
            if(!site.equals(RunOrder.site(other))||!highlight.hasLocalPatch(other)||highlight.leprechaun(other)!=npc)continue;
            var o=observations.get(other);
            if(o==null||!o.localGuidance())continue;
            PatchType type=other.getPatchType();
            if(type==PatchType.BIRDHOUSE||type==PatchType.COMPOST_BIN||type==PatchType.HESPORI)continue;
            Crop crop=seeds.get(other);
            boolean replacement=crop!=null&&plantingBag.getOrDefault(crop.getItemId(),0)>=crop.getQuantity();
            StepEngine.Action next=other==patch?activeAction:StepEngine.action(type,o,preserveReplacement(other,o),replacement,false,runPayRemoval&&type.isTree()&&gardener(other)>0);
            if(o.state==CropState.EMPTY&&reader.hasWeeds(type.name(),o.rawValue))next=StepEngine.Action.RAKE;
            primary=LeprechaunWithdrawal.append(primary,LeprechaunWithdrawal.resolve(client,next,type,compost,barbarian,bag,carried,wateringCan,free,this::observedItemName),free);
            if(replacement)
            {
                primary=LeprechaunWithdrawal.append(primary,LeprechaunWithdrawal.resolve(client,StepEngine.Action.PLANT,type,compost,barbarian,bag,carried,wateringCan,free,this::observedItemName),free);
                if(CompostChoice.isOrdinary(compost)&&CropCare.usesCompost(crop,immune(other))&&!(other==patch&&compostAcknowledged)&&
                    (o.state==CropState.EMPTY||o.state==CropState.DEAD||o.state==CropState.STUMP||
                     o.state==CropState.HARVESTABLE||o.state==CropState.UNCHECKED||CropReplacement.mature(type,o)))
                {
                    int tier=o.state==CropState.EMPTY?highlight.liveCompostTier(other):-1;
                    if(tier<CompostChoice.minimumTier(compost))compostDemand++;
                }
            }
        }
        return CompostChoice.isOrdinary(compost)?LeprechaunWithdrawal.withCompost(primary,compost,
            Math.max(0,compostDemand-bag.getOrDefault(compost,0)),LeprechaunWithdrawal.stored(client,compost),free,observedItemName(compost)):primary;
    }

    private int localDistance(FarmRunPatch patch,WorldPoint here,Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations)
    {
        var o=observations.get(patch);
        if(o==null||o.source!=QuestFarmingStateReader.Source.LIVE||!highlight.hasLocalPatch(patch))return Integer.MAX_VALUE;
        WorldPoint approach=highlight.approach(patch);
        return approach==null?Integer.MAX_VALUE:here.distanceTo(approach);
    }

    private LeprechaunWithdrawal contextualWithdrawal(FarmRunPatch patch,StepEngine.Action action,int compost,
        Map<Integer,Integer> bag,Map<Integer,Integer> carried,int wateringCan,boolean needsCan,int occupied)
    {
        if(!runLeprechaun||highlight==null||!highlight.hasLocalPatch(patch)||highlight.leprechaun(patch)==null)return null;
        int free=Math.max(0,28-occupied);
        LeprechaunWithdrawal immediate=LeprechaunWithdrawal.resolve(client,action,patch.getPatchType(),compost,
            client.getVarbitValue(VarbitID.BRUT_FARMING_PLANTING)==3,bag,carried,wateringCan,free,this::observedItemName);
        return LeprechaunWithdrawal.withWater(client,immediate,action,compost,bag,wateringCan,free,needsCan,this::observedItemName);
    }

    private StepEngine.Action guidanceAction(PatchType type,QuestFarmingStateReader.Observation o,boolean keep,
        boolean replacementCarried,int compostId,int compostTier)
    {
        if((progress.planted()||activeCareOnly)&&!wateredThisRun.contains(current)&&!immune(current)&&CropCare.waterNeeded(type,o,runWater))
            return StepEngine.Action.WATER;
        if(progress.planted()&&o.state==CropState.GROWING)return StepEngine.Action.PROTECT_TREE;
        if(o.state==CropState.EMPTY)
        {
            if(activeLive&&reader.hasWeeds(type.name(),o.rawValue))return StepEngine.Action.RAKE;
            if(activeLive&&compostId>=0&&CropCare.usesCompost(activeCrop,immune(current))&&!activeClearOnly&&
                !compostAcknowledged&&compostTier<CompostChoice.minimumTier(compostId))return StepEngine.Action.COMPOST;
            if(activeCrop!=null&&type.isTree()&&!replacementCarried)return StepEngine.Action.NEED_REPLACEMENT;
        }
        return StepEngine.action(type,o,keep,replacementCarried,activeClearOnly,runPayRemoval&&type.isTree()&&gardener(current)>0);
    }

    private void publishView(GuideView view)
    {
        if(!view.equals(lastView))
        {
            lastView=view;long id=session.get();FarmingHelperPanel target=panel;
            // If multiple game ticks outrun Swing, render only the newest
            // immutable view for this panel/session, never a stale backlog.
            SwingUtilities.invokeLater(()->{if(running&&guideRequested&&id==session.get()&&panel==target&&target!=null&&lastView==view)target.guide(view,id);});
        }
    }

    private boolean showDiseaseInterrupt(String title,PhysicalStock supplies,LocationContext location,
        Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations,List<FarmRunPatch> local)
    {
        if(highlight==null)return false;
        WorldPoint here=location.actual;
        Map<Integer,Integer> carried=supplies.carried;
        Map<FarmRunPatch,WorldPoint> approaches=new LinkedHashMap<>();
        for(FarmRunPatch p:local)
        {
            QuestFarmingStateReader.Observation o=observations.computeIfAbsent(p,this::observe);
            if(o==null||o.source!=QuestFarmingStateReader.Source.LIVE||o.state!=CropState.DISEASED||!highlight.hasVerifiedDisease(p))continue;
            approaches.put(p,highlight.approach(p));
        }
        FarmRunPatch previous=diseaseTarget;
        FarmRunPatch target=diseasePriority.select(approaches.keySet(),p->{WorldPoint a=approaches.get(p);return a==null?Integer.MAX_VALUE:here.distanceTo(a);});
        if(target==null)
        {
            if(previous!=null)lastView=null;
            diseaseTarget=null;diseaseLeg=null;diseaseItems=Collections.emptySet();return false;
        }
        if(previous!=target)lastView=null;
        // Record the disease transition only when the interrupted patch itself is
        // the diseased one, so its existing completion tracker can see recovery.
        if(current==target)progress.observe(observations.get(target));
        PatchType type=target.getPatchType();
        int cure=StepItems.cure(type,carried);
        diseaseItems=Set.of(cure);
        missingStorageItems=GuideView.missingItems(Map.of(cure,1),carried);
        missingTeleports=Collections.emptySet();
        WorldPoint approach=approaches.get(target);
        diseaseLeg=RouteLeg.walk(approach,false);
        diseaseTarget=target;
        if(navigation!=null)navigation.request(location.pathStart(),approach,false);
        String instruction=CropCare.prunes(type)?"Prune the diseased crop with secateurs.":"Use plant cure on the diseased crop.";
        Map<Integer,Integer> bag=supplies.bag;
        leprechaunWithdrawal=contextualWithdrawal(target,StepEngine.Action.CURE,-1,bag,carried,-1,false,Preparation.slots(bag,Map.of(),this::stackable));
        if(leprechaunWithdrawal!=null)instruction=leprechaunWithdrawal.instruction()+", then "+instruction;
        else if(carried.getOrDefault(cure,0)==0)instruction+=CropCare.prunes(type)?" Obtain secateurs or skip this cure.":" Obtain plant cure from a nearby farmer or shop if available, or skip this cure.";
        String resume=preparing()?"Preparation / resupply":current==null?"The remaining run":current.getDisplayName();
        List<GuideView.ItemRow> items=List.of(itemRow(cure,1,supplies,false,""));
        List<GuideView.Stop> stops=new ArrayList<>();stops.add(new GuideView.Stop(target,instruction,true,false));
        if(normalView!=null)for(GuideView.Stop stop:normalView.stops)if(stop.patch!=target)stops.add(new GuideView.Stop(stop.patch,stop.active?"Paused: "+stop.instruction:stop.instruction,false,stop.done));
        cacheIcons(items);
        publishView(new GuideView(title,"Disease priority",items,stops,target,"","Disease detected — current guide paused. Resume: "+resume+". Cure only; no harvest or replant.",hideSeedless,clearSeedless,false,normalView==null?GuideView.Stats.UNKNOWN:normalView.stats,GuideView.Notes.EMPTY));
        return true;
    }

    private ItemComposition itemDefinition(int id){return itemManager==null?null:itemManager.getItemComposition(id);}
    private boolean stackable(int id)
    {
        ItemComposition item=itemDefinition(id);return item!=null&&item.isStackable();
    }
    private boolean storageOpen()
    {
        return visibleWidget(bank.widget) || visibleWidget(vault.widget);
    }
    private boolean preparing(){return workflow.preparing;}
    boolean preparationActive(){return guideRequested&&preparing()&&diseaseTarget==null;}
    boolean preparationVault(){return preparationVault;}
    boolean preparationAtStorage(){return preparationAtStorage;}
    private void beginPreparation(Preparation.Workflow next)
    {
        clearWaterOffer();preparationOrigin=LocationContext.capture(client).normalized;
        workflow=next;visitedStorage=false;explicitResupply=false;
    }
    void continueRun()
    {
        inRun(()->{
            if(!preparing()||workflow==Preparation.Workflow.GUILD_ARRIVAL)return;
            if(workflow.initial)
            {
                if(workflow==Preparation.Workflow.ORDINARY_PREP||workflow==Preparation.Workflow.GUILD_LOCAL_PREP)pinned=null;
                current=null;activeCrop=null;progress.reset();
            }
            workflow=workflow.resume();explicitResupply=false;update();});
    }
    void resupply()
    {
        inRun(()->{if(diseaseTarget!=null||current==null||preparing())return;sampleStorage();beginPreparation(workflow.resupply());explicitResupply=true;update();});
    }

    private void cacheIcons(List<GuideView.ItemRow> rows)
    {
        if(itemManager==null)return;
        Set<Integer> ids=new HashSet<>();for(GuideView.ItemRow row:rows)ids.add(row.id);
        if(icons.keySet().equals(ids))return;
        Map<Integer,ImageIcon> next=new HashMap<>();
        for(int id:ids)
        {
            ImageIcon icon=icons.get(id);
            if(icon==null){var image=itemManager.getImage(id);if(image!=null)icon=new ImageIcon(image);}
            if(icon!=null)next.put(id,icon);
        }
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

    private GuideView.ItemRow itemRow(int id,int count,PhysicalStock supplies,boolean recommended,String suffix)
    {
        Crop crop=CropCatalog.findByItemId(id);
        String name=crop==null?Supplies.name(id):crop.getItemName();
        if(recommended || name.startsWith("Item #")){String actual=observedItemName(id);if(!actual.isEmpty())name=actual;}
        int boxed=supplies.seedBox.getOrDefault(id,0);
        if(boxed>0&&!recommended)name+=" (box: "+boxed+")";
        return new GuideView.ItemRow(id,name+suffix,supplies.carried.getOrDefault(id,0),count,
            bank.items.getOrDefault(id,0),vault.items.getOrDefault(id,0),recommended);
    }

    boolean keepSpecies(KeepCrop c)
    {
        return KeepCrop.resolve(c.type,c,setting("replacement_"+c.type.name(),""),setting(c.key(),""),"","");
    }
    private boolean autoLily(FarmRunPatch p)
    {return resolveReplacement(p,KeepCrop.WHITE_LILY);}
    private boolean resolveReplacement(FarmRunPatch p,KeepCrop c)
    {
        return KeepCrop.resolve(p.getPatchType(),c,setting("replacement_"+p.getPatchType().name(),""),
            c==null?"":setting(c.key(),""),setting("replacementPatch_"+p.key(),""),runReplacement(p.getPatchType()));
    }
    boolean preserveReplacement(FarmRunPatch p,QuestFarmingStateReader.Observation o)
    {return resolveReplacement(p,KeepCrop.observed(p.getPatchType(),o));}
    String override(FarmRunPatch p) { return setting("directorCrop_"+p.key(),""); }
    String accessNote(FarmRunPatch p) { return accessNotes.getOrDefault(p.key(),""); }
    StepEngine.Action activeAction() { return diseaseTarget!=null?StepEngine.Action.CURE:activeAction; }
    LeprechaunWithdrawal leprechaunWithdrawal(){return guideRequested&&(diseaseTarget!=null||!preparing()&&!spaceRelief())?leprechaunWithdrawal:null;}
    boolean compostMissing(){return diseaseTarget==null&&activeLive()&&compostMissing;}
    boolean activeLive() { return guideRequested && (diseaseTarget!=null||activeLive&&!preparing()); }
    int patchCount(FarmRunPatch p) { return QuestFarmingStateReader.patchNamesForType(p.getPatchType().name(),p.getLocation()).size(); }
    Set<Integer> requiredItems() { return !guideRequested?Collections.emptySet():diseaseTarget!=null?diseaseItems:requiredItems; }
    Set<Integer> missingStorageItems() {return guideRequested?missingStorageItems:Collections.emptySet();}
    String withdrawalLabel(int id){return guideRequested&&diseaseTarget==null?withdrawalLabels.get(id):null;}
    Set<Integer> recommendedItems() { return guideRequested?missingTeleports:Collections.emptySet(); }
    int gardener(FarmRunPatch p) { return reader.gardenerId(p.getPatchType().name(),p.getLocation(),p.getName()); }

    RouteLeg activeLeg(){return !guideRequested?null:diseaseTarget!=null?diseaseLeg:activeLeg;}
    int patchVarbit(FarmRunPatch p){return reader.patchVarbit(p.getPatchType().name(),p.getLocation(),p.getName());}
    boolean inPatchRegion(FarmRunPatch p,WorldPoint point){return reader.inPatchRegion(p.getPatchType().name(),p.getLocation(),p.getName(),point);}

    FarmRunPatch activePatch() { return !guideRequested?null:diseaseTarget!=null?diseaseTarget:!preparing()?current:null; }
    boolean diseaseInterruptActive(){return diseaseTarget!=null&&guideRequested;}

    List<FarmRunPatch> patches() { return Collections.unmodifiableList(patches); }

    boolean enabled(FarmRunPatch patch)
    {
        return flag("patchEnabled_" + patch.key(),PatchLocationSelection.isEnabled(legacy,patch.getLocation()));
    }

    boolean preferContracts(){return flag("preferContracts",true);}

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
        if (canSave()) {config.setRSProfileConfiguration("farming-helper",key,value);if(!key.startsWith("observed"))preferenceRevision.incrementAndGet();}
    }

}
