package com.seibel.distanthorizons.core.util.ratelimiting;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SupplierBasedRateAndConcurrencyLimiter<T>
{
	private final SupplierBasedRateLimiter<T> rateLimiter;
	private final SupplierBasedConcurrencyLimiter<T> concurrencyLimiter;
	
	public SupplierBasedRateAndConcurrencyLimiter(Supplier<Integer> maxRateSupplier, Consumer<T> onFailureConsumer)
	{
		this.rateLimiter = new SupplierBasedRateLimiter<>(maxRateSupplier, onFailureConsumer);
		this.concurrencyLimiter = new SupplierBasedConcurrencyLimiter<>(maxRateSupplier, onFailureConsumer);
	}
	
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean tryAcquire(T context)
	{
		if (!this.concurrencyLimiter.tryAcquire(context))
		{
			return false;
		}
		
		if (!this.rateLimiter.tryAcquire(context))
		{
			this.concurrencyLimiter.release();
			return false;
		}
		
		return true;
	}
	
	public void release()
	{
		this.concurrencyLimiter.release();
	}
	
}
