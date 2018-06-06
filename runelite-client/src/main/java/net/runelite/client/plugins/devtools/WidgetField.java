/*
 * Copyright (c) 2018 Abex
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

import com.google.common.collect.ImmutableList;
import java.awt.Rectangle;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import org.slf4j.helpers.MessageFormatter;

@Slf4j
public class WidgetField<T>
{
	public static final List<WidgetField> FIELDS;

	static
	{
		FIELDS = ImmutableList.of(
			new WidgetField<>("Id", Widget::getId, Integer.class),
			new WidgetField<>("Type", Widget::getType, Widget::setType, Integer.class),
			new WidgetField<>("ContentType", Widget::getContentType, Widget::setContentType, Integer.class),
			new WidgetField<>("ParentId", Widget::getParentId, Integer.class),
			new WidgetField<>("SelfHidden", Widget::isSelfHidden, Widget::setHidden, Boolean.class),
			new WidgetField<>("Hidden", Widget::isHidden, Boolean.class),
			new WidgetField<>("Text", Widget::getText, Widget::setText, String.class),
			new WidgetField<>("TextColor",
				w -> Integer.toString(w.getTextColor(), 16),
				(w, str) -> w.setTextColor(Integer.parseInt(str, 16)),
				String.class
			),
			new WidgetField<>("Name", w -> w.getName().trim(), Widget::setName, String.class),
			new WidgetField<>("ItemId", Widget::getItemId, Integer.class),
			new WidgetField<>("ItemQuantity", Widget::getItemQuantity, Integer.class),
			new WidgetField<>("ModelId", Widget::getModelId, Integer.class),
			new WidgetField<>("SpriteId", Widget::getSpriteId, Widget::setSpriteId, Integer.class),
			new WidgetField<>("Width", Widget::getWidth, Widget::setWidth, Integer.class),
			new WidgetField<>("Height", Widget::getHeight, Widget::setHeight, Integer.class),
			new WidgetField<>("RelativeX", Widget::getRelativeX, Widget::setRelativeX, Integer.class),
			new WidgetField<>("RelativeY", Widget::getRelativeY, Widget::setRelativeY, Integer.class),
			new WidgetField<>("CanvasLocation", Widget::getCanvasLocation, Point.class),
			new WidgetField<>("Bounds", Widget::getBounds, Rectangle.class),
			new WidgetField<>("ScrollX", Widget::getScrollX, Integer.class),
			new WidgetField<>("ScrollY", Widget::getScrollY, Integer.class),
			new WidgetField<>("OriginalX", Widget::getOriginalX, Integer.class),
			new WidgetField<>("OriginalY", Widget::getOriginalY, Integer.class),
			new WidgetField<>("PaddingX", Widget::getPaddingX, Integer.class),
			new WidgetField<>("PaddingY", Widget::getPaddingY, Integer.class)
		);
	}

	@Getter
	private final String name;

	private final Function<Widget, T> getter;

	private final BiConsumer<Widget, T> setter;

	@Getter
	private final Class<T> type;

	WidgetField(String name, Function<Widget, T> getter, Class<T> type)
	{
		this(name, getter, null, type);
	}

	WidgetField(String name, Function<Widget, T> getter, BiConsumer<Widget, T> setter, Class<T> type)
	{
		this.name = name;
		this.getter = getter;
		this.setter = setter;
		this.type = type;
	}

	Object getValue(Widget widget)
	{
		Object value = getter.apply(widget);
		// These types are handled by the JTable automatically
		if (value instanceof Boolean || value instanceof Number || value instanceof String)
		{
			return value;
		}
		return MessageFormatter.format("{}", value).getMessage();
	}

	void setValue(Widget widget, Object inValue)
	{
		Object value = null;
		if ("null".equals(inValue))
		{
			value = null;
		}
		if (type.isAssignableFrom(inValue.getClass()))
		{
			value = inValue;
		}
		else if (type == Boolean.class)
		{
			value = Boolean.valueOf((String) inValue);
		}
		else if (type == Integer.class)
		{
			value = Integer.valueOf((String) inValue);
		}
		else
		{
			log.warn("Type {} is not supported for editing", type);
		}
		setter.accept(widget, (T) value);
	}

	boolean isSettable()
	{
		return setter != null;
	}
}
