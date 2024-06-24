package com.seibel.distanthorizons.core.multiplayer.client;

import com.google.common.base.Stopwatch;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.ConfigBasedSpamLogger;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.network.exceptions.InvalidLevelException;
import com.seibel.distanthorizons.core.network.exceptions.RateLimitedException;
import com.seibel.distanthorizons.core.network.exceptions.RequestRejectedException;
import com.seibel.distanthorizons.core.network.messages.plugin.fullData.FullDataSourceRequestMessage;
import com.seibel.distanthorizons.core.network.messages.plugin.fullData.FullDataSourceResponseMessage;
import com.seibel.distanthorizons.core.pos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.ratelimiting.SupplierBasedRateLimiter;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import io.netty.channel.ChannelException;
import org.apache.logging.log4j.LogManager;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class AbstractFullDataRequestQueue implements IDebugRenderable, AutoCloseable
{
	private static final ConfigBasedSpamLogger LOGGER = new ConfigBasedSpamLogger(LogManager.getLogger(),
			() -> Config.Client.Advanced.Logging.logNetworkEvent.get(), 3);
	
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	protected static final long SHUTDOWN_TIMEOUT_SECONDS = 5;
	
	public final ClientNetworkState networkState;
	protected final IDhClientLevel level;
	private final boolean changedOnly;
	
	private volatile CompletableFuture<Void> closingFuture = null;
	
	protected final ConcurrentMap<Long, RequestQueueEntry> waitingTasks = new ConcurrentHashMap<>();
	private final Semaphore pendingTasksSemaphore = new Semaphore(Short.MAX_VALUE, true);
	
	private final F3Screen.NestedMessage f3Message = new F3Screen.NestedMessage(this::f3Log);
	private final AtomicInteger finishedRequests = new AtomicInteger();
	private final AtomicInteger failedRequests = new AtomicInteger();
	private final ConfigEntry<Boolean> showDebugWireframeConfig;
	
	private final SupplierBasedRateLimiter<Void> rateLimiter = new SupplierBasedRateLimiter<>(this::getRequestConcurrencyLimit);
	
	private final ScheduledExecutorService taskFinishScheduler = Executors.newScheduledThreadPool(1);
	
	
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	protected boolean showInDebug() { return true; }
	
	protected abstract int getRequestConcurrencyLimit();
	
	protected abstract String getQueueName();
	
	protected double getPriorityDistanceRatio() { return 1; }
	
	
	public AbstractFullDataRequestQueue(ClientNetworkState networkState, IDhClientLevel level, boolean changedOnly, ConfigEntry<Boolean> showDebugWireframeConfig)
	{
		this.networkState = networkState;
		this.level = level;
		this.changedOnly = changedOnly;
		this.showDebugWireframeConfig = showDebugWireframeConfig;
		DebugRenderer.register(this, this.showDebugWireframeConfig);
	}
	
	public CompletableFuture<Boolean> submitRequest(long sectionPos, Consumer<FullDataSourceV2> chunkDataConsumer)
	{
		return this.submitRequest(sectionPos, null, chunkDataConsumer);
	}
	public CompletableFuture<Boolean> submitRequest(long sectionPos, @Nullable Long clientTimestamp, Consumer<FullDataSourceV2> chunkDataConsumer)
	{
		LodUtil.assertTrue(DhSectionPos.getDetailLevel(sectionPos) == DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL, "Only highest-detail sections are allowed.");
		
		RequestQueueEntry entry = new RequestQueueEntry(chunkDataConsumer, clientTimestamp);
		this.waitingTasks.put(sectionPos, entry);
		return entry.future;
	}
	
	protected int posDistanceSquared(DhBlockPos2D targetPos, long pos)
	{
		return (int) DhSectionPos.getCenterBlockPos(pos).distSquared(targetPos);
	}
	
	public synchronized boolean tick(DhBlockPos2D targetPos)
	{
		if (this.closingFuture != null || !this.networkState.isReady())
		{
			return false;
		}
		
		while (this.getWaitingTaskCount() > this.getInProgressTaskCount()
				&& this.getInProgressTaskCount() < this.getRequestConcurrencyLimit()
				&& this.pendingTasksSemaphore.tryAcquire())
		{
			if (!this.rateLimiter.tryAcquire())
			{
				this.pendingTasksSemaphore.release();
				break;
			}
			
			this.sendNewRequest(targetPos);
		}
		
		return true;
	}
	
	public void removeRetrievalRequestIf(DhSectionPos.ICancelablePrimitiveLongConsumer removeIf)
	{
		for (Map.Entry<Long, RequestQueueEntry> mapEntry : this.waitingTasks.entrySet())
		{
			long pos = mapEntry.getKey();
			RequestQueueEntry entry = mapEntry.getValue();
			
			if (removeIf.accept(pos))
			{
				LOGGER.debug("Removing request  " + mapEntry.getKey() + "...");
				
				entry.future.cancel(false);
				if (entry.request != null)
				{
					entry.request.cancel(false);
				}
			}
		}
	}
	
	private void sendNewRequest(DhBlockPos2D targetPos)
	{
		Map.Entry<Long, RequestQueueEntry> mapEntry = this.waitingTasks.entrySet().stream()
				.filter(task -> task.getValue().request == null)
				.reduce(null, (a, b) -> {
					if (a == null)
					{
						return b;
					}
					
					if (b.getValue().priority < a.getValue().priority)
					{
						Map.Entry<Long, RequestQueueEntry> temp = b;
						b = a;
						a = temp;
					}
					
					double distanceRatio = Math.sqrt(this.posDistanceSquared(targetPos, b.getKey())) / Math.sqrt(this.posDistanceSquared(targetPos, a.getKey()));
					double maxDistanceRatioScaled = Math.pow(this.getPriorityDistanceRatio(), b.getValue().priority - a.getValue().priority);
					
					return distanceRatio < maxDistanceRatioScaled ? b : a;
				});
		
		if (mapEntry == null)
		{
			this.pendingTasksSemaphore.release();
			return;
		}
		
		long sectionPos = mapEntry.getKey();
		RequestQueueEntry entry = mapEntry.getValue();
		
		CompletableFuture<FullDataSourceResponseMessage> request = this.networkState.getSession().sendRequest(
				new FullDataSourceRequestMessage(this.level.getLevelWrapper(), sectionPos, entry.updateTimestamp),
				FullDataSourceResponseMessage.class
		);
		entry.request = request;
		request.handleAsync((response, throwable) ->
		{
			this.pendingTasksSemaphore.release();
			this.finishedRequests.incrementAndGet();
			
			try
			{
				this.waitingTasks.remove(sectionPos);
				
				if (throwable != null)
				{
					throw throwable;
				}
				
				if (response.dataSourceDto != null)
				{
					FullDataSourceV2 fullDataSource = response.dataSourceDto.createPooledDataSource(this.level.getLevelWrapper());
					entry.chunkDataConsumer.accept(fullDataSource);
					FullDataSourceV2.DATA_SOURCE_POOL.returnPooledDataSource(fullDataSource);
				}
				else
				{
					LodUtil.assertTrue(this.changedOnly, "Received empty data source response for not changed-only request");
				}
			}
			catch (InvalidLevelException | RequestRejectedException ignored)
			{
				// We're too late / some cases might trigger a bunch of expected rejections
			}
			catch (ChannelException | RateLimitedException e)
			{
				if (e instanceof RateLimitedException)
				{
					LOGGER.warn("Rate limited by server, re-queueing task [" + sectionPos + "]: " + e.getMessage());
				}
				
				entry.request = null;
				this.finishedRequests.decrementAndGet();
			}
			catch (CancellationException ignored)
			{
				this.finishedRequests.decrementAndGet();
			}
			catch (Throwable e)
			{
				LOGGER.error("Error while fetching full data source", e);
				this.failedRequests.incrementAndGet();
				return entry.future.complete(false);
			}
			
			// Hack to work around a race condition
			// If you finish the request too quickly, the section will never render
			this.taskFinishScheduler.schedule(() -> {
					entry.future.complete(true);
			}, 10, TimeUnit.SECONDS);
			return null;
		});
	}
	
	private String[] f3Log()
	{
		if (!this.showInDebug())
		{
			return new String[0];
		}
		
		return new String[]{
				this.getQueueName() + " [" + this.level.getClientLevelWrapper().getDimensionType().getDimensionName() + "]",
				"Requests: " + this.finishedRequests + " / " + (this.getWaitingTaskCount() + this.finishedRequests.get()) + " (failed: " + this.failedRequests + ", rate limit: " + this.getRequestConcurrencyLimit() + ")"
		};
	}
	
	
	public int getWaitingTaskCount() { return this.waitingTasks.size(); }
	
	public int getInProgressTaskCount() { return Short.MAX_VALUE - this.pendingTasksSemaphore.availablePermits(); }
	
	
	public CompletableFuture<Void> startClosing(boolean alsoInterruptRunning)
	{
		return this.closingFuture = CompletableFuture.runAsync(() -> {
			Stopwatch stopwatch = Stopwatch.createStarted();
			
			do
			{
				for (RequestQueueEntry entry : this.waitingTasks.values())
				{
					entry.future.cancel(alsoInterruptRunning);
					if (entry.request != null && entry.request.cancel(alsoInterruptRunning))
					{
						this.pendingTasksSemaphore.release();
					}
				}
			}
			while (!this.pendingTasksSemaphore.tryAcquire(Short.MAX_VALUE) && stopwatch.elapsed(TimeUnit.SECONDS) < SHUTDOWN_TIMEOUT_SECONDS);
			
			if (stopwatch.elapsed(TimeUnit.SECONDS) >= SHUTDOWN_TIMEOUT_SECONDS)
			{
				LOGGER.warn(this.getQueueName() + " for " + this.level.getLevelWrapper() + " did not shutdown in " + SHUTDOWN_TIMEOUT_SECONDS + " seconds! Some unfinished tasks might be left hanging.");
			}
		});
	}
	
	@Override
	public void close()
	{
		this.f3Message.close();
		DebugRenderer.unregister(this, this.showDebugWireframeConfig);
	}
	
	@Override
	public void debugRender(DebugRenderer r)
	{
		if (!this.showInDebug())
		{
			return;
		}
		
		if (MC_CLIENT.getWrappedClientLevel() != this.level.getClientLevelWrapper())
		{
			return;
		}
		
		for (Map.Entry<Long, RequestQueueEntry> mapEntry : this.waitingTasks.entrySet())
		{
			r.renderBox(new DebugRenderer.Box(mapEntry.getKey(), -32f, 64f, 0.05f,
					mapEntry.getValue().request != null ? Color.red
							: mapEntry.getValue().priority == 3 ? Color.orange
							: mapEntry.getValue().priority == 2 ? Color.cyan
							: mapEntry.getValue().priority == 1 ? Color.blue
							: Color.gray
			));
		}
	}
	
	protected static class RequestQueueEntry
	{
		public final CompletableFuture<Boolean> future = new CompletableFuture<>();
		public final Consumer<FullDataSourceV2> chunkDataConsumer;
		@Nullable
		public final Long updateTimestamp;
		
		// Higher value = higher priority.
		// Priority of 0 is reserved for unassigned value
		public int priority = 0;
		@CheckForNull
		public CompletableFuture<?> request;
		
		public RequestQueueEntry(
				Consumer<FullDataSourceV2> chunkDataConsumer,
				@Nullable Long updateTimestamp)
		{
			this.chunkDataConsumer = chunkDataConsumer;
			this.updateTimestamp = updateTimestamp;
		}
		
	}
	
}