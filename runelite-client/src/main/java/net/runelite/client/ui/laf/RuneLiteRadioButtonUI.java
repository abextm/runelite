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

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import javax.swing.text.View;

// we have our own checkbox stuff for compat with substance, which doesn't
// render the background outside of the box
public class RuneLiteRadioButtonUI extends BasicRadioButtonUI
{
	private static final RuneLiteRadioButtonUI INSTANCE = new RuneLiteRadioButtonUI();

	public static ComponentUI createUI(JComponent b)
	{
		return INSTANCE;
	}

	@Override
	protected void installDefaults(AbstractButton b)
	{
		super.installDefaults(b);

		// we do not draw the background fully
		LookAndFeel.installProperty(b, "opaque", false);
	}

	@Override
	public synchronized void paint(Graphics g, JComponent component)
	{
		AbstractButton btn = (AbstractButton) component;
		g.setFont(component.getFont());
		FontMetrics fm = g.getFontMetrics();

		Dimension size = new Dimension();
		Rectangle viewRect = new Rectangle();
		Rectangle iconRect = new Rectangle();
		Rectangle textRect = new Rectangle();

		Insets i = component.getInsets();
		size = btn.getSize(size);
		viewRect.x = i.left;
		viewRect.y = i.top;
		viewRect.width = size.width - (i.right + viewRect.x);
		viewRect.height = size.height - (i.bottom + viewRect.y);
		iconRect.x = iconRect.y = iconRect.width = iconRect.height = 0;
		textRect.x = textRect.y = textRect.width = textRect.height = 0;

		Icon icon = getIcon(btn);

		String text = SwingUtilities.layoutCompoundLabel(
			component, fm, btn.getText(), icon,
			btn.getVerticalAlignment(), btn.getHorizontalAlignment(),
			btn.getVerticalTextPosition(), btn.getHorizontalTextPosition(),
			viewRect, iconRect, textRect,
			btn.getText() == null ? 0 : btn.getIconTextGap());

		g.setColor(btn.getBackground());
		g.fillRect(iconRect.x, iconRect.y, iconRect.width, iconRect.height);
		icon.paintIcon(component, g, iconRect.x, iconRect.y);

		if (text != null)
		{
			View v = (View) component.getClientProperty(BasicHTML.propertyKey);
			if (v != null)
			{
				v.paint(g, textRect);
			}
			else
			{
				paintText(g, btn, textRect, text);
			}
			if (btn.hasFocus() && btn.isFocusPainted() && textRect.width > 0 && textRect.height > 0)
			{
				paintFocus(g, textRect, size);
			}
		}
	}

	private Icon getIcon(AbstractButton btn)
	{
		ButtonModel btnModel = btn.getModel();

		Icon icon = null;
		if (!btnModel.isEnabled())
		{
			icon = btnModel.isSelected()
				? btn.getDisabledSelectedIcon()
				: btn.getDisabledIcon();
		}
		else if (btnModel.isPressed() && btnModel.isArmed())
		{
			icon = btn.getPressedIcon();
			if (icon == null)
			{
				icon = btn.getSelectedIcon();
			}
		}
		else if (btnModel.isSelected())
		{
			if (btn.isRolloverEnabled() && btnModel.isRollover())
			{
				icon = btn.getRolloverSelectedIcon();
			}
			if (icon == null)
			{
				icon = btn.getSelectedIcon();
			}
		}
		else if (btn.isRolloverEnabled() && btnModel.isRollover())
		{
			icon = btn.getRolloverIcon();
		}

		if (icon == null)
		{
			icon = btn.getIcon();
		}

		if (icon == null)
		{
			icon = getDefaultIcon();
		}

		return icon;
	}
}
