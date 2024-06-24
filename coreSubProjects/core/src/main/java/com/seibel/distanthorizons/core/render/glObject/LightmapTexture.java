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

package com.seibel.distanthorizons.core.render.glObject;

import org.lwjgl.opengl.GL32;

public class LightmapTexture
{
	public int id;
	
	public LightmapTexture()
	{
		id = GL32.glGenTextures();
		bind();
	}
	
	public void bind()
	{
		GL32.glBindTexture(GL32.GL_TEXTURE_2D, id);
	}
	public void unbind()
	{
		GL32.glBindTexture(GL32.GL_TEXTURE_2D, 0);
	}
	
	public void free()
	{
		GL32.glDeleteTextures(id);
	}
	
	// private int[] testArray;
	
	public void fillData(int lightMapWidth, int lightMapHeight, int[] pixels)
	{
		GL32.glDeleteTextures(id);
		id = GL32.glGenTextures();
		GL32.glBindTexture(GL32.GL_TEXTURE_2D, id);
		if (pixels.length != lightMapWidth * lightMapHeight)
			throw new RuntimeException("Lightmap Width*Height not equal to pixels provided!");
		
		// comment me out to see when the lightmap is changing
		/*
			boolean same = true;
			int badIndex = 0;
			if (testArray != null && pixels != null)
			for (int i = 0; i < pixels.length; i++)
			{
				if(pixels[i] != testArray[i])
				{
					same = false;
					badIndex = i;
					break;	
				}
			}
			testArray = pixels;
			MC.sendChatMessage(same + " " + badIndex);
		*/
		// comment this line out to prevent uploading the new lightmap
		GL32.glTexImage2D(GL32.GL_TEXTURE_2D, 0, GL32.GL_RGBA8, lightMapWidth * lightMapHeight,
				1, 0, GL32.GL_RGBA, GL32.GL_UNSIGNED_BYTE, pixels);
		GL32.glTexParameteri(GL32.GL_TEXTURE_2D, GL32.GL_TEXTURE_WRAP_S, GL32.GL_CLAMP_TO_BORDER);
		GL32.glTexParameteri(GL32.GL_TEXTURE_2D, GL32.GL_TEXTURE_WRAP_T, GL32.GL_CLAMP_TO_BORDER);
		GL32.glTexParameteri(GL32.GL_TEXTURE_2D, GL32.GL_TEXTURE_MIN_FILTER, GL32.GL_NEAREST);
		GL32.glTexParameteri(GL32.GL_TEXTURE_2D, GL32.GL_TEXTURE_MAG_FILTER, GL32.GL_NEAREST);
	}
	
}
