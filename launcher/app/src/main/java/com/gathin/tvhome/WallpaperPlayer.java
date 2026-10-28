package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    private final ImageView image;
    private final int backgroundColor;
    private final List<File> files;
    private final long interval;
    private final String order;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    /** config wallpaper_true_color: ARGB_8888 instead of RGB_565. */
    private final Bitmap.Config bitmapConfig;
    private final int height;
    /** wallpaper_dim as an ARGB black, painted into each picture once instead of a full-screen shade view. */
    private final int dimColor;
    /** The picture on screen, released once the next one has been drawn. */
    private Bitmap current;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";
    /** wallpaper_captions: the shown picture's name from wallpapers/captions.txt; null when off or there are none. */
    private final TextView caption;
    private final Map<String, String> names;
    /** wallpaper_moods: the collection's groups by time of day; empty rotates through all of it. */
    private final Map<String, Set<String>> moods;
    /** config.xml latitude and longitude for the sun's times, and today's {sunrise, sunset} once worked out. */
    private final String latitude, longitude;
    private LocalDate sunDay;
    private int[] sun;

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.root = root;
        backgroundColor = config.backgroundColor;
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        bitmapConfig = config.wallpaperTrueColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        // The dim is painted into the picture (decode), so no full-screen shade layer is drawn on every frame.
        dimColor = config.wallpaperDim > 0 ? (config.wallpaperDim * 255 / 100) << 24 : 0;
        order = config.wallpaperOrder;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        moods = config.wallpaperMoods;
        latitude = moods.isEmpty() ? "" : context.getString(R.string.latitude);
        longitude = moods.isEmpty() ? "" : context.getString(R.string.longitude);
        // Playback state belongs to one collection. Changing the collection starts from its first
        // picture instead of resuming a filename that belonged to the previous one.
        String collection = collectionKey(files);
        if (!collection.equals(preferences.getString("collection", ""))) {
            preferences.edit().putString("collection", collection)
                    .remove("image").remove("changed").apply();
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        names = config.wallpaperCaptions && config.wallpaperEnabled && !files.isEmpty()
                ? WallpaperCaptions.read(files.get(0).getParentFile()) : Collections.emptyMap();
        caption = names.isEmpty() ? null : makeCaption(context, root);
        tintWanted = config.wallpaperTintTiles && config.clearGlass;
        frostWanted = config.frostTiles && config.clearGlass;
        if (config.wallpaperEnabled && !files.isEmpty()) {
            root.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showFirst();
        }
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        List<File> playable = playable();
        File first = WallpaperRotation.find(playable, previous);
        if (first == null) first = WallpaperRotation.pick(playable, previous, order, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                show(bitmap);
                if (tintWanted) tintFor(glassFor(first));   // kept for setOnTint, which comes after this
                if (frostWanted) showFrost(frostFor(first));   // and for setOnFrost
                // The picture covers the whole screen: the root's plain colour under it is overdraw.
                root.setBackground(null);
                displayed = first.getName();
                showCaption(false);
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            playable = playable();
            first = WallpaperRotation.pick(playable, previous, order, random);
        }
    }

    void start() {
        active = true;
        update();
    }

    void stop() {
        active = false;
    }

    /** Drops the wallpaper texture when Home is hidden and Android asks the process to free memory. */
    void releaseForMemoryPressure() {
        if (current == null) return;
        endFade();
        image.setImageDrawable(null);
        // The renderer may still have a queued frame that references this hardware bitmap. Drop our references and
        // let Android reclaim its native buffer after rendering releases it instead of recycling it mid-frame.
        current = null;
        displayed = "";
        root.setBackgroundColor(backgroundColor);
    }

    /** The pictures the rotation picks from now: wallpaper_moods' group for the time of day, or the whole collection. */
    private List<File> playable() {
        if (moods.isEmpty()) return withoutHidden(files);
        LocalDateTime now = LocalDateTime.now();
        if (!now.toLocalDate().equals(sunDay)) {
            sunDay = now.toLocalDate();
            sun = WallpaperMood.sun(sunDay, latitude, longitude, ZoneId.systemDefault());
        }
        return withoutHidden(WallpaperMood.select(files, moods, WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1])));
    }

    void close() {
        active = false;
        closed = true;
        decoder.shutdownNow();
    }

    /** wallpaper_tint_tiles: told the tile glass for each picture as it is shown; the latest one, for a late listener. */
    private java.util.function.Consumer<int[]> onTint;
    private int[] lastTint;
    /** wallpaper_tint_tiles with clear_glass: the first picture is sampled too (before setOnTint is called). */
    private boolean tintWanted;

    /** wallpaper_tint_tiles: receives the glass colours of the picture on screen now, and of each one after it. */
    void setOnTint(java.util.function.Consumer<int[]> listener) {
        onTint = listener;
        if (listener != null && lastTint != null) listener.accept(lastTint);
    }

    /**
     * The tile glass for a picture: its colour, read from a 1/32-size decode (the picture on screen is a GPU bitmap,
     * which cannot be read), the lower half counted twice as the tiles sit there. The same smoky gradient as the cold
     * glass, nearly opaque dark at the top to the picture's hue at the bottom, with the saturation kept moderate so a
     * vivid picture still gives glass, not paint. Null when the file cannot be read.
     */
    static int[] glassFor(File file) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 32;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap small = BitmapFactory.decodeFile(file.getPath(), o);
        if (small == null) return null;
        long r = 0, g = 0, b = 0, n = 0;
        int w = small.getWidth(), h = small.getHeight();
        for (int y = 0; y < h; y++) {
            int weight = y >= h / 2 ? 2 : 1;
            for (int x = 0; x < w; x++) {
                int p = small.getPixel(x, y);
                r += (long) android.graphics.Color.red(p) * weight;
                g += (long) android.graphics.Color.green(p) * weight;
                b += (long) android.graphics.Color.blue(p) * weight;
                n += weight;
            }
        }
        small.recycle();
        if (n == 0) return null;
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(android.graphics.Color.rgb((int) (r / n), (int) (g / n), (int) (b / n)), hsv);
        float hue = hsv[0], sat = Math.max(0.15f, Math.min(0.55f, hsv[1] * 1.4f));
        return new int[]{
                android.graphics.Color.HSVToColor(0xDE, new float[]{hue, sat * 0.8f, 0.10f}),
                android.graphics.Color.HSVToColor(0xD6, new float[]{hue, sat, 0.24f}),
                android.graphics.Color.HSVToColor(0xD1, new float[]{hue, sat, 0.48f})};
    }

    /** frost_tiles: told the frosted copy of each picture as it is shown; the latest one, for a late listener. */
    private java.util.function.Consumer<Bitmap> onFrost;
    private Bitmap lastFrost;
    /** frost_tiles: the first picture's frost is made too (before setOnFrost is called). */
    private boolean frostWanted;

    void setOnFrost(java.util.function.Consumer<Bitmap> listener) {
        onFrost = listener;
        if (listener != null && lastFrost != null) listener.accept(lastFrost);
    }

    /**
     * frost_tiles: the picture at 1/16 (about 120 px wide), softened by two box-blur passes. Drawn stretched under
     * each tile (FrostDrawable) it reads as frosted glass; a few thousand pixels, made once per picture on the loader
     * thread. Null when the file cannot be read.
     */
    static Bitmap frostFor(File file) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 16;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap small = BitmapFactory.decodeFile(file.getPath(), o);
        if (small == null) return null;
        int w = small.getWidth(), h = small.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            boxBlur(px, tmp, w, h, 2, true);
            boxBlur(tmp, px, w, h, 2, false);
        }
        FrostTone.apply(px);   // lighter and more colourful than the picture, as the core sample's glass
        Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        small.recycle();
        return out;
    }

    /** One box-blur pass of this radius along rows (or columns), from in to out. */
    private static void boxBlur(int[] in, int[] out, int w, int h, int radius, boolean rows) {
        int lines = rows ? h : w, length = rows ? w : h;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < length; i++) {
                int r = 0, g = 0, b = 0, n = 0;
                for (int k = -radius; k <= radius; k++) {
                    int j = Math.max(0, Math.min(length - 1, i + k));
                    int p = rows ? in[line * w + j] : in[j * w + line];
                    r += (p >> 16) & 0xFF;
                    g += (p >> 8) & 0xFF;
                    b += p & 0xFF;
                    n++;
                }
                int v = 0xFF000000 | (r / n) << 16 | (g / n) << 8 | (b / n);
                if (rows) out[line * w + i] = v;
                else out[i * w + line] = v;
            }
        }
    }

    private void showFrost(Bitmap frost) {
        if (frost == null) return;
        lastFrost = frost;
        if (onFrost != null) onFrost.accept(frost);
    }

    private void tintFor(int[] tint) {
        if (tint == null) return;
        lastTint = tint;
        if (onTint != null) onTint.accept(tint);
    }

    /** The rotation preferences (shared with ClockScene's screensaver_home), and in them the skip request. */
    static final String PREFS = "wallpaper_rotation";
    /** In the rotation preferences: show the next picture at the next update (QuickSettingsActivity's button). */
    static final String SKIP = "skip";

    /** Asks Home for the next wallpaper; it changes, with the crossfade, as soon as Home is in front again. */
    /** In the rotation preferences: pictures hidden from the rotation with the drawer's Hide button, by file name. */
    static final String HIDDEN = "hidden";

    /** The hidden pictures left out; all of them kept if that would leave none, so Home never goes blank. */
    private List<File> withoutHidden(List<File> group) {
        Set<String> hidden = preferences.getStringSet(HIDDEN, Collections.emptySet());
        if (hidden.isEmpty()) return group;
        List<File> kept = new ArrayList<>(group.size());
        for (File f : group) if (!hidden.contains(f.getName())) kept.add(f);
        return kept.isEmpty() ? group : kept;
    }

    /** Hides the picture Home shows now from the rotation and moves Home on to the next one; false with none shown. */
    static boolean hideCurrent(android.content.Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        String current = p.getString("image", "");
        if (current.isEmpty()) return false;
        Set<String> hidden = new java.util.HashSet<>(p.getStringSet(HIDDEN, Collections.emptySet()));
        hidden.add(current);
        p.edit().putStringSet(HIDDEN, hidden).putBoolean(SKIP, true).apply();
        return true;
    }

    /** Brings every hidden picture back into the rotation; how many there were. */
    static int showHidden(android.content.Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        int count = p.getStringSet(HIDDEN, Collections.emptySet()).size();
        p.edit().remove(HIDDEN).apply();
        return count;
    }

    static void skipToNext(android.content.Context c) {
        c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(SKIP, true).apply();
    }

    void update() {
        if (!active || closed || loading || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        // A picture outside the time of day's group (the group changed) is replaced at once, not after the interval.
        List<File> playable = playable();
        File selected = WallpaperRotation.find(playable, previous);
        long changed = preferences.getLong("changed", 0);
        // The settings panel's Next wallpaper (SKIP): the next picture now, whatever the interval, even with none.
        boolean skip = preferences.getBoolean(SKIP, false);
        if (skip) preferences.edit().remove(SKIP).apply();
        if (selected == null || skip || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(playable, previous, order, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        boolean tint = onTint != null, frosted = onFrost != null;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            int[] glass = tint && bitmap != null ? glassFor(next) : null;
            Bitmap frost = frosted && bitmap != null ? frostFor(next) : null;
            root.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                show(bitmap);
                tintFor(glass);   // the tiles turn to the new picture's colour along with its crossfade
                showFrost(frost);   // and frost_tiles tiles to its frosted copy
                displayed = next.getName();
                showCaption(true);
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    /**
     * Small light text under the top-right buttons, above everything else and never focusable. It sits where the
     * top bar ends in every layout, so it does not cover the clock, the stats line or the tiles.
     */
    private static TextView makeCaption(Context context, FrameLayout root) {
        float dp = context.getResources().getDisplayMetrics().density;
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(0xD9FFFFFF);
        text.setShadowLayer(6 * dp, 0, dp, 0xCC000000);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth(Math.round(520 * dp));
        text.setFocusable(false);
        text.setAlpha(0f);
        try {
            text.setTypeface(UiFont.get(context, R.string.font_regular));
        } catch (Exception ignored) {
            // the system font is fine
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        params.topMargin = Math.round(110 * dp);
        params.setMarginEnd(Math.round(62 * dp));
        root.addView(text, params);
        return text;
    }

    /** The displayed picture's name, faded across a change; a picture without a name hides the caption. */
    private void showCaption(boolean fade) {
        if (caption == null) return;
        String name = names.get(displayed);
        caption.animate().cancel();
        if (name == null) {
            caption.setAlpha(0f);
            return;
        }
        caption.setContentDescription(name);
        if (!fade || caption.getAlpha() == 0f) {
            caption.setText(name);
            caption.setAlpha(1f);
            return;
        }
        caption.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            caption.setText(name);
            caption.animate().alpha(1f).setDuration(350).start();
        }).start();
    }

    /** Names plus sizes: enough to notice a different collection without hashing every image. */
    private static String collectionKey(List<File> images) {
        StringBuilder key = new StringBuilder();
        for (File file : images) key.append(file.getName()).append(':').append(file.length()).append(';');
        return key.toString();
    }

    /** The incoming picture while a crossfade runs, drawn just above {@link #image}; null otherwise. */
    private ImageView fading;
    /** The picture that crossfade is replacing, released once it is no longer drawn. */
    private Bitmap fadingFrom;

    /** Puts a picture on screen with a crossfade and releases the previous one after the transition. */
    private void show(Bitmap bitmap) {
        endFade();   // before current changes: a fade cut short settles on the picture it was bringing in
        Bitmap old = current;
        current = bitmap;
        // The wallpaper covers the whole root; avoid drawing the fallback colour under it.
        root.setBackground(null);
        if (old == null || old == bitmap || image.getParent() != root) {
            image.setImageBitmap(bitmap);
            return;
        }
        // Crossfade: the new picture goes directly above the current one (below the tiles and the clock) and
        // fades in over it. Placed under it, as before, the opaque current picture hid the whole fade and the
        // change still landed as a cut when the animation ended.
        ImageView next = new ImageView(root.getContext());
        next.setScaleType(ImageView.ScaleType.CENTER_CROP);
        next.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        next.setImageBitmap(bitmap);
        next.setAlpha(0f);
        root.addView(next, root.indexOfChild(image) + 1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fading = next;
        fadingFrom = old;
        // Cancelling (endFade) skips this end action, so only a fade that ran to the end gets here.
        next.animate().alpha(1f).setDuration(FADE_MS).withLayer().withEndAction(this::endFade).start();
    }

    private static final long FADE_MS = 1500;

    /** Settles a crossfade, finished or cut short: the incoming picture becomes the wallpaper and its overlay goes. */
    private void endFade() {
        ImageView next = fading;
        if (next == null) return;
        Bitmap from = fadingFrom;
        fading = null;
        fadingFrom = null;
        next.animate().cancel();
        if (current != null) image.setImageBitmap(current);
        root.removeView(next);
        // Recycled a little later: the renderer may still hold a frame that draws the old texture.
        if (from != null && from != current) image.postDelayed(from::recycle, 500);
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = bitmapConfig;
            options.inMutable = dimColor != 0;
            Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
            if (decoded == null) return null;
            if (dimColor != 0) new Canvas(decoded).drawColor(dimColor);
            // One copy in GPU memory instead of a software bitmap plus its texture; the decode stays off the UI
            // thread for rotations, and Home's return no longer uploads 4-8 MB to the GPU.
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
