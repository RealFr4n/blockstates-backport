package com.blockstatesbackport.res;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.client.resource.Identifier;
import net.minecraft.client.resource.metadata.ResourceMetadataSection;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializers;
import net.minecraft.client.resource.pack.ResourcePack;

/**
 * Read-only view of a locally installed newer client jar. Only blockstates, models and block textures are
 * visible, so sounds, languages and GUI textures of the newer version never leak into 1.7.10. It sits below
 * the 1.7.10 default pack, so it only fills gaps. Nothing from it is copied or shipped.
 */
public final class VanillaLayerPack implements ResourcePack {

	private final ZipFile zip;
	private final String name;

	public VanillaLayerPack(File jar) throws IOException {
		this.zip = new ZipFile(jar);
		this.name = "Blockstates Backport vanilla layer (" + jar.getName() + ")";
	}

	static boolean visible(String path) {
		return path.startsWith("blockstates/") || path.startsWith("models/")
			|| path.startsWith("textures/blocks/") || path.startsWith("textures/block/");
	}

	private ZipEntry entry(Identifier id) {
		if (!"minecraft".equals(id.getNamespace()) || !visible(id.getPath())) {
			return null;
		}
		return zip.getEntry("assets/minecraft/" + id.getPath());
	}

	@Override
	public InputStream getResource(Identifier id) throws IOException {
		ZipEntry e = entry(id);
		if (e == null) {
			throw new FileNotFoundException(id.toString());
		}
		return zip.getInputStream(e);
	}

	@Override
	public boolean hasResource(Identifier id) {
		return entry(id) != null;
	}

	@Override
	public Set<String> getNamespaces() {
		return Collections.singleton("minecraft");
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
		return name;
	}

	public void close() {
		try {
			zip.close();
		} catch (IOException ignored) {
		}
	}
}
