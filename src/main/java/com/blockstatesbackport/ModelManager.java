package com.blockstatesbackport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.Identifier;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.world.WorldView;

import com.blockstatesbackport.mixin.TextureAtlasAccessor;
import com.blockstatesbackport.model.BakedModel;
import com.blockstatesbackport.model.Baker;
import com.blockstatesbackport.model.Defs;
import com.blockstatesbackport.model.Dir;
import com.blockstatesbackport.model.Models;
import com.blockstatesbackport.model.Quad;
import com.blockstatesbackport.model.StateModel;
import com.blockstatesbackport.res.Packs;
import com.blockstatesbackport.state.IntCache;
import com.blockstatesbackport.state.Mapper;
import com.blockstatesbackport.state.VanillaMappers;

/**
 * Loads blockstates and models when the block atlas is rebuilt, registers their textures in it, and resolves
 * block states to baked models (once per state, cached).
 */
public final class ModelManager {

	/** Prefix of atlas names for textures outside textures/blocks/ (the atlas rejects names with slashes). */
	public static final String PATH_SPRITE = "bsb#";
	private static final Object NONE = new Object();

	private static final class Entry {
		final String name;
		final Mapper mapper;
		final Map<String, Defs.StateDef> defs;
		final IntCache cache = new IntCache();
		final boolean cutout;

		Entry(String name, Mapper mapper, Map<String, Defs.StateDef> defs) {
			this.name = name;
			this.mapper = mapper;
			this.defs = defs;
			this.cutout = CUTOUT.contains(name);
		}
	}

	/** Blocks whose 1.12 getRenderLayer() is CUTOUT (no mipmaps), from the 1.12.2 block classes. */
	private static final Set<String> CUTOUT = new HashSet<>();

	static {
		for (String s : new String[]{"rail", "golden_rail", "detector_rail", "activator_rail", "beacon", "bed", "brewing_stand",
			"cactus", "cake", "carrots", "potatoes", "wheat", "web", "cocoa", "unpowered_comparator", "powered_comparator",
			"unpowered_repeater", "powered_repeater", "deadbush", "tallgrass", "double_plant", "sapling", "yellow_flower",
			"red_flower", "brown_mushroom", "red_mushroom", "waterlily", "nether_wart", "melon_stem", "pumpkin_stem", "reeds",
			"wooden_door", "iron_door", "trapdoor", "fire", "flower_pot", "glass", "ladder", "mob_spawner", "torch",
			"redstone_torch", "unlit_redstone_torch", "redstone_wire", "vine"}) {
			CUTOUT.add("minecraft:" + s);
		}
	}

	/** True for blocks drawn from JSON whose 1.12 render layer is CUTOUT. */
	public static boolean isCutout(Block block) {
		int id = Block.getId(block);
		return id >= 0 && id < entries.length && entries[id] != null && entries[id].cutout;
	}

	private static Entry[] entries = new Entry[0];
	/** Item model id per block id and metadata, for blocks drawn as 3D items. */
	private static String[][] itemIds = new String[0][];
	private static BakedModel[][] itemCache = new BakedModel[0][];
	private static final BakedModel NO_ITEM = new BakedModel();
	/** 1.12 block item model names that differ from the blockstate file, by item damage. */
	private static final Map<String, String[]> ITEM_NAMES = new HashMap<>();

	static {
		ITEM_NAMES.put("minecraft:anvil", new String[]{"anvil_intact", "anvil_slightly_damaged", "anvil_very_damaged"});
		ITEM_NAMES.put("minecraft:fence", new String[]{"oak_fence"});
		ITEM_NAMES.put("minecraft:fence_gate", new String[]{"oak_fence_gate"});
		ITEM_NAMES.put("minecraft:sponge", new String[]{"sponge", "sponge_wet"});
	}
	private static int active;
	private static TextureAtlas atlas;
	private static Models models;
	private static final Map<String, BakedModel> BAKED = new HashMap<>();
	private static final Set<String> OUR_SPRITES = new HashSet<>();
	private static final Set<String> WARNED = new HashSet<>();

	private ModelManager() {
	}

	public static boolean isActive() {
		return active > 0;
	}

	public static TextureAtlas atlas() {
		return atlas;
	}

	/** Atlas sprite name for a model texture path. */
	public static String spriteName(String texture) {
		int c = texture.indexOf(':');
		String ns = c < 0 ? "minecraft" : texture.substring(0, c);
		String path = texture.substring(c + 1);
		String prefix = "minecraft".equals(ns) ? "" : ns + ":";
		if (Models.MISSING.equals(path)) {
			return Models.MISSING;
		}
		if (path.startsWith("blocks/") && path.indexOf('/', 7) < 0) {
			return prefix + path.substring(7);
		}
		return prefix + PATH_SPRITE + path.replace('/', '#');
	}

