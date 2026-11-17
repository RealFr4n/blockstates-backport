package com.blockstatesbackport.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;

@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {

	@Accessor("sourcedSprites")
	Map<String, TextureAtlasSprite> bsb$sourcedSprites();

	@Accessor("type")
	int bsb$type();
}
