package com.blockstatesbackport.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.resource.manager.ResourceManager;

import com.blockstatesbackport.ModelManager;

@Mixin(TextureAtlas.class)
public class TextureAtlasMixin {

	/** Block atlas only: read blockstates/models and register their textures before stitching. */
	@Inject(method = "loadAndStitch", at = @At("HEAD"))
	private void bsb$prepareModels(ResourceManager manager, CallbackInfo ci) {
		if (((TextureAtlasAccessor) this).bsb$type() == 0) {
			ModelManager.prepare((TextureAtlas) (Object) this);
		}
	}

	/**
	 * registerSprites() clears the sprite list. Vanilla only calls it from the constructor, but OptiFine calls it
	 * again inside loadAndStitch, after prepare(); put the model textures back each time.
	 */
	@Inject(method = "registerSprites", at = @At("TAIL"))
	private void bsb$keepModelSprites(CallbackInfo ci) {
		if (((TextureAtlasAccessor) this).bsb$type() == 0) {
			ModelManager.reRegisterSprites((TextureAtlas) (Object) this);
		}
	}
}
