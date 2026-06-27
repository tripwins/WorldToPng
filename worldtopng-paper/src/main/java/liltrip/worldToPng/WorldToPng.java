package liltrip.worldToPng;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import liltrip.worldToPng.core.RenderedMap;
import liltrip.worldToPng.internal.PaperMapRenderer;
import org.bukkit.plugin.Plugin;

/**
 * Paper entry point to WorldToPng.
 *
 * <p>Renders a rectangular region of a Bukkit/Paper {@link org.bukkit.World} into a top-down PNG
 * map image, off the main server thread. Meant to be <b>shaded into a host plugin's jar</b> rather
 * than installed as its own plugin: a plugin passes its own {@link Plugin} to {@link #create(Plugin)}
 * and calls {@link #render(RenderRequest)}.
 *
 * <pre>{@code
 * WorldToPng worldToPng = WorldToPng.create(this); // 'this' is your JavaPlugin
 *
 * RenderRequest request = RenderRequest.builder(world)
 *         .bounds(-512, -512, 512, 512)
 *         .build();
 *
 * worldToPng.render(request)
 *         .thenAccept(map -> map.writeTo(getDataFolder().toPath().resolve("map.png")))
 *         .exceptionally(ex -> { getLogger().warning("Render failed: " + ex); return null; });
 *
 * // In onDisable():
 * worldToPng.close();
 * }</pre>
 *
 * <p>An instance owns background threads, so call {@link #close()} when finished. Targets Paper
 * (uses async chunk loading); Folia is not supported. The rendering, palette and drawing types
 * ({@link RenderedMap}, {@code MapCanvas}, {@code RenderOptions}, ...) live in the platform-neutral
 * {@code liltrip.worldToPng.core} package and are shared with other platform adapters.
 */
public final class WorldToPng implements AutoCloseable {

    private final PaperMapRenderer renderer;

    private WorldToPng(Plugin plugin, Settings settings) {
        this.renderer = new PaperMapRenderer(plugin, settings.maxAreaBlocks(), settings.maxConcurrentChunkLoads());
    }

    /** Create an instance with {@link Settings#defaults() default settings}. */
    public static WorldToPng create(Plugin plugin) {
        return create(plugin, Settings.defaults());
    }

    /** Create an instance with custom {@link Settings}. */
    public static WorldToPng create(Plugin plugin, Settings settings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(settings, "settings");
        return new WorldToPng(plugin, settings);
    }

    /**
     * Render the given request. The returned future completes on a background thread with the
     * {@link RenderedMap}, or completes exceptionally with a {@code RenderException} cause.
     */
    public CompletableFuture<RenderedMap> render(RenderRequest request) {
        return renderer.render(request);
    }

    /** Shut down background threads. After this, {@link #render(RenderRequest)} must not be called. */
    @Override
    public void close() {
        renderer.shutdown();
    }

    /**
     * Tuning knobs for a {@link WorldToPng} instance.
     *
     * @param maxAreaBlocks           the largest area (width &times; depth, in block columns) a
     *                                single render may cover; guards against gigapixel images
     * @param maxConcurrentChunkLoads how many chunks may be loaded and snapshotted concurrently
     */
    public record Settings(int maxAreaBlocks, int maxConcurrentChunkLoads) {
        public Settings {
            if (maxAreaBlocks <= 0) {
                throw new IllegalArgumentException("maxAreaBlocks must be > 0");
            }
            if (maxConcurrentChunkLoads <= 0) {
                throw new IllegalArgumentException("maxConcurrentChunkLoads must be > 0");
            }
        }

        /** Defaults: a 16,000,000-block cap (~a 4096&times;4096 image) and 12 concurrent loads. */
        public static Settings defaults() {
            return new Settings(16_000_000, 12);
        }
    }
}
