package com.seibel.distanthorizons.core.util.ratelimiting;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Limits concurrent tasks based on given current limit supplier. <br>
 * If limit of concurrent tasks was exceeded, acquisitions will fail and the provided failure handler will be called instead.
 * @param <T> Type of the object used as context for failure handler.
 */
public class SupplierBasedConcurrencyLimiter<T>
{
	private final Supplier<Integer> maxConcurrentTasksSupplier;
	private final Consumer<T> onFailureConsumer;
	
	private final AtomicInteger pendingTasks = new AtomicInteger();
	
	public SupplierBasedConcurrencyLimiter(Supplier<Integer> maxConcurrentTasksSupplier, Consumer<T> onFailureConsumer)
	{
		this.maxConcurrentTasksSupplier = maxConcurrentTasksSupplier;
		this.onFailureConsumer = onFailureConsumer;
	}
	
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean tryAcquire(T context)
	{
		if (this.pendingTasks.incrementAndGet() > this.maxConcurrentTasksSupplier.get())
		{
			this.pendingTasks.decrementAndGet();
			this.onFailureConsumer.accept(context);
			return false;
		}
		
		return true;
	}
	
	public void release()
	{
		this.pendingTasks.decrementAndGet();
	}
}