	public static TextureAtlasSprite sprite(String texture) {
		return atlas.getSprite(spriteName(texture));
	}

	/** Before the block atlas stitches: read everything and register the textures. */
	public static void prepare(TextureAtlas blocks) {
		long start = System.nanoTime();
		atlas = blocks;
		BAKED.clear();
		WARNED.clear();
		active = 0;
		Map<String, TextureAtlasSprite> sourced = ((TextureAtlasAccessor) blocks).bsb$sourcedSprites();
		for (String s : OUR_SPRITES) {
			sourced.remove(s);
		}
		OUR_SPRITES.clear();
		entries = new Entry[4096];
		itemIds = new String[4096][];
		itemCache = new BakedModel[4096][];
		if (!Config.enabled) {
			BlockstatesBackport.LOG.info("Disabled in config; 1.7.10 rendering everywhere");
			return;
		}
		List<String> errors = new ArrayList<>();
		models = new Models(errors);
		Set<String> textures = new LinkedHashSet<>();
		int files = 0;
		int items = 0;
		for (Block block : Block.REGISTRY) {
			String name = Block.REGISTRY.getKey(block);
			int id = Block.getId(block);
			if (name == null || id < 0 || id >= 4096 || VanillaMappers.invisible(name)) {
				continue;
			}
			Mapper mapper = VanillaMappers.get(name);
			if (mapper == null) {
				if (name.startsWith("minecraft:")) {
					continue;
				}
				mapper = generic(name);
			}
			Set<String> fileNames = new LinkedHashSet<>();
			String[] metaFiles = new String[16];
			Mapper.Desc d = new Mapper.Desc();
			for (int meta = 0; meta < 16; meta++) {
				d.props.clear();
				d.variant = null;
				mapper.describe(meta, d);
				fileNames.add(d.file);
				metaFiles[meta] = d.file;
			}
			Map<String, Defs.StateDef> defs = new LinkedHashMap<>();
			boolean user = false;
			for (String file : fileNames) {
				int c = file.indexOf(':');
				Identifier loc = new Identifier(file.substring(0, c), "blockstates/" + file.substring(c + 1) + ".json");
				List<Defs.StateDef> parts = new ArrayList<>();
				for (ResourcePack pack : Packs.all(loc)) {
					try {
						parts.add(Defs.parseState(Packs.readJson(pack, loc)));
						user |= Packs.isUser(pack);
					} catch (Exception e) {
						errors.add("blockstate " + file + " in '" + pack.getName() + "' is broken: " + e.getMessage());
					}
				}
				if (!parts.isEmpty()) {
					defs.put(file, Defs.merge(parts));
					files++;
				}
			}
			Set<String> tex = new LinkedHashSet<>();
			for (Defs.StateDef def : defs.values()) {
				for (List<Defs.Variant> vs : def.variants.values()) {
					user |= collect(vs, tex);
				}
				if (def.multipart != null) {
					for (Defs.Part p : def.multipart) {
						user |= collect(p.apply, tex);
					}
				}
			}
			boolean blockActive = !defs.isEmpty() && (user || Config.allBlocks);
			if (blockActive) {
				entries[id] = new Entry(name, mapper, defs);
				textures.addAll(tex);
				active++;
			}
			if (Config.items) {
				items += prepareItems(name, id, metaFiles, blockActive, textures);
			}
		}
		for (String t : textures) {
			String s = spriteName(t);
			if (!Models.MISSING.equals(s) && !sourced.containsKey(s)) {
				blocks.registerSprite(s);
				OUR_SPRITES.add(s);
			}
		}
		for (String e : errors) {
			BlockstatesBackport.LOG.warn(e);
		}
		BlockstatesBackport.LOG.info("Read {} blockstate files; {} blocks and {} block items use JSON models, {} textures registered ({} new), {} problems, {} ms",
			files, active, items, textures.size(), OUR_SPRITES.size(), errors.size(), (System.nanoTime() - start) / 1000000);
	}

