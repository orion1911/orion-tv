package com.gathin.tvhome;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * weather_widget: the weather under the clock, as on an ambient home screen: a symbol and the temperature, the place
 * in small capitals over the sky and today's high and low, and a pill with what to wear and how it turns ("Light
 * jacket · cools to 24° by 22:00"). The launcher has no network permission: the Weather add-on
 * (its own app, its NowProvider) reads Open-Meteo and this asks it, on start, every 10 minutes while shown, and
 * when it announces a new reading. Hidden while there is no reading (add-on missing, or no city chosen in it yet).
 * Home's header and the screensaver (screensaver_home) each make one, so they match.
 */
final class WeatherWidget {
    static final Uri NOW = Uri.parse("content://io.github.sumyabazar1.weather.now/now");
    private static final long EVERY_MS = 10 * 60_000L;
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();

    private final Context ctx;
    private final LinearLayout root;
    private final ImageView symbol;
    private final TextView temp, place, sky, hint;
    private final View pill;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            main.postDelayed(this, EVERY_MS);
        }
    };
    private final ContentObserver observer = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            refresh();
        }
    };
    private boolean started;

    WeatherWidget(Context ctx) {
        this.ctx = ctx;
        float dp = ctx.getResources().getDisplayMetrics().density;
        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setVisibility(View.GONE);
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        symbol = new ImageView(ctx);
        int s = Math.round(24 * dp);
        line.addView(symbol, new LinearLayout.LayoutParams(s, s));
        temp = text(30, R.string.font_light, 0xFFFFFFFF);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(Math.round(8 * dp));
        line.addView(temp, tlp);
        LinearLayout words = new LinearLayout(ctx);
        words.setOrientation(LinearLayout.VERTICAL);
        place = text(10, R.string.font_medium, 0xBFFFFFFF);
        place.setAllCaps(true);
        place.setLetterSpacing(0.12f);
        sky = text(13, R.string.font_regular, 0xE6FFFFFF);
        // Explicit wrap_content: a vertical LinearLayout's default is match_parent, which locked the block to the
        // first line's width and cut "Clear 35° / 29°" at the width of the place name.
        words.addView(place, wrap());
        words.addView(sky, wrap());
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.setMarginStart(Math.round(12 * dp));
        line.addView(words, wlp);
        root.addView(line, wrap());

        LinearLayout chip = new LinearLayout(ctx);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x47000000);
        bg.setStroke(Math.max(1, Math.round(dp * 0.75f)), 0x40FFFFFF);
        bg.setCornerRadius(16 * dp);
        chip.setBackground(bg);
        chip.setPadding(Math.round(10 * dp), Math.round(5 * dp), Math.round(12 * dp), Math.round(5 * dp));
        ImageView hanger = new ImageView(ctx);
        hanger.setImageResource(R.drawable.ic_wx_hanger);
        hanger.setAlpha(0.9f);
        int h = Math.round(14 * dp);
        chip.addView(hanger, new LinearLayout.LayoutParams(h, h));
        hint = text(12, R.string.font_regular, 0xF2FFFFFF);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.setMarginStart(Math.round(6 * dp));
        chip.addView(hint, hlp);
        pill = chip;
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Math.round(10 * dp);
        root.addView(pill, plp);
    }

    View view() {
        return root;
    }

    /** While shown: read now, every 10 minutes, and whenever the add-on has a new reading. */
    void start() {
        if (started) return;
        started = true;
        try {
            ctx.getContentResolver().registerContentObserver(NOW, false, observer);
        } catch (RuntimeException ignored) {   // add-on not installed: the read below finds nothing
        }
        main.post(tick);
    }

    void stop() {
        if (!started) return;
        started = false;
        main.removeCallbacks(tick);
        try {
            ctx.getContentResolver().unregisterContentObserver(observer);
        } catch (RuntimeException ignored) {
        }
    }

    private void refresh() {
        READER.execute(() -> {
            String[] row = null;
            try (Cursor c = ctx.getContentResolver().query(NOW, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    row = new String[]{c.getString(c.getColumnIndexOrThrow("place")),
                            c.getString(c.getColumnIndexOrThrow("temp")), c.getString(c.getColumnIndexOrThrow("sky")),
                            c.getString(c.getColumnIndexOrThrow("symbol")), c.getString(c.getColumnIndexOrThrow("high")),
                            c.getString(c.getColumnIndexOrThrow("low")), c.getString(c.getColumnIndexOrThrow("hint"))};
                }
            } catch (RuntimeException ignored) {   // add-on missing, or its provider failed: keep what is shown
                return;
            }
            String[] shown = row;
            main.post(() -> bind(shown));
        });
    }

    private void bind(String[] row) {
        if (row == null) {
            root.setVisibility(View.GONE);
            return;
        }
        symbol.setImageResource(symbolOf(row[3]));
        temp.setText(row[1]);
        place.setText(row[0]);
        sky.setText(row[2] + "  " + row[4] + " / " + row[5]);
        pill.setVisibility(TextUtils.isEmpty(row[6]) ? View.GONE : View.VISIBLE);
        hint.setText(row[6]);
        root.setContentDescription(row[0] + ", " + row[1] + ", " + row[2]);
        root.setVisibility(View.VISIBLE);
    }

    private static int symbolOf(String name) {
        if (name == null) return R.drawable.ic_wx_cloud;
        switch (name) {
            case "sun": return R.drawable.ic_wx_sun;
            case "moon": return R.drawable.ic_wx_moon;
            case "partly": return R.drawable.ic_wx_partly;
            case "fog": return R.drawable.ic_wx_fog;
            case "rain": return R.drawable.ic_wx_rain;
            case "snow": return R.drawable.ic_wx_snow;
            case "storm": return R.drawable.ic_wx_storm;
            default: return R.drawable.ic_wx_cloud;
        }
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(int sp, int font, int colour) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(UiFont.get(ctx, font));
        t.setTextColor(colour);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }
}
