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

package com.seibel.distanthorizons.core.logging.f3;

import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.util.*;
import java.util.function.Supplier;

public class F3Screen
{
	private static final Logger LOGGER = LogManager.getLogger();
	
	private static final String[] DEFAULT_STRING = {
			"", // blank line for spacing
			ModInfo.READABLE_NAME + " version: " + ModInfo.VERSION
	};
	private static final List<Message> SELF_UPDATE_MESSAGE_LIST = Collections.synchronizedList(new LinkedList<>());
	
	public static void addStringToDisplay(List<String> list)
	{
		list.addAll(Arrays.asList(DEFAULT_STRING));
		synchronized (SELF_UPDATE_MESSAGE_LIST)
		{
			Iterator<Message> iterator = SELF_UPDATE_MESSAGE_LIST.iterator();
			while (iterator.hasNext())
			{
				Message message = iterator.next();
				if (message == null)
				{
					iterator.remove();
				}
				else
				{
					message.printTo(list);
				}
			}
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	// we are using Closeable instead of AutoCloseable because the close method should never throw exceptions
	// and because this class shouldn't be used in a try {} block.
	public static abstract class Message implements Closeable
	{
		protected Message()
		{
			SELF_UPDATE_MESSAGE_LIST.add(this);
		}
		
		public abstract void printTo(List<String> output);
		
		@Override
		public void close()
		{
			boolean removed = SELF_UPDATE_MESSAGE_LIST.remove(this);
		}
		
	}
	
	public static class StaticMessage extends Message
	{
		private final String[] lines;
		
		public StaticMessage(String... lines) { this.lines = lines; }
		
		@Override
		public void printTo(List<String> output) { output.addAll(Arrays.asList(this.lines)); }
		
	}
	
	public static class DynamicMessage extends Message
	{
		private final Supplier<String> supplier;
		
		public DynamicMessage(Supplier<String> message) { this.supplier = message; }
		
		public void printTo(List<String> list)
		{
			
			try
			{
				String message = this.supplier.get();
				if (message != null)
				{
					list.add(message);
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected Exception in F3 ["+DynamicMessage.class.getSimpleName()+"], error: "+e.getMessage(), e);
			}
		}
		
	}
	
	public static class MultiDynamicMessage extends Message
	{
		private final Supplier<String>[] supplierList;
		
		@SafeVarargs
		public MultiDynamicMessage(Supplier<String>... suppliers) { this.supplierList = suppliers; }
		
		public void printTo(List<String> list)
		{
			for (Supplier<String> supplier : this.supplierList)
			{
				try
				{
					String message = supplier.get();
					if (message != null)
					{
						list.add(message);
					}
				}
				catch (Exception e)
				{
					LOGGER.error("Unexpected Exception in F3 ["+DynamicMessage.class.getSimpleName()+"], error: "+e.getMessage(), e);
				}
			}
		}
		
	}
	
	public static class NestedMessage extends Message
	{
		private final Supplier<String[]> supplier;
		
		public NestedMessage(Supplier<String[]> message)
		{
			this.supplier = message;
		}
		
		public void printTo(List<String> list)
		{
			try
			{
				String[] message = this.supplier.get();
				if (message != null)
				{
					list.addAll(Arrays.asList(message));
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected Exception in F3 ["+DynamicMessage.class.getSimpleName()+"], error: "+e.getMessage(), e);
			}
		}
		
	}
	
}
