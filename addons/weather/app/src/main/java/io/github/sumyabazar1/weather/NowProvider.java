package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Process;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The weather now for the Clean Your TV launcher's header (its weather_widget): one row with the chosen city, the
 * temperature, the words for the sky, a symbol name, today's high and low, and the pill's hint. The launcher has no
 * network permission, so it asks this app; the row comes from this app's own cache at once, and a reading older
 * than {@link Forecast#REFRESH_MS} (or for another city) is fetched in the background and announced with
 * notifyChange. Nothing runs unless the launcher asks, and only the launcher (or this app) gets an answer.
 */
public final class NowProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.sumyabazar1.weather.now";
    static final Uri URI = Uri.parse("content://" + AUTHORITY + "/now");
    private static final String LAUNCHER = "com.gathin.tvhome";
    private static final String[] COLUMNS = {"place", "temp", "sky", "symbol", "high", "low", "hint", "updated"};
    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sortOrder) {
        Context c = getContext();
        MatrixCursor out = new MatrixCursor(COLUMNS);
        if (c == null || !trusted(c)) return out;
        SharedPreferences place = c.getSharedPreferences("place", Context.MODE_PRIVATE);
        String name = place.getString("name", null);
        if (name == null) return out;   // no city chosen yet: open the Weather app once
        SharedPreferences cache = c.getSharedPreferences("now", Context.MODE_PRIVATE);
        boolean same = name.equals(cache.getString("place", null));
        long now = System.currentTimeMillis();
        long age = now - cache.getLong("updated", 0);
        long sinceTry = now - cache.getLong("tried", 0);
        if ((!same || age > Forecast.REFRESH_MS) && sinceTry > Forecast.RETRY_MS) {
            refresh(c.getApplicationContext(), name, Double.longBitsToDouble(place.getLong("lat", 0)),
                    Double.longBitsToDouble(place.getLong("lon", 0)));
        }
        if (same && cache.getLong("updated", 0) > 0) {
            out.addRow(new Object[]{name, cache.getString("temp", ""), cache.getString("sky", ""),
                    cache.getString("symbol", "cloud"), cache.getString("high", ""), cache.getString("low", ""),
                    cache.getString("hint", null), cache.getLong("updated", 0)});
        }
        return out;
    }

    /** Only the launcher, or this app itself. */
    private static boolean trusted(Context c) {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return true;
        PackageManager pm = c.getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages == null) return false;
        for (String p : packages) if (LAUNCHER.equals(p)) return true;
        return false;
    }

    private static void refresh(Context c, String name, double lat, double lon) {
        if (!FETCHING.compareAndSet(false, true)) return;
        SharedPreferences cache = c.getSharedPreferences("now", Context.MODE_PRIVATE);
        cache.edit().putLong("tried", System.currentTimeMillis()).apply();
        new Thread(() -> {
            try {
                Forecast.Now n = Forecast.now(lat, lon);
                boolean f = "f".equalsIgnoreCase(c.getString(R.string.temperature_unit).trim());
                cache.edit()
                        .putString("place", name)
                        .putString("temp", Forecast.degrees(n.tempC, f) + "°")
                        .putString("sky", c.getString(Forecast.sky(n.code, n.day)))
                        .putString("symbol", Forecast.symbol(n.code, n.day))
                        .putString("high", Forecast.degrees(n.highC, f) + "°")
                        .putString("low", Forecast.degrees(n.lowC, f) + "°")
                        .putString("hint", Forecast.hint(c, n, f))
                        .putLong("updated", System.currentTimeMillis())
                        .apply();
                c.getContentResolver().notifyChange(URI, null);
            } catch (Exception ignored) {
                // offline or the service failed: the old reading stays, the next ask after RETRY_MS tries again
            } finally {
                FETCHING.set(false);
            }
        }, "weather-now").start();
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
