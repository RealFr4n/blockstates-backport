package com.blockstatesbackport.mixin;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.entity.living.LivingEntity;

import com.blockstatesbackport.render.CutoutChunk;
import com.blockstatesbackport.render.CutoutLayer;

/**
 * Adds the 1.12 CUTOUT layer as a third display list per chunk. The list is compiled through the chunk's own
 * beginLayer/endLayer (vanilla or OptiFine's) with glList pointed at it, so translation and tesselator setup match pass 0.
 */
@Mixin(RenderChunk.class)
public abstract class RenderChunkMixin implements CutoutChunk {

	@Shadow
	private int glList;
	@Shadow
	public boolean dirty;

	@Shadow
	private void beginLayer(int layer) {
	}

	@Shadow
	private void endLayer(int layer, LivingEntity camera) {
	}

	@Unique
	private int bsb$cutoutList;
	@Unique
	private boolean bsb$cutoutEmpty = true;

	@Override
	public int bsb$cutoutList() {
		return bsb$cutoutEmpty || bsb$cutoutList == 0 ? -1 : bsb$cutoutList;
	}

	@Inject(method = "compile", at = @At("HEAD"))
	private void bsb$compileStart(LivingEntity camera, CallbackInfo ci) {
		if (!dirty) {
			return;
		}
		bsb$cutoutEmpty = true;
		CutoutLayer.clear();
		// OptiFine's threaded chunk loading subclasses RenderChunk and compiles off-thread: keep pass 0 as is there.
		if (((Object) this).getClass() == RenderChunk.class && CutoutLayer.usable()) {
			CutoutLayer.compiling = this;
		}
	}

	@Inject(method = "compile", at = @At("RETURN"))
	private void bsb$compileEnd(LivingEntity camera, CallbackInfo ci) {
		if (CutoutLayer.compiling == this) {
			CutoutLayer.clear();
		}
	}

	@Inject(method = "beginLayer", at = @At("HEAD"))
	private void bsb$beginLayer(int layer, CallbackInfo ci) {
		if (CutoutLayer.compiling == this && !CutoutLayer.building) {
			CutoutLayer.layer = layer;
		}
	}

	@Inject(method = "endLayer", at = @At("TAIL"))
	private void bsb$endLayer(int layer, LivingEntity camera, CallbackInfo ci) {
		if (CutoutLayer.compiling != this || CutoutLayer.building || layer != 0 || CutoutLayer.count == 0) {
			return;
		}
		if (bsb$cutoutList == 0) {
			bsb$cutoutList = GL11.glGenLists(1);
		}
		int opaqueList = glList;
		glList = bsb$cutoutList;
		CutoutLayer.building = true;
		try {
			beginLayer(0);
			bsb$cutoutEmpty = !CutoutLayer.drawHeld();
			endLayer(0, camera);
		} finally {
			CutoutLayer.building = false;
			glList = opaqueList;
		}
	}

	/** A chunk holding only CUTOUT blocks has both vanilla layers empty but is not empty (occlusion queries use this). */
	@Inject(method = "isEmpty", at = @At("RETURN"), cancellable = true)
	private void bsb$isEmpty(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && bsb$cutoutList() >= 0) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "reset", at = @At("TAIL"))
	private void bsb$reset(CallbackInfo ci) {
		bsb$cutoutEmpty = true;
	}

	@Inject(method = "delete", at = @At("TAIL"))
	private void bsb$delete(CallbackInfo ci) {
		if (bsb$cutoutList != 0) {
			GL11.glDeleteLists(bsb$cutoutList, 1);
			bsb$cutoutList = 0;
		}
	}
}
