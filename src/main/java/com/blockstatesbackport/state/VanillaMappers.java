package com.blockstatesbackport.state;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.DiodeBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TripwireBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.FlowerPotBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

import com.blockstatesbackport.model.Dir;

/**
 * State mapping for every vanilla 1.7.10 block that has a model in 1.12. Metadata layouts follow 1.8's
 * getStateFromMeta (1.8 reads 1.7 worlds without converting metadata), names and properties follow the 1.12
 * blockstate files and model location providers. Neighbor-dependent properties follow 1.12's actual-state
 * rules, using the 1.7.10 connection checks so the result matches the block's 1.7.10 behavior.
 */
public final class VanillaMappers {

	interface Describe {
		void describe(int key, Mapper.Desc out);
	}

	interface Key {
		int key(WorldView w, int x, int y, int z, int meta);
	}

	static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "silver", "cyan",
		"purple", "blue", "brown", "green", "red", "black"};
	static final String[] WOOD = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
	static final String[] STONE_SLAB = {"stone", "sandstone", "wood_old", "cobblestone", "brick", "stone_brick", "nether_brick", "quartz"};
	static final String[] RAIL = {"north_south", "east_west", "ascending_east", "ascending_west", "ascending_north", "ascending_south",
		"south_east", "south_west", "north_west", "north_east"};
	static final String[] LEVER = {"down_x", "east", "west", "south", "north", "up_z", "up_x", "down_z"};
	static final String[] MUSHROOM = {"all_inside", "north_west", "north", "north_east", "west", "center", "east", "south_west", "south",
		"south_east", "stem", null, null, null, "all_outside", "all_stem"};
	static final String[] POT = {"empty", "rose", "blue_orchid", "allium", "houstonia", "red_tulip", "orange_tulip", "white_tulip",
		"pink_tulip", "oxeye_daisy", "dandelion", "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling",
		"dark_oak_sapling", "mushroom_red", "mushroom_brown", "dead_bush", "fern", "cactus"};
	static final String[] DOUBLE_PLANT = {"sunflower", "syringa", "double_grass", "double_fern", "double_rose", "paeonia"};

	private static final Map<String, Mapper> MAP = new HashMap<>();

	private VanillaMappers() {
	}

	public static Mapper get(String registryName) {
		if (MAP.isEmpty()) {
			init();
		}
		return MAP.get(registryName);
	}

	/** Blocks that 1.12 draws without a block model (fluids, tile entity renderers); never handled. */
	public static boolean invisible(String name) {
		switch (name) {
			case "minecraft:air": case "minecraft:flowing_water": case "minecraft:water": case "minecraft:flowing_lava":
			case "minecraft:lava": case "minecraft:piston_extension": case "minecraft:chest": case "minecraft:ender_chest":
			case "minecraft:trapped_chest": case "minecraft:standing_sign": case "minecraft:wall_sign": case "minecraft:skull":
			case "minecraft:end_portal": case "minecraft:bed":
				return true;
			default:
				return false;
		}
	}

	// ---------------------------------------------------------------- helpers

	static String mc(String name) {
		return "minecraft:" + name;
	}

	private static String at(String[] arr, int i) {
		return i >= 0 && i < arr.length && arr[i] != null ? arr[i] : arr[0];
	}

	private static String horizontal(int i) {
		return Dir.NAMES[Dir.BY_HORIZONTAL[i & 3]];
	}

	/** Direction.byId as 1.8 does it (wraps around). */
	private static String byId(int i) {
		return Dir.NAMES[Math.abs(i % 6)];
	}

	/** byId, but vertical results become north (furnaces, ladders). */
	private static String byIdHorizontalOnly(int i) {
		int d = Math.abs(i % 6);
		return d <= Dir.UP ? "north" : Dir.NAMES[d];
	}

	private static Mapper of(Describe d) {
		return new Mapper() {
			@Override
			public void describe(int key, Desc out) {
				d.describe(key, out);
			}
		};
	}

	private static Mapper of(Key k, Describe d) {
		Mapper m = new Mapper() {
			@Override
			public int key(WorldView w, int x, int y, int z, int meta) {
				return k.key(w, x, y, z, meta);
			}

			@Override
			public void describe(int key, Desc out) {
				d.describe(key, out);
			}
		};
		m.world = true;
		return m;
	}

	private static void put(String name, Mapper m) {
		MAP.put(mc(name), m);
	}

	private static void simple(String... names) {
		for (String n : names) {
			String f = mc(n);
			put(n, of((k, o) -> o.file = f));
		}
	}

	/** File chosen by metadata from a table (variant property folded into the file name). */
	private static void byMeta(String name, int mask, String prefix, String[] table, String suffix) {
		put(name, of((k, o) -> o.file = mc(prefix + at(table, k & mask) + suffix)));
	}

	private static final Map<String, Block> BY_NAME = new HashMap<>();

	/** Block identity check; the name lookup is cached so the render loop only compares references. */
	private static boolean isBlock(WorldView w, int x, int y, int z, String name) {
		Block ref = BY_NAME.get(name);
		if (ref == null) {
			ref = Block.byKey(name);
			BY_NAME.put(name, ref);
		}
		return w.getBlock(x, y, z) == ref;
	}

	private static boolean snowAbove(WorldView w, int x, int y, int z) {
		return isBlock(w, x, y + 1, z, "minecraft:snow") || isBlock(w, x, y + 1, z, "minecraft:snow_layer");
	}

	private static int bit(boolean b, int shift) {
		return b ? 1 << shift : 0;
	}

	// ---------------------------------------------------------------- table

	private static void init() {
		simple("stone", "cobblestone", "bedrock", "gravel", "gold_ore", "iron_ore", "coal_ore", "glass", "lapis_ore", "lapis_block",
			"noteblock", "web", "brown_mushroom", "red_mushroom", "gold_block", "iron_block", "brick_block", "bookshelf",
			"mossy_cobblestone", "obsidian", "mob_spawner", "diamond_ore", "diamond_block", "crafting_table", "redstone_ore",
			"lit_redstone_ore", "ice", "snow", "clay", "netherrack", "soul_sand", "glowstone", "melon_block", "waterlily",
			"nether_brick", "enchanting_table", "end_stone", "dragon_egg", "redstone_lamp", "lit_redstone_lamp", "emerald_ore",
			"emerald_block", "beacon", "redstone_block", "quartz_ore", "hardened_clay", "coal_block", "packed_ice");

		put("tnt", of((k, o) -> {
			o.file = mc("tnt");
			o.put("explode", (k & 1) != 0);
		}).ignore("explode"));
		put("cactus", of((k, o) -> {
			o.file = mc("cactus");
			o.put("age", k & 15);
		}).ignore("age"));
		put("reeds", of((k, o) -> {
			o.file = mc("reeds");
			o.put("age", k & 15);
		}).ignore("age"));
		put("jukebox", of((k, o) -> {
			o.file = mc("jukebox");
			o.put("has_record", (k & 15) > 0);
		}).ignore("has_record"));
		put("sponge", of((k, o) -> {
			o.file = mc("sponge");
			o.put("wet", (k & 1) == 1);
		}));

		put("grass", of((w, x, y, z, m) -> m | bit(snowAbove(w, x, y, z), 4), (k, o) -> {
			o.file = mc("grass");
			o.put("snowy", (k & 16) != 0);
		}));
		put("mycelium", of((w, x, y, z, m) -> m | bit(snowAbove(w, x, y, z), 4), (k, o) -> {
			o.file = mc("mycelium");
			o.put("snowy", (k & 16) != 0);
		}));
		put("dirt", of((w, x, y, z, m) -> m | bit((m & 15) == 2 && snowAbove(w, x, y, z), 4), (k, o) -> {
			String v = at(new String[]{"dirt", "coarse_dirt", "podzol"}, k & 15);
			o.file = mc(v);
			if ("podzol".equals(v)) {
				o.put("snowy", (k & 16) != 0);
			}
		}));

		byMeta("planks", 15, "", WOOD, "_planks");
		put("sapling", of((k, o) -> {
			o.file = mc(at(WOOD, k & 7) + "_sapling");
			o.put("stage", (k & 8) >> 3);
		}));
		byMeta("sand", 15, "", new String[]{"sand", "red_sand"}, "");
		put("log", of((k, o) -> {
			o.file = mc(WOOD[k & 3] + "_log");
			o.put("axis", new String[]{"y", "x", "z", "none"}[(k & 12) >> 2]);
		}));
		put("log2", of((k, o) -> {
			o.file = mc(at(WOOD, (k & 3) + 4) + "_log");
			o.put("axis", new String[]{"y", "x", "z", "none"}[(k & 12) >> 2]);
		}));
		put("leaves", of((k, o) -> {
			o.file = mc(WOOD[k & 3] + "_leaves");
			o.put("check_decay", (k & 8) > 0).put("decayable", (k & 4) == 0);
		}).ignore("check_decay", "decayable"));
		put("leaves2", of((k, o) -> {
			o.file = mc(at(WOOD, (k & 3) + 4) + "_leaves");
			o.put("check_decay", (k & 8) > 0).put("decayable", (k & 4) == 0);
		}).ignore("check_decay", "decayable"));

		for (String n : new String[]{"dispenser", "dropper"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("facing", byId(k & 7)).put("triggered", (k & 8) > 0);
			}).ignore("triggered"));
		}
		byMeta("sandstone", 15, "", new String[]{"sandstone", "chiseled_sandstone", "smooth_sandstone"}, "");

		for (String n : new String[]{"golden_rail", "detector_rail", "activator_rail"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("shape", at(RAIL, (k & 7) > 5 ? 0 : k & 7)).put("powered", (k & 8) > 0);
			}));
		}
		put("rail", of((k, o) -> {
			o.file = mc("rail");
			o.put("shape", at(RAIL, k & 15));
		}));

		for (String n : new String[]{"piston", "sticky_piston"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("facing", (k & 7) > 5 ? "down" : Dir.NAMES[k & 7]).put("extended", (k & 8) > 0);
			}));
		}
		put("piston_head", of((k, o) -> {
			o.file = mc("piston_head");
			// bit 4 ("short") is only set by the moving piston renderer, never by world metadata
			o.put("facing", (k & 7) > 5 ? "down" : Dir.NAMES[k & 7]).put("type", (k & 8) > 0 ? "sticky" : "normal").put("short", (k & 16) != 0);
		}));

		byMeta("tallgrass", 15, "", new String[]{"dead_bush", "tall_grass", "fern"}, "");
		put("deadbush", of((k, o) -> o.file = mc("dead_bush")));
		byMeta("wool", 15, "", COLORS, "_wool");
		byMeta("carpet", 15, "", COLORS, "_carpet");
		byMeta("stained_glass", 15, "", COLORS, "_stained_glass");
		byMeta("stained_hardened_clay", 15, "", COLORS, "_stained_hardened_clay");
		put("yellow_flower", of((k, o) -> o.file = mc("dandelion")));
		byMeta("red_flower", 15, "", new String[]{"poppy", "blue_orchid", "allium", "houstonia", "red_tulip", "orange_tulip", "white_tulip",
			"pink_tulip", "oxeye_daisy"}, "");

		put("double_stone_slab", of((k, o) -> {
			o.file = mc(STONE_SLAB[k & 7] + "_double_slab");
			o.variant = (k & 8) != 0 ? "all" : "normal";
		}));
		put("stone_slab", of((k, o) -> {
			o.file = mc(STONE_SLAB[k & 7] + "_slab");
			o.put("half", (k & 8) == 0 ? "bottom" : "top");
		}));
		put("double_wooden_slab", of((k, o) -> o.file = mc(at(WOOD, k & 7) + "_double_slab")));
		put("wooden_slab", of((k, o) -> {
			o.file = mc(at(WOOD, k & 7) + "_slab");
			o.put("half", (k & 8) == 0 ? "bottom" : "top");
		}));

		for (String n : new String[]{"torch", "redstone_torch", "unlit_redstone_torch"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("facing", at(new String[]{"up", "east", "west", "south", "north", "up"}, k & 15));
			}));
		}

		put("fire", of(VanillaMappers::fireKey, (k, o) -> {
			o.file = mc("fire");
			o.put("age", k & 15).put("north", (k & 16) != 0).put("east", (k & 32) != 0).put("south", (k & 64) != 0)
				.put("west", (k & 128) != 0).put("up", (k & 256) != 0);
		}).ignore("age"));

		for (String n : new String[]{"oak_stairs", "stone_stairs", "brick_stairs", "stone_brick_stairs", "nether_brick_stairs",
			"sandstone_stairs", "spruce_stairs", "birch_stairs", "jungle_stairs", "quartz_stairs", "acacia_stairs", "dark_oak_stairs"}) {
			put(n, of(VanillaMappers::stairKey, (k, o) -> {
				o.file = mc(n);
				o.put("facing", Dir.NAMES[5 - (k & 3)]).put("half", (k & 4) != 0 ? "top" : "bottom")
					.put("shape", new String[]{"straight", "inner_left", "inner_right", "outer_left", "outer_right"}[(k >> 4) & 7]);
			}));
		}

		put("redstone_wire", of(VanillaMappers::wireKey, (k, o) -> {
			o.file = mc("redstone_wire");
			o.put("power", k & 15);
			String[] side = {"none", "side", "up"};
			o.put("north", side[(k >> 4) & 3]).put("east", side[(k >> 6) & 3]).put("south", side[(k >> 8) & 3]).put("west", side[(k >> 10) & 3]);
		}).ignore("power"));

		for (String n : new String[]{"wheat", "carrots", "potatoes", "nether_wart"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("age", k & 15);
			}));
		}
		put("farmland", of((k, o) -> {
			o.file = mc("farmland");
			o.put("moisture", k & 7);
		}));
		for (String n : new String[]{"furnace", "lit_furnace", "ladder"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("facing", byIdHorizontalOnly(k & 15));
			}));
		}

		for (String n : new String[]{"wooden_door", "iron_door"}) {
			put(n, of(VanillaMappers::doorKey, (k, o) -> {
				o.file = mc(n);
				// lower metadata in bits 0-3, upper metadata in bits 4-7
				int lower = k & 15, upper = (k >> 4) & 15;
				o.put("half", (k & 256) != 0 ? "upper" : "lower");
				o.put("facing", Dir.NAMES[Dir.BY_HORIZONTAL[((lower & 3) + 3) & 3]]).put("open", (lower & 4) != 0);
				o.put("hinge", (upper & 1) != 0 ? "right" : "left").put("powered", (upper & 2) != 0);
			}).ignore("powered"));
		}

		put("lever", of((k, o) -> {
			o.file = mc("lever");
			o.put("facing", LEVER[k & 7]).put("powered", (k & 8) > 0);
		}));
		for (String n : new String[]{"stone_pressure_plate", "wooden_pressure_plate"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("powered", (k & 15) == 1);
			}));
		}
		for (String n : new String[]{"light_weighted_pressure_plate", "heavy_weighted_pressure_plate", "daylight_detector"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("power", k & 15);
			}));
		}
		for (String n : new String[]{"stone_button", "wooden_button"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("facing", at(new String[]{"down", "east", "west", "south", "north", "up"}, k & 7)).put("powered", (k & 8) > 0);
			}));
		}
		put("snow_layer", of((k, o) -> {
			o.file = mc("snow_layer");
			o.put("layers", (k & 7) + 1);
		}));

		for (String n : new String[]{"fence", "nether_brick_fence"}) {
			put(n, of(VanillaMappers::fenceKey, (k, o) -> {
				o.file = mc(n);
				o.put("north", (k & 16) != 0).put("east", (k & 32) != 0).put("south", (k & 64) != 0).put("west", (k & 128) != 0);
			}));
		}
		for (String n : new String[]{"iron_bars", "glass_pane"}) {
			put(n, of(VanillaMappers::paneKey, (k, o) -> {
				o.file = mc(n);
				o.put("north", (k & 16) != 0).put("east", (k & 32) != 0).put("south", (k & 64) != 0).put("west", (k & 128) != 0);
			}));
		}
		put("stained_glass_pane", of(VanillaMappers::paneKey, (k, o) -> {
			o.file = mc(COLORS[k & 15] + "_stained_glass_pane");
			o.put("north", (k & 16) != 0).put("east", (k & 32) != 0).put("south", (k & 64) != 0).put("west", (k & 128) != 0);
		}));
		put("cobblestone_wall", of(VanillaMappers::wallKey, (k, o) -> {
			o.file = mc((k & 15) == 1 ? "mossy_cobblestone_wall" : "cobblestone_wall");
			o.put("north", (k & 16) != 0).put("east", (k & 32) != 0).put("south", (k & 64) != 0).put("west", (k & 128) != 0)
				.put("up", (k & 256) != 0);
		}));

		for (String n : new String[]{"pumpkin", "lit_pumpkin"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("facing", horizontal(k));
			}));
		}
		put("portal", of((k, o) -> {
			o.file = mc("portal");
			o.put("axis", (k & 3) == 2 ? "z" : "x");
		}));
		put("cake", of((k, o) -> {
			o.file = mc("cake");
			o.put("bites", Math.min(k & 15, 6));
		}));
		for (String n : new String[]{"unpowered_repeater", "powered_repeater"}) {
			put(n, of(VanillaMappers::repeaterKey, (k, o) -> {
				o.file = mc(n);
				o.put("facing", horizontal(k)).put("delay", 1 + ((k & 15) >> 2)).put("locked", (k & 16) != 0);
			}));
		}
		put("trapdoor", of((k, o) -> {
			o.file = mc("trapdoor");
			o.put("facing", new String[]{"north", "south", "west", "east"}[k & 3]).put("open", (k & 4) != 0)
				.put("half", (k & 8) == 0 ? "bottom" : "top");
		}));
		byMeta("monster_egg", 15, "", new String[]{"stone", "cobblestone", "stone_brick", "mossy_brick", "cracked_brick", "chiseled_brick"},
			"_monster_egg");
		byMeta("stonebrick", 15, "", new String[]{"stonebrick", "mossy_stonebrick", "cracked_stonebrick", "chiseled_stonebrick"}, "");
		for (String n : new String[]{"brown_mushroom_block", "red_mushroom_block"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("variant", at(MUSHROOM, k & 15));
			}));
		}

		put("pumpkin_stem", of((w, x, y, z, m) -> stemKey(w, x, y, z, m, "minecraft:pumpkin"), (k, o) -> stemDescribe("pumpkin_stem", k, o)));
		put("melon_stem", of((w, x, y, z, m) -> stemKey(w, x, y, z, m, "minecraft:melon_block"), (k, o) -> stemDescribe("melon_stem", k, o)));

		put("vine", of((w, x, y, z, m) -> m | bit(w.getBlock(x, y + 1, z).blocksAmbientLight(), 4), (k, o) -> {
			o.file = mc("vine");
			o.put("south", (k & 1) > 0).put("west", (k & 2) > 0).put("north", (k & 4) > 0).put("east", (k & 8) > 0).put("up", (k & 16) != 0);
		}));
		put("fence_gate", of(VanillaMappers::gateKey, (k, o) -> {
			o.file = mc("fence_gate");
			o.put("facing", horizontal(k)).put("open", (k & 4) != 0).put("powered", (k & 8) != 0).put("in_wall", (k & 16) != 0);
		}).ignore("powered"));
		put("brewing_stand", of((k, o) -> {
			o.file = mc("brewing_stand");
			o.put("has_bottle_0", (k & 1) > 0).put("has_bottle_1", (k & 2) > 0).put("has_bottle_2", (k & 4) > 0);
		}));
		put("cauldron", of((k, o) -> {
			o.file = mc("cauldron");
			o.put("level", Math.min(k & 15, 3));
		}));
		put("end_portal_frame", of((k, o) -> {
			o.file = mc("end_portal_frame");
			o.put("eye", (k & 4) != 0).put("facing", horizontal(k));
		}));
		put("cocoa", of((k, o) -> {
			o.file = mc("cocoa");
			o.put("facing", horizontal(k)).put("age", Math.min((k & 15) >> 2, 2));
		}));
		put("tripwire_hook", of((k, o) -> {
			o.file = mc("tripwire_hook");
			o.put("facing", horizontal(k)).put("attached", (k & 4) > 0).put("powered", (k & 8) > 0);
		}));
		put("tripwire", of(VanillaMappers::tripwireKey, (k, o) -> {
			o.file = mc("tripwire");
			o.put("powered", (k & 1) > 0).put("attached", (k & 4) > 0).put("disarmed", (k & 8) > 0);
			o.put("north", (k & 16) != 0).put("east", (k & 32) != 0).put("south", (k & 64) != 0).put("west", (k & 128) != 0);
		}).ignore("disarmed", "powered"));
		// 1.12 command blocks face a direction and can be conditional; 1.7.10 ones are neither
		put("command_block", of((k, o) -> {
			o.file = mc("command_block");
			o.put("facing", "north").put("conditional", false);
		}));
		put("flower_pot", of(VanillaMappers::potKey, (k, o) -> {
			o.file = mc("flower_pot");
			o.put("legacy_data", k & 15).put("contents", at(POT, (k >> 4) & 31));
		}).ignore("legacy_data"));
		put("anvil", of((k, o) -> {
			o.file = mc("anvil");
			o.put("facing", horizontal(k)).put("damage", Math.min((k & 15) >> 2, 2));
		}));
		for (String n : new String[]{"unpowered_comparator", "powered_comparator"}) {
			put(n, of((k, o) -> {
				o.file = mc(n);
				o.put("facing", horizontal(k)).put("powered", (k & 8) > 0).put("mode", (k & 4) > 0 ? "subtract" : "compare");
			}));
		}
		put("hopper", of((k, o) -> {
			o.file = mc("hopper");
			o.put("facing", byId(k & 7)).put("enabled", (k & 8) != 8);
		}).ignore("enabled"));
		put("quartz_block", of((k, o) -> {
			switch (k & 15) {
				case 1: o.file = mc("chiseled_quartz_block"); o.variant = "normal"; break;
				case 2: o.file = mc("quartz_column"); o.variant = "axis=y"; break;
				case 3: o.file = mc("quartz_column"); o.variant = "axis=x"; break;
				case 4: o.file = mc("quartz_column"); o.variant = "axis=z"; break;
				default: o.file = mc("quartz_block"); o.variant = "normal"; break;
			}
		}));
		put("hay_block", of((k, o) -> {
			o.file = mc("hay_block");
			int a = k & 12;
			o.put("axis", a == 4 ? "x" : a == 8 ? "z" : "y");
		}));
		put("double_plant", of(VanillaMappers::doublePlantKey, (k, o) -> {
			// low bits: this block's metadata, bits 4-6: variant read from the lower half
			boolean upper = (k & 8) != 0;
			int variant = upper ? (k >> 4) & 7 : k & 7;
			o.file = mc(at(DOUBLE_PLANT, variant));
			o.put("half", upper ? "upper" : "lower").put("facing", "north");
		}).ignore("facing"));
	}

	// ---------------------------------------------------------------- neighbor rules

	private static int fireKey(WorldView w, int x, int y, int z, int m) {
		FireBlock fire = (FireBlock) w.getBlock(x, y, z);
		if (World.hasSolidTop(w, x, y - 1, z) || fire.isFlammable(w, x, y - 1, z)) {
			return m;
		}
		return m | bit(fire.isFlammable(w, x, y, z - 1), 4) | bit(fire.isFlammable(w, x + 1, y, z), 5)
			| bit(fire.isFlammable(w, x, y, z + 1), 6) | bit(fire.isFlammable(w, x - 1, y, z), 7) | bit(fire.isFlammable(w, x, y + 1, z), 8);
	}

	/** Stair metadata facing: 0 east, 1 west, 2 south, 3 north. */
	private static int stairFacing(int meta) {
		return 5 - (meta & 3);
	}

	private static boolean otherStairDiffers(WorldView w, int x, int y, int z, int dir, int facing, int half) {
		Block b = w.getBlock(x + Dir.X[dir], y, z + Dir.Z[dir]);
		if (!StairsBlock.isStair(b)) {
			return true;
		}
		int m = w.getBlockMetadata(x + Dir.X[dir], y, z + Dir.Z[dir]);
		return stairFacing(m) != facing || (m & 4) != half;
	}

	/** 1.12 stair shape: 0 straight, 1 inner_left, 2 inner_right, 3 outer_left, 4 outer_right. */
	private static int stairKey(WorldView w, int x, int y, int z, int m) {
		int facing = stairFacing(m);
		int half = m & 4;
		int ccw = ccwY(facing);
		int fx = x + Dir.X[facing], fz = z + Dir.Z[facing];
		Block front = w.getBlock(fx, y, fz);
		if (StairsBlock.isStair(front)) {
			int fm = w.getBlockMetadata(fx, y, fz);
			int f2 = stairFacing(fm);
			if ((fm & 4) == half && Dir.axis(f2) != Dir.axis(facing) && otherStairDiffers(w, x, y, z, Dir.OPPOSITE[f2], facing, half)) {
				return m | (f2 == ccw ? 3 : 4) << 4;
			}
		}
		int back = Dir.OPPOSITE[facing];
		int bx = x + Dir.X[back], bz = z + Dir.Z[back];
		Block behind = w.getBlock(bx, y, bz);
		if (StairsBlock.isStair(behind)) {
			int bm = w.getBlockMetadata(bx, y, bz);
			int f3 = stairFacing(bm);
			if ((bm & 4) == half && Dir.axis(f3) != Dir.axis(facing) && otherStairDiffers(w, x, y, z, f3, facing, half)) {
				return m | (f3 == ccw ? 1 : 2) << 4;
			}
		}
		return m;
	}

	/** Counter-clockwise around Y seen from above: north to west, west to south, south to east, east to north. */
	private static int ccwY(int d) {
		switch (d) {
			case Dir.NORTH: return Dir.WEST;
			case Dir.WEST: return Dir.SOUTH;
			case Dir.SOUTH: return Dir.EAST;
			default: return Dir.NORTH;
		}
	}

	/** none 0, side 1, up 2 for each horizontal side, like 1.7.10 draws the wire. */
	private static int wireKey(WorldView w, int x, int y, int z, int m) {
		boolean coveredAbove = w.getBlock(x, y + 1, z).blocksAmbientLight();
		int key = m;
		int[] dirs = {Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST};
		// 1.7.10 passes the horizontal index of the side (0 south, 1 west, 2 north, 3 east) to its connection check
		int[] index = {2, 3, 0, 1};
		for (int i = 0; i < 4; i++) {
			int nx = x + Dir.X[dirs[i]], nz = z + Dir.Z[dirs[i]];
			boolean solid = w.getBlock(nx, y, nz).blocksAmbientLight();
			int c = 0;
			if (!coveredAbove && solid && RedstoneWireBlock.shouldConnectTo(w, nx, y + 1, nz, -1)) {
				c = 2;
			} else if (RedstoneWireBlock.shouldConnectTo(w, nx, y, nz, index[i]) || !solid && RedstoneWireBlock.shouldConnectTo(w, nx, y - 1, nz, -1)) {
				c = 1;
			}
			key |= c << (4 + i * 2);
		}
		return key;
	}

	private static int doorKey(WorldView w, int x, int y, int z, int m) {
		Block self = w.getBlock(x, y, z);
		if ((m & 8) != 0) {
			int lower = w.getBlock(x, y - 1, z) == self ? w.getBlockMetadata(x, y - 1, z) & 7 : 0;
			return lower | (m & 15) << 4 | 256;
		}
		int upper = w.getBlock(x, y + 1, z) == self ? w.getBlockMetadata(x, y + 1, z) & 15 : 8;
		return (m & 7) | upper << 4;
	}

	private static int fenceKey(WorldView w, int x, int y, int z, int m) {
		FenceBlock f = (FenceBlock) w.getBlock(x, y, z);
		return m | bit(f.shouldConnectTo(w, x, y, z - 1), 4) | bit(f.shouldConnectTo(w, x + 1, y, z), 5)
			| bit(f.shouldConnectTo(w, x, y, z + 1), 6) | bit(f.shouldConnectTo(w, x - 1, y, z), 7);
	}

	private static int paneKey(WorldView w, int x, int y, int z, int m) {
		PaneBlock p = (PaneBlock) w.getBlock(x, y, z);
		return m | bit(p.shouldConnectTo(w.getBlock(x, y, z - 1)), 4) | bit(p.shouldConnectTo(w.getBlock(x + 1, y, z)), 5)
			| bit(p.shouldConnectTo(w.getBlock(x, y, z + 1)), 6) | bit(p.shouldConnectTo(w.getBlock(x - 1, y, z)), 7);
	}

	private static int wallKey(WorldView w, int x, int y, int z, int m) {
		WallBlock wall = (WallBlock) w.getBlock(x, y, z);
		boolean n = wall.shouldConnectTo(w, x, y, z - 1), e = wall.shouldConnectTo(w, x + 1, y, z);
		boolean s = wall.shouldConnectTo(w, x, y, z + 1), we = wall.shouldConnectTo(w, x - 1, y, z);
		boolean straight = n && !e && s && !we || !n && e && !s && we;
		boolean up = !straight || !w.isAir(x, y + 1, z);
		return m | bit(n, 4) | bit(e, 5) | bit(s, 6) | bit(we, 7) | bit(up, 8);
	}

	private static int repeaterKey(WorldView w, int x, int y, int z, int m) {
		DiodeBlock d = (DiodeBlock) w.getBlock(x, y, z);
		return m | bit(d.isLocked(w, x, y, z, m), 4);
	}

	/** Bits 4-6: facing index (0 up, 1 north, 2 east, 3 south, 4 west). */
	private static int stemKey(WorldView w, int x, int y, int z, int m, String fruit) {
		if ((m & 7) == 7) {
			int[] dirs = {Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST};
			for (int i = 0; i < 4; i++) {
				if (isBlock(w, x + Dir.X[dirs[i]], y, z + Dir.Z[dirs[i]], fruit)) {
					return m | (i + 1) << 4;
				}
			}
		}
		return m;
	}

	/** 1.12 drops "age" from the variant once the stem bends towards its fruit. */
	private static void stemDescribe(String name, int k, Mapper.Desc o) {
		o.file = mc(name);
		int f = (k >> 4) & 7;
		o.put("facing", new String[]{"up", "north", "east", "south", "west"}[Math.min(f, 4)]);
		if (f == 0) {
			o.put("age", k & 7);
		}
	}

	private static int gateKey(WorldView w, int x, int y, int z, int m) {
		boolean alongX = (Dir.BY_HORIZONTAL[m & 3] == Dir.EAST || Dir.BY_HORIZONTAL[m & 3] == Dir.WEST);
		boolean inWall = alongX
			? isBlock(w, x, y, z - 1, "minecraft:cobblestone_wall") || isBlock(w, x, y, z + 1, "minecraft:cobblestone_wall")
			: isBlock(w, x - 1, y, z, "minecraft:cobblestone_wall") || isBlock(w, x + 1, y, z, "minecraft:cobblestone_wall");
		return m | bit(inWall, 4);
	}

	private static int tripwireKey(WorldView w, int x, int y, int z, int m) {
		// 1.7.10 checks take the horizontal index: 2 north, 3 east, 0 south, 1 west
		return m | bit(TripwireBlock.shouldConnectTo(w, x, y, z, m, 2), 4) | bit(TripwireBlock.shouldConnectTo(w, x, y, z, m, 3), 5)
			| bit(TripwireBlock.shouldConnectTo(w, x, y, z, m, 0), 6) | bit(TripwireBlock.shouldConnectTo(w, x, y, z, m, 1), 7);
	}

	private static int doublePlantKey(WorldView w, int x, int y, int z, int m) {
		if ((m & 8) == 0) {
			return m;
		}
		Block self = w.getBlock(x, y, z);
		int lower = w.getBlock(x, y - 1, z) == self ? w.getBlockMetadata(x, y - 1, z) & 7 : 0;
		return m | lower << 4;
	}

	private static int potKey(WorldView w, int x, int y, int z, int m) {
		BlockEntity be = w.getBlockEntity(x, y, z);
		if (!(be instanceof FlowerPotBlockEntity)) {
			return m;
		}
		FlowerPotBlockEntity pot = (FlowerPotBlockEntity) be;
		Item item = pot.getPlant();
		if (item == null) {
			return m;
		}
		Block b = Block.byItem(item);
		String name = b == null ? null : Block.REGISTRY.getKey(b);
		int meta = pot.getMetadata();
		int c = 0;
		if ("minecraft:sapling".equals(name)) {
			c = 11 + Math.min(Math.max(meta, 0), 5);
		} else if ("minecraft:tallgrass".equals(name)) {
			c = meta == 0 ? 19 : meta == 2 ? 20 : 0;
		} else if ("minecraft:yellow_flower".equals(name)) {
			c = 10;
		} else if ("minecraft:red_flower".equals(name)) {
			c = meta >= 0 && meta <= 8 ? 1 + meta : 0;
		} else if ("minecraft:red_mushroom".equals(name)) {
			c = 17;
		} else if ("minecraft:brown_mushroom".equals(name)) {
			c = 18;
		} else if ("minecraft:deadbush".equals(name)) {
			c = 19;
		} else if ("minecraft:cactus".equals(name)) {
			c = 21;
		}
		return m | c << 4;
	}
}
