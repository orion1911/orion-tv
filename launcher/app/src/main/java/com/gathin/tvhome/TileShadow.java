package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * square_tiles: a soft shadow under a glass tile, as the item's background. Android's elevation shadow is drawn under
 * the whole tile, and through see-through glass it showed as a dark square inside it; this one is cut out where the
 * tile is, so it only darkens the wallpaper around and below it. Stacked rounded rectangles stand in for a blur.
 * It follows the tile's zoom and lift as they animate (the Holder invalidates the item on each frame): light at rest,
 * deeper and a little wider once the tile is focused.
 */
final class TileShadow extends Drawable {
    private static final int RINGS = 10;
    /** Darkness each ring adds, at rest and focused; the rings overlap, so it is darkest along the tile's lower edge. */
    private static final float REST_ALPHA = 0x07, FOCUS_ALPHA = 0x0E;

    private final View card;
    private final float density;
    /** The focused tile's scale (AppAdapter.focusScale), where the shadow reaches its focused strength. */
    private final float zoom;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF();
    private final Path tile = new Path();
    private int alpha = 255;

    TileShadow(View card, float density, float zoom) {
        this.card = card;
        this.density = density;
        this.zoom = zoom;
    }

    @Override
    public void draw(Canvas canvas) {
        int w = card.getWidth(), h = card.getHeight();
        if (w == 0 || h == 0 || alpha == 0 || card.getVisibility() != View.VISIBLE) return;
        // The tile as it is drawn now: scaled about its pivot and lifted by its translation.
        float sx = card.getScaleX(), sy = card.getScaleY();
        float px = card.getPivotX(), py = card.getPivotY();
        float left = card.getLeft() + px - px * sx;
        float top = card.getTop() + py - py * sy + card.getTranslationY();
        float width = w * sx, height = h * sy;
        float radius = AppAdapter.cornerRadius(w, density) * sx;
        float t = zoom > 1f ? Math.max(0f, Math.min(1f, (sx - 1f) / (zoom - 1f))) : 0f;
        float ringAlpha = (REST_ALPHA + (FOCUS_ALPHA - REST_ALPHA) * t) * alpha / 255f;
        float spread = (12 + 4 * t) * density, drop = (7 + 3 * t) * density;
        canvas.save();
        tile.reset();
        tile.addRoundRect(left, top, left + width, top + height, radius, radius, Path.Direction.CW);
        canvas.clipOutPath(tile);
        for (int i = RINGS - 1; i >= 0; i--) {
            float o = spread * i / RINGS;
            paint.setColor(Math.round(ringAlpha * (1f - (float) i / RINGS)) << 24);
            ring.set(left - o * 0.7f, top + drop * 0.4f, left + width + o * 0.7f, top + height + drop + o);
            canvas.drawRoundRect(ring, radius + o, radius + o, paint);
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha == alpha) return;
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // a shadow has no colour to filter
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
