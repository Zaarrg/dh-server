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

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Can be used to more finely control CPU usage and
 * reduce CPU usage if only 1 thread is already assigned.
 */
public class RateLimitedThreadPoolExecutor extends ThreadPoolExecutor
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	/** logs include the thread name by default which can help diagnose deadlocks */
	private static final boolean LOG_SEMAPHORE_ACTIONS = false;
	
	public volatile double runTimeRatio;
	
	/** When this thread started running its last task */
	private final ThreadLocal<Long> runStartNanoTimeRef = ThreadLocal.withInitial(() -> -1L);
	/** How long it took this thread to run its last task */
	private final ThreadLocal<Long> lastRunDurationNanoTimeRef = ThreadLocal.withInitial(() -> -1L);
	
	private Runnable onTerminatedEventHandler = null;
	
	/** if null the thread pool will run independently of other pools */
	@Nullable
	private final Semaphore activeThreadCountSemaphore;
	/** will always be zero if no semaphore is present */
	private final AtomicInteger semaphoresAcquired = new AtomicInteger(0);
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public RateLimitedThreadPoolExecutor(int corePoolSize, double runTimeRatio, ThreadFactory threadFactory) { this(corePoolSize, runTimeRatio, threadFactory, null); }
	public RateLimitedThreadPoolExecutor(int corePoolSize, double runTimeRatio, ThreadFactory threadFactory, @Nullable Semaphore activeThreadCountSemaphore)
	{
		super(corePoolSize, corePoolSize,
				0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<>(), // TODO using a PriorityBlockingQueue would be nice to allow for prioritizing tasks, but then all tasks must be Comparable
				threadFactory);
		
		this.runTimeRatio = runTimeRatio;
		this.activeThreadCountSemaphore = activeThreadCountSemaphore;
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	protected void beforeExecute(Thread thread, Runnable runnable)
	{
		super.beforeExecute(thread, runnable);
		
		if (this.runTimeRatio < 1.0 && this.lastRunDurationNanoTimeRef.get() != -1)
		{
			try
			{
				long deltaMs = TimeUnit.NANOSECONDS.toMillis(this.lastRunDurationNanoTimeRef.get());
				Thread.sleep((long) (deltaMs / this.runTimeRatio - deltaMs));
			}
			catch (InterruptedException ignored)
			{
			}
		}
		
		if (this.activeThreadCountSemaphore != null)
		{
			try
			{
				// Warning, this can cause deadlock if one thread calls another.
				this.activeThreadCountSemaphore.acquire();
				this.semaphoresAcquired.getAndAdd(1);
				
				if (LOG_SEMAPHORE_ACTIONS)
				{
					LOGGER.debug("acquired, available count: ["+this.activeThreadCountSemaphore.availablePermits()+"]");
				}
			}
			catch (InterruptedException ignore) { }
		}
		
		
		this.runStartNanoTimeRef.set(System.nanoTime());
	}
	
	@Override
	protected void afterExecute(Runnable runnable, Throwable throwable)
	{
		super.afterExecute(runnable, throwable);
		this.lastRunDurationNanoTimeRef.set(System.nanoTime() - this.runStartNanoTimeRef.get());
		
		
		if (this.activeThreadCountSemaphore != null)
		{
			this.activeThreadCountSemaphore.release();
			this.semaphoresAcquired.getAndAdd(-1);
			
			if (LOG_SEMAPHORE_ACTIONS)
			{
				LOGGER.debug("released, available count: ["+this.activeThreadCountSemaphore.availablePermits()+"]");
			}
		}
	}
	
	@Override
	protected void terminated() 
	{
		super.terminated();
		if (this.onTerminatedEventHandler != null)
		{
			this.onTerminatedEventHandler.run();
		}
		
		// release all held semaphores (shouldn't normally be necessary, but just in case)
		if (this.activeThreadCountSemaphore != null)
		{
			int semaphoresAcquired = this.semaphoresAcquired.getAndSet(0);
			this.activeThreadCountSemaphore.release(semaphoresAcquired);
			
			if (LOG_SEMAPHORE_ACTIONS)
			{
				LOGGER.info("terminated, released ["+semaphoresAcquired+"], available count: ["+this.activeThreadCountSemaphore.availablePermits()+"]");
			}
		}
	}
	
	
	
	//==============//
	// custom logic //
	//==============//
	
	/** only one event handler can be present at a time */
	public void setOnTerminatedEventHandler(Runnable runnable) { this.onTerminatedEventHandler = runnable; }
	
}