package com.gathin.tvhome;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.widget.TextClock;

import java.util.Locale;

/**
 * clock_large: a large thin clock, its digits narrowed so they read tall, with the date on the line under it in the
 * locale's own order and sentence case ("Monday, October 26"), as on an ambient home screen. Home's header and the
 * screensaver (screensaver_home) both use it, so the clock does not change as one gives way to the other.
 * No text shadows: their blur is cut off at the text's box and showed as a faint square on the wallpaper.
 */
final class HomeClockStyle {
    private HomeClockStyle() {}

    static final float CLOCK_SP = 108f;   // 126 before the weather (weather_widget) needed the room under it
    /** Horizontal squeeze of the digits: narrower, so the clock reads taller (same width as 112sp at 0.84). */
    static final float CLOCK_SCALE_X = 0.75f;
    static final float DATE_SP = 22f;
    /** The date's top margin under the clock: negative, as the digits have no descenders to fill the line below them. */
    static final float DATE_GAP_DP = -14f;

    static void apply(Context ctx, TextClock clock, TextClock ampm, TextClock date) {
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, CLOCK_SP);
        clock.setTypeface(UiFont.get(ctx, R.string.font_extralight));
        clock.setTextScaleX(CLOCK_SCALE_X);
        clock.setLetterSpacing(-0.01f);
        clock.setTextColor(0xFFFFFFFF);
        clock.setShadowLayer(0, 0, 0, 0);
        if (ampm != null) {
            ampm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
            ampm.setTypeface(UiFont.get(ctx, R.string.font_light));
            ampm.setTextColor(0xD9FFFFFF);
            ampm.setShadowLayer(0, 0, 0, 0);
        }
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, DATE_SP);
        date.setTypeface(UiFont.get(ctx, R.string.font_regular));
        date.setAllCaps(false);
        date.setLetterSpacing(0.005f);
        date.setTextColor(0xF2FFFFFF);
        date.setShadowLayer(0, 0, 0, 0);
    }
}
