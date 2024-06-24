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

package com.seibel.distanthorizons.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.ConfigBase;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IPluginPacketSender;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.*;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.fabric.wrappers.modAccessor.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;

#if MC_VER >= MC_1_19_2
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
#else // < 1.19.2
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
#endif

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Initialize and setup the Mod. <br>
 * If you are looking for the real start of the mod
 * check out the ClientProxy.
 */
public class FabricMain extends AbstractModInitializer implements ClientModInitializer, DedicatedServerModInitializer
{
	private static final ResourceLocation INITIAL_PHASE = ResourceLocation.fromNamespaceAndPath(ModInfo.RESOURCE_NAMESPACE, ModInfo.DEDICATED_SERVER_INITIAL_PATH);
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	
	@Override
	protected void createInitialBindings()
	{
		SingletonInjector.INSTANCE.bind(IModChecker.class, ModChecker.INSTANCE);
		SingletonInjector.INSTANCE.bind(IPluginPacketSender.class, new FabricPluginPacketSender());
	}
	
	@Override
	protected IEventProxy createClientProxy() { return new FabricClientProxy(); }
	
	@Override
	protected IEventProxy createServerProxy(boolean isDedicated) { return new FabricServerProxy(isDedicated); }
	
	@Override
	protected void initializeModCompat()
	{
		IModChecker modChecker = SingletonInjector.INSTANCE.get(IModChecker.class);
		if (modChecker.isModLoaded("sodium"))
		{
			ModAccessorInjector.INSTANCE.bind(ISodiumAccessor.class, new SodiumAccessor());
			
			// If sodium is installed Indium is also necessary in order to use the Fabric rendering API
			if (!modChecker.isModLoaded("indium"))
			{
				String indiumMissingMessage = ModInfo.READABLE_NAME + " needs Indium to work with Sodium.\nPlease download Indium from https://modrinth.com/mod/indium";
				LOGGER.fatal(indiumMissingMessage);
				
				if (!GraphicsEnvironment.isHeadless())
				{
					JOptionPane.showMessageDialog(null, indiumMissingMessage, ModInfo.READABLE_NAME, JOptionPane.INFORMATION_MESSAGE);
				}
				
				IMinecraftClientWrapper mc = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
				String errorMessage = "loading Distant Horizons. Distant Horizons requires Indium in order to run with Sodium.";
				String exceptionError = "Distant Horizons conditional mod Exception";
				mc.crashMinecraft(errorMessage, new Exception(exceptionError));
			}
		}
		
		this.tryCreateModCompatAccessor("starlight", IStarlightAccessor.class, StarlightAccessor::new);
		this.tryCreateModCompatAccessor("optifine", IOptifineAccessor.class, OptifineAccessor::new);
		this.tryCreateModCompatAccessor("bclib", IBCLibAccessor.class, BCLibAccessor::new);
		#if MC_VER >= MC_1_19_4
		// 1.19.4 is the lowest version Iris supports DH
		this.tryCreateModCompatAccessor("iris", IIrisAccessor.class, IrisAccessor::new);
		#endif
	}
	
	@Override
	protected void subscribeRegisterCommandsEvent(Consumer<CommandDispatcher<CommandSourceStack>> eventHandler)
	{
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess #if MC_VER >= MC_1_19_2 , environment #endif ) -> {
			eventHandler.accept(dispatcher);
		});
	}
	
	@Override
	protected void subscribeClientStartedEvent(Runnable eventHandler) { ClientLifecycleEvents.CLIENT_STARTED.register((mc) -> eventHandler.run()); }
	
	@Override
	protected void subscribeServerStartingEvent(Consumer<MinecraftServer> eventHandler)
	{
		ServerLifecycleEvents.SERVER_STARTING.addPhaseOrdering(INITIAL_PHASE, Event.DEFAULT_PHASE);
		ServerLifecycleEvents.SERVER_STARTING.register(INITIAL_PHASE, eventHandler::accept);
	}
	
	@Override
	protected void runDelayedSetup()
	{
		SingletonInjector.INSTANCE.runDelayedSetup();
		
		if (Config.Client.Advanced.Graphics.Fog.disableVanillaFog.get() && SingletonInjector.INSTANCE.get(IModChecker.class).isModLoaded("bclib"))
		{
			ModAccessorInjector.INSTANCE.get(IBCLibAccessor.class).setRenderCustomFog(false); // Remove BCLib's fog
		}
		
		#if MC_VER >= MC_1_20_1
		if (SingletonInjector.INSTANCE.get(IModChecker.class).isModLoaded("sodium"))
		{
			ModAccessorInjector.INSTANCE.get(ISodiumAccessor.class).setFogOcclusion(false); // FIXME: This is a tmp fix for sodium 0.5.0, and 0.5.1. This is fixed in sodium 0.5.2
		}
		#endif
		
		if (ConfigBase.INSTANCE == null)
		{
			throw new IllegalStateException("Config was not initialized. Make sure to call LodCommonMain.initConfig() before calling this method.");
		}
	}
	
}
