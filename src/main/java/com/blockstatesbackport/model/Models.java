package com.blockstatesbackport.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.resource.Identifier;
import net.minecraft.client.resource.pack.ResourcePack;

import com.blockstatesbackport.res.Packs;

/**
 * Model files by id ("ns:block/x"), loaded on demand from the active packs, and their resolved parent chains.
 */
public final class Models {

	public static final String MISSING = "missingno";

	/** A model with its parent chain resolved (leaf first). */
	public static final class Resolved {
		public final List<Defs.ModelDef> chain;
		public final List<Defs.Element> elements;
		public final boolean ambientOcclusion;
		public final boolean fromUser;
		/** "generated", "entity", "missing" ... when the chain ends in a builtin parent, else null. */
		public final String builtin;
		public final Map<String, Defs.Transform> display;

		Resolved(List<Defs.ModelDef> chain, String builtin) {
			this.chain = chain;
			this.builtin = builtin;
			List<Defs.Element> els = Collections.emptyList();
			boolean user = false;
			for (Defs.ModelDef m : chain) {
				if (els.isEmpty()) {
					els = m.elements;
				}
				user |= m.fromUser;
			}
			this.elements = els;
			this.fromUser = user;
			// 1.12 takes the flag from the root of the chain
			this.ambientOcclusion = chain.get(chain.size() - 1).ambientOcclusion;
			Map<String, Defs.Transform> disp = new HashMap<>();
			for (int i = chain.size() - 1; i >= 0; i--) {
				disp.putAll(chain.get(i).display);
			}
			this.display = disp;
		}

		/** Resolves a face texture ("#side" or "side") to a texture path, or MISSING. */
		public String texture(String ref) {
			return lookup(ref.charAt(0) == '#' ? ref : "#" + ref, 0);
		}

		private String lookup(String ref, int depth) {
			if (depth > 32) {
				return MISSING;
			}
			String key = ref.substring(1);
			for (Defs.ModelDef m : chain) {
				String v = m.textures.get(key);
				if (v != null) {
					return v.isEmpty() ? MISSING : v.charAt(0) == '#' ? lookup(v, depth + 1) : v;
				}
			}
			return MISSING;
		}
	}

	private final Map<String, Defs.ModelDef> files = new HashMap<>();
	private final Map<String, Resolved> resolved = new HashMap<>();
	private final List<String> errors;

	public Models(List<String> errors) {
		this.errors = errors;
	}

	/** @return the resolved model, or null if a file in the chain is missing or broken (logged). */
	public Resolved resolve(String id) {
		if (resolved.containsKey(id)) {
			return resolved.get(id);
		}
		List<Defs.ModelDef> chain = new ArrayList<>();
		String builtin = null;
		String cur = id;
		Resolved r = null;
		boolean ok = true;
		while (cur != null) {
			String path = cur.substring(cur.indexOf(':') + 1);
			if (path.startsWith("builtin/")) {
				builtin = path.substring("builtin/".length());
				break;
			}
			if (chain.size() > 64) {
				errors.add("model " + id + ": parent chain too long or circular");
				ok = false;
				break;
			}
			Defs.ModelDef m = file(cur);
			if (m == null) {
				errors.add("model " + cur + " not found" + (cur.equals(id) ? "" : " (parent of " + id + ")"));
				ok = false;
				break;
			}
			chain.add(m);
			cur = m.parent;
		}
		if (ok && !chain.isEmpty()) {
			r = new Resolved(chain, builtin);
		}
		resolved.put(id, r);
		return r;
	}

	private Defs.ModelDef file(String id) {
		if (files.containsKey(id)) {
			return files.get(id);
		}
		int c = id.indexOf(':');
		Identifier loc = new Identifier(id.substring(0, c), "models/" + id.substring(c + 1) + ".json");
		Defs.ModelDef def = null;
		ResourcePack pack = Packs.top(loc);
		if (pack != null) {
			try {
				def = Defs.parseModel(id, Packs.readJson(pack, loc));
				def.fromUser = Packs.isUser(pack);
			} catch (Exception e) {
				errors.add("model " + id + " in '" + pack.getName() + "' is broken: " + e.getMessage());
			}
		}
		files.put(id, def);
		return def;
	}
}
