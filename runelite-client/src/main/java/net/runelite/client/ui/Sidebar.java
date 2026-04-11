/*
 * Copyright (c) 2026 Abex
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
package net.runelite.client.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.google.common.base.Splitter;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

@Slf4j
@Singleton
class Sidebar extends JComponent
{
	private static final String CONFIG_CLIENT_SIDEBAR_CLOSED = "clientSidebarClosed";
	private static final String CONFIG_SIDEBAR_ORDER = "sidebarOrder";

	private static final Comparator<TabConfigItem> TCI_COMPARATOR = Comparator.<TabConfigItem>comparingInt(tci -> tci.priority)
		.thenComparing(tci -> tci.id);

	private static final int TAB_HEIGHT = 26;
	private static final int TAB_WIDTH = 31;
	private static final int SELECTION_BAR_WIDTH = 3;

	private static final String POPOUT = ">";

	private final Client client;
	private final ClientToolbarPanel toolbarPanel;
	private final ConfigManager configManager;

	@Nullable
	private TabButton selectedTab;

	@Nullable
	private TabButton draggedTab;

	// List<TabConfigItem | POPOUT>
	private List<Object> configOrder = new ArrayList<>();
	private final TabButtonContainer sidebarButtons = new TabButtonContainer();
	private final TabButtonContainer popoutActive = new TabButtonContainer();
	private final TabButtonContainer popoutButtons = new TabButtonContainer();
	private final JPopupMenu popup = new JPopupMenu();
	private final MoreTabsButton moreTabsButton = new MoreTabsButton();

	private final Map<String, TabButton> registeredButtons = new HashMap<>();

	private final Deque<HistoryEntry> selectedTabHistory = new ArrayDeque<>();

	private Color backgroundColor;
	private Color popoutBackgroundColor;
	private Color underlineColor;
	private Color hoverColor;
	private Color contentAreaColor;

	private final List<KeyListener> keyListeners;

	private JButton toolbarSidebarToggleButton;
	private BufferedImage sidebarOpenIcon;
	private BufferedImage sidebarCloseIcon;

	private boolean popupJustCanceled = false;

	@RequiredArgsConstructor
	private static class HistoryEntry
	{
		private final boolean sidebarOpen;
		private final TabButton tabBtn;
	}

	@Inject
	Sidebar(
		Client client,
		RuneLiteConfig config,
		ClientToolbarPanel toolbarPanel,
		ConfigManager configManager,
		EventBus eventBus
	)
	{
		this.client = client;
		this.toolbarPanel = toolbarPanel;
		this.configManager = configManager;

		eventBus.register(this);
		ToolTipManager.sharedInstance().registerComponent(this);

		sidebarButtons.setTrailer(popoutActive);
		popoutActive.setTrailer(moreTabsButton);

		popup.add(popoutButtons);
		popup.setLayout(new GridLayout(1, 1));

		setOpaque(true);
		setLayout(new SidebarLayoutManager());
		add(sidebarButtons);

		popup.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e)
			{
				popupJustCanceled = true;
				SwingUtilities.invokeLater(() -> popupJustCanceled = false);
			}
		});

		addAncestorListener(new AncestorListener()
		{
			@Override
			public void ancestorAdded(AncestorEvent event)
			{
			}

			@Override
			public void ancestorRemoved(AncestorEvent event)
			{
			}

			@Override
			public void ancestorMoved(AncestorEvent event)
			{
				hidePopup();
			}
		});

		setMoreTabsForceShown(false);

		keyListeners = List.of(
			new HotkeyListener(config::sidebarToggleKey)
			{
				@Override
				public void hotkeyPressed()
				{
					toggleSidebar();
				}
			},
			new HotkeyListener(config::panelToggleKey)
			{
				@Override
				public void hotkeyPressed()
				{
					togglePluginPanel();
				}
			});

		updateUI();

		deserialize(configManager.getConfiguration(RuneLiteConfig.GROUP_NAME, CONFIG_SIDEBAR_ORDER));
	}

	@Override
	public void updateUI()
	{
		backgroundColor = UIManager.getColor("ToolBar.background");
		setBackground(backgroundColor);
		popoutBackgroundColor = UIManager.getColor("PopupMenu.background");
		underlineColor = UIManager.getColor("TabbedPane.underlineColor");
		hoverColor = UIManager.getColor("TabbedPane.hoverColor");
		contentAreaColor = UIManager.getColor("TabbedPane.contentAreaColor");
	}

	void init(boolean withTitleBar)
	{
		var window = SwingUtilities.getWindowAncestor(this);

		if (withTitleBar)
		{
			sidebarOpenIcon = ImageUtil.loadImageResource(ClientUI.class, "open.png");
			sidebarCloseIcon = ImageUtil.flipImage(sidebarOpenIcon, true, false);
			toolbarSidebarToggleButton = toolbarPanel.add(NavigationButton
				.builder()
				.priority(100)
				.icon(sidebarCloseIcon)
				.tooltip("Close sidebar")
				.onClick(this::toggleSidebar)
				.build(), false);
		}
		else
		{
			var trailer = new JPanel();
			trailer.add(popoutActive);
			trailer.add(toolbarPanel);
			trailer.setLayout(new DynamicGridLayout(0, 1));
			sidebarButtons.setTrailer(trailer);
		}

		// Close sidebar if the config closed state is set
		if (configManager.getConfiguration(RuneLiteConfig.GROUP_NAME, CONFIG_CLIENT_SIDEBAR_CLOSED, Boolean.class) == Boolean.TRUE)
		{
			toggleSidebar(false, true);
		}

		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this::dispatchWindowKeyEvent);
		window.addWindowFocusListener(new WindowFocusListener()
		{
			@Override
			public void windowGainedFocus(WindowEvent e)
			{
			}

			@Override
			public void windowLostFocus(WindowEvent e)
			{
				for (KeyListener keyListener : keyListeners)
				{
					keyListener.focusLost();
				}
			}
		});
	}

	void add(NavigationButton navBtn)
	{
		var tb = new TabButton(navBtn);
		if (registeredButtons.putIfAbsent(sanitizeID(navBtn.getId()), tb) != null)
		{
			return;
		}

		var tci = findOrder(navBtn);
		tci.real = tb;
		this.add(tb.panel);
		reassignButtons();
	}

	void remove(NavigationButton navBtn)
	{
		var tb = registeredButtons.remove(sanitizeID(navBtn.getId()));
		if (tb == null)
		{
			return;
		}

		for (var v : configOrder)
		{
			if (v instanceof TabConfigItem)
			{
				var tci = (TabConfigItem) v;
				if (tci.real == tb)
				{
					tci.real = null;
				}
			}
		}

		boolean closingOpenTab = selectedTab == tb;
		selectedTabHistory.removeIf(it -> it.tabBtn == tb);
		this.remove(navBtn.getPanel().getWrappedPanel());
		if (closingOpenTab)
		{
			HistoryEntry entry = selectedTabHistory.isEmpty()
				? new HistoryEntry(true, null)
				: selectedTabHistory.removeLast();

			openPanel(entry.tabBtn, entry.sidebarOpen);
		}

		reassignButtons();
	}

	private boolean dispatchWindowKeyEvent(KeyEvent ev)
	{
		if (!SwingUtilities.getWindowAncestor(this).isFocused())
		{
			return false;
		}

		for (var listener : keyListeners)
		{
			switch (ev.getID())
			{
				case KeyEvent.KEY_TYPED:
					listener.keyTyped(ev);
					break;
				case KeyEvent.KEY_PRESSED:
					listener.keyPressed(ev);
					break;
				case KeyEvent.KEY_RELEASED:
					listener.keyReleased(ev);
					break;
			}

			if (ev.isConsumed())
			{
				return true;
			}
		}

		return false;
	}

	void toggleSidebar()
	{
		toggleSidebar(!isVisible(), true);
	}

	void toggleSidebar(boolean open, boolean pushHistory)
	{
		if (isVisible() == open)
		{
			return;
		}

		if (open)
		{
			configManager.unsetConfiguration(RuneLiteConfig.GROUP_NAME, CONFIG_CLIENT_SIDEBAR_CLOSED);
		}
		else
		{
			configManager.setConfiguration(RuneLiteConfig.GROUP_NAME, CONFIG_CLIENT_SIDEBAR_CLOSED, true);
		}

		this.setVisible(open);

		if (pushHistory)
		{
			pushHistory();
		}

		if (selectedTab != null)
		{
			if (open)
			{
				SwingUtil.activate(selectedTab.panel);
			}
			else
			{
				SwingUtil.deactivate(selectedTab.panel);
			}
		}

		revalidate();

		if (!open)
		{
			giveClientFocus();
		}

		if (toolbarSidebarToggleButton != null)
		{
			toolbarSidebarToggleButton.setIcon(new ImageIcon(open ? sidebarCloseIcon : sidebarOpenIcon));
			toolbarSidebarToggleButton.setToolTipText(open ? "Close sidebar" : "Open sidebar");
		}
	}

	void togglePluginPanel()
	{
		if (!isVisible() || selectedTab == null)
		{
			toggleSidebar(true, false);
			TabButton open = null;
			while (!selectedTabHistory.isEmpty())
			{
				HistoryEntry historyEntry = selectedTabHistory.removeLast();
				if (historyEntry.tabBtn != null)
				{
					open = historyEntry.tabBtn;
					break;
				}
			}

			if (open == null && !registeredButtons.isEmpty())
			{
				// I'm feeling lucky I guess
				open = registeredButtons.values().iterator().next();
			}

			openPanel(open, true);
		}
		else
		{
			openPanel(null);
		}
	}

	/**
	 * find the NavBtn in the order set, inserting it if missing
	 */
	private TabConfigItem findOrder(NavigationButton btn)
	{
		var search = new TabConfigItem(btn);

		int hi = 0;
		boolean hitPopout = false;
		for (Object v : configOrder)
		{
			if (v instanceof TabConfigItem)
			{
				var tci = (TabConfigItem) v;
				if (search.id.equals(tci.id))
				{
					return tci;
				}
				if (!hitPopout && TCI_COMPARATOR.compare(tci, search) > 0)
				{
					hi++;
				}
			}
			else if (POPOUT.equals(v))
			{
				hitPopout = true;
			}
		}

		int maxIdx = 0;
		{
			int lo = 0;
			int max = hi;
			int i = 0;
			for (; i < configOrder.size(); i++)
			{
				var v = configOrder.get(i);
				if (v instanceof TabConfigItem)
				{
					var tci = (TabConfigItem) v;
					int cmp = TCI_COMPARATOR.compare(tci, search);
					if (cmp < 0)
					{
						lo++;
					}
					if (cmp > 0)
					{
						hi--;
					}
					int sum = lo + hi;
					if (sum >= max)
					{
						max = sum;
						maxIdx = i + 1;
					}
				}
				else if (POPOUT.equals(v))
				{
					// never insert into the popout region
					break;
				}
			}
		}

		configOrder.add(maxIdx, search);

		return search;
	}

	private void reassignButtons()
	{
		sidebarButtons.buttons.clear();
		popoutButtons.buttons.clear();

		var into = sidebarButtons;
		for (var v : configOrder)
		{
			if (v instanceof TabConfigItem)
			{
				var tci = (TabConfigItem) v;
				if (tci.real != null)
				{
					tci.real.parent = into;
					tci.real.idx = into.buttons.size();
					into.buttons.add(tci.real);
				}
			}
			else if (POPOUT.equals(v))
			{
				into.buttonsChanged();
				into = popoutButtons;
			}
		}

		updatePopoutActive();
		into.buttonsChanged();
	}

	private void updatePopoutActive()
	{
		popoutActive.buttons.clear();
		if (popoutButtons.buttons.size() > 0)
		{
			moreTabsButton.setEnabled(true);
			boolean popoutIsActive = popoutButtons.buttons.contains(selectedTab);
			popoutActive.buttons.add(popoutIsActive ? selectedTab : null);
		}
		else
		{
			moreTabsButton.setEnabled(false);
		}
		popoutActive.revalidate();
	}

	private void setMoreTabsForceShown(boolean shown)
	{
		moreTabsButton.setDisabledIcon(shown
			? moreTabsButton.getIcon()
			: new ImageIcon());
	}

	void openPanel(NavigationButton button)
	{
		TabButton tb = null;
		if (button != null)
		{
			tb = registeredButtons.get(sanitizeID(button.getId()));
			if (tb == null)
			{
				return;
			}
		}

		openPanel(tb, true);
	}

	private void openPanel(TabButton tabBtn, boolean showSidebar)
	{
		toggleSidebar(showSidebar, false);

		if (tabBtn == selectedTab)
		{
			return;
		}

		if (selectedTab != null)
		{
			selectedTab.panel.setVisible(false);
			SwingUtil.deactivate(selectedTab.panel);
		}

		selectedTab = tabBtn;

		if (tabBtn != null)
		{
			selectedTab.panel.setVisible(true);
			SwingUtil.activate(selectedTab.panel);
		}
		else
		{
			giveClientFocus();
		}

		hidePopup();

		updatePopoutActive();
		this.revalidate();
		this.repaint();

		pushHistory();
	}

	private void giveClientFocus()
	{
		final Canvas c = client.getCanvas();
		if (c != null)
		{
			c.requestFocusInWindow();
		}
	}

	private void pushHistory()
	{
		selectedTabHistory.addLast(new HistoryEntry(this.isVisible(), selectedTab));

		// we keep multiple history entries so you can open a panel, close it, open another, *remove* it, then resume the first open panel
		if (selectedTabHistory.size() > 4)
		{
			HistoryEntry ent = selectedTabHistory.removeFirst();
			// Try to always keep a panel in the history
			if (ent.tabBtn != null && selectedTabHistory.stream().noneMatch(it -> it.tabBtn != null))
			{
				selectedTabHistory.removeFirst();
				selectedTabHistory.addFirst(ent);
			}
		}
	}

	void insert(TabButton button, MouseEvent source)
	{
		final int IDX_POPOUT_APPEND = -2;
		TabButton insertPoint = null;
		int newIdx = -1;

		var mtPt = SwingUtilities.convertPoint(source.getComponent(), source.getX(), source.getY(), moreTabsButton);
		if (moreTabsButton.contains(mtPt))
		{
			openPopup();
			newIdx = IDX_POPOUT_APPEND;
		}
		else
		{
			int min = Integer.MAX_VALUE;
			for (var tc : new TabButtonContainer[]{popoutButtons, sidebarButtons})
			{
				if (!tc.isVisible())
				{
					continue;
				}

				var pt = SwingUtilities.convertPoint(source.getComponent(), source.getX(), source.getY(), tc);
				boolean contained = tc.contains(pt);
				if (contained)
				{
					insertPoint = null;
				}

				for (int i = 0; i < tc.buttons.size(); i++)
				{
					int dx = tc.x[i] + (TAB_WIDTH / 2) - pt.x;
					int dy = tc.y[i] + (TAB_HEIGHT / 2) - pt.y;
					int dist = dx * dx + dy * dy;
					if (dist < min)
					{
						min = dist;
						insertPoint = tc.buttons.get(i);
					}
				}

				if (contained)
				{
					if (insertPoint == null)
					{
						newIdx = tc == sidebarButtons
							? 0
							: IDX_POPOUT_APPEND;
					}
					break;
				}
			}
		}
		if (insertPoint == button)
		{
			return;
		}
		if (insertPoint == null && newIdx == -1)
		{
			return;
		}

		int oldIdx = -1;
		for (int i = 0; i < configOrder.size(); i++)
		{
			var v = configOrder.get(i);
			if (v instanceof TabConfigItem)
			{
				var tci = (TabConfigItem) v;
				if (tci.real == button)
				{
					oldIdx = i;
				}
				else if (newIdx == -1 && tci.real == insertPoint)
				{
					newIdx = i;
				}
				else
				{
					continue;
				}
				if (oldIdx != -1 && newIdx != -1)
				{
					break;
				}
			}
		}

		if (newIdx == IDX_POPOUT_APPEND)
		{
			if (!configOrder.contains(POPOUT))
			{
				configOrder.add(POPOUT);
			}
			newIdx = configOrder.size() - 1;
		}

		if (newIdx != -1 && oldIdx != -1)
		{
			var old = configOrder.remove(oldIdx);
			configOrder.add(newIdx, old);
			serialize();
			reassignButtons();
		}
	}

	private void serialize()
	{
		var sb = new StringBuilder();
		for (var v : configOrder)
		{
			if (v instanceof TabConfigItem)
			{
				var tci = (TabConfigItem) v;
				sb.append(tci.id).append(' ').append(tci.priority).append(',');
			}
			else if (v instanceof String)
			{
				sb.append((String) v).append(',');
			}
		}
		if (sb.length() > 0)
		{
			sb.setLength(sb.length() - 1);
		}

		configManager.setConfiguration(RuneLiteConfig.GROUP_NAME, CONFIG_SIDEBAR_ORDER, sb.toString());
	}

	private void deserialize(String ser)
	{
		if (ser == null)
		{
			ser = "";
		}

		var newConfigOrder = new ArrayList<>();
		var keys = new HashSet<>();
		for (var part : Splitter.on(',').split(ser))
		{
			if (POPOUT.equals(part))
			{
				newConfigOrder.add(POPOUT);
			}
			else
			{
				var idx = part.indexOf(' ');
				if (idx != -1)
				{
					var id = part.substring(0, idx);
					if (keys.add(id))
					{
						try
						{
							int prio = Integer.parseInt(part.substring(idx + 1));

							newConfigOrder.add(new TabConfigItem(id, prio));
						}
						catch (NumberFormatException ignored)
						{
						}
					}
				}
			}
		}

		configOrder = newConfigOrder;

		for (var v : registeredButtons.values())
		{
			findOrder(v.navBtn).real = v;
		}

		reassignButtons();
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged ev)
	{
		if (RuneLiteConfig.GROUP_NAME.equals(ev.getGroup()) && CONFIG_SIDEBAR_ORDER.equals(ev.getKey()))
		{
			deserialize(ev.getNewValue());
		}
	}

	private class TabButtonContainer extends JComponent
	{
		final List<TabButton> buttons = new ArrayList<>();
		Component trailer;
		int buttonsPerColumn;
		int[] x = new int[10];
		int[] y = new int[10];

		@Nullable
		TabButton hoveredTab;

		void setTrailer(Component c)
		{
			if (this.trailer != null)
			{
				this.remove(this.trailer);
			}
			this.trailer = c;
			if (this.trailer != null)
			{
				this.add(this.trailer);
			}
			revalidate();
			repaint();
		}

		TabButtonContainer()
		{
			setOpaque(true);
			setLayout(new LayoutManager()
			{
				@Override
				public void addLayoutComponent(String name, Component comp)
				{
				}

				@Override
				public void removeLayoutComponent(Component comp)
				{
				}

				protected Dimension calculateSize(Container comp, boolean minimum)
				{
					int width = TAB_WIDTH;
					boolean drivenHeight = TabButtonContainer.this == sidebarButtons;

					Insets insets = comp.getInsets();
					int xInsets = insets.left + insets.right;
					int yInsets = insets.bottom + insets.top;

					int count = buttons.size();

					int trailerHeight = 0;
					if (trailer != null)
					{
						var trailerSize = minimum
							? trailer.getMinimumSize()
							: trailer.getPreferredSize();

						trailerHeight = trailerSize.height;
						count += (trailerSize.height + TAB_HEIGHT - 1) / TAB_HEIGHT;
					}

					int rows;
					if (drivenHeight)
					{
						int realHeight = comp.getHeight() - yInsets;
						rows = realHeight / TAB_HEIGHT;
					}
					else
					{
						rows = Math.min(count, 10);
					}
					if (rows > 0)
					{
						int columns = (count + rows - 1) / rows;
						rows = (count + columns - 1) / columns;
						width = Math.max(width, columns * TAB_WIDTH);
					}
					int height;
					if (drivenHeight)
					{
						// as the sidebar tab bar we don't really have a preferred or minimum height
						height = TAB_HEIGHT;
					}
					else
					{
						height = rows * TAB_HEIGHT;
					}
					height = Math.max(height, trailerHeight);

					return new Dimension(width + xInsets, height + yInsets);
				}

				@Override
				public Dimension minimumLayoutSize(Container parent)
				{
					return calculateSize(parent, true);
				}

				@Override
				public Dimension preferredLayoutSize(Container parent)
				{
					return calculateSize(parent, false);
				}

				@Override
				public void layoutContainer(Container comp)
				{
					int lastColumnItems = 0;
					if (trailer != null)
					{
						var trailerSize = trailer.getMinimumSize();
						lastColumnItems += (trailerSize.height + TAB_HEIGHT - 1) / TAB_HEIGHT;

						trailer.setBounds(getWidth() - TAB_WIDTH, getHeight() - trailerSize.height, TAB_WIDTH, trailerSize.height);
					}

					Insets insets = comp.getInsets();
					int yInsets = insets.bottom + insets.top;

					int numColumns = 1;
					int rows = (comp.getHeight() - yInsets) / TAB_HEIGHT;
					if (rows > 0)
					{
						numColumns = Math.max(1, (buttons.size() + lastColumnItems + rows - 1) / rows);
					}

					int size = buttons.size();
					buttonsPerColumn = Math.max(1, (size + numColumns - 1) / numColumns);
					while (size % buttonsPerColumn > rows - lastColumnItems && lastColumnItems < rows)
					{
						buttonsPerColumn++;
					}

					for (int i = 0; i < buttons.size(); i++)
					{
						var btn = buttons.get(i);
						if (btn != null)
						{
							x[i] = insets.left + (i / buttonsPerColumn) * TAB_WIDTH;
							y[i] = insets.top + (i % buttonsPerColumn) * TAB_HEIGHT;
						}
					}

					repaint();
				}
			});
			var ma = new MouseAdapter()
			{
				private boolean pendingClick;
				private Point dragStart = null;

				@Override
				public void mousePressed(MouseEvent e)
				{
					var btn = findButton(e.getX(), e.getY());

					if (btn == null)
					{
						dragStart = null;
						return;
					}

					if (e.getButton() == MouseEvent.BUTTON1)
					{
						if (btn == selectedTab || selectedTab == null || TabButtonContainer.this == popoutButtons)
						{
							pendingClick = true;
						}
						else
						{
							openPanel(btn, true);
						}

						dragStart = e.getPoint();
					}
					else if (e.getButton() == MouseEvent.BUTTON3)
					{
						if (btn.navBtn.getPopup() != null)
						{
							var menu = new JPopupMenu();
							btn.navBtn.getPopup().forEach((name, cb) ->
							{
								var menuItem = new JMenuItem(name);
								menuItem.addActionListener(ev -> cb.run());
								menu.add(menuItem);
							});
							menu.show(TabButtonContainer.this, e.getX(), e.getY());
						}
					}
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (pendingClick && e.getButton() == MouseEvent.BUTTON1)
					{
						var btn = findButton(e.getX(), e.getY());
						if (btn != null)
						{
							if (btn == selectedTab)
							{
								openPanel(null, true);
							}
							else
							{
								openPanel(btn, true);
							}
						}
						pendingClick = false;
					}
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					dragStart = null;
					draggedTab = null;
					setMoreTabsForceShown(false);
				}

				@Override
				public void mouseDragged(MouseEvent e)
				{
					if (draggedTab == null && dragStart != null)
					{
						// only set draggedTab when we are doing significant movement
						if (dragStart.distanceSq(e.getX(), e.getY()) > 10 * 10)
						{
							draggedTab = findButton(dragStart.x, dragStart.y);
							setMoreTabsForceShown(draggedTab != null);
						}
					}

					if (draggedTab != null)
					{
						insert(draggedTab, e);
					}
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					repaintTab(hoveredTab);
					hoveredTab = null;
					//pendingClick = false;
				}

				@Override
				public void mouseMoved(MouseEvent e)
				{
					repaintTab(hoveredTab);
					hoveredTab = findButton(e.getX(), e.getY());
					repaintTab(hoveredTab);
				}
			};
			addMouseListener(ma);
			addMouseMotionListener(ma);
		}

		void buttonsChanged()
		{
			if (x.length < buttons.size())
			{
				x = Arrays.copyOf(x, buttons.size() + 10);
				y = Arrays.copyOf(y, x.length);
			}
			revalidate();
		}

		private void repaintTab(TabButton tab)
		{
			if (tab != null)
			{
				tab.parent.repaint(tab.parent.x[tab.idx], tab.parent.y[tab.idx], TAB_WIDTH, TAB_HEIGHT);
				if (popoutActive.buttons.contains(tab))
				{
					popoutActive.repaint();
				}
			}
		}

		@Nullable
		private TabButton findButton(int x, int y)
		{
			for (int i = 0; i < buttons.size(); i++)
			{
				var tb = buttons.get(i);
				if (tb != null && x >= this.x[i] && x < this.x[i] + TAB_WIDTH && y >= this.y[i] && y < this.y[i] + TAB_HEIGHT)
				{
					return tb;
				}
			}

			return null;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			var backgroundColor = this == popoutButtons
				? popoutBackgroundColor
				: Sidebar.this.backgroundColor;

			g.setColor(backgroundColor);
			g.fillRect(0, 0, getWidth(), getHeight());
			if (this == sidebarButtons)
			{
				g.setColor(contentAreaColor);
				g.fillRect(0, 0, 1, getHeight());
			}

			for (int i = 0; i < buttons.size(); i++)
			{
				var btn = buttons.get(i);
				if (btn != null)
				{
					var background = backgroundColor;
					if (btn == hoveredTab)
					{
						background = hoverColor;
					}
					g.setColor(FlatUIUtils.deriveColor(background, backgroundColor));
					g.fillRect(x[i], y[i], TAB_WIDTH, TAB_HEIGHT);

					if (btn == selectedTab)
					{
						g.setColor(underlineColor);
						g.fillRect(x[i], y[i], SELECTION_BAR_WIDTH, TAB_HEIGHT);
					}

					btn.icon.paintIcon(this, g, x[i] + 7, y[i] + 5);//todo:prop
				}
			}
		}

		@Override
		public String getToolTipText(MouseEvent e)
		{
			var btn = findButton(e.getX(), e.getY());

			if (btn != null)
			{
				return btn.navBtn.getTooltip();
			}

			return super.getToolTipText(e);
		}
	}

	private class SidebarLayoutManager implements LayoutManager
	{
		@Override
		public void addLayoutComponent(String name, Component comp)
		{
		}

		@Override
		public void removeLayoutComponent(Component comp)
		{
		}

		protected Dimension calculateSize(Container comp, boolean minimum)
		{
			var sizeSz = minimum
				? sidebarButtons.getMinimumSize()
				: sidebarButtons.getPreferredSize();
			int w = sizeSz.width;
			int h = sizeSz.height;
			if (selectedTab != null)
			{
				var tabSz = minimum
					? selectedTab.panel.getMinimumSize()
					: selectedTab.panel.getPreferredSize();
				w += tabSz.width;
				h = Math.max(h, tabSz.height);
			}

			Insets insets = comp.getInsets();
			w += insets.left + insets.right;
			h += insets.bottom + insets.top;

			return new Dimension(w, h);
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			return calculateSize(parent, true);
		}

		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			return calculateSize(parent, false);
		}

		@Override
		public void layoutContainer(Container comp)
		{
			Insets insets = comp.getInsets();

			int x = insets.left;
			int y = insets.top;
			int w = comp.getWidth() - insets.left - insets.right;
			int h = comp.getHeight() - insets.top - insets.bottom;

			int sw = sidebarButtons.getPreferredSize().width;
			w -= sw;
			sidebarButtons.setBounds(w, y, sw, h);

			Component selectedPanel = null;
			if (selectedTab != null)
			{
				selectedPanel = selectedTab.panel;
			}

			for (int i = 0; i < comp.getComponentCount(); i++)
			{
				var in = comp.getComponent(i);
				if (in != sidebarButtons)
				{
					in.setBounds(x, y, w, h);
					in.setVisible(in == selectedPanel);
				}
			}
		}
	}

	private static String sanitizeID(String id)
	{
		return id.replace(",", "").replace(" ", "");
	}

	@RequiredArgsConstructor
	@ToString
	private static class TabConfigItem
	{
		final String id;
		final int priority;

		TabButton real;

		TabConfigItem(NavigationButton btn)
		{
			this(sanitizeID(btn.getId()), btn.getPriority());
		}
	}

	private static class TabButton
	{
		final NavigationButton navBtn;
		final Component panel;
		final Icon icon;

		// `parent.buttons.get(idx) == this` always, but `buttons.get(this.idx).idx == this` may
		// be false for the popoutActive TCI
		int idx;
		TabButtonContainer parent;

		public TabButton(NavigationButton navBtn)
		{
			this.navBtn = navBtn;
			this.panel = navBtn.getPanel().getWrappedPanel();

			final int TAB_SIZE = 16;
			icon = new ImageIcon(ImageUtil.resizeImage(navBtn.getIcon(), TAB_SIZE, TAB_SIZE));
		}
	}

	private class MoreTabsButton extends JButton
	{
		boolean preventActivation = false;

		MoreTabsButton()
		{
			super(new ImageIcon(ImageUtil.loadImageResource(Sidebar.class, "open_rs.png")));//TODO:
			setPreferredSize(new Dimension(TAB_WIDTH, TAB_HEIGHT));
			setMinimumSize(new Dimension(TAB_WIDTH, TAB_HEIGHT));
			putClientProperty(FlatClientProperties.STYLE_CLASS, "iconButton");
			addActionListener(e ->
			{
				if (!preventActivation)
				{
					openPopup();
				}
			});

			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseReleased(MouseEvent e)
				{
					preventActivation = false;
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					preventActivation = popupJustCanceled;
				}
			});
		}
	}

	void openPopup()
	{
		var sz = popup.getPreferredSize();
		var buttonPt = SwingUtilities.convertPoint(moreTabsButton, 0, 0, this);
		var sidebarPt = SwingUtilities.convertPoint(sidebarButtons, 0, 0, this);
		popup.show(this,
			sidebarPt.x - sz.width,
			buttonPt.y + moreTabsButton.getHeight() - sz.height);
		popoutButtons.requestFocusInWindow(FocusEvent.Cause.MOUSE_EVENT);
	}

	void hidePopup()
	{
		popup.setVisible(false);
	}
}

