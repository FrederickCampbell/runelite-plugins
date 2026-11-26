package com.farminghelper;

import com.google.inject.Provides;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader;
import java.awt.*;
import net.runelite.client.util.ImageUtil;
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

@PluginDescriptor(name = "Farming Helper", description = "Quest Helper for your farm runs! Includes Shortest Path integration.",
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
    private volatile Set<Integer> missingTeleports = Collections.emptySet();
    private volatile long session;
    private volatile boolean guideRequested;
    private final Map<Integer,Integer> bank = new HashMap<>(), vault = new HashMap<>();
    private final Set<InventoryID> sampledStorage=EnumSet.noneOf(InventoryID.class);
    private PhysicalStock quantities;
    private final SeedBoxStock seedBox=new SeedBoxStock();
    private FarmRunPatch seedBoxPlant;
    private Map<Integer,Integer> seedBoxPlantBag=Map.of();
    private int seedBoxPlantTick,seedBoxPlantRevision;
    private boolean seedBoxBank,seedBoxVault;
    private String seedBoxSaved="";
    private int seedBoxViewTick=-1;
    private List<?> allocationInputs=List.of();
    private Map<FarmRunPatch,Crop> allocated=Map.of();
    private final Map<Set<Integer>,Map<Integer,List<String>>> eligibleTravel=new HashMap<>();
    private List<?> shelfInputs=List.of();
    private Set<Integer> shelfItems=Set.of(), shelfCandidates=Set.of(), bankPlaceholders=Set.of();
    private String stockProfile;
    private long bankObservedAt,vaultObservedAt;
    private volatile Map<Integer,ImageIcon> icons = Collections.emptyMap();
    private Set<FarmRunType> run = Collections.emptySet();
    private Set<String> manualKeys; // physical selections, never persisted as a session
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
    private volatile Preparation.Phase phase=Preparation.Phase.UNCHECKED;
    private volatile boolean hideSeedless,clearSeedless;
    private boolean activeClearOnly, compostAcknowledged;
    private volatile Set<Integer> stepItems=Collections.emptySet();
    private boolean visitedStorage;
    private WorldPoint preparationOrigin;
    private List<FarmRunPatch> pinned;
    private volatile LeprechaunWithdrawal leprechaunWithdrawal;
    private boolean shortestPathAvailable;
    private FarmingHelperPanel panel;
    private NavigationButton nav;
    private volatile FarmRunPatch current;
    private GuideView lastView;
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
        reader.useSceneObjects(highlight.objects());
        navigation=new NavigationBridge(bus::post);
        if(itemManager!=null)vaultViews=new SeedVaultViews(client,itemManager,this);
        if(itemManager!=null)runBank=new ActiveRunBank(client,plugins,itemManager);
        shortestPathAvailable=plugins.getPlugins().stream().anyMatch(p ->
            "Shortest Path".equals(p.getName()) && plugins.isPluginActive(p));
        navigation.enabled(shortestPathAvailable);
        patches.addAll(FarmRunCatalog.patches());
        patches.addAll(BirdhouseRun.patches());
        panel = new FarmingHelperPanel(this);
        nav = NavigationButton.builder().tooltip("Farming Helper").icon(ImageUtil.loadImageResource(FarmingHelperPlugin.class, "sidebar.png"))
            .panel(panel).priority(8).build();
        toolbar.addNavigation(nav);
        overlays.add(highlight);overlays.add(highlight.dialogue());
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
        overlays.remove(highlight);overlays.remove(highlight.dialogue());
        if (itemHighlight != null) overlays.remove(itemHighlight);
        patches.clear();
        highlight.clear();
    }

    void startPreset(RunPreset preset)
    {
        startSelected(preset.select(patches,this::flag),preset.title,"group_"+preset.name());
    }
    void startIndividual(PatchType type)
    {
        Set<String> keys=new LinkedHashSet<>();for(FarmRunPatch p:patches)if(p.getPatchType()==type)keys.add(p.key());
        startSelected(keys,RunPreset.individualTitle(type),"individual_"+type.name());
    }
    void startCombinedShortcut(FarmRunType type)
    {
        Set<String> keys=new LinkedHashSet<>();for(FarmRunPatch p:patches)if(p.getFarmRunType()==type)keys.add(p.key());
        startSelected(keys,type.getDisplayName(),"shortcut_"+type.name());
    }
    void startManual(Set<String> keys)
    {startSelected(keys,"Manual Run","manual");}
    private void startSelected(Set<String> keys,String label,String policyKey)
    {
        Set<String> chosen=Set.copyOf(keys);
        Set<FarmRunType> types=EnumSet.noneOf(FarmRunType.class);
        for(FarmRunPatch p:patches)if(chosen.contains(p.key())&&enabled(p))types.add(p.getFarmRunType());
        if(types.isEmpty()) {SwingUtilities.invokeLater(()->panel.emptySelection());return;}
        start(types,chosen,label,policyKey);
    }

    private void start(Set<FarmRunType> types,Set<String> keys,String label,String policyKey)
    {
        if (types.isEmpty()) return;
        Set<FarmRunType> selected=EnumSet.copyOf(types);
        guideRequested = true;
        final long requested = ++session;
        thread.invokeLater(() -> {
            if (!running || !guideRequested || requested != session) return;
            clearRun();
            run = selected;
            manualKeys=keys;runLabel=label;
            runPolicyKey=policyKey;
            specialtyStaged=SpecialtyStages.stageBirds(policyKey,run);
            runProfile=config.getRSProfileKey();
            refreshRunPreferences();
            updateAccessNotes();
            runHasTrees=patches.stream().anyMatch(p->ManualSelection.includes(p,run,manualKeys)&&enabled(p)&&accessNote(p).isEmpty()&&p.getPatchType().isTree());
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
        manualKeys=null;runLabel=null;
        phase=Preparation.Phase.UNCHECKED;visitedStorage=false;preparationOrigin=null;
        pinned=null;leprechaunWithdrawal=null;allocationInputs=List.of();allocated=Map.of();
        current = null;
        birdhouseTouched.clear();wateredThisRun.clear();activeCareOnly=false;waterOfferTick=-1;waterOfferPatch=null;waterOfferOrigin=null;compostMissing=false;
        diseasePriority.reset();diseaseTarget=null;diseaseLeg=null;diseaseItems=Collections.emptySet();normalView=null;
        specialtyStaged=false;specialtyBirdPhase=false;seaweedBirdTransfer=false;
        runProgress.reset();
        temporaryCare=null;temporaryPay=null;temporaryWater=null;temporaryLeprechaun=null;temporarySeedless=null;temporaryReplacement=Collections.emptyMap();hopping=false;runProfile=null;
        progress.reset();
        activeCrop = null;
        activeAction = StepEngine.Action.INSPECT;
        activeLive=false;
        requiredItems = Collections.emptySet();
        missingStorageItems = Collections.emptySet();
        missingTeleports = Collections.emptySet();
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
        invalidateStock();
        bank.clear();
        vault.clear();
        stockProfile=null;seedBox.clear();seedBoxPlant=null;seedBoxSaved="";seedBoxViewTick=-1;
        bankObservedAt=0;vaultObservedAt=0;
        end();
    }

    CompostChoice compostChoice(){return CompostChoice.load(setting("directorCompostChoice",""),flag("directorUseCompost",true));}
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
    void setRunSupplies(Boolean water,Boolean leprechaun)
    {
        long id=session;thread.invokeLater(()->{
            if(id!=session||!guideRequested)return;
            if(water!=null)temporaryWater=water;
            if(leprechaun!=null)temporaryLeprechaun=leprechaun;
            refreshRunPreferences();lastView=null;update();
        });
    }
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
    Set<Integer> activeStepItems(){return !guideRequested?Collections.emptySet():diseaseTarget!=null?diseaseItems:!preparing()?stepItems:Collections.emptySet();}
    void resetObservedStock()
    {
        long id=session;thread.invokeLater(()->{
            if(id!=session||!guideRequested||run.isEmpty())return;
            loadStock();invalidateStock();bank.clear();vault.clear();bankObservedAt=0;vaultObservedAt=0;
            save("observedBankV1","");save("observedVaultV1","");seedBox.clear();seedBoxSaved="";save("observedSeedBoxV2","");seedBoxPlant=null;
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
    void setSeedlessPolicy(SeedlessPolicy policy)
    {
        long id=session;thread.invokeLater(()->{if(id!=session||!guideRequested||run.isEmpty())return;temporarySeedless=policy;refreshRunPreferences();sampleStorage();lastView=null;update();});
    }
    String runReplacement(PatchType type){return temporaryReplacement.getOrDefault(type,"");}
    void setRunReplacement(PatchType type,String mode)
    {
        long id=session;thread.invokeLater(()->{
            if(id!=session||!guideRequested||run.isEmpty())return;
            Map<PatchType,String> next=new EnumMap<>(PatchType.class);next.putAll(temporaryReplacement);
            if(KeepCrop.explicit(mode))next.put(type,mode);else next.remove(type);
            temporaryReplacement=Map.copyOf(next);lastView=null;update();
        });
    }
    void skipRemainingType(PatchType type)
    {
        long id=session;thread.invokeLater(()->{
            if(id!=session||!guideRequested)return;
            for(FarmRunPatch p:patches)if(p.getPatchType()==type&&ManualSelection.includes(p,run,manualKeys)&&enabled(p)&&!runProgress.finished(p))runProgress.skip(p);
            lastView=null;update();
        });
    }
    List<PatchType> selectedRunTypes()
    {
        Set<PatchType> result=EnumSet.noneOf(PatchType.class);
        for(FarmRunPatch p:patches)if(ManualSelection.includes(p,run,manualKeys)&&enabled(p))result.add(p.getPatchType());
        return new ArrayList<>(result);
    }
    void skip(FarmRunPatch displayed)
    {
        long id=session;thread.invokeLater(() -> {
            if(id!=session||!guideRequested)return;
            if(diseaseTarget!=null)
            {
                if(displayed==diseaseTarget){diseasePriority.skip(displayed);diseaseTarget=null;lastView=null;}
            }
            else if (current != null && current == displayed)
            {
                if(activeAction==StepEngine.Action.WATER)wateredThisRun.add(current);
                else runProgress.skip(current);
            }
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
            if (!run.isEmpty()) update();
        });
    }

    @Subscribe public void onPluginMessage(PluginMessage event)
    {
        if(navigation!=null)navigation.message(event);
    }

    @Subscribe public void onProfileChanged(ProfileChanged event) { reset(); }
    @Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) { if(!guideRequested||!Objects.equals(runProfile,config.getRSProfileKey()))reset(); }
    @Subscribe public void onGameObjectSpawned(GameObjectSpawned event) { highlight.spawned(event.getGameObject()); }
    @Subscribe public void onGameObjectDespawned(GameObjectDespawned event) { highlight.despawned(event.getGameObject()); }
    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if(event.getGameState()==GameState.LOADING || event.getGameState()==GameState.LOGIN_SCREEN) highlight.clear();
        if(event.getGameState()==GameState.HOPPING || event.getGameState()==GameState.LOGGING_IN){seedBox.forget();seedBoxPlant=null;seedBoxViewTick=-1;invalidateStock();}
        if(event.getGameState()==GameState.LOGIN_SCREEN){reset();return;}
        if(event.getGameState()==GameState.HOPPING&&guideRequested)
        {
            // Preserve same-character run decisions, but release scene-local routes/widgets.
            hopping=true;leprechaunWithdrawal=null;highlight.clear();if(navigation!=null)navigation.stop();
            if(runBank!=null)runBank.close();if(vaultViews!=null)vaultViews.close();
            activeLive=false;stepItems=Collections.emptySet();lastAccessLevel=-1;
        }
    }

    @Subscribe public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if(hopping){hopping=false;if(!Objects.equals(runProfile,config.getRSProfileKey()))reset();lastView=null;}
        sampleStorage();
        updateSeedBox();
        // Re-evaluate quest, diary, Hunter and Sailing gates even when Farming is unchanged.
        if(lastAccessLevel!=client.getRealSkillLevel(Skill.FARMING)||++accessTicks>=5){accessTicks=0;invalidateTravel();updateAccessNotes();}
        if (!run.isEmpty() && guideRequested)
        {
            // Re-evaluate supplies and local transfer legs before a pending route can refresh.
            update();
            if(navigation!=null && client.getLocalPlayer()!=null)navigation.tick(LocationContext.capture(client).pathStart());
        }
        if(vaultViews!=null)vaultViews.tick();
        updateRunBank(false);

    }

    @Subscribe public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.BANKMAIN || event.getGroupId() == InterfaceID.SEED_VAULT)
        {
            sampledStorage.remove(event.getGroupId()==InterfaceID.BANKMAIN?InventoryID.BANK:InventoryID.SEED_VAULT);
            thread.invokeLater(() -> { if (running) sampleStorage(); });
        }
    }

    @Subscribe public void onItemContainerChanged(ItemContainerChanged event)
    {
        int id=event.getContainerId();
        if(id==InventoryID.INVENTORY.getId()||id==InventoryID.EQUIPMENT.getId())quantities=null;
        if(id==InventoryID.INVENTORY.getId()&&event.getItemContainer()!=null&&seedBox.known())
            seedBox.carried(readItems(event.getItemContainer()));
        if(id==net.runelite.api.gameval.InventoryID.SEED_BOX&&client.getGameState()==GameState.LOGGED_IN)
        {
            loadStock();
            if(SeedBoxStock.boxId(readItems(client.getItemContainer(InventoryID.INVENTORY)))>0)
            {
                seedBox.observe(event.getItemContainer(),client.getTickCount());seedBoxPlant=null;
                seedBoxChanged();
            }
        }
        if(id==InventoryID.BANK.getId()||id==InventoryID.SEED_VAULT.getId())
        {
            sampledStorage.remove(id==InventoryID.BANK.getId()?InventoryID.BANK:InventoryID.SEED_VAULT);
            sampleStorage();
            if(vaultViews!=null&&id==InventoryID.SEED_VAULT.getId())vaultViews.invalidate();
        }
    }

    @Subscribe public void onVarbitChanged(VarbitChanged event)
    {
        highlight.transformed();
        if(!eligibleTravel.isEmpty()&&CuratedBank.travelChanged(event.getVarbitId(),event.getVarpId()))invalidateTravel();
    }
    @Subscribe public void onConfigChanged(net.runelite.client.events.ConfigChanged event)
    {
        if("shortestpath".equals(event.getGroup()))thread.invokeLater(this::invalidateTravel);
        if("farming-helper".equals(event.getGroup())&&!event.getKey().startsWith("observed"))preferenceRevision++;
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
    {
        if(vaultViews!=null)vaultViews.nativeMenu(event.getParam1());
        if(client.getGameState()!=GameState.LOGGED_IN||itemManager==null||event.isConsumed())return;
        Map<Integer,Integer> bag=stock().bag;
        if(SeedBoxStock.boxId(bag)<0)return;
        // Settle a previous successful action before capturing the next baseline.
        updateSeedBox();
        seedBox.cancel();seedBoxPlant=null;
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
            if(option.equals("fill")||option.startsWith("deposit"))beginSeedTransfer(true,seedItem(id)?id:-1,bag);
            else if(option.equals("empty")||option.equals("empty to bank")||option.startsWith("withdraw")||option.startsWith("remove"))
                beginSeedTransfer(false,seedItem(id)?id:-1,bag);
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
            var inventory=client.getItemContainer(InventoryID.INVENTORY);
            int slot=widget.getIndex();
            if(inventory!=null&&slot>=0&&slot<inventory.getItems().length&&inventory.getItems()[slot]!=null)
                id=inventory.getItems()[slot].getId();
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
        Map<Integer,Integer> result=new HashMap<>(bag);
        if(seedBoxBank)
        {
            var bankItems=client.getItemContainer(InventoryID.BANK);
            if(bankItems==null)return null;
            result=PhysicalStock.combine(result,readItems(bankItems));
        }
        if(seedBoxVault)
        {
            var vaultItems=client.getItemContainer(InventoryID.SEED_VAULT);
            if(vaultItems==null)return null;
            result=PhysicalStock.combine(result,readItems(vaultItems));
        }
        return result;
    }
    private void beginSeedTransfer(boolean adding,int seed,Map<Integer,Integer> bag)
    {
        seedBoxBank=visibleWidget(InterfaceID.Bankmain.UNIVERSE);
        seedBoxVault=visibleWidget(InterfaceID.SeedVault.UNIVERSE);
        Map<Integer,Integer> loose=looseSeeds(bag);
        if(loose!=null)seedBox.begin(adding,seed,loose,client.getTickCount());
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
        if(visibleWidget(InterfaceID.HosidiusSeedbox.UNIVERSE))
        {
            if(seedBoxViewTick<0)seedBoxViewTick=tick;
            if(tick==seedBoxViewTick+2)
            {
                var nativeBox=client.getItemContainer(net.runelite.api.gameval.InventoryID.SEED_BOX);
                if(nativeBox!=null&&SeedBoxStock.boxId(stock().bag)>0)
                {seedBox.observe(nativeBox,tick);seedBoxPlant=null;seedBoxChanged();}
            }
        }
        else seedBoxViewTick=-1;
        if(seedBox.finishCheck(tick))seedBoxChanged();
        if(!seedBox.pending()&&seedBoxPlant==null)return;
        Map<Integer,Integer> bag=stock().bag;
        if(SeedBoxStock.boxId(bag)<0){seedBox.cancel();seedBoxPlant=null;return;}
        if(seedBox.pending())
        {
            if(seedBoxBank&&!visibleWidget(InterfaceID.Bankmain.UNIVERSE)||seedBoxVault&&!visibleWidget(InterfaceID.SeedVault.UNIVERSE))seedBox.cancel();
            else
            {
                Map<Integer,Integer> loose=looseSeeds(bag);
                if(loose==null)seedBox.cancel();
                else if(seedBox.settle(loose,tick,this::seedItem))seedBoxChanged();
            }
        }
        if(seedBoxPlant!=null)
        {
            if(tick-seedBoxPlantTick>30||seedBox.revision()!=seedBoxPlantRevision){seedBoxPlant=null;return;}
            var observation=observe(seedBoxPlant);
            if(observation.source!=QuestFarmingStateReader.Source.LIVE){seedBoxPlant=null;return;}
            if(observation.state==CropState.GROWING)
            {
                String species=QuestFarmingStateReader.livingProduceKey(seedBoxPlant.getPatchType().name(),observation);
                for(var produce:net.runelite.client.plugins.timetracking.farming.Produce.values())
                    if(produce.name().equals(species))
                    {
                        Crop crop=CropCatalog.findByName(seedBoxPlant.getPatchType(),produce.getName());
                        if(crop!=null&&seedBox.planted(crop,seedBoxPlantBag.getOrDefault(crop.getItemId(),0),bag.getOrDefault(crop.getItemId(),0),seedBoxPlantRevision))seedBoxChanged();
                        break;
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
        if(SeedBoxStock.boxId(stock().bag)<0)return;
        if(seedBox.chat(message,client.getTickCount(),this::seedByName))seedBoxChanged();
    }

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
        boolean bird=birdBankPhase();
        List<?> inputs=List.of(session,quantities,bankPlaceholders,runItems,bird,preferenceRevision,vaultAccessRevision);
        if(inputs.equals(shelfInputs))return shelfItems;
        boolean diving=!bird&&patches.stream().anyMatch(p->ManualSelection.includes(p,run,manualKeys)&&enabled(p)&&accessNote(p).isEmpty()&&(p.getPatchType()==PatchType.SEAWEED||p.getPatchType()==PatchType.CORAL));
        Set<Integer> candidates = new HashSet<>(owned.keySet());
        candidates.addAll(bankPlaceholders);
        shelfCandidates = Set.copyOf(candidates);
        Set<Integer> tagged = new HashSet<>(CuratedBank.shelf(runItems,shelfCandidates,this::observedItemName,client,config,
            bird,!bird&&runHasTrees,diving,teleports(shelfCandidates).keySet()));
        // A native placeholder can survive a change to the stock-based crop recommendation.
        if (!bird && !bankPlaceholders.isEmpty())
        {
            List<PatchType> types = selectedRunTypes();
            for (int id : bankPlaceholders)
            {
                Crop crop = VaultRanking.resolve(id,observedItemName(id));
                if (crop != null && types.contains(crop.getPatchType())) tagged.add(id);
            }
        }
        shelfItems = Set.copyOf(tagged);
        shelfInputs=inputs;
        return shelfItems;
    }
    private String updateRunBank(boolean manual)
    {
        if(runBank==null)return "The farming bank view is unavailable.";
        boolean enabled=flag("activeRunBank",true);
        if(!enabled||!guideRequested||run.isEmpty()||!runBank.bankOpen())
        {runBank.tick(null,Collections.emptySet(),enabled,birdBankPhase());return "Open a bank, then choose Show farming bank.";}
        Map<Integer,Integer> owned=stock().shelf;
        Set<Integer> eligible=bankRunItems(owned);
        if(manual)return runBank.show(eligible,shelfCandidates,birdBankPhase());
        runBank.tick(eligible,shelfCandidates,enabled,birdBankPhase());return "";
    }
    void showActiveRunBank(java.util.function.Consumer<String> feedback)
    {
        long id=session;
        thread.invokeLater(()->{
            if(id!=session||!guideRequested||!flag("activeRunBank",true))return;
            sampleStorage();update();
            String message=updateRunBank(true);
            if(!message.isEmpty())SwingUtilities.invokeLater(()->{if(id==session&&guideRequested)feedback.accept(message);});
        });
    }
    TransportHint currentTransport()
    {
        if(!guideRequested||diseaseTarget!=null||preparing()||current==null||navigation==null||client.getLocalPlayer()==null||activeLeg!=null&&activeLeg.interaction!=RouteLeg.Interaction.NONE)return null;
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
        invalidateStock();bank.clear();vault.clear();stockProfile=profile;
        seedBoxSaved=profile==null?"":setting("observedSeedBoxV2","");seedBox.restore(seedBoxSaved);seedBoxPlant=null;seedBoxViewTick=-1;
        bankObservedAt=0;vaultObservedAt=0;
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
        var widget=client.getWidget(component);
        if(widget==null||widget.isHidden()){sampledStorage.remove(id);return;}
        ItemContainer container = client.getItemContainer(id);
        if (container == null) return;
        if(id==InventoryID.BANK)bankObservedAt=System.currentTimeMillis();
        if(id==InventoryID.SEED_VAULT)vaultObservedAt=System.currentTimeMillis();
        if(!sampledStorage.add(id))return;
        Map<Integer,Integer> observed;
        if (id == InventoryID.BANK && itemManager != null)
        {
            Set<Integer> placeholders = new HashSet<>();
            observed = PhysicalStock.read(container,itemManager::getItemComposition,placeholders);
            bankPlaceholders = Set.copyOf(placeholders);
        }
        else observed = readItems(container);
        if(!observed.equals(snapshot))
        {
            quantities=null;snapshot.clear();snapshot.putAll(observed);
            if(Objects.equals(stockProfile,config.getRSProfileKey()))save(key,StockSnapshot.encode(snapshot));
        }
    }

    private void invalidateTravel(){eligibleTravel.clear();shelfInputs=List.of();}

    private void invalidateStock()
    {
        quantities=null;bankPlaceholders=Set.of();sampledStorage.clear();invalidateTravel();
    }

    private Map<Integer,Integer> readItems(ItemContainer container)
    {return itemManager==null?Map.of():PhysicalStock.read(container,itemManager::getItemComposition);}

    PhysicalStock stock()
    {
        if(quantities==null)
        {
            invalidateTravel();
            Map<Integer,Integer> bag=readItems(client.getItemContainer(InventoryID.INVENTORY));
            quantities=new PhysicalStock(bag,readItems(client.getItemContainer(InventoryID.EQUIPMENT)),bank,vault,seedBox.carried(bag),this::observedItemName,
                id->itemManager==null?id:itemManager.canonicalize(id));
        }
        return quantities;
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
        String title=runLabel;
        boolean loggedIn = client.getLocalPlayer()!=null && client.getGameState()==GameState.LOGGED_IN;
        LocationContext location=LocationContext.capture(client);
        int level = client.getRealSkillLevel(Skill.FARMING);
        PhysicalStock supplies=stock();
        Map<Integer,Integer> bag=supplies.bag,equipped=supplies.equipped,carried=supplies.carried,plantingBag=supplies.plantingBag;
        // Run this before normal planning, even during banking or birdhouses. No
        // mutations to the normal guide cursor, progress, or preparation state.
        Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations=new HashMap<>();
        if(waterOfferPatch!=null&&CropCare.offerExpired(client.getTickCount()-waterOfferTick,
            waterOfferOrigin==null||location.actual==null?Integer.MAX_VALUE:waterOfferOrigin.distanceTo(location.actual)))
        {wateredThisRun.add(waterOfferPatch);waterOfferPatch=null;waterOfferOrigin=null;waterOfferTick=-1;}
        if(loggedIn&&showDiseaseInterrupt(title,carried,location,observations))return;
        Map<Integer,Integer> storage=supplies.storage,stock=supplies.stock;
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
        int storedCompost=loggedIn&&runLeprechaun&&runPolicy.compost?CompostChoice.stored(client,compostId):0;
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
        if(loggedIn) for(FarmRunPatch p:patches)
        {
            PatchType type=p.getPatchType();
            if(!ManualSelection.includes(p,run,manualKeys)||!enabled(p)||!accessNote(p).isEmpty())continue;
            runProgress.include(p);
            if(!SpecialtyStages.includes(specialtyStaged,specialtyBirdPhase,type)||runProgress.is(p,RunProgress.Status.SKIPPED))continue;
            QuestFarmingStateReader.Observation o=observations.computeIfAbsent(p,this::observe);
            if(o==null) continue;
            if(type==PatchType.BIRDHOUSE)
            {
                if(runProgress.finished(p)){visible.add(p);continue;}
                if(p==current&&o.source==QuestFarmingStateReader.Source.LIVE)
                {
                    if(o.rawValue>=0&&!BirdhouseRun.seeded(o.rawValue))birdhouseTouched.add(p);
                    if(BirdhouseRun.seeded(o.rawValue)&&birdhouseTouched.contains(p)){runProgress.finish(p,false);visible.add(p);continue;}
                }
                if(o.state!=CropState.GROWING){ready.add(p);visible.add(p);}
                continue;
            }
            if(type==PatchType.COMPOST_BIN)
            {
                if(runProgress.finished(p)){visible.add(p);continue;}
                if(actionable(type,o.state)){ready.add(p);visible.add(p);}
                else if(p==current){runProgress.finish(p,false);visible.add(p);}
                continue;
            }
            if(p==current&&!preparing()){progress.observe(o);if(activeAction==StepEngine.Action.WATER&&CropCare.watered(type,o))wateredThisRun.add(p);}
            boolean preserveCrop=preserveReplacement(p,o);
            // Preserve selected flower species without picking them; newly planted flowers may finish care.
            if((type==PatchType.FLOWER && KeepCrop.observed(type,o)!=null && preserveCrop && !(p==current&&progress.planted())) ||
                ("Farming Guild".equals(p.getLocation()) && QuestFarmingStateReader.isLivingContractCrop(type.name(),o,contract)))
            {runProgress.finish(p,true);continue;}
            if(runProgress.finished(p)) { visible.add(p); continue; }
            if(p==current&&!preparing())
            {
                if(activeCareOnly)
                {
                    // A local care detour must never turn into an ordinary harvest/replant.
                    // The local scanner below re-adds this detour only while the exact target is verified.
                    continue;
                }
                if(hideSeedless&&clearSeedless&&activeClearOnly&&o.source==QuestFarmingStateReader.Source.LIVE&&o.state==CropState.EMPTY)
                {runProgress.finish(p,false);visible.add(p);continue;}
                boolean wants=protect && activeCrop!=null && type.supportsProtection() && !CropCatalog.protection(activeCrop).isEmpty();
                boolean hasPayment=wants && CropCatalog.protection(activeCrop).stream().allMatch(cost -> bag.getOrDefault(cost.getItemId(),0)>=cost.getQuantity());

                // The player may carry a lower-ranked seed than the initially allocated bank crop.
                // Prefer a usable carried replacement for the active mature patch over preserving it prematurely.
                if(CropReplacement.configurable(type)&&!preserveCrop&&CropReplacement.mature(type,o)&&!progress.planted()&&
                    (activeCrop==null||plantingBag.getOrDefault(activeCrop.getItemId(),0)<activeCrop.getQuantity())&&
                    !(preferredContract!=null&&"Farming Guild".equals(p.getLocation())&&type==preferredContract.getPatchType()&&
                        level>=preferredContract.getLevel()&&supplies.allocation.getOrDefault(preferredContract.getItemId(),0)>=preferredContract.getQuantity()))
                {
                    Crop carriedReplacement=SeedChoices.allocate(List.of(p),level,plantingBag,this::priority,this::override,Collections.emptyMap(),true,this::autoLily).get(p);
                    if(carriedReplacement!=null)activeCrop=carriedReplacement;
                }
                boolean mature=CropReplacement.configurable(type)&&CropReplacement.mature(type,o);
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
            Set<PatchType> carriedSaplings=EnumSet.noneOf(PatchType.class);
            for(FarmRunPatch p:plant)if(p.getPatchType().isTree()&&p.getPatchType()!=PatchType.SPIRIT_TREE&&
                CropCatalog.crops(p.getPatchType()).stream().anyMatch(c->c.getLevel()<=level&&bag.getOrDefault(c.getItemId(),0)>=c.getQuantity()))
                carriedSaplings.add(p.getPatchType());
            int usedSlots=Preparation.slots(bag,Collections.emptyMap(),this::stackable);
            // Shortest Path navigates the active leg; the shared planner alone orders patches.
            pinned=RunOrder.flatten(RunOrder.shared(ready,start,guildFirst,usedSlots>=25?carriedSaplings:Collections.emptySet()));
        }
        if(pinned!=null)
        {
            RunOrder.appendReady(pinned,ready);
            Map<FarmRunPatch,Integer> rank=new HashMap<>();
            for(int i=0;i<pinned.size();i++)rank.put(pinned.get(i),i);
            for(List<FarmRunPatch> list:List.of(ready,plant,visible))
            {list.removeIf(p->!rank.containsKey(p));list.sort(Comparator.comparingInt(rank::get));}
        }
        // A selected, local dry crop can be watered without a new planting allocation.
        // Disease is handled independently before the ordinary planner.
        Set<FarmRunPatch> careOnly=new LinkedHashSet<>();
        if(loggedIn&&!preparing()&&highlight!=null&&!(specialtyStaged&&specialtyBirdPhase))
        {
            for(FarmRunPatch p:patches)
            {
                PatchType type=p.getPatchType();
                if(runProgress.is(p,RunProgress.Status.SKIPPED)||type==PatchType.BIRDHOUSE||type==PatchType.COMPOST_BIN||!reader.inPatchRegion(type.name(),p.getLocation(),p.getName(),location.normalized))continue;
                // Do not let routine watering pull Fossil Island ahead of remaining mainland work.
                // Verified disease interruptions remain independent and can still interrupt.
                if(SpecialtyStages.fossilLastRequired(runPolicyKey)&&SpecialtyStages.fossil(p)&&ready.stream().anyMatch(other->!SpecialtyStages.fossil(other)))continue;
                QuestFarmingStateReader.Observation o=observations.computeIfAbsent(p,this::observe);
                boolean water=ManualSelection.includes(p,run,manualKeys)&&enabled(p)&&!wateredThisRun.contains(p)&&CropCare.waterNeeded(type,o,runWater)&&highlight.hasLocalPatch(p);
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
        List<?> inputs=List.of(List.copyOf(plant),level,supplies,preferenceRevision,committed,hideSeedless,temporaryReplacement,contract,Optional.ofNullable(preferredContract));
        if(!inputs.equals(allocationInputs))
        {
            allocated=SeedChoices.allocate(plant,level,supplies.allocation,this::priority,this::override,committed,hideSeedless,this::autoLily,preferredContract);
            allocationInputs=inputs;
        }
        Map<FarmRunPatch,Crop> seeds=allocated;
        int seedlessCount=0;
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
            seedlessCount=seedless.size();
            ready.removeAll(seedless);visible.removeAll(seedless);
        }
        if(loggedIn)runProgress.update(p->SpecialtyStages.includes(specialtyStaged,specialtyBirdPhase,p.getPatchType()),ready,seedless);
        // Transition only after the remaining farming/seaweed work is exhausted.
        // A fresh pinned order is necessary because birdhouses were deliberately absent.
        if(loggedIn&&specialtyStaged&&!specialtyBirdPhase&&ready.isEmpty()&&run.contains(FarmRunType.BIRDHOUSE))
        {
            seaweedBirdTransfer=SpecialRoutes.insideSeaweed(location.normalized)||SpecialRoutes.onSeaweedIsland(location.normalized)||
                patches.stream().anyMatch(p->p.getPatchType()==PatchType.SEAWEED&&runProgress.is(p,RunProgress.Status.COMPLETED));
            specialtyBirdPhase=true;current=null;activeCrop=null;progress.reset();activeCareOnly=false;activeClearOnly=false;
            phase=Preparation.Phase.ACTIVE;visitedStorage=false;pinned=null;
            update();return;
        }
        FarmRunPatch next=ready.isEmpty()?null:ready.get(0);
        if(next!=current) { current=next;progress.reset();activeCrop=null;compostAcknowledged=false; if(current!=null) progress.observe(observations.get(current)); }
        activeClearOnly=clearOnly.contains(current);
        activeCareOnly=careOnly.contains(current);
        if(current!=null && !progress.planted()) activeCrop=seeds.get(current);
        Map<Integer,Integer> required=Supplies.guideKit(ready.stream().filter(p->p.getPatchType()!=PatchType.BIRDHOUSE&&!careOnly.contains(p)).collect(java.util.stream.Collectors.toList()),
            loggedIn && client.getVarbitValue(VarbitID.FARMING_BLOCKWEEDS)==2,
            loggedIn && client.getVarbitValue(VarbitID.BRUT_FARMING_PLANTING)==3);
        if(!clearOnly.isEmpty()&&seeds.isEmpty()) {required.remove(ItemID.DIBBER);required.remove(ItemID.RAKE);}
        if(loggedIn&&runLeprechaun)required.entrySet().removeIf(e->carried.getOrDefault(e.getKey(),0)==0&&LeprechaunWithdrawal.stored(client,e.getKey())>=e.getValue());
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
            if((c!=null||clearOnly.contains(p))&&type.isTree()&&type!=PatchType.CRYSTAL_TREE&&type!=PatchType.CALQUAT)
            {
                var o=observations.get(p);
                if(o!=null&&(o.state==null||CropReplacement.mature(type,o)))
                {if(type==PatchType.REDWOOD)coins+=2000;else if(pay&&reader.gardenerId(type.name(),p.getLocation(),p.getName())>0)coins+=200;else needsAxe=true;}
            }
            if(protect && (c!=null || (p==current && progress.planted() && activeCrop!=null)))
            {
                Crop paymentCrop=c!=null?c:activeCrop;
                if(type.supportsProtection()) for(ProtectionPayment cost:CropCatalog.protection(paymentCrop))
                    required.merge(cost.getItemId(),cost.getQuantity(),Integer::sum);
            }
        }
        if(coins>0) required.merge(ItemID.COINS,coins,Integer::sum);
        if(needsAxe){Map<Integer,Integer> axeStock=new HashMap<>(stock);equipped.forEach((id,n)->axeStock.merge(id,n,Integer::sum));int axe=TreeAxes.best(axeStock,client.getRealSkillLevel(Skill.WOODCUTTING),this::observedItemName);axeId=axe>0?axe:1351;required.put(axeId,1);}
        Map<Integer,Integer> prepSeeds=TreePreparation.preparation(seeds,stock,aliases,bag,this::observedItemName);
        int rawSeeds=TreePreparation.count(prepSeeds,aliases,this::observedItemName,TreePreparation.Stage.SEED);
        int seedlings=TreePreparation.count(prepSeeds,aliases,this::observedItemName,TreePreparation.Stage.SEEDLING);
        int watered=TreePreparation.count(prepSeeds,aliases,this::observedItemName,TreePreparation.Stage.WATERED);
        prepSeeds.forEach((id,n)->required.merge(id,n,Integer::sum));
        if(rawSeeds>0)
        {
            required.put(TreePreparation.FILLED_POT,rawSeeds);
            required.merge(Supplies.GARDENING_TROWEL,1,Math::max);
        }
        boolean needsCan=rawSeeds+seedlings>0||runWater&&ready.stream().anyMatch(p->p.getPatchType().canBeWatered()&&
            (seeds.containsKey(p)||CropCare.waterNeeded(p.getPatchType(),observations.get(p),true)));
        if(needsCan&&wateringCan<=0&&storedCan<=0)
            required.put(CropCare.preferredCan(wateringCan,storedCan,CropCare.wateringCan(stock,this::observedItemName)),1);
        int vines=(int)ready.stream().filter(p->p.getPatchType()==PatchType.GRAPEVINE&&seeds.containsKey(p)).count();
        if(vines>0){required.merge(Supplies.SALTPETRE,vines,Integer::sum);required.put(Supplies.GARDENING_TROWEL,1);}
        if(compostId!=ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED||storedBucket==0)
            CompostChoice.addRequired(required,compostId,compostId==ItemID.BUCKET_ULTRACOMPOST?compostCount:Math.max(0,compostCount-storedCompost),storedUltra,stock,bag);
        if(ready.stream().anyMatch(p->p.getPatchType()==PatchType.CORAL))
        {
            DivingKit.require(required,supplies.travel,carried);
        }
        Map<Integer,Integer> farmingRequirements=new LinkedHashMap<>(required);
        Map<Integer,Integer> birdStock=specialtyStaged&&specialtyBirdPhase?supplies.bankAndBag:stock;
        int hunter=client.getRealSkillLevel(Skill.HUNTER),crafting=client.getRealSkillLevel(Skill.CRAFTING);
        if(specialtyStaged&&specialtyBirdPhase)
            birdStock=BirdhouseRun.preferCarried(ready,observations,plantingBag,birdStock,required,hunter,crafting);
        Map<FarmRunPatch,String> birdhousePlans=BirdhouseRun.supplies(ready,observations,birdStock,required,hunter,crafting);
        List<GuideView.ItemRow> items=new ArrayList<>();
        Map<Integer,Integer> pendingSaplings=TreePreparation.pending(prepSeeds,aliases);
        Map<Integer,Integer> physicalRequired=TreePreparation.physicalRequirements(required,pendingSaplings);
        for(int id:SupplyOrder.ordered(required,this::observedItemName))
        {
            int n=required.get(id);
            if((CompostChoice.isOrdinary(id)||id==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED)&&bag.getOrDefault(id,0)>=n&&binPlans.isEmpty())continue;
            GuideView.ItemRow row=itemRow(id,n,carried,false);
            int pending=pendingSaplings.getOrDefault(id,0);
            if(pending>0)row=new GuideView.ItemRow(row.id,row.name+" ("+pending+" to prepare)",row.owned,row.needed,row.bank,row.vault,row.recommended);
            items.add(row);
        }
        requiredItems=Set.copyOf(required.keySet());
        missingStorageItems=GuideView.missingItems(physicalRequired,carried);
        Map<Integer,Integer> plantingRequired=SupplyOrder.plantingRequirements(physicalRequired,this::observedItemName);
        // Only observed bank/inventory/equipment stock. Keep the existing bank
        // shelf broad and stable; recommendations never change its membership.
        Map<Integer,Set<String>> teleports=loggedIn&&config!=null&&itemManager!=null
            ?RunTeleports.select(ready,teleports(supplies.travel.keySet()),carried)
            :Collections.emptyMap();
        Set<Integer> missingTravel=new HashSet<>();
        teleports.forEach((id,sites)->{
            GuideView.ItemRow row=itemRow(id,1,carried,true);
            String name=observedItemName(id);
            items.add(new GuideView.ItemRow(id,(name.isEmpty()?row.name:name)+" — "+String.join(", ",sites),
                row.owned,row.needed,row.bank,row.vault,true));
            if(!row.satisfied()&&row.bank>0)missingTravel.add(id);
        });
        missingTeleports=Set.copyOf(missingTravel);
        List<GuideView.Stop> stops=new ArrayList<>();
        GuideView.Stats summary=runProgress.stats();
        String instruction=!loggedIn?"Log in to start this guide.":summary.total==0?"No enabled, unlocked patches.":summary.deferred>0||summary.seedless>0?"No ready patches remain.":"Run finished.";
        activeAction=StepEngine.Action.INSPECT;
        activeLive=false;compostMissing=false;leprechaunWithdrawal=null;
        Set<Integer> localStepItems=Collections.emptySet();
        if(current!=null)
        {
            PatchType type=current.getPatchType();QuestFarmingStateReader.Observation o=observations.get(current);
            activeLive=o.source==QuestFarmingStateReader.Source.LIVE;
            boolean replacementCarried=activeCrop!=null&&plantingBag.getOrDefault(activeCrop.getItemId(),0)>=activeCrop.getQuantity();
            boolean keep=preserveReplacement(current,o);
            int stepCompost=CompostChoice.localItem(compostId,bag,storedUltra);
            StepEngine.Action action=guidanceAction(type,o,keep,replacementCarried,stepCompost);
            activeAction=action;
            if(action==StepEngine.Action.WATER&&waterOfferPatch!=current)
            {waterOfferPatch=current;waterOfferTick=client.getTickCount();waterOfferOrigin=location.actual;}
            if(activeLive&&type.usesCompost()&&!activeClearOnly&&(action==StepEngine.Action.COMPOST||action==StepEngine.Action.PLANT))
                compostMissing=!reader.compostRecorded(type.name(),current.getLocation(),current.getName(),1);
            instruction=type==PatchType.BIRDHOUSE?birdhousePlans.get(current):type==PatchType.COMPOST_BIN?binPlans.get(current).instruction:
                StepEngine.instruction(action,activeCrop,type,keep,activeClearOnly,o.state==CropState.EMPTY);
            if(activeLive&&!preparing()&&type!=PatchType.BIRDHOUSE&&type!=PatchType.COMPOST_BIN)
                leprechaunWithdrawal=contextualWithdrawal(current,action,stepCompost,bag,carried,wateringCan);
            if(leprechaunWithdrawal!=null)
                instruction=leprechaunWithdrawal.instruction()+", then "+instruction;
            else if(action==StepEngine.Action.WATER&&wateringCan<=0)
                instruction="Obtain a filled watering can, then water this patch, or skip.";

            if(activeCareOnly&&action==StepEngine.Action.CURE)instruction="Nearby diseased crop — "+instruction+" Then resume the run without replanting.";
            if(leprechaunWithdrawal==null&&action==StepEngine.Action.CURE&&!CropCare.prunes(type)&&bag.getOrDefault(ItemID.PLANT_CURE,0)==0)instruction+=" Obtain plant cure from a nearby farmer or Farming shop, if available; skip if you cannot cure it.";
            if(activeLive)
            {
                if(type==PatchType.COMPOST_BIN)localStepItems=CompostRun.stepItems(current,o,binPlans.get(current),bag);
                else if(type==PatchType.BIRDHOUSE)localStepItems=BirdhouseRun.stepItems(current,o,birdStock,plantingBag,farmingRequirements,client.getRealSkillLevel(Skill.HUNTER),client.getRealSkillLevel(Skill.CRAFTING));
                else if(action==StepEngine.Action.WATER&&wateringCan>0)localStepItems=Set.of(wateringCan);
                else if(leprechaunWithdrawal==null)localStepItems=StepItems.select(action,type,activeCrop,stepCompost,axeId,bag);
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
            if(type==PatchType.BIRDHOUSE){activeAction=StepEngine.Action.INSPECT;instruction=current.getName()+": "+instruction;}
        }
        for(FarmRunPatch p:visible)
        {
            Crop c=seeds.get(p);
            String line=p==current?instruction:careOnly.contains(p)?(observations.get(p).state==CropState.DISEASED?"Nearby disease — cure only.":"Water the growing crop."):runProgress.finished(p)?(runProgress.is(p,RunProgress.Status.PRESERVED)?"Preserved":"Completed"):birdhousePlans.containsKey(p)?birdhousePlans.get(p):binPlans.containsKey(p)?binPlans.get(p).instruction:clearOnly.contains(p)?"Check health / harvest and clear; no replacement.":c==null?"Check or harvest existing crop.":"Harvest / clear, then plant "+c.getName()+".";
            stops.add(new GuideView.Stop(p,line,p==current,runProgress.finished(p)&&!careOnly.contains(p)));
        }
        // Show the selected birdhouse leg at the bottom without adding its supplies to the farming phase.
        if(loggedIn&&specialtyStaged&&!specialtyBirdPhase&&run.contains(FarmRunType.BIRDHOUSE))
        {
            List<FarmRunPatch> birds=new ArrayList<>();
            for(FarmRunPatch p:patches)if(p.getPatchType()==PatchType.BIRDHOUSE&&ManualSelection.includes(p,run,manualKeys)&&enabled(p)&&!runProgress.is(p,RunProgress.Status.SKIPPED)&&accessNote(p).isEmpty())birds.add(p);
            birds.sort(Comparator.comparingInt(BirdhouseRun::order));
            for(FarmRunPatch p:birds)stops.add(new GuideView.Stop(p,"Birdhouses — after farming.",false,false));
        }
        WorldPoint player=loggedIn?location.normalized:null;
        boolean instance=loggedIn&&location.instance;
        boolean aboard=loggedIn && client.getVarbitValue(VarbitID.SAILING_BOARDED_BOAT)!=0;
        boolean rowboat=loggedIn && client.getVarbitValue(SpecialRoutes.ANGLER_ROWBOAT_VARBIT)==1;
        WorldPoint localApproach=current!=null && activeLive && highlight!=null?highlight.approach(current):null;
        String supplyNote=TreePreparation.instruction(rawSeeds,seedlings,watered);
        if(SeedBoxStock.boxId(bag)>0)
        {
            if(!seedBox.complete())
                supplyNote+=(supplyNote.isEmpty()?"":"\n")+(seedBox.cachedTotal()>0&&!seedBox.known()?
                    "Seed box: "+seedBox.cachedTotal()+" last seen; check to confirm.":"Check the box to confirm any untracked contents.");
            else if(!supplies.seedBox.isEmpty())
                supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Seed box counts included. Withdraw tree seeds and birdhouse bait manually when needed.";
        }
        if(rawSeeds+seedlings>0&&wateringCan<=0&&storedCan>0)supplyNote+="\nCollect the stored watering can from a tool leprechaun before watering seedlings.";
        if(compostCount>0&&compostId==ItemID.BUCKET_ULTRACOMPOST&&storedUltra>0)
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Ultracompost: "+storedUltra+" stored with tool leprechauns; withdraw as needed.";
        if(compostCount>0&&(compostId==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED||required.containsKey(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED)))
            supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Check the bottomless bucket's compost type and charges.";
        StringJoiner details=new StringJoiner("\n");
        long now=System.currentTimeMillis();
        boolean bankStale=GuidePresentation.stale(bankObservedAt,now);
        boolean vaultStale=GuidePresentation.stale(vaultObservedAt,now);
        String storageWarning=loggedIn?GuidePresentation.storageWarning(bankStale,level>=45&&!birdBankPhase()&&vaultStale):"";
        String prepHelp=GuidePresentation.PREPARATION_HELP;
        if(specialtyStaged&&!specialtyBirdPhase&&run.contains(FarmRunType.BIRDHOUSE))
            details.add("Birdhouses are last. Resupply after seaweed if needed.");
        if(specialtyStaged&&specialtyBirdPhase&&current!=null)
            details.add("Birdhouse supplies: logs or houses, hammer, chisel, bait and clockworks.");
        if(loggedIn && current!=null)
        {
            int slots=SeedBoxStock.slots(physicalRequired,equipped,supplies.seedBox,this::stackable);
            if(slots>28){supplyNote+=(supplyNote.isEmpty()?"":"\n")+"Take a partial load and resupply as needed.";details.add("Estimated supply slots: "+slots+".");}
            if(phase==Preparation.Phase.UNCHECKED)
            {
                phase=Preparation.Phase.ACTIVE;
                if(rawSeeds+seedlings>0||Preparation.storedShortage(physicalRequired,carried,storage)){
                    beginPreparation(Preparation.Phase.INITIAL);
                }
            }
        }
        if(seedlessCount>0)details.add(seedlessCount+" patch(es) skipped for missing seeds.");
        if(!clearOnly.isEmpty())details.add(clearOnly.size()+" patch(es) to harvest / clear without replanting.");
        if(current==null&&preparing())phase=Preparation.Phase.ACTIVE;
        // All processed patches remain visible in the completed history across stages.
        Set<FarmRunPatch> listed=new HashSet<>();for(GuideView.Stop stop:stops)listed.add(stop.patch);
        for(FarmRunPatch p:patches)if(runProgress.finished(p)&&!listed.contains(p))stops.add(new GuideView.Stop(p,runProgress.is(p,RunProgress.Status.PRESERVED)?"Preserved":"Completed",false,true));
        String prepMessage="";
        RouteLeg leg=SpecialRoutes.leg(current,player,aboard,rowboat,instance,localApproach);
        if(preparing() && loggedIn)
        {
            Preparation.Bank preparationBank=Preparation.chooseSupplies(level,preparationOrigin,phase==Preparation.Phase.INITIAL,
                startAt(),birdBankPhase(),plantingRequired,carried,bank,vault);
            boolean atStorage=storageOpen();visitedStorage|=atStorage;
            boolean vaultOutstanding=level>=45&&Preparation.vaultNeeded(plantingRequired,carried,bank,vault);
            boolean vaultTarget=preparationBank==Preparation.SEED_VAULT;
            // A known vault shortage may still remain after visiting a regular
            // bank. Only the explicit preparation session can suggest it.
            boolean routeToStorage=!missingStorageItems.isEmpty() && (!visitedStorage||vaultTarget&&vaultOutstanding&&!atStorage);
            prepMessage=GuidePresentation.preparation(routeToStorage,preparationBank.name,missingStorageItems.isEmpty());
            String sources=Preparation.sourceNote(plantingRequired,carried,bank,vault,!bankStale,!vaultStale);
            if(!sources.isEmpty())prepHelp+="\n"+sources;
            if(vaultOutstanding)prepHelp+="\nThe Seed Vault is west of the Guild bank chest.";
            if(physicalRequired.entrySet().stream().anyMatch(e->e.getValue()>carried.getOrDefault(e.getKey(),0)&&SupplyOrder.tool(observedItemName(e.getKey()))))
                prepHelp+="\nCheck a tool leprechaun for missing tools.";
            // Preparation is entered only at run start or by the Resupply button.
            // Keep the explicit Start/Continue action available even when supplies are ready.
            {
                RouteLeg exit=SpecialRoutes.leg(null,player,aboard,rowboat,instance,null);
                if(!routeToStorage)leg=RouteLeg.walk(null,false);
                else if(aboard||instance&&!location.inHouse()||SpecialRoutes.insideHespori(player)||SpecialRoutes.insideSeaweed(player)||SpecialRoutes.insideCoral(player))
                    leg=exit.instruction.isEmpty()?RouteLeg.manual("Leave the current instance before banking.",RouteLeg.Interaction.NONE):exit;
                else leg=RouteLeg.approach(preparationBank.point,vaultTarget?"Travel to the Guild bank approach, then use the Seed Vault immediately west of the chest.":"Travel to "+preparationBank.name+" or another accessible bank.",RouteLeg.Interaction.NONE);
                // Ordinary bank approach is already named above. Preserve actual
                // special-transport/instance instructions when necessary.
                if(routeToStorage&&(aboard||instance&&!location.inHouse()||SpecialRoutes.insideHespori(player)||SpecialRoutes.insideSeaweed(player)||SpecialRoutes.insideCoral(player))
                    &&!leg.instruction.isEmpty())prepMessage+="\n"+leg.instruction;
            }
        }
        if(loggedIn&&SpecialRoutes.isType(current,PatchType.BIRDHOUSE)&&
            (SpecialRoutes.onSeaweedIsland(player)||!preparing()&&seaweedBirdTransfer))
        {
            boolean missing=Preparation.missing(physicalRequired,plantingBag);
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
        stepItems=!preparing()&&leg.interaction==RouteLeg.Interaction.NONE&&activeLive&&atAction?localStepItems:Collections.emptySet();
        if(navigation!=null)navigation.request(loggedIn?location.pathStart():null,leg.point,leg.probe);
        if(location.inHouse()&&!shortestPathAvailable&&current!=null&&!preparing())
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
        cacheIcons(items);
        GuideView view=new GuideView(title,instruction,items,stops,preparing()?null:current,prepMessage,supplyNote,hideSeedless,clearSeedless,!preparing()&&activeAction==StepEngine.Action.COMPOST,!preparing()&&activeAction==StepEngine.Action.WATER,summary,
            new GuideView.Notes(phase==Preparation.Phase.INITIAL,storageWarning,prepHelp,details.toString()));
        normalView=view;
        publishView(view);
    }

    private LeprechaunWithdrawal contextualWithdrawal(FarmRunPatch patch,StepEngine.Action action,int compost,
        Map<Integer,Integer> bag,Map<Integer,Integer> carried,int wateringCan)
    {
        if(!runLeprechaun||highlight==null||!highlight.hasLocalPatch(patch)||highlight.leprechaun(patch)==null)return null;
        int free=Math.max(0,28-Preparation.slots(bag,Collections.emptyMap(),this::stackable));
        return LeprechaunWithdrawal.resolve(client,action,patch.getPatchType(),compost,
            client.getVarbitValue(VarbitID.BRUT_FARMING_PLANTING)==3,bag,carried,wateringCan,free,this::observedItemName);
    }

    private StepEngine.Action guidanceAction(PatchType type,QuestFarmingStateReader.Observation o,boolean keep,
        boolean replacementCarried,int compostId)
    {
        if((progress.planted()||activeCareOnly)&&!wateredThisRun.contains(current)&&CropCare.waterNeeded(type,o,runWater))
            return StepEngine.Action.WATER;
        if(progress.planted()&&o.state==CropState.GROWING)return StepEngine.Action.PROTECT_TREE;
        if(o.state==CropState.EMPTY)
        {
            if(activeLive&&reader.hasWeeds(type.name(),o.rawValue))return StepEngine.Action.RAKE;
            if(activeCrop!=null&&activeLive&&compostId>=0&&type.usesCompost()&&!activeClearOnly&&!compostAcknowledged)
            {
                int minimum=compostId==ItemID.BUCKET_ULTRACOMPOST?3:compostId==ItemID.BUCKET_SUPERCOMPOST?2:1;
                compostAcknowledged=reader.compostRecorded(type.name(),current.getLocation(),current.getName(),minimum);
                if(!compostAcknowledged)return StepEngine.Action.COMPOST;
            }
            if(activeCrop!=null&&type.isTree()&&!replacementCarried)return StepEngine.Action.NEED_REPLACEMENT;
        }
        return StepEngine.action(type,o,keep,replacementCarried,activeClearOnly,runPayRemoval&&type.isTree()&&gardener(current)>0);
    }

    private void publishView(GuideView view)
    {
        if(!view.equals(lastView))
        {
            lastView=view;final long id=session;
            SwingUtilities.invokeLater(()->{ if(running && guideRequested && id==session) panel.guide(view,id); });
        }
    }

    private boolean showDiseaseInterrupt(String title,Map<Integer,Integer> carried,LocationContext location,
        Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations)
    {
        if(highlight==null)return false;
        WorldPoint here=location.actual;
        Map<FarmRunPatch,QuestFarmingStateReader.Observation> verified=new LinkedHashMap<>();
        Map<FarmRunPatch,WorldPoint> approaches=new HashMap<>();
        for(FarmRunPatch p:patches)
        {
            PatchType type=p.getPatchType();
            if(type==PatchType.BIRDHOUSE||type==PatchType.COMPOST_BIN||!reader.inPatchRegion(type.name(),p.getLocation(),p.getName(),location.normalized))continue;
            QuestFarmingStateReader.Observation o=observations.computeIfAbsent(p,this::observe);
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
        if(previous!=target){lastView=null;}
        // Record the disease transition only when the interrupted patch itself is
        // the diseased one, so its existing completion tracker can see recovery.
        if(current==target)progress.observe(verified.get(target));
        PatchType type=target.getPatchType();
        int cure=CropCare.prunes(type)?(carried.getOrDefault(ItemID.FAIRY_ENCHANTED_SECATEURS,0)>0?ItemID.FAIRY_ENCHANTED_SECATEURS:ItemID.SECATEURS):ItemID.PLANT_CURE;
        diseaseItems=Set.of(cure);
        missingStorageItems=GuideView.missingItems(Map.of(cure,1),carried);
        missingTeleports=Collections.emptySet();
        WorldPoint approach=approaches.get(target);
        diseaseLeg=RouteLeg.walk(approach,false);
        diseaseTarget=target;
        if(navigation!=null)navigation.request(location.pathStart(),approach,false);
        String instruction=CropCare.prunes(type)?"Prune the diseased crop with secateurs.":"Use plant cure on the diseased crop.";
        Map<Integer,Integer> bag=stock().bag;
        leprechaunWithdrawal=contextualWithdrawal(target,StepEngine.Action.CURE,-1,bag,carried,-1);
        if(leprechaunWithdrawal!=null)instruction=leprechaunWithdrawal.instruction()+", then "+instruction;
        else if(carried.getOrDefault(cure,0)==0)instruction+=CropCare.prunes(type)?" Obtain secateurs or skip this cure.":" Obtain plant cure from a nearby farmer or shop if available, or skip this cure.";
        String resume=preparing()?"Preparation / resupply":current==null?"The remaining run":current.getDisplayName();
        List<GuideView.ItemRow> items=new ArrayList<>();items.add(itemRow(cure,1,carried,false));
        List<GuideView.Stop> stops=new ArrayList<>();stops.add(new GuideView.Stop(target,instruction,true,false));
        if(normalView!=null)for(GuideView.Stop stop:normalView.stops)if(stop.patch!=target)stops.add(new GuideView.Stop(stop.patch,stop.active?"Paused: "+stop.instruction:stop.instruction,false,stop.done));
        cacheIcons(items);
        publishView(new GuideView(title,"Disease priority",items,stops,target,"","Disease detected — current guide paused. Resume: "+resume+". Cure only; no harvest or replant.",hideSeedless,clearSeedless,false,normalView==null?GuideView.Stats.UNKNOWN:normalView.stats,GuideView.Notes.EMPTY));
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
    private boolean preparing(){return phase==Preparation.Phase.INITIAL||phase==Preparation.Phase.RESUPPLY;}
    private void beginPreparation(Preparation.Phase next)
    {
        preparationOrigin=LocationContext.capture(client).normalized;
        phase=next;visitedStorage=false;
    }
    void continueRun()
    {
        long id=session;thread.invokeLater(()->{if(id!=session||!guideRequested)return;
            if(phase==Preparation.Phase.INITIAL){pinned=null;current=null;activeCrop=null;progress.reset();}
            phase=Preparation.Phase.ACTIVE;update();});
    }
    void resupply()
    {
        long id=session;thread.invokeLater(()->{if(id!=session||!guideRequested||diseaseTarget!=null||current==null)return;sampleStorage();beginPreparation(Preparation.Phase.RESUPPLY);update();});
    }

    private void cacheIcons(List<GuideView.ItemRow> rows)
    {
        if(itemManager==null||rows.stream().allMatch(row->icons.containsKey(row.id)))return;
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
        int boxed=stock().seedBox.getOrDefault(id,0);
        if(boxed>0&&!recommended)name+=" (box: "+boxed+")";
        return new GuideView.ItemRow(id,name,carried.getOrDefault(id,0),count,
            bank.getOrDefault(id,0),vault.getOrDefault(id,0),recommended);
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
    LeprechaunWithdrawal leprechaunWithdrawal(){return guideRequested&&(diseaseTarget!=null||!preparing())?leprechaunWithdrawal:null;}
    boolean compostMissing(){return diseaseTarget==null&&activeLive()&&compostMissing;}
    boolean activeLive() { return guideRequested && (diseaseTarget!=null||activeLive&&!preparing()); }
    int patchCount(FarmRunPatch p) { return QuestFarmingStateReader.patchNamesForType(p.getPatchType().name(),p.getLocation()).size(); }
    Set<Integer> requiredItems() { return !guideRequested?Collections.emptySet():diseaseTarget!=null?diseaseItems:requiredItems; }
    Set<Integer> missingStorageItems() {return guideRequested?missingStorageItems:Collections.emptySet();}
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
        if (canSave()) {config.setRSProfileConfiguration("farming-helper",key,value);if(!key.startsWith("observed"))preferenceRevision++;}
    }

}
