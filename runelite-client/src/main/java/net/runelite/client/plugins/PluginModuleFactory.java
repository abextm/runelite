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

import com.google.common.base.Strings;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.CoreBinding;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Singleton
class PluginModuleFactory
{
	@Inject
	@CoreBinding
	private OkHttpClient client;

	@Inject(optional = true)
	@Named("pluginRequestsPerMinute")
	@SuppressWarnings("PMD.ImmutableField")
	private int permitsPerMinute = 15;

	@Inject(optional = true)
	@Named("pluginMaxRequestPermits")
	@SuppressWarnings("PMD.ImmutableField")
	private int maxPermits = 100;

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
				.addInterceptor(new RateLimitInterceptor(maxPermits, permitsPerMinute))
				.build();
		}
	}

	static class RateLimitInterceptor implements Interceptor
	{
		private final int maxPermits;
		private final int permitsPerMinute;

		private final Map<String, Long> domainTimeout = new HashMap<>();
		private int permits;
		private long lastPermit;

		public RateLimitInterceptor(int maxPermits, int permitsPerMinute)
		{
			this.maxPermits = maxPermits;
			this.permitsPerMinute = permitsPerMinute;
			permits = maxPermits;
			lastPermit = now();
		}

		long now()
		{
			return System.nanoTime();
		}

		@Override
		public Response intercept(Chain chain) throws IOException
		{
			var request = chain.request();
			var now = now();

			var host = chain.request().url().host();
			boolean rateLimited;
			synchronized (this)
			{
				var mustAfter = domainTimeout.get(host);
				if (mustAfter != null)
				{
					if (mustAfter > now)
					{
						throw new IOException("Host " + host + " requested retry after");
					}
					else
					{
						domainTimeout.remove(host);
					}
				}

				long elapsed = now - lastPermit;
				permits = (int) Math.min(maxPermits, permits + (elapsed * permitsPerMinute) / TimeUnit.MINUTES.toNanos(1));
				lastPermit = now;

				rateLimited = permits <= 0;
				if (rateLimited)
				{
					request = request.newBuilder()
						.cacheControl(CacheControl.FORCE_CACHE)
						.build();
				}
				else
				{
					permits -= 1;
				}
			}

			var res = chain.proceed(request);

			if (rateLimited && res.cacheResponse() == null)
			{
				throw new IOException("Rate limit exceeded");
			}

			if (!rateLimited && res.networkResponse() == null)
			{
				synchronized (this)
				{
					permits = Math.min(maxPermits, permits + 1);
				}
			}

			if (res.networkResponse() != null)
			{
				var retryAfter = res.networkResponse().header("Retry-After");
				if (!Strings.isNullOrEmpty(retryAfter))
				{
					long longRetryAfter = 5;
					try
					{
						longRetryAfter = Long.parseLong(retryAfter);
					}
					catch (NumberFormatException ignored)
					{
					}

					synchronized (this)
					{
						domainTimeout.put(host, now() + longRetryAfter * TimeUnit.SECONDS.toNanos(1));
					}
				}
			}

			return res;
		}
	}
}
