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

package com.seibel.distanthorizons.api.enums.rendering;

/**
 * BASIC                        <br>
 * IGNORE_HEIGHT                <br>
 * ADDITION                     <br>
 * MAX                          <br>
 * MULTIPLY                     <br>
 * INVERSE_MULTIPLY             <br>
 * LIMITED_ADDITION             <br>
 * MULTIPLY_ADDITION            <br>
 * INVERSE_MULTIPLY_ADDITION    <br>
 * AVERAGE                      <br>
 *
 * @author Leetom
 * @version 2024-4-6
 * @since API 2.0.0
 */
public enum EDhApiHeightFogMixMode
{
	BASIC,
	IGNORE_HEIGHT,
	ADDITION,
	MAX,
	MULTIPLY,
	INVERSE_MULTIPLY,
	LIMITED_ADDITION,
	MULTIPLY_ADDITION,
	INVERSE_MULTIPLY_ADDITION,
	AVERAGE,
}
