package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Boost's Clean up on firmware that refuses killBackgroundProcesses for other apps ("Invalid packageName": this
 * Sony's Android 12 has Android 14's rule). For each app it opens TV Settings' page for that app and presses Force
 * stop, OK, Clear cache, OK, the way a person would, behind a "Cleaning up" cover, then goes Home and shows what came
 * back. It presses only those labels, read from TV Settings' own resources in the TV's language, matched exactly
 * (so never Clear data), only in TV Settings, and only while a clean runs. The launcher switches it on through
 * WRITE_SECURE_SETTINGS the first time Clean up is pressed.
 */
public final class BoostService extends AccessibilityService {
    static final String SETTINGS_PKG = "com.android.tv.settings";
    private static final long STEP_MS = 150, PAGE_TIMEOUT_MS = 5000, DIALOG_TIMEOUT_MS = 3000;
    private static final int FIND_STOP = 0, CONFIRM_STOP = 1, AFTER_STOP = 2, FIND_CACHE = 3, CONFIRM_CACHE = 4,
            DONE_APP = 5;

    private static BoostService connected;
    /** Packages asked for before Android bound the service: cleaned once it is. */
    private static List<String> waiting;
    private static boolean waitingCaches;
    /** This clean also presses Clear cache (Boost's Also clear app caches box). */
    private boolean clearCaches;

    private final Handler main = new Handler(Looper.getMainLooper());
    private List<String> queue;
    private int index, phase, stopped, cleared;
    private long deadline, memBefore;
    private String forceStop, clearCache;
    private final Set<String> okLabels = new LinkedHashSet<>();
    private View cover;
    private TextView coverStatus, coverTitle;
    private LinearLayout coverBar;

    /** Cleans these apps: now if the helper is bound, otherwise it is switched on and cleans once Android binds it. */
    static boolean clean(Context c, List<String> packages, boolean caches) {
        if (connected != null) {
            connected.start(packages, caches);
            return true;
        }
        waiting = new ArrayList<>(packages);
        waitingCaches = caches;
        try {
            enable(c);
            return true;
        } catch (SecurityException e) {   // WRITE_SECURE_SETTINGS not granted
            waiting = null;
            return false;
        }
    }

