package liltrip.worldToPng.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import liltrip.worldToPng.RenderRequest;
import liltrip.worldToPng.core.ColorPalette;
import liltrip.worldToPng.core.Colors;
import liltrip.worldToPng.core.MapBuffer;
import liltrip.worldToPng.core.RenderException;
import liltrip.worldToPng.core.RenderOptions;
import liltrip.worldToPng.core.RenderedMap;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * The Paper rendering engine: it reads a live world via {@link ChunkSnapshot}s and feeds the
 * platform-neutral {@link MapBuffer}.
 *
 * <p>An orchestrator thread walks the region's chunks, throttled by a semaphore so only a bounded
 * number are in flight. Each chunk is loaded with {@code World#getChunkAtAsync} and snapshotted on
 * the main thread (the snapshot is then safe off-thread); a render pool turns its columns into
 * pixels in the buffer. Once every chunk is done, the buffer composes the final image.
 */
public final class PaperMapRenderer {

    private final Plugin plugin;
    private final Server server;
    private final int maxAreaBlocks;
    private final int maxConcurrentChunkLoads;
    private final ExecutorService orchestrator;
    private final ExecutorService renderPool;

    public PaperMapRenderer(Plugin plugin, int maxAreaBlocks, int maxConcurrentChunkLoads) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = plugin.getServer();
        this.maxAreaBlocks = maxAreaBlocks;
        this.maxConcurrentChunkLoads = Math.max(1, maxConcurrentChunkLoads);
        this.orchestrator = Executors.newSingleThreadExecutor(named("WorldToPng-orchestrator"));
        int workers = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.renderPool = Executors.newFixedThreadPool(workers, named("WorldToPng-render"));
    }

    public CompletableFuture<RenderedMap> render(RenderRequest request) {
        Objects.requireNonNull(request, "request");

        long area = request.area();
        if (area <= 0) {
            return CompletableFuture.failedFuture(new RenderException("Render area is empty"));
        }
        if (area > maxAreaBlocks) {
            return CompletableFuture.failedFuture(new RenderException(
                    "Render area " + area + " blocks exceeds the configured limit of " + maxAreaBlocks
                            + " (raise WorldToPng.Settings.maxAreaBlocks if this is intended)"));
        }

        final World world = request.world();
        final MapBuffer buffer = new MapBuffer(world.getName(),
                request.minX(), request.minZ(), request.maxX(), request.maxZ());
        final int[] paletteCache = buildPaletteCache(request.options().palette());

        final CompletableFuture<RenderedMap> result = new CompletableFuture<>();
        orchestrator.execute(() -> {
            try {
                gatherAndRenderChunks(request, buffer, paletteCache);
                RenderedMap map = renderPool.submit(() -> buffer.compose(request.options())).get();
                result.complete(map);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result.completeExceptionally(new RenderException("Render was interrupted", e));
            } catch (Throwable t) {
                result.completeExceptionally(toRenderException(t));
            }
        });
        return result;
    }

    public void shutdown() {
        orchestrator.shutdownNow();
        renderPool.shutdownNow();
    }

    private void gatherAndRenderChunks(RenderRequest request, MapBuffer buffer, int[] paletteCache)
            throws InterruptedException {
        final World world = request.world();
        final boolean generate = request.options().generateMissingChunks();
        final int worldMinY = world.getMinHeight();
        final int minCX = request.minX() >> 4;
        final int maxCX = request.maxX() >> 4;
        final int minCZ = request.minZ() >> 4;
        final int maxCZ = request.maxZ() >> 4;

        final Semaphore permits = new Semaphore(maxConcurrentChunkLoads);
        final List<CompletableFuture<Void>> tasks = new ArrayList<>();

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                permits.acquire();
                CompletableFuture<Void> task = snapshot(world, cx, cz, generate)
                        .thenAcceptAsync(snapshot ->
                                renderChunk(snapshot, request, buffer, paletteCache, worldMinY), renderPool)
                        .whenComplete((ignored, error) -> permits.release());
                tasks.add(task);
            }
        }

        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
    }

    /**
     * Load a chunk and snapshot it on the main thread. {@code getChunkAtAsync} completes its
     * callback on the main thread, so the snapshot is taken there and is then safe to read from
     * worker threads. Completes with {@code null} for an ungenerated chunk when {@code generate}
     * is false.
     */
    private CompletableFuture<ChunkSnapshot> snapshot(World world, int cx, int cz, boolean generate) {
        CompletableFuture<ChunkSnapshot> out = new CompletableFuture<>();
        Runnable load = () -> {
            try {
                world.getChunkAtAsync(cx, cz, generate).whenComplete((chunk, error) -> {
                    if (error != null) {
                        out.completeExceptionally(error);
                    } else if (chunk == null) {
                        out.complete(null);
                    } else {
                        try {
                            out.complete(chunk.getChunkSnapshot(true, false, false));
                        } catch (Throwable t) {
                            out.completeExceptionally(t);
                        }
                    }
                });
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        };
        if (server.isPrimaryThread()) {
            load.run();
        } else {
            server.getScheduler().runTask(plugin, load);
        }
        return out;
    }

    private void renderChunk(ChunkSnapshot snapshot, RenderRequest request,
                            MapBuffer buffer, int[] paletteCache, int worldMinY) {
        if (snapshot == null) {
            return; // ungenerated chunk: leave its columns transparent
        }
        final boolean waterDepth = request.options().waterDepthShading();
        final int minWX = request.minX();
        final int maxWX = request.maxX();
        final int minWZ = request.minZ();
        final int maxWZ = request.maxZ();
        final int baseX = snapshot.getX() << 4;
        final int baseZ = snapshot.getZ() << 4;

        for (int dx = 0; dx < 16; dx++) {
            final int worldX = baseX + dx;
            if (worldX < minWX || worldX > maxWX) {
                continue;
            }
            for (int dz = 0; dz < 16; dz++) {
                final int worldZ = baseZ + dz;
                if (worldZ < minWZ || worldZ > maxWZ) {
                    continue;
                }
                final int surfaceY = highestSolidY(snapshot, dx, dz, worldMinY);
                if (surfaceY < worldMinY) {
                    continue; // empty column (void)
                }
                final Material surface = snapshot.getBlockType(dx, surfaceY, dz);
                int color = paletteCache[surface.ordinal()];
                if (Colors.alpha(color) == 0) {
                    continue; // palette asked to skip this block
                }
                if (waterDepth && isWater(surface)) {
                    int floor = surfaceY;
                    while (floor > worldMinY && isWater(snapshot.getBlockType(dx, floor - 1, dz))) {
                        floor--;
                    }
                    color = darkenByDepth(color, surfaceY - floor);
                }
                buffer.set(worldX, worldZ, color, surfaceY);
            }
        }
    }

    /** Pre-resolve every block material to a colour so the hot loop is a plain array lookup. */
    private static int[] buildPaletteCache(ColorPalette palette) {
        Material[] materials = Material.values();
        int[] cache = new int[materials.length];
        for (int i = 0; i < materials.length; i++) {
            Material material = materials[i];
            if (material.isLegacy()) {
                continue;
            }
            String id;
            try {
                id = material.getKey().toString();
            } catch (Throwable ignored) {
                id = "minecraft:" + material.name().toLowerCase(Locale.ROOT);
            }
            cache[i] = palette.argb(id);
        }
        return cache;
    }

    private static int highestSolidY(ChunkSnapshot snapshot, int dx, int dz, int worldMinY) {
        int y = snapshot.getHighestBlockYAt(dx, dz);
        while (y >= worldMinY && isSkippable(snapshot.getBlockType(dx, y, dz))) {
            y--;
        }
        return y;
    }

    private static boolean isSkippable(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private static boolean isWater(Material material) {
        return material == Material.WATER || material == Material.BUBBLE_COLUMN;
    }

    private static int darkenByDepth(int argb, int depth) {
        double factor = Math.max(0.35, 1.0 - depth * 0.03);
        return Colors.scale(argb, factor);
    }

    private static RenderException toRenderException(Throwable t) {
        Throwable cause = t;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof RenderException re) {
            return re;
        }
        return new RenderException("Render failed: " + cause, cause);
    }

    private static ThreadFactory named(String prefix) {
        final AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
