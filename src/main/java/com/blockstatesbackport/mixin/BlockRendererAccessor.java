package com.blockstatesbackport.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.client.render.texture.Sprite;

@Mixin(BlockRenderer.class)
public interface BlockRendererAccessor {

	@Accessor("forcedSprite")
	Sprite bsb$forcedSprite();

	@Accessor("renderFaceAlways")
	boolean bsb$renderFaceAlways();
}
