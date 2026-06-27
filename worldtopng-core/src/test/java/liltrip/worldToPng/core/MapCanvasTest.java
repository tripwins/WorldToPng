package liltrip.worldToPng.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class MapCanvasTest {

    private RenderedMap blankMap(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, 0xFF000000);
            }
        }
        return new RenderedMap(img, "test", 0, 0, w - 1, h - 1);
    }

    @Test
    void fillRectChangesPixels() {
        RenderedMap map = blankMap(32, 32);
        try (MapCanvas c = map.canvas()) {
            c.color(0xFFFF0000).fillRect(4, 4, 8, 8);
        }
        int px = map.image().getRGB(6, 6);
        assertEquals(0xFF, Colors.alpha(px));
        assertTrue(Colors.red(px) > 200, "red channel should be high after a red fill");
    }

    @Test
    void worldToPixelMapsThroughBounds() {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        RenderedMap map = new RenderedMap(img, "w", -100, -100, -91, -91);
        try (MapCanvas c = map.canvas()) {
            assertEquals(0, c.toPixelX(-100));
            assertEquals(5, c.toPixelX(-95));
            assertEquals(9, c.toPixelZ(-91));
        }
    }

    @Test
    void textAndShapesDoNotThrow() {
        RenderedMap map = blankMap(64, 64);
        try (MapCanvas c = map.canvas()) {
            c.color(0xFFFFFFFF).fontSize(14f).bold().text("Spawn", 2, 14)
                    .strokeWidth(2f).line(0, 0, 63, 63).circle(32, 32, 10).markerAtWorld(32, 32, 3);
        }
        assertEquals(64, map.width());
    }

    @Test
    void loadImageRoundTripsFromBytes() throws Exception {
        RenderedMap map = blankMap(8, 8);
        byte[] png = map.toPngBytes();
        BufferedImage loaded = MapCanvas.loadImage(png);
        assertEquals(8, loaded.getWidth());
        assertEquals(8, loaded.getHeight());
    }
}
