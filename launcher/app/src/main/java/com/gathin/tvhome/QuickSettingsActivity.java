package com.gathin.tvhome;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The panel the remote's Settings button opens (settings_key_panel, HomeKeyService) and Home's bell opens
 * (notifications_button), shaped like Google TV's own, which left with its stock launcher: a compact drawer at the top right
 * with the date and time, a grid of quick tiles, the notifications as cards and Clear all. Translucent and in a task
 * of its own, so the app behind keeps running and Back returns to it. Pressing Settings again closes it.
 */
public final class QuickSettingsActivity extends Activity {
    /** Opened from Home's bell: focus starts on the first notification instead of the first tile. */
    static final String EXTRA_NOTIFICATIONS = "com.gathin.tvhome.extra.NOTIFICATIONS";
    private static final int MAX_CARDS = 8;

    static final int CARD = 0xFF2E333D, CARD_FOCUSED = 0xFFE9EEF6;
    static final int TEXT = 0xFFFFFFFF, TEXT_FOCUSED = 0xFF141922;
    static final int SUB = 0xB3FFFFFF, SUB_FOCUSED = 0xFF4B525D;
    static final int BADGE = 0xFF3B414C, BADGE_FOCUSED = 0xFFD3DAE6;

    private float dp;
    private LinearLayout cards;
    private View firstTile;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_panel);
        dp = getResources().getDisplayMetrics().density;
        findViewById(R.id.scrim).setOnClickListener(v -> slideOutAndFinish(this, findViewById(R.id.drawer)));
        slideIn(findViewById(R.id.drawer));
        findViewById(R.id.drawer).setOnClickListener(v -> { });   // a click on the drawer itself does not close it

        TextClock date = findViewById(R.id.panelDate);
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        ImageView gear = findViewById(R.id.panelGear);
        gear.setBackground(states(0x00000000, CARD_FOCUSED, 17 * dp));
        gear.setImageTintList(colors(TEXT, TEXT_FOCUSED));
        gear.setOnClickListener(v -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        // Next wallpaper, left of the gear: Home changes to the next picture (with its crossfade) as the panel closes,
        // instead of waiting out the rotation.
        ImageView nextPicture = new ImageView(this);
        nextPicture.setImageResource(R.drawable.ic_next_wallpaper);
        nextPicture.setImageTintList(colors(TEXT, TEXT_FOCUSED));
        nextPicture.setBackground(states(0x00000000, CARD_FOCUSED, 17 * dp));
        int pad = Math.round(8 * dp);
        nextPicture.setPadding(pad, pad, pad, pad);
        nextPicture.setFocusable(true);
        nextPicture.setClickable(true);
        nextPicture.setContentDescription(getString(R.string.next_wallpaper));
        nextPicture.setOnClickListener(v -> {
            WallpaperPlayer.skipToNext(this);
            slideOutAndFinish(this, findViewById(R.id.drawer));
        });
        focusZoom(nextPicture);
        focusZoom(gear);
        ViewGroup header = (ViewGroup) gear.getParent();
        int size = Math.round(34 * dp);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(size, size);
        nlp.setMarginEnd(Math.round(4 * dp));
        header.addView(nextPicture, header.indexOfChild(gear), nlp);
        // Hide this wallpaper, between Next and the gear: the picture leaves the rotation for good and Home moves on to the next
        // one. Holding it brings every hidden picture back (WallpaperPlayer.HIDDEN).
        ImageView hidePicture = new ImageView(this);
        hidePicture.setImageResource(R.drawable.ic_hide_wallpaper);
        hidePicture.setImageTintList(colors(TEXT, TEXT_FOCUSED));
        hidePicture.setBackground(states(0x00000000, CARD_FOCUSED, 17 * dp));
        hidePicture.setPadding(pad, pad, pad, pad);
        hidePicture.setFocusable(true);
        hidePicture.setClickable(true);
        hidePicture.setContentDescription(getString(R.string.hide_wallpaper));
        hidePicture.setOnClickListener(v -> {
            if (WallpaperPlayer.hideCurrent(this)) GlassToast.makeText(this, R.string.wallpaper_hidden, Toast.LENGTH_LONG).show();
            slideOutAndFinish(this, findViewById(R.id.drawer));
        });
        hidePicture.setOnLongClickListener(v -> {
            int back = WallpaperPlayer.showHidden(this);
            GlassToast.makeText(this, back > 0 ? getString(R.string.wallpapers_unhidden, back)
                    : getString(R.string.no_hidden_wallpapers), Toast.LENGTH_SHORT).show();
            return true;
        });
        focusZoom(hidePicture);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(size, size);
        hlp.setMarginEnd(Math.round(4 * dp));
        header.addView(hidePicture, header.indexOfChild(gear), hlp);

        List<View> tiles = new ArrayList<>();
        tiles.add(tile(R.drawable.ic_screensaver, R.string.quick_screensaver, () -> open(new Intent(Intent.ACTION_MAIN)
                .setClassName("com.android.systemui", "com.android.systemui.Somnambulator"))));
        tiles.add(tile(R.drawable.ic_wifi, R.string.quick_network,
                () -> open(SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS))));
        // Google TV's settings have no screen for the standard Bluetooth action (it answered "not available"): the
        // accessory pairing screen Android TV opens for CONNECT_INPUT is next, TV settings last.
        // Both accessory screens need BLUETOOTH_CONNECT (Android 12): the manifest asks for it and it is granted
        // over ADB; without it they refuse and the next one is tried. Sony's own screen first, then Android TV's.
        // TV Settings' own "Remotes & Accessories" page is a slice its SliceActivity shows by address, the one its main
        // menu uses (found in the Settings app; on this Sony MediaTek's provider of the same name only has /rs, RS-232).
        // The screens after it are fallbacks.
        tiles.add(tile(R.drawable.ic_bluetooth, R.string.quick_bluetooth, () -> openFirst(
                accessories("content://com.android.tv.settings.accessories.sliceprovider/general"),
                new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                new Intent("android.settings.SONYADDACCESSORYACTIVITY"),
                new Intent("com.google.android.intent.action.CONNECT_INPUT"),
                SystemSettings.intent(this, Settings.ACTION_SETTINGS))));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        tiles.add(tile(R.drawable.ic_home_settings, R.string.launcher_settings,
                () -> open(new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS))));
        LinearLayout grid = findViewById(R.id.tiles);
        for (int i = 0; i < tiles.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, Math.round(46 * dp), 1f);
            row.addView(tiles.get(i), half);
            LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, Math.round(46 * dp), 1f);
            second.setMarginStart(Math.round(8 * dp));
            row.addView(i + 1 < tiles.size() ? tiles.get(i + 1) : new View(this), second);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = Math.round(8 * dp);
            grid.addView(row, rowLp);
        }
        firstTile = tiles.get(0);

        cards = findViewById(R.id.cards);
        boolean notifications;
        try {
            notifications = Config.load(this).notificationsButton;
        } catch (RuntimeException e) {
            notifications = false;
        }
        if (notifications) fillCards();
        else cards.setVisibility(View.GONE);

        View first = firstTile;
        if (getIntent().getBooleanExtra(EXTRA_NOTIFICATIONS, false) && cards.getChildCount() > 0
                && cards.getChildAt(0).isFocusable()) first = cards.getChildAt(0);
        first.requestFocus();
    }

    /** The notification cards, newest first, then Clear all; a quiet line when there are none. */
    private void fillCards() {
        cards.removeAllViews();
        List<StatusBarNotification> list = GuardRevival.active(this);
        PackageManager pm = getPackageManager();
        for (int i = 0; i < list.size() && i < MAX_CARDS; i++) {
            StatusBarNotification sbn = list.get(i);
            Notification n = sbn.getNotification();
            Drawable icon;
            try {
                icon = pm.getApplicationIcon(sbn.getPackageName());
            } catch (PackageManager.NameNotFoundException e) {
                icon = getDrawable(R.drawable.ic_bell);
            }
            View card = card(this, dp, icon, false, n.extras.getCharSequence(Notification.EXTRA_TITLE),
                    n.extras.getCharSequence(Notification.EXTRA_TEXT));
            String pkg = sbn.getPackageName(), key = sbn.getKey();
            PendingIntent target = n.contentIntent;
            boolean autoCancel = (n.flags & Notification.FLAG_AUTO_CANCEL) != 0;
            boolean clearable = sbn.isClearable();
            card.setOnClickListener(v -> {
                if (target != null) {
                    try {
                        target.send();
                        if (autoCancel) GuardRevival.cancel(key);
                        finish();
                        return;
                    } catch (PendingIntent.CanceledException ignored) {
                        // its app withdrew the action: open the app itself, below
                    }
                }
                Intent app = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (app == null) app = pm.getLaunchIntentForPackage(pkg);
                if (app != null) {
                    if (autoCancel) GuardRevival.cancel(key);
                    open(app);
                    return;
                }
                // Nothing to open (a notice with no action, from no app with a screen): selecting it dismisses it, as
                // reading it is all there is to do, and the drawer stays.
                if (clearable) {
                    GuardRevival.cancel(key);
                    cards.postDelayed(this::refocusAfterRefill, 300);
                }
            });
            cards.addView(card);
        }
        if (list.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.no_notifications);
            none.setTextColor(SUB);
            none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            none.setTypeface(UiFont.get(this, R.string.font_regular));
            none.setPadding(Math.round(6 * dp), Math.round(2 * dp), 0, Math.round(10 * dp));
            cards.addView(none);
            return;
        }
        // Clear all only when something listed can be cleared: a notice its app marks as standing (ongoing, no-clear)
        // stays whatever is pressed, and a Clear all that leaves everything in place looks broken.
        List<String> clearable = new ArrayList<>();
        for (int i = 0; i < list.size() && i < MAX_CARDS; i++) {
            if (list.get(i).isClearable()) clearable.add(list.get(i).getKey());
        }
        if (clearable.isEmpty()) return;
        View clear = card(this, dp, getDrawable(R.drawable.ic_clear_all), true, getString(R.string.clear_all), null);
        clear.setOnClickListener(v -> {
            for (String key : clearable) GuardRevival.cancel(key);
            GuardRevival.clearAll();   // and any clearable one past the cards shown
            firstTile.requestFocus();
            cards.postDelayed(this::fillCards, 400);   // the system takes a moment to drop them
        });
        cards.addView(clear);
    }

    /** The cards again after one went, focus on the first one left (or the first tile when none are). */
    private void refocusAfterRefill() {
        fillCards();
        View first = cards.getChildCount() > 0 && cards.getChildAt(0).isFocusable() ? cards.getChildAt(0) : firstTile;
        first.requestFocus();
    }

    /** A quick tile: icon and label on a rounded card that turns light when focused. */
    private View tile(int icon, int label, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(11 * dp);
        tile.setPadding(pad, 0, pad, 0);
        tile.setBackground(states(CARD, CARD_FOCUSED, 14 * dp));
        tile.setFocusable(true);
        tile.setClickable(true);
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setImageTintList(colors(TEXT, TEXT_FOCUSED));
        image.setDuplicateParentStateEnabled(true);
        int size = Math.round(17 * dp);
        tile.addView(image, new LinearLayout.LayoutParams(size, size));
        TextView text = label(this, getString(label), 12, R.string.font_medium, TEXT, TEXT_FOCUSED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Math.round(8 * dp));
        tile.addView(text, lp);
        tile.setOnClickListener(v -> action.run());
        focusZoom(tile);
        return tile;
    }

    /** A notification card: the app's icon on a round badge, the title and up to two lines of text. */
    static View card(Context c, float dp, Drawable icon, boolean tinted, CharSequence title, CharSequence text) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(9 * dp);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(states(CARD, CARD_FOCUSED, 14 * dp));
        card.setFocusable(true);
        card.setClickable(true);
        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(states(BADGE, BADGE_FOCUSED, 14 * dp));
        badge.setDuplicateParentStateEnabled(true);
        ImageView image = new ImageView(c);
        image.setImageDrawable(icon);
        if (tinted) image.setImageTintList(colors(TEXT, TEXT_FOCUSED));
        image.setDuplicateParentStateEnabled(true);
        int inner = Math.round(16 * dp);
        badge.addView(image, new FrameLayout.LayoutParams(inner, inner, Gravity.CENTER));
        int outer = Math.round(28 * dp);
        card.addView(badge, new LinearLayout.LayoutParams(outer, outer));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setDuplicateParentStateEnabled(true);
        texts.addView(label(c, title, 12, R.string.font_medium, TEXT, TEXT_FOCUSED));
        if (!TextUtils.isEmpty(text)) {
            TextView body = label(c, text, 11, R.string.font_regular, SUB, SUB_FOCUSED);
            body.setSingleLine(false);
            body.setMaxLines(2);
            texts.addView(body);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Math.round(10 * dp));
        card.addView(texts, lp);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = Math.round(8 * dp);
        card.setLayoutParams(cardLp);
        focusZoom(card);
        return card;
    }

    static TextView label(Context c, CharSequence value, int sp, int font, int color, int focused) {
        TextView text = new TextView(c);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        text.setTypeface(UiFont.get(c, font));
        text.setTextColor(colors(color, focused));
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setDuplicateParentStateEnabled(true);
        return text;
    }

    static ColorStateList colors(int normal, int focused) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}},
                new int[]{focused, focused, normal});
    }

    static StateListDrawable states(int normal, int focused, float radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, round(focused, radius));
        states.addState(new int[]{android.R.attr.state_pressed}, round(focused, radius));
        states.addState(new int[]{}, round(normal, radius));
        // The light focus fill cross-fades in and out instead of switching on the frame focus moves.
        states.setEnterFadeDuration(160);
        states.setExitFadeDuration(200);
        return states;
    }

    /** A focused card or tile grows a touch, gently; back to size as focus leaves. */
    static void focusZoom(View v) {
        v.setOnFocusChangeListener((view, focused) -> view.animate()
                .scaleX(focused ? 1.03f : 1f).scaleY(focused ? 1.03f : 1f)
                .setDuration(focused ? 180 : 160)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start());
    }

    /** The drawer slides in from the right as the panel opens, instead of appearing at once. */
    static void slideIn(View drawer) {
        float dp = drawer.getResources().getDisplayMetrics().density;
        drawer.setAlpha(0f);
        drawer.setTranslationX(28 * dp);
        drawer.animate().alpha(1f).translationX(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    /** And back out before the panel closes (Back, or a click beside it). */
    static void slideOutAndFinish(Activity activity, View drawer) {
        float dp = drawer.getResources().getDisplayMetrics().density;
        drawer.animate().cancel();
        drawer.animate().alpha(0f).translationX(28 * dp).setDuration(160)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    activity.finish();
                    activity.overridePendingTransition(0, 0);
                }).start();
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    /**
     * TV Settings' accessories page at this slice address, or null when the TV has no provider there: SliceActivity
     * exists on every Android TV and shows an empty page for an address nothing answers, so it cannot fail over.
     */
    private Intent accessories(String uri) {
        String authority = android.net.Uri.parse(uri).getAuthority();
        if (authority == null || getPackageManager().resolveContentProvider(authority, 0) == null) return null;
        return new Intent("android.settings.SLICE_SETTINGS")
                .putExtra("slice_uri", uri)
                .putExtra("slice_title", getString(R.string.remotes_accessories));
    }

    /**
     * The first of these the TV can open. Each is tried by starting it, not looked up first: on Android 11 and later a
     * lookup can miss a settings screen the launcher is not allowed to see, while starting it still works.
     */
    private void openFirst(Intent... targets) {
        for (Intent target : targets) {
            if (target == null) continue;
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // not on this TV: the next one
            }
        }
        GlassToast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void open(Intent target) {
        try {
            if (target != null) startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else GlassToast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        } catch (ActivityNotFoundException | SecurityException e) {
            GlassToast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    /** Back slides the drawer out before the panel closes. */
    @Override
    public void onBackPressed() {
        slideOutAndFinish(this, findViewById(R.id.drawer));
    }

    /** Settings (or the bell) pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }
}
