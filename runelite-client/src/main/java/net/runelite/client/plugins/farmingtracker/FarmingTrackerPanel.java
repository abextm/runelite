/*
 * Copyright (c) 2018 Abex
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.farmingtracker;

import com.google.common.base.Strings;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.vars.Autoweed;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.AsyncBufferedImage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

@Slf4j
class FarmingTrackerPanel extends PluginPanel
{
	private static final BufferedImage BLANK_ICON = new BufferedImage(32, 36, BufferedImage.TYPE_INT_ARGB);

	private final Client client;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final FarmingTrackerConfig config;

	private boolean active;

	private List<FarmingPatchPanel> patchPanels = new ArrayList<>();

	/* This is the panel the tabs' respective panels will be displayed on. */
	private final JPanel display = new JPanel();
	private final MaterialTabGroup tabGroup = new MaterialTabGroup(display);

	FarmingTrackerPanel(
		Client client,
		ItemManager itemManager,
		ConfigManager configManager,
		FarmingTrackerConfig config,
		FarmingWorld farmingWorld
	)
	{
		super(false);

		this.client = client;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.config = config;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		display.setBorder(new EmptyBorder(10, 10, 8, 10));

		tabGroup.setBorder(new EmptyBorder(10, 10, 0, 10));
		tabGroup.setLayout(new GridLayout(0, 5, 7, 7));

		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		farmingWorld.getTabs().forEach((tab, patches) ->
		{
			JPanel container = new JPanel(new DynamicGridLayout(0, 1, 0, 7))
			{
				@Override
				public Dimension getPreferredSize()
				{
					return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
				}
			};
			container.setBackground(ColorScheme.DARK_GRAY_COLOR);

			String lastCategory = null;

			boolean first = true;
			for (Timeable patch : patches)
			{
				FarmingPatchPanel p = new FarmingPatchPanel(patch);

				/* Show labels to subdivide tabs into sections */
				if (!Strings.isNullOrEmpty(patch.getCategory()) && !patch.getCategory().equals(lastCategory))
				{
					JLabel groupLabel = new JLabel(patch.getCategory());

					if (first)
					{
						first = false;
						groupLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
					}
					else
					{
						groupLabel.setBorder(new EmptyBorder(15, 0, 0, 0));
					}

					groupLabel.setFont(FontManager.getRunescapeSmallFont());

					container.add(groupLabel);
					lastCategory = patch.getCategory();
				}

				patchPanels.add(p);
				container.add(p);

				/* This is a weird hack to remove the top border on the first tracker of every tab */
				if (first)
				{
					first = false;
					p.setBorder(null);
				}
			}

			JPanel wrapped = new JPanel(new BorderLayout());
			wrapped.add(container, BorderLayout.NORTH);
			wrapped.setBackground(ColorScheme.DARK_GRAY_COLOR);

			JScrollPane scroller = new JScrollPane(wrapped);
			scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			scroller.getVerticalScrollBar().setPreferredSize(new Dimension(16, 0));
			scroller.getVerticalScrollBar().setBorder(new EmptyBorder(0, 9, 0, 0));
			scroller.setBackground(ColorScheme.DARK_GRAY_COLOR);

			//Use a placeholder icon until the async image gets loaded
			MaterialTab materialTab = new MaterialTab(new ImageIcon(), tabGroup, scroller);
			materialTab.setBorder(new EmptyBorder(2, 2, 2, 2));
			materialTab.setName(tab.getName());

			AsyncBufferedImage icon = itemManager.getImage(tab.getItemID());
			Runnable resize = () ->
			{
				BufferedImage subIcon = icon.getSubimage(0, 0, 32, 32);
				materialTab.setIcon(new ImageIcon(subIcon.getScaledInstance(24, 24, Image.SCALE_SMOOTH)));
			};
			icon.onChanged(resize);
			resize.run();

			materialTab.setOnSelectEvent(() ->
			{
				config.setPatch(tab);
				return true;
			});

			tabGroup.addTab(materialTab);
			if (config.patch() == tab)
			{
				tabGroup.select(materialTab);
			}
		});
	}

	void update()
	{
		if (!active)
		{
			return;
		}

		long unixNow = Instant.now().getEpochSecond();
		log.debug("Updating panel with username {}", client.getUsername());
		boolean autoweed = false;
		{
			String group = FarmingTrackerConfig.KEY_NAME + "." + client.getUsername();
			autoweed = Integer.toString(Autoweed.ON.ordinal())
				.equals(configManager.getConfiguration(group, FarmingTrackerConfig.AUTOWEED));
		}
		for (FarmingPatchPanel panel : patchPanels)
		{
			Timeable timable = panel.getPatch();
			FarmingPatch patch = timable instanceof FarmingPatch ? (FarmingPatch) timable : null;
			Birdhouse birdhouse = timable instanceof Birdhouse ? (Birdhouse) timable : null;
			String regionKey = patch == null ? FarmingTrackerConfig.BIRDHOUSE : Integer.toString(patch.getRegion().getRegionID());
			String group = FarmingTrackerConfig.KEY_NAME + "." + client.getUsername() + "." + regionKey;
			String key = Integer.toString(patch == null ? birdhouse.getVarp().getId() : patch.getVarbit().getId());
			String storedValue = configManager.getConfiguration(group, key);
			long unixTime = 0;
			int value = 0;
			if (storedValue != null)
			{
				String[] parts = storedValue.split(":");
				if (parts.length == 2)
				{
					try
					{
						value = Integer.parseInt(parts[0]);
						unixTime = Long.parseLong(parts[1]);
					}
					catch (NumberFormatException e)
					{
					}
				}
			}

			if (birdhouse != null)
			{
				BirdhouseState state = birdhouse.getState(value);
				int itemID = birdhouse.getItemID(value);

				if (itemID >= 0)
				{
					itemManager.getImage(itemID).addTo(panel.getIcon());
				}
				else
				{
					panel.getIcon().setIcon(new ImageIcon(BLANK_ICON));
				}

				if (unixTime <= 0 || state == null)
				{
					panel.getEstimate().setText("Unknown");
					panel.getProgress().setVisible(false);
				}
				else if (state == BirdhouseState.EMPTY)
				{
					panel.getProgress().setForeground(ColorScheme.PROGRESS_ERROR_COLOR.darker());
					panel.getProgress().setMaximumValue(1);
					panel.getProgress().setValue(0);
					panel.getProgress().setVisible(true);
					panel.getProgress().update();
					panel.getEstimate().setText("Not enough seeds");
				}
				else if (state == BirdhouseState.UNSET)
				{
					panel.getProgress().setVisible(false);
					panel.getEstimate().setText("No birdhouse");
				}
				else
				{
					long doneEstimate = unixTime + 50 * 60;
					int progress = (int) (unixNow - unixTime) / 60;
					panel.setTime(config, doneEstimate);
					panel.getProgress().setVisible(true);
					panel.getProgress().setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR.darker());
					panel.getProgress().setMaximumValue(50);
					panel.getProgress().setValue(progress);
					panel.getProgress().update();
				}

				continue;
			}

			PatchState state = unixTime <= 0 ? null : patch.getImplementation().forVarbitValue(value);
			if (state == null)
			{
				panel.getIcon().setIcon(new ImageIcon(BLANK_ICON));
				panel.getIcon().setToolTipText("Unknown state");
				panel.getProgress().setVisible(false);
			}
			else
			{
				if (state.getProduce().getItemID() < 0)
				{
					panel.getIcon().setIcon(new ImageIcon(BLANK_ICON));
					panel.getIcon().setToolTipText("Unknown state");
					panel.getProgress().setVisible(false);
				}
				else
				{
					itemManager.getImage(state.getProduce().getItemID()).addTo(panel.getIcon());
					panel.getIcon().setToolTipText(state.getProduce().getName());
				}

				int stage = state.getStage();
				int stages = state.getCropState() == CropState.HARVESTABLE ?
					state.getProduce().getHarvestStages() :
					state.getProduce().getStages();
				int tickrate = 0;
				switch (state.getCropState())
				{
					case HARVESTABLE:
						tickrate = state.getProduce().getRegrowTickrate() * 60;
						break;
					case GROWING:
						tickrate = state.getProduce().getTickrate() * 60;
						break;
				}
				if (autoweed && state.getProduce() == Produce.WEEDS)
				{
					stage = 0;
					stages = 1;
					tickrate = 0;
				}
				if (tickrate > 0)
				{
					long tickNow = unixNow / tickrate;
					long tickTime = unixTime / tickrate;
					int delta = (int) (tickNow - tickTime);

					long doneEstimate = ((stages - 1 - stage) + tickTime) * tickrate;

					stage += delta;
					if (stage >= stages)
					{
						stage = stages - 1;
					}

					panel.setTime(config, doneEstimate);
				}
				else
				{
					switch (state.getCropState())
					{
						case HARVESTABLE:
							panel.getEstimate().setText("Done");
							break;
						case GROWING:
							if (stage == stages - 1)
							{
								panel.getEstimate().setText("Done");
							}
							else
							{
								panel.getEstimate().setText("Unknown");
							}
							break;
						case DISEASED:
							panel.getEstimate().setText("Diseased");
							break;
						case DEAD:
							panel.getEstimate().setText("Dead");
							break;
					}
				}

				/* Hide any fully grown weeds' progress bar. */
				if (state.getProduce() != Produce.WEEDS
					|| (state.getProduce() == Produce.WEEDS && !autoweed && stage < stages - 1))
				{
					panel.getProgress().setVisible(true);
					panel.getProgress().setForeground(state.getCropState().getColor().darker());
					panel.getProgress().setMaximumValue(stages - 1);
					panel.getProgress().setValue(stage);
					panel.getProgress().update();
				}
				else
				{
					panel.getProgress().setVisible(false);
				}
			}
		}
	}

	@Override
	public void onActivate()
	{
		active = true;
		update();
	}

	@Override
	public void onDeactivate()
	{
		active = false;
	}
}
