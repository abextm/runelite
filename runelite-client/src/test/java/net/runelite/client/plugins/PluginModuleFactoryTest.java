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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PluginModuleFactoryTest
{
	@Rule
	public MockWebServer server = new MockWebServer();

	@Rule
	public TemporaryFolder cacheDir = new TemporaryFolder();

	@Test
	public void testRateLimit() throws IOException
	{
		var limit = new MockRateLimitInterceptor(5, 1);

		var client = OkHttpClient.Builder.create()
			.addInterceptor(limit)
			.build();

		for (int i = 0; i < 5; i++)
		{
			server.enqueue(new MockResponse());
			request(client);
		}

		requestFail(client);

		limit.now += TimeUnit.SECONDS.toNanos(1);

		requestFail(client);

		limit.now += TimeUnit.MINUTES.toNanos(1);

		server.enqueue(new MockResponse());
		request(client);
	}

	@Test
	public void testRateLimitCache() throws IOException
	{
		var limit = new MockRateLimitInterceptor(1, 1);
		var cache = new Cache(cacheDir.getRoot(), 50_000_000);
		cache.initialize();

		var client = OkHttpClient.Builder.create()
			.cache(cache)
			.addInterceptor(limit)
			.build();

		server.enqueue(new MockResponse()
			.addHeader("Cache-Control", "max-age=60, public")
			.setBody(""));

		for (int i = 0; i < 5; i++)
		{
			request(client);
		}

		cache.evictAll();

		requestFail(client);
	}

	@Test
	public void testRetryAfter() throws IOException
	{
		var limit = new MockRateLimitInterceptor(100, 1);

		var client = OkHttpClient.Builder.create()
			.addInterceptor(limit)
			.build();

		server.enqueue(new MockResponse());
		request(client);

		server.enqueue(new MockResponse()
			.addHeader("Retry-After", "30"));
		request(client);

		server.enqueue(new MockResponse());
		try
		{
			request(client);
			Assert.fail();
		}
		catch (IOException ignored)
		{
		}

		limit.now += TimeUnit.SECONDS.toNanos(30);

		request(client);
	}

	private void request(OkHttpClient client) throws IOException
	{
		try (var res = client.newCall(new Request.Builder()
			.url(server.url("/"))
			.build()).execute())
		{
			if (res.body() != null)
			{
				try (var b = res.body())
				{
					// consume response for cache
					b.bytes();
				}
			}
		}
	}

	private void requestFail(OkHttpClient client) throws IOException
	{
		try
		{
			request(client);
			Assert.fail();
		}
		catch (IOException e)
		{
			Assert.assertEquals("Rate limit exceeded", e.getMessage());
		}
	}

	private static class MockRateLimitInterceptor extends PluginModuleFactory.RateLimitInterceptor
	{
		long now = 0;

		public MockRateLimitInterceptor(int maxPermits, int permitsPerMinute)
		{
			super(maxPermits, permitsPerMinute);
		}

		@Override
		long now()
		{
			return now;
		}
	}
}