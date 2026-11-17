package com.blockstatesbackport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * config/blockstates-backport.properties. Re-read on every resource reload (F3+T).
 */
public final class Config {

	/** Master switch: false = original 1.7.10 rendering everywhere. */
	public static boolean enabled = true;
	/** false = only blocks whose blockstate or models come from a user resource pack use JSON models. */
	public static boolean allBlocks = false;
	/** JSON models for blocks drawn as items (inventory, hand, ground). */
	public static boolean items = true;
	/** 1.12 CUTOUT layer (redstone, torches, plants...) drawn without mipmaps, as in 1.12. */
	public static boolean cutoutLayer = true;
	/** "auto" or a path to a 1.12.2 (or 1.8.9) client jar used as the lowest-priority vanilla layer. */
	public static String vanillaJar = "auto";

	private Config() {
	}

	public static void load(File gameDir) {
		File file = new File(new File(gameDir, "config"), "blockstates-backport.properties");
		Properties p = new Properties();
		if (file.isFile()) {
			try (InputStream in = new FileInputStream(file)) {
				p.load(in);
			} catch (Exception e) {
				BlockstatesBackport.LOG.warn("Could not read {}: {}", file, e.toString());
			}
		}
		enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
		allBlocks = Boolean.parseBoolean(p.getProperty("allBlocks", "false"));
		items = Boolean.parseBoolean(p.getProperty("items", "true"));
		cutoutLayer = Boolean.parseBoolean(p.getProperty("cutoutLayer", "true"));
		vanillaJar = p.getProperty("vanillaJar", "auto").trim();
		if (!file.isFile()) {
			p.setProperty("enabled", String.valueOf(enabled));
			p.setProperty("allBlocks", String.valueOf(allBlocks));
			p.setProperty("items", String.valueOf(items));
			p.setProperty("cutoutLayer", String.valueOf(cutoutLayer));
			p.setProperty("vanillaJar", vanillaJar);
			file.getParentFile().mkdirs();
			try (OutputStream out = new FileOutputStream(file)) {
				p.store(out, "Blockstates Backport. enabled: master switch. allBlocks: also use JSON for blocks no user pack changes."
					+ " items: JSON models for block items. cutoutLayer: 1.12 CUTOUT blocks without mipmaps. vanillaJar: auto or path to a 1.12.2 client jar (never redistributed).");
			} catch (Exception e) {
				BlockstatesBackport.LOG.warn("Could not write {}: {}", file, e.toString());
			}
		}
	}
}
