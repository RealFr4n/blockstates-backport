package com.blockstatesbackport.render;

import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.client.render.block.BlockRenderer;

import com.blockstatesbackport.BlockstatesBackport;
import com.blockstatesbackport.Config;
import com.blockstatesbackport.ModelManager;

/**
 * 1.12 CUTOUT layer: blocks drawn with mipmapping off (redstone, torches, rails, plants...). 1.7.10 has one opaque pass, so
 * while a chunk compiles its opaque layer these blocks are held back and compiled into a third display list of the chunk,
 * drawn right after the opaque pass with the atlas set to GL_NEAREST, like 1.12 RenderGlobal does for CUTOUT.
 */
public final class CutoutLayer {

	/** Chunk compiling on this frame (main thread only), or null when blocks must not be held back. */
	public static Object compiling;
	public static int layer = -1;
	/** True while the held-back blocks are being drawn into the cutout list. */
	public static boolean building;

	public static BlockRenderer renderer;
	public static int count;
	public static Block[] blocks = new Block[256];
	public static int[] pos = new int[256 * 3];

	private static boolean optifineChecked;
	private static Method fastRender;
	private static Method shaders;

	private CutoutLayer() {
	}

	/** Whether the extra pass can be used now; with OptiFine Fast Render or shaders chunks are drawn differently. */
	public static boolean usable() {
		if (!Config.enabled || !Config.cutoutLayer || !ModelManager.isActive()) {
			return false;
		}
		if (!optifineChecked) {
			optifineChecked = true;
			try {
				Class<?> c = Class.forName("Config", false, CutoutLayer.class.getClassLoader());
				fastRender = c.getMethod("isFastRender");
				shaders = c.getMethod("isShaders");
			} catch (Throwable ignored) {
				// no OptiFine
			}
		}
		try {
			return (fastRender == null || !(Boolean) fastRender.invoke(null)) && (shaders == null || !(Boolean) shaders.invoke(null));
		} catch (Throwable t) {
			BlockstatesBackport.LOG.warn("OptiFine check failed, CUTOUT layer off: {}", t.toString());
			fastRender = null;
			shaders = null;
			optifineChecked = true;
			Config.cutoutLayer = false;
			return false;
		}
	}

	/** Called for JSON-modelled blocks while a chunk compiles; true = held back for the cutout list. */
	public static boolean hold(BlockRenderer r, Block block, int x, int y, int z, boolean renderFaceAlways) {
		if (compiling == null || layer != 0 || building || renderFaceAlways || !ModelManager.isCutout(block)) {
			return false;
		}
		if (count == blocks.length) {
			Block[] b = new Block[count * 2];
			System.arraycopy(blocks, 0, b, 0, count);
			blocks = b;
			int[] p = new int[count * 6];
			System.arraycopy(pos, 0, p, 0, count * 3);
			pos = p;
		}
		renderer = r;
		blocks[count] = block;
		pos[count * 3] = x;
		pos[count * 3 + 1] = y;
		pos[count * 3 + 2] = z;
		count++;
		return true;
	}

	/** Draws the held-back blocks into the currently open layer; returns true if anything was drawn. */
	public static boolean drawHeld() {
		boolean any = false;
		for (int i = 0; i < count; i++) {
			any |= renderer.tesselateInWorld(blocks[i], pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
			blocks[i] = null;
		}
		count = 0;
		renderer = null;
		return any;
	}

	public static void clear() {
		for (int i = 0; i < count; i++) {
			blocks[i] = null;
		}
		count = 0;
		renderer = null;
		compiling = null;
		layer = -1;
	}
}
