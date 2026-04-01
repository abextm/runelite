package net.runelite.client.ui.laf;

import java.awt.Container;
import java.awt.LayoutManager;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.components.DNDTabbedPane;

@Slf4j
@RequiredArgsConstructor
public class RuneLiteDNDTabbedPaneUI extends RuneLiteTabbedPaneUI
{
	protected final DNDTabbedPane tabPane;

	private Handler handler;
	private String draggedTab;

	private Map<String, Integer> tabIndex = new HashMap<>();

	public static ComponentUI createUI(JComponent c)
	{
		return new RuneLiteDNDTabbedPaneUI((DNDTabbedPane) c);
	}

	@Override
	protected LayoutManager createLayoutManager()
	{
		return new RuneLiteDNDTabbedPaneLayout();
	}

	protected class RuneLiteDNDTabbedPaneLayout extends RuneLiteTabbedPaneLayout
	{
		@Override
		public void layoutContainer(Container parent)
		{
			super.layoutContainer(parent);

			// XXX this only works if all tabs are the same size
			// RuneLiteTabbedPaneUI has the same quirk

			// this also must be after FlatTabbedPaneLayout calculates
			// rectsTotalHeight() since it assumes that rects is in y-order

			var order = tabPane.getTabOrder();

			int lastIdx = -1;
			if (tabIndex.isEmpty())
			{
				int count = tabPane.getTabCount();
				for (int i = 0; i < count; i++)
				{
					var id = (String) getTabClientProperty(i, DNDTabbedPane.TAB_ID);
					if (id != null)
					{
						tabIndex.put(id, i);

						int idx = order.indexOf(id);
						if (idx == -1)
						{
							idx = lastIdx + 1;
							order.add(idx, id);
						}
						lastIdx = idx;
					}
				}
			}

			var old = rects.clone();
			Arrays.fill(rects, null);

			var tab = 0;
			for (var id : order)
			{
				var index = tabIndex.get(id);
				if (index != null)
				{
					rects[index] = old[tab++];
				}
			}

			assert tab == rects.length;
		}
	}

	private Handler getHandler()
	{
		if (handler == null)
		{
			handler = new Handler();
		}
		return handler;
	}

	@Override
	protected MouseListener createMouseListener()
	{
		var h = getHandler();
		h.mouseDelegate = super.createMouseListener();
		return h;
	}

	@Override
	protected PropertyChangeListener createPropertyChangeListener()
	{
		var h = getHandler();
		h.propertyChangeDelegate = super.createPropertyChangeListener();
		return h;
	}

	@Override
	protected void installListeners()
	{
		super.installListeners();

		tabPane.addMouseMotionListener(getHandler());
	}

	@Override
	protected void uninstallListeners()
	{
		super.uninstallListeners();

		tabPane.removeMouseMotionListener(getHandler());
	}

	private class Handler implements MouseListener, MouseMotionListener, PropertyChangeListener, ContainerListener
	{
		private MouseListener mouseDelegate;
		private PropertyChangeListener propertyChangeDelegate;

		@Override
		public void mouseClicked(MouseEvent e)
		{
			mouseDelegate.mouseClicked(e);
		}

		@Override
		public void mousePressed(MouseEvent e)
		{
			var pred = tabPane.getDragPredicate();
			if (pred.test(e))
			{
				int idx = tabPane.indexAtLocation(e.getX(), e.getY());
				draggedTab = (String) getTabClientProperty(idx, DNDTabbedPane.TAB_ID);

				return;
			}

			mouseDelegate.mousePressed(e);
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			draggedTab = null;
			mouseDelegate.mouseClicked(e);
		}

		@Override
		public void mouseEntered(MouseEvent e)
		{
			mouseDelegate.mouseEntered(e);
		}

		@Override
		public void mouseExited(MouseEvent e)
		{
			mouseDelegate.mouseExited(e);
		}

		@Override
		public void mouseDragged(MouseEvent e)
		{
			if (draggedTab != null)
			{
				int overTabIdx = tabPane.indexAtLocation(e.getX(), e.getY());
				var overTab = (String) getTabClientProperty(overTabIdx, DNDTabbedPane.TAB_ID);
				if (overTab != null && !overTab.equals(draggedTab))
				{
					var order = tabPane.getTabOrder();
					int overOrderIdx = order.indexOf(overTab);
					if (overOrderIdx != -1)
					{
						order.remove(draggedTab);
						order.add(overOrderIdx, draggedTab);
						tabPane.revalidate();
						tabPane.repaint();
					}
				}
			}
		}

		@Override
		public void mouseMoved(MouseEvent e)
		{
		}

		@Override
		public void propertyChange(PropertyChangeEvent evt)
		{
			propertyChangeDelegate.propertyChange(evt);
		}

		@Override
		public void componentRemoved(ContainerEvent e)
		{
			// its hard to tell which indices got shifted, so just clear the whole thing
			tabIndex.clear();
		}

		@Override
		public void componentAdded(ContainerEvent e)
		{
			tabIndex.clear();
		}
	}
}
