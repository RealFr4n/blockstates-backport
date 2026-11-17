package com.blockstatesbackport.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.Block;
import net.minecraft.block.entity.MovingBlockEntity;
import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.client.render.block.entity.MovingBlockRenderer;

import com.blockstatesbackport.render.PistonModels;

/** Piston head and retracting base during the animation, from their JSON models when there are any. */
@Mixin(MovingBlockRenderer.class)
public class MovingBlockRendererMixin {

	private static final String RENDER = "render(Lnet/minecraft/block/entity/MovingBlockEntity;DDDF)V";

	@Inject(method = RENDER, at = @At("HEAD"))
	private void bsb$remember(MovingBlockEntity te, double dx, double dy, double dz, float tickDelta, CallbackInfo ci) {
		PistonModels.current = te;
		PistonModels.tickDelta = tickDelta;
	}

	@Inject(method = RENDER, at = @At("RETURN"))
	private void bsb$forget(MovingBlockEntity te, double dx, double dy, double dz, float tickDelta, CallbackInfo ci) {
		PistonModels.current = null;
	}

	@Redirect(method = RENDER, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/render/block/BlockRenderer;renderPistonHead(Lnet/minecraft/block/Block;IIIZ)V"))
	private void bsb$head(BlockRenderer renderer, Block block, int x, int y, int z, boolean flag) {
		if (!PistonModels.renderHead(renderer.world, x, y, z)) {
			renderer.renderPistonHead(block, x, y, z, flag);
		}
	}

	@Redirect(method = RENDER, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/render/block/BlockRenderer;renderExtendedPistonBase(Lnet/minecraft/block/Block;III)V"))
	private void bsb$base(BlockRenderer renderer, Block block, int x, int y, int z) {
		if (!PistonModels.renderExtendedBase(renderer.world, block, x, y, z)) {
			renderer.renderExtendedPistonBase(block, x, y, z);
		}
	}
}
