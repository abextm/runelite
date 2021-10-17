/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
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
package net.runelite.cache;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.flat.FlatStorage;
import net.runelite.cache.region.Region;
import net.runelite.cache.region.RegionLoader;
import net.runelite.cache.util.XteaKeyManager;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

@Slf4j
public class MapImageDumperTest
{
	@Rule
	public TemporaryFolder folder = StoreLocation.getTemporaryFolder();

	@Test
	@Ignore
	public void dumpMap() throws IOException
	{
		File base = StoreLocation.LOCATION,
			outDir = new File("/tmp");

		try (Store store = new Store(new FlatStorage(new File("/home/abex/code/osrs-cache/"))))
		{
			store.load();

			XteaKeyManager keyManager = new XteaKeyManager();
			keyManager.loadKeys(new FileInputStream("/home/abex/xteas.json"));

			MapImageDumper dumper = new MapImageDumper(store, keyManager)
				.setBrightness(.75)
				.setTransparency(true)
				.load();

			for (int i = 0; i < Region.Z; ++i)
			{
				BufferedImage image = dumper.drawMap(i);

				File imageFile = new File(outDir, "img-" + i + ".png");

				try (OutputStream os = new FileOutputStream(imageFile))
				{
					write(os, image, 2);
				}
				log.info("Wrote image {}", imageFile);
			}
		}
	}

	@Test
	@Ignore
	public void dumpRegions() throws Exception
	{
		File base = StoreLocation.LOCATION,
			outDir = new File("/tmp/map");

		outDir.mkdirs();

		try (Store store = new Store(new FlatStorage(new File("/home/abex/code/osrs-cache/"))))
		{
			store.load();

			XteaKeyManager keyManager = new XteaKeyManager();
			keyManager.loadKeys(new FileInputStream("/home/abex/xteas.json"));

			RegionLoader regionLoader = new RegionLoader(store, keyManager);
			regionLoader.loadRegions();

			MapImageDumper dumper = new MapImageDumper(store, regionLoader);
			dumper.load();

			int z = 0;
			for (Region region : regionLoader.getRegions())
			{
				File imageFile = new File(outDir, "img-" + z + "-" + region.getRegionID() + ".png");
				BufferedImage image = dumper.drawRegion(region, z);
				ImageIO.write(image, "png", imageFile);
			}
		}
	}

	public static void setDeflateLevel(ImageWriteParam param, int deflateLevel)
	{
		try
		{
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(1f - (deflateLevel / 9f)); // works like jpeg quality, but is actually applied to zlib, so 0 is 9
		}
		catch (RuntimeException e)
		{
			log.info("cannot set compression", e);
		}
	}

	public static void write(OutputStream os, BufferedImage bimg, int deflateLevel) throws IOException
	{
		ImageWriter pngWriter = null;
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(os))
		{
			pngWriter = ImageIO.getImageWritersByFormatName("png").next();
			pngWriter.setOutput(ios);

			ImageWriteParam param = pngWriter.getDefaultWriteParam();
			setDeflateLevel(param, deflateLevel);
			IIOMetadata meta = pngWriter.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(bimg), param);

			IIOImage iimg = new IIOImage(bimg, null, meta);

			pngWriter.write(null, iimg, param);
		}
		finally
		{
			if (pngWriter != null)
			{
				pngWriter.dispose();
			}
		}
	}
}
