package com.farmingpatchadvisor;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

final class FarmingItemOverlay extends WidgetItemOverlay
{
	private final FarmingPatchAdvisorConfig config;
	private final ItemManager itemManager;
	private final FarmingLoadout farmingLoadout;
    private final FarmingPatchAdvisorPlugin plugin;

	@Inject
	private FarmingItemOverlay(FarmingPatchAdvisorConfig config, ItemManager itemManager,
		FarmingLoadout farmingLoadout, FarmingPatchAdvisorPlugin plugin)
	{
		this.config = config;
		this.itemManager = itemManager;
		this.farmingLoadout = farmingLoadout;
        this.plugin = plugin;
		showOnBank();
		showOnInventory();
		showOnInterfaces(InterfaceID.SEED_VAULT);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		boolean seedVaultItem = isSeedVaultItem(widgetItem);
		boolean bankItem = isBankItem(widgetItem);
		if ((!config.highlightBank() && bankItem)
			|| (!config.highlightSeedVault() && seedVaultItem)
			|| (!config.highlightInventory() && !bankItem && !seedVaultItem))
		{
			return;
		}

        DirectorWorldGuide.Target target = plugin.activeDirectorWorldTarget();
        if (!bankItem && !seedVaultItem && target != null && config.directorWorldHints())
        {
            if (!DirectorWorldGuide.matchesInventoryItem(target, itemId)) return;
            BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), config.directorTargetColor());
            Rectangle bounds = widgetItem.getCanvasBounds();
            graphics.drawImage(outline, bounds.x, bounds.y, null);
            return;
        }
        if (!bankItem && !seedVaultItem) return;
        DirectorBankChecklist director = farmingLoadout.activeDirectorChecklist();
        if (!farmingLoadout.directorBankGuidance() || director == null) return;
        boolean planting = director.seeds.containsKey(itemId);
        if (!planting && !director.ids().contains(itemId)) return;
        BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(),
            planting ? config.seedColor() : config.requiredItemColor());
        Rectangle bounds = widgetItem.getCanvasBounds();
        graphics.drawImage(outline, bounds.x, bounds.y, null);
    }

	private static boolean isBankItem(WidgetItem widgetItem)
	{
		int parentId = widgetItem.getWidget().getParentId();
		return parentId == InterfaceID.Bankmain.ITEMS || parentId == InterfaceID.SharedBank.ITEMS;
	}

	private static boolean isSeedVaultItem(WidgetItem widgetItem)
	{
		return WidgetUtil.componentToInterface(widgetItem.getWidget().getParentId()) == InterfaceID.SEED_VAULT;
	}
}
