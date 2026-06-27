package liltrip.worldToPng.core;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * The result of a render: an ARGB {@link BufferedImage} plus the world and bounds it came from.
 *
 * <p>Get a {@link MapCanvas} via {@link #canvas()} to draw text, images and shapes on top before
 * saving. PNG encoding is lazy ({@link #toPngBytes()} / {@link #writeTo(Path)}).
 *
 * <p>Bounds are inclusive world coordinates; {@code minX}/{@code minZ} are expected to be the
 * lower corner.
 */
public record RenderedMap(
        BufferedImage image,
        String worldName,
        int minX,
        int minZ,
        int maxX,
        int maxZ
) {
    static {
        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }
    }

    public RenderedMap {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(worldName, "worldName");
    }

    /** Image width in pixels. */
    public int width() {
        return image.getWidth();
    }

    /** Image height in pixels. */
    public int height() {
        return image.getHeight();
    }

    /** Open a drawing surface over this map's image. Drawing mutates the image in place. */
    public MapCanvas canvas() {
        return new MapCanvas(this);
    }

    /** Encode the image to PNG and return the bytes. */
    public byte[] toPngBytes() throws IOException {
        var out = new ByteArrayOutputStream(Math.max(1024, width() * height() / 4));
        writePng(out);
        return out.toByteArray();
    }

    /**
     * Encode the image to a PNG file, creating parent directories as needed.
     *
     * @return {@code file}, for chaining
     */
    public Path writeTo(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            writePng(out);
        }
        return file;
    }

    private void writePng(OutputStream out) throws IOException {
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("No PNG ImageIO writer is available in this JVM");
        }
    }
}
