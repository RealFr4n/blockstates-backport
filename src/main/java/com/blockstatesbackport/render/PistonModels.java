package com.blockstatesbackport.render;

import net.minecraft.block.Block;
import net.minecraft.block.entity.MovingBlockEntity;
import net.minecraft.world.WorldView;

import com.blockstatesbackport.Config;
import com.blockstatesbackport.ModelManager;
import com.blockstatesbackport.model.StateModel;

/**
 * Moving pistons drawn from their models like 1.12's piston renderer: the head with "short" by progress, and the
 * base of a retracting piston with extended=true. Returns false to let 1.7.10 draw it when there is no model.
 */
public final class PistonModels {

	/** Set by the moving block renderer before it draws. */
	public static MovingBlockEntity current;
	public static float tickDelta;

	private static Block head;
	private static Block sticky;

	private PistonModels() {
	}

	private static void init() {
		if (head == null) {
			head = Block.byKey("piston_head");
			sticky = Block.byKey("sticky_piston");
		}
	}

	public static boolean renderHead(WorldView w, int x, int y, int z) {
		MovingBlockEntity te = current;
		if (!Config.enabled || !ModelManager.isActive() || te == null) {
			return false;
		}
		init();
		Block moved = te.getMovedBlock();
		int meta = te.getBlockMetadata();
		float progress = te.getProgress(tickDelta);
		int key;
		if (moved == head) {
			// extending: the moving block is the head itself
			key = (meta & 15) | (progress <= 0.25F ? 16 : 0);
		} else {
			// retracting: head pulled back into the base
			key = (meta & 7) | (moved == sticky ? 8 : 0) | (progress >= 0.5F ? 16 : 0);
		}
		return draw(w, head, x, y, z, key);
	}

	public static boolean renderExtendedBase(WorldView w, Block base, int x, int y, int z) {
		MovingBlockEntity te = current;
		if (!Config.enabled || !ModelManager.isActive() || te == null) {
			return false;
		}
		return draw(w, base, x, y, z, (te.getBlockMetadata() & 7) | 8);
	}

	private static boolean draw(WorldView w, Block block, int x, int y, int z, int key) {
		StateModel model = ModelManager.getByKey(block, key);
		if (model == null) {
			return false;
		}
		ModelRenderer.render(w, block, model.block, x, y, z, key, model, null, false);
		return true;
	}
}
