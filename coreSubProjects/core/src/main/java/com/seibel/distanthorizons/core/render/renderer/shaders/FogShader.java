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

package com.seibel.distanthorizons.core.render.renderer.shaders;

import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogColorMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.render.fog.LodFogConfig;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.glObject.shader.Shader;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.IVersionConstants;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.coreapi.util.math.Mat4f;
import org.lwjgl.opengl.GL32;

import java.awt.*;

public class FogShader extends AbstractShaderRenderer
{
	public static FogShader INSTANCE = new FogShader(LodFogConfig.generateFogConfig());
	
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IVersionConstants VERSION_CONSTANTS = SingletonInjector.INSTANCE.get(IVersionConstants.class);
	
	
	private final LodFogConfig fogConfig;
	private Mat4f inverseMvmProjMatrix;
	public int gInvertedModelViewProjectionUniform;
	public int gDepthMapUniform;
	
	// Fog Uniforms
	public int fogColorUniform;
	public int fogScaleUniform;
	public int fogVerticalScaleUniform;
	public int nearFogStartUniform;
	public int nearFogLengthUniform;
	public int fullFogModeUniform;
	
	
	public FogShader(LodFogConfig fogConfig)
	{
		this.fogConfig = fogConfig;
	}

	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				// TODO rename normal.vert to something like "postProcess.vert"
				() -> Shader.loadFile("shaders/normal.vert", false, new StringBuilder()).toString(),
				() -> this.fogConfig.loadAndProcessFragShader("shaders/fog/fog.frag", false).toString(),
				"fragColor", new String[]{"vPosition"}
		);
		
		// all uniforms should be tryGet...
		// because disabling fog can cause the GLSL to optimize out most (if not all) uniforms
		
		this.gInvertedModelViewProjectionUniform = this.shader.tryGetUniformLocation("gInvMvmProj");
		this.gDepthMapUniform = this.shader.tryGetUniformLocation("gDepthMap");
		
		// Fog uniforms
		this.fogColorUniform = this.shader.tryGetUniformLocation("fogColor");
		this.fullFogModeUniform = this.shader.tryGetUniformLocation("fullFogMode");
		this.fogScaleUniform = this.shader.tryGetUniformLocation("fogScale");
		this.fogVerticalScaleUniform = this.shader.tryGetUniformLocation("fogVerticalScale");
		
		// near fog
		this.nearFogStartUniform = this.shader.tryGetUniformLocation("nearFogStart");
		this.nearFogLengthUniform = this.shader.tryGetUniformLocation("nearFogLength");
	}
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		this.shader.setUniform(this.gInvertedModelViewProjectionUniform, this.inverseMvmProjMatrix);
		
		int lodDrawDistance = Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get() * LodUtil.CHUNK_WIDTH;
		int vanillaDrawDistance = MC_RENDER.getRenderDistance() * LodUtil.CHUNK_WIDTH;
		vanillaDrawDistance += LodUtil.CHUNK_WIDTH * 2; // Give it a 2 chunk boundary for near fog.
		
		// bind the depth buffer
		if (this.gDepthMapUniform != -1)
		{
			GL32.glActiveTexture(GL32.GL_TEXTURE1);
			GL32.glBindTexture(GL32.GL_TEXTURE_2D, LodRenderer.getActiveDepthTextureId());
			GL32.glUniform1i(this.gDepthMapUniform, 1);
		}
		
		// Fog
		if (this.fullFogModeUniform != -1) this.shader.setUniform(this.fullFogModeUniform, MC_RENDER.isFogStateSpecial() ? 1 : 0);
		if (this.fogColorUniform != -1) this.shader.setUniform(this.fogColorUniform, MC_RENDER.isFogStateSpecial() ? this.getSpecialFogColor(partialTicks) : this.getFogColor(partialTicks));
		
		float nearFogLen = vanillaDrawDistance * 0.2f / lodDrawDistance;
		float nearFogStart = vanillaDrawDistance * (VERSION_CONSTANTS.isVanillaRenderedChunkSquare() ? (float) Math.sqrt(2.0) : 1.0f) / lodDrawDistance;
		if (this.nearFogStartUniform != -1) this.shader.setUniform(this.nearFogStartUniform, nearFogStart);
		if (this.nearFogLengthUniform != -1) this.shader.setUniform(this.nearFogLengthUniform, nearFogLen);
		if (this.fogScaleUniform != -1) this.shader.setUniform(this.fogScaleUniform, 1.f / lodDrawDistance);
		if (this.fogVerticalScaleUniform != -1) this.shader.setUniform(this.fogVerticalScaleUniform, 1.f / MC.getWrappedClientLevel().getHeight());
	}
	
	private Color getFogColor(float partialTicks)
	{
		Color fogColor;
		
		if (Config.Client.Advanced.Graphics.Fog.colorMode.get() == EDhApiFogColorMode.USE_SKY_COLOR)
		{
			fogColor = MC_RENDER.getSkyColor();
		}
		else
		{
			fogColor = MC_RENDER.getFogColor(partialTicks);
		}
		
		return fogColor;
	}
	
	private Color getSpecialFogColor(float partialTicks) { return MC_RENDER.getSpecialFogColor(partialTicks); }
	
	public void setModelViewProjectionMatrix(Mat4f combinedModelViewProjectionMatrix)
	{
		this.inverseMvmProjMatrix = new Mat4f(combinedModelViewProjectionMatrix);
		this.inverseMvmProjMatrix.invert();
	}
	
	@Override
	protected void onRender()
	{
		GLState state = new GLState();
		
		GL32.glDisable(GL32.GL_DEPTH_TEST);
		GL32.glDisable(GL32.GL_SCISSOR_TEST);
		
		GL32.glEnable(GL32.GL_BLEND);
		GL32.glBlendEquation(GL32.GL_FUNC_ADD);
		GL32.glBlendFuncSeparate(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA, GL32.GL_ONE, GL32.GL_ONE);
		
		GL32.glActiveTexture(GL32.GL_TEXTURE0);
		GL32.glBindTexture(GL32.GL_TEXTURE_2D, LodRenderer.getActiveColorTextureId());
		ScreenQuad.INSTANCE.render();
		
		state.restore();
	}
	
}
