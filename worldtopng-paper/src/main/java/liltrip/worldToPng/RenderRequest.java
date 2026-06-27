package liltrip.worldToPng;

import java.util.Objects;
import liltrip.worldToPng.core.RenderOptions;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Describes a single render: the world, the inclusive rectangular block region (in world
 * coordinates), and the {@link RenderOptions} to use.
 *
 * <p>The resulting image is {@link #width()} &times; {@link #depth()} pixels, one pixel per block
 * column, oriented like an in-game map: {@code -Z} (north) is up and {@code -X} (west) is left.
 * Bounds are normalised on construction, so corners may be given in any order.
 */
public record RenderRequest(
        World world,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        RenderOptions options
) {
    public RenderRequest {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(options, "options");
        if (minX > maxX) {
            int tmp = minX;
            minX = maxX;
            maxX = tmp;
        }
        if (minZ > maxZ) {
            int tmp = minZ;
            minZ = maxZ;
            maxZ = tmp;
        }
    }

    /** Width of the output image in pixels (blocks along X, inclusive). */
    public int width() {
        return maxX - minX + 1;
    }

    /** Height of the output image in pixels (blocks along Z, inclusive). */
    public int depth() {
        return maxZ - minZ + 1;
    }

    /** Total number of block columns (pixels) this render covers. */
    public long area() {
        return (long) width() * depth();
    }

    /** Start building a request for {@code world}. */
    public static Builder builder(World world) {
        return new Builder(world);
    }

    /** A square region of side {@code 2 * radius + 1} centred on a column. */
    public static RenderRequest around(World world, int centerX, int centerZ, int radius, RenderOptions options) {
        return new RenderRequest(world, centerX - radius, centerZ - radius, centerX + radius, centerZ + radius, options);
    }

    /**
     * Render the rectangle spanning two world points (e.g. a WorldEdit selection or two players).
     * Only the X/Z of each point are used; the <b>Y level is ignored</b> since the map is top-down.
     * Corner order does not matter, and both points must be in the same world.
     */
    public static RenderRequest between(Location corner1, Location corner2) {
        return between(corner1, corner2, RenderOptions.defaults());
    }

    /** As {@link #between(Location, Location)}, with explicit options. */
    public static RenderRequest between(Location corner1, Location corner2, RenderOptions options) {
        Objects.requireNonNull(corner1, "corner1");
        Objects.requireNonNull(corner2, "corner2");
        World world = Objects.requireNonNull(corner1.getWorld(), "corner1 has no world");
        if (corner2.getWorld() != null && !world.equals(corner2.getWorld())) {
            throw new IllegalArgumentException("corner1 and corner2 must be in the same world");
        }
        return new RenderRequest(world,
                corner1.getBlockX(), corner1.getBlockZ(),
                corner2.getBlockX(), corner2.getBlockZ(),
                options);
    }

    /** Fluent builder for {@link RenderRequest}. */
    public static final class Builder {
        private final World world;
        private int minX, minZ, maxX, maxZ;
        private boolean boundsSet;
        private RenderOptions options = RenderOptions.defaults();

        private Builder(World world) {
            this.world = Objects.requireNonNull(world, "world");
        }

        /** Inclusive block bounds; corner order does not matter. */
        public Builder bounds(int x1, int z1, int x2, int z2) {
            this.minX = x1;
            this.minZ = z1;
            this.maxX = x2;
            this.maxZ = z2;
            this.boundsSet = true;
            return this;
        }

        /** A square region of side {@code 2 * radius + 1} centred on a column. */
        public Builder around(int centerX, int centerZ, int radius) {
            return bounds(centerX - radius, centerZ - radius, centerX + radius, centerZ + radius);
        }

        /** Set bounds from two world points; only X/Z are used (the Y level is ignored). */
        public Builder between(Location corner1, Location corner2) {
            Objects.requireNonNull(corner1, "corner1");
            Objects.requireNonNull(corner2, "corner2");
            return bounds(corner1.getBlockX(), corner1.getBlockZ(), corner2.getBlockX(), corner2.getBlockZ());
        }

        public Builder options(RenderOptions options) {
            this.options = Objects.requireNonNull(options, "options");
            return this;
        }

        public RenderRequest build() {
            if (!boundsSet) {
                throw new IllegalStateException("bounds(...) or around(...) must be set before build()");
            }
            return new RenderRequest(world, minX, minZ, maxX, maxZ, options);
        }
    }
}
