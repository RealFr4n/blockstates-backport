package com.blockstatesbackport.render;

import net.minecraft.block.Block;
import net.minecraft.world.WorldView;

/**
 * Color for quads with a tintindex, following 1.12's block colors. Biome colors come from the 1.7.10 blocks
 * themselves (same biome math as 1.8).
 */
public final class Tints {

	private static Block grass;

	private Tints() {
	}

	public static int color(Block block, String name, WorldView w, int x, int y, int z, int key) {
		switch (name) {
			case "minecraft:redstone_wire":
				return wire(key & 15);
			case "minecraft:pumpkin_stem":
			case "minecraft:melon_stem": {
				int age = key & 7;
				return age * 32 << 16 | (255 - age * 8) << 8 | age * 4;
			}
			case "minecraft:double_plant": {
				int variant = (key & 8) != 0 ? (key >> 4) & 7 : key & 7;
				return variant == 2 || variant == 3 ? grass(w, x, y, z) : 0xFFFFFF;
			}
			case "minecraft:flower_pot":
				// fern and dead bush in a pot take the grass color like the tall grass block does
				int c = (key >> 4) & 31;
				return c == 19 || c == 20 ? grass(w, x, y, z) : 0xFFFFFF;
			default:
				return block.getColorTint(w, x, y, z) & 0xFFFFFF;
		}
	}

	private static int grass(WorldView w, int x, int y, int z) {
		if (grass == null) {
			grass = Block.byKey("grass");
		}
		return grass.getColorTint(w, x, y, z) & 0xFFFFFF;
	}

	/** 1.12 redstone dust color by power. */
	static int wire(int power) {
		float f = power / 15F;
		float r = power == 0 ? 0.3F : f * 0.6F + 0.4F;
		float g = Math.max(0F, f * f * 0.7F - 0.5F);
		float b = Math.max(0F, f * f * 0.6F - 0.7F);
		return clamp((int) (r * 255F)) << 16 | clamp((int) (g * 255F)) << 8 | clamp((int) (b * 255F));
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : Math.min(v, 255);
	}
}
