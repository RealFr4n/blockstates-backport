package com.blockstatesbackport.render;

import org.lwjgl.opengl.GL11;

import net.minecraft.block.Block;
import net.minecraft.client.render.vertex.Tesselator;

import com.blockstatesbackport.model.BakedModel;
import com.blockstatesbackport.model.Defs;
import com.blockstatesbackport.model.Dir;
import com.blockstatesbackport.model.Quad;

/**
 * Draws a block item from its item model, inside the transform 1.7.10 already set up for the context (GUI, hand,
 * ground, frame). Like 1.12 items: no baked shade, the quad's face normal for GL lighting, tint for tinted quads.
 * <p>
 * In a GUI slot, 1.7.10's transform (with its own 90 degree turn) plus one more quarter turn equals a 1.12
 * display of rotation [30, 135, 0] and scale 0.625 on the same base; that is undone and the model's "gui" display
 * applied instead, so the item looks like 1.12. Other contexts (hand, ground, frame) keep 1.7.10's placement.
 */
public final class ItemModelRenderer {

	/** Set while 1.7.10 draws an item in a GUI slot. */
	public static boolean inGui;

	/** What 1.7.10's GUI item transform amounts to, written as a 1.12 display. */
	private static final Defs.Transform LEGACY_GUI = new Defs.Transform(new float[]{30, 135, 0}, new float[3],
		new float[]{0.625F, 0.625F, 0.625F});
	/** 1.12 block/block gui display, for models without one. */
	private static final Defs.Transform DEFAULT_GUI = new Defs.Transform(new float[]{30, 225, 0}, new float[3],
		new float[]{0.625F, 0.625F, 0.625F});

	private ItemModelRenderer() {
	}

	public static void render(BakedModel m, Block block, int meta, float brightness, boolean applyTint) {
		int color = applyTint ? block.getColor(meta) & 0xFFFFFF : 0xFFFFFF;
		// 1.7.10 leaves this rotation applied after drawing a block item; keep doing the same
		GL11.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
		GL11.glPushMatrix();
		if (inGui) {
			// measured against a vanilla 1.12.2 screenshot (per-slot pixel diff of 0/90/180/270): 90 matches
			GL11.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
			Defs.Transform gui = m.display.get("gui");
			undo(LEGACY_GUI);
			apply(gui != null ? gui : DEFAULT_GUI);
		}
		GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
		Tesselator t = Tesselator.INSTANCE;
		t.begin();
		for (Quad[] quads : m.quads) {
			for (Quad q : quads) {
				int c = q.tint >= 0 ? color : 0xFFFFFF;
				t.normal(Dir.X[q.face], Dir.Y[q.face], Dir.Z[q.face]);
				t.color((c >> 16 & 255) / 255F * brightness, (c >> 8 & 255) / 255F * brightness, (c & 255) / 255F * brightness);
				float[] d = q.data;
				for (int i = 0; i < 4; i++) {
					t.vertex(d[i * 5], d[i * 5 + 1], d[i * 5 + 2], d[i * 5 + 3], d[i * 5 + 4]);
				}
			}
		}
		t.end();
		GL11.glPopMatrix();
	}

	/** 1.12 display transform: translate (1/16, clamped to 5), rotate X then Y then Z, scale (clamped to 4). */
	private static void apply(Defs.Transform tr) {
		GL11.glTranslatef(clamp(tr.translation[0] / 16F, 5F), clamp(tr.translation[1] / 16F, 5F), clamp(tr.translation[2] / 16F, 5F));
		GL11.glRotatef(tr.rotation[0], 1, 0, 0);
		GL11.glRotatef(tr.rotation[1], 0, 1, 0);
		GL11.glRotatef(tr.rotation[2], 0, 0, 1);
		GL11.glScalef(clamp(tr.scale[0], 4F), clamp(tr.scale[1], 4F), clamp(tr.scale[2], 4F));
	}

	/** Inverse of apply(). */
	private static void undo(Defs.Transform tr) {
		GL11.glScalef(1F / tr.scale[0], 1F / tr.scale[1], 1F / tr.scale[2]);
		GL11.glRotatef(-tr.rotation[2], 0, 0, 1);
		GL11.glRotatef(-tr.rotation[1], 0, 1, 0);
		GL11.glRotatef(-tr.rotation[0], 1, 0, 0);
		GL11.glTranslatef(-tr.translation[0] / 16F, -tr.translation[1] / 16F, -tr.translation[2] / 16F);
	}

	private static float clamp(float v, float max) {
		return Math.max(-max, Math.min(max, v));
	}
}
