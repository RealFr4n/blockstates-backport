package com.blockstatesbackport.res;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import net.minecraft.client.resource.Identifier;
import net.minecraft.client.resource.metadata.ResourceMetadataSection;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializers;
import net.minecraft.client.resource.pack.ResourcePack;

import com.blockstatesbackport.ModelManager;

/**
 * Model textures outside textures/blocks/ (for example "block/foo", used by 1.12 packs) get an atlas name like
 * "bsb#block#foo", because the 1.7.10 atlas only loads names without slashes from textures/blocks/. This pack
 * answers the atlas request "textures/blocks/bsb#block#foo.png" (and its .mcmeta) with the real file
 * "textures/block/foo.png" from the highest-priority pack that has it. Going through the normal path keeps it
 * working with atlases patched by other mods (OptiFine does not use the vanilla path method).
 */
public final class AliasPack implements ResourcePack {

	private static final String PREFIX = "textures/blocks/" + ModelManager.PATH_SPRITE;

	private final Set<String> namespaces;

	AliasPack(Set<String> namespaces) {
		this.namespaces = namespaces;
	}

	/** Real location for an alias request, or null. */
	private static Identifier real(Identifier id) {
		String path = id.getPath();
		if (!path.startsWith(PREFIX)) {
			return null;
		}
		return new Identifier(id.getNamespace(), "textures/" + path.substring(PREFIX.length()).replace('#', '/'));
	}

	@Override
	public InputStream getResource(Identifier id) throws IOException {
		Identifier real = real(id);
		ResourcePack pack = real == null ? null : Packs.top(real);
		if (pack == null) {
			throw new FileNotFoundException(id.toString());
		}
		return pack.getResource(real);
	}

	@Override
	public boolean hasResource(Identifier id) {
		Identifier real = real(id);
		return real != null && Packs.top(real) != null;
	}

	@Override
	public Set<String> getNamespaces() {
		return namespaces;
	}

	@Override
	public <T extends ResourceMetadataSection> T getMetadataSection(ResourceMetadataSerializers serializers, String section) {
		return null;
	}

	@Override
	public BufferedImage getIcon() {
		return null;
	}

	@Override
	public String getName() {
		return "Blockstates Backport texture paths";
	}
}
