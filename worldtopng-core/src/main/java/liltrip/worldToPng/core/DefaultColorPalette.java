package liltrip.worldToPng.core;

import java.util.HashMap;
import java.util.Map;

/**
 * The default {@link ColorPalette}: a top-down "map" colouring keyed by namespaced block id. It
 * needs no data files yet covers effectively every block.
 *
 * <p>A block id resolves in this order:
 * <ol>
 *     <li>caller-supplied overrides (see {@link #builder()});</li>
 *     <li>a curated table of terrain and landmark colours;</li>
 *     <li>the sixteen dyed block families, from a built-in dye-colour table;</li>
 *     <li>keyword heuristics on the id path, then a neutral-grey fallback.</li>
 * </ol>
 *
 * <p>Instances are immutable and safe for concurrent use.
 */
public final class DefaultColorPalette implements ColorPalette {

    private static final int OPAQUE = 0xFF000000;
    private static final int FALLBACK = 0xFF8C8C8C;

    /** The sixteen vanilla dye colours, indexed alongside {@link #DYE_NAMES}. */
    private static final String[] DYE_NAMES = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    private static final int[] DYE_RGB = {
            0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21
    };

    private static final Map<String, Integer> CURATED = buildCurated();
    private static final DefaultColorPalette INSTANCE = new DefaultColorPalette(Map.of());

    private final Map<String, Integer> overrides;

    private DefaultColorPalette(Map<String, Integer> overrides) {
        this.overrides = overrides;
    }

    /** The shared, no-override default palette. */
    public static DefaultColorPalette instance() {
        return INSTANCE;
    }

