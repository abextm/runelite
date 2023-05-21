/*
 * Copyright (c) 2023 Abex
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
package net.runelite.client.ui.components;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ComplexList<M> extends JComponent
{
	public interface Renderer<M, V extends Component>
	{
		@Nonnull
		V render(M model);

		default void retire(V view)
		{
		}
	}

	public interface Sizer<M, V extends Component>
	{
		@Nonnull
		Dimension size(M model, Supplier<V> viewSupplier);
	}

	private static class Entry<M>
	{
		private int width;
		private int height;
		boolean hidden;

		int y;

		M model;
		Component view;
	}

	private List<Entry<M>> entries = new ArrayList<>();

	@Getter
	private Renderer<M, Component> renderer;

	@Getter
	private Sizer<M, Component> sizer;

	@Getter
	private int gap;

	private boolean forciblyValid;

	public ComplexList()
	{
		super.setLayout(new Layout());
		setRenderer(null);
		setSizer(null);
	}

	private Entry<M> newEntry(M model)
	{
		Entry<M> e = new Entry<>();
		e.model = model;
		applySize(e);
		return e;
	}

	private Component createView(Entry<M> e)
	{
		if (e.view != null)
		{
			return e.view;
		}

		return e.view = renderer.render(e.model);
	}

	private <V extends Component> void retireView(Entry<M> e)
	{
		if (e.view != null)
		{
			remove(e.view);
			renderer.retire(e.view);
			e.view = null;
		}
	}

	private void applySize(Entry<M> e)
	{
		boolean retire = e.view == null;
		Dimension size = sizer.size(e.model, () -> createView(e));
		if (retire)
		{
			retireView(e);
		}
		e.width = size.width;
		e.height = size.height;
	}

	@Override
	public void invalidate()
	{
		if (!forciblyValid)
		{
			super.invalidate();
		}
	}

	@Override
	public void setLayout(LayoutManager mgr)
	{
		if (mgr != getLayout())
		{
			throw new IllegalArgumentException();
		}
	}

	@Override
	protected void paintChildren(Graphics g)
	{
		Rectangle viewport = getVisibleRect();

		Insets insets = getInsets();
		int x = insets.left;
		int width = getWidth() - insets.left - insets.right;

		try
		{
			forciblyValid = true;
			for (Entry<M> e : entries)
			{
				if (!e.hidden && viewport.intersects(x, e.y, width, e.height))
				{
					Component view = createView(e);
					if (view.getParent() != this)
					{
						add(view);
					}

					view.setBounds(x, e.y, width, e.height);
					view.validate();
				}
				else
				{
					retireView(e);
				}
			}
		}
		finally
		{
			forciblyValid = false;
		}

		super.paintChildren(g);
	}

	public void setSizer(Sizer<M, ?> sizer)
	{
		if (sizer == null)
		{
			sizer = (_m, v) -> v.get().getPreferredSize();
		}

		this.sizer = (Sizer<M, Component>) sizer;
		entries.forEach(this::applySize);
		revalidate();
	}

	public void setRenderer(Renderer<M, ?> renderer)
	{
		if (renderer == null)
		{
			renderer = m -> new JLabel("" + m);
		}

		entries.forEach(this::retireView);
		this.renderer = (Renderer<M, Component>) renderer;
		revalidate();
	}

	public void setGap(int gap)
	{
		this.gap = gap;
		revalidate();
	}

	public void filter(Predicate<M> filter)
	{
		for (Entry<M> e : entries)
		{
			e.hidden = !filter.test(e.model);
			if (e.hidden)
			{
				retireView(e);
			}
		}

		revalidate();
	}

	public List<M> entries()
	{
		assert SwingUtilities.isEventDispatchThread();
		return new ModelList();
	}

	private class ModelList extends AbstractList<M>
	{
		@Override
		public M set(int index, M element)
		{
			Entry<M> old = entries.set(index, newEntry(element));
			retireView(old);
			revalidate();
			return old.model;
		}

		@Override
		public void add(int index, M element)
		{
			entries.add(index, newEntry(element));
			revalidate();
		}

		@Override
		public M remove(int index)
		{
			Entry<M> old = entries.remove(index);
			retireView(old);
			revalidate();
			return old.model;
		}

		@Override
		public void sort(Comparator<? super M> c)
		{
			entries.sort(Comparator.comparing(e -> e.model, c));
			revalidate();
		}

		@Override
		public M get(int index)
		{
			return entries.get(index).model;
		}

		@Override
		public int size()
		{
			return entries.size();
		}
	}

	private class Layout implements LayoutManager
	{
		@Override
		public void addLayoutComponent(String name, Component comp)
		{
		}

		@Override
		public void removeLayoutComponent(Component comp)
		{
		}

		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			int width = 0;
			int height = 0;
			for (Entry<M> e : entries)
			{
				if (!e.hidden)
				{
					width = Math.max(width, e.width);
					height += e.height + gap;
				}
			}
			if (height > 0)
			{
				height -= gap;
			}

			Insets insets = getInsets();
			height += insets.top + insets.bottom;
			width += insets.left + insets.right;

			return new Dimension(width, height);
		}

		@Override
		public Dimension minimumLayoutSize(Container parent)
		{
			Dimension pref = this.preferredLayoutSize(parent);
			return new Dimension(0, pref.height);
		}

		@Override
		public void layoutContainer(Container parent)
		{
			Insets insets = getInsets();
			int y = insets.top;

			for (Entry<M> e : entries)
			{
				if (!e.hidden)
				{
					e.y = y;
					y += e.height + gap;
				}
			}

			repaint();
		}
	}
}
