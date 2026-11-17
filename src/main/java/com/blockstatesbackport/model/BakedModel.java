package com.blockstatesbackport.model;

import net.minecraft.client.render.texture.TextureAtlasSprite;

/**
 * Quads of one model under one rotation, grouped by cull face (index 6 = never culled).
 */
public final class BakedModel {

	public static final Quad[] NO_QUADS = new Quad[0];

	public final Quad[][] quads = new Quad[7][];
	public boolean ambientOcclusion = true;
	public TextureAtlasSprite particle;
	/** Item display transforms (by slot name), from the model chain. */
	public java.util.Map<String, Defs.Transform> display = java.util.Collections.emptyMap();
	/** True when the chain ends in builtin/generated (flat item model). */
	public boolean generated;

	public boolean isEmpty() {
		for (Quad[] q : quads) {
			if (q.length > 0) {
				return false;
			}
		}
		return true;
	}
}