    /** A builder for a palette that layers custom colours over the defaults. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int argb(String blockId) {
        Integer override = overrides.get(blockId);
        if (override != null) {
            return override;
        }
        Integer curated = CURATED.get(blockId);
        if (curated != null) {
            return curated;
        }
        return heuristic(blockId);
    }

    private static int heuristic(String blockId) {
        int colon = blockId.indexOf(':');
        final String path = colon >= 0 ? blockId.substring(colon + 1) : blockId;

        // Dyed block families: wool, concrete, terracotta, glass, carpet, beds, candles, ...
        for (int i = 0; i < DYE_NAMES.length; i++) {
            if (path.startsWith(DYE_NAMES[i] + "_")) {
                int base = DYE_RGB[i] | OPAQUE;
                return path.contains("terracotta") ? Colors.scale(base, 0.70) : base;
            }
        }

        // Keyword heuristics, ordered so the more specific term wins.
        if (path.contains("water") || path.contains("bubble_column")) return 0xFF3B5DC9;
        if (path.contains("lava")) return 0xFFD45A12;
        if (path.endsWith("_log") || path.endsWith("_wood")
                || path.endsWith("_stem") || path.endsWith("_hyphae")) return 0xFF6E5638;
        if (path.contains("planks")) return 0xFFB8945F;
        if (path.contains("leaves") || path.contains("vine") || path.contains("moss")
                || path.contains("sapling") || path.endsWith("_fern") || path.endsWith("grass")
                || path.contains("bamboo") || path.contains("kelp") || path.contains("seagrass")
                || path.contains("lily_pad") || path.contains("azalea")) return 0xFF59812F;
        if (path.contains("deepslate")) return 0xFF4D4D52;
        if (path.contains("blackstone") || path.contains("basalt") || path.contains("obsidian")) return 0xFF2A2630;
        if (path.contains("terracotta")) return 0xFF9A5E45;
        if (path.contains("netherrack") || path.contains("nether") || path.contains("crimson")) return 0xFF6E3637;
        if (path.contains("warped")) return 0xFF2C7A6E;
        if (path.contains("sandstone")) return 0xFFE6DCAA;
        if (path.contains("red_sand")) return 0xFFBE6B2E;
        if (path.contains("sand")) return 0xFFE0D6A2;
        if (path.contains("ice")) return 0xFFA8C0F0;
        if (path.contains("snow")) return 0xFFF0FAFF;
        if (path.contains("dirt") || path.contains("mud") || path.contains("clay")
                || path.contains("podzol") || path.contains("coarse") || path.contains("rooted")
                || path.contains("farmland") || path.contains("mycelium")) return 0xFF8B6A47;
        if (path.contains("gravel")) return 0xFF8A8782;
        if (path.contains("copper")) return 0xFFC1714B;
        if (path.contains("prismarine")) return 0xFF5F9387;
        if (path.contains("quartz")) return 0xFFE6E1D9;
        if (path.contains("wool") || path.contains("carpet")) return 0xFFDDDDDD;
        if (path.contains("glass")) return 0xFFEFF6F8;
        if (path.contains("brick")) return 0xFF9A5240;
        if (path.contains("stone") || path.contains("cobble") || path.contains("andesite")
                || path.contains("diorite") || path.contains("granite") || path.contains("tuff")
                || path.contains("calcite")) return 0xFF7F7F7F;

        return FALLBACK;
    }

    private static Map<String, Integer> buildCurated() {
        Map<String, Integer> m = new HashMap<>();

        // Surface terrain
        m.put("minecraft:grass_block", 0xFF7FB238);
        m.put("minecraft:dirt", 0xFF976D4D);
        m.put("minecraft:coarse_dirt", 0xFF8C6A4A);
        m.put("minecraft:rooted_dirt", 0xFF8C6A4A);
        m.put("minecraft:podzol", 0xFF5E4127);
        m.put("minecraft:dirt_path", 0xFF9C7F4E);
        m.put("minecraft:farmland", 0xFF6B4A2B);
        m.put("minecraft:mud", 0xFF3C3A3C);
        m.put("minecraft:clay", 0xFFA4A8B8);
        m.put("minecraft:gravel", 0xFF8A8782);
        m.put("minecraft:sand", 0xFFE0D6A2);
        m.put("minecraft:red_sand", 0xFFBE6B2E);
        m.put("minecraft:sandstone", 0xFFE6DCAA);
        m.put("minecraft:red_sandstone", 0xFFD8924B);
        m.put("minecraft:moss_block", 0xFF5E7A2E);
        m.put("minecraft:mycelium", 0xFF6F6265);

        // Stone family
        m.put("minecraft:stone", 0xFF7F7F7F);
        m.put("minecraft:cobblestone", 0xFF7A7A7A);
        m.put("minecraft:mossy_cobblestone", 0xFF6E7A60);
        m.put("minecraft:granite", 0xFF9F6B58);
        m.put("minecraft:diorite", 0xFFBFBFBF);
        m.put("minecraft:andesite", 0xFF888889);
        m.put("minecraft:calcite", 0xFFD8D8D2);
        m.put("minecraft:tuff", 0xFF6C6E66);
        m.put("minecraft:deepslate", 0xFF4D4D52);
        m.put("minecraft:cobbled_deepslate", 0xFF4A4A4F);
        m.put("minecraft:bedrock", 0xFF565656);
        m.put("minecraft:bricks", 0xFF9A5240);

        // Water and ice
        m.put("minecraft:water", 0xFF3B5DC9);
        m.put("minecraft:ice", 0xFFA8C0F0);
        m.put("minecraft:packed_ice", 0xFF8EA8E8);
        m.put("minecraft:blue_ice", 0xFF74A4F0);
        m.put("minecraft:snow", 0xFFF0FAFF);
        m.put("minecraft:snow_block", 0xFFF0FAFF);
        m.put("minecraft:powder_snow", 0xFFEFF6FF);

        // Wood (logs and leaves)
        m.put("minecraft:oak_log", 0xFF6E5638);
        m.put("minecraft:spruce_log", 0xFF4A3B23);
        m.put("minecraft:birch_log", 0xFFD6D2C4);
        m.put("minecraft:oak_planks", 0xFFB8945F);
        m.put("minecraft:oak_leaves", 0xFF4A7A2F);
        m.put("minecraft:spruce_leaves", 0xFF3F5E33);
        m.put("minecraft:birch_leaves", 0xFF698F3D);

        // Lava and the Nether
        m.put("minecraft:lava", 0xFFD45A12);
        m.put("minecraft:netherrack", 0xFF6E3637);
        m.put("minecraft:soul_sand", 0xFF513E33);
        m.put("minecraft:soul_soil", 0xFF4A382E);
        m.put("minecraft:magma_block", 0xFF8E3A20);
        m.put("minecraft:glowstone", 0xFFD2A24B);
        m.put("minecraft:crimson_nylium", 0xFF82393B);
        m.put("minecraft:warped_nylium", 0xFF2C7A6E);
        m.put("minecraft:obsidian", 0xFF14121F);

        // The End
        m.put("minecraft:end_stone", 0xFFDAD7A0);
        m.put("minecraft:end_stone_bricks", 0xFFD9D6A0);
        m.put("minecraft:purpur_block", 0xFFA779A7);

        // Misc landmarks
        m.put("minecraft:pumpkin", 0xFFC07615);
        m.put("minecraft:melon", 0xFF6F8C2B);
        m.put("minecraft:hay_block", 0xFFB8950F);
        m.put("minecraft:prismarine", 0xFF5F9387);
        m.put("minecraft:dark_prismarine", 0xFF334E45);
        m.put("minecraft:sea_lantern", 0xFFC9D3C0);
        m.put("minecraft:bookshelf", 0xFFB0884A);
        m.put("minecraft:glass", 0xFFEFF6F8);

        // Ores
        m.put("minecraft:coal_ore", 0xFF6B6B6B);
        m.put("minecraft:iron_ore", 0xFF9A8A7B);
        m.put("minecraft:copper_ore", 0xFF8C6A55);
        m.put("minecraft:gold_ore", 0xFF8F844F);
        m.put("minecraft:redstone_ore", 0xFF8A4B4B);
        m.put("minecraft:lapis_ore", 0xFF4A5C8A);
        m.put("minecraft:diamond_ore", 0xFF6FB7B3);
        m.put("minecraft:emerald_ore", 0xFF5BA66F);

        // Mineral / utility blocks
        m.put("minecraft:coal_block", 0xFF19191B);
        m.put("minecraft:iron_block", 0xFFD8D8D8);
        m.put("minecraft:gold_block", 0xFFF6D74B);
        m.put("minecraft:diamond_block", 0xFF6FE5D6);
        m.put("minecraft:emerald_block", 0xFF2DB45A);
        m.put("minecraft:redstone_block", 0xFFB01B0F);
        m.put("minecraft:lapis_block", 0xFF26458F);
        m.put("minecraft:netherite_block", 0xFF45403F);
        m.put("minecraft:copper_block", 0xFFC1714B);
        m.put("minecraft:quartz_block", 0xFFE6E1D9);

        return Map.copyOf(m);
    }

    /** Builds a {@link DefaultColorPalette} with caller overrides layered on top of the defaults. */
    public static final class Builder {
        private final Map<String, Integer> overrides = new HashMap<>();

        private Builder() {
        }

        /** Override the colour for a block id; {@code argb} is packed {@code 0xAARRGGBB}. */
        public Builder set(String blockId, int argb) {
            overrides.put(blockId, argb);
            return this;
        }

        public ColorPalette build() {
            return overrides.isEmpty() ? INSTANCE : new DefaultColorPalette(Map.copyOf(overrides));
        }
    }
}
