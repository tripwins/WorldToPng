package liltrip.worldToPng.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * A fluent 2D surface for annotating a {@link RenderedMap} after rendering: text, coloured text,
 * images, and shapes (lines, rectangles, circles, ovals, polylines/polygons). Obtain one via
 * {@link RenderedMap#canvas()}.
 *
 * <p>Drawing mutates the map's image in place, so you can {@link RenderedMap#writeTo(Path)} the
 * same map afterwards. Colours are packed {@code 0xAARRGGBB} ints (same convention as the
 * palette). Coordinates are pixels, except the {@code *AtWorld} helpers which take world block
 * coordinates and map them through the map's bounds.
 *
 * <p>The canvas carries a small "pen" (colour, stroke width, font) used by the calls that don't
 * take an explicit colour. It holds a {@link Graphics2D}; close it (it is {@link AutoCloseable})
 * or call {@link #finish()} when done.
 *
 * <pre>{@code
 * try (MapCanvas c = map.canvas()) {
 *     c.color(0xFFFFFFFF).fontSize(16f).bold().text("Spawn", 8, 20);
 *     c.color(0xFFE53935).strokeWidth(2f).markerAtWorld(spawnX, spawnZ, 4);
 * }
 * map.writeTo(out);
 * }</pre>
 */
public final class MapCanvas implements AutoCloseable {

    private final RenderedMap map;
    private final Graphics2D g;

    MapCanvas(RenderedMap map) {
        this.map = Objects.requireNonNull(map, "map");
        this.g = map.image().createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    }

    // ---- coordinate mapping (world block -> pixel) --------------------------

    public int toPixelX(int worldX) {
        return worldX - map.minX();
    }

    public int toPixelZ(int worldZ) {
        return worldZ - map.minZ();
    }

    // ---- pen state ----------------------------------------------------------

    /** Set the current colour (packed {@code 0xAARRGGBB}). */
    public MapCanvas color(int argb) {
        g.setColor(new Color(argb, true));
        return this;
    }

    /** Set the current stroke width, in pixels. */
    public MapCanvas strokeWidth(float pixels) {
        g.setStroke(new BasicStroke(pixels, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        return this;
    }

    /** Set the current font. */
    public MapCanvas font(Font font) {
        g.setFont(Objects.requireNonNull(font, "font"));
        return this;
    }

    /** Set the current font size, keeping the family and style. */
    public MapCanvas fontSize(float size) {
        g.setFont(g.getFont().deriveFont(size));
        return this;
    }

    /** Make the current font bold. */
    public MapCanvas bold() {
        g.setFont(g.getFont().deriveFont(Font.BOLD));
        return this;
    }

    // ---- text ---------------------------------------------------------------

    /** Draw text with the current pen; {@code (x, y)} is the text baseline. */
    public MapCanvas text(String text, int x, int y) {
        g.drawString(text, x, y);
        return this;
    }

    /** Draw coloured text. */
    public MapCanvas text(String text, int x, int y, int argb) {
        return color(argb).text(text, x, y);
    }

    /** Draw text centred horizontally on {@code centerX}, baseline at {@code y}. */
    public MapCanvas textCentered(String text, int centerX, int y) {
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, centerX - w / 2, y);
        return this;
    }

    /** Draw text anchored at a world coordinate. */
    public MapCanvas textAtWorld(String text, int worldX, int worldZ) {
        return text(text, toPixelX(worldX), toPixelZ(worldZ));
    }

    // ---- shapes -------------------------------------------------------------

    public MapCanvas line(int x1, int y1, int x2, int y2) {
        g.drawLine(x1, y1, x2, y2);
        return this;
    }

    public MapCanvas rect(int x, int y, int w, int h) {
        g.drawRect(x, y, w, h);
        return this;
    }

    public MapCanvas fillRect(int x, int y, int w, int h) {
        g.fillRect(x, y, w, h);
        return this;
    }

    public MapCanvas oval(int x, int y, int w, int h) {
        g.drawOval(x, y, w, h);
        return this;
    }

    public MapCanvas fillOval(int x, int y, int w, int h) {
        g.fillOval(x, y, w, h);
        return this;
    }

    /** Outline a circle centred on {@code (cx, cy)}. */
    public MapCanvas circle(int cx, int cy, int radius) {
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        return this;
    }

    /** Fill a circle centred on {@code (cx, cy)}. */
    public MapCanvas fillCircle(int cx, int cy, int radius) {
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        return this;
    }

    public MapCanvas polyline(int[] xs, int[] ys) {
        g.drawPolyline(xs, ys, Math.min(xs.length, ys.length));
        return this;
    }

    public MapCanvas polygon(int[] xs, int[] ys) {
        g.drawPolygon(xs, ys, Math.min(xs.length, ys.length));
        return this;
    }

    public MapCanvas fillPolygon(int[] xs, int[] ys) {
        g.fillPolygon(xs, ys, Math.min(xs.length, ys.length));
        return this;
    }

    /** A filled dot at a world coordinate (a map marker). */
    public MapCanvas markerAtWorld(int worldX, int worldZ, int radius) {
        return fillCircle(toPixelX(worldX), toPixelZ(worldZ), radius);
    }

    // ---- images -------------------------------------------------------------

    /** Draw an image with its top-left at {@code (x, y)}. */
    public MapCanvas image(BufferedImage img, int x, int y) {
        g.drawImage(img, x, y, null);
        return this;
    }

    /** Draw an image scaled to {@code w x h} at {@code (x, y)}. */
    public MapCanvas image(BufferedImage img, int x, int y, int w, int h) {
        g.drawImage(img, x, y, w, h, null);
        return this;
    }

    /** Draw an image centred on a world coordinate. */
    public MapCanvas imageAtWorld(BufferedImage img, int worldX, int worldZ) {
        return image(img, toPixelX(worldX) - img.getWidth() / 2, toPixelZ(worldZ) - img.getHeight() / 2);
    }

    // ---- lifecycle ----------------------------------------------------------

    /** The map being drawn on, for {@code writeTo}/{@code toPngBytes}/&hellip; */
    public RenderedMap map() {
        return map;
    }

    /** Dispose the graphics context and return the (now annotated) map. */
    public RenderedMap finish() {
        g.dispose();
        return map;
    }

    @Override
    public void close() {
        g.dispose();
    }

    // ---- image loading helpers ----------------------------------------------

    /** Read an image (PNG/JPG/&hellip;) from a file, e.g. an icon to stamp on the map. */
    public static BufferedImage loadImage(Path file) throws IOException {
        BufferedImage img = ImageIO.read(file.toFile());
        if (img == null) {
            throw new IOException("Unsupported or unreadable image file: " + file);
        }
        return img;
    }

    /** Read an image from bytes. */
    public static BufferedImage loadImage(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                throw new IOException("Unsupported or unreadable image bytes");
            }
            return img;
        }
    }
}
