package com.blockstatesbackport.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.render.texture.TextureAtlasSprite;

/**
 * Turns resolved model elements into quads, with the 1.12 rules: variant rotation in 90 degree steps around the
 * block center, element rotation with optional rescale, uvlock, directional shade and vertex winding.
 */
public final class Baker {

	/** Which end (0 = from, 1 = to) of each axis the 4 vertices of a face use, per face. */
	private static final int[][][] CORNERS = {
		{{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}}, // down
		{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // up
		{{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}}, // north
		{{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}}, // south
		{{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}}, // west
		{{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}, // east
	};
	private static final float[] SHADE = {0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F};

	/** Variant rotation x/y (multiples of 90). */
	public static final class Rot {
		final int x;
		final int y;
		final int[] m = new int[9];
		final int[] mx = new int[9];

		private Rot(int x, int y) {
			this.x = x;
			this.y = y;
			// rotate by -x around X, then by -y around Y (a positive y turns north towards east)
			int cx = cos(-x), sx = sin(-x), cy = cos(-y), sy = sin(-y);
			int[] rx = {1, 0, 0, 0, cx, -sx, 0, sx, cx};
			int[] ry = {cy, 0, sy, 0, 1, 0, -sy, 0, cy};
			System.arraycopy(rx, 0, mx, 0, 9);
			for (int r = 0; r < 3; r++) {
				for (int c = 0; c < 3; c++) {
					int s = 0;
					for (int k = 0; k < 3; k++) {
						s += ry[r * 3 + k] * rx[k * 3 + c];
					}
					m[r * 3 + c] = s;
				}
			}
		}

		boolean identity() {
			return x == 0 && y == 0;
		}

		void apply(float[] p) {
			float a = p[0] - 0.5F, b = p[1] - 0.5F, c = p[2] - 0.5F;
			p[0] = m[0] * a + m[1] * b + m[2] * c + 0.5F;
			p[1] = m[3] * a + m[4] * b + m[5] * c + 0.5F;
			p[2] = m[6] * a + m[7] * b + m[8] * c + 0.5F;
		}

		public int dir(int d) {
			return turn(m, d);
		}

		/** Where vertex i of face f ends up, like the vanilla rotation does for rotated elements. */
		int perm(int f, int i) {
			int j = i;
			int sx = x / 90, sy = y / 90;
			if (Dir.axis(f) == 0) {
				j = (j + sx) % 4;
			}
			if (Dir.axis(turn(mx, f)) == 1) {
				j = (j + sy) % 4;
			}
			return j;
		}

		private static int turn(int[] m, int d) {
			int vx = Dir.X[d], vy = Dir.Y[d], vz = Dir.Z[d];
			return Dir.nearest(m[0] * vx + m[1] * vy + m[2] * vz, m[3] * vx + m[4] * vy + m[5] * vz, m[6] * vx + m[7] * vy + m[8] * vz);
		}

		private static int cos(int deg) {
			switch (Math.floorMod(deg, 360)) {
				case 0: return 1;
				case 180: return -1;
				default: return 0;
			}
		}

		private static int sin(int deg) {
			switch (Math.floorMod(deg, 360)) {
				case 90: return 1;
				case 270: return -1;
				default: return 0;
			}
		}
	}

	private static final Rot[] ROTS = new Rot[16];
	/** uvlock: quarter turns applied to the face UV rect, per rotation and face. Derived at startup, see lockTurns. */
	private static final int[][] LOCK = new int[16][6];

	static {
		for (int i = 0; i < 16; i++) {
			ROTS[i] = new Rot(i / 4 * 90, i % 4 * 90);
		}
		for (int i = 0; i < 16; i++) {
			for (int f = 0; f < 6; f++) {
				LOCK[i][f] = lockTurns(ROTS[i], f);
			}
		}
	}

	private Baker() {
	}

	public static Rot rot(int x, int y) {
		return ROTS[x / 90 * 4 + y / 90];
	}

	/** Exposed for the self-check. */
	public static int lock(int x, int y, int face) {
		return LOCK[x / 90 * 4 + y / 90][face];
	}

	/**
	 * uvlock keeps a texture aligned with the world after the model turns. For each rotation and face this picks
	 * the quarter turn of the UV rect that makes a full face map exactly like an unrotated face pointing the same
	 * way would.
	 */
	private static int lockTurns(Rot r, int f) {
		float[] full = {0, 0, 16, 16};
		for (int k = 0; k < 4; k++) {
			float[] uv = new float[4];
			int texRot = fixup(full, k, uv);
			float[] pos = new float[12];
			float[] loc = new float[8];
			int face = place(new float[]{0, 0, 0}, new float[]{1, 1, 1}, null, f, uv, texRot, r, pos, loc);
			if (matchesProjection(pos, loc, face)) {
				return k;
			}
		}
		return 0;
	}

	private static boolean matchesProjection(float[] pos, float[] loc, int face) {
		for (int i = 0; i < 4; i++) {
			float x = pos[i * 3], y = pos[i * 3 + 1], z = pos[i * 3 + 2];
			float u, v;
			switch (face) {
				case Dir.DOWN: u = x * 16; v = (1 - z) * 16; break;
				case Dir.UP: u = x * 16; v = z * 16; break;
				case Dir.NORTH: u = (1 - x) * 16; v = (1 - y) * 16; break;
				case Dir.SOUTH: u = x * 16; v = (1 - y) * 16; break;
				case Dir.WEST: u = z * 16; v = (1 - y) * 16; break;
				default: u = (1 - z) * 16; v = (1 - y) * 16; break;
			}
			if (Math.abs(u - loc[i * 2]) > 1e-3F || Math.abs(v - loc[i * 2 + 1]) > 1e-3F) {
				return false;
			}
		}
		return true;
	}

	/** Turns the UV rect k quarter turns inside the 16x16 texture; returns the new face rotation. */
	private static int fixup(float[] uv, int k, float[] out) {
		float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
		switch (k) {
			case 1:
				out[0] = v1; out[1] = 16 - u0; out[2] = v0; out[3] = 16 - u1;
				return 270;
			case 2:
				out[0] = 16 - u0; out[1] = 16 - v0; out[2] = 16 - u1; out[3] = 16 - v1;
				return 0;
			case 3:
				out[0] = 16 - v0; out[1] = u1; out[2] = 16 - v1; out[3] = u0;
				return 90;
			default:
				System.arraycopy(uv, 0, out, 0, 4);
				return 0;
		}
	}

	private static float texU(float[] uv, int rot, int i) {
		int idx = (i + rot / 90) % 4;
		return idx == 0 || idx == 1 ? uv[0] : uv[2];
	}

	private static float texV(float[] uv, int rot, int i) {
		int idx = (i + rot / 90) % 4;
		return idx == 0 || idx == 3 ? uv[1] : uv[3];
	}

	/**
	 * Places the 4 vertices of face f of the box from..to (block units) into pos/loc and returns the facing.
	 */
	private static int place(float[] from, float[] to, Defs.Element el, int f, float[] uv, int texRot, Rot r, float[] pos, float[] loc) {
		float[] p = new float[3];
		for (int i = 0; i < 4; i++) {
			int[] c = CORNERS[f][i];
			p[0] = c[0] == 0 ? from[0] : to[0];
			p[1] = c[1] == 0 ? from[1] : to[1];
			p[2] = c[2] == 0 ? from[2] : to[2];
			if (el != null && el.rotAxis >= 0) {
				rotateElement(p, el);
			}
			int j = i;
			if (!r.identity()) {
				r.apply(p);
				j = r.perm(f, i);
			}
			pos[j * 3] = p[0];
			pos[j * 3 + 1] = p[1];
			pos[j * 3 + 2] = p[2];
			loc[j * 2] = texU(uv, texRot, i);
			loc[j * 2 + 1] = texV(uv, texRot, i);
		}
		// facing from the vertex normal: (v2 - v1) x (v0 - v1)
		float ax = pos[0] - pos[3], ay = pos[1] - pos[4], az = pos[2] - pos[5];
		float bx = pos[6] - pos[3], by = pos[7] - pos[4], bz = pos[8] - pos[5];
		float nx = by * az - bz * ay, ny = bz * ax - bx * az, nz = bx * ay - by * ax;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		int face = len == 0 ? Dir.UP : Dir.nearest(nx / len, ny / len, nz / len);
		if (el == null || el.rotAxis < 0) {
			rewind(pos, loc, face);
		}
		return face;
	}

	private static void rotateElement(float[] p, Defs.Element el) {
		double a = Math.toRadians(el.rotAngle);
		float c = (float) Math.cos(a), s = (float) Math.sin(a);
		float[] o = el.rotOrigin;
		float x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
		float nx, ny, nz;
		switch (el.rotAxis) {
			case 0: nx = x; ny = c * y - s * z; nz = s * y + c * z; break;
			case 1: nx = c * x + s * z; ny = y; nz = -s * x + c * z; break;
			default: nx = c * x - s * y; ny = s * x + c * y; nz = z; break;
		}
		if (el.rescale) {
			// vanilla uses the 45 degree factor for any angle that is not 22.5
			float k = 1F / (float) Math.cos(Math.abs(el.rotAngle) == 22.5F ? Math.PI / 8 : Math.PI / 4);
			if (el.rotAxis != 0) {
				nx *= k;
			}
			if (el.rotAxis != 1) {
				ny *= k;
			}
			if (el.rotAxis != 2) {
				nz *= k;
			}
		}
		p[0] = nx + o[0];
		p[1] = ny + o[1];
		p[2] = nz + o[2];
	}

	/** Reorders the vertices to the standard corner order of the final facing, keeping each UV with its corner. */
	private static void rewind(float[] pos, float[] loc, int face) {
		float minX = 999, minY = 999, minZ = 999, maxX = -999, maxY = -999, maxZ = -999;
		for (int i = 0; i < 4; i++) {
			minX = Math.min(minX, pos[i * 3]);
			minY = Math.min(minY, pos[i * 3 + 1]);
			minZ = Math.min(minZ, pos[i * 3 + 2]);
			maxX = Math.max(maxX, pos[i * 3]);
			maxY = Math.max(maxY, pos[i * 3 + 1]);
			maxZ = Math.max(maxZ, pos[i * 3 + 2]);
		}
		float[] oldPos = pos.clone();
		float[] oldLoc = loc.clone();
		for (int j = 0; j < 4; j++) {
			int[] c = CORNERS[face][j];
			float x = c[0] == 0 ? minX : maxX, y = c[1] == 0 ? minY : maxY, z = c[2] == 0 ? minZ : maxZ;
			pos[j * 3] = x;
			pos[j * 3 + 1] = y;
			pos[j * 3 + 2] = z;
			for (int n = 0; n < 4; n++) {
				if (near(x, oldPos[n * 3]) && near(y, oldPos[n * 3 + 1]) && near(z, oldPos[n * 3 + 2])) {
					loc[j * 2] = oldLoc[n * 2];
					loc[j * 2 + 1] = oldLoc[n * 2 + 1];
				}
			}
		}
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 1.0E-5F;
	}

	/**
	 * Bakes elements with a sprite lookup (texture name already resolved through the model's texture map).
	 */
	public static BakedModel bake(List<Defs.Element> elements, Function<String, TextureAtlasSprite> sprites, Rot r, boolean uvlock,
			boolean ao) {
		List<List<Quad>> lists = new ArrayList<>(7);
		for (int i = 0; i < 7; i++) {
			lists.add(new ArrayList<>());
		}
		int ri = r.x / 90 * 4 + r.y / 90;
		float[] uv = new float[4];
		float[] pos = new float[12];
		float[] loc = new float[8];
		for (Defs.Element el : elements) {
			float[] from = {el.from[0] / 16F, el.from[1] / 16F, el.from[2] / 16F};
			float[] to = {el.to[0] / 16F, el.to[1] / 16F, el.to[2] / 16F};
			for (int f = 0; f < 6; f++) {
				Defs.Face face = el.faces[f];
				if (face == null) {
					continue;
				}
				TextureAtlasSprite sprite = sprites.apply(face.texture);
				int texRot = face.rotation;
				if (uvlock) {
					texRot = fixup(face.uv, LOCK[ri][f], uv);
				} else {
					System.arraycopy(face.uv, 0, uv, 0, 4);
				}
				Quad q = new Quad();
				q.face = place(from, to, el, f, uv, texRot, r, pos, loc);
				q.shade = el.shade ? SHADE[r.dir(f)] : 1F;
				q.tint = face.tint;
				q.cull = face.cull < 0 ? -1 : r.dir(face.cull);
				q.sprite = sprite;
				System.arraycopy(loc, 0, q.local, 0, 8);
				for (int i = 0; i < 4; i++) {
					q.data[i * 5] = pos[i * 3];
					q.data[i * 5 + 1] = pos[i * 3 + 1];
					q.data[i * 5 + 2] = pos[i * 3 + 2];
					q.data[i * 5 + 3] = sprite.getU(loc[i * 2]);
					q.data[i * 5 + 4] = sprite.getV(loc[i * 2 + 1]);
				}
				lists.get(q.cull < 0 ? 6 : q.cull).add(q);
			}
		}
		BakedModel m = new BakedModel();
		for (int i = 0; i < 7; i++) {
			m.quads[i] = lists.get(i).isEmpty() ? BakedModel.NO_QUADS : lists.get(i).toArray(new Quad[0]);
		}
		m.ambientOcclusion = ao;
		return m;
	}
}
