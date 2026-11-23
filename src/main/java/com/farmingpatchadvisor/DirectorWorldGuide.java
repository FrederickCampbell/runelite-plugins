package com.farmingpatchadvisor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/** Active-step presentation, deliberately independent of quest plugin internals.
 * The clickbox + arrow + item icon pattern follows Quest Helper ObjectStep.
 * No clicks, menu changes or pathfinding are performed here. */
final class DirectorWorldGuide
{
    static final class Target
    {
        final FarmRunPatch patch;
        final Crop crop;
        final DirectorStepEngine.Action action;
        final String profile;
        final boolean compost;
        final int compostItem;
        Target(DirectorPlanner.Step step, DirectorStepEngine.Action action, String profile, boolean compost, int compostItem)
        {
            this.patch = step.patch;
            this.crop = step.crop;
            this.action = action;
            this.profile = profile;
            this.compost = compost;
            this.compostItem = compostItem;
        }
    }

    static String instruction(DirectorStepEngine.Action action, Crop crop, PatchType type, boolean compost)
    {
        switch (action)
        {
            case HARVEST: return type == PatchType.HESPORI ? "Harvest after defeating Hespori" : "Harvest";
            case CHECK_HEALTH: return "Check health";
            case REMOVE_TREE: return "Remove tree";
            case PAY_REMOVE: return "Pay gardener to remove tree";
            case CHOP_TREE: return "Chop tree";
            case RAKE: return "Rake weeds";
            case DIG_STUMP: return "Dig stump";
            case CLEAR_DEAD: return "Clear dead crop";
            case CURE: return DirectorStepEngine.isTreeType(type) ? "Prune tree" : "Cure crop";
            case PLANT: return crop == null ? "No replacement — bank or continue" :
                "Plant " + crop.getName();
            case COMPOST: return "Apply compost";
            case PROTECT_TREE: return "Pay gardener";
            case COMPLETE: return "Done · next patch";
            default: return "Inspect patch";
        }
    }

    static int itemId(Target target)
    {
        switch (target.action)
        {
            case PLANT: return target.crop == null ? -1 : target.crop.getItemId();
            case COMPOST: return target.compostItem;
            case RAKE: return ItemID.RAKE;
            case PAY_REMOVE: return ItemID.COINS;
            case DIG_STUMP: case CLEAR_DEAD: return ItemID.SPADE;
            case CURE: return DirectorStepEngine.isTreeType(target.patch.getPatchType()) ? ItemID.SECATEURS : ItemID.PLANT_CURE;
            case PROTECT_TREE:
                List<ProtectionPayment> payments = ProtectionPaymentCatalog.forCrop(target.crop);
                return payments.isEmpty() ? -1 : payments.get(0).getItemId();
            default: return -1;
        }
    }

    static boolean matchesInventoryItem(Target target, int id)
    {
        if (target.action == DirectorStepEngine.Action.PROTECT_TREE)
            return ProtectionPaymentCatalog.forCrop(target.crop).stream().anyMatch(p -> p.getItemId() == id);
        if (target.action == DirectorStepEngine.Action.CURE && DirectorStepEngine.isTreeType(target.patch.getPatchType()))
            return id == ItemID.SECATEURS || id == ItemID.FAIRY_ENCHANTED_SECATEURS;
        return id == itemId(target);
    }

    /** Exact positions win. For named compass pairs, only resolve when BOTH
     * physical patches are observed, and the directional distinction is clear.
     * Numbered grapevine rows remain unverified rather than guessed. */
    static int select(FarmRunPatch patch, List<WorldPoint> positions, int expectedCount, WorldPoint exact)
    {
        if (positions.isEmpty()) return -1;
        if (exact != null)
        {
            int best = -1, distance = 6;
            boolean tied = false;
            for (int i = 0; i < positions.size(); i++)
            {
                WorldPoint pos = positions.get(i);
                if (pos.getPlane() != exact.getPlane()) continue;
                int d = pos.distanceTo(exact);
                if (d < distance) { best = i; distance = d; tied = false; }
                else if (d == distance && best >= 0) tied = true;
            }
            return tied ? -1 : best;
        }
        if (expectedCount == 1 && positions.size() == 1) return 0;
        if (expectedCount != 2 || positions.size() != 2) return -1;
        String name = patch.getName().trim().toLowerCase(Locale.ROOT);
        int x = name.contains("east") ? 1 : name.contains("west") ? -1 : 0;
        int y = name.contains("north") ? 1 : name.contains("south") ? -1 : 0;
        if (x == 0 && y == 0 || name.matches(".*[0-9].*")) return -1;
        WorldPoint a = positions.get(0), b = positions.get(1);
        if (a.getPlane() != b.getPlane()) return -1;
        int dx = (a.getX() - b.getX()) * x, dy = (a.getY() - b.getY()) * y;
        // Reject conflicting compass axes and equal projections.
        if (dx * dy < 0 || dx + dy == 0) return -1;
        return dx + dy > 0 ? 0 : 1;
    }

    static void drawHint(Graphics2D graphics, Shape shape, Color color, String action, BufferedImage icon)
    {
        if (shape == null) return;
        Rectangle bounds = shape.getBounds();
        if (bounds.isEmpty()) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45));
            g.fill(shape);
            g.setColor(color);
            g.setStroke(new java.awt.BasicStroke(2f));
            g.draw(shape);
            int x = bounds.x + bounds.width / 2, y = bounds.y - 7;
            Polygon arrow = new Polygon(new int[]{x-5,x+5,x+5,x+11,x,x-11,x-5},
                new int[]{y-25,y-25,y-13,y-13,y,y-13,y-13}, 7);
            g.setColor(Color.BLACK);
            g.setStroke(new java.awt.BasicStroke(3f));
            g.draw(arrow);
            g.setColor(color);
            g.fill(arrow);
            int textY = y - 31;
            int width = g.getFontMetrics().stringWidth(action);
            g.setColor(new Color(0, 0, 0, 195));
            g.fillRoundRect(x - width/2 - 5, textY - g.getFontMetrics().getAscent() - 2,
                width + 10, g.getFontMetrics().getHeight() + 4, 6, 6);
            g.setColor(Color.WHITE);
            g.drawString(action, x - width/2, textY);
            if (icon != null) g.drawImage(icon, x - 15, textY - g.getFontMetrics().getHeight() - 32, null);
        }
        finally { g.dispose(); }
    }
}
