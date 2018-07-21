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
package net.runelite.client.game;

import net.runelite.api.EventBus;
import net.runelite.api.Subscribe;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptCallbackEvent;

@Singleton
@Slf4j
public class ChatboxInputManager
{
	public static final int NO_LIMIT = Integer.MAX_VALUE;
	private final Client client;
	private final EventBus eventBus;

	private Consumer<String> done;
	private Consumer<String> changed;
	private int characterLimit = NO_LIMIT;

	@Getter
	private boolean open = false;

	@Inject
	public ChatboxInputManager(Client client, EventBus eventBus)
	{
		this.client = client;
		this.eventBus = eventBus;
		eventBus.register(this);
	}

	/**
	 * Opens a RuneScape-style chatbox input
	 *
	 * @param text   Text to show at the top of the window
	 * @param defaul Default text in the editable field
	 * @param done   Callback when the text box has been exited, called with "" on esc
	 */
	public void openInputWindow(String text, String defaul, Consumer<String> done)
	{
		openInputWindow(text, defaul, NO_LIMIT, done);
	}

	public void openInputWindow(String text, String defaul, int characterLimit, Consumer<String> done)
	{
		openInputWindow(text, defaul, characterLimit, null, done);
	}

	public void openInputWindow(String text, String defaul, int characterLimit, Consumer<String> changed, Consumer<String> done)
	{
		this.done = done;
		this.changed = changed;
		this.characterLimit = characterLimit;
		this.open = true;
		eventBus.once(ClientTick.class, e -> client.runScript(
			ScriptID.RUNELITE_CHATBOX_INPUT_INIT,
			text,
			defaul
		));
	}

	/**
	 * Closes the RuneScape-style chatbox input
	 */
	public void closeInputWindow()
	{
		if (!this.open)
		{
			return;
		}
		this.open = false;
		eventBus.once(ClientTick.class, e -> client.runScript(
			ScriptID.CLOSE_CHATBOX_INPUT,
			1,
			1
		));
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent ev)
	{
		// This replaces script 74 and most of 112
		if ("chatboxInputHandler".equals(ev.getEventName()))
		{
			int intStackSize = client.getIntStackSize();
			int stringStackSize = client.getStringStackSize();
			int typedKey = client.getIntStack()[--intStackSize];
			String str = client.getStringStack()[--stringStackSize];
			int retval = 0;

			switch (typedKey)
			{
				case 27: // Escape
					str = "";
					if (changed != null)
					{
						changed.accept(str);
					}
					// fallthrough
				case '\n':
					if (done != null)
					{
						done.accept(str);
					}
					this.open = false;
					retval = 1;
					break;
				case '\b':
					if (str.length() > 0)
					{
						str = str.substring(0, str.length() - 1);
					}
				default:
					// If we wanted to do numbers only, we could add a limit here
					if (typedKey >= 32 && (str.length() < characterLimit))
					{
						str += Character.toString((char) typedKey);
					}
			}

			if (changed != null)
			{
				changed.accept(str);
			}

			client.getStringStack()[stringStackSize++] = str;
			client.getIntStack()[intStackSize++] = retval;
			client.setIntStackSize(intStackSize);
			client.setStringStackSize(stringStackSize);
		}
	}
}
