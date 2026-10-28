package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The launcher's toasts in the tile menu's dark glass (glass_panel: the grey gradient, faint rim and 20dp corners of
 * the Hold-OK menu) instead of Android's stock pill, as a slim pill at the bottom centre, under the tile row. Android draws its own and other
 * apps' toasts itself, so only the launcher's change. Android 11 and later block a custom toast from an app that is
 * not in front, so then the stock toast is used and no message is lost. Same calls as Toast.makeText.
 */
final class GlassToast {

    private GlassToast() {
    }

    static Toast makeText(Context c, int text, int duration) {
        return makeText(c, c.getText(text), duration);
    }

    @SuppressWarnings("deprecation")
    static Toast makeText(Context c, CharSequence text, int duration) {
        if (!inFront()) return Toast.makeText(c, text, duration);
        float dp = c.getResources().getDisplayMetrics().density;
        TextView view = new TextView(c);
        view.setText(text);
        view.setTextColor(0xFFF2F2F7);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        view.setTypeface(UiFont.get(c, R.string.font_medium));
        view.setLetterSpacing(0.01f);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundResource(R.drawable.glass_panel);
        // A slim pill under the tile row (the person, 23:44): smaller text and padding, near the bottom edge.
        int h = Math.round(16 * dp), v = Math.round(6 * dp);
        view.setPadding(h, v, h, v);
        view.setMaxWidth(Math.round(560 * dp));
        Toast toast = new Toast(c.getApplicationContext());
        toast.setView(view);
        toast.setDuration(duration);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, Math.round(4 * dp));
        return toast;
    }

    /** True while the launcher is the app in front (its own activities, Home or a settings page). */
    private static boolean inFront() {
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(info);
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }
}
