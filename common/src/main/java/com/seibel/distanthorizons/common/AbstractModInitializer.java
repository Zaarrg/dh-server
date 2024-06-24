package com.seibel.distanthorizons.common;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterDhInitEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDhInitEvent;
import com.seibel.distanthorizons.common.wrappers.DependencySetup;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftDedicatedServerWrapper;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.ConfigBase;
import com.seibel.distanthorizons.core.config.eventHandlers.presets.ThreadPresetConfigEventHandler;
import com.seibel.distanthorizons.core.config.types.AbstractConfigType;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.jar.ModJarInfo;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.objects.Pair;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IModChecker;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

#if MC_VER >= MC_1_19_2
import net.minecraft.network.chat.Component;
#else // < 1.19.2
import net.minecraft.network.chat.TranslatableComponent;
#endif

/**
 * Base for all mod loader initializers 
 * and handles most setup. 
 */
public abstract class AbstractModInitializer
{
	protected static final Logger LOGGER = DhLoggerBuilder.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
	
	private CommandDispatcher<CommandSourceStack> commandDispatcher;
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	protected abstract void createInitialBindings();
	protected abstract IEventProxy createClientProxy();
	protected abstract IEventProxy createServerProxy(boolean isDedicated);
	protected abstract void initializeModCompat();
	
	protected abstract void subscribeRegisterCommandsEvent(Consumer<CommandDispatcher<CommandSourceStack>> eventHandler);
	
	protected abstract void subscribeClientStartedEvent(Runnable eventHandler);
	protected abstract void subscribeServerStartingEvent(Consumer<MinecraftServer> eventHandler);
	protected abstract void runDelayedSetup();
	
	
	
	//===================//
	// initialize events //
	//===================//
	
	public void onInitializeClient()
	{
		DependencySetup.createClientBindings();
		
		LOGGER.info("Initializing " + ModInfo.READABLE_NAME);
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeDhInitEvent.class, null);
		
		this.startup();
		this.printModInfo();
		
		this.createClientProxy().registerEvents();
		this.createServerProxy(false).registerEvents();
		
		this.initializeModCompat();
		
