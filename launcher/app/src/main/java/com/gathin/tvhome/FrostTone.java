package com.gathin.tvhome;

/**
 * frost_tiles: the colour of the frosted glass, calibrated on the core samples (2026-10-27). Their glass is lighter
 * than the picture behind it in proportion to it: about 0.51 over the peaks sample's forest (lightness 0.27), about
 * 0.22-0.27 over the coast sample's dark hill (0.11), so a dark picture still gives dark glass. On the TV the person found that a bit bright (20:20), so
 * lightness is 0.06 + 1.5x the picture's, about 15 % under the samples: 0.44 at a picture lightness of 0.25 (the lower end of the samples' 0.42-0.56); above that it climbs only 0.14 per unit, so snow
 * ends near 0.53 and a logo still reads. The colour is raised 1.6x up to 0.72 and the hue kept. The colour is raised
 * as chroma (max - min), not as HSL saturation: a near-black pixel's saturation is noise, a few levels of red in a
 * shadow read as fully saturated, and lifting it turned shadows vivid magenta.
 * Plain Java with no Android types, so it is unit-tested on the JVM (FrostToneTest).
 */
final class FrostTone {
    /** Glass lightness = OFFSET + GAIN x the picture's, up to the picture lightness KNEE. */
    static final float OFFSET = 0.06f, GAIN = 1.5f, KNEE = 0.25f;
    /** How fast the glass lightens above KNEE (snow and bright sky). */
    static final float ABOVE_KNEE = 0.14f;
    /** Chroma gain, and the most the glass gets (the sample's vivid blue sits at about 0.7). */
    static final float SATURATE = 1.6f, MAX_CHROMA = 0.72f;

    private FrostTone() {
    }

    /** Tones every pixel in place. */
    static void apply(int[] argb) {
        for (int i = 0; i < argb.length; i++) argb[i] = tone(argb[i]);
    }

    static int tone(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f, d = max - min, h = 0f;
        if (d > 1e-6f) {
            if (max == r) h = (g - b) / d;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
            if (h < 0f) h += 360f;
        }
        float lt = l <= KNEE ? OFFSET + GAIN * l : OFFSET + GAIN * KNEE + (l - KNEE) * ABOVE_KNEE;
        float c = Math.min(Math.min(MAX_CHROMA, d * SATURATE), 1f - Math.abs(2f * lt - 1f));
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = lt - c / 2f, r1, g1, b1;
        if (h < 60f) { r1 = c; g1 = x; b1 = 0f; }
        else if (h < 120f) { r1 = x; g1 = c; b1 = 0f; }
        else if (h < 180f) { r1 = 0f; g1 = c; b1 = x; }
        else if (h < 240f) { r1 = 0f; g1 = x; b1 = c; }
        else if (h < 300f) { r1 = x; g1 = 0f; b1 = c; }
        else { r1 = c; g1 = 0f; b1 = x; }
        return (argb & 0xFF000000) | to8(r1 + m) << 16 | to8(g1 + m) << 8 | to8(b1 + m);
    }

    private static int to8(float v) {
        return Math.round(Math.max(0f, Math.min(1f, v)) * 255f);
    }
}
