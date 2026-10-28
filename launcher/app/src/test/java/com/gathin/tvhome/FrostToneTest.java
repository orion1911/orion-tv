package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** frost_tiles' glass colour, against the core samples: lighter than the picture in proportion, colour kept. */
public class FrostToneTest {

    private static float lightness(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (Math.max(r, Math.max(g, b)) + Math.min(r, Math.min(g, b))) / 510f;
    }

    private static int spread(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    @Test
    public void darkBlueForestTurnsToTheSamplesLightBlueGlass() {
        // Behind the peaks sample's tiles: (12,45,125), lightness 0.27. Its glass: (45,118,213), lightness 0.51; ours is set a little darker.
        int out = FrostTone.tone(0xFF0C2D7D);
        float l = lightness(out);
        assertTrue("lightness " + l, l > 0.40f && l < 0.50f);
        int r = (out >> 16) & 0xFF, g = (out >> 8) & 0xFF, b = out & 0xFF;
        assertTrue("still blue", b > g && g > r);
        assertTrue("vivid, not grey", b - r > 140);
    }

    @Test
    public void darkGroundStaysDarkGlass() {
        // The coast sample's dark hill (22,23,36), lightness 0.11: its glass there is about 0.22.
        float l = lightness(FrostTone.tone(0xFF161724));
        assertTrue("lightness " + l, l > 0.2f && l < 0.32f);
    }

    @Test
    public void snowStaysBelowLogoContrast() {
        assertTrue(lightness(FrostTone.tone(0xFFF0F2F5)) < 0.58f);
        assertTrue(lightness(FrostTone.tone(0xFFFFFFFF)) < 0.58f);
    }

    @Test
    public void greyStaysNeutralAndBlackGetsTheOffset() {
        int grey = FrostTone.tone(0xFF808080);
        assertEquals((grey >> 16) & 0xFF, grey & 0xFF);
        assertEquals(FrostTone.OFFSET, lightness(FrostTone.tone(0xFF000000)), 0.01f);
    }

    @Test
    public void aShadowsColourNoiseIsNotTurnedVivid() {
        // A near-black pixel with a faint red cast, as under the coast sample's tiles: lifted, it must stay greyish.
        int out = FrostTone.tone(0xFF150409);
        assertTrue("spread " + spread(out), spread(out) < 35);
    }

    @Test
    public void hueAndAlphaAreKept() {
        int warm = FrostTone.tone(0xFF64503C);   // brown rock
        assertTrue(((warm >> 16) & 0xFF) > (warm & 0xFF));
        assertEquals(0x80, FrostTone.tone(0x80102030) >>> 24);
    }
}
