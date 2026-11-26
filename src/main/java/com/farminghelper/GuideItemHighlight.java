package com.farminghelper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Provider;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Read-only requirement outlines, adapted from the earlier licensed Farming Helper item overlay. */
final class GuideItemHighlight extends WidgetItemOverlay
{
    private final ItemManager items;
    private final Client client;
    private final Provider<FarmingHelperPlugin> plugin;
    @Inject GuideItemHighlight(Client client,ItemManager items,Provider<FarmingHelperPlugin> plugin)
    {
        this.client=client;this.items=items;this.plugin=plugin;
        showOnInventory();showOnBank();showOnInterfaces(InterfaceID.SEED_VAULT);
    }
    @Override public void renderItemOverlay(Graphics2D graphics,int id,WidgetItem widget)
    {
        FarmingHelperPlugin helper=plugin.get();
        Widget itemWidget=widget.getWidget();
        if(itemWidget==null)return;
        int group=itemWidget.getId()>>>16;
        Color color;
        if(group==InterfaceID.INVENTORY)
        {
            if(!helper.activeStepItems().contains(id))return;
            ItemContainer inventory=client.getItemContainer(InventoryID.INVENTORY);
            if(!StepItems.firstSlot(id,itemWidget.getIndex(),inventory))return;
            color=Color.CYAN;
        }
        else if(group==InterfaceID.SEED_VAULT)
            color=helper.missingStorageItems().contains(id)?Color.CYAN:null;
        else if(group==InterfaceID.BANKMAIN)
            color=helper.missingStorageItems().contains(id)?Color.CYAN:helper.recommendedItems().contains(id)?FarmingTheme.WARNING:null;
        else return;
        if(color==null)return;
        BufferedImage outline=items.getItemOutline(id,widget.getQuantity(),color);
        Rectangle bounds=widget.getCanvasBounds();
        if(outline!=null && bounds!=null) graphics.drawImage(outline,bounds.x,bounds.y,null);
    }
}
