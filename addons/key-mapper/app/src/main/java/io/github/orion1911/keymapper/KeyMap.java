package io.github.orion1911.keymapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The saved list of remote buttons: one "token=target" per line, where a token is "scan:187" (a Linux
 * scancode) or "code:190" (an Android keycode) and the target is the package to open. A scancode is tried
 * before a keycode, since remotes such as Sony's send several app buttons as one keycode. Blank lines,
 * lines without a target and tokens of any other shape are skipped; a token listed twice keeps the last
 * target, as the launcher's app_keys does.
 */
final class KeyMap {
    static final String SCAN = "scan:";
    static final String CODE = "code:";

    private KeyMap() { }

    static Map<String, String> parse(String store) {
        Map<String, String> keys = new LinkedHashMap<>();
        if (store == null) return keys;
        for (String line : store.split("\n")) {
            String text = line.trim();
            int equals = text.indexOf('=');
            if (equals <= 0) continue;
            String token = text.substring(0, equals).trim();
            String target = text.substring(equals + 1).trim();
            if (target.isEmpty() || !valid(token)) continue;
            keys.put(token, target);
        }
        return keys;
    }

    static String encode(Map<String, String> keys) {
        StringBuilder store = new StringBuilder();
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            if (store.length() > 0) store.append('\n');
            store.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return store.toString();
    }

    /** A press as its saved token: the scancode when the remote reports one, the keycode otherwise. */
    static String token(int keyCode, int scanCode) {
        return scanCode > 0 ? SCAN + scanCode : CODE + keyCode;
    }

    /** True for a token this list can hold: "scan:<digits>" or "code:<digits>". */
    static boolean valid(String token) {
        if (token.startsWith(SCAN)) return digits(token.substring(SCAN.length()));
        if (token.startsWith(CODE)) return digits(token.substring(CODE.length()));
        return false;
    }

    /** The number inside a valid token; 0 for anything else. */
    static int number(String token) {
        if (!valid(token)) return 0;
        return Integer.parseInt(token.substring(token.indexOf(':') + 1));
    }

    private static boolean digits(String text) {
        if (text.isEmpty() || text.length() > 6) return false;
        for (int i = 0; i < text.length(); i++) if (!Character.isDigit(text.charAt(i))) return false;
        return true;
    }
}
