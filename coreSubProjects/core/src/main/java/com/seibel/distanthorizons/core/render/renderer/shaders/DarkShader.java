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

import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.renderer.ScreenQuad;
import org.lwjgl.opengl.GL32;

public class DarkShader extends AbstractShaderRenderer
{
	public static DarkShader INSTANCE = new DarkShader();

	
	@Override
	public void onInit()
	{
		this.shader = new ShaderProgram(
				"shaders/normal.vert",
				"shaders/test/dark.frag",
				"fragColor",
				new String[]{"vPosition", "color"});
	}
	
	@Override
	protected void onApplyUniforms(float partialTicks)
	{
		GL32.glActiveTexture(GL32.GL_TEXTURE0);
		GL32.glBindTexture(GL32.GL_TEXTURE_2D, MC_RENDER.getDepthTextureId());
	}
	
	@Override
	protected void onRender()
	{
		GLState state = new GLState();
		
		GL32.glDisable(GL32.GL_DEPTH_TEST);
		GL32.glDisable(GL32.GL_SCISSOR_TEST);
		GL32.glEnable(GL32.GL_BLEND);
		GL32.glBlendFunc(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA);
		
		ScreenQuad.INSTANCE.render();

		state.restore();
	}
}
