/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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
package net.runelite.client.plugins.banktags;

import com.google.common.base.Strings;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Provides;
import java.awt.Rectangle;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IntegerNode;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.SoundEffectID;
import net.runelite.api.SpritePixels;
import net.runelite.api.VarClientStr;
import net.runelite.api.WidgetType;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.DraggingWidgetChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetConfig;
import static net.runelite.api.widgets.WidgetConfig.DRAG;
import static net.runelite.api.widgets.WidgetConfig.DRAG_ON;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ChatboxInputManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Bank Tags",
	description = "Enable tagging of bank items and searching of bank tags",
	tags = {"searching", "tagging"}
)
@Slf4j
public class BankTagsPlugin extends Plugin implements MouseWheelListener
{
	static final String CONFIG_GROUP = "banktags";

	private static final String ITEM_KEY_PREFIX = "item_";

	private static final String SEARCH_BANK_INPUT_TEXT =
		"Show items whose names or tags contain the following text:<br>" +
			"(To show only tagged items, start your search with 'tag:')";

	private static final String SEARCH_BANK_INPUT_TEXT_FOUND =
		"Show items whose names or tags contain the following text: (%d found)<br>" +
			"(To show only tagged items, start your search with 'tag:')";

	private static final String TAG_SEARCH = "tag:";

	private static final String EDIT_TAGS_MENU_OPTION = "Edit-tags";

	private static final int EDIT_TAGS_MENU_INDEX = 8;

	private static final String SCROLL_UP = "Scroll Up";
	private static final String SCROLL_DOWN = "Scroll Down";
	private static final String CHANGE_ICON = "Set Tab Icon";
	private static final String CHANGE_ICON_ACTION = "Set tag:";
	private static final String REMOVE_TAB = "Remove Tab";
	private static final String NEW_TAB = "New Tag Tab";
	private static final String OPEN_TAG = "Open Tag";
	private static final String MOVE_TAB = "Move Tab";
	static final String ICON_SEARCH = "icon_";
	static final String TAG_TAGS_CONFIG = "tagtabs";

	static final int SCROLL_TICK = 500;
	static final int TAB_HEIGHT = 40;
	static final int TAB_WIDTH = 39;
	static final int MARGIN = 1;
	static final int BUTTON_HEIGHT = 20;

	private static final int INCINERATOR = -200;
	private static final int TAB_BACKGROUND = -201;
	private static final int TAB_BACKGROUND_ACTIVE = -202;
	private static final int UP_ARROW = -203;
	private static final int DOWN_ARROW = -204;
	private static final int NEW_TAG_TAB = -205;

	public Rectangle tabsBounds = new Rectangle();

	private TagTab focusedTab = null;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatboxInputManager chatboxInputManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private TooltipManager tooltipManager;

	@Inject
	private MouseManager mouseManager;

