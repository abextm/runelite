/*
 * Copyright (c) 2018, raiyni <https://github.com/raiyni>
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

import java.util.Objects;
import lombok.Data;
import net.runelite.api.widgets.Widget;

@Data
public class TagTab
{
	private int itemId;
	private String tag;

	public TagTab(int itemId, String tag)
	{
		this.itemId = itemId;
		this.tag = tag;
	}

	private Widget background;
	private Widget icon;

	public String toString()
	{
		return "TagTab{tag=" + tag + ", itemId=" + itemId + "}";
	}

	public boolean equals(String s)
	{
		return tag.equalsIgnoreCase(s);
	}

	@Override
	public boolean equals(Object o)
	{
		if (o == null || o.getClass() != TagTab.class)
		{
			return false;
		}

		TagTab t = (TagTab) o;

		return equals(t.tag);
	}

	@Override
	public int hashCode()
	{
		return Objects.hashCode(this.tag) + 7 * 31;
	}
}
