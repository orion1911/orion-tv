package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * boost_button: the top bar's Boost panel, in the settings drawer's look. Memory and storage, free and used, and
 * Clean up: every launchable app sitting in the background is stopped (ActivityManager.killBackgroundProcesses, the
 * one way a launcher may: what is on screen and the system's own services are left alone), then the memory that
 * came back is shown. Other apps' caches can only be cleared by the system, so the TV's Storage page is one row
 * away. Apps stopped this way start a little slower next time; Android would otherwise reclaim them when it needs to.
 */
public final class BoostActivity extends Activity {
    /**
     * Never stopped: Google's services and the store (sign-in, casting, updates), TV Settings, and the Weather add-on,
     * which Home's weather widget starts again each time Home is shown (stopping it only made every Clean up find
     * it again).
     */
    private static final Set<String> KEEP = new HashSet<>(java.util.Arrays.asList(
            "com.google.android.gms", "com.google.android.gsf", "com.android.vending",
            "com.google.android.tvlauncher", "com.android.tv.settings", "io.github.sumyabazar1.weather"));

    private float dp;
    private LinearLayout stats;
    private TextView cleanTitle, cleanHint;
    private View clean;
    private boolean cleaning;
    /** The Also clear app caches box. */
    private boolean withCache;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_panel);
        dp = getResources().getDisplayMetrics().density;
        findViewById(R.id.scrim).setOnClickListener(v ->
                QuickSettingsActivity.slideOutAndFinish(this, findViewById(R.id.drawer)));
        findViewById(R.id.drawer).setOnClickListener(v -> { });
        QuickSettingsActivity.slideIn(findViewById(R.id.drawer));
        ((View) findViewById(R.id.panelGear).getParent()).setVisibility(View.GONE);   // the settings panel's clock row

        LinearLayout top = findViewById(R.id.tiles);
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(Math.round(4 * dp), 0, 0, Math.round(12 * dp));
        ImageView rocket = new ImageView(this);
        rocket.setImageResource(R.drawable.ic_boost);
        int r = Math.round(20 * dp);
        title.addView(rocket, new LinearLayout.LayoutParams(r, r));
        TextView name = QuickSettingsActivity.label(this, getString(R.string.boost), 16, R.string.font_medium,
                QuickSettingsActivity.TEXT, QuickSettingsActivity.TEXT);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nlp.setMarginStart(Math.round(10 * dp));
        title.addView(name, nlp);
        top.addView(title);
        stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.VERTICAL);
        top.addView(stats);
        showStats();

        LinearLayout cards = findViewById(R.id.cards);
        clean = QuickSettingsActivity.card(this, dp, getDrawable(R.drawable.ic_boost), true,
                getString(R.string.boost_clean), getString(R.string.boost_clean_hint));
        cleanTitle = (TextView) ((LinearLayout) ((LinearLayout) clean).getChildAt(1)).getChildAt(0);
        cleanHint = (TextView) ((LinearLayout) ((LinearLayout) clean).getChildAt(1)).getChildAt(1);
        clean.setOnClickListener(v -> cleanUp());
        cards.addView(clean);
        // Force stop alone is the quick clean; caches only when asked, box off each time the panel opens.
        View cacheBox = QuickSettingsActivity.card(this, dp, getDrawable(R.drawable.ic_check_off), true,
                getString(R.string.boost_with_cache), getString(R.string.boost_with_cache_hint));
        ImageView box = (ImageView) ((android.view.ViewGroup) ((LinearLayout) cacheBox).getChildAt(0)).getChildAt(0);
        cacheBox.setOnClickListener(v -> {
            withCache = !withCache;
            box.setImageResource(withCache ? R.drawable.ic_check_on : R.drawable.ic_check_off);
            cleanHint.setText(withCache ? R.string.boost_clean_hint_cache : R.string.boost_clean_hint);
        });
        cards.addView(cacheBox);
        View storage = QuickSettingsActivity.card(this, dp, getDrawable(R.drawable.ic_settings), true,
                getString(R.string.boost_storage), getString(R.string.boost_storage_hint));
        storage.setOnClickListener(v -> openStorage());
        cards.addView(storage);
        clean.requestFocus();
    }

    /** Memory and storage: a line of words and a bar each. */
    private void showStats() {
        stats.removeAllViews();
        ActivityManager am = getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mem);
        addStat(getString(R.string.boost_memory), mem.availMem, mem.totalMem);
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        addStat(getString(R.string.boost_storage_title), fs.getAvailableBytes(), fs.getTotalBytes());
    }

    private void addStat(String what, long free, long total) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(Math.round(4 * dp), 0, Math.round(4 * dp), Math.round(12 * dp));
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        TextView label = QuickSettingsActivity.label(this, what, 12, R.string.font_medium,
                QuickSettingsActivity.TEXT, QuickSettingsActivity.TEXT);
        line.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView numbers = QuickSettingsActivity.label(this,
                getString(R.string.boost_free_of, Formatter.formatShortFileSize(this, free),
                        Formatter.formatShortFileSize(this, total)),
                11, R.string.font_regular, QuickSettingsActivity.SUB, QuickSettingsActivity.SUB);
        line.addView(numbers);
        block.addView(line);
        // The bar: used part filled, the rest the track.
        float used = total > 0 ? Math.max(0f, Math.min(1f, (total - free) / (float) total)) : 0f;
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x33FFFFFF);
        track.setCornerRadius(3 * dp);
        bar.setBackground(track);
        View fill = new View(this);
        GradientDrawable fillShape = new GradientDrawable();
        fillShape.setColor(used > 0.85f ? 0xFFFF8A65 : 0xFF8FB8FF);
        fillShape.setCornerRadius(3 * dp);
        fill.setBackground(fillShape);
        int h = Math.round(6 * dp);
        bar.addView(fill, new LinearLayout.LayoutParams(0, h, used));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, h, 1f - used));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h);
        blp.topMargin = Math.round(6 * dp);
        block.addView(bar, blp);
        stats.addView(block);
    }

    /**
     * Hands the apps to BoostService, which force-stops them and clears their caches through TV Settings (this
     * firmware refuses killBackgroundProcesses for other apps). The apps: the ones a person runs, opened since the
     * TV started when usage access tells that, never this launcher, Google's services, the keyboard or what is
     * playing. The panel closes; the service's cover shows the progress and the result.
     */
    private void cleanUp() {
        if (cleaning) return;
        cleaning = true;
        cleanTitle.setText(R.string.boost_cleaning);
        new Thread(() -> {
            Set<String> packages = launchable();
            packages.remove(getPackageName());
            packages.removeAll(KEEP);
            packages.removeAll(playing());
            String keyboard = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (keyboard != null && keyboard.contains("/")) packages.remove(keyboard.substring(0, keyboard.indexOf('/')));
            Set<String> used = usedSinceBoot();
            if (used != null) packages.retainAll(used);
            // Force stop only: an app Android already has stopped has nothing to close (a second run right after the
            // first finds nothing, and says so). With caches, they stay for their cache.
            if (!withCache) packages.removeIf(this::alreadyStopped);
            java.util.List<String> list = new java.util.ArrayList<>(packages);
            java.util.Collections.sort(list);
            main.post(() -> {
                if (isFinishing()) return;
                cleaning = false;
                if (list.isEmpty()) {
                    cleanTitle.setText(R.string.boost_clean);
                    cleanHint.setText(R.string.boost_nothing);
                    return;
                }
                if (BoostService.clean(this, list, withCache)) {
                    finish();
                } else {
                    cleanTitle.setText(R.string.boost_clean);
                    cleanHint.setText(R.string.boost_no_helper);
                }
            });
        }, "boost").start();
    }

    /** Android's stopped flag: set by a force stop, cleared when the app (or anything) starts it again. */
    private boolean alreadyStopped(String pkg) {
        try {
            return (getPackageManager().getApplicationInfo(pkg, 0).flags
                    & android.content.pm.ApplicationInfo.FLAG_STOPPED) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    /**
     * Apps brought to the front since the TV started, or null when usage access is not granted (then every app a
     * person runs is cleaned). Granted over ADB: appops set com.gathin.tvhome GET_USAGE_STATS allow.
     */
    private Set<String> usedSinceBoot() {
        android.app.AppOpsManager ops = getSystemService(android.app.AppOpsManager.class);
        // unsafeCheckOpNoThrow is API 29; the older checkOpNoThrow still answers on 26 to 28.
        int mode = android.os.Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(), getPackageName())
                : ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(), getPackageName());
        if (mode != android.app.AppOpsManager.MODE_ALLOWED) return null;
        android.app.usage.UsageStatsManager usm = getSystemService(android.app.usage.UsageStatsManager.class);
        long now = System.currentTimeMillis();
        long boot = now - android.os.SystemClock.elapsedRealtime();
        Set<String> used = new HashSet<>();
        android.app.usage.UsageEvents events = usm.queryEvents(boot, now);
        android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) used.add(e.getPackageName());
        }
        return used;
    }
    /**
     * Packages with a TV or phone launcher entry: the apps a person runs. The TV's own built-in apps (system, never
     * updated: Live TV, inputs, its settings) are left out; updated ones such as YouTube are in.
     */
    private Set<String> launchable() {
        PackageManager pm = getPackageManager();
        Set<String> out = new HashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> found = pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
            for (ResolveInfo ri : found) {
                android.content.pm.ApplicationInfo app = ri.activityInfo.applicationInfo;
                boolean builtIn = (app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        && (app.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (!builtIn) out.add(ri.activityInfo.packageName);
            }
        }
        return out;
    }

    /**
     * Apps playing or buffering right now (music in the background, a cast): left running. Their media sessions are
     * visible through the notification listener's access (GuardRevival); without it, none are known.
     */
    private Set<String> playing() {
        Set<String> out = new HashSet<>();
        try {
            android.media.session.MediaSessionManager sessions =
                    getSystemService(android.media.session.MediaSessionManager.class);
            for (android.media.session.MediaController c : sessions.getActiveSessions(
                    new android.content.ComponentName(this, GuardRevival.class))) {
                android.media.session.PlaybackState s = c.getPlaybackState();
                if (s != null && (s.getState() == android.media.session.PlaybackState.STATE_PLAYING
                        || s.getState() == android.media.session.PlaybackState.STATE_BUFFERING)) {
                    out.add(c.getPackageName());
                }
            }
        } catch (RuntimeException ignored) {
            // no listener access: nothing known to be playing
        }
        return out;
    }

    /** The TV's Storage page, where app caches can be cleared; TV Settings when it has none. */
    private void openStorage() {
        Intent[] targets = {new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                SystemSettings.intent(this, Settings.ACTION_SETTINGS)};
        for (Intent target : targets) {
            if (target == null) continue;
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // the next one
            }
        }
        GlassToast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
    }

    /** Back slides the panel out before it closes. */
    @Override
    public void onBackPressed() {
        QuickSettingsActivity.slideOutAndFinish(this, findViewById(R.id.drawer));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }
}
