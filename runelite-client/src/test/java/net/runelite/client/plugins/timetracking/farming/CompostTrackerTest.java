package net.runelite.client.plugins.timetracking.farming;

import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.time.Instant;
import java.util.Collections;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CompostTrackerTest
{
	
	@Inject
	private CompostTracker compostTracker;

	@Mock
	@Bind
	private Client client;

	@Mock
	@Bind
	private FarmingWorld farmingWorld;

	@Mock
	@Bind
	private ConfigManager configManager;
	
	@Mock
	@Bind
	private FarmingRegion farmingRegion;
	
	@Mock
	@Bind
	private FarmingPatch farmingPatch;
	
	@Mock
	@Bind
	private GameObject patchObject;
	
	@Mock
	@Bind
	private Player player;
	
	@Mock
	@Bind
	private Scene scene;
	
	@Mock
	@Bind
	private Tile tile;

	@Rule
	public ErrorCollector collector = new ErrorCollector();

	private static final int PATCH_ID = 12345;
	private static final WorldPoint worldPoint = new WorldPoint(1, 2, 0); // can't be mocked
	
	@Before
	public void before()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
		compostTracker.pendingCompostActions.clear();

		when(client.getBaseX()).thenReturn(0);
		when(client.getBaseY()).thenReturn(0);
		when(client.getPlane()).thenReturn(0);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldLocation()).thenReturn(worldPoint);
		when(client.getScene()).thenReturn(scene);
		when(scene.getTiles()).thenReturn(new Tile[][][]{{null, {null, null, tile}}}); // indices match worldPoint
		when(tile.getGameObjects()).thenReturn(new GameObject[]{patchObject});
		when(farmingWorld.getRegionsForLocation(any())).thenReturn(Collections.singleton(farmingRegion));
		when(farmingRegion.getPatches()).thenReturn(new FarmingPatch[] {farmingPatch});
		when(farmingPatch.configKey()).thenReturn("MOCK");
		when(farmingPatch.getGameObjectIds()).thenReturn(Collections.singletonList(PATCH_ID));
		when(patchObject.getWorldLocation()).thenReturn(worldPoint);
		when(patchObject.getId()).thenReturn(PATCH_ID);
		when(patchObject.sizeX()).thenReturn(1);
		when(patchObject.sizeY()).thenReturn(1);
	}

	@Test
	public void setCompostState_storesNonNullChangesToConfig()
	{
		compostTracker.setCompostState(farmingPatch, Compost.COMPOST);
		verify(configManager).setRSProfileConfiguration("timetracking", "MOCK.compost", Compost.COMPOST);
	}

	@Test
	public void setCompostState_storesNullChangesByClearingConfig()
	{
		compostTracker.setCompostState(farmingPatch, null);
		verify(configManager).unsetRSProfileConfiguration("timetracking", "MOCK.compost");
	}

	@Test
	public void getCompostState_directlyReturnsFromConfig()
	{
		when(configManager.getRSProfileConfiguration("timetracking", "MOCK.compost", Compost.class)).thenReturn(Compost.SUPERCOMPOST);
		assertThat(compostTracker.getCompostState(farmingPatch), is(Compost.SUPERCOMPOST));
	}
	
	@Test
	public void determineCompostUsed_returnsAppropriateCompostValues()
	{
		// invalid
		collector.checkThat(
			compostTracker.determineCompostUsed("This is not a farming chat message."),
			is((Compost) null)
		);
		collector.checkThat(
			compostTracker.determineCompostUsed("Contains word compost but is not examine message."),
			is((Compost) null)
		);
		
		// inspect
		collector.checkThat(
			compostTracker.determineCompostUsed("This is an allotment. The soil has been treated with supercompost. The patch is empty and weeded."),
			is(Compost.SUPERCOMPOST)
		);
		
		// fertile soil on existing patch
		collector.checkThat(
			compostTracker.determineCompostUsed("This patch has already been fertilised with ultracompost - the spell can't make it any more fertile."),
			is(Compost.ULTRACOMPOST)
		);
		// fertile soil on cleared patch
		collector.checkThat(
			compostTracker.determineCompostUsed("The herb patch has been treated with supercompost."),
			is(Compost.SUPERCOMPOST)
		);
		
		// bucket on cleared patch
		collector.checkThat(
			compostTracker.determineCompostUsed("You treat the herb patch with ultracompost."),
			is(Compost.ULTRACOMPOST)
		);
		collector.checkThat(
			compostTracker.determineCompostUsed("You treat the tree patch with compost."),
			is(Compost.COMPOST)
		);
	}
	
	@Test
	public void onMenuOptionClicked_ignoresConsumedActions()
	{
		MenuOptionClicked alreadyConsumed = mock(MenuOptionClicked.class);
		when(alreadyConsumed.isConsumed()).thenReturn(true);
		
		compostTracker.onMenuOptionClicked(alreadyConsumed);
		
		verifyNoInteractions(client);
		verifyNoInteractions(farmingWorld);
	}
	
	@Test
	public void onMenuOptionClicked_queuesPendingCompostForInspectActions()
	{
		MenuOptionClicked inspectPatchAction = mock(MenuOptionClicked.class);
		when(inspectPatchAction.getMenuAction()).thenReturn(MenuAction.GAME_OBJECT_SECOND_OPTION);
		when(inspectPatchAction.getMenuOption()).thenReturn("Inspect");
		when(inspectPatchAction.getId()).thenReturn(PATCH_ID);
		when(inspectPatchAction.getParam0()).thenReturn(1);
		when(inspectPatchAction.getParam1()).thenReturn(2);
		
		compostTracker.onMenuOptionClicked(inspectPatchAction);
		CompostTracker.PendingCompost actual = compostTracker.pendingCompostActions.get(farmingPatch);

		assertThat(actual.getFarmingPatch(), is(farmingPatch));
		assertThat(actual.getPatchLocation(), is(new WorldPoint(1, 2, 0)));
	}
	
	@Test
	public void onMenuOptionClicked_queuesPendingCompostForCompostActions()
	{
		Widget widget = mock(Widget.class);
		when(client.getSelectedWidget()).thenReturn(widget);
		when(widget.getItemId()).thenReturn(ItemID.ULTRACOMPOST);
		
		MenuOptionClicked inspectPatchAction = mock(MenuOptionClicked.class);
		when(inspectPatchAction.getMenuAction()).thenReturn(MenuAction.WIDGET_TARGET_ON_GAME_OBJECT);
		when(inspectPatchAction.getId()).thenReturn(PATCH_ID);
		when(inspectPatchAction.getParam0()).thenReturn(1);
		when(inspectPatchAction.getParam1()).thenReturn(2);
		
		compostTracker.onMenuOptionClicked(inspectPatchAction);
		CompostTracker.PendingCompost actual = compostTracker.pendingCompostActions.get(farmingPatch);

		assertThat(actual.getFarmingPatch(), is(farmingPatch));
		assertThat(actual.getPatchLocation(), is(new WorldPoint(1, 2, 0)));
	}
	
	@Test
	public void onMenuOptionClicked_queuesPendingCompostForFertileSoilSpellActions()
	{
		Widget widget = mock(Widget.class);
		when(client.getSelectedWidget()).thenReturn(widget);
		when(widget.getId()).thenReturn(WidgetInfo.SPELL_LUNAR_FERTILE_SOIL.getPackedId());
		
		MenuOptionClicked inspectPatchAction = mock(MenuOptionClicked.class);
		when(inspectPatchAction.getMenuAction()).thenReturn(MenuAction.WIDGET_TARGET_ON_GAME_OBJECT);
		when(inspectPatchAction.getId()).thenReturn(PATCH_ID);
		when(inspectPatchAction.getParam0()).thenReturn(1);
		when(inspectPatchAction.getParam1()).thenReturn(2);
		
		compostTracker.onMenuOptionClicked(inspectPatchAction);
		CompostTracker.PendingCompost actual = compostTracker.pendingCompostActions.get(farmingPatch);

		assertThat(actual.getFarmingPatch(), is(farmingPatch));
		assertThat(actual.getPatchLocation(), is(new WorldPoint(1, 2, 0)));
	}
	
	@Test
	public void onChatMessage_ignoresInvalidTypes()
	{
		ChatMessage chatEvent = mock(ChatMessage.class);
		when(chatEvent.getType()).thenReturn(ChatMessageType.PUBLICCHAT);

		compostTracker.onChatMessage(chatEvent);

		verifyNoInteractions(client);
		verifyNoInteractions(farmingWorld);
	}
	
	@Test
	public void onChatMessage_handlesInspectMessages()
	{
		ChatMessage chatEvent = mock(ChatMessage.class);
		when(chatEvent.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
		when(chatEvent.getMessage()).thenReturn("This is a tree patch. The soil has been treated with supercompost. The patch is empty and weeded.");

		compostTracker.pendingCompostActions.put(farmingPatch, new CompostTracker.PendingCompost(Instant.now(), worldPoint, farmingPatch));
		compostTracker.onChatMessage(chatEvent);

		verify(configManager).setRSProfileConfiguration("timetracking", "MOCK.compost", Compost.SUPERCOMPOST);
	}
	
	@Test
	public void onChatMessage_handlesBucketUseMessages()
	{
		ChatMessage chatEvent = mock(ChatMessage.class);
		when(chatEvent.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
		when(chatEvent.getMessage()).thenReturn("You treat the herb patch with compost.");

		compostTracker.pendingCompostActions.put(farmingPatch, new CompostTracker.PendingCompost(Instant.now(), worldPoint, farmingPatch));
		compostTracker.onChatMessage(chatEvent);

		verify(configManager).setRSProfileConfiguration("timetracking", "MOCK.compost", Compost.COMPOST);
	}
	
	@Test
	public void onChatMessage_handlesFertileSoilMessages()
	{
		ChatMessage chatEvent = mock(ChatMessage.class);
		when(chatEvent.getType()).thenReturn(ChatMessageType.GAMEMESSAGE);
		when(chatEvent.getMessage()).thenReturn("The allotment has been treated with supercompost.");

		compostTracker.pendingCompostActions.put(farmingPatch, new CompostTracker.PendingCompost(Instant.now(), worldPoint, farmingPatch));
		compostTracker.onChatMessage(chatEvent);

		verify(configManager).setRSProfileConfiguration("timetracking", "MOCK.compost", Compost.SUPERCOMPOST);
	}

}
