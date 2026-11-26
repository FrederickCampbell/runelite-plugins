package com.farminghelper.queststate;

import com.farminghelper.LocationContext;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.client.plugins.timetracking.farming.Produce;

/**
 * Standalone adapter around Quest Helper's actual FarmingWorld/varbit decoder.
 * Reads live varbits at a nearby patch or RuneLite's Time Tracking snapshot
 * elsewhere. Missing data stays UNKNOWN: never silently means "empty".
 */
@Singleton
public final class QuestFarmingStateReader
{
    private static final int LIVE_TILE_RADIUS = 18;
    private static final String TIME_TRACKING = TimeTrackingConfig.CONFIG_GROUP;
    private final Client client;
    private final ConfigManager config;
    private static final FarmingWorld world=new FarmingWorld();
    private static final Map<String,Map<String,FarmingPatch>> sites=indexSites();
    private static Map<String,Map<String,FarmingPatch>> indexSites()
    {
        Map<String,Map<String,FarmingPatch>> sites=new HashMap<>();
        world.getTabs().values().forEach(patches->patches.forEach(patch->{
            String key=patch.getImplementation()+"~"+normalize(patch.getRegion().getName());
            Map<String,FarmingPatch> names=sites.computeIfAbsent(key,k->new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER));
            if(names.put(patch.getName(),patch)!=null)throw new IllegalStateException("Duplicate farming patch "+key+" "+patch.getName());
        }));
        return sites;
    }

    private static Map<String,FarmingPatch> site(String type,String location)
    {
        return sites.getOrDefault(implementation(type,location)+"~"+normalize(location),Collections.emptyMap());
    }

    static FarmingPatch lookup(String type,String location,String name)
    {
        return name==null?null:site(type,location).get(name);
    }

    private final FarmingHandler handler;
    private final Map<String,String> recent=new HashMap<>();
    private final java.util.Set<String> staleCompost=new java.util.HashSet<>();
    private String recentProfile;

    @Inject
    public QuestFarmingStateReader(Client client, ConfigManager config)
    {
        this.client = client;
        this.config = config;
        handler = new FarmingHandler(client, config);
    }

    public enum Source
    {
        LIVE, TIME_TRACKING, OBSERVED, ADVISOR, NONE
    }

    public static final class Observation
    {
        public final CropState state;
        public final Source source;
        public final long observedAtMillis;
        @Nullable public final Boolean protectedByFarmer;
        public final String patchName;
        public final int rawValue;

        public Observation(@Nullable CropState state, Source source, long time,
                           @Nullable Boolean protection, String patchName)
        {
            this(state, source, time, protection, patchName, -1);
        }

        public Observation(@Nullable CropState state, Source source, long time,
                           @Nullable Boolean protection, String patchName, int rawValue)
        {
            this.rawValue = rawValue;
            this.state = state;
            this.source = source;
            this.observedAtMillis = time;
            this.protectedByFarmer = protection;
            this.patchName = patchName;
        }

        public boolean known()
        {
            return state != null && source != Source.NONE;
        }

    }

    /**
     * Full patch-type-aware state lookup. Several categories share RuneLite's
     * tab (e.g. hardwood and ordinary trees), so tab alone is insufficient.
     * Pass the Advisor patch enum name; mapping is verified against Quest
     * Helper's PatchImplementation definitions.
     */
    public Observation observeType(String patchType, String location,
                                   String patchName, WorldPoint worldPoint)
    {
        if (client.getGameState() != GameState.LOGGED_IN ||
            config.getRSProfileKey() == null)
        {
            return new Observation(null, Source.NONE, 0, null, "");
        }

        String profile=config.getRSProfileKey();
        if(!java.util.Objects.equals(profile,recentProfile)){recent.clear();staleCompost.clear();recentProfile=profile;}
        FarmingPatch match = lookup(patchType,location,patchName);
        if (match == null)
        {
            return new Observation(null, Source.NONE, 0, null, "");
        }
        PatchImplementation implementation=match.getImplementation();
        Boolean protection = config.getRSProfileConfiguration(TIME_TRACKING,
            match.configKey() + "." + TimeTrackingConfig.PROTECTED, Boolean.class);

        WorldPoint player = client.getLocalPlayer() == null ? null
            : client.getLocalPlayer().getWorldLocation();
        if (client.getLocalPlayer()!=null && client.getLocalPlayer().getLocalLocation()!=null)
            player = LocationContext.normalize(client,client.getLocalPlayer().getLocalLocation(),player);
        boolean near = player != null && inRegion(match,player) &&
            (worldPoint == null
                ? player.getRegionID() == match.getRegion().getRegionID()
                : (implementation==PatchImplementation.CORAL || implementation==PatchImplementation.SEAWEED
                    ? player.distanceTo2D(worldPoint)<=LIVE_TILE_RADIUS
                    : player.getPlane() == worldPoint.getPlane() && player.distanceTo(worldPoint) <= LIVE_TILE_RADIUS));
        boolean kastoriFlower = implementation==PatchImplementation.FLOWER && "Kastori".equals(location);
        boolean bin = implementation==PatchImplementation.COMPOST || implementation==PatchImplementation.BIG_COMPOST;
        if (near && (kastoriFlower || bin) && !confirmedObject(match,worldPoint,kastoriFlower))
            return new Observation(null,Source.NONE,0,protection,match.getName());
        if (near)
        {
            int value = client.getVarbitValue(match.getVarbit());
            PatchState raw = match.getImplementation().forVarbitValue(value);
            if (raw != null)
            {
                CropState state = normalizedState(match.getImplementation(), value, raw);
                long now=System.currentTimeMillis();
                String key=match.configKey(),old=recent.get(key);
                if(old==null || !old.startsWith(value+":"))
                {
                    if(old!=null&&(state==CropState.EMPTY||state==CropState.DEAD))
                    {
                        int prior=Integer.parseInt(old,0,old.indexOf(':'),10);
                        CropState previous=normalizedState(implementation,prior,implementation.forVarbitValue(prior));
                        if(previous!=null&&previous!=state)staleCompost.add(key);
                    }
                    recent.put(key,value+":"+(now/1000));
                }
                // An observed tracker reset permits a subsequent treatment record.
                if(staleCompost.contains(key)&&config.getRSProfileConfiguration(TIME_TRACKING,key+".compost")==null)
                    staleCompost.remove(key);
                return new Observation(state, Source.LIVE, now,
                    protection, match.getName(), value);
            }
            // A local undecodable value is not evidence that the historical crop
            // is still growing. Inspect rather than silently deferring or planting.
            return new Observation(null,Source.NONE,0,protection,match.getName());
        }

        // Old or cross-region flower snapshots must never suppress a Kastori visit.
        if(kastoriFlower) return new Observation(null,Source.NONE,0,protection,match.getName());
        String raw = config.getConfiguration(TIME_TRACKING, profile, match.configKey());
        String observed=recent.get(match.configKey());
        long localTime=timestampMillis(observed),trackerTime=timestampMillis(raw);
        // Tracker timestamps have one-second precision; retain our last verified
        // live value on an equal-time conflict rather than resurrecting old state.
        boolean own=localTime>0&&localTime>=trackerTime;
        if(own)raw=observed;
        long observedAtMillis = own ? localTime : trackerTime;
        if (observedAtMillis <= 0 || observedAtMillis > System.currentTimeMillis())
        {
            return new Observation(null, Source.NONE, 0, protection, match.getName());
        }
        CropState state = handler.predictObserved(match,profile,raw);
        return new Observation(state, state == null ? Source.NONE : own?Source.OBSERVED:Source.TIME_TRACKING,
            observedAtMillis, protection, match.getName(), Integer.parseInt(raw,0,raw.indexOf(':'),10));
    }

    public static Produce livingProduce(String type,Observation o)
    {
        if(o==null||o.rawValue<0||o.state==null||o.state==CropState.EMPTY||o.state==CropState.DEAD||
            o.state==CropState.STUMP||o.state==CropState.DISEASED)return null;
        PatchImplementation impl=implementation(type);
        PatchState raw=impl==null?null:impl.forVarbitValue(o.rawValue);
        return raw==null||raw.getProduce()==Produce.WEEDS?null:raw.getProduce();
    }
    public static String livingProduceKey(String type,Observation o)
    {
        Produce produce=livingProduce(type,o);
        return produce==null?"":produce.name();
    }
    public static boolean isLivingContractCrop(String type,Observation o,int contract)
    {
        if(contract<=0)return false;
        Produce produce=livingProduce(type,o);
        return produce!=null&&produce.getContractVarbitValue()==contract;
    }

    public boolean hasWeeds(String type,int value)
    {
        PatchImplementation impl=implementation(type);
        if(impl==null || value<0)return false;
        PatchState raw=impl.forVarbitValue(value);
        // The hops decoder explicitly identifies additional Rake-action weed
        // values (including after dead/harvested crops). Do not treat them as clean soil.
        return raw!=null && raw.getProduce()==Produce.WEEDS && raw.getStage()>0
            && (impl==PatchImplementation.HOPS||value>=0&&value<=2)
            && impl!=PatchImplementation.GRAPES && impl!=PatchImplementation.HESPORI && impl!=PatchImplementation.CORAL;
    }

    /** Actual bin contents from the existing Quest Helper varbit decoder. Stage is
     * zero-based for filling/harvesting. Closed ripe bins retain their full capacity. */
    public static final class BinContents
    {
        public final CropState state;
        public final String kind;
        public final int count,capacity;
        public final boolean closed;
        BinContents(CropState state,String kind,int count,int capacity,boolean closed)
        {this.state=state;this.kind=kind;this.count=count;this.capacity=capacity;this.closed=closed;}
    }
    public static BinContents binContents(boolean large,Observation o)
    {
        if(o==null||!o.known()||o.rawValue<0)return null;
        PatchImplementation impl=large?PatchImplementation.BIG_COMPOST:PatchImplementation.COMPOST;
        PatchState raw=impl.forVarbitValue(o.rawValue);if(raw==null)return null;
        CropState state=normalizedState(impl,o.rawValue,raw);
        // A predicted mature remote bin still requires live verification.
        if(state==CropState.GROWING && o.state!=CropState.GROWING)return null;
        String kind=raw.getProduce().name().replace("BIG_","");
        int capacity=large?30:15;
        boolean closed=raw.getCropState()==CropState.GROWING;
        int count=state==CropState.EMPTY?0:closed?capacity:raw.getStage()+1;
        return new BinContents(state,kind,count,capacity,closed);
    }

    /** Historical fallback only. A live crop removal invalidates a previous
     * treatment until the upstream tracker has been observed to reset. */
    public int compostTier(String type,String location,String name)
    {
        FarmingPatch patch=lookup(type,location,name);
        if(patch==null)return 0;
        String key=patch.configKey(),value=config.getRSProfileConfiguration(TIME_TRACKING,key+".compost");
        if(value==null){staleCompost.remove(key);return 0;}
        if(staleCompost.contains(key))return 0;
        switch(value.toUpperCase(Locale.ROOT))
        {
            case "ULTRACOMPOST":return 3;
            case "SUPERCOMPOST":return 2;
            case "COMPOST":return 1;
            default:return 0;
        }
    }
    public boolean compostRecorded(String type,String location,String name,int minimum)
    {return compostTier(type,location,name)>=minimum;}

    /** Existing Quest Helper registry supplies the actual gardener NPC ID. */
    public int gardenerId(String patchType, String location, String patchName)
    {
        FarmingPatch patch=lookup(patchType,location,patchName);
        return patch == null ? -1 : patch.getFarmer();
    }

    public int patchVarbit(String type,String location,String name)
    {
        FarmingPatch patch=lookup(type,location,name);
        return patch==null?-1:patch.getVarbit();
    }

    public boolean inPatchRegion(String type,String location,String name,WorldPoint point)
    {
        if(point==null)return false;
        FarmingPatch patch=lookup(type,location,name);
        return patch!=null && inRegion(patch,point);
    }

    private WorldPoint regionTile;
    private Collection<FarmingRegion> regions;

    private boolean inRegion(FarmingPatch patch,WorldPoint point)
    {
        PatchImplementation impl=patch.getImplementation();
        if(impl==PatchImplementation.CORAL && point.getRegionID()!=13194)return false;
        if(impl==PatchImplementation.SEAWEED && (point.getRegionID()!=15008||point.getPlane()!=1))return false;
        if(impl==PatchImplementation.HESPORI && point.getRegionID()!=5021)return false;
        if (!point.equals(regionTile))
        {
            regions = world.getRegionsForLocation(point);
            regionTile = point;
        }
        return regions.contains(patch.getRegion());
    }

    public static List<String> patchNamesForType(String type,String location)
    {
        return List.copyOf(site(type,location).keySet());
    }

    static PatchImplementation implementation(String type,String location)
    {
        return "COMPOST_BIN".equals(type) && "Farming Guild".equals(location) ? PatchImplementation.BIG_COMPOST : implementation(type);
    }

    @Nullable
    static PatchImplementation implementation(String type)
    {
        if (type == null) return null;
        String normalized = "GRAPEVINE".equals(type) ? "GRAPES" : "COMPOST_BIN".equals(type) ? "COMPOST" : type;
        try
        {
            return PatchImplementation.valueOf(normalized);
        }
        catch (IllegalArgumentException ex)
        {
            return null;
        }
    }

    private Iterable<GameObject> objects=Collections.emptyList();

    public void useSceneObjects(Iterable<GameObject> objects){this.objects=objects;}

    boolean confirmedObject(FarmingPatch match,WorldPoint marker,boolean flower)
    {
        WorldView view=client.getLocalPlayer()==null?null:client.getLocalPlayer().getWorldView();
        if(marker==null||view==null)return false;
        for(GameObject object:objects)
        {
            if(object.getWorldView()!=view || object.getPlane()!=marker.getPlane())continue;
            ObjectComposition base=client.getObjectDefinition(object.getId());
            if(base==null||base.getVarbitId()!=match.getVarbit())continue;
            ObjectComposition actual=base.getImpostorIds()==null?base:base.getImpostor();
            if(actual==null||!matchesLiveName(actual.getName(),flower))continue;
            var min=object.getSceneMinLocation();
            if(min==null)continue;
            WorldArea footprint=new WorldArea(view.getBaseX()+min.getX(),view.getBaseY()+min.getY(),
                Math.max(1,object.sizeX()),Math.max(1,object.sizeY()),object.getPlane());
            if(footprint.distanceTo(marker)<=(flower?6:25))return true;
        }
        return false;
    }
    public static boolean matchesLiveName(String name,boolean flower)
    {
        if(name==null)return false;
        String n=name.toLowerCase(Locale.ROOT);
        return flower?(n.contains("flower patch")||n.equals("flowers")||n.contains("marigold")||n.contains("rosemary")||n.contains("nasturtium")||n.contains("woad")||n.contains("limpwurt")||n.contains("lily")||n.contains("lilies")):n.contains("compost bin");
    }

    /** Some upstream special patches intentionally use GROWING for their
     * final stage. The Director needs their actionable state, not just their
     * presence. Values come from the vendored decoder's documented actions. */
    static CropState normalizedState(PatchImplementation implementation, int value, PatchState raw)
    {
        if (raw == null) return null;
        if (implementation==PatchImplementation.COMPOST && (value==94||value==126) || implementation==PatchImplementation.BIG_COMPOST && (value==93||value==99||value==222)) return CropState.HARVESTABLE;
        if (raw.getProduce() == Produce.WEEDS) return CropState.EMPTY;
        if (implementation == PatchImplementation.CORAL && (value==8 || value==19 || value==30)) return CropState.HARVESTABLE;
        if (implementation == PatchImplementation.ANIMA &&
            (value == 16 || value == 25 || value == 34)) return CropState.DEAD;
        if (awaitingHealthCheck(implementation,value)) return CropState.UNCHECKED;
        // Mature redwoods and harvested celastrus are distinct from harvestable fruit.
        // Leave permanent woodcutting trees in place; celastrus can be removed after bark harvest.
        if (implementation == PatchImplementation.REDWOOD && raw.getCropState() == CropState.HARVESTABLE)
            return CropState.GROWING;
        if (implementation == PatchImplementation.CELASTRUS && value == 17)
            return CropState.HARVESTABLE;
        if ((implementation == PatchImplementation.BUSH || implementation == PatchImplementation.CACTUS)
            && raw.getCropState() == CropState.HARVESTABLE && raw.getStage() == 0)
            return CropState.GROWING; // No produce left; regrowth is not a new planting.
        return raw.getCropState();
    }

    /** Explicit Check-health action values documented in the vendored decoder.
     * Do not infer this from the final growth stage: several ordinary growing
     * values share the stage index, but do not have the health-check action. */
    /** Raw decoder distinguishes a mature crop with no produce from true growth.
     * Keep existing normalized Time Tracking semantics; only the guide uses this distinction. */
    public static boolean matureDepleted(String type,int value)
    {
        if(value<0)return false;
        PatchImplementation implementation;
        try{implementation=PatchImplementation.valueOf(type);}catch(IllegalArgumentException e){return false;}
        PatchState raw=implementation.forVarbitValue(value);
        if(raw==null||awaitingHealthCheck(implementation,value)||raw.getCropState()!=CropState.HARVESTABLE)return false;
        switch(implementation)
        {
            case BUSH: case CACTUS: case FRUIT_TREE: case CALQUAT:return raw.getStage()==0;
            case CELASTRUS:return value==17;
            case REDWOOD:return true;
            default:return false;
        }
    }
    static boolean awaitingHealthCheck(PatchImplementation implementation,int value)
    {
        switch(implementation)
        {
            case FRUIT_TREE: return value==34||value==61||value==98||value==125||value==162||value==189||value==226||value==253;
            case TREE: return value==12||value==21||value==32||value==45||value==60;
            case HARDWOOD_TREE: return value==15||value==38||value==63||value==88||value==114;
            case BUSH: return value>=250&&value<=255;
            case CALQUAT: return value==34;
            case SPIRIT_TREE: return value==44;
            case REDWOOD: return value==37;
            case CELASTRUS: return value==13;
            case CRYSTAL_TREE: return value==14;
            case CACTUS: return value==31||value==58;
            default:return false;
        }
    }

    /** The RuneLite tracker stores: varbit-value:unix-seconds. */
    public static long timestampMillis(String raw)
    {
        if (raw == null) return 0;
        int separator = raw.indexOf(':');
        if (separator <= 0 || separator == raw.length() - 1) return 0;
        try
        {
            int value = Integer.parseInt(raw, 0, separator, 10);
            long epochSeconds = Long.parseLong(raw, separator + 1, raw.length(), 10);
            if (value < 0 || epochSeconds <= 0)
            {
                return 0;
            }
            return Math.multiplyExact(epochSeconds, 1000L);
        }
        catch (IllegalArgumentException | ArithmeticException ignored)
        {
            return 0;
        }
    }

    private static String normalize(String location)
    {
        String normalized = location.trim().toLowerCase(Locale.ROOT);
        return "harmony island".equals(normalized) ? "harmony" : normalized;
    }
}
