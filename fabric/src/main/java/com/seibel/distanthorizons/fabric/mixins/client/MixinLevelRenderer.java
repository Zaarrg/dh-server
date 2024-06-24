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

package com.seibel.distanthorizons.fabric.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
#if MC_VER < MC_1_19_4
import com.mojang.math.Matrix4f;
#else
import org.joml.Matrix4f;
#endif
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.coreapi.util.math.Mat4f;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This class is used to mix in my rendering code
 * before Minecraft starts rendering blocks.
 * If this wasn't done, and we used Forge's
 * render last event, the LODs would render on top
 * of the normal terrain.
 *
 * This is also the mixin for rendering the clouds
 *
 * @author coolGi
 * @author James Seibel
 * @version 12-31-2021
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer
{
    @Shadow
    private ClientLevel level;

	#if MC_VER < MC_1_17_1
    @Inject(at = @At("HEAD"),
			method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDD)V",
			cancellable = true)
	private void renderChunkLayer(RenderType renderType, PoseStack matrixStackIn, double xIn, double yIn, double zIn, CallbackInfo callback)
	#elif MC_VER < MC_1_19_4
    @Inject(at = @At("HEAD"),
            method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLcom/mojang/math/Matrix4f;)V",
            cancellable = true)
    private void renderChunkLayer(RenderType renderType, PoseStack modelViewMatrixStack, double cameraXBlockPos, double cameraYBlockPos, double cameraZBlockPos, Matrix4f projectionMatrix, CallbackInfo callback)
	#elif MC_VER < MC_1_20_2
    @Inject(at = @At("HEAD"),
            method = "renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
            cancellable = true)
    private void renderChunkLayer(RenderType renderType, PoseStack modelViewMatrixStack, double cameraXBlockPos, double cameraYBlockPos, double cameraZBlockPos, Matrix4f projectionMatrix, CallbackInfo callback)
    #elif MC_VER < MC_1_20_6
	@Inject(at = @At("HEAD"),
			method = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
			cancellable = true)
	private void renderChunkLayer(RenderType renderType, PoseStack modelViewMatrixStack, double camX, double camY, double camZ, Matrix4f projectionMatrix, CallbackInfo callback)
	#else
	@Inject(at = @At("HEAD"),
			method = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
			cancellable = true)
	private void renderChunkLayer(RenderType renderType, double x, double y, double z, Matrix4f projectionMatrix, Matrix4f frustumMatrix, CallbackInfo callback)
    #endif
    {
		#if MC_VER == MC_1_16_5
	    // get the matrices from the OpenGL fixed pipeline
	    float[] mcProjMatrixRaw = new float[16];
	    GL15.glGetFloatv(GL15.GL_PROJECTION_MATRIX, mcProjMatrixRaw);
	    Mat4f mcProjectionMatrix = new Mat4f(mcProjMatrixRaw);
	    mcProjectionMatrix.transpose();
	    
	    Mat4f mcModelViewMatrix = McObjectConverter.Convert(matrixStackIn.last().pose());
		
		#elif MC_VER <= MC_1_20_4
		// get the matrices directly from MC
		Mat4f mcModelViewMatrix = McObjectConverter.Convert(modelViewMatrixStack.last().pose());
		Mat4f mcProjectionMatrix = McObjectConverter.Convert(projectionMatrix);
		#else
	    // get the matrices directly from MC
	    Mat4f mcModelViewMatrix = McObjectConverter.Convert(projectionMatrix);
	    Mat4f mcProjectionMatrix = new Mat4f();
	    mcProjectionMatrix.setIdentity();
		#endif
	    
	    if (renderType.equals(RenderType.translucent())) {
		    ClientApi.INSTANCE.renderDeferredLods(ClientLevelWrapper.getWrapper(this.level),
				    mcModelViewMatrix,
				    mcProjectionMatrix,
				    #if MC_VER < MC_1_21
		    Minecraft.getInstance().getFrameTime()
					#else
		    Minecraft.getInstance().getTimer().getRealtimeDeltaTicks()
					#endif
				    );
	    }
		
		// FIXME completely disables rendering when sodium is installed
		if (Config.Client.Advanced.Debugging.lodOnlyMode.get())
		{
		    callback.cancel();
		}
    }
	
	#if MC_VER < MC_1_19_4
	@Inject(at = @At(value = "TAIL", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runUpdates(IZZ)I"), method = "renderLevel")
	public void callAfterRunUpdates(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci)
	#elif MC_VER < MC_1_20_1
	@Inject(at = @At(value = "TAIL", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runUpdates(IZZ)I"), method = "renderLevel")
	public void callAfterRunUpdates(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci)
	#elif MC_VER < MC_1_20_6
	@Inject(at = @At(value = "TAIL", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runLightUpdates()I"), method = "renderLevel")
	private void callAfterRunUpdates(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci)
	#else
	@Inject(at = @At(value = "TAIL", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runLightUpdates()I"), method = "renderLevel")
	private void callAfterRunUpdates(CallbackInfo ci) 
	#endif
	{
		ChunkWrapper.syncedUpdateClientLightStatus();
	}
	
	
}
