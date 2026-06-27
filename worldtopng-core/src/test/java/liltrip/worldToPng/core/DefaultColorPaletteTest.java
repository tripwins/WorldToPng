package liltrip.worldToPng.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DefaultColorPaletteTest {

    private final ColorPalette palette = DefaultColorPalette.instance();

    @Test
    void curatedWaterColourIsStable() {
        assertEquals(0xFF3B5DC9, palette.argb("minecraft:water"));
    }

    @Test
    void grassIsGreenDominant() {
        int c = palette.argb("minecraft:grass_block");
        assertTrue(Colors.green(c) > Colors.red(c) && Colors.green(c) > Colors.blue(c));
    }

    @Test
    void dyedFamiliesResolveFromDyeTable() {
        int red = palette.argb("minecraft:red_wool");
        assertTrue(Colors.red(red) > Colors.green(red) && Colors.red(red) > Colors.blue(red));

        int lime = palette.argb("minecraft:lime_concrete");
        assertTrue(Colors.green(lime) > Colors.red(lime) && Colors.green(lime) > Colors.blue(lime));
    }

    @Test
    void variousBlocksAreAllOpaque() {
        for (String id : new String[]{
                "minecraft:stone", "minecraft:oak_log", "minecraft:lava", "minecraft:deepslate",
                "minecraft:blue_terracotta", "minecraft:packed_ice", "minecraft:nether_wart_block",
                "minecraft:totally_made_up_block"}) {
            assertEquals(0xFF, Colors.alpha(palette.argb(id)), id);
        }
    }

    @Test
    void overridesTakePrecedenceAndLeaveOthersAlone() {
        ColorPalette custom = DefaultColorPalette.builder()
                .set("minecraft:water", 0xFF010203)
                .build();
        assertEquals(0xFF010203, custom.argb("minecraft:water"));
        assertEquals(palette.argb("minecraft:grass_block"), custom.argb("minecraft:grass_block"));
    }
}
