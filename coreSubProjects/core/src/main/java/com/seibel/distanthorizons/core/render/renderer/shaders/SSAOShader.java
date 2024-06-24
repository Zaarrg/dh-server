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

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.render.renderer.SSAORenderer;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import com.seibel.distanthorizons.coreapi.util.math.Mat4f;
import org.lwjgl.opengl.GL32;

/**
 * Draws the SSAO to a texture. <br><br>
 *
 * See Also: <br>
 * {@link SSAORenderer} - Parent to this shader. <br>
 * {@link SSAOApplyShader} - draws the SSAO texture to DH's FrameBuffer. <br>
 */
public class SSAOShader extends AbstractShaderRenderer
{
	public static SSAOShader INSTANCE = new SSAOShader();
	
	public int frameBuffer;
	
	private Mat4f projection;
	private Mat4f invertedProjection;
	
	// uniforms
	public int gProjUniform;
	public int gInvProjUniform;
	public int gSampleCountUniform;
	public int gRadiusUniform;
	public int gStrengthUniform;
	public int gMinLightUniform;
	public int gBiasUniform;
	public int gDepthMapUniform;
	
	
	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram("shaders/normal.vert", "shaders/ssao/ao.frag",
				"fragColor", new String[]{"vPosition"});
		
		// uniform setup
		this.gProjUniform = this.shader.getUniformLocation("gProj");
		this.gInvProjUniform = this.shader.getUniformLocation("gInvProj");
		this.gSampleCountUniform = this.shader.getUniformLocation("gSampleCount");
		this.gRadiusUniform = this.shader.getUniformLocation("gRadius");
		this.gStrengthUniform = this.shader.getUniformLocation("gStrength");
		this.gMinLightUniform = this.shader.getUniformLocation("gMinLight");
		this.gBiasUniform = this.shader.getUniformLocation("gBias");
		this.gDepthMapUniform = this.shader.getUniformLocation("gDepthMap");
	}
	
	public void setProjectionMatrix(Mat4f projectionMatrix)
	{
		this.projection = projectionMatrix;
		
		this.invertedProjection = new Mat4f(projectionMatrix);
		this.invertedProjection.invert();
	}
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		this.shader.setUniform(this.gProjUniform, this.projection);
		
		this.shader.setUniform(this.gInvProjUniform, this.invertedProjection);
		
		this.shader.setUniform(this.gSampleCountUniform,
				Config.Client.Advanced.Graphics.Ssao.sampleCount.get());
		
		this.shader.setUniform(this.gRadiusUniform,
				Config.Client.Advanced.Graphics.Ssao.radius.get().floatValue());
		
		this.shader.setUniform(this.gStrengthUniform,
				Config.Client.Advanced.Graphics.Ssao.strength.get().floatValue());
		
		this.shader.setUniform(this.gMinLightUniform,
				Config.Client.Advanced.Graphics.Ssao.minLight.get().floatValue());
		
		this.shader.setUniform(this.gBiasUniform,
				Config.Client.Advanced.Graphics.Ssao.bias.get().floatValue());
		
		GL32.glActiveTexture(GL32.GL_TEXTURE0);
		GL32.glBindTexture(GL32.GL_TEXTURE_2D, LodRenderer.getActiveDepthTextureId());
		
		GL32.glUniform1i(this.gDepthMapUniform, 0);
	}
	
	
	//========//
	// render //
	//========//
	
	@Override
	protected void onRender()
	{
		GL32.glBindFramebuffer(GL32.GL_FRAMEBUFFER, this.frameBuffer);
		GL32.glDisable(GL32.GL_SCISSOR_TEST);
		GL32.glDisable(GL32.GL_DEPTH_TEST);
		GL32.glDisable(GL32.GL_BLEND);
		
		ScreenQuad.INSTANCE.render();
	}
}
