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
import java.awt.Font;
import java.util.Locale;
import java.util.Map;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.UIResource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class UIPatcher
{
	@Getter
	private final UIDefaults defaults;

	private String[] activeType;
	private Object lastValue;

	UIPatcher type(String... types)
	{
		this.activeType = types;
		return this;
	}

	UIPatcher anyType()
	{
		return type(null);
	}

	private void setValue(Object value)
	{
		if (value instanceof UIResource)
		{
			// no-op
		}
		else if (value instanceof Color)
		{
			value = new ColorUIResource((Color) value);
		}
		else if (value instanceof Font)
		{
			value = new FontUIResource((Font) value);
		}
		lastValue = value;
	}

	private void doSet(String subkey, boolean checked)
	{
		Object value = lastValue;
		if (activeType == null)
		{
			String dotSubkey = "." + subkey;
			String capSubkey = subkey.substring(0, 1).toUpperCase(Locale.ROOT) + subkey.substring(1);
			int updates = 0;
			for (Map.Entry<Object, Object> entry : defaults.entrySet())
			{
				if (!(entry.getKey() instanceof String))
				{
					continue;
				}

				String key = (String) entry.getKey();
				if (key.endsWith(dotSubkey) || key.endsWith(capSubkey))
				{
					entry.setValue(value);
					updates++;
				}
			}

			if (checked)
			{
				assert updates > 0 : "UI key *\"" + subkey + "\" does not exist";
			}
		}
		else
		{
			int updates = 0;
			for (String type : activeType)
			{
				String key;
				if (!type.endsWith("."))
				{
					key = type + subkey.substring(0, 1).toUpperCase(Locale.ROOT) + subkey.substring(1);
				}
				else
				{
					key = type + subkey;
				}
				if (checked && defaults.containsKey(key))
				{
					updates++;
				}
				defaults.put(key, value);
			}

			assert updates > 0 : "UI key \"" + subkey + "\" does not exist";
		}
	}

	UIPatcher set(String subkey, UIDefaults.LazyValue value)
	{
		return set(subkey, (Object) value);
	}

	UIPatcher set(String subkey, Object value)
	{
		setValue(value);
		return set(subkey);
	}

	UIPatcher set(String subkey)
	{
		doSet(subkey, true);
		return this;
	}

	UIPatcher setAnyway(String subkey, Object value)
	{
		setValue(value);
		return setAnyway(subkey);
	}

	UIPatcher setExact(String key)
	{
		assert defaults.containsKey(key) : "UI key \"" + key + "\" does not exist";
		defaults.put(key, lastValue);
		return this;
	}

	UIPatcher setExact(String key, Object value)
	{
		setValue(value);
		return setExact(key);
	}

	UIPatcher setAnyway(String subkey)
	{
		doSet(subkey, false);
		return this;
	}

	UIPatcher ui(Class<? extends ComponentUI> clazz)
	{
		for (String type : activeType)
		{
			if (type.endsWith("."))
			{
				String key = type.substring(0, type.length() - 1) + "UI";
				assert defaults.containsKey(key) : "UI key \"" + key + "\" does not exist";
				defaults.put(key, clazz.getName());
			}
		}
		return this;
	}
}