	@Provides
	BankTagsConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankTagsConfig.class);
	}

	@Inject
	private BankTagsConfig config;

	private Rectangle canvasBounds = new Rectangle();
	private Rectangle bounds = new Rectangle();

	@Inject
	private TabManager tabManager;

	private int idx = 0;
	private int maxTabs = 0;

	private boolean scrollWait = false;
	private boolean isBankOpen = false;

	private Widget upButton = null;
	private Widget downButton = null;
	private Widget newTab = null;
	private Widget parent = null;

	private TagTab iconToSet = null;
	private TagTab activeTab = null;

	private Map<Integer, SpritePixels> overrides = new HashMap<>();

	@Override
	public void startUp()
	{
		parent = client.getWidget(WidgetInfo.BANK_CONTENT_CONTAINER);

		if (parent != null && !parent.isHidden())
		{
			loadTabs();
		}

		mouseManager.registerMouseWheelListener(this);

		Map<Integer, SpritePixels> overrides = new HashMap<>();
		overrides.put(INCINERATOR, getSpritePixels(ImageUtil.getResourceStreamFromClass(getClass(), "incinerator.png")));
		overrides.put(TAB_BACKGROUND, getSpritePixels(ImageUtil.getResourceStreamFromClass(getClass(), "tag-tab.png")));
		overrides.put(TAB_BACKGROUND_ACTIVE, getSpritePixels(ImageUtil.getResourceStreamFromClass(getClass(), "tag-tab-active.png")));
		overrides.put(UP_ARROW, getSpritePixels(ImageUtil.getResourceStreamFromClass(getClass(), "up-arrow.png")));
		overrides.put(DOWN_ARROW, getSpritePixels(ImageUtil.getResourceStreamFromClass(getClass(), "down-arrow.png")));
		overrides.put(NEW_TAG_TAB, getSpritePixels(ImageUtil.getResourceStreamFromClass(getClass(), "new-tab.png")));
		client.setSpriteOverrides(overrides);
	}

	private SpritePixels getSpritePixels(BufferedImage image)
	{
		int[] pixels = new int[image.getWidth() * image.getHeight()];

		try
		{
			new PixelGrabber(image, 0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth())
				.grabPixels();
		}
		catch (InterruptedException ex)
		{
			log.debug("PixelGrabber was interrupted: ", ex);
		}

		return client.createSpritePixels(pixels, image.getWidth(), image.getHeight());
	}


	@Override
	public void shutDown()
	{
		upButton.setHidden(true);
		downButton.setHidden(true);
		newTab.setHidden(true);

		tabManager.clear();

		mouseManager.unregisterMouseWheelListener(this);
	}

	@Subscribe
	public void configChanged(ConfigChanged configChanged)
	{
		if (configChanged.getGroup().equals("banktags"))
		{
			if (configChanged.getKey().equals("rememberPosition"))
			{
				config.position(config.rememberPosition() ? idx : 0);
			}
			else if (configChanged.getKey().equals("useTabs"))
			{
				if (config.tabs())
				{
					loadTabs();
				}
				else
				{
					shutDown();
				}
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		if (widgetLoaded.getGroupId() == WidgetID.BANK_GROUP_ID)
		{
			parent = client.getWidget(WidgetInfo.BANK_CONTENT_CONTAINER);
			loadTabs();
		}
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged clientStrChanged)
	{
		if (clientStrChanged.getIndex() == VarClientStr.SEARCH_TEXT.getIndex())
		{
			String str = client.getVar(VarClientStr.SEARCH_TEXT);

			if (str != null)
			{
				str = str.trim();
			}

			if (str.startsWith(TAG_SEARCH))
			{
				TagTab tagTab = tabManager.find(str.substring(4));

				setActiveTab(tagTab);
			}
			else
			{
				setActiveTab(null);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (isBankOpen)
		{
			Widget widget = client.getWidget(WidgetInfo.BANK_CONTAINER);

			if (widget == null)
			{
				isBankOpen = false;
				parent = null;

				if (config.rememberPosition() && idx != config.position())
				{
					config.position(idx);
				}
				else if (!config.rememberPosition() && config.position() != 0)
				{
					config.position(0);
				}

				log.debug("bank closed");
			}
			else
			{
				updateTabs(0);
			}
		}
	}

	@Subscribe
	public void onScriptEvent(ScriptCallbackEvent event)
	{
		String eventName = event.getEventName();

		int[] intStack = client.getIntStack();
		String[] stringStack = client.getStringStack();
		int intStackSize = client.getIntStackSize();
		int stringStackSize = client.getStringStackSize();

		switch (eventName)
		{
			case "bankTagsActive":
				// tell the script the bank tag plugin is active
				intStack[intStackSize - 1] = 1;
				break;
			case "setSearchBankInputText":

				if (stringStackSize > 0)
				{
					stringStack[stringStackSize - 1] = SEARCH_BANK_INPUT_TEXT;
				}
				break;
			case "setSearchBankInputTextFound":
			{
				int matches = intStack[intStackSize - 1];
				stringStack[stringStackSize - 1] = String.format(SEARCH_BANK_INPUT_TEXT_FOUND, matches);
				break;
			}
			case "setBankItemMenu":
			{
				// set menu action index so the edit tags option will not be overridden
				if (intStackSize > 2)
				{
					intStack[intStackSize - 3] = EDIT_TAGS_MENU_INDEX;

					int itemId = intStack[intStackSize - 2];
					int tagCount = getTagCount(itemId);
					if (tagCount > 0)
					{
						stringStack[stringStackSize - 1] += " (" + tagCount + ")";
					}

					int index = intStack[intStackSize - 1];
					long key = (long) index + ((long) WidgetInfo.BANK_ITEM_CONTAINER.getId() << 32);
					IntegerNode flagNode = (IntegerNode) client.getWidgetFlags().get(key);
					if (flagNode != null && flagNode.getValue() != 0)
					{
						flagNode.setValue(flagNode.getValue() | WidgetConfig.SHOW_MENU_OPTION_NINE);
					}
				}

				break;
			}
			case "bankSearchFilter":
				int itemId = intStack[intStackSize - 1];
				String itemName = stringStack[stringStackSize - 2];
				String searchInput = stringStack[stringStackSize - 1];

				ItemComposition itemComposition = itemManager.getItemComposition(itemId);
				if (itemComposition.getPlaceholderTemplateId() != -1)
				{
					// if the item is a placeholder then get the item id for the normal item
					itemId = itemComposition.getPlaceholderId();
				}

				String tagsConfig = configManager.getConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId);
				if (tagsConfig == null || tagsConfig.length() == 0)
				{
					intStack[intStackSize - 2] = itemName.contains(searchInput) ? 1 : 0;
					return;
				}

				boolean tagSearch = searchInput.startsWith(TAG_SEARCH);
				String search;
				if (tagSearch)
				{
					search = searchInput.substring(TAG_SEARCH.length()).trim();
				}
				else
				{
					search = searchInput;
				}

				List<String> tags = Arrays.asList(tagsConfig.toLowerCase().split(","));

				if (tags.stream().anyMatch(tag -> tag.contains(search.toLowerCase())))
				{
					// return true
					intStack[intStackSize - 2] = 1;
				}
				else if (!tagSearch)
				{
					intStack[intStackSize - 2] = itemName.contains(search) ? 1 : 0;
				}
				break;
		}
	}

	@Subscribe
	public void draggedWidget(DraggingWidgetChanged event)
	{
		Widget draggedOn = client.getDraggedOnWidget();
		Widget draggedWidget = client.getDraggedWidget();

		if (isBankOpen && event.isDraggingWidget() && draggedOn != null)
		{
			// is dragging widget and mouse button released
			if (client.getMouseCurrentButton() == 0)
			{
				if (draggedWidget.getItemId() > 0 && draggedWidget.getId() != parent.getId())
				{
					// Tag an item dragged on a tag tab
					if (draggedOn.getId() == parent.getId())
					{
						String tag = Text.removeTags(draggedOn.getName());
						int itemId = draggedWidget.getItemId();

						ItemComposition itemComposition = itemManager.getItemComposition(itemId);
						if (itemComposition.getPlaceholderTemplateId() != -1)
						{
							// if the item is a placeholder then get the item id for the normal item
							itemId = itemComposition.getPlaceholderId();
						}

						appendTag(itemId, tag);
					}
				}
				else if (parent.getId() == draggedOn.getId() && parent.getId() == draggedWidget.getId())
				{
					// Reorder tag tabs
					String destinationTag = Text.removeTags(draggedOn.getName());
					String tagToMove = Text.removeTags(draggedWidget.getName());
					if (!Strings.isNullOrEmpty(destinationTag))
					{
						tabManager.move(tagToMove, destinationTag);
						tabManager.save();
						renderTabs();
					}
				}
			}
			else
			{
				if (draggedWidget.getItemId() > 0)
				{
					MenuEntry[] entries = client.getMenuEntries();


					if (entries.length > 0)
					{
						MenuEntry entry = entries[entries.length - 1];

						if (draggedWidget.getItemId() > 0 && entry.getOption().equals(OPEN_TAG) && draggedOn.getId() != draggedWidget.getId())
						{
							entry.setOption(TAG_SEARCH + Text.removeTags(entry.getTarget()));
							entry.setTarget(draggedWidget.getName());
							client.setMenuEntries(entries);
						}

						if (entry.getOption().equals(SCROLL_UP))
						{
							scrollDragging(-1, true);
						}
						else if (entry.getOption().equals(SCROLL_DOWN))
						{
							scrollDragging(1, true);
						}
					}
				}
			}
		}
	}

	@Subscribe
	public void onMenuOptionAdded(MenuEntryAdded menuEntryAdded)
	{
		if (isBankOpen)
		{
			MenuEntry[] entries = client.getMenuEntries();

			if (entries.length > 0)
			{
				MenuEntry entry = entries[entries.length - 1];

				if (iconToSet != null && (entry.getOption().equals("Withdraw-1") || entry.getOption().equals("Release")))
				{
					entry.setOption(CHANGE_ICON_ACTION + iconToSet.getTag() + " icon");
					client.setMenuEntries(entries);
				}
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (isBankOpen)
		{
			if (event.getWidgetId() == WidgetInfo.BANK_ITEM_CONTAINER.getId()
				&& event.getMenuAction() == MenuAction.EXAMINE_ITEM_BANK_EQ
				&& event.getId() == EDIT_TAGS_MENU_INDEX
				&& event.getMenuOption().startsWith(EDIT_TAGS_MENU_OPTION))
			{
				event.consume();
				int inventoryIndex = event.getActionParam();
				ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
				if (bankContainer == null)
				{
					return;
				}
				Item[] items = bankContainer.getItems();
				if (inventoryIndex < 0 || inventoryIndex >= items.length)
				{
					return;
				}
				Item item = bankContainer.getItems()[inventoryIndex];

				if (item == null)
				{
					return;
				}
				ItemComposition itemComposition = itemManager.getItemComposition(item.getId());
				int itemId;
				if (itemComposition.getPlaceholderTemplateId() != -1)
				{
					// if the item is a placeholder then get the item id for the normal item
					itemId = itemComposition.getPlaceholderId();
				}
				else
				{
					itemId = item.getId();
				}

				String itemName = itemComposition.getName();

				String initialValue = getTags(itemId);

				chatboxInputManager.openInputWindow(itemName + " tags:", initialValue, (newTags) ->
				{
					if (newTags == null)
					{
						return;
					}
					setTags(itemId, newTags);
					Widget bankContainerWidget = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
					if (bankContainerWidget == null)
					{
						return;
					}
					Widget[] bankItemWidgets = bankContainerWidget.getDynamicChildren();
					if (bankItemWidgets == null || inventoryIndex >= bankItemWidgets.length)
					{
						return;
					}
					Widget bankItemWidget = bankItemWidgets[inventoryIndex];
					String[] actions = bankItemWidget.getActions();
					if (actions == null || EDIT_TAGS_MENU_INDEX - 1 >= actions.length
						|| itemId != bankItemWidget.getItemId())
					{
						return;
					}
					int tagCount = getTagCount(itemId);
					actions[EDIT_TAGS_MENU_INDEX - 1] = EDIT_TAGS_MENU_OPTION;
					if (tagCount > 0)
					{
						actions[EDIT_TAGS_MENU_INDEX - 1] += " (" + tagCount + ")";
					}
				});
			}
			else
			{
				if (iconToSet != null)
				{
					if (event.getMenuOption().startsWith(CHANGE_ICON_ACTION))
					{
						event.consume();

						int inventoryIndex = event.getActionParam();
						ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
						if (bankContainer == null)
						{
							return;
						}
						Item[] items = bankContainer.getItems();
						if (inventoryIndex < 0 || inventoryIndex >= items.length)
						{
							return;
						}
						Item item = bankContainer.getItems()[inventoryIndex];
						if (item == null)
						{
							return;
						}
						ItemComposition itemComposition = itemManager.getItemComposition(item.getId());
						int itemId;
						if (itemComposition.getPlaceholderTemplateId() != -1)
						{
							// if the item is a placeholder then get the item id for the normal item
							itemId = itemComposition.getPlaceholderId();
						}
						else
						{
							itemId = item.getId();
						}

						iconToSet.setItemId(itemId);
						iconToSet.getIcon().setItemId(itemId);

						configManager.setConfiguration(CONFIG_GROUP, ICON_SEARCH + iconToSet.getTag(), itemId + "");
					}

					iconToSet = null;
				}

				switch (event.getMenuOption())
				{
					case SCROLL_UP:
						event.consume();
						scrollDragging(-1, false);
						break;
					case SCROLL_DOWN:
						event.consume();
						scrollDragging(1, false);
						break;
					case CHANGE_ICON:
						event.consume();
						iconToSet = tabManager.find(Text.removeTags(event.getMenuTarget()));
						break;
					case OPEN_TAG:
						event.consume();
						Widget[] children = parent.getDynamicChildren();
						Widget clicked = children[event.getActionParam()];

						openTag(TAG_SEARCH + Text.removeTags(clicked.getName()));
						break;
					case NEW_TAB:
						event.consume();
						chatboxInputManager.openInputWindow("Tag Name", "", (tagName) ->
						{
							if (!Strings.isNullOrEmpty(tagName))
							{
								addTab(tagName);
								tabManager.save();
								updateTabs(0);
							}
						});
						break;
					case REMOVE_TAB:
						event.consume();
						chatboxInputManager.openInputWindow("Are you sure you want to delete tab " + Text.removeTags(event.getMenuTarget()) + "?<br>(y)es or (n)o:", "", (response) ->
						{
							if (response.equalsIgnoreCase("y") || response.equalsIgnoreCase("yes"))
							{
								deleteTab(Text.removeTags(event.getMenuTarget()));
							}
						});
						break;
				}
			}
		}
	}

	public void scrollDragging(int direction, boolean fireEvent)
	{
		if (!fireEvent || !scrollWait)
		{
			updateTabs(direction);
			client.playSoundEffect(SoundEffectID.UI_BOOP);

			if (fireEvent)
			{
				scrollWait = true;
				setTimeout(() -> scrollWait = false, SCROLL_TICK);
			}
		}
	}

	private void setTimeout(Runnable runnable, int delay)
	{
		new Thread(() ->
		{
			try
			{
				Thread.sleep(delay);
				runnable.run();
			}
			catch (Exception e)
			{
				log.error(e.toString());
			}
		}).start();
	}

	private void deleteTab(String tag)
	{
		log.debug("Removing tag tab: {}", tag);

		tabManager.remove(tag);
		configManager.unsetConfiguration(CONFIG_GROUP, ICON_SEARCH + tag);
		tabManager.save();

		updateTabs(0);
	}

	private int getWidgetId(WidgetInfo widgetInfo)
	{
		return client.getWidget(widgetInfo).getId();
	}

	private void openTag(String tag)
	{
		Widget widget = client.getWidget(WidgetInfo.CHATBOX_SEARCH);
		TagTab tagTab = tabManager.find(tag.substring(4));

		String searched = client.getVar(VarClientStr.SEARCH_TEXT);

		if (widget != null && widget.isHidden() && !Strings.isNullOrEmpty(searched))
		{
			// Re-triggering search requires this to be an empty string if search mode is off
			client.setVar(VarClientStr.SEARCH_TEXT, "");
		}

		if (widget != null && widget.isHidden())
		{
			client.runScript(ScriptID.OPEN_BANK_SEARCH, 1,
				getWidgetId(WidgetInfo.BANK_CONTAINER),
				getWidgetId(WidgetInfo.BANK_INNER_CONTAINER),
				getWidgetId(WidgetInfo.BANK_SETTINGS),
				getWidgetId(WidgetInfo.BANK_ITEM_CONTAINER),
				getWidgetId(WidgetInfo.BANK_SCROLLBAR),
				getWidgetId(WidgetInfo.BANK_BOTTOM_BAR),
				getWidgetId(WidgetInfo.BANK_TITLE_BAR),
				getWidgetId(WidgetInfo.BANK_ITEM_COUNT),
				getWidgetId(WidgetInfo.BANK_SEARCH_BUTTON_BACKGROUND),
				getWidgetId(WidgetInfo.BANK_TAB_BAR),
				getWidgetId(WidgetInfo.BANK_INCINERATOR),
				getWidgetId(WidgetInfo.BANK_INCINERATOR_CONFIRM),
				getWidgetId(WidgetInfo.BANK_SOMETHING));
			widget = client.getWidget(WidgetInfo.CHATBOX_SEARCH);
		}

		client.setVar(VarClientStr.SEARCH_TEXT, tag);
		widget.setText(tag);

		setActiveTab(tagTab);
	}

	private void updateBounds()
	{
		Widget itemContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
		if (itemContainer != null)
		{
			bounds.setSize(41, itemContainer.getHeight());
			bounds.setLocation(1, itemContainer.getRelativeY() - 1);

			Widget incinerator = client.getWidget(WidgetInfo.BANK_INCINERATOR);

			if (incinerator != null && !incinerator.isHidden())
			{
				// This is the required way to move incinerator, don't change it!
				incinerator.setOriginalHeight(39);
				incinerator.setOriginalWidth(48);
				incinerator.setRelativeY(itemContainer.getHeight());
				incinerator.revalidate();

				Widget child = incinerator.getDynamicChildren()[0];
				child.setHeight(39);
				child.setWidth(48);
				child.setType(WidgetType.GRAPHIC);
				child.setSpriteId(INCINERATOR);

				bounds.setSize(41, itemContainer.getHeight() - incinerator.getHeight());
			}

			if (upButton != null)
			{
				Point p = upButton.getCanvasLocation();
				canvasBounds.setBounds(p.getX(), p.getY() + BUTTON_HEIGHT, bounds.width, maxTabs * TAB_HEIGHT + maxTabs * MARGIN);
			}
		}
	}

	private void makeUpButton()
	{
		upButton = createGraphic("", UP_ARROW, -1, TAB_WIDTH, BUTTON_HEIGHT, bounds.x, 0, true);
		upButton.setAction(1, SCROLL_UP);
		int clickmask = upButton.getClickMask();
		clickmask |= DRAG;
		upButton.setClickMask(clickmask);
	}

	private void makeDownButton()
	{
		downButton = createGraphic("", DOWN_ARROW, -1, TAB_WIDTH, BUTTON_HEIGHT, bounds.x, 0, true);
		downButton.setAction(1, SCROLL_DOWN);
		int clickmask = downButton.getClickMask();
		clickmask |= DRAG;
		downButton.setClickMask(clickmask);
	}

	private void makeNewTabButton()
	{
		newTab = createGraphic("", NEW_TAG_TAB, -1, TAB_WIDTH, 39, bounds.x, 0, true);
		newTab.setAction(1, NEW_TAB);
	}

	private void updateArrows()
	{
		if (upButton != null && downButton != null)
		{
			boolean hidden = !(tabManager.size() > 0);

			upButton.setHidden(hidden);
			upButton.setOriginalY(bounds.y);
			upButton.revalidate();

			downButton.setHidden(hidden);
			downButton.setOriginalY(bounds.y + maxTabs * TAB_HEIGHT + MARGIN * maxTabs + BUTTON_HEIGHT + MARGIN);
			downButton.revalidate();
		}
	}

	private Widget createGraphic(String name, int spriteId, int itemId, int width, int height, int x, int y, boolean hasListener)
	{
		Widget widget = parent.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.setOriginalX(x);
		widget.setOriginalY(y);

		widget.setSpriteId(spriteId);

		if (itemId > -1)
		{
			widget.setItemId(itemId);
			widget.setItemQuantity(-1);
			widget.setBorderThickness(1);
		}

		if (hasListener)
		{
			widget.setOnOpListener(ScriptID.NULL);
			widget.setHasListener(true);
		}

		widget.setName(name);
		widget.revalidate();

		return widget;
	}

	private void addTab(String t)
	{
		TagTab tagTab = tabManager.loadTab(t);

		if (tagTab.getBackground() == null)
		{
			Widget btn = createGraphic("<col=FFA500>" + tagTab.getTag() + "</col>", TAB_BACKGROUND, -1, TAB_WIDTH, TAB_HEIGHT, bounds.x, 1, true);
			btn.setAction(1, OPEN_TAG);
			btn.setAction(2, CHANGE_ICON);
			btn.setAction(3, REMOVE_TAB);

			tagTab.setBackground(btn);
		}

		if (tagTab.getIcon() == null)
		{
			Widget icon = createGraphic("<col=FFA500>" + tagTab.getTag() + "</col>", -1, tagTab.getItemId(), 36, 32, bounds.x + 3, 1, false);
			int clickmask = icon.getClickMask();
			clickmask |= DRAG;
			clickmask |= DRAG_ON;
			icon.setClickMask(clickmask);

			tagTab.setIcon(icon);
		}

		tabManager.add(tagTab);
	}

	private void addTabs()
	{
		List<String> tags = getAllTags();
		for (String t : tags)
		{
			addTab(t);
		}
	}

	public void loadTabs()
	{
		if (!config.tabs())
		{
			return;
		}

		idx = config.position();

		log.debug("bank opened");

		isBankOpen = true;
		iconToSet = null;
		setActiveTab(null);
		tabManager.clear();

		updateBounds();

		makeNewTabButton();
		makeUpButton();
		makeDownButton();

		addTabs();
		updateTabs(0);
	}

	public void setActiveTab(TagTab tagTab)
	{
		if (activeTab != null)
		{
			Widget tab = activeTab.getBackground();
			tab.setSpriteId(TAB_BACKGROUND);
			tab.revalidate();

			activeTab = null;
		}

		if (tagTab != null)
		{
			Widget tab = tagTab.getBackground();
			tab.setSpriteId(TAB_BACKGROUND_ACTIVE);
			tab.revalidate();

			activeTab = tagTab;
		}
	}

	private void renderTabs()
	{
		int y = bounds.y + MARGIN + BUTTON_HEIGHT;

		if (maxTabs >= tabManager.size())
		{
			idx = 0;
		}
		else
		{
			y -= (idx * TAB_HEIGHT + idx * MARGIN);
		}

		for (TagTab tg : tabManager.getTagTabs())
		{
			updateWidget(tg.getBackground(), y);
			updateWidget(tg.getIcon(), y + 4);

			// Edge case where item icon is 1 pixel out of bounds (still happens sometimes)
			tg.getIcon().setHidden(tg.getBackground().isHidden());

			// Keep item widget shown while drag scrolling
			if (client.getDraggedWidget() == tg.getIcon())
			{
				tg.getIcon().setHidden(false);
			}

			y += TAB_HEIGHT + MARGIN;
		}

		updateArrows();
	}

	private void updateTabs(int num)
	{
		updateBounds();

		maxTabs = (bounds.height - BUTTON_HEIGHT * 2 - MARGIN * 2) / TAB_HEIGHT;

		// prevent running into the incinerator
		while (bounds.y + maxTabs * TAB_HEIGHT + MARGIN * maxTabs + BUTTON_HEIGHT * 2 + MARGIN > bounds.y + bounds.height)
		{
			--maxTabs;
		}

		if (idx + num >= tabManager.size())
		{
			idx = 0;
		}

		if (idx + num < 0)
		{
			idx = 0;
		}

		if ((tabManager.size() - (idx + num) >= maxTabs) && (idx + num > -1))
		{
			idx += num;
		}
		else if (maxTabs < tabManager.size() && tabManager.size() - (idx + num) < maxTabs)
		{
			// Edge case when only 1 tab displays instead of up to maxTabs when one is deleted at the end of the list
			idx += num;
			updateTabs(-1);
			return;
		}

		renderTabs();
	}

	private void updateWidget(Widget t, int y)
	{
		t.setOriginalY(y);
		t.setRelativeY(y);

		if (y < (bounds.y + BUTTON_HEIGHT + MARGIN) || y > (bounds.y + bounds.height - TAB_HEIGHT - MARGIN - BUTTON_HEIGHT))
		{
			t.setHidden(true);
		}
		else
		{
			t.setHidden(false);
		}
		t.revalidate();
	}

	// TODO: move tag stuff
	private void appendTag(int itemId, String tag)
	{
		String s = getTags(itemId);
		List<String> tags = Arrays.stream(s.split(",")).filter(v -> !Strings.isNullOrEmpty(v) && !v.equalsIgnoreCase(tag)).collect(Collectors.toList());
		tags.add(tag);
		setTags(itemId, String.join(",", tags));
	}

	private List<String> getAllTags()
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, TAG_TAGS_CONFIG);

		if (Strings.isNullOrEmpty(value))
		{
			return new ArrayList<>();
		}

		return Arrays.stream(value.split(",")).collect(Collectors.toList());
	}

	private int getTagCount(int itemId)
	{
		String tags = getTags(itemId);
		if (tags.length() > 0)
		{
			return tags.split(",").length;
		}
		return 0;
	}

	private String getTags(int itemId)
	{
		String config = configManager.getConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId);
		if (config == null)
		{
			return "";
		}
		return config;
	}

	private void setTags(int itemId, String tags)
	{
		if (tags == null || tags.isEmpty())
		{
			configManager.unsetConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId);
		}
		else
		{
			configManager.setConfiguration(CONFIG_GROUP, ITEM_KEY_PREFIX + itemId, tags);
		}
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
	{
		java.awt.Point p = event.getPoint();
		if (canvasBounds.contains(p.getX(), p.getY()))
		{
			scrollDragging(event.getWheelRotation(), false);
		}

		return event;
	}
}
