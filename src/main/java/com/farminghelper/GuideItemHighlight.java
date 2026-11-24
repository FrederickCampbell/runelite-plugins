package com.farminghelper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Provider;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Read-only requirement outlines, adapted from the earlier licensed Farming Helper item overlay. */
final class GuideItemHighlight extends WidgetItemOverlay
{
    private final ItemManager items;
    private final Provider<FarmingHelperPlugin> plugin;
    @Inject GuideItemHighlight(ItemManager items,Provider<FarmingHelperPlugin> plugin)
    {
        this.items=items;this.plugin=plugin;
        showOnBank();showOnInventory();showOnInterfaces(InterfaceID.SEED_VAULT);
    }
    @Override public void renderItemOverlay(Graphics2D graphics,int id,WidgetItem widget)
    {
        FarmingHelperPlugin helper=plugin.get();
        Color color=helper.requiredItems().contains(id)?Color.CYAN:
            helper.recommendedItems().contains(id)?Color.YELLOW:null;
        if(color==null)return;
        BufferedImage outline=items.getItemOutline(id,widget.getQuantity(),color);
        Rectangle bounds=widget.getCanvasBounds();
        if(outline!=null && bounds!=null) graphics.drawImage(outline,bounds.x,bounds.y,null);
    }
}
