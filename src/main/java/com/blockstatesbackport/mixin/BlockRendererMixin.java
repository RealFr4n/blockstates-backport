package com.blockstatesbackport.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.Block;
import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.world.WorldView;

import com.blockstatesbackport.Config;
import com.blockstatesbackport.ModelManager;
import com.blockstatesbackport.model.BakedModel;
import com.blockstatesbackport.model.StateModel;
import com.blockstatesbackport.render.CutoutLayer;
import com.blockstatesbackport.render.ItemModelRenderer;
import com.blockstatesbackport.render.ModelRenderer;

@Mixin(BlockRenderer.class)
public class BlockRendererMixin {

	@Shadow
	public WorldView world;

	/** Blocks with a JSON model are drawn from it; everything else keeps the 1.7.10 path. */
	@Inject(method = "tesselateInWorld", at = @At("HEAD"), cancellable = true)
	private void bsb$tesselateModel(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
		if (!Config.enabled || !ModelManager.isActive() || world == null) {
			return;
		}
		int meta = world.getBlockMetadata(x, y, z);
		StateModel model = ModelManager.get(world, block, x, y, z, meta);
		if (model == null) {
			return;
		}
		BlockRendererAccessor self = (BlockRendererAccessor) this;
		if (CutoutLayer.hold((BlockRenderer) (Object) this, block, x, y, z, self.bsb$renderFaceAlways())) {
			cir.setReturnValue(false);
			return;
		}
		cir.setReturnValue(ModelRenderer.render(world, block, model.block, x, y, z, meta, model, self.bsb$forcedSprite(),
			self.bsb$renderFaceAlways()));
	}

	@Shadow
	public boolean applyColorTint;

	/** Block items (inventory, hand, ground, frames) drawn from their 1.12 item model. */
	@Inject(method = "renderAsItem", at = @At("HEAD"), cancellable = true)
	private void bsb$renderItemModel(Block block, int meta, float brightness, CallbackInfo ci) {
		if (!Config.enabled || !Config.items) {
			return;
		}
		BakedModel model = ModelManager.itemModel(block, meta);
		if (model == null) {
			return;
		}
		ItemModelRenderer.render(model, block, meta, brightness, applyColorTint);
		ci.cancel();
	}
}
