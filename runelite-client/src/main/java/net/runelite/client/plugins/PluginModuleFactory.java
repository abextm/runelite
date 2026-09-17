/*
 * Copyright (c) 2026 Abex
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
package net.runelite.client.plugins;

import com.google.inject.AbstractModule;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.CoreBinding;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;

@Singleton
class PluginModuleFactory
{
	@Inject
	@CoreBinding
	private OkHttpClient client;

	@RequiredArgsConstructor
	class PluginModule extends AbstractModule
	{
		private final Plugin plugin;

		@Override
		protected void configure()
		{
			// Since the plugin itself is a module, it won't bind itself, so we'll bind it here
			binder().bind((Class<Plugin>) plugin.getClass()).toInstance(plugin);
			binder().install(plugin);
		}

		@Provides
		@Singleton
		public OkHttpClient okHttpClient()
		{
			String userAgent = RuneLite.USER_AGENT;
			var internalName = ExternalPluginManager.getInternalName(plugin.getClass());
			if (internalName != null)
			{
				userAgent += " (" + internalName;

				var dd = ExternalPluginManager.getDisplayData(plugin.getClass());
				if (dd != null)
				{
					userAgent += "/" + dd.getVersion();
				}
				userAgent += ")";
			}
			String userAgent0 = userAgent;

			return client.newBuilder()
				.addInterceptor(chain ->
				{
					Request request = chain.request();

					Request userAgentRequest = request
						.newBuilder()
						.header("User-Agent", userAgent0)
						.build();
					return chain.proceed(userAgentRequest);
				})
				.build();
		}
	}
}