	/**
	 * Item models follow the 1.12 names, which for block items match the blockstate file of the metadata
	 * (models/item/oak_planks.json ...), except the few in ITEM_NAMES (indexed by item damage). Flat (builtin/generated) item models are left to
	 * 1.7.10, which already draws those items as flat icons.
	 */
	private static int prepareItems(String name, int id, String[] metaFiles, boolean blockActive, Set<String> textures) {
		int count = 0;
		for (int meta = 0; meta < 16; meta++) {
			String[] special = ITEM_NAMES.get(name);
			String file = special == null ? metaFiles[meta] : "minecraft:" + special[meta < special.length ? meta : 0];
			int c = file.indexOf(':');
			String itemId = file.substring(0, c) + ":item/" + file.substring(c + 1);
			if (Packs.top(new Identifier(file.substring(0, c), "models/item/" + file.substring(c + 1) + ".json")) == null) {
				continue;
			}
			Models.Resolved r = models.resolve(itemId);
			if (r == null || r.builtin != null || r.elements.isEmpty() || !(blockActive || r.fromUser || Config.allBlocks)) {
				continue;
			}
			for (Defs.Element el : r.elements) {
				for (Defs.Face f : el.faces) {
					if (f != null) {
						textures.add(r.texture(f.texture));
					}
				}
			}
			if (itemIds[id] == null) {
				itemIds[id] = new String[16];
				itemCache[id] = new BakedModel[16];
			}
			itemIds[id][meta] = itemId;
			count++;
		}
		return count > 0 ? 1 : 0;
	}

	/** Item model for a block drawn as an item, or null to keep the 1.7.10 renderer. */
	public static BakedModel itemModel(Block block, int meta) {
		int id = Block.getId(block);
		if (id < 0 || id >= itemIds.length || itemIds[id] == null) {
			return null;
		}
		int m = meta & 15;
		String itemId = itemIds[id][m];
		if (itemId == null) {
			return null;
		}
		BakedModel b = itemCache[id][m];
		if (b == null) {
			Models.Resolved r = models.resolve(itemId);
			b = r == null || r.elements.isEmpty() ? NO_ITEM
				: Baker.bake(r.elements, t -> sprite(r.texture(t)), Baker.rot(0, 0), false, r.ambientOcclusion);
			if (b != NO_ITEM) {
				b.display = r.display;
			}
			itemCache[id][m] = b;
		}
		return b == NO_ITEM ? null : b;
	}

	/** Registers the model textures again after something cleared the atlas sprite list. */
	public static void reRegisterSprites(TextureAtlas blocks) {
		Map<String, TextureAtlasSprite> sourced = ((TextureAtlasAccessor) blocks).bsb$sourcedSprites();
		for (String s : OUR_SPRITES) {
			if (!sourced.containsKey(s)) {
				blocks.registerSprite(s);
			}
		}
	}

	/** Collects textures of the variants' models; returns true if a model comes from a user pack. */
	private static boolean collect(List<Defs.Variant> variants, Set<String> textures) {
		boolean user = false;
		for (Defs.Variant v : variants) {
			Models.Resolved r = models.resolve(v.model);
			if (r == null) {
				continue;
			}
			user |= r.fromUser;
			for (Defs.Element el : r.elements) {
				for (Defs.Face f : el.faces) {
					if (f != null) {
						textures.add(r.texture(f.texture));
					}
				}
			}
		}
		return user;
	}

	private static Mapper generic(String name) {
		Mapper m = new Mapper() {
			@Override
			public void describe(int key, Desc out) {
				out.file = name;
				out.put("meta", key & 15);
			}
		}.ignore("meta");
		m.generic = true;
		return m;
	}

	/** State model for a block in the world, or null to keep the 1.7.10 renderer. */
	public static StateModel get(WorldView w, Block block, int x, int y, int z, int meta) {
		int id = Block.getId(block);
		if (id < 0 || id >= entries.length) {
			return null;
		}
		Entry e = entries[id];
		if (e == null) {
			return null;
		}
		int key = e.mapper.world ? e.mapper.key(w, x, y, z, meta) : meta;
		Object v = e.cache.get(key);
		if (v == null) {
			v = resolve(e, key);
			if (v instanceof StateModel) {
				((StateModel) v).block = e.name;
			}
			e.cache.put(key, v);
		}
		return v == NONE ? null : (StateModel) v;
	}

	/** State model for an explicit mapper key (moving pistons, which are not in the world as themselves). */
	public static StateModel getByKey(Block block, int key) {
		int id = Block.getId(block);
		if (id < 0 || id >= entries.length || entries[id] == null) {
			return null;
		}
		Entry e = entries[id];
		Object v = e.cache.get(key);
		if (v == null) {
			v = resolve(e, key);
			if (v instanceof StateModel) {
				((StateModel) v).block = e.name;
			}
			e.cache.put(key, v);
		}
		return v == NONE ? null : (StateModel) v;
	}

	/** Mapper key for a block in the world (used by tints). */
	public static int key(WorldView w, Block block, int x, int y, int z, int meta) {
		Entry e = entries[Block.getId(block)];
		return e != null && e.mapper.world ? e.mapper.key(w, x, y, z, meta) : meta;
	}

