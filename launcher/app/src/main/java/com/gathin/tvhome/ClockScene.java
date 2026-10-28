package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextClock;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock and date on black over a slow ambient
 * glow. Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final long MOVE_MS = 60_000L;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final ImageView ambient;
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    /** clock_wallpaper or clock_glow false: something else (a picture, or plain black) replaced the coloured glow. */
    private boolean wallpaperShown;
    /** clock_wallpaper: the picture start() decodes, its darkening, and the resources to draw it with. */
    private File wallpaperFile;
    private int wallpaperShade;
    private Resources wallpaperRes;
    /** One number per decode request: a result whose number is no longer the current one is dropped. Main thread only. */
    private int wallpaperGeneration;
    /** clock_wallpaper with a collection: the pictures, how long each lasts, and how the next one is picked. */
    private List<File> rotation;
    private long rotationMs;
    private long shownMs;
    private String order;
    private Map<String, Set<String>> moods;
    private String latitude, longitude;

    /**
     * screensaver_home: the screensaver is Home without its tiles and buttons. Home's clock style at Home's place,
     * still; the wallpaper as Home shows it (its own dim, its colour depth), changing with a crossfade.
     */
    private boolean homeMode;
    private final View root;
    /** screensaver_home: the next picture fades in here, over ambient. */
    private final ImageView ambientNext;
    private Bitmap.Config wallpaperConfig = Bitmap.Config.RGB_565;
    /** WallpaperPlayer's rotation state: screensaver_home writes each change back, so Home wakes on the same picture. */
    private SharedPreferences rotationPrefs;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!homeMode) {
                moveClock();
                shiftGlow();
            }
            advanceWallpaper();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        this.root = root;
        ambientNext = root.findViewById(R.id.dreamAmbientNext);
        content = root.findViewById(R.id.dreamContent);
        ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
    }

    /** screensaver_home is on: ClockDream keeps the screen at full brightness so it matches Home. */
    boolean homeMode() {
        return homeMode;
    }

    void start() {
        if (homeMode) {
            // Exactly where Home's clock is (its header's padding, the greeting hidden) and visible at once: as the
            // screensaver fades in over Home, only the tiles and the top buttons appear to go.
            float dp = content.getResources().getDisplayMetrics().density;
            content.animate().cancel();
            content.setAlpha(1f);
            content.setX(56 * dp);
            content.setY(26 * dp);
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, MOVE_MS);
            // Decoded right here, as Home's first picture is: fading in over Home with a black frame and the picture
            // popping in later would look like the TV switching off.
            if (wallpaperFile != null && !wallpaperShown) showWallpaperNow();
            if (weather != null) weather.start();
            return;
        }
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
        if (wallpaperFile != null && !wallpaperShown) loadWallpaper();
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);   // the tick and any glow steps still queued
        content.animate().cancel();
        ambientNext.animate().cancel();
        if (weather != null) weather.stop();
        wallpaperGeneration++;   // a decode still running must not attach its picture to a stopped scene
    }

    /**
     * clock_wallpaper: put the picture Home is showing behind the clock, darkened by clock_wallpaper_dim, in
     * place of the glow, and carry a collection's rotation on while the screensaver is up. Decoded off the main
     * thread at screen size in RGB_565 (4 MB at 1080p), like Home's own; until then, or if it cannot be read,
     * the glow stays.
     * Call before start(), which starts the decode (again after a stop() that dropped an unfinished one).
     */
    void useHomeWallpaper(Context ctx) {
        Config cfg;
        try {
            cfg = Config.load(ctx);
        } catch (RuntimeException e) {
            return;
        }
        homeMode = cfg.screensaverHome;
        if (homeMode) setUpHome(ctx, cfg);
        if (!cfg.clockWallpaper && !homeMode) {
            // clock_glow false: plain black behind the clock (OLED); the clock itself still moves every minute.
            if (!cfg.clockGlow) {
                wallpaperShown = true;   // also stops the glow's colour cycle
                ambient.setBackground(null);
            }
            return;
        }
        // What Home plays, and Config puts the single wallpaper in it when a theme has no collection.
        // Reading cfg.wallpaper first held the screensaver on wallpaper.jpg while Home rotated.
        if (cfg.wallpapers.isEmpty()) {
            wallpaperFile = null;
        } else {
            // Start on the picture Home is showing. WallpaperPlayer records it under the same
            // preferences and the screensaver runs in the same process, so this is just a read.
            rotation = new ArrayList<>(cfg.wallpapers);
            String shown = ctx.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE)
                    .getString("image", "");
            File current = WallpaperRotation.find(rotation, shown);
            wallpaperFile = current != null ? current : rotation.get(0);
            // Home only rotates while it is visible, so the screensaver carries the rotation on
            // itself; without this it holds one picture for as long as it is up.
            rotationMs = cfg.wallpaperRotationMinutes * 60_000L;
            if (homeMode) {
                // Carry on Home's timing, not a fresh interval: the picture has already been up since "changed".
                rotationPrefs = ctx.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
                long changed = rotationPrefs.getLong("changed", 0);
                long up = System.currentTimeMillis() - changed;
                if (current != null && changed > 0 && up > 0) shownMs = Math.min(up, rotationMs);
            }
            order = cfg.wallpaperOrder;
            moods = cfg.wallpaperMoods;
            latitude = moods.isEmpty() ? "" : ctx.getString(R.string.latitude);
            longitude = moods.isEmpty() ? "" : ctx.getString(R.string.longitude);
        }
        // screensaver_home: Home's own dim (wallpaper_dim) and colour depth, so the picture does not change as the
        // screensaver takes over; otherwise the screensaver's darker clock_wallpaper_dim.
        int dim = homeMode ? cfg.wallpaperDim : cfg.clockWallpaperDim;
        wallpaperShade = (dim * 255 / 100) << 24;
        if (homeMode && cfg.wallpaperTrueColor) wallpaperConfig = Bitmap.Config.ARGB_8888;
        wallpaperRes = ctx.getResources();
    }

    /** screensaver_home: Home's clock style (HomeClockStyle) and margins, no glow, Home's background colour behind. */
    private void setUpHome(Context ctx, Config cfg) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        TextClock clock = root.findViewById(R.id.dreamClock);
        TextClock ampm = root.findViewById(R.id.dreamAmpm);
        TextClock date = root.findViewById(R.id.dreamDate);
        HomeClockStyle.apply(ctx, clock, ampm, date);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) date.getLayoutParams();
        lp.topMargin = Math.round(HomeClockStyle.DATE_GAP_DP * dp);
        lp.setMarginStart(0);
        date.setLayoutParams(lp);
        ViewGroup.MarginLayoutParams alp = (ViewGroup.MarginLayoutParams) ampm.getLayoutParams();
        alp.setMarginStart(Math.round(6 * dp));
        ampm.setLayoutParams(alp);
        ambient.setBackground(new ColorDrawable(cfg.backgroundColor));
        if (cfg.weatherWidget) {
            // The same weather as Home's header, under the date, so nothing moves as the screensaver takes over.
            weather = new WeatherWidget(ctx);
            android.widget.LinearLayout.LayoutParams wlp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wlp.topMargin = Math.round(14 * dp);
            ((ViewGroup) content).addView(weather.view(), wlp);
        }
    }

    /** screensaver_home with weather_widget: Home's weather under the clock; null otherwise. */
    private WeatherWidget weather;

    /** screensaver_home: the first picture, decoded on this thread so the screensaver's first frame has it. */
    private void showWallpaperNow() {
        DisplayMetrics dm = wallpaperRes.getDisplayMetrics();
        Bitmap bmp = decode(wallpaperFile, dm.widthPixels, dm.heightPixels, wallpaperConfig);
        if (bmp == null) {
            loadWallpaper();   // unreadable here too: the background colour stays, as on Home
            return;
        }
        wallpaperShown = true;
        ambient.setBackground(null);
        ambient.setImageDrawable(picture(wallpaperRes, bmp, wallpaperShade));
    }

    private static Drawable picture(Resources res, Bitmap bmp, int shade) {
        Drawable bitmap = new BitmapDrawable(res, bmp);
        return shade == 0 ? bitmap : new LayerDrawable(new Drawable[]{bitmap, new ColorDrawable(shade)});
    }

    /** screensaver_home: the next picture fades in over the current one (1.5 s, as on Home), then replaces it. */
    private void crossfade(Drawable next) {
        ambientNext.animate().cancel();
        ambientNext.setImageDrawable(next);
        ambientNext.setAlpha(0f);
        ambientNext.animate().alpha(1f).setDuration(1500).withLayer().withEndAction(() -> {
            ambient.setImageDrawable(next);
            ambientNext.setAlpha(0f);
            ambientNext.setImageDrawable(null);
        }).start();
    }

    private void loadWallpaper() {
        File file = wallpaperFile;
        int shade = wallpaperShade;
        Resources res = wallpaperRes;
        DisplayMetrics dm = res.getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        int generation = ++wallpaperGeneration;
        Bitmap.Config config = wallpaperConfig;
        new Thread(() -> {
            Bitmap bmp = decode(file, w, h, config);
            if (bmp == null) return;
            handler.post(() -> {
                if (generation != wallpaperGeneration || wallpaperShown) {
                    bmp.recycle();   // stopped meanwhile (or already shown): this picture was never attached
                    return;
                }
                wallpaperShown = true;
                // As the image, not the background: a background is stretched to the view, so a picture of
                // another shape than the screen would be distorted. centerCrop scales and crops it like Home.
                Drawable next = picture(res, bmp, shade);
                if (homeMode && ambient.getDrawable() != null) {
                    crossfade(next);
                } else {
                    ambient.setBackground(null);
                    ambient.setImageDrawable(next);
                }
            });
        }, "clock-wallpaper").start();
    }

    /** WallpaperPlayer's decode: sampled and scaled down to cover the screen, never bigger than 4K. */
    private static Bitmap decode(File file, int screenW, int screenH, Bitmap.Config config) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, screenW, screenH);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, screenW, screenH);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = config;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    /** Step to the next picture once its turn is up; the tick already runs every minute. */
    private void advanceWallpaper() {
        if (rotation == null || rotationMs <= 0 || rotation.size() < 2) return;
        shownMs += MOVE_MS;
        if (shownMs < rotationMs) return;
        shownMs = 0;
        // The same pick as Home's, so a shuffled collection stays shuffled and wallpaper_moods still
        // decides what may show at this hour.
        File next = WallpaperRotation.pick(playable(), wallpaperFile.getName(), order, random);
        if (next.equals(wallpaperFile)) return;   // a mood group of one
        wallpaperFile = next;
        if (rotationPrefs != null) {
            // Home reads the same record when it comes back, so it wakes on this picture instead of an older one.
            rotationPrefs.edit().putString("image", next.getName())
                    .putLong("changed", System.currentTimeMillis()).apply();
        }
        wallpaperShown = false;
        loadWallpaper();
    }

    /** WallpaperPlayer's filter: the pictures wallpaper_moods allows at this hour, or all of them. */
    private List<File> playable() {
        if (moods.isEmpty()) return rotation;
        LocalDateTime now = LocalDateTime.now();
        int[] sun = WallpaperMood.sun(now.toLocalDate(), latitude, longitude, ZoneId.systemDefault());
        return WallpaperMood.select(rotation, moods,
                WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withLayer().withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).withLayer().start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        if (wallpaperShown) return;
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        int to = PALETTE[colorIndex];
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> setGlow((int) mix.evaluate(f, from, to)), i * GLOW_STEP_MS);
        }
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
