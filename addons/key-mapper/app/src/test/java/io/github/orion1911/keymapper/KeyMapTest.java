package io.github.orion1911.keymapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class KeyMapTest {

    @Test
    public void parsesScancodesAndKeycodes() {
        Map<String, String> keys = KeyMap.parse("scan:187=com.stremio.one\ncode:190=org.smarttube.stable");
        assertEquals(2, keys.size());
        assertEquals("com.stremio.one", keys.get("scan:187"));
        assertEquals("org.smarttube.stable", keys.get("code:190"));
    }

    @Test
    public void skipsMalformedLinesAndKeepsTheLastTarget() {
        Map<String, String> keys = KeyMap.parse(
                "\n  \nno-equals\nscan:x=com.a\nscan:1=\nfoo:2=com.b\nscan:3=com.c\nscan:3=com.d\n");
        assertEquals(1, keys.size());
        assertEquals("com.d", keys.get("scan:3"));
    }

    @Test
    public void roundTrips() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("scan:187", "com.stremio.one");
        keys.put("code:190", "org.smarttube.stable");
        assertEquals(keys, KeyMap.parse(KeyMap.encode(keys)));
    }

    @Test
    public void emptyStoreMeansNoKeys() {
        assertTrue(KeyMap.parse(null).isEmpty());
        assertTrue(KeyMap.parse("").isEmpty());
    }

    @Test
    public void tokenPrefersScancodeWhenThereIsOne() {
        assertEquals("scan:187", KeyMap.token(190, 187));
        assertEquals("code:190", KeyMap.token(190, 0));
        assertEquals("code:190", KeyMap.token(190, -1));
    }

    @Test
    public void numberReadsValidTokensOnly() {
        assertEquals(187, KeyMap.number("scan:187"));
        assertEquals(190, KeyMap.number("code:190"));
        assertEquals(0, KeyMap.number("scan:"));
        assertEquals(0, KeyMap.number("KEYCODE_BUTTON_3"));
        assertTrue(KeyMap.valid("scan:187"));
        assertFalse(KeyMap.valid("scan:abc"));
        assertFalse(KeyMap.valid("187"));
    }
}
