/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020-2023 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.api.internal;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.*;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.network.plugin.PluginChannelMessage;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiDebugRendering;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.ConfigBasedSpamLogger;
import com.seibel.distanthorizons.core.logging.SpamReducedLogger;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.renderer.TestRenderer;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.util.objects.Pair;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.world.DhClientServerWorld;
import com.seibel.distanthorizons.core.world.DhClientWorld;
import com.seibel.distanthorizons.core.world.IDhClientWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.util.math.Mat4f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This holds the methods that should be called
 * by the host mod loader (Fabric, Forge, etc.).
 * Specifically for the client.
 */
public class ClientApi
{
	private static final Logger LOGGER = LogManager.getLogger();
	
	public static boolean prefLoggerEnabled = false;
	
	public static final ClientApi INSTANCE = new ClientApi();
	public static TestRenderer testRenderer = new TestRenderer();
	
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	public static final long SPAM_LOGGER_FLUSH_NS = TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS);
	
	private boolean configOverrideReminderPrinted = false;
	
	private final Queue<String> chatMessageQueueForNextFrame = new LinkedBlockingQueue<>();
	
	public boolean rendererDisabledBecauseOfExceptions = false;
	
	private long lastFlushNanoTime = 0;
	
	private final ClientPluginChannelApi pluginChannelApi = new ClientPluginChannelApi(this::clientLevelLoadEvent, this::clientLevelUnloadEvent);
	
	
	/** Holds any levels that were loaded before the {@link ClientApi#onClientOnlyConnected} was fired. */
	public final HashSet<IClientLevelWrapper> waitingClientLevels = new HashSet<>();
	/** Holds any chunks that were loaded before the {@link ClientApi#clientLevelLoadEvent(IClientLevelWrapper)} was fired. */
	public final HashMap<Pair<IClientLevelWrapper, DhChunkPos>, IChunkWrapper> waitingChunkByClientLevelAndPos = new HashMap<>();
	
	/** re-set every frame during the opaque rendering stage */
	private boolean renderingCancelledForThisFrame;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	private ClientApi() { }
	
	
	
	//==============//
	// world events //
	//==============//
	
	/**
	 * May be fired slightly before or after the associated
	 * {@link ClientApi#clientLevelLoadEvent(IClientLevelWrapper)} event
	 * depending on how the host mod loader functions. <br><br>
	 * 
	 * Synchronized shouldn't be necessary, but is present to match {@see onClientOnlyDisconnected} and prevent any unforeseen issues. 
	 */
	public synchronized void onClientOnlyConnected()
	{
		// only continue if the client is connected to a different server
		if (MC.clientConnectedToDedicatedServer())
		{
			LOGGER.info("Client on ClientOnly mode connecting.");
			
			// firing after clientLevelLoadEvent
			// TODO if level has prepped to load it should fire level load event
			DhClientWorld world = new DhClientWorld();
			SharedApi.setDhWorld(world);
			
			this.pluginChannelApi.onJoin(world.networkState.getSession());
			world.networkState.sendConfigMessage();
			
			LOGGER.info("Loading [" + this.waitingClientLevels.size() + "] waiting client level wrappers.");
			for (IClientLevelWrapper level : this.waitingClientLevels)
			{
				this.clientLevelLoadEvent(level);
			}
			
			this.waitingClientLevels.clear();
		}
	}
	
	/** Synchronized to prevent a rare issue where multiple disconnect events are triggered on top of each other. */
	public synchronized void onClientOnlyDisconnected()
	{
		AbstractDhWorld world = SharedApi.getAbstractDhWorld();
		if (world != null)
		{
			LOGGER.info("Client on ClientOnly mode disconnecting.");
			
			world.close();
			SharedApi.setDhWorld(null);
		}
		
		// remove any waiting items
		this.waitingChunkByClientLevelAndPos.clear();
		this.waitingClientLevels.clear();
	}
	
	
	
	//==============//
	// level events //
	//==============//
	
	public void clientLevelUnloadEvent(IClientLevelWrapper level)
	{
		this.clientLevelUnloadEvent(level, false);
	}
	
	public void clientLevelUnloadEvent(IClientLevelWrapper level, boolean respawn)
	{
		try
		{
			LOGGER.info("Unloading client level [" + level + "]-["+level.getDimensionType().getDimensionName()+"].");
			
			if (level instanceof IServerKeyedClientLevel && !respawn)
			{
				this.pluginChannelApi.onClientLevelUnload();
			}
			
			AbstractDhWorld world = SharedApi.getAbstractDhWorld();
			if (world != null)
			{
				world.unloadLevel(level);
				ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelUnloadEvent.class, new DhApiLevelUnloadEvent.EventParam(level));
			}
			else
			{
				this.waitingClientLevels.remove(level);
			}
		}
		catch (Exception e)
		{
			// handle errors here to prevent blowing up a mixin or API up stream
			LOGGER.error("Unexpected error in ClientApi.clientLevelUnloadEvent(), error: "+e.getMessage(), e);
		}
	}
	
	public void clientLevelLoadEvent(IClientLevelWrapper level)
	{
		try
		{
			LOGGER.info("Loading client level [" + level + "]-["+level.getDimensionType().getDimensionName()+"].");
			
			AbstractDhWorld world = SharedApi.getAbstractDhWorld();
			if (world != null)
			{
				if (!this.pluginChannelApi.allowLevelAutoload())
				{
					LOGGER.info("Levels in this connection are managed by the server, skipping auto-load.");
					
					// Instead of attempting to load themselves, send config and wait for level key.
					((DhClientWorld) world).networkState.sendConfigMessage();
					return;
				}
				
				world.getOrLoadLevel(level);
				ApiEventInjector.INSTANCE.fireAllEvents(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent.EventParam(level));
				
				this.loadWaitingChunksForLevel(level);
			}
			else
			{
				this.waitingClientLevels.add(level);
			}
		}
		catch (Exception e)
		{
			// handle errors here to prevent blowing up a mixin or API up stream
			LOGGER.error("Unexpected error in ClientApi.clientLevelLoadEvent(), error: "+e.getMessage(), e);
		}
	}
	
	private void loadWaitingChunksForLevel(IClientLevelWrapper level)
	{
		HashSet<Pair<IClientLevelWrapper, DhChunkPos>> keysToRemove = new HashSet<>();
		for (Pair<IClientLevelWrapper, DhChunkPos> levelChunkPair : this.waitingChunkByClientLevelAndPos.keySet())
		{
			// only load chunks that came from this level
			IClientLevelWrapper levelWrapper = levelChunkPair.first;
			if (levelWrapper.equals(level))
			{
				IChunkWrapper chunkWrapper = this.waitingChunkByClientLevelAndPos.get(levelChunkPair);
				SharedApi.INSTANCE.chunkLoadEvent(chunkWrapper, levelWrapper);
				keysToRemove.add(levelChunkPair);
			}
		}
		LOGGER.info("Loaded [" + keysToRemove.size() + "] waiting chunk wrappers.");
		
		for (Pair<IClientLevelWrapper, DhChunkPos> keyToRemove : keysToRemove)
		{
			this.waitingChunkByClientLevelAndPos.remove(keyToRemove);
		}
	}
	
	
	
	//===============//
	// render events //
	//===============//
	
	public void rendererShutdownEvent()
	{
		LOGGER.info("Renderer shutting down.");
		
		IProfilerWrapper profiler = MC.getProfiler();
		profiler.push("DH-RendererShutdown");
		
		profiler.pop();
	}
	
	public void rendererStartupEvent()
	{
		LOGGER.info("Renderer starting up.");
		
		IProfilerWrapper profiler = MC.getProfiler();
		profiler.push("DH-RendererStartup");
		
		// make sure the GLProxy is created before the LodBufferBuilder needs it
		GLProxy.getInstance();
		profiler.pop();
	}
	
	public void clientTickEvent()
	{
		IProfilerWrapper profiler = MC.getProfiler();
		profiler.push("DH-ClientTick");
		
		try
		{
			boolean doFlush = System.nanoTime() - this.lastFlushNanoTime >= SPAM_LOGGER_FLUSH_NS;
			if (doFlush)
			{
				this.lastFlushNanoTime = System.nanoTime();
				SpamReducedLogger.flushAll();
			}
			ConfigBasedLogger.updateAll();
			ConfigBasedSpamLogger.updateAll(doFlush);
			
			IDhClientWorld clientWorld = SharedApi.getIDhClientWorld();
			if (clientWorld != null)
			{
				clientWorld.clientTick();
				
				// Ignore local world gen, as it's managed by server ticking
				if (!(clientWorld instanceof DhClientServerWorld))
				{
					SharedApi.worldGenTick(clientWorld::doWorldGen);
				}
			}
		}
		catch (Exception e)
		{
			// handle errors here to prevent blowing up a mixin or API up stream
			LOGGER.error("Unexpected error in ClientApi.clientTickEvent(), error: "+e.getMessage(), e);
		}
		
		profiler.pop();
	}
	
	
	
	//============//
	// networking //
	//============//
	
	public void pluginMessageReceived(@NotNull PluginChannelMessage message)
	{
		this.pluginChannelApi.session.tryHandleMessage(message);
	}
	
	
	
	//===========//
	// rendering //
	//===========//
	
	/** Should be called before {@link ClientApi#renderDeferredLods} */
	public void renderLods(IClientLevelWrapper levelWrapper, Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks)
	{ this.renderLodLayer(levelWrapper, mcModelViewMatrix, mcProjectionMatrix, partialTicks, false); }
	
	/** 
	 * Only necessary when Shaders are in use.
	 * Should be called after {@link ClientApi#renderLods} 
	 */
	public void renderDeferredLods(IClientLevelWrapper levelWrapper, Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks)
	{ this.renderLodLayer(levelWrapper, mcModelViewMatrix, mcProjectionMatrix, partialTicks, true); }
	
	private void renderLodLayer(
			IClientLevelWrapper levelWrapper, Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks,
			boolean renderingDeferredLayer)
	{
		// logging //
		
		// dev build
		if (ModInfo.IS_DEV_BUILD && !this.configOverrideReminderPrinted && MC.playerExists())
		{
			this.configOverrideReminderPrinted = true;
			
			// remind the user that this is a development build
			MC.sendChatMessage("Distant Horizons nightly/unstable build, version: [" + ModInfo.VERSION+"].");
			MC.sendChatMessage("Issues may occur with this version.");
			MC.sendChatMessage("Here be dragons!");
			MC.sendChatMessage("");
		}
		
		// generic messages
		while (!this.chatMessageQueueForNextFrame.isEmpty())
		{
			String message = this.chatMessageQueueForNextFrame.poll();
			if (message == null)
			{
				// done to prevent potential null pointers
				message = "";
			}
			MC.sendChatMessage(message);
		}
		
		IProfilerWrapper profiler = MC.getProfiler();
		profiler.pop(); // get out of "terrain"
		profiler.push("DH-RenderLevel");
		
		
		
		// render parameter setup //
		
		EDhApiRenderPass renderPass;
		if (DhApiRenderProxy.INSTANCE.getDeferTransparentRendering())
		{
			if (renderingDeferredLayer)
			{
				renderPass = EDhApiRenderPass.TRANSPARENT;
			}
			else
			{
				renderPass = EDhApiRenderPass.OPAQUE;
			}
		}
		else
		{
			renderPass = EDhApiRenderPass.OPAQUE_AND_TRANSPARENT;
		}
		
		DhApiRenderParam renderEventParam =
				new DhApiRenderParam(
						renderPass,
						partialTicks,
						RenderUtil.getNearClipPlaneDistanceInBlocks(partialTicks), RenderUtil.getFarClipPlaneDistanceInBlocks(),
						mcProjectionMatrix, mcModelViewMatrix,
						RenderUtil.createLodProjectionMatrix(mcProjectionMatrix, partialTicks), RenderUtil.createLodModelViewMatrix(mcModelViewMatrix),
						levelWrapper.getMinHeight()
				);
		
		
		
		// render validation //
		
		try
		{
			if (!RenderUtil.shouldLodsRender(levelWrapper))
			{
				return;
			}
			
			IDhClientWorld dhClientWorld = SharedApi.getIDhClientWorld();
			if (dhClientWorld == null)
			{
				return;
			}
			
			IDhClientLevel level = (IDhClientLevel) dhClientWorld.getLevel(levelWrapper);
			if (level == null)
			{
				return;
			}
			
			if (this.rendererDisabledBecauseOfExceptions)
			{
				return;
			}
			
			
			
			// render pass //
			
			if (!renderingDeferredLayer)
			{
				if (Config.Client.Advanced.Debugging.rendererMode.get() == EDhApiRendererMode.DEFAULT)
				{
					this.renderingCancelledForThisFrame = ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeRenderEvent.class, renderEventParam);
					if (!this.renderingCancelledForThisFrame)
					{
						level.render(renderEventParam, profiler);
					}
					
					if (!DhApi.Delayed.renderProxy.getDeferTransparentRendering())
					{
						ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterRenderEvent.class, renderEventParam);
					}
				}
				else if (Config.Client.Advanced.Debugging.rendererMode.get() == EDhApiRendererMode.DEBUG)
				{
					profiler.push("Render Debug");
					ClientApi.testRenderer.render();
					profiler.pop();
				}
			}
			else
			{
				boolean renderingCancelled = ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeDeferredRenderEvent.class, renderEventParam);
				if (!renderingCancelled)
				{
					level.renderDeferred(renderEventParam, profiler);
				}
				
				
				if (DhApi.Delayed.renderProxy.getDeferTransparentRendering())
				{
					ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterRenderEvent.class, renderEventParam);
				}
			}
		}
		catch (Exception e)
		{
			this.rendererDisabledBecauseOfExceptions = true;
			LOGGER.error("Unexpected Renderer error in render pass [" + renderPass + "]. Error: " + e.getMessage(), e);
			
			MC.sendChatMessage("\u00A74\u00A7l\u00A7uERROR: Distant Horizons renderer has encountered an exception!");
			MC.sendChatMessage("\u00A74Renderer is now disabled to prevent further issues.");
			MC.sendChatMessage("\u00A74Exception detail: " + e);
		}
		finally
		{
			try
			{
				// these tasks always need to be called, regardless of whether the renderer is enabled or not to prevent memory leaks
				GLProxy.getInstance().runRenderThreadTasks();
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected issue running render thread tasks.", e);
			}
			
			
			profiler.pop(); // end LOD
			profiler.push("terrain"); // go back into "terrain"
		}
	}
	
	
	
	
	//=================//
	//    DEBUG USE    //
	//=================//
	
	/** Trigger once on key press, with CLIENT PLAYER. */
	public void keyPressedEvent(int glfwKey)
	{
		if (!Config.Client.Advanced.Debugging.enableDebugKeybindings.get())
		{
			// keybindings are disabled
			return;
		}
		
		
		if (glfwKey == GLFW.GLFW_KEY_F8)
		{
			Config.Client.Advanced.Debugging.debugRendering.set(EDhApiDebugRendering.next(Config.Client.Advanced.Debugging.debugRendering.get()));
			MC.sendChatMessage("F8: Set debug mode to " + Config.Client.Advanced.Debugging.debugRendering.get());
		}
		else if (glfwKey == GLFW.GLFW_KEY_F6)
		{
			Config.Client.Advanced.Debugging.rendererMode.set(EDhApiRendererMode.next(Config.Client.Advanced.Debugging.rendererMode.get()));
			MC.sendChatMessage("F6: Set rendering to " + Config.Client.Advanced.Debugging.rendererMode.get());
		}
		else if (glfwKey == GLFW.GLFW_KEY_P)
		{
			prefLoggerEnabled = !prefLoggerEnabled;
			MC.sendChatMessage("P: Debug Pref Logger is " + (prefLoggerEnabled ? "enabled" : "disabled"));
		}
	}
	
	/** 
	 * Queues the given message to appear in chat the next valid frame.
	 * Useful for queueing up messages that may be triggered before the user has loaded into the world. 
	 */
	public void showChatMessageNextFrame(String chatMessage) { this.chatMessageQueueForNextFrame.add(chatMessage); }
	
}