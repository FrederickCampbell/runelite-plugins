package com.farminghelper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FarmingHelperPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FarmingHelperPlugin.class);
		RuneLite.main(args);
	}
}
