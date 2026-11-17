package com.blockstatesbackport.model;

/**
 * What one block state draws: one weighted choice per applied part (a single part for "variants" files).
 */
public final class StateModel {

	/** Weighted alternatives; picked by position like vanilla (entries sorted by weight, heaviest first). */
	public static final class Choice {
		final BakedModel[] models;
		final int[] weights;
		final int total;

		public Choice(BakedModel[] models, int[] weights) {
			this.models = models;
			this.weights = weights;
			int t = 0;
			for (int w : weights) {
				t += w;
			}
			this.total = t;
		}

		public BakedModel pick(long seed) {
			if (models.length == 1) {
				return models[0];
			}
			int r = Math.abs((int) seed >> 16) % total;
			for (int i = 0; i < models.length; i++) {
				r -= weights[i];
				if (r < 0) {
					return models[i];
				}
			}
			return models[models.length - 1];
		}

		public BakedModel first() {
			return models[0];
		}
	}

	public final Choice[] parts;
	public final boolean ambientOcclusion;
	/** Registry name of the block, for tints and plant offsets. */
	public String block = "";

	public StateModel(Choice[] parts, boolean ambientOcclusion) {
		this.parts = parts;
		this.ambientOcclusion = ambientOcclusion;
	}

	/** The block position hash vanilla uses for random model choice and plant offsets. */
	public static long seed(int x, int y, int z) {
		long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
		return l * l * 42317861L + l * 11L;
	}
}
