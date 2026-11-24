package com.farminghelper.queststate;

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
    private final FarmingWorld world;
    private final FarmingHandler handler;
    private final Map<String,String> recent=new HashMap<>();
    private String recentProfile;

    @Inject
    public QuestFarmingStateReader(Client client, ConfigManager config)
    {
        this.client = client;
        this.config = config;
        world = new FarmingWorld();
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

        public boolean isFreshSince(long sinceMillis)
        {
            return source == Source.LIVE || (observedAtMillis > sinceMillis && sinceMillis > 0);
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
        PatchImplementation implementation = implementation(patchType,location);
        if (implementation == null)
        {
            return new Observation(null, Source.NONE, 0, null, "");
        }
        return observeResolved(implementation.getTab(), location, patchName,
            worldPoint, implementation);
    }

    public static boolean isLivingWhiteLily(Observation o)
    {
        if(o==null||o.rawValue<0||o.state==null||o.state==CropState.DEAD||o.state==CropState.EMPTY||o.state==CropState.DISEASED)return false;
        PatchState raw=PatchImplementation.FLOWER.forVarbitValue(o.rawValue);
        return raw!=null&&raw.getProduce()==Produce.WHITE_LILY;
    }

    /** Preserve an established crop assigned by Guildmaster Jane. The
     * contract mapping comes from RuneLite's actual Produce metadata. */
    public static boolean isLivingContractCrop(String type,Observation o,int contract)
    {
        if(contract<=0||o==null||o.rawValue<0||o.state==null||o.state==CropState.DEAD||o.state==CropState.EMPTY||o.state==CropState.DISEASED||o.state==CropState.STUMP)return false;
        PatchImplementation impl=implementation(type);
        PatchState state=impl==null?null:impl.forVarbitValue(o.rawValue);
        return state!=null&&state.getProduce().getContractVarbitValue()==contract;
    }

    public boolean hasWeeds(String type,int value)
    {
        PatchImplementation impl=implementation(type);
        if(impl==null || value<0)return false;
        PatchState raw=impl.forVarbitValue(value);
        return raw!=null && raw.getProduce()==Produce.WEEDS && value>=0 && value<=2 && raw.getStage()>0
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

    /** Existing Quest Helper registry supplies the actual gardener NPC ID. */
    public int gardenerId(String patchType, String location, String patchName)
    {
        PatchImplementation impl = implementation(patchType,location);
        FarmingPatch patch = impl == null ? null : find(world, impl.getTab(), location, patchName, impl);
        return patch == null ? -1 : patch.getFarmer();
    }

    public int patchVarbit(String type,String location,String name)
    {
        PatchImplementation impl=implementation(type,location);
        FarmingPatch patch=impl==null?null:find(world,impl.getTab(),location,name,impl);
        return patch==null?-1:patch.getVarbit();
    }

    public boolean inPatchRegion(String type,String location,String name,WorldPoint point)
    {
        if(point==null)return false;
        PatchImplementation impl=implementation(type,location);
        FarmingPatch patch=impl==null?null:find(world,impl.getTab(),location,name,impl);
        if(patch==null)return false;
        if(impl==PatchImplementation.CORAL && point.getRegionID()!=13194)return false;
        if(impl==PatchImplementation.HESPORI && point.getRegionID()!=5021)return false;
        return world.getRegionsForLocation(point).contains(patch.getRegion());
    }

    public List<String> patchNamesForType(String patchType, String location)
    {
        PatchImplementation impl = implementation(patchType,location);
        return impl == null ? Collections.emptyList()
            : patchNamesResolved(impl.getTab(), location, impl);
    }

    /** Geographical fallback derived from Quest Helper's actual world regions.
     * A region centre is approximate, never a verified patch tile.
     */
    @Nullable
    public WorldPoint regionHint(String patchType, String location)
    {
        PatchImplementation impl = implementation(patchType,location);
        if (impl == null) return null;
        FarmingPatch patch = find(world, impl.getTab(), location, null, impl);
        if (patch == null) return null;
        int region = patch.getRegion().getRegionID();
        return new WorldPoint(((region >> 8) & 0xff) * 64 + 32,
            (region & 0xff) * 64 + 32, 0);
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

    /**
     * Call on RuneLite's client thread. Regions follow Quest Helper's registry
     * including the nonintuitive Harmony vs Harmony Island display name.
     */
    public Observation observe(Tab tab, String location, WorldPoint worldPoint)
    {
        return observe(tab, location, null, worldPoint);
    }

    public Observation observe(Tab tab, String location, String patchName, WorldPoint worldPoint)
    {
        return observeResolved(tab, location, patchName, worldPoint, null);
    }

    private Observation observeResolved(Tab tab, String location, String patchName,
                                        WorldPoint worldPoint, PatchImplementation implementation)
    {
        if (client.getGameState() != GameState.LOGGED_IN ||
            config.getRSProfileKey() == null)
        {
            return new Observation(null, Source.NONE, 0, null, "");
        }

        String profile=config.getRSProfileKey();
        if(!java.util.Objects.equals(profile,recentProfile)){recent.clear();recentProfile=profile;}
        FarmingPatch match = find(world, tab, location, patchName, implementation);
        if (match == null)
        {
            return new Observation(null, Source.NONE, 0, null, "");
        }
        Boolean protection = config.getRSProfileConfiguration(TIME_TRACKING,
            match.configKey() + "." + TimeTrackingConfig.PROTECTED, Boolean.class);

        WorldPoint player = client.getLocalPlayer() == null ? null
            : client.getLocalPlayer().getWorldLocation();
        if (client.getLocalPlayer()!=null && client.getLocalPlayer().getWorldView()!=null && client.getLocalPlayer().getWorldView().isInstance() && client.getLocalPlayer().getLocalLocation()!=null)
            player = WorldPoint.fromLocalInstance(client,client.getLocalPlayer().getLocalLocation());
        boolean near = player != null && inPatchRegion(implementation==null?match.getImplementation().name():implementation.name(),location,patchName,player) &&
            (worldPoint == null
                ? player.getRegionID() == match.getRegion().getRegionID()
                : (implementation==PatchImplementation.CORAL ? player.distanceTo2D(worldPoint)<=LIVE_TILE_RADIUS :
                    player.getPlane() == worldPoint.getPlane() && player.distanceTo(worldPoint) <= LIVE_TILE_RADIUS));
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
                String old=recent.get(match.configKey());
                if(old==null || !old.startsWith(value+":"))recent.put(match.configKey(),value+":"+(System.currentTimeMillis()/1000));
                return new Observation(state, Source.LIVE, System.currentTimeMillis(),
                    protection, match.getName(), value);
            }
        }

        // Old or cross-region flower snapshots must never suppress a Kastori visit.
        if(kastoriFlower) return new Observation(null,Source.NONE,0,protection,match.getName());
        String raw = config.getConfiguration(TIME_TRACKING, profile, match.configKey());
        String observed=recent.get(match.configKey());
        boolean own=timestampMillis(observed)>timestampMillis(raw);
        if(own)raw=observed;
        if (raw == null)
        {
            return new Observation(null, Source.NONE, 0, protection, match.getName());
        }
        long observedAtMillis = timestampMillis(raw);
        if (observedAtMillis <= 0 || observedAtMillis > System.currentTimeMillis())
        {
            return new Observation(null, Source.NONE, 0, protection, match.getName());
        }
        CropState state = handler.predictObserved(match,profile,raw);
        return new Observation(state, state == null ? Source.NONE : own?Source.OBSERVED:Source.TIME_TRACKING,
            observedAtMillis, protection, match.getName(), Integer.parseInt(raw.split(":",2)[0]));
    }

    private boolean confirmedObject(FarmingPatch match,WorldPoint marker,boolean flower)
    {
        if(marker==null||client.getScene()==null||client.getScene().getTiles()==null)return false;
        net.runelite.api.Tile[][][] tiles=client.getScene().getTiles();
        int plane=marker.getPlane();if(plane<0||plane>=tiles.length||tiles[plane]==null)return false;
        // Local scene only. Varbits are multiplexed between areas, so do not
        // interpret a neighbour's transmit value as the requested patch.
        for(net.runelite.api.Tile[] row:tiles[plane])if(row!=null)for(net.runelite.api.Tile tile:row)
        {
            if(tile==null||tile.getWorldLocation()==null||tile.getWorldLocation().distanceTo(marker)>(flower?6:25)||tile.getGameObjects()==null)continue;
            for(net.runelite.api.GameObject object:tile.getGameObjects())if(object!=null)
            {
                net.runelite.api.ObjectComposition base=client.getObjectDefinition(object.getId());
                if(base==null||base.getVarbitId()!=match.getVarbit())continue;
                net.runelite.api.ObjectComposition actual=base.getImpostorIds()==null?base:base.getImpostor();
                if(actual!=null&&matchesLiveName(actual.getName(),flower))return true;
            }
        }
        return false;
    }
    static boolean matchesLiveName(String name,boolean flower)
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
        if (raw == null)
        {
            return 0;
        }
        String[] fields = raw.split(":", 3);
        if (fields.length != 2)
        {
            return 0;
        }
        try
        {
            int value = Integer.parseInt(fields[0]);
            long epochSeconds = Long.parseLong(fields[1]);
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

    static FarmingPatch find(FarmingWorld world, Tab tab, String location, String patchName)
    {
        return find(world, tab, location, patchName, null);
    }

    static FarmingPatch find(FarmingWorld world, Tab tab, String location,
                             String patchName, PatchImplementation implementation)
    {
        Collection<FarmingPatch> patches = world.getTabs().get(tab);
        if (patches == null || location == null)
        {
            return null;
        }
        String expected = normalize(location);
        FarmingPatch match=null;
        for (FarmingPatch patch : patches)
        {
            if (normalize(patch.getRegion().getName()).equals(expected)
                && (implementation == null || patch.getImplementation() == implementation)
                && (patchName == null || patch.getName().equalsIgnoreCase(patchName)))
            {
                // Explicit physical identities must be unique. Location-only
                // legacy/region queries may still return the first representative.
                if(patchName==null||implementation==null)return patch;
                if(match!=null)return null;
                match=patch;
            }
        }
        return match;
    }

    /** Canonical Quest Helper patch names for multi-patch locations. */
    public List<String> patchNames(Tab tab, String location)
    {
        return patchNamesResolved(tab, location, null);
    }

    private List<String> patchNamesResolved(Tab tab, String location, PatchImplementation implementation)
    {
        Collection<FarmingPatch> patches = world.getTabs().get(tab);
        if (patches == null || location == null)
        {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (FarmingPatch patch : patches)
        {
            if (normalize(patch.getRegion().getName()).equals(normalize(location))
                && (implementation == null || patch.getImplementation() == implementation))
            {
                result.add(patch.getName());
            }
        }
        Collections.sort(result);
        return result;
    }

    private static String normalize(String location)
    {
        String normalized = location.trim().toLowerCase(Locale.ROOT);
        return "harmony island".equals(normalized) ? "harmony" : normalized;
    }
}
