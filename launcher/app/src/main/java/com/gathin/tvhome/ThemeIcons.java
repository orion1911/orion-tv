package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import java.io.File;

/**
 * custom_icons: a theme's own logo for an app, icons/&lt;package&gt;.png in the theme folder (tools/theme.ps1 pushes the
 * theme's icons/ folder), drawn on the tile instead of the mark made from the app's icon. The tile's accent colour
 * still comes from the app's own icon.
 */
final class ThemeIcons {

    private ThemeIcons() {
    }

    /** This package's logo from the theme, or null to keep the generated one. */
    static Drawable forPackage(Context ctx, String pkg) {
        if (pkg == null) return null;
        File dir = Config.dir(ctx);
        if (dir == null) return null;
        File f = new File(new File(dir, ThemeFiles.ICONS), pkg + ".png");
        if (!f.isFile()) return null;
        Bitmap b = BitmapFactory.decodeFile(f.getPath());
        return b == null ? null : new BitmapDrawable(ctx.getResources(), b);
    }
}
