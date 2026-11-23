package com.farmingpatchadvisor;

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
	private final FarmingPatchAdvisorPlugin plugin;
	private final FarmingPatchAdvisorConfig config;
	private final FarmingLoadout farmingLoadout;
	private final FarmingContractManager contractManager;
	private final PatchTimerManager timerManager;
	private final FarmRunFilterState runFilterState;
	private final FarmRouteManager routes;
    private final com.farmingpatchadvisor.queststate.QuestFarmingStateReader states;
    private final net.runelite.client.game.ItemManager itemManager;

	@Inject
	private FarmingPatchOverlay(Client client, FarmingPatchAdvisorPlugin plugin, FarmingPatchAdvisorConfig config,
		FarmingLoadout farmingLoadout, FarmingContractManager contractManager,
		PatchTimerManager timerManager, FarmRunFilterState runFilterState, FarmRouteManager routes,
        com.farmingpatchadvisor.queststate.QuestFarmingStateReader states,
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
        if (plugin.directorRunDisplayed())
        {
            DirectorWorldGuide.Target target = plugin.activeDirectorWorldTarget();
            if (target != null && config.directorWorldHints()) renderDirectorTarget(graphics, target);
            return null; // Never render conflicting legacy recommendations during a Director run.
        }
		if (!config.showPatchLabels())
		{
			return null;
		}

		Set<ChecklistPatch> selectedPatches = ChecklistPatch.selected(config);
		List<FarmRunPatch> route = routes.activePatches();
		// Share recommendations within this frame, while refreshing on the next frame
		// so inventory, settings and character changes cannot leave stale results.
		EnumMap<PatchType, Crop> recommendations = new EnumMap<>(PatchType.class);
		List<PatchObject> remaining = new ArrayList<>();
		for (GameObject object : plugin.getPatchObjects())
		{
			if (object.getWorldView() == null || object.getPlane() != object.getWorldView().getPlane())
			{
				continue;
			}

			PatchType patchType = plugin.getPatchType(object);
			if (!shouldHighlight(patchType, selectedPatches, runFilterState))
			{
				continue;
			}
			Crop contractCrop = contractCrop(object, patchType);
			if (contractCrop == null && !recommendations.containsKey(patchType))
			{
				recommendations.put(patchType, farmingLoadout.recommendedCrop(patchType));
			}
			Crop crop = contractCrop != null ? contractCrop
				: recommendations.get(patchType);
			if (crop == null)
			{
				continue;
			}

			remaining.add(new PatchObject(object, patchType, crop, contractCrop != null));
		}

		while (!remaining.isEmpty())
		{
			PatchObject first = remaining.remove(remaining.size() - 1);
			List<PatchObject> group = collectConnectedPatch(first, remaining);
			PatchObject representative = group.stream().filter(patch -> patch.contractPatch)
				.findFirst().orElse(first);
			PatchTimer timer = timerManager.findTimer(representative.object.getWorldLocation(),
				representative.patchType, 4);
			Crop observedCrop = plugin.getPatchCrop(representative.object, representative.patchType);
			Crop currentCrop = observedCrop != null ? observedCrop : timer == null ? null : timer.getCrop();
			boolean clearForContract = representative.contractPatch
				&& requiresClearingForContract(currentCrop, representative.crop);
			Set<PatchTile> patchTiles = patchTiles(group);
			Area patchArea = canvasArea(patchTiles, representative.object);

			if (!patchArea.isEmpty())
			{
				Instant now = Instant.now();
				Color color = timer != null && timer.isDead() ? Color.RED
					: timer != null && timer.isDiseased() ? Color.YELLOW
						: timer != null && timer.isPicked() ? Color.WHITE
						: clearForContract ? CONTRACT_CLEAR_COLOR : config.seedColor();
				renderPatchBoundary(graphics, patchArea, patchTiles, representative.object, color);

				Rectangle bounds = patchArea.getBounds();
				List<String> lines = new ArrayList<>();
				String location = PatchLocationCatalog.name(representative.object.getWorldLocation());
				for (int i = 0; i < route.size(); i++)
				{
					if (route.get(i).getLocation().equals(location)
						&& route.get(i).getPatchType() == representative.patchType)
					{
						lines.add("Patch #" + (i + 1));
						break;
					}
				}
				if (clearForContract)
				{
					lines.add("Clear patch for contract");
					lines.add("Next: " + representative.crop.getItemName() + " x"
						+ representative.crop.getQuantity());
				}
				else
				{
					if (currentCrop != null)
					{
						lines.add("Plant: " + currentCrop.getName());
						lines.add("Time remaining: " + patchTimeRemaining(timer, now));
					}
					else
					{
						lines.add(representative.crop.getItemName() + " x" + representative.crop.getQuantity()
							+ " (Lvl " + representative.crop.getLevel() + ")");
					}
				}
				boolean growing = timer != null && !timer.isDead() && !timer.isDiseased()
					&& now.isBefore(timer.getReadyAt());
				if (growing && (timer.needsCompost() || timer.needsWater()))
				{
					StringBuilder needs = new StringBuilder("Needs: ");
					if (timer.needsCompost())
					{
						needs.append("Compost");
					}
					if (timer.needsWater())
					{
						if (needs.length() > 7)
						{
							needs.append(" + ");
						}
						needs.append("Water");
					}
					lines.add(needs.toString());
				}
				int lineHeight = graphics.getFontMetrics().getHeight();
				int textY = bounds.y + bounds.height / 2 - (lines.size() - 1) * lineHeight / 2;
				for (String line : lines)
				{
					int textX = bounds.x + (bounds.width - graphics.getFontMetrics().stringWidth(line)) / 2;
					OverlayUtil.renderTextLocation(graphics, new Point(textX, textY), line, color);
					textY += lineHeight;
				}
			}
		}
		return null;
	}

    private void renderDirectorTarget(Graphics2D graphics, DirectorWorldGuide.Target target)
    {
        if (client.getGameState() != net.runelite.api.GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
        FarmRunPatch patch = target.patch;
        Color color = config.directorTargetColor();
        String text = DirectorWorldGuide.instruction(target.action, target.crop, patch.getPatchType(), target.compost);
        int iconId = DirectorWorldGuide.itemId(target);
        java.awt.image.BufferedImage icon = iconId < 0 ? null : itemManager.getImage(iconId);
        if (target.action == DirectorStepEngine.Action.PROTECT_TREE || target.action == DirectorStepEngine.Action.PAY_REMOVE)
        {
            DirectorQuestTargets.Definition def = DirectorQuestTargets.get(patch);
            int id = def != null && def.gardenerId >= 0 ? def.gardenerId : states.gardenerId(patch.getPatchType().name(), patch.getLocation(), patch.getName());
            if (id >= 0 && client.getTopLevelWorldView() != null)
            {
                for (net.runelite.api.NPC npc : client.getTopLevelWorldView().npcs())
                {
                    if (npc.getId() == id && patch.getLocation().equals(PatchLocationCatalog.name(npc.getWorldLocation())))
                    {
                        DirectorWorldGuide.drawHint(graphics, npc.getConvexHull(), color, text, icon);
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
        DirectorWorldGuide.drawHint(graphics, clickboxes.isEmpty() ? footprint : clickboxes, color, text, icon);
    }

    private List<PatchObject> resolveDirectorPatch(FarmRunPatch patch)
    {
        if (client.getLocalPlayer() == null) return java.util.Collections.emptyList();
        DirectorQuestTargets.Definition def = DirectorQuestTargets.get(patch);
        if (def != null)
        {
            List<PatchObject> exactObjects = new ArrayList<>();
            for (GameObject object : plugin.getDirectorObjects())
            {
                if (object.getWorldView() == client.getLocalPlayer().getWorldView() &&
                    DirectorQuestTargets.matches(client, object, def) &&
                    DirectorQuestTargets.atPoint(object, DirectorWaypoints.exactPoint(patch)))
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
        WorldPoint exact = patch.getPatchType() == PatchType.HERB || DirectorStepEngine.isTreeType(patch.getPatchType())
            ? DirectorWaypoints.exactPoint(patch) : null;
        int count = states.patchNamesForType(patch.getPatchType().name(), patch.getLocation()).size();
        int selected = DirectorWorldGuide.select(patch, positions, count, exact);
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
