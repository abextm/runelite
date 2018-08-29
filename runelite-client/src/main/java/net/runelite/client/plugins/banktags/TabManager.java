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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.client.config.ConfigManager;
import org.apache.commons.lang3.math.NumberUtils;

@Singleton
@Slf4j
class TabManager
{
	@Getter
	List<TagTab> tagTabs = new ArrayList<>();

	private ConfigManager configManager;

	@Inject
	TabManager(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	void add(TagTab tagTab)
	{
		if (!contains(tagTab))
		{
			tagTabs.add(tagTab);
		}
	}

	void move(String tagToMove, String tagDestination)
	{
		if (contains(tagToMove) && contains(tagDestination))
		{
			TagTab tag = find(tagToMove);
			remove(tag);

			int idx = indexOf(tagDestination);
			tagTabs.add(idx, tag);
		}
	}

	int indexOf(TagTab tagTab)
	{
		return tagTabs.indexOf(tagTab);
	}

	int indexOf(String tag)
	{
		return indexOf(new TagTab(0, tag));
	}

	boolean contains(TagTab tagTab)
	{
		return tagTabs.contains(tagTab);
	}

	boolean contains(String tag)
	{
		return tagTabs.stream().anyMatch(t -> t.equals(tag));
	}

	void remove(TagTab tagTab)
	{
		remove(tagTab.getTag());
	}

	void remove(String tag)
	{
		tagTabs.removeIf(t ->
		{
			if (t.equals(tag))
			{
				t.getBackground().setHidden(true);
				t.getIcon().setHidden(true);

				return true;
			}

			return false;
		});
	}

	void clear()
	{
		tagTabs.forEach(t ->
		{
			t.getBackground().setHidden(true);
			t.getIcon().setHidden(true);
		});

		tagTabs.clear();
	}

	TagTab find(String tag)
	{
		Optional<TagTab> first = tagTabs.stream().filter(t -> t.equals(tag)).findFirst();

		if (first.isPresent())
		{
			return first.get();
		}

		return null;
	}

	int size()
	{
		return tagTabs.size();
	}

	TagTab loadTab(String tag)
	{
		tag = tag.trim();
		TagTab tagTab = find(tag);

		if (tagTab == null)
		{
			String item = configManager.getConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.ICON_SEARCH + tag);
			int itemid = NumberUtils.toInt(item, ItemID.SPADE);

			tagTab = new TagTab(itemid, tag);
			;
		}

		return tagTab;
	}

	void save()
	{
		String tags = tagTabs.stream().map(t -> t.getTag()).collect(Collectors.joining(","));

		configManager.setConfiguration(BankTagsPlugin.CONFIG_GROUP, BankTagsPlugin.TAG_TAGS_CONFIG, tags);
	}
}
