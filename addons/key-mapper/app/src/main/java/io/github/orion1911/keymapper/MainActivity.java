package io.github.orion1911.keymapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The remote-only list of mapped buttons: press OK on "Add a remote button", then press the button you
 * want, then pick the app it opens. OK on a saved row changes its app or removes it. The service reads the
 * same list, so a change applies at once.
 */
public final class MainActivity extends Activity {

    /** How long the service keeps passing keys through while this screen waits for one. */
    private static final long LEARN_MS = 120_000L;

    private SharedPreferences prefs;
    private LinearLayout list;
    private TextView status;
    private TextView hint;
    private boolean learning;
    /** KeyService learns the button and writes it here, since a firmware key never reaches this window. */
    private final SharedPreferences.OnSharedPreferenceChangeListener learnedListener = (changed, key) -> {
        if (!KeyService.LEARNED.equals(key)) return;
        String token = changed.getString(KeyService.LEARNED, "");
        changed.edit().remove(KeyService.LEARNED).apply();
        if (token.isEmpty()) return;
        stopLearning();
        pickTarget(token);
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(KeyService.PREFS, MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(learnedListener);
        list = findViewById(R.id.keyList);
        status = findViewById(R.id.helperStatus);
        hint = findViewById(R.id.hint);
        status.setOnClickListener(v -> openAccessibilitySettings());
        findViewById(R.id.addChoice).setOnClickListener(v -> startLearning());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onStop() {
        stopLearning();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(learnedListener);
        super.onDestroy();
    }

    private Map<String, String> keys() {
        return KeyMap.parse(prefs.getString(KeyService.STORE, ""));
    }

    private void save(Map<String, String> keys) {
        prefs.edit().putString(KeyService.STORE, KeyMap.encode(keys)).apply();
        render();
    }

    private void render() {
        boolean on = helperOn();
        status.setText(getString(on ? R.string.helper_on : R.string.helper_off));
        status.setSelected(!on);
        list.removeAllViews();
        Map<String, String> keys = keys();
        if (keys.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_keys);
            empty.setTextColor(0xFFACB8C1);
            empty.setTextSize(16f);
            empty.setPadding(18, 18, 18, 18);
            list.addView(empty);
            return;
        }
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            TextView row = (TextView) getLayoutInflater().inflate(R.layout.row, list, false);
            row.setText(keyLabel(entry.getKey()) + "  →  " + targetLabel(entry.getValue()));
            final String token = entry.getKey();
            row.setOnClickListener(v -> editEntry(token));
            list.addView(row);
        }
    }

    private String keyLabel(String token) {
        int number = KeyMap.number(token);
        if (token.startsWith(KeyMap.SCAN)) return getString(R.string.scan_label, number);
        String name = KeyEvent.keyCodeToString(number);
        return name != null && name.startsWith("KEYCODE_") ? name.substring("KEYCODE_".length()) : name;
    }

    private String targetLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException gone) {
            return pkg;
        }
    }

    private void editEntry(String token) {
        String[] choices = {getString(R.string.change_app), getString(R.string.remove)};
        new AlertDialog.Builder(this)
                .setTitle(keyLabel(token))
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        pickTarget(token);
                    } else {
                        Map<String, String> keys = keys();
                        keys.remove(token);
                        save(keys);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Learning: the service passes every key through until the next one arrives, Back, or a minute. */
    private void startLearning() {
        learning = true;
        hint.setText(R.string.press_button);
        hint.setVisibility(View.VISIBLE);
        prefs.edit().putLong(KeyService.LEARNING, System.currentTimeMillis() + LEARN_MS)
                .remove(KeyService.LEARNED).apply();
    }

    private void stopLearning() {
        if (!learning) return;
        learning = false;
        hint.setVisibility(View.GONE);
        prefs.edit().remove(KeyService.LEARNING).remove(KeyService.LEARNED).apply();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (learning) {
                stopLearning();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if (!learning) return super.dispatchKeyEvent(event);
        // Arrows and OK still move around the screen, so only an app or a number key is a button to map.
        if (isNavigation(code) || code == KeyEvent.KEYCODE_POWER) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            String token = KeyMap.token(code, event.getScanCode());
            stopLearning();
            pickTarget(token);
            return true;
        }
        return true;
    }

    private static boolean isNavigation(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    /** Every app with a TV entry point, by name, then the same for ordinary apps. */
    private void pickTarget(String token) {
        PackageManager pm = getPackageManager();
        Map<String, String> apps = new LinkedHashMap<>();   // package -> label
        List<Intent> queries = java.util.Arrays.asList(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER));
        for (Intent query : queries) {
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || apps.containsKey(pkg)) continue;
                apps.put(pkg, info.loadLabel(pm).toString());
            }
        }
        List<String> packages = new ArrayList<>(apps.keySet());
        Collections.sort(packages, (a, b) -> apps.get(a).compareToIgnoreCase(apps.get(b)));
        String[] labels = new String[packages.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = apps.get(packages.get(i));
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_app)
                .setItems(labels, (dialog, which) -> {
                    Map<String, String> keys = keys();
                    keys.put(token, packages.get(which));
                    save(keys);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private boolean helperOn() {
        if (Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false;
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String full = getPackageName() + "/" + KeyService.class.getName();
        return enabled.contains(full) || enabled.contains(getPackageName() + "/.KeyService");
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, R.string.open_accessibility_hint, Toast.LENGTH_LONG).show();
        }
    }
}
