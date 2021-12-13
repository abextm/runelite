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
package net.runelite.client.util;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class GLib
{
	@Getter(lazy = true)
	private static final GLib instance = load();

	@Getter
	private final Lib lib;

	@Getter
	private final IO io;

	interface Lib extends Library
	{
		Pointer g_main_loop_new(Pointer context, int running);

		void g_main_loop_unref(Pointer loop);

		void g_main_loop_run(Pointer loop);

		void g_main_loop_quit(Pointer loop);
	}

	interface IO extends Library
	{
		void g_app_info_launch_default_for_uri_async(String uri, Pointer context, Pointer cancellable, GAsyncReadyCallback callback, Pointer userData);

		int g_app_info_launch_default_for_uri_finish(Pointer result, Pointer error);
	}

	interface GAsyncReadyCallback extends Callback
	{
		void callback(Pointer sourceObject, Pointer result, Pointer userData);
	}

	private class AsyncLaunch implements GAsyncReadyCallback
	{
		boolean ok;

		@Override
		public void callback(Pointer sourceObject, Pointer result, Pointer loop)
		{
			ok = io.g_app_info_launch_default_for_uri_finish(result, null) != 0;
			lib.g_main_loop_quit(loop);
		}
	}

	public boolean launch(String uri)
	{
		// we do this manually to avoid JDK-8275494
		try
		{
			Pointer loop = lib.g_main_loop_new(null, 1);
			try
			{
				AsyncLaunch callback = new AsyncLaunch();
				io.g_app_info_launch_default_for_uri_async(uri, null, null, callback, loop);
				lib.g_main_loop_run(loop);
				return callback.ok;
			}
			finally
			{
				lib.g_main_loop_unref(loop);
			}
		}
		catch (UnsatisfiedLinkError e)
		{
			log.debug("unsupported glib", e);
			return false;
		}
	}

	@Nullable
	private static GLib load()
	{
		if (OSType.getOSType() != OSType.Linux)
		{
			return null;
		}

		try
		{
			return new GLib(
				Native.load("glib-2.0", Lib.class),
				Native.load("gio-2.0", IO.class));
		}
		catch (UnsatisfiedLinkError e)
		{
			return null;
		}
	}
}
