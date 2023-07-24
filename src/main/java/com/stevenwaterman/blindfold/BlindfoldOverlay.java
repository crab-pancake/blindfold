package com.stevenwaterman.blindfold;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public class BlindfoldOverlay extends Overlay
{
	private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

	@Inject
	private Client client;

	@Inject
	private BlindfoldPluginConfig config;

	public BlindfoldOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(OverlayPriority.HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D g) {
		if (client.isGpu() && !config.enableUi())
		{
			var bounds = g.getClipBounds();
			g.setBackground(TRANSPARENT);
			g.clearRect(0, 0, bounds.width, bounds.height);
			// Since the background may be system dependent, also fill with transparency
			// https://docs.oracle.com/javase/8/docs/api/java/awt/Graphics.html#clearRect-int-int-int-int-
			g.setColor(TRANSPARENT);
			g.fillRect(0, 0, bounds.width, bounds.height);
		}

		return null;
	}
}
