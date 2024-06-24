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

package com.seibel.distanthorizons.api.interfaces.config.client;

import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigGroup;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;

/**
 * Distant Horizons' OpenGL buffer configuration.
 *
 * @author James Seibel
 * @version 2023-6-14
 * @since API 1.0.0
 */
public interface IDhApiGpuBuffersConfig extends IDhApiConfigGroup
{
	
	/** Defines how geometry data is uploaded to the GPU. */
	IDhApiConfigValue<EDhApiGpuUploadMethod> gpuUploadMethod();
	
	/**
	 * Defines how long we should wait after uploading one
	 * Megabyte of geometry data to the GPU before uploading
	 * the next Megabyte of data. <br>
	 * This can be set to a non-zero number to reduce stuttering caused by
	 * uploading buffers to the GPU.
	 */
	IDhApiConfigValue<Integer> gpuUploadPerMegabyteInMilliseconds();
	
}
