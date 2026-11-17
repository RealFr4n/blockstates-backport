package com.blockstatesbackport.render;

/** Implemented on RenderChunk by RenderChunkMixin. */
public interface CutoutChunk {

	/** The chunk's CUTOUT display list, or -1 if it has none. */
	int bsb$cutoutList();
}
