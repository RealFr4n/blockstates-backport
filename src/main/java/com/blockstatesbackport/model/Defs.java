package com.blockstatesbackport.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Parsed blockstate and model files, following the 1.12 file format.
 */
public final class Defs {

	private Defs() {
	}

	// ---------------------------------------------------------------- blockstates

	public static final class Variant {
		public final String model;
		public final int x;
		public final int y;
		public final boolean uvlock;
		public final int weight;

		Variant(String model, int x, int y, boolean uvlock, int weight) {
			this.model = model;
			this.x = x;
			this.y = y;
			this.uvlock = uvlock;
			this.weight = weight;
		}

		String key() {
			return model + "|" + x + "|" + y + "|" + uvlock;
		}
	}

	public interface Condition {
		/** @return true if the state matches; unknown properties throw. */
		boolean test(Map<String, String> props);
	}

	public static final class Part {
		public final Condition when;
		public final List<Variant> apply;

		Part(Condition when, List<Variant> apply) {
			this.when = when;
			this.apply = apply;
		}
	}

	public static final class StateDef {
		public final Map<String, List<Variant>> variants = new LinkedHashMap<>();
		public List<Part> multipart;
	}

	/** Merges blockstate files from several packs (lowest priority first) the way 1.12 does. */
	public static StateDef merge(List<StateDef> defs) {
		StateDef out = new StateDef();
		for (StateDef d : defs) {
			if (d.multipart != null) {
				out.variants.clear();
				out.multipart = d.multipart;
			}
			out.variants.putAll(d.variants);
		}
		return out;
	}

