/*
 * Copyright (c) 2021 Abex
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
package net.runelite.client.ui.laf;

import java.awt.Color;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.basic.BasicLookAndFeel;
import javax.swing.plaf.metal.MetalIconFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

@Slf4j
public class RuneLiteLAF extends BasicLookAndFeel
{
	@Override
	public String getName()
	{
		return "RuneLite";
	}

	@Override
	public String getID()
	{
		return "RuneLite";
	}

	@Override
	public String getDescription()
	{
		return "RuneLite";
	}

	@Override
	public boolean isNativeLookAndFeel()
	{
		return false;
	}

	@Override
	public boolean isSupportedLookAndFeel()
	{
		return true;
	}

	@SneakyThrows
	public void use()
	{
		UIManager.setLookAndFeel(this);
	}

	@Override
	protected void initSystemColorDefaults(UIDefaults table)
	{
		super.initSystemColorDefaults(table);
		new UIPatcher(table)
			// mdi stuff we don't use
			.setExact("desktop", ColorScheme.DARKER_GRAY_HOVER_COLOR)
			.setExact("activeCaption", ColorScheme.BRAND_ORANGE)
			.setExact("activeCaptionText", Color.BLACK)
			.setExact("activeCaptionBorder", ColorScheme.DARKER_GRAY_COLOR)
			.setExact("inactiveCaption", ColorScheme.BRAND_ORANGE_TRANSPARENT)
			.setExact("inactiveCaptionText", Color.BLACK)
			.setExact("inactiveCaptionBorder", ColorScheme.DARKER_GRAY_HOVER_COLOR)
			.setExact("windowBorder", ColorScheme.DARKER_GRAY_HOVER_COLOR)
			.setExact("windowText", ColorScheme.COLOR_FOREGROUND)

			.setExact("window", ColorScheme.DARK_GRAY_HOVER_COLOR) // control (inputs) background
			.setExact("text") // tree text background color
			.setExact("menu", ColorScheme.DARKER_GRAY_COLOR) // menu background
			.setExact("menuText", Color.WHITE) // menu text foreground
			.setExact("textText") // panel text foreground
			.setExact("controlText") // button and panel foreground
			.setExact("textHighlight", ColorScheme.BRAND_ORANGE_TRANSPARENT) // selected text background
			.setExact("textHighlightText", Color.WHITE) /* Text color when selected */
			.setExact("textInactiveText", new Color(0x808080)) /* Text color when disabled */
			.setExact("control", ColorScheme.DARK_GRAY_COLOR) // button and panel background

			.setExact("controlShadow", ColorScheme.DARKER_GRAY_HOVER_COLOR)
			.setExact("controlDkShadow")
			.setExact("controlHighlight")
			.setExact("controlLtHighlight")

			.setExact("scrollbar", ColorScheme.SCROLL_TRACK_COLOR) // scrollbar track

			.setExact("info", ColorScheme.DARKER_GRAY_HOVER_COLOR) // tooltip
			.setExact("infoText", ColorScheme.COLOR_FOREGROUND);
	}

	@Override
	public UIDefaults getDefaults()
	{
		UIDefaults defaults = super.getDefaults();
		HashMap<Object, Object> baseDefaults;
		if (log.isDebugEnabled() || true)
		{
			baseDefaults = new HashMap<>(defaults);
		}
		else
		{
			baseDefaults = null;
		}
		defaults = new UIPatcher(defaults)
			.anyType()
			.set("font", FontManager.getRunescapeFont())

			.type("RadioButton.")
			.ui(RuneLiteRadioButtonUI.class)
			.set("icon", MetalIconFactory.getRadioButtonIcon())

			.type("CheckBox.")
			.ui(RuneLiteCheckBoxUI.class)
			.set("icon", MetalIconFactory.getCheckBoxIcon()) // this is crap

			.type("ScrollBar.")
			.ui(RuneLiteScrollBarUI.class)
			.set("thumb", ColorScheme.MEDIUM_GRAY_COLOR)
			.set("track", ColorScheme.SCROLL_TRACK_COLOR)
			.set("width", 7)

			.type("ScrollPane.")
			.set("border", new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0))

			.getDefaults();

		defaults.put("ClassLoader", getClass().getClassLoader());

		if (baseDefaults != null)
		{
			log.info("UIDefaults:\n{}", defaults.entrySet().stream()
				.sorted(Comparator.comparing(e -> e.getKey().toString()))
				.map(e -> {
					String p = Objects.equals(baseDefaults.get(e.getKey()), e.getValue())
					 ? " " : "*";
					return p + " " + e.getKey() + " = " + e.getValue();
				})
				.collect(Collectors.joining("\n")));
		}

		return defaults;
	}

}
