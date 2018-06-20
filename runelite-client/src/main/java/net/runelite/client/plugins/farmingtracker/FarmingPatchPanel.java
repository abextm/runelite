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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ThinProgressBar;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;

@Getter
class FarmingPatchPanel extends JPanel
{
	private final Timeable patch;
	private final JLabel icon = new JLabel();
	private final JLabel estimate = new JLabel();
	private final ThinProgressBar progress = new ThinProgressBar();

	FarmingPatchPanel(Timeable patch)
	{
		this.patch = patch;

		setLayout(new BorderLayout());

		JPanel topContainer = new JPanel();
		topContainer.setBorder(new EmptyBorder(7, 7, 6, 0));
		topContainer.setLayout(new BorderLayout());
		topContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		icon.setMinimumSize(new Dimension(36, 32));

		JPanel infoPanel = new JPanel();
		infoPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		infoPanel.setLayout(new GridLayout(2, 1));
		infoPanel.setBorder(new EmptyBorder(4, 4, 4, 0));

		final JLabel location = new JShadowedLabel(patch.toString());
		location.setFont(FontManager.getRunescapeSmallFont());
		location.setForeground(Color.WHITE);

		estimate.setFont(FontManager.getRunescapeSmallFont());
		estimate.setForeground(Color.GRAY);

		infoPanel.add(location);
		infoPanel.add(estimate);

		topContainer.add(icon, BorderLayout.WEST);
		topContainer.add(infoPanel, BorderLayout.CENTER);

		add(topContainer, BorderLayout.NORTH);
		add(progress, BorderLayout.SOUTH);
	}

	void setTime(FarmingTrackerConfig config, long doneEstimate)
	{
		long unixNow = Instant.now().getEpochSecond();

		if (doneEstimate < unixNow)
		{
			estimate.setText("Done");
		}
		else if (config.estimateRelative())
		{
			int remaining = (int) (59 + doneEstimate - unixNow) / 60;
			StringBuilder f = new StringBuilder();
			f.append("Done in ");
			int min = remaining % 60;
			int hours = (remaining / 60) % 24;
			int days = remaining / (60 * 24);
			if (days > 0)
			{
				f.append(days).append("d ");
			}
			if (hours > 0)
			{
				f.append(hours).append("h ");
			}
			if (min > 0)
			{
				f.append(min).append("m ");
			}
			estimate.setText(f.toString());
		}
		else
		{
			StringBuilder f = new StringBuilder();
			LocalDateTime ldtTime = LocalDateTime.ofEpochSecond(doneEstimate, 0, OffsetDateTime.now().getOffset());
			LocalDateTime ldtNow = LocalDateTime.now();
			f.append("Done ");
			if (ldtTime.getDayOfWeek() != ldtNow.getDayOfWeek())
			{
				f.append(ldtTime.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())).append(" ");
			}
			f.append(String.format("at %d:%02d", ldtTime.getHour(), ldtTime.getMinute()));
			estimate.setText(f.toString());
		}
	}
}
