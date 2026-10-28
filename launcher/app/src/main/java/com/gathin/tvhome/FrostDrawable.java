package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * frost_tiles: the wallpaper behind a tile, frosted, as the tile's glass. It draws the part of a tiny softened copy
 * of the picture (WallpaperPlayer.frostFor, about 120 px wide) that lies under the card on screen, stretched over the
 * card: a picture that small, filtered up, reads as frosted glass, at the cost of one small bitmap per picture and no
 * blur pass. The part is read from where the card is drawn now (scrolled, zoomed), so it lines up with the wallpaper,
 * and a focused tile shows it magnified a little, like glass would. A new picture cross-fades in with the wallpaper's
 * own crossfade.
 */
final class FrostDrawable extends Drawable {
    private final View card;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final int[] at = new int[2];
    private Bitmap frost, previous;
    private float mix = 1f;
    private ValueAnimator fade;

    FrostDrawable(View card) {
        this.card = card;
    }

    /** The picture to frost from; a change cross-fades over the wallpaper's 1.5 s unless animate is false. */
    void setFrost(Bitmap next, boolean animate) {
        if (next == frost) return;
        if (fade != null) fade.cancel();
        if (!animate || frost == null) {
            frost = next;
            previous = null;
            mix = 1f;
            invalidateSelf();
            return;
        }
        previous = frost;
        frost = next;
        mix = 0f;
        fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(1500);
        fade.setInterpolator(new DecelerateInterpolator());
        fade.addUpdateListener(a -> {
            mix = (float) a.getAnimatedValue();
            if (mix >= 1f) previous = null;
            invalidateSelf();
        });
        fade.start();
    }

    @Override
    public void draw(Canvas canvas) {
        if (frost == null) return;
        Rect bounds = getBounds();
        dst.set(bounds);
        if (previous != null && mix < 1f) {
            drawPart(canvas, previous, 255);
            drawPart(canvas, frost, Math.round(255 * mix));
        } else {
            drawPart(canvas, frost, 255);
        }
    }

    /** The part of this picture under the card where it is drawn now (scale and translation included). */
    private void drawPart(Canvas canvas, Bitmap picture, int alpha) {
        DisplayMetrics dm = card.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels, screenH = dm.heightPixels;
        int bw = picture.getWidth(), bh = picture.getHeight();
        // The wallpaper is centre-cropped to the screen: the same mapping from screen to picture.
        float s = Math.max(screenW / (float) bw, screenH / (float) bh);
        float ox = (screenW - bw * s) / 2f, oy = (screenH - bh * s) / 2f;
        card.getLocationOnScreen(at);
        float left = at[0], top = at[1];
        float right = left + card.getWidth() * card.getScaleX(), bottom = top + card.getHeight() * card.getScaleY();
        src.set(Math.round((left - ox) / s), Math.round((top - oy) / s),
                Math.round((right - ox) / s), Math.round((bottom - oy) / s));
        if (src.width() <= 0 || src.height() <= 0) return;
        paint.setAlpha(alpha * ownAlpha / 255);
        canvas.drawBitmap(picture, src, dst, paint);
    }

    /** The drawable's own alpha: a StateListDrawable cross-fading a glass pill (glass_buttons, soft_motion) fades the
     * frost with its other layers; ignored before, the bare frost showed dark for a moment as focus arrived. */
    private int ownAlpha = 255;

    @Override
    public void setAlpha(int alpha) {
        if (alpha == ownAlpha) return;
        ownAlpha = alpha;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return ownAlpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return ownAlpha == 255 ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT;
    }
}
