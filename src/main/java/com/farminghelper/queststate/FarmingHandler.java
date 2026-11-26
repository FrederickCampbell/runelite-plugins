/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.farminghelper.queststate;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.client.plugins.timetracking.farming.Produce;

import javax.annotation.Nullable;
import java.time.Instant;

public class FarmingHandler
{
	private final Client client;
	private final ConfigManager configManager;

	public FarmingHandler(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
	}

	public CropState predictPatch(FarmingPatch patch)
	{
		return predictPatch(patch, configManager.getRSProfileKey());
	}

	@Nullable
	public CropState predictPatch(FarmingPatch patch, String profile)
	{
        return predictObserved(patch,profile,configManager.getConfiguration("timetracking",profile,patch.configKey()));
    }

    CropState predictObserved(FarmingPatch patch,String profile,String storedValue)
    {
        long unixNow=Instant.now().getEpochSecond();
        long observedAt=QuestFarmingStateReader.timestampMillis(storedValue);
        if(observedAt<=0||observedAt/1000L>unixNow)return null;
        long unixTime=observedAt/1000L;
        int value=Integer.parseInt(storedValue.substring(0,storedValue.indexOf(':')));

		PatchState state = patch.getImplementation().forVarbitValue(value);
		if (state == null) return null;
        CropState normalized = QuestFarmingStateReader.normalizedState(patch.getImplementation(), value, state);
        if (normalized != CropState.GROWING) return normalized;
        if (patch.getImplementation() == PatchImplementation.REDWOOD && state.getCropState() == CropState.HARVESTABLE)
            return CropState.GROWING;
        if (state.getCropState() == CropState.HARVESTABLE)
        {
            int minutes=state.getTickRate();
            return minutes>0 && unixTime>0 && unixNow-unixTime>=minutes*60L ? CropState.HARVESTABLE : CropState.GROWING;
        }
        if (patch.getImplementation() == PatchImplementation.SPIRIT_TREE && value==20) return CropState.GROWING;

		if (unixTime <= 0)
		{
			return null;
		}

		int stage = state.getStage();
		int stages = state.getStages();
		int tickrate = state.getTickRate();

		boolean botanist = client.getVarbitValue(VarbitID.LEAGUE_RELIC_SELECTION_4) == 1;

		if (botanist)
		{
			tickrate /= 5;
		}

		// Unknown or malformed stage timing must not predict instant maturity.
		if (tickrate <= 0 || stages <= 0 || stage < 0 || stage >= stages)
		{
			return null;
		}
		long tickTime = getTickTime(tickrate, 0, unixTime, profile);
		long doneEstimate = getTickTime(tickrate, stages - 1 - stage, tickTime, profile);

		if (unixNow >= doneEstimate)
		{
			// Anima expires rather than becoming a health-check or harvest stop.
			// Revisit before deciding whether to clear the plant.
			return patch.getImplementation() == PatchImplementation.ANIMA
				? null : CropState.UNCHECKED;
		}

		return CropState.GROWING;
	}

	public long getTickTime(int tickRate, int ticks, long requestedTime, String profile)
	{
		Integer offsetPrecisionMins = configManager.getConfiguration(TimeTrackingConfig.CONFIG_GROUP, profile, TimeTrackingConfig.FARM_TICK_OFFSET_PRECISION, int.class);
		Integer offsetTimeMins = configManager.getConfiguration(TimeTrackingConfig.CONFIG_GROUP, profile, TimeTrackingConfig.FARM_TICK_OFFSET, int.class);

		//All offsets are negative but are stored as positive
		long calculatedOffsetTime = 0L;
		if (offsetPrecisionMins != null && offsetTimeMins != null && (offsetPrecisionMins >= tickRate || offsetPrecisionMins >= 40))
		{
			calculatedOffsetTime = (offsetTimeMins % tickRate) * 60L;
		}

		//Calculate "now" as +offset seconds in the future so we calculate the correct ticks
		long unixNow = requestedTime + calculatedOffsetTime;

		//The time that the tick requested will happen
		long timeOfCurrentTick = (unixNow - (unixNow % (tickRate * 60L)));
		long timeOfGoalTick = timeOfCurrentTick + ((long) ticks * tickRate * 60);

		//Move ourselves back to real time
		return timeOfGoalTick - calculatedOffsetTime;
	}
}
