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

package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.StatsMap;
import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.util.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.*;

/**
 * Java representation of one or more OpenGL buffers for rendering.
 *
 * @see ColumnRenderBufferBuilder
 */
public class ColumnRenderBuffer implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper minecraftClient = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	private static final long MAX_BUFFER_UPLOAD_TIMEOUT_NANOSECONDS = 1_000_000;
	
	public static final int QUADS_BYTE_SIZE = LodUtil.LOD_VERTEX_FORMAT.getByteSize() * 4; // TODO what does the 4 represent
	public static final int MAX_QUADS_PER_BUFFER = (1024 * 1024 * 1) / QUADS_BYTE_SIZE; // TODO what do these multiples represent?
	public static final int FULL_SIZED_BUFFER = MAX_QUADS_PER_BUFFER * QUADS_BYTE_SIZE;
	
	
	
	
	public final DhBlockPos pos;
	
	public boolean buffersUploaded = false;
	
	private GLVertexBuffer[] vbos;
	private GLVertexBuffer[] vbosTransparent;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public ColumnRenderBuffer(DhBlockPos pos)
	{
		this.pos = pos;
		this.vbos = new GLVertexBuffer[0];
		this.vbosTransparent = new GLVertexBuffer[0];
	}
	
	
	
	
	
	//==================//
	// buffer uploading //
	//==================//
	
	/** Should be run on a DH thread. */
	public void uploadBuffer(LodQuadBuilder builder, EDhApiGpuUploadMethod gpuUploadMethod) throws InterruptedException
	{
		LodUtil.assertTrue(Thread.currentThread().getName().startsWith(ThreadUtil.THREAD_NAME_PREFIX), "Buffer uploading needs to be done on a DH thread to prevent locking up any MC threads.");
		
		
		// upload on MC's render thread
		CompletableFuture<Void> uploadFuture = new CompletableFuture<>();
		minecraftClient.executeOnRenderThread(() ->
		{
			try
			{
				this.uploadBuffersUsingUploadMethod(builder, gpuUploadMethod);
				uploadFuture.complete(null);
			}
			catch (InterruptedException e)
			{
				throw new CompletionException(e);
			}
		});
		
		
		try
		{
			// wait for the upload to finish
			uploadFuture.get(5_000, TimeUnit.MILLISECONDS);
		}
		catch (ExecutionException e)
		{
			LOGGER.warn("Error uploading builder ["+builder+"] synchronously. Error: "+e.getMessage(), e);
		}
		catch (TimeoutException e)
		{
			// timeouts can be ignored because it generally means the
			// MC Render thread executor was closed 
			//LOGGER.warn("Error uploading builder ["+builder+"] synchronously. Error: "+e.getMessage(), e);
		}
	}
	private void uploadBuffersUsingUploadMethod(LodQuadBuilder builder, EDhApiGpuUploadMethod gpuUploadMethod) throws InterruptedException
	{
		if (gpuUploadMethod.useEarlyMapping)
		{
			this.uploadBuffersMapped(builder, gpuUploadMethod);
		}
		else
		{
			this.uploadBuffersDirect(builder, gpuUploadMethod);
		}
		
		this.buffersUploaded = true;
	}
	
	
	
	private void uploadBuffersMapped(LodQuadBuilder builder, EDhApiGpuUploadMethod method)
	{
		// opaque vbos //
		
		this.vbos = ColumnRenderBufferBuilder.resizeBuffer(this.vbos, builder.getCurrentNeededOpaqueVertexBufferCount());
		for (int i = 0; i < this.vbos.length; i++)
		{
			if (this.vbos[i] == null)
			{
				this.vbos[i] = new GLVertexBuffer(method.useBufferStorage);
			}
		}
		LodQuadBuilder.BufferFiller func = builder.makeOpaqueBufferFiller(method);
		for (GLVertexBuffer vbo : this.vbos)
		{
			func.fill(vbo);
		}
		
		
		// transparent vbos //
		
		this.vbosTransparent = ColumnRenderBufferBuilder.resizeBuffer(this.vbosTransparent, builder.getCurrentNeededTransparentVertexBufferCount());
		for (int i = 0; i < this.vbosTransparent.length; i++)
		{
			if (this.vbosTransparent[i] == null)
			{
				this.vbosTransparent[i] = new GLVertexBuffer(method.useBufferStorage);
			}
		}
		LodQuadBuilder.BufferFiller transparentFillerFunc = builder.makeTransparentBufferFiller(method);
		for (GLVertexBuffer vbo : this.vbosTransparent)
		{
			transparentFillerFunc.fill(vbo);
		}
	}
	
	private void uploadBuffersDirect(LodQuadBuilder builder, EDhApiGpuUploadMethod method) throws InterruptedException
	{
		this.vbos = ColumnRenderBufferBuilder.resizeBuffer(this.vbos, builder.getCurrentNeededOpaqueVertexBufferCount());
		uploadBuffersDirect(this.vbos, builder.makeOpaqueVertexBuffers(), method);
		
		this.vbosTransparent = ColumnRenderBufferBuilder.resizeBuffer(this.vbosTransparent, builder.getCurrentNeededTransparentVertexBufferCount());
		uploadBuffersDirect(this.vbosTransparent, builder.makeTransparentVertexBuffers(), method);
	}
	private static void uploadBuffersDirect(GLVertexBuffer[] vbos, Iterator<ByteBuffer> iter, EDhApiGpuUploadMethod method) throws InterruptedException
	{
		long remainingMS = 0;
		long MBPerMS = Config.Client.Advanced.GpuBuffers.gpuUploadPerMegabyteInMilliseconds.get();
		int vboIndex = 0;
		while (iter.hasNext())
		{
			if (vboIndex >= vbos.length)
			{
				throw new RuntimeException("Too many vertex buffers!!");
			}
			
			
			// get or create the VBO
			if (vbos[vboIndex] == null)
			{
				vbos[vboIndex] = new GLVertexBuffer(method.useBufferStorage);
			}
			GLVertexBuffer vbo = vbos[vboIndex];
			
			
			ByteBuffer bb = iter.next();
			int size = bb.limit() - bb.position();
			
			try
			{
				vbo.bind();
				vbo.uploadBuffer(bb, size / LodUtil.LOD_VERTEX_FORMAT.getByteSize(), method, FULL_SIZED_BUFFER);
			}
			catch (Exception e)
			{
				vbos[vboIndex] = null;
				vbo.close();
				LOGGER.error("Failed to upload buffer: ", e);
			}
			
			
			if (MBPerMS > 0)
			{
				// upload buffers over an extended period of time
				// to hopefully prevent stuttering.
				remainingMS += size * MBPerMS;
				if (remainingMS >= TimeUnit.NANOSECONDS.convert(1000 / 60, TimeUnit.MILLISECONDS))
				{
					if (remainingMS > MAX_BUFFER_UPLOAD_TIMEOUT_NANOSECONDS)
					{
						remainingMS = MAX_BUFFER_UPLOAD_TIMEOUT_NANOSECONDS;
					}
					
					Thread.sleep(remainingMS / 1000000, (int) (remainingMS % 1000000));
					remainingMS = 0;
				}
			}
			
			vboIndex++;
		}
		
		if (vboIndex < vbos.length)
		{
			throw new RuntimeException("Too few vertex buffers!!");
		}
	}
	
	
	
	
	
	//========//
	// render //
	//========//
	
	/** @return true if something was rendered, false otherwise */
	public boolean renderOpaque(LodRenderer renderContext, DhApiRenderParam renderEventParam)
	{
		boolean hasRendered = false;
		renderContext.setModelViewMatrixOffset(this.pos, renderEventParam);
		for (GLVertexBuffer vbo : this.vbos)
		{
			if (vbo == null)
			{
				continue;
			}
			
			if (vbo.getVertexCount() == 0)
			{
				continue;
			}
			
			hasRendered = true;
			renderContext.drawVbo(vbo);
			//LodRenderer.tickLogger.info("Vertex buffer: {}", vbo);
		}
		return hasRendered;
	}
	
	/** @return true if something was rendered, false otherwise */
	public boolean renderTransparent(LodRenderer renderContext, DhApiRenderParam renderEventParam)
	{
		boolean hasRendered = false;
		
		try
		{
			// can throw an IllegalStateException if the GL program was freed before it should've been
			renderContext.setModelViewMatrixOffset(this.pos, renderEventParam);
			
			for (GLVertexBuffer vbo : this.vbosTransparent)
			{
				if (vbo == null)
				{
					continue;
				}
				
				if (vbo.getVertexCount() == 0)
				{
					continue;
				}
				
				hasRendered = true;
				renderContext.drawVbo(vbo);
				//LodRenderer.tickLogger.info("Vertex buffer: {}", vbo);
			}
		}
		catch (IllegalStateException e)
		{
			LOGGER.error("renderContext program doesn't exist for pos: "+this.pos, e);
		}
		
		return hasRendered;
	}
	
	
	
	//==============//
	// misc methods //
	//==============//
	
	/** can be used when debugging */
	public boolean hasNonNullVbos() { return this.vbos != null || this.vbosTransparent != null; }
	
	/** can be used when debugging */
	public int vboBufferCount() 
	{
		int count = 0;
		
		if (this.vbos != null)
		{
			count += this.vbos.length;
		}
		
		if (this.vbosTransparent != null)
		{
			count += this.vbosTransparent.length;
		}
		
		return count;
	}
	
	public void debugDumpStats(StatsMap statsMap)
	{
		statsMap.incStat("RenderBuffers");
		statsMap.incStat("SimpleRenderBuffers");
		for (GLVertexBuffer vertexBuffer : vbos)
		{
			if (vertexBuffer != null)
			{
				statsMap.incStat("VBOs");
				if (vertexBuffer.getSize() == FULL_SIZED_BUFFER)
				{
					statsMap.incStat("FullsizedVBOs");
				}
				
				if (vertexBuffer.getSize() == 0)
				{
					GLProxy.GL_LOGGER.warn("VBO with size 0");
				}
				statsMap.incBytesStat("TotalUsage", vertexBuffer.getSize());
			}
		}
	}
	
	/**
	 * This method is called when object is no longer in use.
	 * Called either after uploadBuffers() returned false (On buffer Upload
	 * thread), or by others when the object is not being used. (not in build,
	 * upload, or render state). 
	 */
	@Override
	public void close()
	{
		this.buffersUploaded = false;
		
		GLProxy.getInstance().queueRunningOnRenderThread(() ->
		{
			for (GLVertexBuffer buffer : this.vbos)
			{
				if (buffer != null)
				{
					buffer.destroyAsync();
				}
			}
			
			for (GLVertexBuffer buffer : this.vbosTransparent)
			{
				if (buffer != null)
				{
					buffer.destroyAsync();
				}
			}
		});
	}
	
}