		LOGGER.info(ModInfo.READABLE_NAME + " Initialized");
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterDhInitEvent.class, null);
		
		// Client uses config for auto-updater, so it's initialized here instead of post-init stage
		this.initConfig();
		
		this.subscribeClientStartedEvent(this::postInit);
	}
	
	public void onInitializeServer()
	{
		DependencySetup.createServerBindings();
		
		LOGGER.info("Initializing " + ModInfo.READABLE_NAME);
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiBeforeDhInitEvent.class, null);
		
		this.startup();
		this.printModInfo();
		
		// This prevents returning uninitialized Config values,
		// resulting from a circular reference mid-initialization in a static class
		// noinspection ResultOfMethodCallIgnored
		ThreadPresetConfigEventHandler.INSTANCE.toString();
		
		this.createServerProxy(true).registerEvents();
		
		this.initializeModCompat();
		
		LOGGER.info(ModInfo.READABLE_NAME + " Initialized");
		ApiEventInjector.INSTANCE.fireAllEvents(DhApiAfterDhInitEvent.class, null);
		
		this.subscribeRegisterCommandsEvent(dispatcher -> { this.commandDispatcher = dispatcher; });
		
		this.subscribeServerStartingEvent(server -> 
		{
			MinecraftDedicatedServerWrapper.INSTANCE.dedicatedServer = (DedicatedServer)server;
			
			this.initConfig();
			this.postInit();
			this.initCommands();
			
			LOGGER.info("Dedicated server initialized at " + server.getServerDirectory());
		});
	}
	
	
	
	//===========================//
	// inner initializer methods //
	//===========================//
	
	private void startup()
	{
		DependencySetup.createSharedBindings();
		SharedApi.init();
		this.createInitialBindings();
	}
	
	private void printModInfo()
	{
		LOGGER.info(ModInfo.READABLE_NAME + ", Version: " + ModInfo.VERSION);
		
		// Useful for dev builds
		LOGGER.info("DH Branch: " + ModJarInfo.Git_Branch);
		LOGGER.info("DH Commit: " + ModJarInfo.Git_Commit);
		LOGGER.info("DH Jar Build Source: " + ModJarInfo.Build_Source);
	}
	
	protected <T extends IModAccessor> void tryCreateModCompatAccessor(String modId, Class<? super T> accessorClass, Supplier<T> accessorConstructor)
	{
		IModChecker modChecker = SingletonInjector.INSTANCE.get(IModChecker.class);
		if (modChecker.isModLoaded(modId))
		{
			//noinspection unchecked
			ModAccessorInjector.INSTANCE.bind((Class<? extends IModAccessor>) accessorClass, accessorConstructor.get());
		}
	}
	
	private void initConfig()
	{
		ConfigBase.INSTANCE = new ConfigBase(ModInfo.ID, ModInfo.NAME, Config.class, 2);
		Config.completeDelayedSetup();
	}
	
	private void postInit()
	{
		LOGGER.info("Post-Initializing Mod");
		this.runDelayedSetup();
		LOGGER.info("Mod Post-Initialized");
	}
	
	@SuppressWarnings({"rawtypes", "unchecked"})
	private void initCommands()
	{
		LiteralArgumentBuilder<CommandSourceStack> builder = literal("dhconfig")
				.requires(source -> source.hasPermission(4));
		
		for (AbstractConfigType<?, ?> type : ConfigBase.INSTANCE.entries)
		{
			if (!(type instanceof ConfigEntry))
			{
				continue;
			}
			//noinspection PatternVariableCanBeUsed
			ConfigEntry configEntry = (ConfigEntry) type;
			if (configEntry.getServersideShortName() == null)
			{
				continue;
			}
			
			Function<
					Function<CommandContext<CommandSourceStack>, Object>,
					Command<CommandSourceStack>
					> makeConfigUpdater = getter -> c -> {
				Object value = getter.apply(c);
				
				c.getSource().sendSuccess(
						#if MC_VER >= MC_1_20_1
						() -> Component.literal("Changed the value of "+configEntry.getServersideShortName()+" to "+value),
						#elif MC_VER >= MC_1_19_2
						Component.literal("Changed the value of "+configEntry.getServersideShortName()+" to "+value),
						#else
						new TranslatableComponent("Changed the value of "+configEntry.getServersideShortName()+" to "+value),
						#endif
						true);
				configEntry.set(value);
				return 1;
			};
			
			LiteralArgumentBuilder<CommandSourceStack> subcommand = literal(configEntry.getServersideShortName())
					.executes(c -> {
						#if MC_VER >= MC_1_20_1
						c.getSource().sendSuccess(() -> Component.literal("Current value of "+configEntry.getServersideShortName()+" is "+configEntry.get()), true);
						#elif MC_VER >= MC_1_19_2
						c.getSource().sendSuccess(Component.literal("Current value of "+configEntry.getServersideShortName()+" is "+configEntry.get()), true);
						#else // < 1.19.2
						c.getSource().sendSuccess(new TranslatableComponent("Current value of "+configEntry.getServersideShortName()+" is "+configEntry.get()), true);
						#endif
						return 1;
					});
			
			if (Enum.class.isAssignableFrom(configEntry.getType()))
			{
				for (Object choice : configEntry.getType().getEnumConstants())
				{
					subcommand.then(
							literal(choice.toString())
									.executes(makeConfigUpdater.apply(c -> choice))
					);
				}
			}
			else
			{
				boolean setterAdded = false;
				
				for (java.util.Map.Entry<Class<?>, Pair<Supplier<ArgumentType<?>>, BiFunction<CommandContext<?>, String, ?>>> pair : new HashMap<
						Class<?>,
						Pair<
								Supplier<ArgumentType<?>>,
								BiFunction<CommandContext<?>, String, ?>>
						>() {{
					this.put(Integer.class, new Pair<>(() -> integer((int) configEntry.getMin(), (int) configEntry.getMax()), IntegerArgumentType::getInteger));
					this.put(Double.class, new Pair<>(() -> doubleArg((double) configEntry.getMin(), (double) configEntry.getMax()), DoubleArgumentType::getDouble));
					this.put(Boolean.class, new Pair<>(BoolArgumentType::bool, BoolArgumentType::getBool));
					this.put(String.class, new Pair<>(StringArgumentType::string, StringArgumentType::getString));
				}}.entrySet())
				{
					if (!pair.getKey().isAssignableFrom(configEntry.getType()))
					{
						continue;
					}
					
					subcommand.then(argument("value", pair.getValue().first.get())
							.executes(makeConfigUpdater.apply(c -> pair.getValue().second.apply(c, "value"))));
					
					setterAdded = true;
					break;
				}
				
				if (!setterAdded)
				{
					throw new RuntimeException("Config type of "+type.getName()+" is not supported: "+configEntry.getType().getSimpleName());
				}
			}
			
			builder.then(subcommand);
		}
		
		this.commandDispatcher.register(builder);
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	public interface IEventProxy
	{
		void registerEvents();
	}
	
}