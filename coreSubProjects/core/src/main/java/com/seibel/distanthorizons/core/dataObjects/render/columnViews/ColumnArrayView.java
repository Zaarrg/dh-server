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

package com.seibel.distanthorizons.core.dataObjects.render.columnViews;


import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Arrays;

public final class ColumnArrayView implements IColumnDataView
{
	public final LongArrayList data;
	public final int size;
	public final int offset; // offset in longs
	/** can be 0 if this column was created for an empty data source */
	public final int vertSize; // vertical size in longs
	
	
	
	public ColumnArrayView(LongArrayList data, int size, int offset, int vertSize)
	{
		this.data = data;
		this.size = size;
		this.offset = offset;
		this.vertSize = vertSize;
	}
	
	
	
	@Override
	public long get(int index) { return data.getLong(index + offset); }
	
	public void set(int index, long value) { data.set(index + offset, value); }
	
	@Override
	public int size() { return size; }
	
	@Override
	public int verticalSize() { return vertSize; }
	
	@Override
	public int dataCount() { return (this.vertSize != 0) ? (this.size / this.vertSize) : 0; }
	
	@Override
	public ColumnArrayView subView(int dataIndexStart, int dataCount)
	{
		return new ColumnArrayView(data, dataCount * vertSize, offset + dataIndexStart * vertSize, vertSize);
	}
	
	public void fill(long value) { Arrays.fill(data.elements(), offset, offset + size, value); }
	
	public void copyFrom(IColumnDataView source) { copyFrom(source, 0); }
	public void copyFrom(IColumnDataView source, int outputDataIndexOffset)
	{
		if (source.verticalSize() > vertSize)
		{
			throw new IllegalArgumentException("source verticalSize must be <= self's verticalSize to copy");
		}
		else if (source.dataCount() + outputDataIndexOffset > dataCount())
		{
			throw new IllegalArgumentException("dataIndexStart + source.dataCount() must be <= self.dataCount() to copy");
		}
		else if (source.verticalSize() != vertSize)
		{
			for (int i = 0; i < source.dataCount(); i++)
			{
				int outputOffset = offset + outputDataIndexOffset * vertSize + i * vertSize;
				source.subView(i, 1).copyTo(data.elements(), outputOffset, source.verticalSize());
				Arrays.fill(data.elements(), outputOffset + source.verticalSize(),
						outputOffset + vertSize, 0);
			}
		}
		else
		{
			source.copyTo(data.elements(), offset + outputDataIndexOffset * vertSize, source.size());
		}
	}
	
	@Override
	public void copyTo(long[] target, int offset, int size) { System.arraycopy(data.elements(), this.offset, target, offset, size); }
	
	public boolean mergeWith(ColumnArrayView source, boolean override)
	{
		if (size != source.size)
		{
			throw new IllegalArgumentException("Cannot merge views of different sizes");
		}
		if (vertSize != source.vertSize)
		{
			throw new IllegalArgumentException("Cannot merge views of different vertical sizes");
		}
		boolean anyChange = false;
		for (int o = 0; o < (source.size() * vertSize); o += vertSize)
		{
			if (override)
			{
				if (RenderDataPointUtil.compareDatapointPriority(source.get(o), get(o)) >= 0)
				{
					anyChange = true;
					System.arraycopy(source.data, source.offset + o, data, offset + o, vertSize);
				}
			}
			else
			{
				if (RenderDataPointUtil.compareDatapointPriority(source.get(o), get(o)) > 0)
				{
					anyChange = true;
					System.arraycopy(source.data, source.offset + o, data, offset + o, vertSize);
				}
			}
		}
		return anyChange;
	}
	
	public void changeVerticalSizeFrom(IColumnDataView source)
	{
		if (this.dataCount() != source.dataCount())
		{
			throw new IllegalArgumentException("Cannot copy and resize to views with different dataCounts");
		}
		
		if (this.vertSize >= source.verticalSize())
		{
			this.copyFrom(source);
		}
		else
		{
			for (int i = 0; i < this.dataCount(); i++)
			{
				RenderDataPointUtil.mergeMultiData(source.subView(i, 1), subView(i, 1));
			}
		}
	}
	
	public void mergeMultiDataFrom(IColumnDataView source)
	{
		if (dataCount() != 1)
		{
			throw new IllegalArgumentException("output dataCount must be 1");
		}
		
		RenderDataPointUtil.mergeMultiData(source, this);
	}
	
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("S:").append(size);
		sb.append(" V:").append(vertSize);
		sb.append(" O:").append(offset);
		
		sb.append(" [");
		for (int i = 0; i < size; i++)
		{
			sb.append(RenderDataPointUtil.toString(data.getLong(offset + i)));
			if (i < size - 1)
			{
				sb.append(",\n");
			}
		}
		sb.append("]");
		
		return sb.toString();
	}
	
	public int getDataHash()
	{
		return arrayHash(data, offset, size);
	}
	
	private static int arrayHash(LongArrayList a, int offset, int length)
	{
		if (a == null)
		{
			return 0;
		}
		
		int result = 1;
		int end = offset + length;
		for (int i = offset; i < end; i++)
		{
			long element = a.getLong(i);
			int elementHash = (int) (element ^ (element >>> 32));
			result = 31 * result + elementHash;
		}
		return result;
	}
	
}