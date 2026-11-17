package com.blockstatesbackport.state;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.world.WorldView;

/**
 * Turns a 1.7.10 block (metadata + world) into a 1.12 style state: a blockstate file plus property values.
 * <p>
 * The hot path only computes an int key (metadata in the low 4 bits, neighbor/tile entity bits above); the
 * description of a key is computed once and cached by the caller.
 */
public abstract class Mapper {

	/** Output of describe(). */
	public static final class Desc {
		/** "ns:name" of the blockstate file. */
		public String file;
		/** Every property of the state, including ones the variant key ignores (multipart can test them). */
		public final Map<String, String> props = new TreeMap<>();
		/** Explicit variant string for the few vanilla blocks that do not use "prop=value" (e.g. double slabs). */
		public String variant;

		public Desc put(String k, String v) {
			props.put(k, v);
			return this;
		}

		public Desc put(String k, boolean v) {
			props.put(k, v ? "true" : "false");
			return this;
		}

		public Desc put(String k, int v) {
			props.put(k, Integer.toString(v));
			return this;
		}
	}

	private Set<String> ignored = Collections.emptySet();
	/** True when key() reads the world; false means key == metadata. */
	public boolean world;
	/** Mapper for blocks without a dedicated table (other mods): tries "meta=N" before "normal". */
	public boolean generic;

	public Mapper ignore(String... names) {
		ignored = new HashSet<>(Arrays.asList(names));
		return this;
	}

	public int key(WorldView w, int x, int y, int z, int meta) {
		return meta;
	}

	public abstract void describe(int key, Desc out);

	/** 1.12 variant string: sorted "prop=value" pairs without ignored properties, or "normal". */
	public String variant(Desc d) {
		if (d.variant != null) {
			return d.variant;
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : d.props.entrySet()) {
			if (ignored.contains(e.getKey())) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(e.getKey()).append('=').append(e.getValue());
		}
		return sb.length() == 0 ? "normal" : sb.toString();
	}
}
