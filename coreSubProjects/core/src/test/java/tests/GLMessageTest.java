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

package tests;

import com.seibel.distanthorizons.core.util.objects.GLMessage;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

public class GLMessageTest
{
	public static final String MESSAGE_ID = "0x20071";
	public static final GLMessage.ESource MESSAGE_SOURCE = GLMessage.ESource.API;
	public static final GLMessage.EType MESSAGE_TYPE = GLMessage.EType.OTHER;
	public static final GLMessage.ESeverity MESSAGE_SEVERITY = GLMessage.ESeverity.NOTIFICATION;
	public static final String MESSAGE = "Buffer detailed info: Buffer object 1014084 (bound to GL_ARRAY_BUFFER_ARB, usage hint is GL_STATIC_DRAW) will use VIDEO memory as\" \"the source for buffer object operations.";
	
	
	/** This is how debug messages were sent prior to Minecraft 1.20.2 */
	private static final String[] PRE_1_20_2_MESSAGE_ARRAY = 
			{
				 "[LWJGL] OpenGL debug message"
				,"ID", ":", "0x20071"
				,"Source", ":", "API"
				,"Type", ":", "OTHER"
				,"Severity", ":", "NOTIFICATION"
				,"Message", ":", "Buffer detailed info: Buffer object 1014084 (bound to GL_ARRAY_BUFFER_ARB, usage hint is GL_STATIC_DRAW) will use VIDEO memory as\" \"the source for buffer object operations."
				
				// optional addition to force the builder into noticing the message ended, shouldn't be necessary	
				//,"[LWJGL] OpenGL debug message"
			};
	
	/** This is how debug messages were sent after (and including) Minecraft 1.20.2 */
	private static final String[] POST_1_20_2_MESSAGE_ARRAY = 
			{
				 "[LWJGL] OpenGL debug message"
				,"ID: 0x20071"
				,"Source: API"
				,"Type: OTHER"
				,"Severity: NOTIFICATION"
				,"Message: Buffer detailed info: Buffer object 1014084 (bound to GL_ARRAY_BUFFER_ARB, usage hint is GL_STATIC_DRAW) will use VIDEO memory as\" \"the source for buffer object operations."
					
				// optional addition to force the builder into noticing the message ended, shouldn't be necessary	
				//,"[LWJGL] OpenGL debug message"
			};
	
	
	
	@Test
	public void preMc1_20_2()
	{
		ArrayList<GLMessage> messageList = new ArrayList<>();
		for (String str : PRE_1_20_2_MESSAGE_ARRAY)
		{
			GLMessage message = GLMessage.Builder.DEFAULT_MESSAGE_BUILDER.add(str);
			if (message != null)
			{
				messageList.add(message);
			}
		}
		
		//Assert.assertEquals("Incorrect message parse count.", 1, messageList.size());
		//testMessage(messageList.get(0));
	}
	
	@Test
	public void mc1_20_2()
	{
		// TODO
	}
	
	
	private static void messageMatchesExpected(GLMessage testMessage)
	{
		Assert.assertEquals(MESSAGE_ID, testMessage.id);
		Assert.assertEquals(MESSAGE_SOURCE, testMessage.source);
		Assert.assertEquals(MESSAGE_TYPE, testMessage.type);
		Assert.assertEquals(MESSAGE_SEVERITY, testMessage.severity);
		Assert.assertEquals(MESSAGE, testMessage.message);
	}
	
}
