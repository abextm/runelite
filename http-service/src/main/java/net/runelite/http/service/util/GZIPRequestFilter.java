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
package net.runelite.http.service.util;

import com.google.common.io.ByteStreams;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.apache.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class GZIPRequestFilter implements Filter
{
	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
	{
		HttpServletRequest req = (HttpServletRequest) request;

		if ("gzip".equals(req.getHeader(HttpHeaders.CONTENT_ENCODING)))
		{
			request = new BodyWrappingHttpServletRequest(req)
			{
				@Override
				InputStream filter(InputStream is) throws IOException
				{
					return ByteStreams.limit(new GZIPInputStream(is), 64 * 1024 * 1024);
				}
			};
		}

		chain.doFilter(request, response);
	}

	@Override
	public void destroy()
	{
	}

	abstract static class BodyWrappingHttpServletRequest extends HttpServletRequestWrapper
	{
		private final ServletInputStream sis;
		private BufferedReader bufferedReader;

		public BodyWrappingHttpServletRequest(HttpServletRequest request) throws IOException
		{
			super(request);
			ServletInputStream sis = request.getInputStream();
			if (sis != null)
			{
				InputStream is = filter(sis);
				ServletInputStream finalSis = sis;
				sis = new ServletInputStream()
				{
					@Override
					public boolean isFinished()
					{
						return finalSis.isFinished();
					}

					@Override
					public boolean isReady()
					{
						return finalSis.isReady();
					}

					@Override
					public void setReadListener(ReadListener listener)
					{
						finalSis.setReadListener(listener);
					}

					@Override
					public int read() throws IOException
					{
						return is.read();
					}

					@Override
					public int read(byte[] b, int off, int len) throws IOException
					{
						return is.read(b, off, len);
					}
				};
			}
			this.sis = sis;
		}

		abstract InputStream filter(InputStream is) throws IOException;

		@Override
		public ServletInputStream getInputStream() throws IOException
		{
			return sis;
		}

		@Override
		public BufferedReader getReader() throws IOException
		{
			if (this.bufferedReader == null)
			{
				bufferedReader = new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding()));
			}
			return bufferedReader;
		}
	}
}
