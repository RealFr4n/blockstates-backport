package com.blockstatesbackport.mixin;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.world.ChunkRenderer;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.living.LivingEntity;

import com.blockstatesbackport.render.CutoutChunk;
import com.blockstatesbackport.render.CutoutLayer;

/** Draws the CUTOUT lists right after the opaque pass with the block atlas un-mipmapped (1.12 setBlurMipmap(false, false)). */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

	@Shadow
	private RenderChunk[] sortedChunks;
	@Shadow
	private ChunkRenderer[] chunkRenderers;
	@Shadow
	private boolean occlusion;
	@Shadow
	private Minecraft minecraft;

	@Shadow
	public abstract void renderLastChunks(int layer, double tickDelta);

	@Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;ID)I", at = @At("RETURN"))
	private void bsb$renderCutout(LivingEntity camera, int layer, double tickDelta, CallbackInfoReturnable<Integer> cir) {
		if (layer != 0 || sortedChunks == null || !CutoutLayer.usable()) {
			return;
		}
		LivingEntity cam = minecraft.camera;
		double cx = cam.prevX + (cam.x - cam.prevX) * tickDelta;
		double cy = cam.prevY + (cam.y - cam.prevY) * tickDelta;
		double cz = cam.prevZ + (cam.z - cam.prevZ) * tickDelta;
		for (ChunkRenderer r : chunkRenderers) {
			r.clear();
		}
		// Same grouping as WorldRenderer.renderChunks: one ChunkRenderer per render origin.
		int used = 0;
		for (RenderChunk c : sortedChunks) {
			// OptiFine fills sortedChunks only up to its own count; the rest is null.
			if (c == null) {
				continue;
			}
			int list = ((CutoutChunk) c).bsb$cutoutList();
			if (list < 0 || !c.visible || occlusion && !c.occlusionVisible) {
				continue;
			}
			int idx = -1;
			for (int i = 0; i < used; i++) {
				if (chunkRenderers[i].isAt(c.renderX, c.renderY, c.renderZ)) {
					idx = i;
					break;
				}
			}
			if (idx < 0) {
				if (used == chunkRenderers.length) {
					continue;
				}
				idx = used++;
				chunkRenderers[idx].init(c.renderX, c.renderY, c.renderZ, cx, cy, cz);
			}
			chunkRenderers[idx].addGlList(list);
		}
		if (used == 0) {
			return;
		}
		minecraft.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
		int minFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		renderLastChunks(0, tickDelta);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
	}
}
