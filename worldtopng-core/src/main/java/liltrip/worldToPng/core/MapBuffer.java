package liltrip.worldToPng.core;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * A platform-neutral grid of map columns that a platform adapter fills and then composes into a
 * {@link RenderedMap}.
 *
 * <p>An adapter walks a world's columns (on whatever thread is correct for that platform) and
 * calls {@link #set(int, int, int, int)} with the world coordinate, the already-resolved base
 * colour, and the surface height. Different chunks write disjoint pixels, so {@code set} is safe
 * to call concurrently. {@link #compose(RenderOptions)} then applies cross-column relief shading
 * and packs the result into an image.
 */
public final class MapBuffer {

    /** Sentinel height for "no block here" (void / ungenerated). */
    static final int VOID = Integer.MIN_VALUE;

    static {
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
    }

    private final String worldName;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final int width;
    private final int depth;
    private final int[] argb;
    private final int[] height;

    public MapBuffer(String worldName, int minX, int minZ, int maxX, int maxZ) {
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
        this.width = this.maxX - this.minX + 1;
        this.depth = this.maxZ - this.minZ + 1;
        this.argb = new int[width * depth];
        this.height = new int[width * depth];
        Arrays.fill(height, VOID);
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    /**
     * Set the column at a world coordinate. Out-of-bounds coordinates are ignored, so adapters can
     * blindly push whole border chunks.
     *
     * @param argb     the base colour for the column ({@code alpha == 0} leaves it transparent)
     * @param surfaceY the surface block's Y, used for relief shading
     */
    public void set(int worldX, int worldZ, int argb, int surfaceY) {
        if (worldX < minX || worldX > maxX || worldZ < minZ || worldZ > maxZ) {
            return;
        }
        int index = (worldZ - minZ) * width + (worldX - minX);
        this.argb[index] = argb;
        this.height[index] = surfaceY;
    }

    /** Apply shading (per {@code options}) and build the final {@link RenderedMap}. */
    public RenderedMap compose(RenderOptions options) {
        if (options.heightShading()) {
            applyHeightShading(argb, height, width, depth);
        }
        BufferedImage image = new BufferedImage(width, depth, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, depth, argb, 0, width);
        return new RenderedMap(image, worldName, minX, minZ, maxX, maxZ);
    }

    /**
     * Relief shading: compare each column's surface height to its northern neighbour (the pixel
     * above, {@code -Z}). Higher than north is brightened, lower is darkened. Parallelised by row;
     * each row writes only its own pixels and reads heights read-only, so it is race-free.
     */
    private static void applyHeightShading(int[] argb, int[] height, int width, int depth) {
        IntStream.range(0, depth).parallel().forEach(py -> {
            final int rowBase = py * width;
            final int northBase = py > 0 ? rowBase - width : -1;
            for (int px = 0; px < width; px++) {
                final int index = rowBase + px;
                if (Colors.alpha(argb[index]) == 0) {
                    continue;
                }
                final int here = height[index];
                if (here == VOID) {
                    continue;
                }
                int north = northBase >= 0 ? height[northBase + px] : here;
                if (north == VOID) {
                    north = here;
                }
                if (here > north) {
                    argb[index] = Colors.scale(argb[index], 1.12);
                } else if (here < north) {
                    argb[index] = Colors.scale(argb[index], 0.88);
                }
            }
        });
    }
}
