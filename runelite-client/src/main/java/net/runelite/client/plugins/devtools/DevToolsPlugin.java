/*
 * Copyright (c) 2017, Kronos <https://github.com/KronosDesign>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.devtools;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Provides;
import com.sun.corba.se.spi.ior.ObjectId;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import static java.lang.Math.min;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.NpcID;
import net.runelite.api.NullItemID;
import net.runelite.api.NullObjectID;
import net.runelite.api.ObjectComposition;
import net.runelite.api.ObjectID;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.RasterizerState;
import net.runelite.api.Sequence;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.ExperienceChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.kit.KitType;
import net.runelite.api.model.Vertex;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.timetracking.farming.PatchImplementation;
import net.runelite.client.plugins.timetracking.farming.PatchState;
import net.runelite.client.plugins.timetracking.farming.Produce;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Developer Tools",
	tags = {"panel"},
	developerPlugin = true
)
@Slf4j
@Getter
public class DevToolsPlugin extends Plugin
{
	private static final List<MenuAction> EXAMINE_MENU_ACTIONS = ImmutableList.of(MenuAction.EXAMINE_ITEM,
		MenuAction.EXAMINE_ITEM_GROUND, MenuAction.EXAMINE_NPC, MenuAction.EXAMINE_OBJECT);

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DevToolsOverlay overlay;

	@Inject
	private LocationOverlay locationOverlay;

	@Inject
	private SceneOverlay sceneOverlay;

	@Inject
	private CameraOverlay cameraOverlay;

	@Inject
	private WorldMapLocationOverlay worldMapLocationOverlay;

	@Inject
	private WorldMapRegionOverlay mapRegionOverlay;

	@Inject
	private EventBus eventBus;

	private DevToolsButton players;
	private DevToolsButton npcs;
	private DevToolsButton groundItems;
	private DevToolsButton groundObjects;
	private DevToolsButton gameObjects;
	private DevToolsButton graphicsObjects;
	private DevToolsButton walls;
	private DevToolsButton decorations;
	private DevToolsButton inventory;
	private DevToolsButton projectiles;
	private DevToolsButton location;
	private DevToolsButton chunkBorders;
	private DevToolsButton mapSquares;
	private DevToolsButton validMovement;
	private DevToolsButton lineOfSight;
	private DevToolsButton cameraPosition;
	private DevToolsButton worldMapLocation;
	private DevToolsButton tileLocation;
	private DevToolsButton interacting;
	private DevToolsButton examine;
	private DevToolsButton detachedCamera;
	private DevToolsButton widgetInspector;
	private DevToolsButton varInspector;
	private NavigationButton navButton;

	@Provides
	DevToolsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DevToolsConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		players = new DevToolsButton("Players");
		npcs = new DevToolsButton("NPCs");

		groundItems = new DevToolsButton("Ground Items");
		groundObjects = new DevToolsButton("Ground Objects");
		gameObjects = new DevToolsButton("Game Objects");
		graphicsObjects = new DevToolsButton("Graphics Objects");
		walls = new DevToolsButton("Walls");
		decorations = new DevToolsButton("Decorations");

		inventory = new DevToolsButton("Inventory");
		projectiles = new DevToolsButton("Projectiles");

		location = new DevToolsButton("Location");
		worldMapLocation = new DevToolsButton("World Map Location");
		tileLocation = new DevToolsButton("Tile Location");
		cameraPosition = new DevToolsButton("Camera Position");

		chunkBorders = new DevToolsButton("Chunk Borders");
		mapSquares = new DevToolsButton("Map Squares");

		lineOfSight = new DevToolsButton("Line Of Sight");
		validMovement = new DevToolsButton("Valid Movement");
		interacting = new DevToolsButton("Interacting");
		examine = new DevToolsButton("Examine");

		detachedCamera = new DevToolsButton("Detached Camera");
		widgetInspector = new DevToolsButton("Widget Inspector");
		varInspector = new DevToolsButton("Var Inspector");

		overlayManager.add(overlay);
		overlayManager.add(locationOverlay);
		overlayManager.add(sceneOverlay);
		overlayManager.add(cameraOverlay);
		overlayManager.add(worldMapLocationOverlay);
		overlayManager.add(mapRegionOverlay);

		final DevToolsPanel panel = injector.getInstance(DevToolsPanel.class);

		final BufferedImage icon = ImageUtil.getResourceStreamFromClass(getClass(), "devtools_icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Developer Tools")
			.icon(icon)
			.priority(1)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		overlayManager.remove(locationOverlay);
		overlayManager.remove(sceneOverlay);
		overlayManager.remove(cameraOverlay);
		overlayManager.remove(worldMapLocationOverlay);
		overlayManager.remove(mapRegionOverlay);
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted commandExecuted)
	{
		String[] args = commandExecuted.getArguments();

		switch (commandExecuted.getCommand())
		{
			case "logger":
			{
				final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
				String message;
				Level currentLoggerLevel = logger.getLevel();

				if (args.length < 1)
				{
					message = "Logger level is currently set to " + currentLoggerLevel;
				}
				else
				{
					Level newLoggerLevel = Level.toLevel(args[0], currentLoggerLevel);
					logger.setLevel(newLoggerLevel);
					message = "Logger level has been set to " + newLoggerLevel;
				}

				client.addChatMessage(ChatMessageType.SERVER, "", message, null);
				break;
			}
			case "getvarp":
			{
				int varp = Integer.parseInt(args[0]);
				int value = client.getVarpValue(client.getVarps(), varp);
				client.addChatMessage(ChatMessageType.SERVER, "", "VarPlayer " + varp + ": " + value, null);
				break;
			}
			case "setvarp":
			{
				int varp = Integer.parseInt(args[0]);
				int value = Integer.parseInt(args[1]);
				client.setVarpValue(client.getVarps(), varp, value);
				client.addChatMessage(ChatMessageType.SERVER, "", "Set VarPlayer " + varp + " to " + value, null);
				eventBus.post(new VarbitChanged()); // fake event
				break;
			}
			case "getvarb":
			{
				int varbit = Integer.parseInt(args[0]);
				int value = client.getVarbitValue(client.getVarps(), varbit);
				client.addChatMessage(ChatMessageType.SERVER, "", "Varbit " + varbit + ": " + value, null);
				break;
			}
			case "setvarb":
			{
				int varbit = Integer.parseInt(args[0]);
				int value = Integer.parseInt(args[1]);
				client.setVarbitValue(client.getVarps(), varbit, value);
				client.addChatMessage(ChatMessageType.SERVER, "", "Set varbit " + varbit + " to " + value, null);
				eventBus.post(new VarbitChanged()); // fake event
				break;
			}
			case "addxp":
			{
				Skill skill = Skill.valueOf(args[0].toUpperCase());
				int xp = Integer.parseInt(args[1]);

				int totalXp = client.getSkillExperience(skill) + xp;
				int level = min(Experience.getLevelForXp(totalXp), 99);

				client.getBoostedSkillLevels()[skill.ordinal()] = level;
				client.getRealSkillLevels()[skill.ordinal()] = level;
				client.getSkillExperiences()[skill.ordinal()] = totalXp;

				client.queueChangedSkill(skill);

				ExperienceChanged experienceChanged = new ExperienceChanged();
				experienceChanged.setSkill(skill);
				eventBus.post(experienceChanged);
				break;
			}
			case "anim":
			{
				int id = Integer.parseInt(args[0]);
				Player localPlayer = client.getLocalPlayer();
				localPlayer.setAnimation(id);
				localPlayer.setActionFrame(0);
				break;
			}
			case "gfx":
			{
				int id = Integer.parseInt(args[0]);
				Player localPlayer = client.getLocalPlayer();
				localPlayer.setGraphic(id);
				localPlayer.setSpotAnimFrame(0);
				break;
			}
			case "transform":
			{
				int id = Integer.parseInt(args[0]);
				Player player = client.getLocalPlayer();
				player.getPlayerComposition().setTransformedNpcId(id);
				player.setIdlePoseAnimation(-1);
				player.setPoseAnimation(-1);
				break;
			}
			case "cape":
			{
				int id = Integer.parseInt(args[0]);
				Player player = client.getLocalPlayer();
				player.getPlayerComposition().getEquipmentIds()[KitType.CAPE.getIndex()] = id + 512;
				player.getPlayerComposition().setHash();
				break;
			}
			case "oops":
			{
				Player player = client.getLocalPlayer();
				log.info("{}", player.getPlayerComposition().getEquipmentIds());
				/**player.getPlayerComposition().getEquipmentIds()[0] = ItemID.MYSTIC_HAT_DUSK + 512;
				 player.getPlayerComposition().getEquipmentIds()[KitType.BOOTS.getIndex()] = ItemID.MYSTIC_BOOTS_DUSK + 512;
				 player.getPlayerComposition().getEquipmentIds()[KitType.HANDS.getIndex()] = ItemID.MYSTIC_GLOVES_DUSK + 512;
				 player.getPlayerComposition().getEquipmentIds()[KitType.LEGS.getIndex()] = ItemID.MYSTIC_ROBE_BOTTOM_DUSK + 512;
				 player.getPlayerComposition().getEquipmentIds()[KitType.TORSO.getIndex()] = ItemID.MYSTIC_ROBE_TOP_DUSK + 512;
				 player.getPlayerComposition().setHash();*/
				// that ^ doesn't really work right because you don't get a face
				log.info("{}", player.getPlayerComposition().getEquipmentIds());
				BufferedImage img = new BufferedImage(1536, 1536, BufferedImage.TYPE_INT_ARGB_PRE);
				RasterizerState c = client.rasterizerSwitchToImage(img);
				try
				{
					Model var21 = client.getLocalPlayer().getModel();
					int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
					int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
					for (Vertex vx : var21.getVertices())
					{
						if (minX > vx.getX())
						{
							minX = vx.getX();
						}
						if (maxX < vx.getX())
						{
							maxX = vx.getX();
						}
						if (minY > vx.getY())
						{
							minY = vx.getY();
						}
						if (maxY < vx.getY())
						{
							maxY = vx.getY();
						}
						if (minZ > vx.getZ())
						{
							minZ = vx.getZ();
						}
						if (maxZ < vx.getZ())
						{
							maxZ = vx.getZ();
						}
					}

					int zoom2d = Math.max((maxX - minX), Math.max((maxY - minY), (maxZ - minZ))) * 24;

					//int zoom2d = (int) (5000 * 1.5d);
					int yan2d = 128;
					int xan2d = 128;
					int zan2d = 0;
					int offsetX2d = 0;
					int offsetY2d = 0;
					int var17 = zoom2d * Perspective.SINE[xan2d] >> 16;
					int var18 = zoom2d * Perspective.COSINE[xan2d] >> 16;

					var21.calculateBoundsCylinder();
					var21.drawFrustum(0,
						yan2d, zan2d, xan2d,
						0,
						var17 - (minY + maxY) / 2,
						var18);
					ImageIO.write(img, "png", new File("r:/oops.png"));
				}
				catch (Exception ex)
				{
					log.info("Cannot render", ex);
				}
				finally
				{
					client.rasterizerRestoreState(c, img);
				}
			}
			break;
			case "book":
				String title = client.getWidget(27, 3).getText();
				StringBuilder content = new StringBuilder();
				for (int i = 33; i <= 62; i++)
				{
					content.append(client.getWidget(27, i).getText()).append("\n");
				}
				String finame = title + "_pg " + client.getWidget(27, 98).getText() + "_" + client.getWidget(27, 99).getText() + ".txt";
				try
				{
					Files.write(new File("r:/" + finame).toPath(), content.toString().getBytes());
				}
				catch (IOException e)
				{
					log.warn("", e);
				}
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!examine.isActive())
		{
			return;
		}

		MenuAction action = MenuAction.of(event.getType());

		if (EXAMINE_MENU_ACTIONS.contains(action))
		{
			MenuEntry[] entries = client.getMenuEntries();
			MenuEntry entry = entries[entries.length - 1];

			final int identifier = event.getIdentifier();
			String info = "ID: ";

			if (action == MenuAction.EXAMINE_NPC)
			{
				NPC npc = client.getCachedNPCs()[identifier];
				info += npc.getId();
			}
			else
			{
				info += identifier;

				if (action == MenuAction.EXAMINE_OBJECT)
				{
					WorldPoint point = WorldPoint.fromScene(client, entry.getParam0(), entry.getParam1(), client.getPlane());
					info += " X: " + point.getX() + " Y: " + point.getY();
				}
			}

			entry.setTarget(entry.getTarget() + " " + ColorUtil.prependColorTag("(" + info + ")", JagexColors.MENU_TARGET));
			client.setMenuEntries(entries);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if (e.getMenuAction() == MenuAction.EXAMINE_NPC)
		{
			Actor r = client.getCachedNPCs()[e.getId()];
			BufferedImage img = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB_PRE);
			RasterizerState c = client.rasterizerSwitchToImage(img);
			try
			{
				Model var21 = r.getModel();
				int zoom2d = 4000;
				int yan2d = 0;
				int xan2d = 0;
				int zan2d = 0;
				int offsetX2d = 0;
				int offsetY2d = 0;
				int var17 = zoom2d * Perspective.SINE[xan2d] >> 16;
				int var18 = zoom2d * Perspective.COSINE[xan2d] >> 16;
				var21.calculateBoundsCylinder();
				var21.drawFrustum(0, yan2d, zan2d, xan2d, offsetX2d, var21.getModelHeight() / 2 + var17 + offsetY2d, var18 + offsetY2d);

			}
			finally
			{
				client.rasterizerRestoreState(c, img);
			}
			try
			{
				ImageIO.write(img, "png", new File("r:/" + r.getName() + ".png"));
			}
			catch (IOException ee)
			{
				ee.printStackTrace();
			}
		}
	}

	int maxzoom2d = 0;

	@Subscribe
	public void onGameStateChanged(GameStateChanged e) throws Exception
	{
		if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			double brightness = .6f;
			client.setBrightness(brightness);
			client.getTextureProvider().setBrightness(brightness);
			LinkedBlockingQueue<Callable<Boolean>> todo = new LinkedBlockingQueue<>(16);
			for (int i = 0; i < 7; i++)
			{
				new Thread(() -> {
					for (; ; )
					{
						try
						{
							todo.take().call();
						}
						catch (Exception ee)
						{
							throw new RuntimeException(ee);
						}
					}
				}).start();
			}
		/*
			for (Field f : NullItemID.class.getFields())
			{
				String name = f.getName().toLowerCase();
				int id = (int) f.get(null);
				if (id != 5092 && id != 22565 && id != 22802) continue;

				ItemComposition oc = client.getItemDefinition(id);
				BufferedImage img = new BufferedImage(1536, 1536, BufferedImage.TYPE_INT_ARGB_PRE);
				RasterizerState c = client.rasterizerSwitchToImage(img);
				try
				{
					Model var21 = oc.getModel(1);
					int zoom2d = (int) (oc.getZoom2d() * 1.5d);
					int yan2d = oc.getYan2d();
					int xan2d = oc.getXan2d();
					int zan2d = oc.getZan2d();
					int offsetX2d = oc.getOffsetX2d();
					int offsetY2d = oc.getOffsetY2d();
					int var17 = zoom2d * Perspective.SINE[xan2d] >> 16;
					int var18 = zoom2d * Perspective.COSINE[xan2d] >> 16;
					var21.calculateBoundsCylinder();
					var21.drawFrustum(0, yan2d, zan2d, xan2d, offsetX2d, var21.getModelHeight() / 2 + var17 + offsetY2d, var18 + offsetY2d);

				}
				finally
				{
					client.rasterizerRestoreState(c, img);
				}
				todo.put(() -> ImageIO.write(img, "png", new File("r:/items/" + name + ".png")));
			}/**/

			for (Field f : NpcID.class.getFields())
			{
				String name = f.getName().toLowerCase();
				int id = (int) f.get(null);
				if (id != 4724 && id != 4725 && id != 4726 && id != 4727)
				{
					continue;
				}

				NPCComposition nc = client.getNpcDefinition(id);
				Sequence anim = nc.getWalkingAnimation() == -1 ? null : client.getAnimation(nc.getWalkingAnimation());

				int[] frames;
				if (anim == null)
				{
					frames = new int[]{0};
				}
				else
				{
					frames = IntStream.range(0, anim.getFrameIDs().length).toArray();
				}
				for (int frame : frames)
				{
					BufferedImage img = new BufferedImage(1536, 1536, BufferedImage.TYPE_INT_ARGB_PRE);
					RasterizerState c = client.rasterizerSwitchToImage(img);
					try
					{
						Model var21 = nc.getModel(null, 0, anim, frame);
						int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
						int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
						for (Vertex vx : var21.getVertices())
						{
							if (minX > vx.getX())
							{
								minX = vx.getX();
							}
							if (maxX < vx.getX())
							{
								maxX = vx.getX();
							}
							if (minY > vx.getY())
							{
								minY = vx.getY();
							}
							if (maxY < vx.getY())
							{
								maxY = vx.getY();
							}
							if (minZ > vx.getZ())
							{
								minZ = vx.getZ();
							}
							if (maxZ < vx.getZ())
							{
								maxZ = vx.getZ();
							}
						}

						int zoom2d = Math.max((maxX - minX), Math.max((maxY - minY), (maxZ - minZ))) * 24;

						//int zoom2d = (int) (5000 * 1.5d);
						int yan2d = 128;
						int xan2d = 128;
						int zan2d = 0;
						int offsetX2d = 0;
						int offsetY2d = 0;
						int var17 = zoom2d * Perspective.SINE[xan2d] >> 16;
						int var18 = zoom2d * Perspective.COSINE[xan2d] >> 16;

						var21.calculateBoundsCylinder();
						var21.drawFrustum(0,
							yan2d, zan2d, xan2d,
							0,
							var17 - (minY + maxY) / 2,
							var18);
						todo.put(() -> ImageIO.write(img, "png", new File("r:/npcs/" + name + "_" + frame + ".png")));
					}
					catch (Exception ex)
					{
						log.info("Cannot render {}", id, ex);
					}
					finally
					{
						client.rasterizerRestoreState(c, img);
					}
				}
			}
			/**/
			/*
			Map<PatchImplementation, Integer> vs = new HashMap<>();
			/*vs.put(PatchImplementation.BELLADONNA, 7572);
			vs.put(PatchImplementation.MUSHROOM, 8337);
			vs.put(PatchImplementation.HESPORI, 34630);
			vs.put(PatchImplementation.ALLOTMENT, 33694);
			vs.put(PatchImplementation.HERB, 33979);
			vs.put(PatchImplementation.FLOWER, 33649);
			vs.put(PatchImplementation.BUSH, 34006);
			vs.put(PatchImplementation.FRUIT_TREE, 34007);
			vs.put(PatchImplementation.HOPS, 8173);
			vs.put(PatchImplementation.TREE, 33732);*//*
			vs.put(PatchImplementation.HARDWOOD_TREE, 30481);/*
			vs.put(PatchImplementation.SPIRIT_TREE, 33733);
			vs.put(PatchImplementation.ANIMA, 33998);
			vs.put(PatchImplementation.CACTUS, 33761);
			vs.put(PatchImplementation.SEAWEED, 30500);
			vs.put(PatchImplementation.CALQUAT, 7807);
			vs.put(PatchImplementation.CELASTRUS, 34629);*//*

			vs.entrySet().forEach(f -> {
				//try
				{
					PatchImplementation impl = f.getKey();
					ObjectComposition roc = client.getObjectDefinition(f.getValue());

					int[] objids = roc.getImpostorIds();

					for (int i = 0; i < objids.length; i++)
					{
						PatchState ps = impl.forVarbitValue(i);
						if (ps == null || ps.getProduce() == Produce.WEEDS) continue;;
						String name2 = impl.name() + "_" + i + "_" + ps.getProduce().getName() + "_" + ps.getCropState() + "_" + ps.getStage();
						ObjectComposition oc = client.getObjectDefinition(objids[i]);
/*
						int[] types = oc.getObjectTypes();
						if (types == null)
						{
							types = new int[]{10};
						}
						for (int type : types)
						{
							for (int rot : new int[]{
								0, 1, 2, 3, 4, 5, 6, 7
							})
							{
								String name3 = (oc.getObjectTypes() == null ? name2 : name2 + "_t" + type) + "_r" + rot;*//*

								BufferedImage img = new BufferedImage(409, 409, BufferedImage.TYPE_INT_ARGB_PRE);
								RasterizerState c = client.rasterizerSwitchToImage(img);
								try
								{
									ModelData data = oc.getModel(10, 0);
									if (data == null)
									{
										continue;
									}
									Model var21 = data.light(oc.getAmbient() + 64, oc.getContrast() * 25 + 768, -50, -10, -50);
									int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
									int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
									for (Vertex vx : var21.getVertices())
									{
										if (minX > vx.getX())
										{
											minX = vx.getX();
										}
										if (maxX < vx.getX())
										{
											maxX = vx.getX();
										}
										if (minY > vx.getY())
										{
											minY = vx.getY();
										}
										if (maxY < vx.getY())
										{
											maxY = vx.getY();
										}
										if (minZ > vx.getZ())
										{
											minZ = vx.getZ();
										}
										if (maxZ < vx.getZ())
										{
											maxZ = vx.getZ();
										}
									}

									int zoom2d = 16000;
									//int zoom2d = Math.max((maxX - minX), Math.max((maxY - minY), (maxZ - minZ))) * 24;

									/*if (zoom2d > maxzoom2d)
									{
										maxzoom2d = zoom2d;
										log.info("maxzoom2d{}", maxzoom2d);
									}
									if(true)continue;*//*

									//int zoom2d = (int) (5000 * 1.5d);
									int yan2d = 0;
									int xan2d = 0;
									int zan2d = 0;
									int offsetX2d = 0;
									int offsetY2d = 0;
									int var17 = zoom2d * Perspective.SINE[xan2d] >> 16;
									int var18 = zoom2d * Perspective.COSINE[xan2d] >> 16;

									//var21.calculateBoundsCylinder();
									var21.drawFrustum(0,
										yan2d, zan2d, xan2d,
										0,
										var17 - (minY + maxY) / 2,
										var18);
									todo.put(() -> ImageIO.write(img, "png", new File("r:/objs/" + name2 + ".png")));
								}
								catch (Exception ex)
								{
									log.info("Cannot render {}", oc.getId(), ex);
								}
								finally
								{
									client.rasterizerRestoreState(c, img);
								}
							}
						}
					/*}
				}
				catch (ReflectiveOperationException xe)
				{
					throw new RuntimeException(xe);
				}*/
			/*});/**/
		}
	}
}
