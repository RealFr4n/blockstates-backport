package com.blockstatesbackport.render;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.Sprite;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.world.WorldView;

import com.blockstatesbackport.ModelManager;
import com.blockstatesbackport.model.BakedModel;
import com.blockstatesbackport.model.Dir;
import com.blockstatesbackport.model.Quad;
import com.blockstatesbackport.model.StateModel;

/**
 * Draws baked quads into the 1.7.10 Tesselator with 1.12 lighting rules (smooth and flat), tints and plant offsets.
 * Single-threaded like 1.7.10 chunk building, so scratch buffers are shared.
 */
public final class ModelRenderer {

	/** The two axes sampled for smooth light on each face, as {first, opposite, second, opposite}. */
	private static final int[][] SIDES = {
		{Dir.WEST, Dir.EAST, Dir.NORTH, Dir.SOUTH},
		{Dir.EAST, Dir.WEST, Dir.NORTH, Dir.SOUTH},
		{Dir.UP, Dir.DOWN, Dir.EAST, Dir.WEST},
		{Dir.WEST, Dir.EAST, Dir.DOWN, Dir.UP},
		{Dir.UP, Dir.DOWN, Dir.NORTH, Dir.SOUTH},
		{Dir.DOWN, Dir.UP, Dir.NORTH, Dir.SOUTH},
	};
	private static final int[][][] CORNERS = {
		{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
		{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
		{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
		{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
		{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
		{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}},
	};

	private static final float[] BRIGHT = new float[4];
	private static final int[] LIGHT = new int[4];
	private static final float[] BOX = new float[6];
	/** Corner samples as (first-axis side, second-axis side) index pairs into SIDES. */
	private static final int[][] PAIRS = {{0, 3}, {0, 2}, {1, 2}, {1, 3}};
	private static final int[] S_LIGHT = new int[4];
	private static final float[] S_OCC = new float[4];
	private static final boolean[] S_OPEN = new boolean[4];
	private static final float[] D_OCC = new float[4];
	private static final int[] D_LIGHT = new int[4];
	private static final float[] C_OCC = new float[4];
	private static final int[] C_LIGHT = new int[4];

	private ModelRenderer() {
	}

	/** Offset type of 1.12 plants: 0 none, 1 XZ, 2 XYZ. */
	private static int offsetType(String name) {
		switch (name) {
			case "minecraft:tallgrass":
				return 2;
			case "minecraft:yellow_flower":
			case "minecraft:red_flower":
			case "minecraft:double_plant":
				return 1;
			default:
				return 0;
		}
	}

	public static boolean render(WorldView w, Block block, String name, int x, int y, int z, int meta, StateModel model,
			Sprite forced, boolean renderAll) {
		block.updateShape(w, x, y, z);
		boolean ao = forced == null && Minecraft.isAmbientOcclusionEnabled() && block.getLight() == 0 && model.ambientOcclusion;
		long seed = StateModel.seed(x, y, z);
		double ox = x, oy = y, oz = z;
		int offset = offsetType(name);
		if (offset != 0) {
			long l = StateModel.seed(x, 0, z);
			ox += ((l >> 16 & 15L) / 15.0F - 0.5D) * 0.5D;
			oz += ((l >> 24 & 15L) / 15.0F - 0.5D) * 0.5D;
			if (offset == 2) {
				oy += ((l >> 20 & 15L) / 15.0F - 1.0D) * 0.2D;
			}
		}
		Tesselator t = Tesselator.INSTANCE;
		int tint = -1;
		boolean drew = false;
		for (int side = 0; side < 7; side++) {
			boolean checked = false;
			int faceLight = 0;
			for (StateModel.Choice part : model.parts) {
				BakedModel m = part.pick(seed);
				Quad[] quads = m.quads[side];
				if (quads.length == 0) {
					continue;
				}
				if (side < 6 && !checked) {
					if (!renderAll && !block.shouldRenderFace(w, x + Dir.X[side], y + Dir.Y[side], z + Dir.Z[side], side)) {
						break;
					}
					checked = true;
					faceLight = block.getLightColor(w, x + Dir.X[side], y + Dir.Y[side], z + Dir.Z[side]);
				}
				for (Quad q : quads) {
					if (q.tint >= 0 && tint == -1 && forced == null) {
						tint = Tints.color(block, name, w, x, y, z, ModelManager.key(w, block, x, y, z, meta));
					}
					emit(t, w, block, x, y, z, q, ao, side < 6, faceLight, q.tint >= 0 && forced == null ? tint : 0xFFFFFF,
						forced, ox, oy, oz);
					drew = true;
				}
			}
		}
		return drew;
	}

	private static void emit(Tesselator t, WorldView w, Block block, int x, int y, int z, Quad q, boolean ao, boolean culled,
			int faceLight, int tint, Sprite forced, double ox, double oy, double oz) {
		float r = (tint >> 16 & 255) / 255F * q.shade;
		float g = (tint >> 8 & 255) / 255F * q.shade;
		float b = (tint & 255) / 255F * q.shade;
		float[] d = q.data;
		if (ao) {
			smooth(w, block, x, y, z, q);
		} else {
			int light = culled ? faceLight : flatLight(w, block, x, y, z, q);
			LIGHT[0] = LIGHT[1] = LIGHT[2] = LIGHT[3] = light;
			BRIGHT[0] = BRIGHT[1] = BRIGHT[2] = BRIGHT[3] = 1F;
		}
		for (int i = 0; i < 4; i++) {
			t.color(r * BRIGHT[i], g * BRIGHT[i], b * BRIGHT[i]);
			t.brightness(LIGHT[i]);
			float u = d[i * 5 + 3], v = d[i * 5 + 4];
			if (forced != null) {
				u = forced.getU(q.local[i * 2]);
				v = forced.getV(q.local[i * 2 + 1]);
			}
			t.vertex(ox + d[i * 5], oy + d[i * 5 + 1], oz + d[i * 5 + 2], u, v);
		}
	}

	private static void bounds(Quad q) {
		float[] d = q.data;
		BOX[0] = BOX[1] = BOX[2] = 32F;
		BOX[3] = BOX[4] = BOX[5] = -32F;
		for (int i = 0; i < 4; i++) {
			for (int a = 0; a < 3; a++) {
				BOX[a] = Math.min(BOX[a], d[i * 5 + a]);
				BOX[a + 3] = Math.max(BOX[a + 3], d[i * 5 + a]);
			}
		}
	}

	/** True when the quad lies on the block boundary in its facing direction (vanilla "flush" bit). */
	private static boolean flush(Block block, int face) {
		float lo, hi;
		int a = Dir.axis(face);
		lo = BOX[a];
		hi = BOX[a + 3];
		boolean onEdge = Dir.positive(face) ? hi > 0.9999F : lo < 1.0E-4F;
		return (onEdge || block.isCube()) && lo == hi;
	}

	/** True when the quad does not cover the whole face (vanilla "partial" bit). */
	private static boolean partial(int face) {
		int a = Dir.axis(face);
		for (int i = 0; i < 3; i++) {
			if (i == a) {
				continue;
			}
			if (BOX[i] >= 1.0E-4F || BOX[i + 3] <= 0.9999F) {
				return true;
			}
		}
		return false;
	}

	private static int flatLight(WorldView w, Block block, int x, int y, int z, Quad q) {
		bounds(q);
		int f = q.face;
		return flush(block, f) ? block.getLightColor(w, x + Dir.X[f], y + Dir.Y[f], z + Dir.Z[f]) : block.getLightColor(w, x, y, z);
	}

	private static void smooth(WorldView w, Block block, int x, int y, int z, Quad q) {
		bounds(q);
		int f = q.face;
		boolean flush = flush(block, f);
		boolean partial = partial(f);
		int bx = flush ? x + Dir.X[f] : x, by = flush ? y + Dir.Y[f] : y, bz = flush ? z + Dir.Z[f] : z;
		int[] s = SIDES[f];
		int[] light = S_LIGHT;
		float[] occ = S_OCC;
		boolean[] open = S_OPEN;
		for (int i = 0; i < 4; i++) {
			int sx = bx + Dir.X[s[i]], sy = by + Dir.Y[s[i]], sz = bz + Dir.Z[s[i]];
			light[i] = block.getLightColor(w, sx, sy, sz);
			occ[i] = w.getBlock(sx, sy, sz).getAmbientOcclusionLight();
			open[i] = w.getBlock(sx + Dir.X[f], sy + Dir.Y[f], sz + Dir.Z[f]).isTranslucent();
		}
		// diagonals: (first axis side a, second axis side b); fall back to the first-axis side when both are closed
		float[] dOcc = D_OCC;
		int[] dLight = D_LIGHT;
		int[][] pairs = PAIRS;
		for (int p = 0; p < 4; p++) {
			int a = pairs[p][0], b = pairs[p][1];
			if (open[a] || open[b]) {
				int cx = bx + Dir.X[s[a]] + Dir.X[s[b]], cy = by + Dir.Y[s[a]] + Dir.Y[s[b]], cz = bz + Dir.Z[s[a]] + Dir.Z[s[b]];
				dOcc[p] = w.getBlock(cx, cy, cz).getAmbientOcclusionLight();
				dLight[p] = block.getLightColor(w, cx, cy, cz);
			} else {
				dOcc[p] = occ[a];
				dLight[p] = light[a];
			}
		}
		int center = block.getLightColor(w, x, y, z);
		if (flush || !w.getBlock(x + Dir.X[f], y + Dir.Y[f], z + Dir.Z[f]).isSolidRender()) {
			center = block.getLightColor(w, x + Dir.X[f], y + Dir.Y[f], z + Dir.Z[f]);
		}
		float self = flush ? w.getBlock(bx, by, bz).getAmbientOcclusionLight() : w.getBlock(x, y, z).getAmbientOcclusionLight();
		float[] cOcc = C_OCC;
		int[] cLight = C_LIGHT;
		for (int p = 0; p < 4; p++) {
			int a = pairs[p][0], b = pairs[p][1];
			cOcc[p] = (occ[b] + occ[a] + dOcc[p] + self) * 0.25F;
			cLight[p] = blend(light[b], light[a], dLight[p], center);
		}
		for (int v = 0; v < 4; v++) {
			int[] c = CORNERS[f][v];
			float best = -1F;
			float bright = 0F;
			float sky = 0F, blk = 0F;
			int bestP = 0;
			for (int p = 0; p < 4; p++) {
				float wgt = weight(s[pairs[p][0]], c) * weight(s[pairs[p][1]], c);
				bright += cOcc[p] * wgt;
				sky += (cLight[p] >> 16 & 255) * wgt;
				blk += (cLight[p] & 255) * wgt;
				if (wgt > best) {
					best = wgt;
					bestP = p;
				}
			}
			if (partial) {
				BRIGHT[v] = bright;
				LIGHT[v] = ((int) sky & 255) << 16 | ((int) blk & 255);
			} else {
				BRIGHT[v] = cOcc[bestP];
				LIGHT[v] = cLight[bestP];
			}
		}
	}

	/** Share of a corner sample toward direction d for the box corner c (from the quad's bounds). */
	private static float weight(int d, int[] c) {
		int a = Dir.axis(d);
		float coord = c[a] == 0 ? BOX[a] : BOX[a + 3];
		return Dir.positive(d) ? coord : 1F - coord;
	}

	private static int blend(int a, int b, int c, int center) {
		if (a == 0) {
			a = center;
		}
		if (b == 0) {
			b = center;
		}
		if (c == 0) {
			c = center;
		}
		return a + b + c + center >> 2 & 0xFF00FF;
	}
}
