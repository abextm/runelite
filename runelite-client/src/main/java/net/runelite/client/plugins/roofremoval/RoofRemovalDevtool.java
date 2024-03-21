package net.runelite.client.plugins.roofremoval;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

@Slf4j
public class RoofRemovalDevtool
{

	@Inject
	private Client client;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Gson gson;

	private WorldPoint a;
	private int areaRegion;
	private RoofRemovalPlugin.FlaggedArea area;

	boolean started;

	public void start()
	{
		if (started) return;
		started = true;
		mouseManager.registerMouseListener(new MouseAdapter()
		{
			@Override
			public MouseEvent mousePressed(MouseEvent mouseEvent)
			{
				if (client.getSelectedSceneTile() != null && mouseEvent.isAltDown() && mouseEvent.getButton() == 1)
				{
					if (a == null)
					{
						a = client.getSelectedSceneTile().getWorldLocation();
					}
					else
					{
						if (area != null)
						{
							System.out.println("\n" +
								"  \"" + areaRegion + "\": [\n" +
								"    {\n" +
								"      \"rx1\": " + area.rx1 + ",\n" +
								"      \"ry1\": " + area.ry1 + ",\n" +
								"      \"rx2\": " + area.rx2 + ",\n" +
								"      \"ry2\": " + area.ry2 + ",\n" +
								"      \"z1\": " + area.z1 + ",\n" +
								"      \"z2\": " + area.z2 + "\n" +
								"    }\n" +
								"  ]");
						}

						a = null;
					}
				}

				return mouseEvent;
			}
		});


		overlayManager.add(new Overlay()
		{
			{
				setPosition(OverlayPosition.DYNAMIC);
				setLayer(OverlayLayer.ABOVE_SCENE);
			}

			@Override
			public Dimension render(Graphics2D graphics)
			{
				graphics.setStroke(new BasicStroke(2));

				if (a != null && client.getSelectedSceneTile() != null)
				{
					WorldPoint b = client.getSelectedSceneTile().getWorldLocation();
					areaRegion = a.getRegionID();
					area = new RoofRemovalPlugin.FlaggedArea();
					area.rx1 = Math.min(a.getRegionX(), b.getRegionX());
					area.ry1 = Math.min(a.getRegionY(), b.getRegionY());
					area.rx2 = Math.max(a.getRegionX(), b.getRegionX());
					area.ry2 = Math.max(a.getRegionY(), b.getRegionY());
					area.z1 = client.getPlane();
					area.z2 = client.getPlane();

					graphics.setColor(areaRegion != b.getRegionID()
						? Color.RED
						: Color.GREEN);

					var path = toPath(area, areaRegion, client.getPlane());
					if (path != null)
					{
						graphics.draw(path);
					}
				}
				else
				{
					area = null;
				}

				graphics.setColor(Color.CYAN);

				try (InputStream in = getClass().getResourceAsStream("overrides.jsonc"))
				{
					final InputStreamReader data = new InputStreamReader(in, StandardCharsets.UTF_8);
					//CHECKSTYLE:OFF
					final Type type = new TypeToken<Map<Integer, List<RoofRemovalPlugin.FlaggedArea>>>()
					{
					}.getType();
					//CHECKSTYLE:ON
					Map<Integer, List<RoofRemovalPlugin.FlaggedArea>> parsed = gson.fromJson(data, type);

					for(int r :client.getMapRegions())
					{
						var l = parsed.get(r);
						if (l != null)
						{
							for(var f : l)
							{
								for(int p = f.z1; p <= f.z2; p++)
								{
									var s = toPath(f, r, p);
									if (s != null)
									{
										graphics.draw(s);
									}
								}
							}
						}
					}
				}
				catch (IOException e){}

				return null;
			}

			private GeneralPath toPath(RoofRemovalPlugin.FlaggedArea area, int region, int plane)
			{
				GeneralPath p = null;

				int rx = area.rx1;
				int ry = area.ry1;
				p = push(p, region, rx, ry, plane);
				for (; ry <= area.ry2; ry++) p = push(p, region, rx, ry, plane);
				p = push(p, region, rx, ry, plane);
				for (; rx <= area.rx2; rx++) p = push(p, region, rx, ry, plane);
				p = push(p, region, rx, ry, plane);
				for (; ry > area.ry1; ry--) p = push(p, region, rx, ry, plane);
				p = push(p, region, rx, ry, plane);
				for (; rx > area.rx1; rx--) p = push(p, region, rx, ry, plane);
				p = push(p, region, rx, ry, plane);

				return p;
			}

			private GeneralPath push(GeneralPath p, int region, int x, int y, int plane)
			{
				WorldPoint wp = WorldPoint.fromRegion(region, x, y, plane);
				LocalPoint lp = LocalPoint.fromWorld(client, wp);
				if (lp == null) return p;
				Point cp = Perspective.localToCanvas(client, new LocalPoint(lp.getX() & ~127, lp.getY() & ~127), plane);
				if (cp == null) return p;
				if (p == null)
				{
					p = new GeneralPath();
					p.moveTo(cp.getX(), cp.getY());
				}
				else
				{
					p.lineTo(cp.getX(), cp.getY());
				}
				return p;
			}
		});

	}

}
