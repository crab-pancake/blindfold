/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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
package com.stevenwaterman.blindfold;

import com.google.inject.Provides;
import java.util.HashSet;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.hooks.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Blindfold",
	description = "Stops things rendering (requires GPU)",
	tags = {"blindfold", "blind", "black", "greenscreen"}
)
@Slf4j
public class BlindfoldPlugin extends Plugin implements DrawCallbacks
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BlindfoldPluginConfig config;

	@Inject
	private BlindfoldOverlay overlay;

	@Provides
	BlindfoldPluginConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BlindfoldPluginConfig.class);
	}

	private DrawCallbacks interceptedDrawCallbacks;
	private final HashSet<Plugin> interceptedPlugins = new HashSet<>();

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		clientThread.invokeLater(() -> {
			// We want to specifically ignore the return value here, so RuneLite doesn't
			// interpret this as something that needs to be retried until it returns true
			interceptDrawCalls();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientThread.invokeLater(() -> {
			if (client.getDrawCallbacks() == this)
				client.setDrawCallbacks(interceptedDrawCallbacks);
			interceptedDrawCallbacks = null;
			interceptedPlugins.clear();
		});
	}

	@Subscribe
	public void onPluginChanged(PluginChanged pluginChanged)
	{
		var plugin = pluginChanged.getPlugin();
		if (plugin == this)
			return;

		// After another plugin has started or stopped,
		// check whether draw calls need to be intercepted.
		// We assume that other plugins are well-behaved,
		// and that they only replace draw calls once each.
		clientThread.invokeLater(() -> {
			if (pluginChanged.isLoaded())
			{
				// This should never happen, but is probably worth ensuring
				if (interceptedPlugins.contains(plugin))
					return;

				if (interceptDrawCalls())
					interceptedPlugins.add(plugin);
			}
			else if (client.getDrawCallbacks() == null)
			{
				// Draw callbacks have been reset.
				// Regardless of whether the plugin was previously intercepted,
				// the chain is broken, and we should just reset everything
				interceptedDrawCallbacks = null;
				interceptedPlugins.clear();
			}
			else
			{
				// Assume the other plugin is well-behaved and will restore
				// the draw callbacks to our prior intercepted state
				interceptedPlugins.remove(plugin);
			}
		});
	}

	private boolean interceptDrawCalls()
	{
		assert client.isClientThread();

		// We only care about replacing draw callbacks while a GPU plugin is active
		if (!client.isGpu())
			return false;

		var drawCallbacks = client.getDrawCallbacks();
		if (drawCallbacks == null)
			return false;

		// Check if draw calls have already been intercepted, and are unchanged
		if (drawCallbacks == this)
			return false;

		interceptedDrawCallbacks = drawCallbacks;
		client.setDrawCallbacks(this);
		return true;
	}

	@Override
	public void drawScene(int cameraX, int cameraY, int cameraZ, int cameraPitch, int cameraYaw, int plane)
	{
		if (interceptedDrawCallbacks != null)
			interceptedDrawCallbacks.drawScene(cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, plane);
	}

	@Override
	public void postDrawScene() {
		if (interceptedDrawCallbacks != null)
			interceptedDrawCallbacks.postDrawScene();
	}

	@Override
	public void animate(Texture texture, int diff)
	{
		// Probably a no-op, but pass it on anyway
		if (interceptedDrawCallbacks != null)
			interceptedDrawCallbacks.animate(texture, diff);
	}

	@Override
	public void loadScene(Scene scene) {
		if (interceptedDrawCallbacks != null)
			interceptedDrawCallbacks.loadScene(scene);
	}

	@Override
	public void swapScene(Scene scene) {
		if (interceptedDrawCallbacks != null)
			interceptedDrawCallbacks.swapScene(scene);
	}

	@Override
	public void drawScenePaint(
		int orientation,
		int pitchSin, int pitchCos, int yawSin, int yawCos,
		int x, int y, int z,
		SceneTilePaint paint,
		int tileZ, int tileX, int tileY,
		int zoom, int centerX, int centerY)
	{
		if (interceptedDrawCallbacks != null && config.enableTerrain())
			interceptedDrawCallbacks.drawScenePaint(
				orientation,
				pitchSin, pitchCos, yawSin, yawCos,
				x, y, z, paint,
				tileZ, tileX, tileY,
				zoom, centerX, centerY
			);
	}

	@Override
	public void drawSceneModel(
		int orientation,
		int pitchSin, int pitchCos, int yawSin, int yawCos,
		int x, int y, int z,
		SceneTileModel model,
		int tileZ, int tileX, int tileY,
		int zoom, int centerX, int centerY)
	{
		if (interceptedDrawCallbacks != null && config.enableTerrain())
			interceptedDrawCallbacks.drawSceneModel(
				orientation,
				pitchSin, pitchCos, yawSin, yawCos,
				x, y, z, model,
				tileZ, tileX, tileY,
				zoom, centerX, centerY
			);
	}

	@Override
	public void draw(int overlayColor)
	{
		// Draw the frame normally. UI-hiding is handled in BlindfoldOverlay
		if (interceptedDrawCallbacks != null)
			interceptedDrawCallbacks.draw(overlayColor);
	}

	@Override
	public boolean drawFace(Model model, int face)
	{
		// Probably a no-op, but pass it on anyway
		if (interceptedDrawCallbacks != null)
			return interceptedDrawCallbacks.drawFace(model, face);
		return false;
	}

	@Override
	public void draw(
		Renderable renderable, int orientation,
		int pitchSin, int pitchCos, int yawSin, int yawCos,
		int x, int y, int z, long hash)
	{
		if (interceptedDrawCallbacks == null)
			return;

		boolean render =
			renderable == client.getLocalPlayer() ||
			config.enableScenery() && (
				renderable instanceof Model ||
				renderable instanceof ModelData ||
				renderable instanceof GraphicsObject ||
				renderable instanceof DynamicObject
			) ||
			config.enableEntities() && (
				renderable instanceof Projectile ||
				renderable instanceof TileItem ||
				renderable instanceof Actor
			);

		// skip rendering, but process clickbox if render == false

		if (render) {
			interceptedDrawCallbacks.draw(
				renderable, orientation,
				pitchSin, pitchCos, yawSin, yawCos,
				x, y, z, hash
			);
		} else {
			// Check the clickbox even if not drawn
			Model model = renderable instanceof Model ? (Model) renderable : renderable.getModel();
			if (model == null)
				return;

			// Apply height to renderable from the model
			if (model != renderable)
				renderable.setModelHeight(model.getModelHeight());

			if (!isVisible(model, pitchSin, pitchCos, yawSin, yawCos, x, y, z))
				return;

			client.checkClickbox(model, orientation, pitchSin, pitchCos, yawSin, yawCos, x, y, z, hash);
		}
	}

	/**
	 * Check is a model is visible and should be drawn.
	 */
	private boolean isVisible(Model model, int pitchSin, int pitchCos, int yawSin, int yawCos, int x, int y, int z)
	{
		model.calculateBoundsCylinder();

		final int xzMag = model.getXYZMag();
		final int bottomY = model.getBottomY();
		final int zoom = client.get3dZoom();
		final int modelHeight = model.getModelHeight();

		int Rasterizer3D_clipMidX2 = client.getRasterizer3D_clipMidX2(); // width / 2
		int Rasterizer3D_clipNegativeMidX = client.getRasterizer3D_clipNegativeMidX(); // -width / 2
		int Rasterizer3D_clipNegativeMidY = client.getRasterizer3D_clipNegativeMidY(); // -height / 2
		int Rasterizer3D_clipMidY2 = client.getRasterizer3D_clipMidY2(); // height / 2

		int var11 = yawCos * z - yawSin * x >> 16;
		int var12 = pitchSin * y + pitchCos * var11 >> 16;
		int var13 = pitchCos * xzMag >> 16;
		int depth = var12 + var13;
		if (depth > 50)
		{
			int rx = z * yawSin + yawCos * x >> 16;
			int var16 = (rx - xzMag) * zoom;
			if (var16 / depth < Rasterizer3D_clipMidX2)
			{
				int var17 = (rx + xzMag) * zoom;
				if (var17 / depth > Rasterizer3D_clipNegativeMidX)
				{
					int ry = pitchCos * y - var11 * pitchSin >> 16;
					int yheight = pitchSin * xzMag >> 16;
					int ybottom = (pitchCos * bottomY >> 16) + yheight; // use bottom height instead of y pos for height
					int var20 = (ry + ybottom) * zoom;
					if (var20 / depth > Rasterizer3D_clipNegativeMidY)
					{
						int ytop = (pitchCos * modelHeight >> 16) + yheight;
						int var22 = (ry - ytop) * zoom;
						return var22 / depth < Rasterizer3D_clipMidY2;
					}
				}
			}
		}
		return false;
	}
}
