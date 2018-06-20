/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.farmingtracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;
import net.runelite.api.VarPlayer;

@Getter
@RequiredArgsConstructor
public class Birdhouse implements Timeable
{
	private final String name;
	private final VarPlayer varp;

	@Override
	public String getCategory()
	{
		return "";
	}

	private static final int[] BIRDHOUSES = new int[]
		{
			ItemID.BIRD_HOUSE,
			ItemID.OAK_BIRD_HOUSE,
			ItemID.WILLOW_BIRD_HOUSE,
			ItemID.TEAK_BIRD_HOUSE,
			ItemID.MAPLE_BIRD_HOUSE,
			ItemID.MAHOGANY_BIRD_HOUSE,
			ItemID.YEW_BIRD_HOUSE,
			ItemID.MAGIC_BIRD_HOUSE,
			ItemID.REDWOOD_BIRD_HOUSE,
		};

	public int getItemID(int varpValue)
	{
		if (varpValue < 1 || varpValue > BIRDHOUSES.length * 3 + 1)
		{
			return -1;
		}
		return BIRDHOUSES[(varpValue - 1) / 3];
	}

	public BirdhouseState getState(int varpValue)
	{
		if (varpValue < 1)
		{
			return BirdhouseState.UNSET;
		}
		if (varpValue > BIRDHOUSES.length * 3 + 1)
		{
			return null;
		}

		if ((varpValue - 1) % 3 != 2)
		{
			return BirdhouseState.EMPTY;
		}
			else
		{
			return BirdhouseState.CATCHING;
		}
	}

	@Override
	public String toString()
	{
		return name;
	}
}
