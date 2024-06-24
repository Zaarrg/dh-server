package com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject;

#if MC_VER >= MC_1_21

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.ChunkPos;

public class DhGenerationChunkHolder extends GenerationChunkHolder
{
	
	public DhGenerationChunkHolder(ChunkPos pos)
	{
		super(pos);
	}
	
	@Override
	public int getTicketLevel() { return 0; }
	@Override
	public int getQueueLevel() { return 0; }
	
}

#endif
