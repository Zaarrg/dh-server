package com.seibel.distanthorizons.fabric.mixins.client;

import com.seibel.distanthorizons.api.enums.config.EDhApiUpdateBranch;
import com.seibel.distanthorizons.common.wrappers.gui.updater.UpdateModScreen;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.jar.installer.GitlabGetter;
import com.seibel.distanthorizons.core.jar.installer.ModrinthGetter;
import com.seibel.distanthorizons.core.jar.updater.SelfUpdater;
import com.seibel.distanthorizons.core.wrapperInterfaces.IVersionConstants;
import com.seibel.distanthorizons.coreapi.ModInfo;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * At the moment this is only used for the auto updater
 *
 * @author coolGi
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft
{
	@Shadow
	public abstract boolean isLocalServer();
	
	@Unique
	private ClientLevel lastLevel;
	
	/** 
	 * Can be enabled for testing the auto updater UI. <br/>
	 * will always show the auto updater if set to true. 
	 */
	@Unique
	private static final boolean DEBUG_ALWAYS_SHOW_UPDATER = false;

	
	
	#if MC_VER < MC_1_20_2
	#if MC_VER == MC_1_20_1
	@Redirect(
			method = "Lnet/minecraft/client/Minecraft;setInitialScreen(Lcom/mojang/realmsclient/client/RealmsClient;Lnet/minecraft/server/packs/resources/ReloadInstance;Lnet/minecraft/client/main/GameConfig$QuickPlayData;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V")
	)
	public void onOpenScreen(Minecraft instance, Screen guiScreen)
	{
	#else
	@Redirect(
			method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V")
	)
	public void onOpenScreen(Minecraft instance, Screen guiScreen)
	{
	#endif
		if (!Config.Client.Advanced.AutoUpdater.enableAutoUpdater.get() && !DEBUG_ALWAYS_SHOW_UPDATER) // Don't do anything if the user doesn't want it
		{
			instance.setScreen(guiScreen); // Sets the screen back to the vanilla screen as if nothing ever happened
			return;
		}
		
		if (SelfUpdater.onStart() || DEBUG_ALWAYS_SHOW_UPDATER)
		{
			instance.setScreen(new UpdateModScreen(
					new TitleScreen(false), // We don't want to use the vanilla title screen as it would fade the buttons
					(Config.Client.Advanced.AutoUpdater.updateBranch.get() == EDhApiUpdateBranch.STABLE ? ModrinthGetter.getLatestIDForVersion(SingletonInjector.INSTANCE.get(IVersionConstants.class).getMinecraftVersion()): GitlabGetter.INSTANCE.projectPipelines.get(0).get("sha"))
			));
		}
		else
		{
			instance.setScreen(guiScreen); // Sets the screen back to the vanilla screen as if nothing ever happened
		}
	}
	#endif
	
	#if MC_VER >= MC_1_20_2
	@Redirect(
			method = "Lnet/minecraft/client/Minecraft;onGameLoadFinished(Lnet/minecraft/client/Minecraft$GameLoadCookie;)V",
			at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V")
	)
	private void buildInitialScreens(Runnable runnable)
	{
		if (
				DEBUG_ALWAYS_SHOW_UPDATER ||
				(
					// Don't do anything if the user doesn't want it
					Config.Client.Advanced.AutoUpdater.enableAutoUpdater.get()
					&& SelfUpdater.onStart()
				)
			)
		{
			runnable = () -> 
			{
				String versionId;
				EDhApiUpdateBranch updateBranch = EDhApiUpdateBranch.convertAutoToStableOrNightly(Config.Client.Advanced.AutoUpdater.updateBranch.get());
				if (updateBranch == EDhApiUpdateBranch.STABLE)
				{
					versionId = ModrinthGetter.getLatestIDForVersion(SingletonInjector.INSTANCE.get(IVersionConstants.class).getMinecraftVersion());
				}
				else
				{
					versionId = GitlabGetter.INSTANCE.projectPipelines.get(0).get("sha");
				}
				
				Minecraft.getInstance().setScreen(new UpdateModScreen(
						// TODO: Change to runnable, instead of tittle screen
						new TitleScreen(false), // We don't want to use the vanilla title screen as it would fade the buttons
						versionId
				));
			};
		}
		
		runnable.run();
	}
	#endif
	
	@Inject(at = @At("HEAD"), method = "updateLevelInEngines")
	public void updateLevelInEngines(ClientLevel level, CallbackInfo ci)
	{
		// Only for multiplayer clients
		if (!this.isLocalServer())
		{
			if (this.lastLevel != null && level != this.lastLevel)
			{
				ClientApi.INSTANCE.clientLevelUnloadEvent(ClientLevelWrapper.getWrapper(this.lastLevel));
			}
			if (level != null)
			{
				ClientApi.INSTANCE.clientLevelLoadEvent(ClientLevelWrapper.getWrapper(level));
			}
			this.lastLevel = level;
		}
	}
	
	@Inject(at = @At("HEAD"), method = "close()V")
	public void close(CallbackInfo ci) { SelfUpdater.onClose(); }
	
}