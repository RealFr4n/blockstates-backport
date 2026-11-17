package com.blockstatesbackport.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.pack.ResourcePack;

import com.blockstatesbackport.res.Packs;

@Mixin(Minecraft.class)
public class MinecraftReloadMixin {

	/**
	 * Records the pack list of every reload (startup, F3+T, pack screen) and puts the vanilla layer below it.
	 * Hooked at the call site because OSL replaces the resource manager itself.
	 */
	@ModifyArg(method = "reloadResources", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/resource/manager/ReloadableResourceManager;reload(Ljava/util/List;)V"))
	private List<ResourcePack> bsb$addVanillaLayer(List<ResourcePack> packs) {
		return Packs.onReload(packs);
	}
}