	public static StateDef parseState(JsonElement root) {
		JsonObject json = root.getAsJsonObject();
		StateDef def = new StateDef();
		if (json.has("variants")) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("variants").entrySet()) {
				def.variants.put(e.getKey(), parseVariants(e.getValue()));
			}
		}
		if (json.has("multipart")) {
			List<Part> parts = new ArrayList<>();
			for (JsonElement p : json.getAsJsonArray("multipart")) {
				JsonObject o = p.getAsJsonObject();
				Condition when = o.has("when") ? parseCondition(o.getAsJsonObject("when")) : props -> true;
				parts.add(new Part(when, parseVariants(o.get("apply"))));
			}
			def.multipart = parts;
		}
		if (def.variants.isEmpty() && def.multipart == null) {
			throw new JsonParseException("Neither 'variants' nor 'multipart' found");
		}
		return def;
	}

	private static List<Variant> parseVariants(JsonElement e) {
		List<Variant> list = new ArrayList<>();
		if (e.isJsonArray()) {
			for (JsonElement v : e.getAsJsonArray()) {
				list.add(parseVariant(v.getAsJsonObject()));
			}
		} else {
			list.add(parseVariant(e.getAsJsonObject()));
		}
		return list;
	}

	private static Variant parseVariant(JsonObject o) {
		String model = modelId(str(o, "model", null));
		int x = integer(o, "x", 0);
		int y = integer(o, "y", 0);
		if (x % 90 != 0 || y % 90 != 0) {
			throw new JsonParseException("Invalid rotation x: " + x + ", y: " + y);
		}
		int weight = integer(o, "weight", 1);
		if (weight < 1) {
			throw new JsonParseException("Invalid weight " + weight + " found, expected integer >= 1");
		}
		return new Variant(model, Math.floorMod(x, 360), Math.floorMod(y, 360), bool(o, "uvlock", false), weight);
	}

	/** Blockstate model references live under models/block/ (1.8-1.12). */
	static String modelId(String name) {
		if (name == null) {
			throw new JsonParseException("Missing model");
		}
		int c = name.indexOf(':');
		return c < 0 ? "minecraft:block/" + name : name.substring(0, c) + ":block/" + name.substring(c + 1);
	}

	private static Condition parseCondition(JsonObject json) {
		if (json.entrySet().isEmpty()) {
			throw new JsonParseException("No elements found in selector");
		}
		if (json.entrySet().size() == 1) {
			if (json.has("OR")) {
				return any(json.getAsJsonArray("OR"));
			}
			if (json.has("AND")) {
				return every(json.getAsJsonArray("AND"));
			}
		}
		List<Condition> all = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : json.entrySet()) {
			all.add(property(e.getKey(), e.getValue().getAsString()));
		}
		return all.size() == 1 ? all.get(0) : props -> {
			for (Condition c : all) {
				if (!c.test(props)) {
					return false;
				}
			}
			return true;
		};
	}

	private static Condition any(JsonArray arr) {
		List<Condition> list = new ArrayList<>();
		for (JsonElement e : arr) {
			list.add(parseCondition(e.getAsJsonObject()));
		}
		return props -> {
			for (Condition c : list) {
				if (c.test(props)) {
					return true;
				}
			}
			return false;
		};
	}

	private static Condition every(JsonArray arr) {
		List<Condition> list = new ArrayList<>();
		for (JsonElement e : arr) {
			list.add(parseCondition(e.getAsJsonObject()));
		}
		return props -> {
			for (Condition c : list) {
				if (!c.test(props)) {
					return false;
				}
			}
			return true;
		};
	}

	/** key=value, key=a|b, key=!a|b. */
	private static Condition property(String key, String raw) {
		boolean negate = !raw.isEmpty() && raw.charAt(0) == '!';
		String body = negate ? raw.substring(1) : raw;
		List<String> values = new ArrayList<>();
		for (String v : body.split("\\|")) {
			if (!v.isEmpty()) {
				values.add(v);
			}
		}
		if (values.isEmpty()) {
			throw new JsonParseException("Empty value for property " + key);
		}
		return props -> {
			String v = props.get(key);
			if (v == null) {
				throw new IllegalStateException("has no property: " + key);
			}
			return values.contains(v) != negate;
		};
	}

	// ---------------------------------------------------------------- models

	public static final class Face {
		public float[] uv;
		public final String texture;
		public final int cull;
		public final int rotation;
		public final int tint;

		Face(float[] uv, String texture, int cull, int rotation, int tint) {
			this.uv = uv;
			this.texture = texture;
			this.cull = cull;
			this.rotation = rotation;
			this.tint = tint;
		}
	}

	public static final class Element {
		public final float[] from;
		public final float[] to;
		/** Rotation origin in block units (0..1), axis 0/1/2 = x/y/z or -1 for none. */
		public final float[] rotOrigin;
		public final int rotAxis;
		public final float rotAngle;
		public final boolean rescale;
		public final boolean shade;
		public final Face[] faces = new Face[6];

		Element(float[] from, float[] to, float[] rotOrigin, int rotAxis, float rotAngle, boolean rescale, boolean shade) {
			this.from = from;
			this.to = to;
			this.rotOrigin = rotOrigin;
			this.rotAxis = rotAxis;
			this.rotAngle = rotAngle;
			this.rescale = rescale;
			this.shade = shade;
		}
	}

	/** rotation (degrees), translation and scale of one display slot. */
	public static final class Transform {
		public final float[] rotation;
		public final float[] translation;
		public final float[] scale;

		public Transform(float[] rotation, float[] translation, float[] scale) {
			this.rotation = rotation;
			this.translation = translation;
			this.scale = scale;
		}
	}

	public static final class ModelDef {
		public final String id;
		public String parent;
		public final Map<String, String> textures = new HashMap<>();
		public List<Element> elements = Collections.emptyList();
		public boolean ambientOcclusion = true;
		public final Map<String, Transform> display = new HashMap<>();
		/** Set when the file came from a user resource pack. */
		public boolean fromUser;

		ModelDef(String id) {
			this.id = id;
		}
	}

	public static ModelDef parseModel(String id, JsonElement root) {
		JsonObject json = root.getAsJsonObject();
		ModelDef m = new ModelDef(id);
		String parent = str(json, "parent", "");
		m.parent = parent.isEmpty() ? null : (parent.indexOf(':') < 0 ? "minecraft:" + parent : parent);
		if (json.has("textures")) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("textures").entrySet()) {
				m.textures.put(e.getKey(), e.getValue().getAsString());
			}
		}
		m.ambientOcclusion = bool(json, "ambientocclusion", true);
		if (json.has("elements")) {
			List<Element> list = new ArrayList<>();
			for (JsonElement e : json.getAsJsonArray("elements")) {
				list.add(parseElement(e.getAsJsonObject()));
			}
			m.elements = list;
		}
		if (json.has("display")) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("display").entrySet()) {
				JsonObject t = e.getValue().getAsJsonObject();
				m.display.put(e.getKey(), new Transform(vec(t, "rotation", new float[3]), vec(t, "translation", new float[3]),
					vec(t, "scale", new float[]{1, 1, 1})));
			}
		}
		return m;
	}

	private static Element parseElement(JsonObject o) {
		float[] from = bounded(vec(o, "from", null), "from");
		float[] to = bounded(vec(o, "to", null), "to");
		float[] origin = null;
		int axis = -1;
		float angle = 0;
		boolean rescale = false;
		if (o.has("rotation")) {
			JsonObject r = o.getAsJsonObject("rotation");
			origin = vec(r, "origin", null);
			for (int i = 0; i < 3; i++) {
				origin[i] *= 0.0625F;
			}
			String a = str(r, "axis", "").toLowerCase();
			axis = "x".equals(a) ? 0 : "y".equals(a) ? 1 : "z".equals(a) ? 2 : -2;
			if (axis == -2) {
				throw new JsonParseException("Invalid rotation axis: " + a);
			}
			angle = r.get("angle").getAsFloat();
			if (angle != 0 && Math.abs(angle) != 22.5F && Math.abs(angle) != 45F) {
				throw new JsonParseException("Invalid rotation " + angle + " found, only -45/-22.5/0/22.5/45 allowed");
			}
			rescale = bool(r, "rescale", false);
		}
		Element el = new Element(from, to, origin, axis, angle, rescale, bool(o, "shade", true));
		int count = 0;
		for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("faces").entrySet()) {
			int dir = Dir.byName(e.getKey());
			if (dir < 0) {
				throw new JsonParseException("Unknown facing: " + e.getKey());
			}
			JsonObject f = e.getValue().getAsJsonObject();
			float[] uv = f.has("uv") ? vec4(f) : null;
			int rot = integer(f, "rotation", 0);
			if (rot < 0 || rot % 90 != 0 || rot / 90 > 3) {
				throw new JsonParseException("Invalid rotation " + rot + " found, only 0/90/180/270 allowed");
			}
			String tex = str(f, "texture", null);
			if (tex == null) {
				throw new JsonParseException("Face without texture");
			}
			el.faces[dir] = new Face(uv == null ? defaultUv(from, to, dir) : uv, tex, Dir.byName(str(f, "cullface", "")),
				rot, integer(f, "tintindex", -1));
			count++;
		}
		if (count == 0) {
			throw new JsonParseException("Expected between 1 and 6 unique faces, got 0");
		}
		return el;
	}

	/** A full 16x16x16 cube with the same texture reference on all six faces, each culled on its own side. */
	public static Element fullCube(String texture) {
		float[] from = {0, 0, 0};
		float[] to = {16, 16, 16};
		Element el = new Element(from, to, null, -1, 0, false, true);
		for (int d = 0; d < 6; d++) {
			el.faces[d] = new Face(defaultUv(from, to, d), texture, d, 0, -1);
		}
		return el;
	}

	/** UVs taken from the element box when a face has none. */
	private static float[] defaultUv(float[] f, float[] t, int dir) {
		switch (dir) {
			case Dir.DOWN:
				return new float[]{f[0], 16 - t[2], t[0], 16 - f[2]};
			case Dir.UP:
				return new float[]{f[0], f[2], t[0], t[2]};
			case Dir.SOUTH:
				return new float[]{f[0], 16 - t[1], t[0], 16 - f[1]};
			case Dir.WEST:
				return new float[]{f[2], 16 - t[1], t[2], 16 - f[1]};
			case Dir.EAST:
				return new float[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
			case Dir.NORTH:
			default:
				return new float[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
		}
	}

	private static float[] bounded(float[] v, String name) {
		for (float f : v) {
			if (f < -16 || f > 32) {
				throw new JsonParseException("'" + name + "' specifier exceeds the allowed boundaries");
			}
		}
		return v;
	}

	private static float[] vec(JsonObject o, String key, float[] def) {
		if (!o.has(key)) {
			if (def == null) {
				throw new JsonParseException("Missing " + key);
			}
			return def;
		}
		JsonArray a = o.getAsJsonArray(key);
		if (a.size() != 3) {
			throw new JsonParseException("Expected 3 " + key + " values, found: " + a.size());
		}
		return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	private static float[] vec4(JsonObject o) {
		JsonArray a = o.getAsJsonArray("uv");
		if (a.size() != 4) {
			throw new JsonParseException("Expected 4 uv values, found: " + a.size());
		}
		return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat()};
	}

	private static String str(JsonObject o, String key, String def) {
		return o.has(key) ? o.get(key).getAsString() : def;
	}

	private static int integer(JsonObject o, String key, int def) {
		return o.has(key) ? o.get(key).getAsInt() : def;
	}

	private static boolean bool(JsonObject o, String key, boolean def) {
		return o.has(key) ? o.get(key).getAsBoolean() : def;
	}
}
