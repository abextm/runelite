/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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
package net.runelite.client.input;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.client.config.RuneLiteConfig;

@Singleton
public class MouseManager
{
	// Button numbers greater than BUTTON3 have no constant identifier
	private static final int MOUSE_BUTTON_4 = 4;

	private List<Subscriber<MouseListener>> mouseListeners = new ArrayList<>();
	private List<Subscriber<MouseWheelListener>> mouseWheelListeners = new ArrayList<>();

	private final RuneLiteConfig runeLiteConfig;

	@Value
	private static class Subscriber<T>
	{
		final float priority;
		final T listener;
	}

	@Inject
	private MouseManager(RuneLiteConfig runeLiteConfig)
	{
		this.runeLiteConfig = runeLiteConfig;
	}

	public void registerMouseListener(MouseListener mouseListener)
	{
		registerMouseListener(0.f, mouseListener);
	}

	public synchronized void registerMouseListener(float priority, MouseListener mouseListener)
	{
		if (mouseListeners.stream().noneMatch(it -> it.listener == mouseListener))
		{
			var ml = new ArrayList<>(mouseListeners);
			ml.add(new Subscriber<>(priority, mouseListener));
			ml.sort(Comparator.<Subscriber<?>>comparingDouble(Subscriber::getPriority).reversed()
				.thenComparing(s -> s.listener.getClass().getName()));
			mouseListeners = ml;
		}
	}

	public synchronized void unregisterMouseListener(MouseListener mouseListener)
	{
		var ml = new ArrayList<>(mouseListeners);
		if (ml.removeIf(it -> it.listener == mouseListener))
		{
			mouseListeners = ml;
		}
	}

	public void registerMouseWheelListener(MouseWheelListener mouseWheelListener)
	{
		registerMouseWheelListener(0.f, mouseWheelListener);
	}

	public synchronized void registerMouseWheelListener(float priority, MouseWheelListener mouseWheelListener)
	{
		if (mouseWheelListeners.stream().noneMatch(it -> it.listener == mouseWheelListener))
		{
			var ml = new ArrayList<>(mouseWheelListeners);
			ml.add(new Subscriber<>(priority, mouseWheelListener));
			ml.sort(Comparator.<Subscriber<?>>comparingDouble(Subscriber::getPriority).reversed()
				.thenComparing(s -> s.listener.getClass().getName()));
			mouseWheelListeners = ml;
		}
	}

	public void unregisterMouseWheelListener(MouseWheelListener mouseWheelListener)
	{
		var ml = new ArrayList<>(mouseWheelListeners);
		if (ml.removeIf(it -> it.listener == mouseWheelListener))
		{
			mouseWheelListeners = ml;
		}
	}

	public MouseEvent processMousePressed(MouseEvent mouseEvent)
	{
		if (mouseEvent.isConsumed())
		{
			return mouseEvent;
		}

		checkExtraMouseButtons(mouseEvent);
		for (var sub : mouseListeners)
		{
			var mouseListener = sub.listener;
			mouseEvent = mouseListener.mousePressed(mouseEvent);
			if (mouseEvent.isConsumed())
			{
				break;
			}
		}
		return mouseEvent;
	}

	public MouseEvent processMouseReleased(MouseEvent mouseEvent)
	{
		if (mouseEvent.isConsumed())
		{
			return mouseEvent;
		}

		checkExtraMouseButtons(mouseEvent);
		for (var sub : mouseListeners)
		{
			var mouseListener = sub.listener;
			mouseEvent = mouseListener.mouseReleased(mouseEvent);
			if (mouseEvent.isConsumed())
			{
				break;
			}
		}
		return mouseEvent;
	}

	public MouseEvent processMouseClicked(MouseEvent mouseEvent)
	{
		if (mouseEvent.isConsumed())
		{
			return mouseEvent;
		}

		checkExtraMouseButtons(mouseEvent);
		for (var sub : mouseListeners)
		{
			var mouseListener = sub.listener;
			mouseEvent = mouseListener.mouseClicked(mouseEvent);
			if (mouseEvent.isConsumed())
			{
				break;
			}
		}
		return mouseEvent;
	}

	private void checkExtraMouseButtons(MouseEvent mouseEvent)
	{
		// Prevent extra mouse buttons from being passed into the client,
		// as it treats them all as left click
		int button = mouseEvent.getButton();
		if (button >= MOUSE_BUTTON_4 && runeLiteConfig.blockExtraMouseButtons())
		{
			mouseEvent.consume();
		}
	}

	public MouseEvent processMouseEntered(MouseEvent mouseEvent)
	{
		if (mouseEvent.isConsumed())
		{
			return mouseEvent;
		}

		for (var sub : mouseListeners)
		{
			var mouseListener = sub.listener;
			mouseEvent = mouseListener.mouseEntered(mouseEvent);
			if (mouseEvent.isConsumed())
			{
				break;
			}
		}
		return mouseEvent;
	}

	public MouseEvent processMouseExited(MouseEvent mouseEvent)
	{
		if (mouseEvent.isConsumed())
		{
			return mouseEvent;
		}

		for (var sub : mouseListeners)
		{
			var mouseListener = sub.listener;
			mouseEvent = mouseListener.mouseExited(mouseEvent);
			if (mouseEvent.isConsumed())
			{
				break;
			}
		}
		return mouseEvent;
	}

	public MouseEvent processMouseDragged(MouseEvent mouseEvent)
	{
		if (mouseEvent.isConsumed())
		{
			return mouseEvent;
		}

		for (var sub : mouseListeners)
		{
			var mouseListener = sub.listener;
			mouseEvent = mouseListener.mouseDragged(mouseEvent);
			if (mouseEvent.isConsumed())
			{
				break;
			}
		}
		return mouseEvent;
	}

	public MouseEvent processMouseMoved(MouseEvent mouseEvent)
	{
		if (mouseEvent.isConsumed())
		{
			return mouseEvent;
		}

		for (var sub : mouseListeners)
		{
			var mouseListener = sub.listener;
			mouseEvent = mouseListener.mouseMoved(mouseEvent);
			if (mouseEvent.isConsumed())
			{
				break;
			}
		}
		return mouseEvent;
	}

	public MouseWheelEvent processMouseWheelMoved(MouseWheelEvent mouseWheelEvent)
	{
		if (mouseWheelEvent.isConsumed())
		{
			return mouseWheelEvent;
		}

		for (var sub : mouseWheelListeners)
		{
			var mouseWheelListener = sub.listener;
			mouseWheelEvent = mouseWheelListener.mouseWheelMoved(mouseWheelEvent);
			if (mouseWheelEvent.isConsumed())
			{
				break;
			}
		}
		return mouseWheelEvent;
	}
}
