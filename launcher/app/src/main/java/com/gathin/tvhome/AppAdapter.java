package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order,
 * then empty slots up to a full row, see {@link DockSlots}), then the module cards the owner chose to show
 * (by label), the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);

        void onLibraryRequested();
        void onLibraryFocused();
        /** Hold OK on a tile: the activity shows the tile menu (move, remove from Home, app info). */
        void onTileLongPress(int position, View card);
        void onLibraryUp();
    }

    static final class Entry {
        static final int APP = 0, HOME_SETTINGS = 1, CARD = 2, EMPTY = 3;

        final int kind;
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        /** Text under the tile when it is focused in the grid: the label, plus the caption for a card. */
        final String name;
        /** Ambient glow colour; 0 until sampled from the banner, or the card's accent from the start. */
        int ambient;
        /** tile_accent: the colour this app's frosted tile turns to towards the bottom (accentFor); 0 for none. */
        int accent;
        /** uniform_tiles card fill, top and bottom colour; null until sampled from the icon. */
        int[] backdrop;
        /** uniform_tiles: the adaptive icon's foreground alone, drawn on the glass when the card took the icon's plate colour. */
        Drawable mark;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this(APP, label, component, banner, icon, null, 0);
        }

        Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent) {
            this.kind = kind;
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.name = caption == null ? label : label + " \u00B7 " + caption;
            this.ambient = accent;
        }
    }

    static final float FOCUS_SCALE = 1.10f;
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
     * highlight, capped at the design size so 4 columns on a 1080p TV look exactly as before.
     */
    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        // square_tiles: an ambient home row's proportions, each tile about four fifths of its slot, so the gaps stay
        // even at any column count (the 80dp floor below would close them up at 9 or 10 columns).
        if (squareTiles) return Math.round(cell * SQUARE_SHARE);
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    /**
     * square_tiles: near-square tiles with rounder corners, set from the config before any tile is measured
     * (MainActivity and the constructor). A static, like the sizes here, so every static helper agrees on the shape.
     */
    static boolean squareTiles;
    /** square_tiles: height over width, and the corner radius as a share of the width. */
    static final float SQUARE_RATIO = 0.94f, SQUARE_CORNER = 0.22f;
    /** square_tiles: the tile's share of its column. */
    static final float SQUARE_SHARE = 0.81f;
    /** square_tiles: the logo's brightness on a tile that is not focused (of 255), so the focused one stands out. */
    static final int RESTING_LOGO_LEVEL = 0xBF;

    /** A logo at this brightness: null (as drawn) at full, else multiplied by that grey. */
    static ColorFilter logoFilter(int level) {
        return level >= 255 ? null : new PorterDuffColorFilter(Color.rgb(level, level, level), PorterDuff.Mode.MULTIPLY);
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * (squareTiles ? SQUARE_RATIO : DESIGN_H_DP / DESIGN_W_DP));
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return itemHeightPx(tileHeightPx, true, density);
    }

    /** The same for either layout: the plain grid (dock_row false) uses the compact spacing. */
    static int itemHeightPx(int tileHeightPx, boolean dockRow, float density) {
        return tileHeightPx + Math.round((dockRow ? ITEM_EXTRA_DP : COMPACT_ITEM_EXTRA_DP) * density);
    }

    /**
     * dock_row false: 4dp padding above and below, a 4dp gap and a 20dp name label, and 16dp kept under the last
     * row. The plain grid fits more rows on the screen, and with item_app.xml's roomier spacing the tiles came
     * out small with wide gaps between them.
     */
    static final float COMPACT_ITEM_EXTRA_DP = 4 + 4 + 4 + 20;
    static final float COMPACT_BOTTOM_ROOM_DP = 16;

    /** item_app.xml: 8dp padding above and below, a 7dp gap and the 22dp name label under the tile. */
    static final float ITEM_EXTRA_DP = 45;
    /** DockDecoration's offsets above and below the dock row. */
    static final float DOCK_EXTRA_DP = 6 + 12;
    /** Room kept under the last row rows_below_dock asks for (MainActivity places the dock with the same). */
    static final float BOTTOM_ROOM_DP = 30;
    /** Room kept above the dock for the dock title (48sp hero name plus its 40dp gap) when dock_title is on. */
    static final float TITLE_ROOM_DP = 104;
    /** dock_row false: room above the first row for its zoom and glass (MainActivity places the grid with it). */
    static final float TOP_ROOM_DP = 12;

    /**
     * Tile width that fits the columns across and, with rows_below_dock > 0, the dock (with its title above it,
     * if shown) plus that many rows down the grid's height; the smaller wins. With rows_below_dock = 0, or before
     * the grid has a height, only the width counts, as before. Without this, 2 or 3 rows below the dock did not
     * fit: the dock was pushed to the top and its title drew over the clock. With dock_row false there is no band
     * or title to make room for: the first row plus rows_below_dock rows fit under a small top margin.
     */
    static int tileWidthPx(int innerWidthPx, int gridHeightPx, int columns, int rowsBelowDock, boolean dockTitle,
                           boolean dockRow, float density) {
        int w = tileWidthPx(innerWidthPx, columns, density);
        if (gridHeightPx <= 0 || rowsBelowDock <= 0) return w;
        float reserved = (dockRow ? DOCK_EXTRA_DP + BOTTOM_ROOM_DP + (dockTitle ? TITLE_ROOM_DP : 0)
                : TOP_ROOM_DP + COMPACT_BOTTOM_ROOM_DP) * density;
        float perItem = (gridHeightPx - reserved) / (rowsBelowDock + 1);
        float tileH = perItem - (dockRow ? ITEM_EXTRA_DP : COMPACT_ITEM_EXTRA_DP) * density;
        int byHeight = Math.round(tileH / (squareTiles ? SQUARE_RATIO : DESIGN_H_DP / DESIGN_W_DP));
        // Shrink at most to MIN_HEIGHT_FIT of the width-only size. Below that the tiles look lost in their wide
        // columns (2 rows under a titled dock came out at 80dp); the rows that still don't fit run off the bottom
        // and scroll, and MainActivity keeps the title's room above the dock.
        return Math.max(Math.max(Math.round(80 * density), Math.round(w * MIN_HEIGHT_FIT)), Math.min(w, byHeight));
    }

    /** The smallest share of the width-only tile size that fitting rows_below_dock into the height may go to. */
    static final float MIN_HEIGHT_FIT = 0.85f;

    private final Context ctx;
    private final int rowsBelowDock;
    private final boolean dockTitle;
    /** The owner's saved order (reorder button, Order): key -> rank. Empty when there is none or it no longer applies. */
    private Map<String, Integer> savedRanks = Collections.emptyMap();
    /** Dock size recorded with the saved order (Order.docked), or -1: then dock_apps says which tiles are docked. */
    private int savedDocked = -1;
    /** Reorder mode: no empty dock slots, so every position holds a tile that can move. */
    private boolean reordering;
    private final TileListener listener;
    private final int dockSize;
    /** config dock_row: false makes the dock apps the first tiles of one plain grid (names shown, no empty slots). */
    private final boolean dockRow;
    /** config single_row: tiles sit in one sideways row, each as wide as a grid column would be. */
    private final boolean singleRow;
    /** config always_show_names: the alpha of an unfocused tile's name (0 hides it). */
    private final float restNameAlpha;
    /** cinematic_home: only then does a launch record the last-opened app the shelf uses. */
    private final boolean cinematic;
    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Grid apps fill the dock slots dock_apps leaves free (config.xml, fill_dock). */
    private final boolean fillDock;
    /** Dock apps found at the last reload, and the empty slots after them. */
    private int docked, emptySlots;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    /** Apps pre-seeded into the App library's "added" set (config.xml library_apps); empty by default. */
    private final Set<String> libraryPreload;
    private final boolean animations;
    private final boolean advancedEffects;
    private final boolean showLibrary;
    /** Packages with a leanback entry at the last reload(): their tiles launch with the leanback category. */
    private Set<String> leanbackPackages = new HashSet<>();
    private final boolean showHomeSettings;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): no overshoot, shorter fades. */
    private final boolean reducedMotion;
    /** The focus zoom may overshoot (config.xml, focus_overshoot). */
    private final boolean focusOvershoot;
    /** focus_zoom in percent; 0 keeps the built-in zoom. */
    private final int focusZoom;
    /** white_marks: white logos on brand-coloured glass (whiteMark, brandGlass). */
    private final boolean whiteMarks;
    /** clear_glass: untinted frosted glass with each logo in its own colours (whiteMark with keepColour). */
    private final boolean clearGlass;
    /** Package to the action its tile sends instead of the start screen (config.xml, launch_actions). */
    private final Map<String, String> launchActions;
    /** Hold OK opens the activity's tile menu (config.xml, tile_menu); false opens App info, as before. */
    private final boolean tileMenu;
    /** Tile names and bundled banners that replace the app's own (config.xml, app_labels and app_banners). */
    private final Map<String, String> appLabels, appBanners;
    /** Every app tile is its icon on a card tinted from that icon, never its banner (config.xml, uniform_tiles). */
    private final boolean uniformTiles;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int dockSize, Config cfg) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.favourites = cfg.dockApps;
        this.fillDock = cfg.fillDock;
        this.hidden = new HashSet<>(cfg.hiddenApps);
        this.libraryPreload = new HashSet<>(cfg.libraryApps);
        this.animations = cfg.animations;
        this.advancedEffects = cfg.animations && cfg.advancedEffects;
        this.showHomeSettings = cfg.showHomeSettingsShortcut;
        this.showLibrary = LibraryApps.showPill(cfg.appLibrary, cfg.libraryButton, cfg.showLibraryPill);
        this.reducedMotion = cfg.reducedMotion;
        this.focusOvershoot = cfg.focusOvershoot;
        this.rowsBelowDock = cfg.rowsBelowDock;
        this.dockTitle = cfg.dockTitle;
        this.dockRow = cfg.dockRow;
        this.singleRow = cfg.singleRow;
        this.restNameAlpha = cfg.alwaysShowNames ? 0.75f : 0f;
        this.cinematic = cfg.cinematicHome;
        this.launchActions = cfg.launchActions;
        this.tileMenu = cfg.tileMenu;
        this.appLabels = cfg.appLabels;
        this.appBanners = cfg.appBanners;
        this.uniformTiles = cfg.uniformTiles;
        squareTiles = cfg.squareTiles;
        this.focusZoom = cfg.focusZoom;
        this.whiteMarks = cfg.whiteMarks;
        this.clearGlass = cfg.clearGlass;
        this.frostTiles = cfg.frostTiles && cfg.clearGlass;
        this.tileAccent = cfg.tileAccent && this.frostTiles;
        this.whiteLogos = cfg.whiteLogos;
        this.customIcons = cfg.customIcons;
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
    }

    /**
     * Rebuilds the tiles. A shown module card stands in for every tile of its package (same component,
     * so focus follows it); hidden_apps wins over a card, and a card's package in dock_apps takes that slot.
     */
    void reload(List<Cards.Card> cards, List<ResolveInfo> leanback) {
        PackageManager pm = ctx.getPackageManager();
        // The LEANBACK query is shared with the module-card check (MainActivity); copy it before adding
        // library apps so the caller's list stays as it was.
        List<ResolveInfo> found = new ArrayList<>(leanback);
        Set<String> tvPackages = new HashSet<>();
        for (ResolveInfo ri : found) if (ri.activityInfo != null) tvPackages.add(ri.activityInfo.packageName);
        leanbackPackages = tvPackages;
        // Copy before merging: LibraryApps.load() may return an immutable empty set (#419).
        Set<String> added = new HashSet<>(LibraryApps.load(ctx));
        added.addAll(libraryPreload);
        Set<String> removed = LibraryApps.loadRemoved(ctx);
        if (!added.isEmpty()) {
            Set<String> seen = new HashSet<>(tvPackages);
            for (ResolveInfo ri : pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                if (ri.activityInfo != null && added.contains(ri.activityInfo.packageName)
                        && seen.add(ri.activityInfo.packageName)) found.add(ri);
            }
        }
        Map<String, Cards.Card> cardByPkg = new HashMap<>();
        for (Cards.Card c : cards) cardByPkg.put(c.pkg, c);
        List<Entry> fresh = new ArrayList<>();
        // Some system apps declare two leanback entry points under one name (Sony's Timers & Clock on the
        // BRAVIA VU31), which would show as two identical tiles: keep the first per package and label.
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || !ri.activityInfo.exported
                    || !LibraryApps.visible(tvPackages.contains(pkg), hidden.contains(pkg), added.contains(pkg), removed.contains(pkg))) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            Cards.Card card = cardByPkg.get(pkg);
            if (card != null && !card.component.equals(component)) continue;   // the card stands in for this tile
            // Same cleaner as the card prompt: an app label with a bidi override or line separator must not
            // redraw the tile name as something else (#117).
            String label = card != null ? card.label : Cards.cleanLabel(
                    appLabels.containsKey(pkg) ? appLabels.get(pkg) : String.valueOf(ri.loadLabel(pm)), pkg);
            if (!seen.add(pkg + '\n' + label)) continue;
            Drawable banner = null;
            // uniform_tiles draws the icon, so an app's banner is never loaded. A module card keeps its banner, except
            // with clear_glass: there every tile, card or app, new or old, is its logo on the same glass.
            if (!(uniformTiles && (card == null || clearGlass))) {
                banner = bundledBanner(appBanners.get(pkg));
                if (banner == null) banner = loadBanner(pm, ri);
            }
            // The icon is only drawn (and sampled for the glow) when there is no banner, so a tile with a
            // banner skips loading and decoding it.
            Drawable icon = banner == null ? ri.loadIcon(pm) : null;
            if (card != null) {
                fresh.add(new Entry(Entry.CARD, label, component, banner, icon, card.caption, card.accent));
            } else {
                fresh.add(new Entry(label, component, banner, icon));
            }
        }
        Collections.sort(fresh, (a, b) -> {
            // The owner's own order first; tiles it does not know (new installs) follow by the rule below.
            int saved = Order.compare(savedRanks, a.component.flattenToShortString(), b.component.flattenToShortString());
            if (saved != 0) return saved;
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            // Cards come right after the configured dock apps, before the alphabetical grid.
            boolean ca = a.kind == Entry.CARD, cb = b.kind == Entry.CARD;
            if (ca != cb) return ca ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        // Retain the optional Default-home tile until dependent tile-ordering work is merged.
        if (showHomeSettings
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(new Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings), null, 0));
        }
        docked = 0;
        while (docked < fresh.size() && isDocked(fresh.get(docked))) docked++;
        emptySlots = dockRow && !reordering ? DockSlots.empty(docked, dockSize, fresh.size(), fillDock) : 0;
        for (int i = 0; i < emptySlots; i++) {
            fresh.add(docked + i, new Entry(Entry.EMPTY, "", new ComponentName(ctx, "empty-dock-slot-" + i),
                    null, null, null, 0));
        }
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
    }

    /** A banner_* drawable bundled with the launcher, or null for none or a name that is not one. */
    private Drawable bundledBanner(String name) {
        if (name == null || !name.startsWith("banner_")) return null;
        int id = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
        return id != 0 ? ctx.getDrawable(id) : null;
    }

    private Drawable loadBanner(PackageManager pm, ResolveInfo ri) {
        int id = ri.activityInfo.banner;
        if (id == 0) id = ri.activityInfo.applicationInfo.banner;
        Drawable banner = null;
        if (id != 0) {
            try {
                Resources resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                // Keep at least xhdpi detail rather than downsampling artwork before the focus zoom.
                int density = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.getResources().getDisplayMetrics().densityDpi);
                banner = resources.getDrawableForDensity(id, density, null);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            }
        }
        if (banner == null) banner = ri.activityInfo.loadBanner(pm);
        // A square "banner" counts as none: the tile shows the app's icon and name instead of a blur.
        return banner != null && isBannerShaped(banner.getIntrinsicWidth(), banner.getIntrinsicHeight()) ? banner : null;
    }

    /**
     * Some apps declare a small square image as their banner (Rockchip's Media Center ships a 64 px one). Cropped
     * over a 16:9 tile it is a blur. A drawable with no intrinsic size (a colour, a shape) still fills the tile.
     */
    static boolean isBannerShaped(int width, int height) {
        return width <= 0 || height <= 0 || width >= height * 1.4f;
    }

    /** Lets go of every tile and its artwork (Home is in the background and memory is short). */
    void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 1) {
            View row = LayoutInflater.from(ctx).inflate(R.layout.row_app_library, parent, false);
            View button = row.findViewById(R.id.btnLibrary);
            button.setOnClickListener(v -> listener.onLibraryRequested());
            button.setOnFocusChangeListener((v, focused) -> { if (focused) listener.onLibraryFocused(); });
            button.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_UP) listener.onLibraryUp();
                return key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                        || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
            });
            return new RecyclerView.ViewHolder(row) { };
        }
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, parent.getHeight(), dockSize, rowsBelowDock, dockTitle, dockRow, density), h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round((dockRow ? 7 : 4) * density);
        if (!dockRow) {
            // Compact spacing (COMPACT_ITEM_EXTRA_DP): 4dp item padding and a 20dp name instead of 8dp and 22dp.
            nameLp.height = Math.round(20 * density);
            int pad = Math.round(4 * density);
            v.setPadding(v.getPaddingLeft(), pad, v.getPaddingRight(), pad);
        }
        name.setLayoutParams(nameLp);
        // A sideways row would stretch a match_parent item across the whole screen: give it a grid column's width.
        if (singleRow) v.getLayoutParams().width = inner / dockSize;
        if (uniformTiles) {
            // tvOS-style tile: a large centred mark, no caption inside the card.
            View icon = v.findViewById(R.id.icon);
            FrameLayout.LayoutParams iconLp = (FrameLayout.LayoutParams) icon.getLayoutParams();
            // square_tiles: a larger logo; their marks carry no plate, so they need the room to read at a distance.
            iconLp.width = iconLp.height = Math.round(h * (squareTiles ? 0.78f : ICON_SHARE));
            iconLp.bottomMargin = 0;
            icon.setLayoutParams(iconLp);
        } else if (h < Math.round(DESIGN_H_DP * density)) {
            // Shrunk tiles (e.g. 5 or 6 columns): scale icon and bottom margin proportionally with tile height so the label does not overlap the icon.
            View icon = v.findViewById(R.id.icon);
            FrameLayout.LayoutParams iconLp = (FrameLayout.LayoutParams) icon.getLayoutParams();
            float scale = (float) h / (DESIGN_H_DP * density);
            iconLp.width = iconLp.height = Math.round(48f * density * scale);
            iconLp.bottomMargin = Math.round(14f * density * scale);
            icon.setLayoutParams(iconLp);
            View label = v.findViewById(R.id.label);
            label.setPadding(label.getPaddingLeft(), label.getPaddingTop(), label.getPaddingRight(), Math.round(4f * density));
        }
        if (squareTiles) v.setBackground(new TileShadow(v.findViewById(R.id.card), density, focusScale()));
        return new Holder(v, listener, animations, advancedEffects, reducedMotion, restNameAlpha, focusOvershoot, focusScale());
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (isLibraryAt(position)) return;
        Holder h = (Holder) holder;
        Entry e = items.get(position);
        if (e.kind == Entry.EMPTY) {
            // A free dock slot keeps its place in the row and the band, but shows nothing and takes no focus.
            h.card.setVisibility(View.INVISIBLE);
            h.card.setFocusable(false);
            h.name.setVisibility(View.INVISIBLE);
            h.banner.setImageDrawable(null);
            h.icon.setImageDrawable(null);
            h.card.setOnClickListener(null);
            h.card.setOnLongClickListener(null);
            return;
        }
        h.card.setVisibility(View.VISIBLE);
        h.card.setFocusable(true);
        // square_tiles: the soft shadow under each tile (TileShadow) follows the tile's zoom; redraw it for this state.
        if (h.itemView.getBackground() instanceof TileShadow) h.itemView.invalidate();
        h.name.setText(e.name);
        // The dock has no labels, unless always_show_names asks for them (DockDecoration then makes room in the band).
        // In a single row every tile is the dock's kind: the big title above names it.
        boolean dockKind = dockRow && (singleRow || position < dockSize);
        // With dock_title off nothing else names the focused app, so its own name fades in under it.
        h.name.setVisibility(dockKind && restNameAlpha == 0f && dockTitle ? View.INVISIBLE : View.VISIBLE);
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        h.name.setAlpha(focused ? 1f : restNameAlpha);
        h.card.setScaleX(focused && animations ? focusScale() : 1f);
        h.card.setScaleY(focused && animations ? focusScale() : 1f);
        h.card.setTranslationY(focused && advancedEffects ? -4f * ctx.getResources().getDisplayMetrics().density : 0f);
        h.card.setTranslationZ(focused && advancedEffects ? 8f * ctx.getResources().getDisplayMetrics().density : 0f);
        h.banner.setScaleX(focused && advancedEffects ? 1.035f : 1f);
        h.banner.setScaleY(focused && advancedEffects ? 1.035f : 1f);
        // A fully opaque banner covers the whole card (centerCrop), so the card's own fill under it is drawn for
        // nothing on every frame; keep it under banners with transparent parts and under icon tiles.
        h.card.setBackground(e.banner != null && e.banner.getOpacity() == PixelFormat.OPAQUE ? null : h.cardBackground);
        if (uniformTiles && e.banner == null) {
            if (e.backdrop == null) {
                int[] plate = iconPlate(e.icon);
                if (tileAccent) e.accent = accentFor(plate, e.icon);
                if (clearGlass) {
                    Drawable own = customIcons ? ThemeIcons.forPackage(ctx, e.pkg) : null;
                    e.mark = own != null ? own : whiteMark(e.icon, h.card.getResources(), !whiteLogos);
                    e.backdrop = smokyGlass(plate, e.icon);
                } else if (whiteMarks) {
                    e.mark = whiteMark(e.icon, h.card.getResources(), false);
                    e.backdrop = brandGlass(plate, e.icon);
                } else {
                    // The card takes the icon's plate colour, so drawing only the mark lets the glass show around it
                    // instead of an opaque square of the same colour in the middle of a see-through tile.
                    e.mark = plate != null ? markOf(e.icon) : null;
                    e.backdrop = plate != null ? glassTint(plate) : backdrop(e.icon, ambientColorAt(position));
                }
            }
            if (frostTiles) h.card.setBackground(frostTile(h, e.accent));
            else h.card.setBackground(glassTile(clearGlass ? currentGlass() : e.backdrop, h.card.getLayoutParams().width,
                    h.card.getResources(),
                    whiteMarks ? GradientDrawable.Orientation.TL_BR : GradientDrawable.Orientation.TOP_BOTTOM));
        }
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(uniformTiles && e.mark != null ? e.mark : e.icon);
            if (squareTiles) h.setLogo(h.card.isFocused() ? 255 : RESTING_LOGO_LEVEL, false);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(uniformTiles ? View.GONE : View.VISIBLE);
        }
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            if (tileMenu) {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) listener.onTileLongPress(pos, v);
                return true;
            }
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        });
    }

    /** Opens the tile's activity by explicit component, with no extras: a card can name nothing else. */
    private void launch(Entry e) {
        if (e.kind == Entry.HOME_SETTINGS) {
            ctx.startActivity(new Intent(ctx, HomeSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        // TV apps launch with the same leanback intent as from the stock launcher or the remote, so an app
        // already running resumes instead of restarting; only phone apps added from the library use plain MAIN.
        if (leanbackPackages.contains(e.pkg)) i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        String action = launchActions.get(e.pkg);
        if (action != null) {
            // NEW_TASK only: with RESET_TASK_IF_NEEDED, Android just brings a running task of the app to the front
            // ("Activity not started"), so the screen never opens while the app is up, e.g. MagentaTV's boot-time task.
            Intent byAction = new Intent(action).setPackage(e.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (ctx.getPackageManager().resolveActivity(byAction, 0) != null) i = byAction;
        }
        try {
            ctx.startActivity(i);
            if (cinematic) LastOpened.record(ctx, e.pkg);
            if (advancedEffects && !reducedMotion && ctx instanceof Activity) {
                ((Activity) ctx).overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
            }
        } catch (Exception ex) {
            GlassToast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + (showLibrary ? 1 : 0);
    }

    int appCount() { return items.size(); }

    boolean isLibraryAt(int position) { return showLibrary && position == items.size(); }

    @Override public int getItemViewType(int position) { return isLibraryAt(position) ? 1 : 0; }

    boolean containsPackage(String pkg) {
        for (Entry entry : items) if (entry.pkg.equals(pkg)) return true;
        return false;
    }

    @Override
    public long getItemId(int position) {
        return isLibraryAt(position) ? Long.MIN_VALUE : items.get(position).component.flattenToShortString().hashCode();
    }

    /** Uses this saved order and dock size from the next reload() (Order.ranks, Order.docked). */
    void setSavedOrder(Map<String, Integer> ranks, int docked) {
        savedRanks = ranks;
        savedDocked = docked;
    }

    /**
     * A tile in the dock: one of the first savedDocked tiles of the saved order when one was recorded, so an app
     * the owner moved into the dock stays there (#499); else a dock_apps entry.
     */
    private boolean isDocked(Entry e) {
        if (savedDocked < 0) return favourites.contains(e.pkg);
        Integer rank = savedRanks.get(e.component.flattenToShortString());
        return rank != null && rank < savedDocked;
    }

    /** Reorder mode on or off; takes effect at the next reload() (empty dock slots are left out while on). */
    void setReordering(boolean on) {
        reordering = on;
    }

    boolean hasEmptySlots() {
        return emptySlots > 0;
    }

    /** Tiles that can be moved: everything but the "Default home" tile, which always stays last. */
    int movableCount() {
        int n = items.size();
        return n > 0 && items.get(n - 1).kind == Entry.HOME_SETTINGS ? n - 1 : n;
    }

    /** Keys of the movable tiles in their current order, for Order (empty dock slots are not tiles). */
    List<String> movableKeys() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < movableCount(); i++) {
            Entry e = items.get(i);
            if (e.kind != Entry.EMPTY) keys.add(e.component.flattenToShortString());
        }
        return keys;
    }

    /** Moves one tile; the others shift by one. Stable ids keep the moved tile's view (and its focus). */
    void move(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= movableCount() || to >= movableCount()) return;
        items.add(to, items.remove(from));
        notifyItemMoved(from, to);
        // The dock row hides names by position: rebind the tiles whose side of the dock edge changed.
        if (from < dockSize || to < dockSize) {
            int lo = Math.min(from, to), hi = Math.max(from, to);
            notifyItemRangeChanged(lo, hi - lo + 1);
        }
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component.flattenToShortString() : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    /** Dock slots that hold an app: the whole dock unless some slots are empty. */
    int dockFilled() {
        return emptySlots > 0 ? docked : dockSize;
    }

    /** A position focus can land on: an empty dock slot gives the last dock app. */
    int focusablePosition(int position) {
        return DockSlots.focusable(position, docked, emptySlots);
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    String packageAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).pkg : null;
    }

    /** The tile's artwork, its banner or else its icon, for the cinematic shelf's backdrop (HeroArt). */
    Drawable artworkAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        Entry e = items.get(position);
        return e.banner != null ? e.banner : e.icon;
    }

    float focusScale() {
        if (!animations) return 1f;
        if (focusZoom > 0) return focusZoom / 100f;
        return advancedEffects ? 1.12f : 1.10f;
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry; a card's accent when it set one. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) {
            e.ambient = uniformTiles && e.banner == null ? dominantColor(e.icon) : vividColor(e.banner != null ? e.banner : e.icon);
        }
        return e.ambient;
    }

    /** Share of the tile height the icon takes in uniform_tiles mode (onCreateViewHolder). */
    private static final float ICON_SHARE = 0.62f;

    /**
     * The colour covering most of an icon, for a card that should read as that app: a small saturated badge
     * (a red "new" corner on a blue icon) must not win, as it can with {@link #vividColor}. Grey if none.
     */
    private static int dominantColor(Drawable live) {
        if (live == null) return 0xFF6E6E73;
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 24;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, n, n);
        d.draw(new Canvas(bmp));
        d.setBounds(saved);
        float[] weight = new float[12];
        float[][] sum = new float[12][3];
        float[] hsv = new float[3];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                if (hsv[1] < 0.3f || hsv[2] < 0.25f) continue;   // white, grey and black carry no hue
                int bin = (int) (hsv[0] / 30f) % 12;
                float w = hsv[1] * hsv[2];
                weight[bin] += w;
                sum[bin][0] += Color.red(p) * w;
                sum[bin][1] += Color.green(p) * w;
                sum[bin][2] += Color.blue(p) * w;
            }
        }
        bmp.recycle();
        int best = 0;
        for (int i = 1; i < 12; i++) if (weight[i] > weight[best]) best = i;
        if (weight[best] < n * n * 0.03f) return 0xFF6E6E73;   // a monochrome icon, like a grey gear
        float w = weight[best];
        return Color.rgb((int) (sum[best][0] / w), (int) (sum[best][1] / w), (int) (sum[best][2] / w));
    }

    /**
     * The uniform tile's fill for an icon with no plate of its own (onBindViewHolder takes the plate's colour when
     * it has one): a gradient of the icon's main colour, deepened so the mark stands out, at glass strength.
     */
    private static int[] backdrop(Drawable live, int fallback) {
        float[] hsv = new float[3];
        Color.colorToHSV(fallback, hsv);
        // Yellow darkened turns olive: lean it to amber instead.
        if (hsv[0] > 42f && hsv[0] < 70f && hsv[1] > 0.3f) hsv[0] = 36f;
        hsv[2] = Math.min(hsv[2], 0.62f);
        int top = Color.HSVToColor(hsv);
        hsv[2] *= 0.6f;
        return glassTint(new int[]{top, Color.HSVToColor(hsv)});
    }

    /** How much of the tint a glass tile keeps: the wallpaper shows through the rest (0x99 = 60 %). */
    private static final int GLASS_TINT_ALPHA = 0x99;

    /** The colours at glass strength: a lighter top, a slightly clearer bottom, so the tile reads as lit from above. */
    private static int[] glassTint(int[] colours) {
        int[] out = new int[colours.length];
        for (int i = 0; i < colours.length; i++) {
            int alpha = i == 0 ? GLASS_TINT_ALPHA : GLASS_TINT_ALPHA - 0x14;
            out[i] = (colours[i] & 0x00FFFFFF) | (alpha << 24);
        }
        return out;
    }

    /**
     * A uniform tile's glass: the tint, a sheen where light catches the top, and a faint hairline round the edge
     * (far fainter than the focus rim). No blur: three flat gradients, cheap on a 1 GB TV.
     */
    private static Drawable glassTile(int[] tint, int widthPx, Resources res, GradientDrawable.Orientation direction) {
        float density = res.getDisplayMetrics().density;
        GradientDrawable body = new GradientDrawable(direction, tint);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33FFFFFF, 0x12FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(0);
        edge.setCornerRadius(widthPx > 0 ? cornerRadius(widthPx, density) : 16f * density);
        edge.setStroke(Math.max(1, Math.round(0.75f * density)), 0x47FFFFFF);
        return new LayerDrawable(new Drawable[]{body, sheen, edge});
    }

    /**
     * An adaptive icon's foreground alone, at the size it has inside the whole icon, or null for any other icon.
     * Drawn on a copy, so the icon itself (sampled for colours elsewhere) keeps its own layer bounds.
     */
    private static Drawable markOf(Drawable icon) {
        if (!(icon instanceof AdaptiveIconDrawable)) return null;
        Drawable copy = icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
        if (!(copy instanceof AdaptiveIconDrawable)) return null;
        Drawable foreground = ((AdaptiveIconDrawable) copy).getForeground();
        if (foreground == null) return null;
        // The whole icon draws each layer at 1.5 times its bounds, centred (108dp layers seen through 72dp).
        return new InsetDrawable(foreground, -AdaptiveIconDrawable.getExtraInsetFraction());
    }

    /** Size a white mark is rendered at, in px: sharper than the icon view at 1080p, and quick to make for every app. */
    private static final int MARK_PX = 192;

    /**
     * white_marks: the app's logo in white. An adaptive icon's foreground (its own background left out), or any other
     * icon whole with its plate keyed out by colour. Light parts stay fully white and darker ones a little clearer,
     * so detail such as a play triangle on a logo still reads. Null keeps the icon as it is: a picture-like icon with
     * no plate to key out would only turn into a white blob. Rendered once per app.
     */
    private static Drawable whiteMark(Drawable icon, Resources res, boolean keepColour) {
        if (icon == null) return null;
        Drawable foreground = markOf(icon);
        Drawable source = foreground != null ? foreground
                : icon.getConstantState() != null ? icon.getConstantState().newDrawable().mutate() : icon;
        int n = MARK_PX;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Rect saved = new Rect(source.getBounds());
        source.setBounds(0, 0, n, n);
        source.draw(new Canvas(bmp));
        source.setBounds(saved);
        int[] px = new int[n * n];
        bmp.getPixels(px, 0, n, 0, 0, n, n);
        // A plate still in the picture (a whole icon, or a foreground drawn on a background of its own) goes by its
        // colour, sampled as iconPlate does on two rows and graded between them.
        int rowA = Math.round(n * 0.16f), rowB = n - 1 - rowA;
        int plateA = plateColor(bmp, rowA, n), plateB = plateColor(bmp, rowB, n);
        boolean keyed = plateA != 0 && plateB != 0;
        if (!keyed && foreground == null) {
            int opaque = 0;
            for (int p : px) if (Color.alpha(p) > 200) opaque++;
            if (opaque > px.length * 0.55f) {
                bmp.recycle();
                return null;
            }
        }
        // A foreground layer is seen through the icon's rounded mask: anything it draws outside that (a long shadow,
        // a square backdrop) would show as a square edge on the glass, so the same rounded square is cut here.
        float half = n / 2f, corner = n * 0.3f, straight = half - corner;
        for (int y = 0; y < n; y++) {
            float t = Math.max(0f, Math.min(1f, (y - rowA) / (float) (rowB - rowA)));
            int plate = keyed ? mix(plateA, plateB, t) : 0;
            for (int x = 0; x < n; x++) {
                int i = y * n + x, p = px[i], a = Color.alpha(p);
                if (a == 0) continue;
                if (foreground != null) {
                    float dx = Math.max(0f, Math.abs(x + 0.5f - half) - straight);
                    float dy = Math.max(0f, Math.abs(y + 0.5f - half) - straight);
                    if (dx * dx + dy * dy > corner * corner) {
                        px[i] = 0;
                        continue;
                    }
                }
                int r = Color.red(p), g = Color.green(p), b = Color.blue(p);
                float keep = 1f;
                if (keyed) {
                    int d = Math.abs(r - Color.red(plate)) + Math.abs(g - Color.green(plate)) + Math.abs(b - Color.blue(plate));
                    keep = Math.max(0f, Math.min(1f, (d - 56f) / 60f));   // soft edge, so the mark keeps its anti-aliasing
                }
                float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
                float alpha = a / 255f;
                // Only the solid logo turns white. Faint pixels (a haze or backdrop behind the logo) and soft dark
                // ones (its drop shadow) would otherwise turn into a pale square behind it.
                float solid = Math.max(0f, Math.min(1f, (alpha - 0.4f) / 0.35f));
                if (lum < 0.3f && alpha < 0.7f) solid = 0f;
                px[i] = keepColour
                        ? Math.round(255 * solid * keep) << 24 | (p & 0xFFFFFF)
                        : Math.round(255 * solid * keep * (0.72f + 0.28f * lum)) << 24 | 0xFFFFFF;
            }
        }
        bmp.setPixels(px, 0, n, 0, 0, n, n);
        return new BitmapDrawable(res, bmp);
    }

    /** dominantColor's answer for an icon with no colour of its own, and the frosted grey such a tile gets instead. */
    private static final int NO_COLOUR = 0xFF6E6E73, NEUTRAL_GLASS = 0xFF9EA4AE;   // a cool silver

    /**
     * white_marks: the tile's glass in the app's brand colour, lighter at the top left and deeper, turned a little
     * round the colour wheel, at the bottom right. The plate's colour when it is a real colour; a white, grey or black
     * plate, or none, gives the logo's own main colour, so a white mark never sits on white.
     */
    /**
     * clear_glass: smoky glass, nearly opaque dark at the top and lighter towards the lower edge, as on an ambient
     * home screen. Opaque enough that the logo reads over a bright wallpaper.
     */
    private static int[] smokyGlass(int[] plate, Drawable icon) {
        // One cold glass for every tile, matching the theme's frosty blue-hour wallpapers: per-app brand colours
        // turned red and pink logos' tiles warm, which clashed with them. Navy at the top to steel blue at the bottom.
        return COLD_GLASS.clone();
    }

    /** frost_tiles: each tile is frosted glass over the wallpaper behind it (FrostDrawable), the picture's copy here. */
    private final boolean frostTiles;
    /** tile_accent (with frost_tiles): each tile's lower part in the app's own colour (accentFor). */
    private final boolean tileAccent;
    /** white_logos (with clear_glass): every logo as one white mark (whiteMark without keepColour). */
    private final boolean whiteLogos;
    /** custom_icons: the theme's own logo for an app when it has one (ThemeIcons). */
    private final boolean customIcons;
    private android.graphics.Bitmap frost;
    /** The grid this adapter feeds: its tiles get each new frost, and redraw it as the row scrolls. */
    private RecyclerView attached;
    private final RecyclerView.OnScrollListener refrost = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
            for (int i = 0; i < rv.getChildCount(); i++) {
                View card = rv.getChildAt(i).findViewById(R.id.card);
                if (card != null) card.invalidate();
            }
        }
    };

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView rv) {
        attached = rv;
        if (frostTiles) rv.addOnScrollListener(refrost);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView rv) {
        rv.removeOnScrollListener(refrost);
        attached = null;
    }

    /** frost_tiles: the tiles frost from this picture now, cross-fading from the last one. */
    void setFrost(android.graphics.Bitmap picture) {
        if (picture == null || picture == frost) return;
        frost = picture;
        if (attached == null) return;
        for (int i = 0; i < attached.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = attached.getChildViewHolder(attached.getChildAt(i));
            if (vh instanceof Holder && ((Holder) vh).frost != null) ((Holder) vh).frost.setFrost(picture, animations);
        }
    }

    /**
     * frost_tiles: the tile's glass. The frosted wallpaper behind it, already lighter and more colourful than the
     * picture (FrostTone), with tile_accent the app's colour washing up from the bottom, a faint gloss along the top
     * and the fine edge. No darkening layer: on the core sample the glass is lighter than what is behind it.
     */
    private Drawable frostTile(Holder h, int accent) {
        if (h.frost == null) h.frost = new FrostDrawable(h.card);
        h.frost.setFrost(frost, false);
        float density = h.card.getResources().getDisplayMetrics().density;
        int widthPx = h.card.getLayoutParams().width;
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x1AFFFFFF, 0x08FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(0);
        edge.setCornerRadius(widthPx > 0 ? cornerRadius(widthPx, density) : 16f * density);
        edge.setStroke(Math.max(1, Math.round(0.75f * density)), 0x5CFFFFFF);
        if (accent == 0) return new LayerDrawable(new Drawable[]{h.frost, sheen, edge});
        // Clear down to 40-60 % of the height, then the app's colour up to its full (about 65 %) strength at the
        // bottom, as measured on the sample. GradientDrawable spaces its stops evenly (positions need API 29).
        int rgb = accent & 0xFFFFFF, a = accent >>> 24;
        GradientDrawable wash = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                rgb, rgb, rgb, Math.round(a * 0.35f) << 24 | rgb, Math.round(a * 0.85f) << 24 | rgb, a << 24 | rgb});
        return new LayerDrawable(new Drawable[]{h.frost, wash, sheen, edge});
    }

    /** tile_accent's wash for an app with no colour of its own: on the sample, Apple TV's glass only turns lighter. */
    private static final int ACCENT_NEUTRAL = 0x40FFFFFF;

    /**
     * tile_accent: the colour a frosted tile's lower part takes. On the core sample every tile's glass is the same
     * blue at the top and turns to the app's colour towards the bottom (Netflix's ends magenta, Hulu's teal). The
     * plate's colour when it is a real colour, else the logo's main colour, brightened and a little softened, at about 65 %
     * strength; a faint white for an app with no colour.
     */
    private static int accentFor(int[] plate, Drawable icon) {
        float[] hsv = new float[3];
        int base = 0;
        if (plate != null) {
            int middle = mix(plate[0], plate[1], 0.5f);
            Color.colorToHSV(middle, hsv);
            if (hsv[1] >= 0.25f && hsv[2] >= 0.22f) base = middle;
        }
        if (base == 0) base = dominantColor(icon);
        if (base == NO_COLOUR) return ACCENT_NEUTRAL;
        Color.colorToHSV(base, hsv);
        hsv[1] = Math.min(hsv[1], 0.85f);
        hsv[2] = Math.max(hsv[2], 0.72f);
        return Color.HSVToColor(0xA6, hsv);
    }

    /** wallpaper_tint_tiles: the glass in the current wallpaper's colour (WallpaperPlayer.glassFor); null for the cold one. */
    private int[] wallpaperGlass;
    /** A rebind that only changes the tiles' glass colour (setGlassTint). */
    private static final Object TINT = new Object();

    private int[] currentGlass() {
        return wallpaperGlass != null ? wallpaperGlass : COLD_GLASS;
    }

    /** wallpaper_tint_tiles: every tile's glass to this picture's colour, cross-fading (TINT rebind, no full bind). */
    void setGlassTint(int[] tint) {
        if (tint == null || java.util.Arrays.equals(tint, wallpaperGlass)) return;
        wallpaperGlass = tint;
        if (clearGlass) notifyItemRangeChanged(0, getItemCount(), TINT);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(TINT) && frostTiles) return;   // frost tiles take the picture itself, not its tint
        if (payloads.contains(TINT) && holder instanceof Holder && position < items.size()) {
            Holder h = (Holder) holder;
            Entry e = items.get(position);
            if (e.kind == Entry.EMPTY || !uniformTiles || e.banner != null) return;
            Drawable fresh = glassTile(currentGlass(), h.card.getLayoutParams().width, h.card.getResources(),
                    GradientDrawable.Orientation.TOP_BOTTOM);
            Drawable old = h.card.getBackground();
            if (old instanceof TransitionDrawable) old = ((TransitionDrawable) old).getDrawable(1);
            if (old == null || !animations) {
                h.card.setBackground(fresh);
                return;
            }
            // Along with the wallpaper's own crossfade, not a jump to the new colour.
            TransitionDrawable fade = new TransitionDrawable(new Drawable[]{old, fresh});
            fade.setCrossFadeEnabled(true);
            h.card.setBackground(fade);
            fade.startTransition(900);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    /** clear_glass: smoky navy at the top, slate through the middle, a cold steel blue along the lower edge. */
    private static final int[] COLD_GLASS = {0xDE0E1622, 0xD61C2A3E, 0xD1426080};

    private static int[] brandGlass(int[] plate, Drawable icon) {
        float[] hsv = new float[3];
        int base = 0;
        if (plate != null) {
            int middle = mix(plate[0], plate[1], 0.5f);
            Color.colorToHSV(middle, hsv);
            if (hsv[1] >= 0.25f && hsv[2] >= 0.22f) base = middle;
        }
        if (base == 0) base = dominantColor(icon);
        if (base == NO_COLOUR) base = NEUTRAL_GLASS;
        Color.colorToHSV(base, hsv);
        // Yellow deepened turns olive: lean it to amber instead.
        if (hsv[0] > 42f && hsv[0] < 70f && hsv[1] > 0.3f) hsv[0] = 36f;
        float h = hsv[0], s = hsv[1], v = hsv[2];
        // A hint of the colour on clear glass, not a coloured slab: half the saturation, and see-through enough that
        // the wallpaper still reads behind every tile.
        int light = Color.HSVToColor(0x80, new float[]{h, s * 0.5f, Math.min(0.78f, Math.max(0.45f, v * 0.95f + 0.1f))});
        int deep = Color.HSVToColor(0x8C, new float[]{(h + 12f) % 360f, Math.min(1f, s * 0.62f),
                Math.max(0.16f, Math.min(0.46f, v * 0.58f))});
        return new int[]{light, deep};
    }

    /**
     * The plate an icon's artwork sits on (a flat or vertically graded rounded square), carried out to the
     * card's top and bottom edges so the mark reads as filling the tile; null when the icon has no
     * opaque plate (a bare mark on transparency).
     */
    private static int[] iconPlate(Drawable live) {
        if (live == null) return null;
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 48;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, n, n);
        d.draw(new Canvas(bmp));
        d.setBounds(saved);
        // Just inside a rounded square's corners; one side may go a step further in, past a corner badge.
        // An icon padded on both sides is a small plate in the middle, which no fill would meet.
        int a = plateColor(bmp, 7, n), b = plateColor(bmp, n - 8, n);
        int topRow = 7, bottomRow = n - 8;
        if (a == 0 && b != 0) a = plateColor(bmp, topRow = 10, n);
        else if (b == 0 && a != 0) b = plateColor(bmp, bottomRow = n - 11, n);
        bmp.recycle();
        if (a == 0 || b == 0) return null;
        // The sampled rows sit at these heights of the tile; extend the line through them to its edges. A row
        // taken further in sits on artwork-side plate, so its slope is less certain: cap how far it reaches.
        float margin = (1f - ICON_SHARE) / 2f;
        float yA = margin + ICON_SHARE * topRow / n, yB = margin + ICON_SHARE * bottomRow / n;
        float t0 = -yA / (yB - yA), t1 = (1f - yA) / (yB - yA);
        if (topRow != 7 || bottomRow != n - 8) {
            t0 = Math.max(-0.35f, t0);
            t1 = Math.min(1.35f, t1);
        }
        return new int[]{mix(a, b, t0), mix(a, b, t1)};
    }

    /** {@link #plateColor(int[])} of row y, between the same insets as the row's distance from the edge. */
    private static int plateColor(Bitmap bmp, int y, int n) {
        int inset = Math.min(y, n - 1 - y);
        int[] row = new int[n - 2 * inset];
        bmp.getPixels(row, 0, row.length, inset, y, row.length, 1);
        return plateColor(row);
    }

    /** The colour most of a row shares, ignoring artwork that crosses it; 0 if the row is not mostly plate. */
    private static int plateColor(int[] row) {
        int[] r = new int[row.length], g = new int[row.length], b = new int[row.length];
        int opaque = 0;
        for (int p : row) {
            if (Color.alpha(p) < 240) continue;
            r[opaque] = Color.red(p);
            g[opaque] = Color.green(p);
            b[opaque] = Color.blue(p);
            opaque++;
        }
        if (opaque < row.length * 0.9f) return 0;
        int mr = median(r, opaque), mg = median(g, opaque), mb = median(b, opaque);
        long sr = 0, sg = 0, sb = 0;
        int close = 0;
        for (int i = 0; i < opaque; i++) {
            if (Math.abs(r[i] - mr) + Math.abs(g[i] - mg) + Math.abs(b[i] - mb) >= 48) continue;
            sr += r[i];
            sg += g[i];
            sb += b[i];
            close++;
        }
        if (close < row.length * 0.7f) return 0;
        return Color.rgb((int) (sr / close), (int) (sg / close), (int) (sb / close));
    }

    private static int median(int[] values, int count) {
        int[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        return sorted[count / 2];
    }

    /** a + (b - a) * t per channel, clamped; t outside 0..1 extrapolates. */
    private static int mix(int a, int b, float t) {
        return Color.rgb(clamp(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                clamp(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                clamp(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        int[] px = new int[n * n];
        bmp.getPixels(px, 0, n, 0, 0, n, n);
        bmp.recycle();
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int p : px) {
            int pr = (p >> 16) & 0xFF, pg = (p >> 8) & 0xFF, pb = p & 0xFF;
            r += pr;
            g += pg;
            b += pb;
            int max = Math.max(pr, Math.max(pg, pb));
            int min = Math.min(pr, Math.min(pg, pb));
            float sat = max == 0 ? 0f : (max - min) / (float) max;
            float val = max / 255f;
            float score = sat * Math.min(1f, val * 1.4f);   // saturated and not too dark
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    /** Corner radius for a tile of this width: 16dp at the design width, proportionally less when smaller. */
    static float cornerRadius(int tileWidthPx, float density) {
        if (squareTiles) return tileWidthPx * SQUARE_CORNER;
        return 16f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        /** item_app.xml's tile_bg: the dark card behind an icon tile, or behind a banner that has transparent parts. */
        final Drawable cardBackground;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;
        final boolean animations;
        final boolean advancedEffects;
        final boolean reducedMotion;
        /** Alpha of the name while the tile is not focused (always_show_names). */
        final float restNameAlpha;
        final boolean overshoot;
        /** frost_tiles: this tile's frosted backdrop, kept across binds. */
        FrostDrawable frost;
        /** square_tiles: the logo's brightness now (of 255), and its fade towards the next one. */
        private int logoLevel = 255;
        private android.animation.ValueAnimator logoFade;

        /**
         * The logo to full brightness (focused) or dimmed, fading over the zoom's time instead of switching on the
         * frame focus moves; at once when not animating (a rebind, animations off).
         */
        void setLogo(int level, boolean animate) {
            if (logoFade != null) logoFade.cancel();
            if (!animate || level == logoLevel) {
                logoLevel = level;
                icon.setColorFilter(logoFilter(level));
                return;
            }
            logoFade = android.animation.ValueAnimator.ofInt(logoLevel, level);
            logoFade.setDuration(level > logoLevel ? 220 : 260);
            logoFade.setInterpolator(new DecelerateInterpolator());
            logoFade.addUpdateListener(a -> {
                logoLevel = (int) a.getAnimatedValue();
                icon.setColorFilter(logoFilter(logoLevel));
            });
            logoFade.start();
        }

        Holder(View v, TileListener listener, boolean animations, boolean advancedEffects,
               boolean reducedMotion, float restNameAlpha, boolean overshoot, float focusScale) {
            super(v);
            card = v.findViewById(R.id.card);
            cardBackground = card.getBackground();
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);
            this.animations = animations;
            this.advancedEffects = advancedEffects;
            this.reducedMotion = reducedMotion;
            this.restNameAlpha = restNameAlpha;
            this.overshoot = overshoot;

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // 16dp at the design width, scaled down with the tile so small tiles keep the same shape.
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                    // square_tiles: the outline only clips the corners. Its elevation shadow would show through the
                    // glass as a dark square; TileShadow draws one outside the tile instead.
                    if (squareTiles) outline.setAlpha(0f);
                }
            });
            card.setClipToOutline(true);
            // square_tiles: TileShadow follows the zoom, so redraw the item's background on each frame of it.
            if (squareTiles) card.animate().setUpdateListener(a -> {
                itemView.invalidate();
                if (frost != null) card.invalidate();   // the frost follows the zoom: the glass magnifies what is behind
            });

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                if (squareTiles) setLogo(focused ? 255 : RESTING_LOGO_LEVEL, animations);
                view.animate().cancel();
                banner.animate().cancel();
                name.animate().cancel();
                float scale = focusScale;   // the adapter's focusScale(): the glass is fitted to the same zoom
                float s = focused ? scale : 1f;
                if (!animations) {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    view.setTranslationY(0f);
                    view.setTranslationZ(0f);
                    banner.setScaleX(1f);
                    banner.setScaleY(1f);
                    name.setAlpha(focused ? 1f : restNameAlpha);
                    name.setTranslationY(0f);
                } else if (reducedMotion) {
                    // A short zoom with no hardware layer: building a layer for so short an animation costs more
                    // frames than it saves. The name just appears instead of fading.
                    view.animate().scaleX(s).scaleY(s)
                            .translationY(focused && advancedEffects ? -4f * density : 0f)
                            .translationZ(focused && advancedEffects ? 8f * density : 0f)
                            .setDuration(focused ? Motion.REDUCED_MS : 60)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    name.setAlpha(focused ? 1f : restNameAlpha);
                    // Same place as the full motion's slide ends: the layout's -6dp is only its starting point, and
                    // left there the name overlaps the zoomed tile.
                    // Only on focus: snapping it back to -6dp while it fades out would jump it up over the tile.
                    if (focused) name.setTranslationY(density * 4f);
                } else {
                    view.animate().scaleX(s).scaleY(s)
                            .translationY(focused && advancedEffects ? -4f * density : 0f)
                            .translationZ(focused && advancedEffects ? 8f * density : 0f)
                            .setDuration(advancedEffects ? (focused ? 220 : 170) : (focused ? 260 : 180))
                            .setInterpolator(focused && !overshoot ? new DecelerateInterpolator(1.5f)
                                    : new OvershootInterpolator(focused ? (advancedEffects ? 0.7f : 1.6f) : 0f))
                            .withLayer().start();
                    if (advancedEffects) {
                        banner.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f)
                                .setDuration(280).start();
                    }
                    name.animate().alpha(focused ? 1f : restNameAlpha)
                            .translationY(focused ? density * 4f : -density * 6f)
                            .setDuration(220).start();
                }
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
