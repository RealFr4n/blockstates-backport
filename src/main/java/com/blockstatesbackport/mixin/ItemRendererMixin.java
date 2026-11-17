package com.blockstatesbackport.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.item.ItemStack;

import com.blockstatesbackport.render.ItemModelRenderer;

/** Marks GUI item rendering so block item models can use their "gui" display transform. */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

	@Inject(method = "renderGuiItem", at = @At("HEAD"))
	private void bsb$guiStart(TextRenderer text, TextureManager textures, ItemStack stack, int x, int y, CallbackInfo ci) {
		ItemModelRenderer.inGui = true;
	}

	@Inject(method = "renderGuiItem", at = @At("RETURN"))
	private void bsb$guiEnd(TextRenderer text, TextureManager textures, ItemStack stack, int x, int y, CallbackInfo ci) {
		ItemModelRenderer.inGui = false;
	}
}
