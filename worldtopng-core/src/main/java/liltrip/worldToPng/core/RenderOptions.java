package liltrip.worldToPng.core;

import java.util.Objects;

/**
 * Visual options for a render. Platform-neutral: an adapter reads {@link #waterDepthShading()}
 * and {@link #generateMissingChunks()} while gathering data; the core applies
 * {@link #heightShading()} and uses the {@link #palette()}.
 *
 * @param heightShading         apply north-neighbour relief shading (the classic map effect)
 * @param waterDepthShading     darken water by depth so oceans read as deep
 * @param generateMissingChunks generate ungenerated chunks (writes terrain to disk); default off
 * @param palette               the block-id-to-colour mapping
 */
public record RenderOptions(
        boolean heightShading,
        boolean waterDepthShading,
        boolean generateMissingChunks,
        ColorPalette palette
) {
    public RenderOptions {
        Objects.requireNonNull(palette, "palette");
    }

    /** Relief and water-depth shading on, no chunk generation, the {@link DefaultColorPalette}. */
    public static RenderOptions defaults() {
        return new RenderOptions(true, true, false, DefaultColorPalette.instance());
    }

    public RenderOptions withHeightShading(boolean value) {
        return new RenderOptions(value, waterDepthShading, generateMissingChunks, palette);
    }

    public RenderOptions withWaterDepthShading(boolean value) {
        return new RenderOptions(heightShading, value, generateMissingChunks, palette);
    }

    public RenderOptions withGenerateMissingChunks(boolean value) {
        return new RenderOptions(heightShading, waterDepthShading, value, palette);
    }

    public RenderOptions withPalette(ColorPalette palette) {
        return new RenderOptions(heightShading, waterDepthShading, generateMissingChunks, palette);
    }
}
