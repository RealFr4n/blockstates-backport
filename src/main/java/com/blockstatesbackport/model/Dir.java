package com.blockstatesbackport.model;

/**
 * Directions as ints, same ids as 1.7.10 block sides and 1.8 EnumFacing: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east.
 */
public final class Dir {

	public static final int DOWN = 0;
	public static final int UP = 1;
	public static final int NORTH = 2;
	public static final int SOUTH = 3;
	public static final int WEST = 4;
	public static final int EAST = 5;

	public static final int[] X = {0, 0, 0, 0, -1, 1};
	public static final int[] Y = {-1, 1, 0, 0, 0, 0};
	public static final int[] Z = {0, 0, -1, 1, 0, 0};
	public static final String[] NAMES = {"down", "up", "north", "south", "west", "east"};
	public static final int[] OPPOSITE = {1, 0, 3, 2, 5, 4};

	/** Horizontal index as stored in 1.7.10 metadata by most blocks: 0 south, 1 west, 2 north, 3 east. */
	public static final int[] BY_HORIZONTAL = {SOUTH, WEST, NORTH, EAST};

	private Dir() {
	}

	public static int byName(String name) {
		for (int i = 0; i < 6; i++) {
			if (NAMES[i].equals(name)) {
				return i;
			}
		}
		return -1;
	}

	/** Axis of a direction: 0 x, 1 y, 2 z. */
	public static int axis(int dir) {
		return dir <= UP ? 1 : dir <= SOUTH ? 2 : 0;
	}

	public static boolean positive(int dir) {
		return dir == UP || dir == SOUTH || dir == EAST;
	}

	/** Direction closest to a normal vector. */
	public static int nearest(float x, float y, float z) {
		int best = UP;
		float bestDot = 0;
		for (int d = 0; d < 6; d++) {
			float dot = x * X[d] + y * Y[d] + z * Z[d];
			if (dot >= 0 && dot > bestDot) {
				bestDot = dot;
				best = d;
			}
		}
		return best;
	}
}
