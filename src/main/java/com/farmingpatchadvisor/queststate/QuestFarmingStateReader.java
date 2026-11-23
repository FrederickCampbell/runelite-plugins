package com.farmingpatchadvisor.queststate;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    @Inject
    QuestFarmingStateReader(Client client, ConfigManager config)
    {
        this.client = client;
        this.config = config;
        world = new FarmingWorld();
        handler = new FarmingHandler(client, config);
    }

    public enum Source
    {
        LIVE, TIME_TRACKING, ADVISOR, NONE
    }

    public static final class Observation
    {
        public final CropState state;
        public final Source source;
        public final long observedAtMillis;
        @Nullable public final Boolean protectedByFarmer;
        public final String patchName;

        public Observation(@Nullable CropState state, Source source, long time,
                           @Nullable Boolean protection, String patchName)
        {
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
     * Call on RuneLite's client thread. Regions follow Quest Helper's registry
     * including the nonintuitive Harmony vs Harmony Island display name.
     */
    public Observation observe(Tab tab, String location, WorldPoint worldPoint)
    {
        return observe(tab, location, null, worldPoint);
    }

    public Observation observe(Tab tab, String location, String patchName, WorldPoint worldPoint)
    {
        if (client.getGameState() != GameState.LOGGED_IN ||
            config.getRSProfileKey() == null)
        {
            return new Observation(null, Source.NONE, 0, null, "");
        }

        FarmingPatch match = find(world, tab, location, patchName);
        if (match == null)
        {
            return new Observation(null, Source.NONE, 0, null, "");
        }
        Boolean protection = config.getRSProfileConfiguration(TIME_TRACKING,
            match.configKey() + "." + TimeTrackingConfig.PROTECTED, Boolean.class);

        WorldPoint player = client.getLocalPlayer() == null ? null
            : client.getLocalPlayer().getWorldLocation();
        boolean near = player != null && worldPoint != null &&
            player.getPlane() == worldPoint.getPlane() &&
            player.distanceTo(worldPoint) <= LIVE_TILE_RADIUS &&
            world.getRegionsForLocation(player).contains(match.getRegion());
        if (near)
        {
            PatchState raw = match.getImplementation()
                .forVarbitValue(client.getVarbitValue(match.getVarbit()));
            if (raw != null)
            {
                CropState state = raw.getProduce() == Produce.WEEDS
                    ? CropState.EMPTY : raw.getCropState();
                return new Observation(state, Source.LIVE, System.currentTimeMillis(),
                    protection, match.getName());
            }
        }

        String raw = config.getConfiguration(TIME_TRACKING, config.getRSProfileKey(),
            match.configKey());
        if (raw == null)
        {
            return new Observation(null, Source.NONE, 0, protection, match.getName());
        }
        long observedAtMillis = timestampMillis(raw);
        if (observedAtMillis <= 0)
        {
            return new Observation(null, Source.NONE, 0, protection, match.getName());
        }
        CropState state = handler.predictPatch(match);
        return new Observation(state, state == null ? Source.NONE : Source.TIME_TRACKING,
            observedAtMillis, protection, match.getName());
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
        Collection<FarmingPatch> patches = world.getTabs().get(tab);
        if (patches == null || location == null)
        {
            return null;
        }
        String expected = normalize(location);
        for (FarmingPatch patch : patches)
        {
            if (normalize(patch.getRegion().getName()).equals(expected)
                && (patchName == null || patch.getName().equalsIgnoreCase(patchName)))
            {
                return patch;
            }
        }
        return null;
    }

    /** Canonical Quest Helper patch names for multi-patch locations. */
    public List<String> patchNames(Tab tab, String location)
    {
        Collection<FarmingPatch> patches = world.getTabs().get(tab);
        if (patches == null || location == null)
        {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (FarmingPatch patch : patches)
        {
            if (normalize(patch.getRegion().getName()).equals(normalize(location)))
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
