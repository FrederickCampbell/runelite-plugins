package com.farminghelper;

import net.runelite.client.config.*;

@ConfigGroup("farming-helper")
public interface FarmingHelperConfig extends Config
{
	@ConfigItem(keyName = "includeMorytania", name = "Morytania", description = "Include patches in Morytania", hidden = true)
	default boolean includeMorytania() { return true; }
	@ConfigItem(keyName = "includeKourend", name = "Kourend", description = "Include patches in Kourend", hidden = true)
	default boolean includeKourend() { return true; }
	@ConfigItem(keyName = "includeTrollStronghold", name = "Troll Stronghold", description = "Include the Troll Stronghold herb patch", hidden = true)
	default boolean includeTrollStronghold() { return true; }
	@ConfigItem(keyName = "includeHarmonyIsland", name = "Harmony Island", description = "Include patches on Harmony Island", hidden = true)
	default boolean includeHarmonyIsland() { return true; }
	@ConfigItem(keyName = "includeWeiss", name = "Weiss", description = "Include the Weiss herb patch", hidden = true)
	default boolean includeWeiss() { return true; }
	@ConfigItem(keyName = "includeFarmingGuild", name = "Farming Guild", description = "Include patches in the Farming Guild", hidden = true)
	default boolean includeFarmingGuild() { return true; }
	@ConfigItem(keyName = "includePrifddinas", name = "Prifddinas", description = "Include patches in Prifddinas", hidden = true)
	default boolean includePrifddinas() { return true; }
	@ConfigItem(keyName = "includeLletya", name = "Lletya", description = "Include the Lletya fruit-tree patch", hidden = true)
	default boolean includeLletya() { return true; }
	@ConfigItem(keyName = "includeFossilIsland", name = "Fossil Island", description = "Include hardwood and underwater seaweed patches", hidden = true)
	default boolean includeFossilIsland() { return true; }
	@ConfigItem(keyName = "includeEtceteria", name = "Etceteria", description = "Include patches on Etceteria", hidden = true)
	default boolean includeEtceteria() { return true; }
	@ConfigItem(keyName = "includeEntrana", name = "Entrana", description = "Include the Entrana hops patch", hidden = true)
	default boolean includeEntrana() { return true; }
	@ConfigItem(keyName = "includeVarlamore", name = "Varlamore", description = "Include patches across Varlamore", hidden = true)
	default boolean includeVarlamore() { return true; }
	@ConfigItem(keyName = "includeGreatConch", name = "Great Conch", description = "Include patches at the Great Conch", hidden = true)
	default boolean includeGreatConch() { return true; }
}
