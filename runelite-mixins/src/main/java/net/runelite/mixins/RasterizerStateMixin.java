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

package net.runelite.mixins;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import net.runelite.api.RasterizerState;
import net.runelite.api.SpritePixels;
import net.runelite.api.mixins.Inject;
import net.runelite.api.mixins.Mixin;
import net.runelite.rs.api.RSClient;
import net.runelite.rs.api.RSSpritePixels;

@Mixin(RSClient.class)
public abstract class RasterizerStateMixin implements RSClient
{
	@Inject
	private static boolean rasterTargetAlpha;

	@Inject
	@Override
	public RasterizerState rasterizerSwitchToImage(BufferedImage image)
	{
		assert isClientThread();

		WritableRaster raster = image.getRaster();
		DataBuffer buffer = raster.getDataBuffer();
		assert buffer.getDataType() == DataBuffer.TYPE_INT;
		assert buffer.getNumBanks() == 1;
		DataBufferInt intBuffer = (DataBufferInt) buffer;

		// Same global state
		RasterizerState state = new RasterizerState();
		state.setZoom(get3dZoom());
		state.setPixels(getGraphicsPixels());
		state.setWidth(getGraphicsPixelsWidth());
		state.setHeight(getGraphicsPixelsHeight());
		int[] drawRegion = new int[4];
		rasterizerCopyDrawRegion(drawRegion);
		state.setGouraudLowRes(rasterizerGetGouraudLowRes());

		state.setDrawRegion(drawRegion);
		int[] pixels = intBuffer.getData();
		rasterizerSetRasterBuffer(pixels, raster.getWidth(), raster.getHeight());
		rasterizerResetRasterClipping();
		rasterizerSetMidpoint(raster.getWidth() / 2, raster.getHeight() / 2);

		set3dZoom(image.getHeight() * 16);
		rasterTargetAlpha = true;

		return state;
	}

	@Inject
	@Override
	public void rasterizerRestoreState(RasterizerState state, BufferedImage image)
	{
		assert isClientThread();

		int[] pixels = getGraphicsPixels();

		WritableRaster raster = image.getRaster();
		DataBuffer buffer = raster.getDataBuffer();
		assert buffer.getDataType() == DataBuffer.TYPE_INT;
		assert buffer.getNumBanks() == 1;
		DataBufferInt intBuffer = (DataBufferInt) buffer;
		if (intBuffer.getData() != pixels)
		{
			DataBufferInt newb = new DataBufferInt(pixels, pixels.length);
			WritableRaster newr = Raster.createWritableRaster(raster.getSampleModel(), newb, new Point(0, 0));
			image.setData(newr);
		}

		rasterizerSetRasterBuffer(state.getPixels(), state.getWidth(), state.getHeight());
		rasterizerResetDrawRegion(state.getDrawRegion());
		rasterizerResetRasterClipping();
		set3dZoom(state.getZoom());
		rasterizerSetGouraudLowRes(state.isGouraudLowRes());
		rasterTargetAlpha = false;
	}

	@Inject
	@Override
	public RSSpritePixels rasterizerGetSpritePixels()
	{
		return createSpritePixels(getGraphicsPixels(), getGraphicsPixelsWidth(), getGraphicsPixelsHeight());
	}

	@Inject
	@Override
	public void rasterizerSetSpritePixels(SpritePixels raspx)
	{
		rasterizerSetRasterBuffer(raspx.getPixels(), raspx.getWidth(), raspx.getHeight());
	}
}
