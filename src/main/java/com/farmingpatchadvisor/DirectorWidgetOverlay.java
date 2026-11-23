package com.farmingpatchadvisor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/** Read-only widget hints based on Quest Helper TreeRun dialog choices and
 * SpellWidgetHighlight. Never changes widget text, events, menus or input. */
final class DirectorWidgetOverlay extends Overlay
{
    private final Client client;
    private final FarmingPatchAdvisorPlugin plugin;
    private final FarmingPatchAdvisorConfig config;

    @Inject
    DirectorWidgetOverlay(Client client, FarmingPatchAdvisorPlugin plugin, FarmingPatchAdvisorConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        DirectorWorldGuide.Target target = plugin.activeDirectorWorldTarget();
        if (target == null || !config.directorWorldHints() || client.getGameState() != GameState.LOGGED_IN) return null;
        if (target.travelling)
        {
            String spell = DirectorWorldGuide.spellName(target);
            if (!spell.isEmpty())
            {
                // Same spell-list lookup as Quest Helper; spell names survive UI reordering.
                Widget list = client.getWidget(InterfaceID.MAGIC_SPELLBOOK, 3);
                if (list != null && !list.isHidden())
                {
                    Widget[] children = list.getStaticChildren();
                    if (children != null) for (Widget child : children)
                    {
                        if (child != null && !child.isHidden() && spell.equalsIgnoreCase(Text.removeTags(child.getName())))
                        {
                            highlight(graphics, child.getBounds());
                            break;
                        }
                    }
                }
            }
            return null;
        }
        if (target.action != DirectorStepEngine.Action.PAY_REMOVE && target.action != DirectorStepEngine.Action.PROTECT_TREE) return null;
        Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
        if (options == null || options.isHidden()) return null;
        highlightOptions(graphics, options.getChildren(), target.action);
        highlightOptions(graphics, options.getNestedChildren(), target.action);
        return null;
    }

    private void highlightOptions(Graphics2D graphics, Widget[] children, DirectorStepEngine.Action action)
    {
        if (children == null) return;
        for (Widget child : children)
        {
            if (child != null && !child.isHidden() && matchesChoice(action, child.getText()))
                highlight(graphics, child.getBounds());
        }
    }

    static boolean matchesChoice(DirectorStepEngine.Action action, String value)
    {
        if (value == null) return false;
        String text = Text.removeTags(value).trim();
        if (action == DirectorStepEngine.Action.PROTECT_TREE)
            return text.equals("Would you look after my crops for me?");
        if (action == DirectorStepEngine.Action.PAY_REMOVE)
            return text.equals("Would you chop my tree down for me?") ||
                text.equals("I can't be bothered - I'd rather pay you to do it.") ||
                text.equals("Here's 200 Coins - chop my tree down please.");
        // Never guess a generic 'Yes.' in an unrelated conversation.
        return false;
    }

    private void highlight(Graphics2D graphics, Rectangle bounds)
    {
        if (bounds == null || bounds.isEmpty()) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            Color c = config.directorTargetColor();
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 45));
            g.fill(bounds);
            g.setColor(c);
            g.setStroke(new BasicStroke(2f));
            g.draw(bounds);
        }
        finally { g.dispose(); }
    }
}
