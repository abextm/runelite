package net.runelite.client.plugins.timetracking.farming;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.annotations.Varbit;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class CompostTracker
{
	@Value
	@VisibleForTesting
	static class PendingCompost
	{
		private final Instant queuedTime;
		private final WorldPoint patchLocation;
		private final FarmingPatch farmingPatch;
	}

	private static final Duration COMPOST_ACTION_TIMEOUT = Duration.ofSeconds(30);

	private static final Pattern COMPOST_USED_ON_PATCH = Pattern.compile(
		"You treat the (\\w+)( patch)? with (?<compostType>ultra|super|)compost\\.");
	private static final Pattern FERTILE_SOIL_CAST = Pattern.compile(
		"The (\\w+)( patch)? has been treated with (?<compostType>ultra|super|)compost\\.");
	private static final Pattern ALREADY_TREATED = Pattern.compile(
		"This (\\w+)( patch)? has already been (treated|fertilised) with (?<compostType>ultra|super|)compost( - the spell can't make it any more fertile)?\\.");
	private static final Pattern INSPECT_PATCH = Pattern.compile(
		"This is an? (\\w+)( patch)?\\. The soil has been treated with (?<compostType>ultra|super|)compost\\..*");

	private static final ImmutableSet<Integer> compostItems = ImmutableSet.of(
		ItemID.COMPOST,
		ItemID.SUPERCOMPOST,
		ItemID.ULTRACOMPOST,
		ItemID.BOTTOMLESS_COMPOST_BUCKET_22997
	);

	private final Client client;
	private final FarmingWorld farmingWorld;
	private final ConfigManager configManager;

	@VisibleForTesting
	final Map<FarmingPatch, PendingCompost> pendingCompostActions = new HashMap<>();

	private static String configKey(FarmingPatch fp)
	{
		return fp.configKey() + "." + TimeTrackingConfig.COMPOST;
	}

	public void setCompostState(FarmingPatch fp, Compost state)
	{
		log.debug("Storing compost state [{}] for patch [{}]", state, fp);
		if (state == null)
		{
			configManager.unsetRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, configKey(fp));
		}
		else
		{
			configManager.setRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, configKey(fp), state);
		}
	}

	public Compost getCompostState(FarmingPatch fp)
	{
		return configManager.getRSProfileConfiguration(TimeTrackingConfig.CONFIG_GROUP, configKey(fp), Compost.class);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if (!(e.getMenuAction() == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT
			|| (e.getMenuAction() == MenuAction.GAME_OBJECT_SECOND_OPTION && "Inspect".equals(e.getMenuOption()))))
		{
			return;
		}

		ObjectComposition lc = client.getObjectDefinition(e.getId());
		@Varbit int varbit = lc.getVarbitId();

		WorldPoint objLocation = WorldPoint.fromScene(client, e.getParam0(), e.getParam1(), client.getPlane());

		FarmingPatch farmingPatch = farmingWorld.getRegionsForLocation(objLocation).stream()
			.flatMap(r -> Stream.of(r.getPatches()))
			.filter(p -> p.getVarbit() == varbit)
			.findFirst()
			.orElse(null);
		if (farmingPatch == null)
		{
			return;
		}

		if (e.getMenuAction() == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			Widget w = client.getSelectedWidget();
			assert w != null;
			int itemId = w.getItemId();
			if (!compostItems.contains(itemId) && w.getId() != WidgetInfo.SPELL_LUNAR_FERTILE_SOIL.getPackedId())
			{
				return;
			}
		}

		PendingCompost pc = new PendingCompost(
			Instant.now(),
			objLocation,
			farmingPatch
		);
		log.debug("Storing pending compost action [{}] for patch [{}]", pc, farmingPatch);
		pendingCompostActions.put(farmingPatch, pc);
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if (e.getType() != ChatMessageType.GAMEMESSAGE && e.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		Compost compostUsed = determineCompostUsed(e.getMessage());
		if (compostUsed == null)
		{
			return;
		}

		this.filterTimeouts();

		pendingCompostActions.values()
			.stream()
			.filter(this::playerIsBesidePatch)
			.findFirst()
			.ifPresent(pc ->
			{
				setCompostState(pc.getFarmingPatch(), compostUsed);
				pendingCompostActions.remove(pc.getFarmingPatch());
			});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		switch (e.getGameState())
		{
			case LOGGED_IN:
			case LOADING:
				return;

			default:
				pendingCompostActions.clear();
		}
	}

	private boolean playerIsBesidePatch(PendingCompost pendingCompost)
	{
		// find gameobject instance in scene
		// it is possible that the scene has reloaded between use and action occurring so we use worldpoint
		// instead of storing scene coords in the menuoptionclicked event
		LocalPoint localPatchLocation = LocalPoint.fromWorld(client, pendingCompost.getPatchLocation());
		if (localPatchLocation == null)
		{
			return false;
		}

		Tile patchTile = client.getScene().getTiles()[client.getPlane()][localPatchLocation.getSceneX()][localPatchLocation.getSceneY()];
		GameObject patchObject = null;
		for (GameObject go : patchTile.getGameObjects())
		{
			if (go != null && client.getObjectDefinition(go.getId()).getVarbitId() == pendingCompost.getFarmingPatch().getVarbit())
			{
				patchObject = go;
				break;
			}
		}
		assert patchObject != null;

		// player coords
		Player p = client.getLocalPlayer();
		int playerX = p.getWorldLocation().getX();
		int playerY = p.getWorldLocation().getY();

		// patch coords
		int minX = patchObject.getWorldLocation().getX();
		int minY = patchObject.getWorldLocation().getY();
		int maxX = minX + patchObject.sizeX() - 1;
		int maxY = minY + patchObject.sizeY() - 1;

		// player should be within one tile of these coords
		// todo corner detection?
		return playerX >= (minX - 1) && playerX <= (maxX + 1) && playerY >= (minY - 1) && playerY <= (maxY + 1);
	}

	private void filterTimeouts()
	{
		Instant timeout = Instant.now().minus(COMPOST_ACTION_TIMEOUT);
		pendingCompostActions.values().removeIf(a -> a.getQueuedTime().isBefore(timeout));
	}

	@VisibleForTesting
	Compost determineCompostUsed(String chatMessage)
	{
		if (!chatMessage.contains("compost"))
		{
			return null;
		}

		Matcher matcher;
		if ((matcher = COMPOST_USED_ON_PATCH.matcher(chatMessage)).matches() ||
			(matcher = FERTILE_SOIL_CAST.matcher(chatMessage)).matches() ||
			(matcher = ALREADY_TREATED.matcher(chatMessage)).matches() ||
			(matcher = INSPECT_PATCH.matcher(chatMessage)).matches())
		{
			String compostGroup = matcher.group("compostType");
			switch (compostGroup)
			{
				case "ultra":
					return Compost.ULTRACOMPOST;
				case "super":
					return Compost.SUPERCOMPOST;
				default:
					return Compost.COMPOST;
			}
		}

		return null;
	}
}