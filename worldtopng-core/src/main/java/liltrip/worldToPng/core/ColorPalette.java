package liltrip.worldToPng.core;

/**
 * Maps a block's namespaced id (e.g. {@code "minecraft:grass_block"}) to a packed
 * {@code 0xAARRGGBB} colour for that block's pixel on the map.
 *
 * <p>Keying by id <em>string</em> (rather than a platform enum like Bukkit's {@code Material})
 * is what lets one palette serve every platform adapter — Paper, Fabric, and so on all produce
 * the same ids.
 *
 * <p>A fully transparent result ({@code alpha == 0}) tells the renderer to skip that block.
 * Implementations must be safe for concurrent reads.
 */
@FunctionalInterface
public interface ColorPalette {

    /**
     * @param blockId the block's namespaced id, e.g. {@code "minecraft:stone"}
     * @return the packed {@code 0xAARRGGBB} colour to draw
     */
    int argb(String blockId);
}