	private static Object resolve(Entry e, int key) {
		if ((key & 7) >= 6 && ("minecraft:piston".equals(e.name) || "minecraft:sticky_piston".equals(e.name))) {
			return sixSidedPiston(e, key);
		}
		Mapper.Desc d = new Mapper.Desc();
		e.mapper.describe(key, d);
		Defs.StateDef def = e.defs.get(d.file);
		if (def == null) {
			return NONE;
		}
		String variant = e.mapper.variant(d);
		List<Defs.Variant> vs = null;
		if (e.mapper.generic) {
			vs = def.variants.get("meta=" + (key & 15));
		}
		if (vs == null) {
			vs = def.variants.get(variant);
		}
		try {
			if (vs != null) {
				StateModel.Choice c = choice(vs);
				return c == null ? NONE : new StateModel(new StateModel.Choice[]{c}, c.first().ambientOcclusion);
			}
			if (def.multipart != null) {
				List<StateModel.Choice> parts = new ArrayList<>();
				Boolean ao = null;
				for (Defs.Part p : def.multipart) {
					StateModel.Choice c = choice(p.apply);
					if (ao == null && c != null) {
						ao = c.first().ambientOcclusion;
					}
					if (c != null && p.when.test(d.props)) {
						parts.add(c);
					}
				}
				return new StateModel(parts.toArray(new StateModel.Choice[0]), ao == null || ao);
			}
		} catch (RuntimeException ex) {
			warn(e.name + " " + d.props + ": " + ex.getMessage());
			return NONE;
		}
		warn("blockstate " + d.file + " has no variant '" + variant + "'; using 1.7.10 rendering for it");
		return NONE;
	}

	/**
	 * Piston metadata 6 and 7 have no facing; 1.7.10 draws them with the front texture on all six faces and 1.12 has
	 * no such state. Do the same with the pack's front texture: the upward face of the facing=up model.
	 */
	private static Object sixSidedPiston(Entry e, int key) {
		int upKey = Dir.UP | (key & 8);
		Object up = e.cache.get(upKey);
		if (up == null) {
			up = resolve(e, upKey);
			if (up instanceof StateModel) {
				((StateModel) up).block = e.name;
			}
			e.cache.put(upKey, up);
		}
		if (!(up instanceof StateModel) || ((StateModel) up).parts.length == 0) {
			return NONE;
		}
		BakedModel upModel = ((StateModel) up).parts[0].first();
		TextureAtlasSprite front = null;
		for (Quad[] quads : upModel.quads) {
			for (Quad q : quads) {
				if (q.face == Dir.UP && (front == null || q.cull == Dir.UP)) {
					front = q.sprite;
				}
			}
		}
		if (front == null) {
			return NONE;
		}
		TextureAtlasSprite sprite = front;
		BakedModel cube = Baker.bake(Collections.singletonList(Defs.fullCube("#front")), t -> sprite, Baker.rot(0, 0), false,
			upModel.ambientOcclusion);
		return new StateModel(new StateModel.Choice[]{new StateModel.Choice(new BakedModel[]{cube}, new int[]{1})},
			((StateModel) up).ambientOcclusion);
	}

	private static StateModel.Choice choice(List<Defs.Variant> variants) {
		List<Defs.Variant> sorted = new ArrayList<>(variants);
		// heaviest first, stable for equal weights (like vanilla's weighted model)
		sorted.sort((a, b) -> Integer.compare(b.weight, a.weight));
		List<BakedModel> list = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		for (Defs.Variant v : sorted) {
			BakedModel m = bake(v);
			if (m != null) {
				list.add(m);
				weights.add(v.weight);
			}
		}
		if (list.isEmpty()) {
			return null;
		}
		int[] w = new int[weights.size()];
		for (int i = 0; i < w.length; i++) {
			w[i] = weights.get(i);
		}
		return new StateModel.Choice(list.toArray(new BakedModel[0]), w);
	}

	private static BakedModel bake(Defs.Variant v) {
		String key = v.model + "|" + v.x + "|" + v.y + "|" + v.uvlock;
		if (BAKED.containsKey(key)) {
			return BAKED.get(key);
		}
		Models.Resolved r = models.resolve(v.model);
		BakedModel m = null;
		if (r != null && !r.elements.isEmpty()) {
			m = Baker.bake(r.elements, t -> sprite(r.texture(t)), Baker.rot(v.x, v.y), v.uvlock, r.ambientOcclusion);
			m.particle = sprite(r.texture("particle"));
			m.display = r.display;
		}
		BAKED.put(key, m);
		return m;
	}

	private static void warn(String msg) {
		if (WARNED.add(msg)) {
			BlockstatesBackport.LOG.warn(msg);
		}
	}
}
