package com.blockstatesbackport.model;

import net.minecraft.client.render.texture.TextureAtlasSprite;

/**
 * One baked face: 4 vertices in block space (0..1) with atlas UVs, plus the 0..16 texture-space UVs so the
 * quad can be re-textured (block breaking overlay).
 */
public final class Quad {

	/** x, y, z, u, v per vertex. */
	public final float[] data = new float[20];
	/** u, v per vertex in texture space (0..16). */
	public final float[] local = new float[8];
	/** Directional shade baked into the vertex color (1 when shade is off). */
	public float shade = 1F;
	/** Facing computed from the geometry; drives lighting. */
	public int face;
	/** Cull direction or -1. */
	public int cull = -1;
	public int tint = -1;
	public TextureAtlasSprite sprite;
}
