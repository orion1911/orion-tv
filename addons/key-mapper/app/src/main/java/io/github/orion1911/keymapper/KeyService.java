package io.github.orion1911.keymapper;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Opens the app mapped to a remote button, from any app. The list is the one MainActivity edits, kept in
 * this app's own preferences: "scan:187=com.stremio.one" or "code:190=org.smarttube.stable". A scancode is
 * tried before a keycode. Every other key passes through untouched.
 *
 * While MainActivity is learning a button it writes a short deadline to the same preferences, and this
 * service then passes every key through, or it would swallow the very press the screen waits for.
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services io.github.orion1911.keymapper/.KeyService
 *   settings put secure accessibility_enabled 1
 */
public class KeyService extends AccessibilityService {

    static final String PREFS = "keymap";
    static final String STORE = "keys";
    /** A millisecond deadline from MainActivity: until then, the next key is learned instead of opened. */
    static final String LEARNING = "learning_until";
    /** The button the service learned for MainActivity: a token, read and cleared there. */
    static final String LEARNED = "learned_key";

    private SharedPreferences prefs;
    private Map<String, String> scans = Collections.emptyMap();
    private Map<String, String> codes = Collections.emptyMap();
    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (changed, key) -> { if (STORE.equals(key)) reload(); };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(listener);
        reload();
    }

    @Override
    public void onDestroy() {
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(listener);
        super.onDestroy();
    }

    private void reload() {
        Map<String, String> all = KeyMap.parse(prefs.getString(STORE, ""));
        Map<String, String> scanKeys = new HashMap<>();
        Map<String, String> codeKeys = new HashMap<>();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            if (entry.getKey().startsWith(KeyMap.SCAN)) scanKeys.put(entry.getKey(), entry.getValue());
            else codeKeys.put(entry.getKey(), entry.getValue());
        }
        scans = scanKeys;
        codes = codeKeys;
    }

    private boolean learning() {
        return System.currentTimeMillis() < prefs.getLong(LEARNING, 0L);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (learning()) return learn(event);
        String target = scans.get(KeyMap.SCAN + event.getScanCode());
        if (target == null) target = codes.get(KeyMap.CODE + event.getKeyCode());
        if (target == null) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) open(target);
        return true;
    }

    /**
     * Learning happens here, not on the screen: firmware buttons such as Sony's Netflix key are global
     * keys, and the window never sees them. The filter does, before that dispatch, so the next press
     * becomes the token MainActivity picks an app for. Arrows, OK and Back stay with the screen.
     */
    private boolean learn(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BACK) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            prefs.edit().putString(LEARNED, KeyMap.token(code, event.getScanCode()))
                    .remove(LEARNING).apply();
        }
        return true;   // swallow the press, so the button's own app does not open over the picker
    }

    private void open(String pkg) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        if (intent == null) return;
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            // the app went away between the press and the launch
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