    /** Adds this service to the enabled accessibility services, keeping the others (HomeKeyService). */
    private static void enable(Context c) {
        ContentResolver cr = c.getContentResolver();
        ComponentName me = new ComponentName(c, BoostService.class);
        String list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) list = "";
        boolean on = false;
        for (String s : list.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (me.equals(cn)) on = true;
        }
        if (!on) {
            String add = me.flattenToShortString();
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    list.isEmpty() ? add : list + ":" + add);
        }
        Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = this;
        List<String> pending = waiting;
        waiting = null;
        boolean caches = waitingCaches;
        if (pending != null) main.postDelayed(() -> start(pending, caches), 300);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Polled in tick(): the page is read when needed, not on every event.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (connected == this) connected = null;
        hideCover();
        super.onDestroy();
    }

    private void start(List<String> packages, boolean caches) {
        if (queue != null || packages.isEmpty()) return;   // one clean at a time
        clearCaches = caches;
        queue = new ArrayList<>(packages);
        index = 0;
        stopped = 0;
        cleared = 0;
        memBefore = availMem();
        loadLabels();
        showCover();
        main.post(this::openNext);
    }

    /** TV Settings' own words for the items, in the TV's language, with English and Android's OK as fallbacks. */
    private void loadLabels() {
        forceStop = settingsString("Force stop", "device_apps_app_management_force_stop", "force_stop");
        clearCache = settingsString("Clear cache", "device_apps_app_management_clear_cache", "clear_cache");
        okLabels.clear();
        okLabels.add(getString(android.R.string.ok));
        okLabels.add(settingsString("OK", "settings_ok", "lb_guidedaction_ok_title", "okay"));
        okLabels.add("OK");
    }

    private String settingsString(String fallback, String... names) {
        try {
            Resources r = getPackageManager().getResourcesForApplication(SETTINGS_PKG);
            for (String name : names) {
                int id = r.getIdentifier(name, "string", SETTINGS_PKG);
                if (id != 0) return r.getString(id);
            }
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
        }
        return fallback;
    }

    private void openNext() {
        if (queue == null) return;
        if (index >= queue.size()) {
            finishClean();
            return;
        }
        String pkg = queue.get(index);
        // Already stopped (no process): nothing to close, and its page is skipped unless caches are wanted.
        if (!clearCaches && isStopped(pkg)) {
            next();
            return;
        }
        showProgress(appLabel(pkg), index + 1, queue.size());
        Intent page = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(page);
        } catch (RuntimeException e) {
            next();
            return;
        }
        phase = FIND_STOP;
        deadline = SystemClock.uptimeMillis() + PAGE_TIMEOUT_MS;
        main.postDelayed(this::tick, 450);
    }

    /** One look at the page; the step's work, or the next look in STEP_MS. */
    private void tick() {
        if (queue == null) return;
        AccessibilityNodeInfo root = settingsRoot();
        long now = SystemClock.uptimeMillis();
        switch (phase) {
            case FIND_STOP: {
                AccessibilityNodeInfo stop = exact(root, forceStop);
                // An app already stopped keeps an enabled Force stop on this Sony (Kodi): pressing it does nothing.
                if (stop != null && stop.isEnabled() && !isStopped(queue.get(index)) && click(stop)) {
                    go(CONFIRM_STOP, DIALOG_TIMEOUT_MS);
                } else if (exact(root, clearCache) != null || now > deadline) {
                    // The page is up with no Force stop (already stopped), or it never came: the cache if asked.
                    if (!clearCaches) {
                        next();
                        return;
                    }
                    go(FIND_CACHE, DIALOG_TIMEOUT_MS);
                }
                break;
            }
            case CONFIRM_STOP: {
                AccessibilityNodeInfo ok = anyExact(root, okLabels);
                if (ok != null && click(ok)) {
                    go(AFTER_STOP, DIALOG_TIMEOUT_MS);
                } else if (now > deadline) {
                    if (!clearCaches) {
                        next();
                        return;
                    }
                    go(FIND_CACHE, DIALOG_TIMEOUT_MS);
                }
                break;
            }
            case AFTER_STOP:
                // Back on the app page once the dialog has gone; counted only if Android now has it stopped. Then the
                // cache if asked, else the next app.
                if (exact(root, clearCache) != null || now > deadline) {
                    if (isStopped(queue.get(index))) stopped++;
                    if (!clearCaches) {
                        next();
                        return;
                    }
                    go(FIND_CACHE, DIALOG_TIMEOUT_MS);
                }
                break;
            case FIND_CACHE: {
                AccessibilityNodeInfo cache = exact(root, clearCache);
                if (cache != null && cache.isEnabled() && click(cache)) {
                    go(CONFIRM_CACHE, DIALOG_TIMEOUT_MS);
                } else if (now > deadline || (cache != null && !cache.isEnabled())) {
                    next();
                    return;
                }
                break;
            }
            case CONFIRM_CACHE: {
                AccessibilityNodeInfo ok = anyExact(root, okLabels);
                if (ok != null && click(ok)) {
                    cleared++;
                    go(DONE_APP, 500);
                } else if (now > deadline) {
                    next();
                    return;
                }
                break;
            }
            default:
                if (now > deadline) {
                    next();
                    return;
                }
        }
        main.postDelayed(this::tick, STEP_MS);
    }

    private void go(int nextPhase, long timeout) {
        phase = nextPhase;
        deadline = SystemClock.uptimeMillis() + timeout;
    }

    private void next() {
        index++;
        main.postDelayed(this::openNext, 120);
    }

    private void finishClean() {
        queue = null;
        closeSettingsPage(0);
    }

    /**
     * Leaves no TV Settings page behind. Home alone parked the last app page in TV Settings' task, and it came back
     * the next time Settings was opened and closed with Back. Back closes the page (and that task) first, a few times
     * at most, then Home, then the result.
     */
    private void closeSettingsPage(int tries) {
        if (tries < 3 && settingsRoot() != null) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            main.postDelayed(() -> closeSettingsPage(tries + 1), 350);
            return;
        }
        performGlobalAction(GLOBAL_ACTION_HOME);
        main.postDelayed(() -> {
            long freed = Math.max(0, availMem() - memBefore);
            if (coverTitle != null) coverTitle.setText(R.string.boost_done_title);
            if (coverStatus != null) {
                boolean some = freed >= 10L * 1024 * 1024;
                String mb = Formatter.formatShortFileSize(this, freed);
                coverStatus.setText(clearCaches
                        ? (some ? getString(R.string.boost_done_cache, stopped, cleared, mb)
                                : getString(R.string.boost_done_cache_count, stopped, cleared))
                        : (some ? getString(R.string.boost_done, stopped, mb) : getString(R.string.boost_done_count, stopped)));
            }
            setBar(1f);
            main.postDelayed(this::hideCover, 2500);
        }, 1200);
    }

    /** TV Settings' window: the active one, or the first window it owns. */
    private AccessibilityNodeInfo settingsRoot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && SETTINGS_PKG.contentEquals(root.getPackageName())) return root;
        for (AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo r = w.getRoot();
            if (r != null && SETTINGS_PKG.contentEquals(r.getPackageName())) return r;
        }
        return null;
    }

    /** The node whose whole text is this label (case aside); never a longer text that merely contains it. */
    private static AccessibilityNodeInfo exact(AccessibilityNodeInfo root, String label) {
        if (root == null || label == null) return null;
        for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(label)) {
            CharSequence t = n.getText();
            if (t != null && t.toString().trim().equalsIgnoreCase(label.trim())) return n;
        }
        return null;
    }

    private static AccessibilityNodeInfo anyExact(AccessibilityNodeInfo root, Set<String> labels) {
        for (String label : labels) {
            AccessibilityNodeInfo n = exact(root, label);
            if (n != null) return n;
        }
        return null;
    }

    /** Clicks the node or the nearest clickable row around it. */
    private static boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node;
        for (int i = 0; n != null && i < 6; i++, n = n.getParent()) {
            if (n.isClickable()) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }

    /** Android's stopped flag for the app: set by a force stop, cleared when anything starts it again. */
    private boolean isStopped(String pkg) {
        try {
            return (getPackageManager().getApplicationInfo(pkg, 0).flags
                    & android.content.pm.ApplicationInfo.FLAG_STOPPED) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private long availMem() {
        ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
        getSystemService(ActivityManager.class).getMemoryInfo(m);
        return m.availMem;
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    // ---- the cover -----------------------------------------------------------------------------------------------

    /**
     * A full-screen, fully opaque cover over TV Settings while it is driven, so none of the pages flicking by show:
     * deep navy to black, the rocket, what is being closed and a progress bar in the middle.
     */
    private void showCover() {
        if (cover != null) return;
        float dp = getResources().getDisplayMetrics().density;
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF101826, 0xFF05070B}));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(24 * dp);
        card.setPadding(pad, pad, pad, pad);
        ImageView rocket = new ImageView(this);
        rocket.setImageResource(R.drawable.ic_boost);
        int r = Math.round(56 * dp);
        card.addView(rocket, new LinearLayout.LayoutParams(r, r));
        coverTitle = text(26, R.string.font_light, 0xFFFFFFFF);
        coverTitle.setText(R.string.boost_cleaning);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Math.round(12 * dp);
        card.addView(coverTitle, tlp);
        coverStatus = text(15, R.string.font_regular, 0xB3FFFFFF);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Math.round(6 * dp);
        card.addView(coverStatus, slp);
        coverBar = new LinearLayout(this);
        coverBar.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x33FFFFFF);
        track.setCornerRadius(3 * dp);
        coverBar.setBackground(track);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Math.round(360 * dp), Math.round(6 * dp));
        blp.topMargin = Math.round(22 * dp);
        card.addView(coverBar, blp);
        setBar(0f);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        frame.addView(card, clp);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        try {
            getSystemService(WindowManager.class).addView(frame, lp);
            cover = frame;
        } catch (RuntimeException ignored) {
            // no cover: the clean still runs
        }
    }

    private void showProgress(String app, int n, int total) {
        if (coverStatus != null) {
            coverStatus.setText(getString(R.string.boost_closing, app, n, total));
        }
        setBar((n - 1) / (float) Math.max(1, total));
    }

    private void setBar(float done) {
        if (coverBar == null) return;
        coverBar.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;
        View fill = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xFF8FB8FF);
        shape.setCornerRadius(3 * dp);
        fill.setBackground(shape);
        coverBar.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, done));
        coverBar.addView(new View(this), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT,
                1f - done));
    }

    private void hideCover() {
        if (cover == null) return;
        try {
            getSystemService(WindowManager.class).removeView(cover);
        } catch (RuntimeException ignored) {
        }
        cover = null;
        coverStatus = null;
        coverTitle = null;
        coverBar = null;
    }

    private TextView text(int sp, int font, int colour) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(UiFont.get(this, font));
        t.setTextColor(colour);
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
