package liltrip.worldToPng.core;

/**
 * Helpers for packed {@code 0xAARRGGBB} colours. Shared by the renderer, the palette and the
 * drawing API so colour maths lives in exactly one place.
 */
public final class Colors {

    private Colors() {
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    /** Multiply the RGB channels by {@code factor} (clamped), preserving alpha. */
    public static int scale(int argb, double factor) {
        int a = argb & 0xFF000000;
        int r = clamp((int) (red(argb) * factor));
        int g = clamp((int) (green(argb) * factor));
        int b = clamp((int) (blue(argb) * factor));
        return a | (r << 16) | (g << 8) | b;
    }

    /** Replace the alpha channel. */
    public static int withAlpha(int argb, int alpha) {
        return (clamp(alpha) << 24) | (argb & 0x00FFFFFF);
    }

    public static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}
