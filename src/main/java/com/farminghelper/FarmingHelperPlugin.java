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

@PluginDescriptor(name = "Farming Helper", description = "Farming guides with stock-aware seed choices",
    tags = {"farming", "seed", "herb", "tree"})
public class FarmingHelperPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private PluginManager plugins;
    private boolean routingAvailable;
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
    private Set<FarmRunType> run = Collections.emptySet();
    private FarmingHelperPanel panel;
    private NavigationButton nav;
    private WorldPoint destination;
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
        routingAvailable = plugins.getPlugins().stream().anyMatch(p ->
            "Shortest Path".equals(p.getName()) && plugins.isPluginActive(p));
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
        reset();
        toolbar.removeNavigation(nav);
        overlays.remove(highlight);
        if (itemHighlight != null) overlays.remove(itemHighlight);
        patches.clear();
        highlight.clear();
    }

    void start(Set<FarmRunType> types)
    {
        if (types.isEmpty()) return;
        guideRequested = true;
        final long requested = ++session;
        thread.invokeLater(() -> {
            if (!running || !guideRequested || requested != session) return;
            clearRun();
            run = EnumSet.copyOf(types);
            sampleStorage();
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
        navigate(null);
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
        ++session;
        lastAccessLevel=-1;
        accessNotes=Collections.emptyMap();
        bank.clear();
        vault.clear();
        end();
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
            routingAvailable = event.isLoaded();
            destination = null;
            if (!run.isEmpty()) update();
        });
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
        if (!run.isEmpty() && guideRequested) update();
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
    }

    private void sampleStorage()
    {
        observeStorage(InterfaceID.Bankmain.UNIVERSE, InventoryID.BANK, bank);
        observeStorage(InterfaceID.SeedVault.UNIVERSE, InventoryID.SEED_VAULT, vault);
    }

    private void observeStorage(int component, InventoryID id, Map<Integer,Integer> snapshot)
    {
        if (client.getWidget(component) == null || client.getWidget(component).isHidden()) return;
        ItemContainer container = client.getItemContainer(id);
        if (container == null) return;
        snapshot.clear();
        addItems(snapshot, container);
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
        return state != CropState.GROWING && state != CropState.FILLING;
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
            if (issue == null && CropCatalog.recommend(patch.getPatchType(),level) == null) issue = "Farming level too low";
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
        String title = run.size() == 1 ? run.iterator().next().getDisplayName() : "Combined run";
        boolean loggedIn = client.getLocalPlayer()!=null && client.getGameState()==GameState.LOGGED_IN;
        int level = client.getRealSkillLevel(Skill.FARMING);
        Map<Integer,Integer> bag=new HashMap<>(), carried=new HashMap<>();
        addItems(bag,client.getItemContainer(InventoryID.INVENTORY));
        carried.putAll(bag); addItems(carried,client.getItemContainer(InventoryID.EQUIPMENT));
        Map<Integer,Integer> stock=new HashMap<>(bag);
        bank.forEach((id,n)->stock.merge(id,n,Integer::sum));
        vault.forEach((id,n)->stock.merge(id,n,Integer::sum));
        boolean protect=flag("directorProtectTrees",true), pay=flag("directorPayRemoval",true);
        CompostChoice compost=CompostChoice.load(setting("directorCompostChoice",""),flag("directorUseCompost",true));
        int compostId=compost.resolve(stock.containsKey(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED));
        List<FarmRunPatch> ready=new ArrayList<>(), plant=new ArrayList<>(), visible=new ArrayList<>();
        Map<FarmRunPatch,QuestFarmingStateReader.Observation> observations=new LinkedHashMap<>();
        int eligible=0;
        int spiritSlots=spiritCapacity(level);
        if(loggedIn && run.contains(FarmRunType.SPECIALTY) && level<99)
            for(FarmRunPatch p:patches) if(p.getPatchType()==PatchType.SPIRIT_TREE)
            { CropState state=observe(p).state; if(state!=null && state!=CropState.EMPTY && state!=CropState.DEAD) spiritSlots--; }
        if(loggedIn) for(FarmRunPatch p:patches)
        {
            PatchType type=p.getPatchType();
            if(!run.contains(p.getFarmRunType()) || !enabled(p) || skipped.contains(p) ||
                Waypoints.accessIssue(client,type,p.getLocation(),level,false)!=null || CropCatalog.recommend(type,level)==null) continue;
            eligible++;
            QuestFarmingStateReader.Observation o=observe(p);
            if(o==null) continue;
            observations.put(p,o);
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
            if(!StepEngine.keepExisting(type,o.state)) { plant.add(p); if(type==PatchType.SPIRIT_TREE) spiritSlots--; }
        }
        // Keep the active step stable while the player is working, rather than jumping when another patch matures.
        if(current!=null && ready.remove(current)) ready.add(0,current);
        if(current!=null && plant.remove(current)) plant.add(0,current);
        FarmRunPatch next=ready.isEmpty()?null:ready.get(0);
        if(next!=current) { current=next;progress.reset();activeCrop=null; if(current!=null) progress.observe(observations.get(current)); }
        Map<FarmRunPatch,Crop> seeds=SeedChoices.allocate(plant,level,stock,this::priority,this::override);
        if(current!=null && !progress.planted()) activeCrop=seeds.get(current);
        Map<Integer,Integer> required=Supplies.guideKit(ready,-1,false,false,
            loggedIn && client.getVarbitValue(VarbitID.FARMING_BLOCKWEEDS)==2,
            loggedIn && client.getVarbitValue(VarbitID.BRUT_FARMING_PLANTING)==3);
        Map<Integer,Integer> recommended=new LinkedHashMap<>();
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
        if(compostId>=0 && compostCount>0) recommended.merge(compostId,compostId==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED?1:compostCount,Integer::sum);
        if(ready.stream().anyMatch(p->p.getPatchType()==PatchType.HERB)) recommended.put(ItemID.FAIRY_ENCHANTED_SECATEURS,1);
        List<GuideView.ItemRow> items=new ArrayList<>();
        required.forEach((id,n)->items.add(itemRow(id,n,carried,false)));
        recommended.forEach((id,n)->items.add(itemRow(id,n+required.getOrDefault(id,0),carried,true)));
        requiredItems=Set.copyOf(required.keySet());recommendedItems=Set.copyOf(recommended.keySet());
        List<GuideView.Stop> stops=new ArrayList<>();
        String instruction=!loggedIn?"Log in to start this guide.":eligible==0?"No enabled, unlocked patches remain.":completed.isEmpty()?"Wait for plants to grow.":"Run complete. Close the helper to start another run.";
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
            instruction=StepEngine.instruction(action,activeCrop,type,compostId>=0);
        }
        for(FarmRunPatch p:visible)
        {
            Crop c=seeds.get(p);
            String line=p==current?instruction:completed.contains(p)?"Completed":c==null?"Check or harvest existing crop.":"Harvest / clear, then plant "+c.getName()+".";
            stops.add(new GuideView.Stop(p,line,p==current,completed.contains(p)));
        }
        WorldPoint player=loggedIn?client.getLocalPlayer().getWorldLocation():null;
        if(loggedIn && current!=null && current.getPatchType()==PatchType.HESPORI && client.getLocalPlayer().getLocalLocation()!=null)
            player=WorldPoint.fromLocalInstance(client,client.getLocalPlayer().getLocalLocation());
        WorldPoint point=current==null?null:Waypoints.navigationTarget(current,player);
        if(current!=null)
        {
            String entrance=Waypoints.entranceInstruction(current,player);
            if(!entrance.isEmpty())
            {
                instruction=entrance+" "+instruction;
                for(int i=0;i<stops.size();i++) if(stops.get(i).active) stops.set(i,new GuideView.Stop(current,instruction,true,false));
            }
        }
        navigate(point);
        GuideView view=new GuideView(title,instruction,items,stops,current);
        String signature=view.signature();
        if(!signature.equals(lastView))
        {
            lastView=signature;final long id=session;
            SwingUtilities.invokeLater(()->{ if(running && guideRequested && id==session) panel.guide(view); });
        }
    }

    private GuideView.ItemRow itemRow(int id,int count,Map<Integer,Integer> carried,boolean recommended)
    {
        Crop crop=CropCatalog.findByItemId(id);
        return new GuideView.ItemRow(id,crop==null?Supplies.name(id):crop.getItemName(),carried.getOrDefault(id,0),count,
            bank.getOrDefault(id,0),vault.getOrDefault(id,0),recommended);
    }

    String override(FarmRunPatch p) { return setting("directorCrop_"+p.key(),""); }
    String accessNote(FarmRunPatch p) { return accessNotes.getOrDefault(p.key(),""); }
    StepEngine.Action activeAction() { return activeAction; }
    boolean activeLive() { return activeLive && guideRequested; }
    int patchCount(FarmRunPatch p) { return reader.patchNamesForType(p.getPatchType().name(),p.getLocation()).size(); }
    Set<Integer> requiredItems() { return guideRequested ? requiredItems : Collections.emptySet(); }
    Set<Integer> recommendedItems() { return guideRequested ? recommendedItems : Collections.emptySet(); }
    int gardener(FarmRunPatch p) { return reader.gardenerId(p.getPatchType().name(),p.getLocation(),p.getName()); }

    private void navigate(WorldPoint point)
    {
        if (!routingAvailable || Objects.equals(destination,point)) return;
        if (destination != null) bus.post(new PluginMessage("shortestpath","clear"));
        destination = point;
        if (point != null && client.getLocalPlayer() != null)
            bus.post(new PluginMessage("shortestpath","path",
                Map.of("start",client.getLocalPlayer().getWorldLocation(),"target",point)));
    }

    FarmRunPatch activePatch() { return guideRequested ? current : null; }

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
        if (canSave()) config.setRSProfileConfiguration("farming-helper",key,value);
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
