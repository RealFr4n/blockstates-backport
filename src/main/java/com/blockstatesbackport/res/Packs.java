package com.blockstatesbackport.res;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.Identifier;
import net.minecraft.client.resource.pack.BuiltInResourcePack;
import net.minecraft.client.resource.pack.ResourcePack;

import com.blockstatesbackport.BlockstatesBackport;
import com.blockstatesbackport.Config;

/**
 * The resource packs of the last reload, lowest priority first (same order the resource manager uses).
 */
public final class Packs {

	private static final String JAR_SUFFIX = "libraries/com/mojang/minecraft/1.12.2/minecraft-1.12.2-client.jar";

	private static List<ResourcePack> packs = Collections.emptyList();
	private static VanillaLayerPack vanilla;
	private static String vanillaPath;

	private Packs() {
	}

	/**
	 * Called right before every resource reload with the pack list (lowest priority first). The list is changed
	 * in place and kept by reference: other mods (OSL) may still add packs to it at the same call site.
	 */
	public static List<ResourcePack> onReload(List<ResourcePack> list) {
		Config.load(Minecraft.getInstance().gameDir);
		list.removeIf(p -> p instanceof VanillaLayerPack || p instanceof AliasPack);
		if (Config.enabled) {
			Set<String> namespaces = new HashSet<>();
			for (ResourcePack p : list) {
				namespaces.addAll(p.getNamespaces());
			}
			namespaces.add("minecraft");
			list.add(0, new AliasPack(namespaces));
			VanillaLayerPack layer = vanillaLayer();
			if (layer != null) {
				list.add(0, layer);
			}
		}
		packs = list;
		return list;
	}


	/** User pack = anything except the built-in 1.7.10 assets and the vanilla layer. */
	public static boolean isUser(ResourcePack p) {
		return !(p instanceof VanillaLayerPack) && !(p instanceof BuiltInResourcePack) && !(p instanceof AliasPack);
	}

	/** Highest-priority pack that has the resource, or null. */
	public static ResourcePack top(Identifier id) {
		for (int i = packs.size() - 1; i >= 0; i--) {
			ResourcePack p = packs.get(i);
			if (p.hasResource(id)) {
				return p;
			}
		}
		return null;
	}

	/** Every pack that has the resource, lowest priority first. */
	public static List<ResourcePack> all(Identifier id) {
		List<ResourcePack> out = new ArrayList<>(2);
		for (ResourcePack p : packs) {
			if (p.hasResource(id)) {
				out.add(p);
			}
		}
		return out;
	}

	public static JsonElement readJson(ResourcePack pack, Identifier id) throws Exception {
		try (InputStream in = pack.getResource(id); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			return new JsonParser().parse(r);
		}
	}

	private static VanillaLayerPack vanillaLayer() {
		File jar = findJar();
		String path = jar == null ? null : jar.getAbsolutePath();
		if (path == null) {
			if (vanillaPath != null || vanilla == null) {
				BlockstatesBackport.LOG.warn("No 1.12.2 client jar found (vanillaJar={}); vanilla parent models and blockstates are unavailable", Config.vanillaJar);
			}
			closeVanilla();
			return null;
		}
		if (!path.equals(vanillaPath)) {
			closeVanilla();
			try {
				vanilla = new VanillaLayerPack(jar);
				vanillaPath = path;
				BlockstatesBackport.LOG.info("Vanilla layer: {}", path);
			} catch (Exception e) {
				BlockstatesBackport.LOG.error("Cannot open vanilla jar {}: {}", path, e.toString());
				vanilla = null;
			}
		}
		return vanilla;
	}

	private static void closeVanilla() {
		if (vanilla != null) {
			vanilla.close();
		}
		vanilla = null;
		vanillaPath = null;
	}

	private static File findJar() {
		if (!"auto".equalsIgnoreCase(Config.vanillaJar) && !Config.vanillaJar.isEmpty()) {
			File f = new File(Config.vanillaJar);
			return f.isFile() ? f : null;
		}
		// The launcher keeps client jars in <root>/libraries; game dirs live a few levels below the root.
		File dir = Minecraft.getInstance().gameDir.getAbsoluteFile();
		for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
			File f = new File(dir, JAR_SUFFIX);
			if (f.isFile()) {
				return f;
			}
		}
		return null;
	}
}
