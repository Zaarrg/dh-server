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

package com.seibel.distanthorizons.core.util.threading;

import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.concurrent.ThreadFactory;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Just a simple ThreadFactory to name ExecutorService
 * threads, which is helpful when debugging.
 */
public class DhThreadFactory implements ThreadFactory
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public final String threadName;
	public final int priority;
	private int threadCount = 0;
	private final LinkedList<WeakReference<Thread>> threads = new LinkedList<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DhThreadFactory(String newThreadName, int priority)
	{
		if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY)
		{
			throw new IllegalArgumentException("Thread priority [" + priority + "] out of bounds. Priority should be between [" + Thread.MIN_PRIORITY + "-" + Thread.MAX_PRIORITY + "]!");
		}
		
		this.threadName = ThreadUtil.THREAD_NAME_PREFIX + newThreadName + " Thread";
		this.priority = priority;
	}
	
	@Override
	public Thread newThread(@NotNull Runnable runnable)
	{
		Thread thread = new Thread(runnable, this.threadName + "[" + (this.threadCount++) + "]");
		thread.setPriority(this.priority);
		this.threads.add(new WeakReference<>(thread));
		return thread;
	}
	
	
	
	//===========//
	// debugging //
	//===========//
	
	private static String StackTraceToString(StackTraceElement[] stackTraceArray)
	{
		StringBuilder str = new StringBuilder();
		str.append(stackTraceArray[0]);
		str.append('\n');
		for (int i = 1; i < stackTraceArray.length; i++)
		{
			str.append("  at ");
			str.append(stackTraceArray[i]);
			str.append('\n');
		}
		return str.toString();
	}
	
	public void dumpAllThreadStacks()
	{
		for (WeakReference<Thread> threadRef : this.threads)
		{
			Thread thread = threadRef.get();
			if (thread != null)
			{
				StackTraceElement[] stacks = thread.getStackTrace();
				if (stacks.length != 0)
				{
					LOGGER.info("===========================================\n" +
							"Thread: " + thread.getName() + "\n" +
							StackTraceToString(stacks));
				}
			}
		}
		
		this.threads.removeIf((weakRef) -> weakRef.get() == null);
	}
	
}
