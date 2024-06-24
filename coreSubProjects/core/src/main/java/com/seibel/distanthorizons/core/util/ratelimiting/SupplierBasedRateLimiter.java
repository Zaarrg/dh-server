package com.seibel.distanthorizons.core.util.ratelimiting;

import com.google.common.util.concurrent.RateLimiter;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Limits rate of tasks based on given current limit supplier. <br>
 * If rate limit was exceeded, acquisitions will fail and the provided failure handler will be called instead.
 * @param <T> Type of the object used as context for failure handler.
 */
@SuppressWarnings("UnstableApiUsage")
public class SupplierBasedRateLimiter<T>
{
	private final Supplier<Integer> maxRateSupplier;
	private final Consumer<T> onFailureConsumer;
	
	private final RateLimiter rateLimiter = RateLimiter.create(Double.POSITIVE_INFINITY);
	
	public SupplierBasedRateLimiter(Supplier<Integer> maxRateSupplier) { this(maxRateSupplier, ignored -> { }); }
	public SupplierBasedRateLimiter(Supplier<Integer> maxRateSupplier, Consumer<T> onFailureConsumer)
	{
		this.maxRateSupplier = maxRateSupplier;
		this.onFailureConsumer = onFailureConsumer;
	}
	
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean tryAcquire(T context)
	{
		return this.tryAcquire(context, 1);
	}
	
	public boolean tryAcquire()
	{
		return this.tryAcquire(null, 1);
	}
	
	public int acquireOrDrain(int permits)
	{
		this.rateLimiter.setRate(this.maxRateSupplier.get());
		
		if (this.rateLimiter.tryAcquire(permits))
		{
			return permits;
		}
		
		int acquired = 0;
		while ((permits /= 2) > 0)
		{
			if (this.rateLimiter.tryAcquire(permits))
			{
				acquired += permits;
			}
		}
		
		return acquired;
	}
	
	public boolean tryAcquire(T context, int permits)
	{
		this.rateLimiter.setRate(this.maxRateSupplier.get());
		
		if (!this.rateLimiter.tryAcquire(permits))
		{
			this.onFailureConsumer.accept(context);
			return false;
		}
		
		return true;
	}
}
