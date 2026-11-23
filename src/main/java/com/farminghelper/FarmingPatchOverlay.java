package com.farminghelper;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

final class FarmingPatchOverlay extends Overlay
{
	private static final Color CONTRACT_CLEAR_COLOR = new Color(255, 152, 31);
	private final Client client;
	private final FarmingHelperPlugin plugin;
	private final FarmingHelperConfig config;
	private final FarmingLoadout farmingLoadout;
	private final FarmingContractManager contractManager;
	private final PatchTimerManager timerManager;
	private final FarmRunFilterState runFilterState;
	private final FarmRouteManager routes;
    private final com.farminghelper.queststate.QuestFarmingStateReader states;
    private final net.runelite.client.game.ItemManager itemManager;

	@Inject
	private FarmingPatchOverlay(Client client, FarmingHelperPlugin plugin, FarmingHelperConfig config,
		FarmingLoadout farmingLoadout, FarmingContractManager contractManager,
		PatchTimerManager timerManager, FarmRunFilterState runFilterState, FarmRouteManager routes,
        com.farminghelper.queststate.QuestFarmingStateReader states,
        net.runelite.client.game.ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.farmingLoadout = farmingLoadout;
		this.contractManager = contractManager;
		this.timerManager = timerManager;
		this.runFilterState = runFilterState;
		this.routes = routes;
        this.states = states;
        this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_LOW);
	}

    @Override
    public Dimension render(Graphics2D graphics)
    {
        WorldGuide.Target target = plugin.activeDirectorWorldTarget();
        if (target != null && config.directorWorldHints()) renderDirectorTarget(graphics, target);
        return null;
    }

    private void renderDirectorTarget(Graphics2D graphics, WorldGuide.Target target)
    {
        if (client.getGameState() != net.runelite.api.GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
        FarmRunPatch patch = target.patch;
        if (target.travelling) return;
        Color color = config.directorTargetColor();
        String text = WorldGuide.instruction(target.action, target.crop, patch.getPatchType(), target.compost);
        int iconId = WorldGuide.itemId(target);
        java.awt.image.BufferedImage icon = iconId < 0 ? null : itemManager.getImage(iconId);
        if (target.action == StepEngine.Action.PROTECT_TREE || target.action == StepEngine.Action.PAY_REMOVE)
        {
            QuestTargets.Definition def = QuestTargets.get(patch);
            int id = def != null && def.gardenerId >= 0 ? def.gardenerId : states.gardenerId(patch.getPatchType().name(), patch.getLocation(), patch.getName());
            if (id >= 0 && client.getTopLevelWorldView() != null)
            {
                for (net.runelite.api.NPC npc : client.getTopLevelWorldView().npcs())
                {
                    if (npc.getId() == id && patch.getLocation().equals(PatchLocationCatalog.name(npc.getWorldLocation())))
                    {
                        WorldGuide.drawHint(graphics, npc.getConvexHull(), color, text, icon);
                        return;
                    }
                }
            }
            return; // Never point at the tree when the interaction is with a gardener.
        }
        List<PatchObject> group = resolveDirectorPatch(patch);
        if (group.isEmpty()) return;
        GameObject reference = group.get(0).object;
        Set<PatchTile> tiles = patchTiles(group);
        Area footprint = canvasArea(tiles, reference);
        Area clickboxes = new Area();
        for (PatchObject object : group)
        {
            java.awt.Shape clickbox = object.object.getClickbox();
            if (clickbox != null) clickboxes.add(new Area(clickbox));
        }
        if (!footprint.isEmpty()) renderPatchBoundary(graphics, footprint, tiles, reference, color);
        WorldGuide.drawHint(graphics, clickboxes.isEmpty() ? footprint : clickboxes, color, text, icon);
    }

    private List<PatchObject> resolveDirectorPatch(FarmRunPatch patch)
    {
        if (client.getLocalPlayer() == null) return java.util.Collections.emptyList();
        QuestTargets.Definition def = QuestTargets.get(patch);
        if (def != null)
        {
            List<PatchObject> exactObjects = new ArrayList<>();
            for (GameObject object : plugin.getDirectorObjects())
            {
                if (object.getWorldView() == client.getLocalPlayer().getWorldView() &&
                    QuestTargets.matches(client, object, def) &&
                    QuestTargets.atPoint(object, Waypoints.exactPoint(patch)))
                    exactObjects.add(new PatchObject(object, patch.getPatchType(), null, false));
            }
            if (!exactObjects.isEmpty()) return exactObjects;
        }
        List<PatchObject> candidates = new ArrayList<>();
        for (GameObject object : plugin.getPatchObjects())
        {
            if (object.getWorldView() != client.getLocalPlayer().getWorldView() ||
                object.getPlane() != client.getLocalPlayer().getWorldLocation().getPlane()) continue;
            if (plugin.directorPatchType(object) == patch.getPatchType() &&
                patch.getLocation().equals(PatchLocationCatalog.name(object.getWorldLocation())))
                candidates.add(new PatchObject(object, patch.getPatchType(), null, false));
        }
        List<List<PatchObject>> groups = new ArrayList<>();
        List<WorldPoint> positions = new ArrayList<>();
        while (!candidates.isEmpty())
        {
            List<PatchObject> group = collectConnectedPatch(candidates.remove(candidates.size() - 1), candidates);
            groups.add(group);
            int x = 0, y = 0;
            for (PatchObject object : group)
            {
                x += object.object.getWorldLocation().getX();
                y += object.object.getWorldLocation().getY();
            }
            positions.add(new WorldPoint(x / group.size(), y / group.size(), group.get(0).object.getPlane()));
        }
        WorldPoint exact = patch.getPatchType() == PatchType.HERB || StepEngine.isTreeType(patch.getPatchType())
            ? Waypoints.exactPoint(patch) : null;
        int count = states.patchNamesForType(patch.getPatchType().name(), patch.getLocation()).size();
        int selected = WorldGuide.select(patch, positions, count, exact);
        return selected < 0 ? java.util.Collections.emptyList() : groups.get(selected);
    }

    boolean directorPatchHasWeeds(FarmRunPatch patch)
    {
        for (PatchObject p : resolveDirectorPatch(patch))
        {
            net.runelite.api.ObjectComposition c = client.getObjectDefinition(p.object.getId());
            if (c != null && c.getImpostorIds() != null) c = c.getImpostor();
            if (PatchClassifier.hasAction(c, "Rake")) return true;
        }
        return false;
    }

    WorldPoint directorPatchAnchor(FarmRunPatch patch)
    {
        List<PatchObject> group = resolveDirectorPatch(patch);
        if (group.isEmpty()) return null;
        int x = group.stream().mapToInt(o -> o.object.getWorldLocation().getX()).min().orElse(0);
        int y = group.stream().mapToInt(o -> o.object.getWorldLocation().getY()).min().orElse(0);
        return new WorldPoint(x, y, group.get(0).object.getPlane());
    }

	static boolean shouldHighlight(PatchType patchType, Set<ChecklistPatch> selectedPatches,
		FarmRunFilterState runFilterState)
	{
		return patchType != null
			&& ChecklistPatch.includes(selectedPatches, patchType)
			&& runFilterState.includes(patchType);
	}

	private Crop contractCrop(GameObject object, PatchType patchType)
	{
		if (patchType == null)
		{
			return null;
		}
		FarmingContract contract = config.showFarmingContract() && config.highlightContractPatch()
			? contractManager.getContract() : null;
		if (contract != null && contract.getCrop().getPatchType() == patchType
			&& "Farming Guild".equals(PatchLocationCatalog.name(object.getWorldLocation())))
		{
			PatchTimer plantedContract = plantedContractTimer(contract);
			return plantedContract == null
				|| belongsToPlantedContractPatch(object.getWorldLocation(), plantedContract.getPatchLocation())
				? contract.getCrop() : null;
		}
		return null;
	}

	private PatchTimer plantedContractTimer(FarmingContract contract)
	{
		for (PatchTimer timer : timerManager.getTimers())
		{
			if (!timer.isDead() && timer.getCrop().equals(contract.getCrop())
				&& "Farming Guild".equals(PatchLocationCatalog.name(timer.getPatchLocation())))
			{
				return timer;
			}
		}
		return null;
	}

	static boolean belongsToPlantedContractPatch(WorldPoint objectLocation, WorldPoint timerLocation)
	{
		return objectLocation.getPlane() == timerLocation.getPlane()
			&& Math.abs(objectLocation.getX() - timerLocation.getX()) <= 1
			&& Math.abs(objectLocation.getY() - timerLocation.getY()) <= 1;
	}

	static boolean requiresClearingForContract(Crop currentCrop, Crop contractCrop)
	{
		return currentCrop != null && contractCrop != null && !contractCrop.equals(currentCrop);
	}

	static boolean usesContractGroundOutline(boolean contractPatch, Crop currentCrop)
	{
		return contractPatch && currentCrop != null;
	}

	static String patchTimeRemaining(PatchTimer timer, Instant now)
	{
		if (timer == null)
		{
			return "Inspect patch";
		}
		if (timer.isDead())
		{
			return "DEAD";
		}
		if (timer.isDiseased())
		{
			return "DISEASED";
		}
		if (timer.isPicked())
		{
			return "PICKED";
		}
		if (!now.isBefore(timer.getReadyAt()))
		{
			return "READY";
		}
		return PatchTimerOverlay.formatRemaining(Duration.between(now, timer.getReadyAt()));
	}

	private Set<PatchTile> patchTiles(List<PatchObject> group)
	{
		Set<PatchTile> tiles = new HashSet<>();
		for (PatchObject patchObject : group)
		{
			Point min = patchObject.object.getSceneMinLocation();
			Point max = patchObject.object.getSceneMaxLocation();
			if (min == null || max == null)
			{
				LocalPoint local = patchObject.object.getLocalLocation();
				tiles.add(new PatchTile(local.getSceneX(), local.getSceneY()));
				continue;
			}
			for (int x = min.getX(); x <= max.getX(); x++)
			{
				for (int y = min.getY(); y <= max.getY(); y++)
				{
					tiles.add(new PatchTile(x, y));
				}
			}
		}
		return tiles;
	}

	private Area canvasArea(Set<PatchTile> tiles, GameObject reference)
	{
		Area area = new Area();
		for (PatchTile tile : tiles)
		{
			java.awt.Polygon polygon = Perspective.getCanvasTilePoly(client,
				LocalPoint.fromScene(tile.x, tile.y, reference.getWorldView()), reference.getPlane());
			if (polygon != null)
			{
				area.add(new Area(polygon));
			}
		}
		return area;
	}

	private void renderPatchBoundary(Graphics2D graphics, Area area, Set<PatchTile> tiles,
		GameObject reference, Color color)
	{
		Color previousColor = graphics.getColor();
		Stroke previousStroke = graphics.getStroke();
		graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
		graphics.fill(area);
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(2));
		for (PatchTile tile : tiles)
		{
			if (!tiles.contains(new PatchTile(tile.x, tile.y - 1)))
			{
				drawEdge(graphics, reference, tile.x, tile.y, tile.x + 1, tile.y);
			}
			if (!tiles.contains(new PatchTile(tile.x + 1, tile.y)))
			{
				drawEdge(graphics, reference, tile.x + 1, tile.y, tile.x + 1, tile.y + 1);
			}
			if (!tiles.contains(new PatchTile(tile.x, tile.y + 1)))
			{
				drawEdge(graphics, reference, tile.x + 1, tile.y + 1, tile.x, tile.y + 1);
			}
			if (!tiles.contains(new PatchTile(tile.x - 1, tile.y)))
			{
				drawEdge(graphics, reference, tile.x, tile.y + 1, tile.x, tile.y);
			}
		}
		graphics.setStroke(previousStroke);
		graphics.setColor(previousColor);
	}

	private void drawEdge(Graphics2D graphics, GameObject reference,
		int startX, int startY, int endX, int endY)
	{
		Point start = canvasCorner(reference, startX, startY);
		Point end = canvasCorner(reference, endX, endY);
		if (start != null && end != null)
		{
			graphics.drawLine(start.getX(), start.getY(), end.getX(), end.getY());
		}
	}

	private Point canvasCorner(GameObject reference, int sceneX, int sceneY)
	{
		LocalPoint tileCenter = LocalPoint.fromScene(sceneX, sceneY, reference.getWorldView());
		return Perspective.localToCanvas(client,
			tileCenter.plus(-Perspective.LOCAL_HALF_TILE_SIZE, -Perspective.LOCAL_HALF_TILE_SIZE),
			reference.getPlane());
	}

	private static List<PatchObject> collectConnectedPatch(PatchObject first, List<PatchObject> remaining)
	{
		List<PatchObject> group = new ArrayList<>();
		ArrayDeque<PatchObject> queue = new ArrayDeque<>();
		Set<GameObject> queued = new HashSet<>();
		queue.add(first);
		queued.add(first.object);

		while (!queue.isEmpty())
		{
			PatchObject current = queue.removeFirst();
			group.add(current);
			Iterator<PatchObject> iterator = remaining.iterator();
			while (iterator.hasNext())
			{
				PatchObject candidate = iterator.next();
				if (isAdjacent(current, candidate) && queued.add(candidate.object))
				{
					queue.addLast(candidate);
					iterator.remove();
				}
			}
		}
		return group;
	}

	private static boolean isAdjacent(PatchObject first, PatchObject second)
	{
		if (first.patchType != second.patchType
			|| first.object.getWorldView() != second.object.getWorldView()
			|| first.object.getPlane() != second.object.getPlane())
		{
			return false;
		}

		Point firstMin = first.object.getSceneMinLocation();
		Point firstMax = first.object.getSceneMaxLocation();
		Point secondMin = second.object.getSceneMinLocation();
		Point secondMax = second.object.getSceneMaxLocation();
		if (firstMin != null && firstMax != null && secondMin != null && secondMax != null)
		{
			return sceneAreasTouch(firstMin, firstMax, secondMin, secondMax);
		}

		WorldPoint a = first.object.getWorldLocation();
		WorldPoint b = second.object.getWorldLocation();
		return Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getY() - b.getY()) <= 1;
	}

	static boolean sceneAreasTouch(Point firstMin, Point firstMax, Point secondMin, Point secondMax)
	{
		return firstMin.getX() <= secondMax.getX() + 1
			&& secondMin.getX() <= firstMax.getX() + 1
			&& firstMin.getY() <= secondMax.getY() + 1
			&& secondMin.getY() <= firstMax.getY() + 1;
	}

	private static final class PatchObject
	{
		private final GameObject object;
		private final PatchType patchType;
		private final Crop crop;
		private final boolean contractPatch;

		private PatchObject(GameObject object, PatchType patchType, Crop crop, boolean contractPatch)
		{
			this.object = object;
			this.patchType = patchType;
			this.crop = crop;
			this.contractPatch = contractPatch;
		}
	}

	private static final class PatchTile
	{
		private final int x;
		private final int y;

		private PatchTile(int x, int y)
		{
			this.x = x;
			this.y = y;
		}

		@Override
		public boolean equals(Object other)
		{
			return other instanceof PatchTile && ((PatchTile) other).x == x && ((PatchTile) other).y == y;
		}

		@Override
		public int hashCode()
		{
			return 31 * x + y;
		}
	}
}
