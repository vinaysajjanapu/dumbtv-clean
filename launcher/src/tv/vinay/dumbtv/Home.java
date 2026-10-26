package tv.vinay.dumbtv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Rect;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class Home extends Activity {
    /** How long Menu is held to open Home Settings while the child lock is on. */
    static final long UNLOCK_MS = 3000;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final List<View> chips = new ArrayList<>();
    private Config cfg;
    private TvInputManager tv;
    private int applied = -1, appsGeneration;
    private boolean resumed, landed, unlockOpened;
    private FrameLayout root;
    private ImageView wall;
    private View shade;
    private TextView clock, greeting, quote;
    private Row apps, inputs;
    private AllApps allApps;
    private Hold hold;
    private View appsChip;
    private ScrollView page;
    private Map<String, Long> recent = new HashMap<>();
    private Tile moving;
    private String wallKey;
    private boolean serviceFetch, serviceWarned;
    /**
     * Place in the current shuffled cycle. Saved, because this activity drops its state
     * ({@code stateNotNeeded}) and a fresh process would otherwise start at the first lines again.
     * The category is saved with it, so switching lists starts that list at the beginning.
     */
    private long quoteSeed;
    private int quoteStep;
    private int quotePeriod = -1;
    private String quoteCategory;
    /** A line has been shown before, so the next visit moves on instead of repeating it. */
    private boolean quoteStarted;
    /** This foreground visit already has its line. A second onResume must not skip ahead. */
    private boolean quoteOnScreen;
    private static final String QUOTE_PREFS = "quotes";
    private static final String QUOTE_SEED = "seed";
    private static final String QUOTE_STEP = "step";
    private static final String QUOTE_PERIOD = "period";
    private static final String QUOTE_CATEGORY = "category";

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Uri data = i.getData();
            String pkg = data == null ? null : data.getSchemeSpecificPart();
            boolean added = Intent.ACTION_PACKAGE_ADDED.equals(i.getAction())
                    && !i.getBooleanExtra(Intent.EXTRA_REPLACING, false);
            loadApps(pkg, added);
        }
    };

    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    private final TvInputManager.TvInputCallback inputWatch = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String id, int state) {
            loadInputs();
        }

        @Override
        public void onInputAdded(String id) {
            loadInputs();
        }

        @Override
        public void onInputRemoved(String id) {
            loadInputs();
        }

        @Override
        public void onInputUpdated(String id) {
            loadInputs();
        }
    };

    private final class Row {
        final String kind;
        final TextView title;
        final HorizontalScrollView scroll;
        final LinearLayout strip;
        final Grid grid;
        List<Item> all = new ArrayList<>();

        Row(String kind, String name, LinearLayout parent) {
            this.kind = kind;
            int edge = dp(56), bleed = dp(14);
            title = Ui.text(Home.this, 12, Ui.FAINT, Ui.MEDIUM);
            title.setText(name.toUpperCase(Locale.ROOT));
            title.setLetterSpacing(0.14f);
            title.setPadding(edge, dp(20), edge, dp(2));
            strip = new LinearLayout(Home.this);
            strip.setGravity(Gravity.CENTER_VERTICAL);
            strip.setClipChildren(false);
            strip.setClipToPadding(false);
            strip.setPadding(bleed, dp(12), bleed, dp(8));
            scroll = new HorizontalScrollView(Home.this) {
                /** Keeps the focused tile centered, except near either end of the row. */
                @Override
                protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                    if (getChildCount() == 0) return 0;
                    int range = Math.max(0, getChildAt(0).getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight()));
                    int target = Math.max(0, Math.min(range, r.centerX() - getWidth() / 2));
                    return target - getScrollX();
                }
            };
            scroll.setFocusable(false);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setClipChildren(false);
            scroll.setClipToPadding(false);
            scroll.setPadding(edge - bleed, 0, edge - bleed, 0);
            scroll.addView(strip, new FrameLayout.LayoutParams(WRAP, WRAP));
            grid = new Grid(Home.this);
            grid.setPadding(edge, dp(12), edge, dp(8));
            parent.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
            parent.addView(scroll, new LinearLayout.LayoutParams(MATCH, WRAP));
            parent.addView(grid, new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        /** Whichever container currently holds the tiles, following the layout setting. */
        ViewGroup tiles() {
            return "grid".equals(cfg.get(Config.LAYOUT)) ? grid : strip;
        }

        void setVisible(boolean on) {
            boolean gridMode = "grid".equals(cfg.get(Config.LAYOUT));
            title.setVisibility(on && title.getText().length() > 0 ? View.VISIBLE : View.GONE);
            scroll.setVisibility(on && !gridMode ? View.VISIBLE : View.GONE);
            grid.setVisibility(on && gridMode ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences quotes = getSharedPreferences(QUOTE_PREFS, MODE_PRIVATE);
        if (quotes.contains(QUOTE_SEED)) {
            quoteSeed = quotes.getLong(QUOTE_SEED, 1);
            quoteStep = quotes.getInt(QUOTE_STEP, 0);
            quotePeriod = quotes.getInt(QUOTE_PERIOD, -1);
            quoteCategory = quotes.getString(QUOTE_CATEGORY, Quotes.CALM);
            quoteStarted = true;
            if (quoteStep < 0) {
                quoteStep = 0;
            }
        } else {
            quoteSeed = System.nanoTime();
            if (quoteSeed == 0) quoteSeed = 1;
        }
        Future<List<Item>> catalog = io.submit(() -> Catalog.apps(this));
        cfg = Config.get(this);
        Future<Map<String, Long>> times = recentFor(Catalog.APPS) == null ? null
                : io.submit(() -> Recent.times(this));
        tv = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        setContentView(build());

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        Keeper.start(this);
        if (tv != null) tv.registerCallback(inputWatch, ui);
        firstApps(catalog, times);
        loadInputs();
    }

    /**
     * Waits for the app list and loads the art of the tiles that fit on screen, so the first
     * frame is complete. After a cold start that frame is what people see first: loading in the
     * background drew the Inputs row alone, focused HDMI 1 for ~0.3 s, then popped the apps and
     * their banners in.
     */
    private void firstApps(Future<List<Item>> catalog, Future<Map<String, Long>> times) {
        try {
            apps.all = catalog.get();
            if (times != null) recent = times.get();
        } catch (ExecutionException e) {
            loadApps(null);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            loadApps(null);
            return;
        }
        int fit = getResources().getDisplayMetrics().widthPixels / (cardSize()[0] + dp(18)) + 1;
        for (Item it : Catalog.visible(cfg, Catalog.APPS, apps.all, recentFor(Catalog.APPS))) {
            if (fit-- <= 0) break;
            Catalog.art(this, it);
            it.artRequested = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Guard.arrived(this);
        cfg.refresh();
        cfg.visited();
        if (cfg.version() != applied) apply();
        else background();
        // The line turns over once when the user comes home. The wallpaper stays
        // until Get Another Picture or the rotation actually moves on.
        // A duplicate onResume while still visible must keep the current line.
        showQuote(quoteStarted && !quoteOnScreen);
        quoteOnScreen = true;
        refreshRecent();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(time, f);
        tick();
    }

    @Override
    public void onEnterAnimationComplete() {
        Guard.shown();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        allApps.dismiss(false);
        hold.dismiss(false);
        unregisterReceiver(time);
        quoteOnScreen = false;
        if (moving != null) stopMoving();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        if (tv != null) tv.unregisterCallback(inputWatch);
        io.shutdownNow();
        net.shutdownNow();
        super.onDestroy();
    }

    /**
     * Android 9 keeps a hidden window's GPU context (about 35 MB on this TV) unless memory is
     * critical. Dropping it while an app plays costs one context rebuild on the way back.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_COMPLETE) return;
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Object global = wmg.getMethod("getInstance").invoke(null);
            wmg.getMethod("trimMemory", int.class).invoke(global, TRIM_MEMORY_COMPLETE);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not reachable on this build: the context simply stays
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (moving != null) stopMoving();
        allApps.dismiss(false);
        hold.dismiss(false);
        if (resumed) focusFirst();
    }

    @Override
    public void onBackPressed() {
        if (hold.dismiss(true)) return;
        if (allApps.dismissPopup()) return;
        if (allApps.dismiss(true)) return;
        focusFirst();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (moving == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    shift(-1);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    shift(1);
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // In a grid, up and down move the tile a whole line; a strip has no lines.
                    if (moving.getParent() instanceof Grid) {
                        int cols = ((Grid) moving.getParent()).columns();
                        shift(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP ? -cols : cols);
                    }
                    break;
                default:
                    stopMoving();
            }
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU && hold.dismiss(true)) return true;
        if (code == KeyEvent.KEYCODE_MENU && allApps.dismissPopup()) return true;
        if (code == KeyEvent.KEYCODE_MENU && allApps.dismiss(true)) return true;
        if (code == KeyEvent.KEYCODE_MENU && locked()) {
            if (e.getRepeatCount() == 0) {
                unlockOpened = false;
            } else if (!unlockOpened && e.getEventTime() - e.getDownTime() >= UNLOCK_MS) {
                unlockOpened = true;
                startActivity(new Intent(this, Options.class));
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f instanceof Tile) menu((Tile) f);
            else startActivity(new Intent(this, Options.class));
            return true;
        }
        return super.onKeyDown(code, e);
    }

    /**
     * The scroll position for a focused rect {@code r}: the earliest of the top, the apps row or
     * the inputs row that still shows the row holding {@code r} whole.
     */
    private int rowTarget(ScrollView page, Rect r) {
        View content = page.getChildAt(0);
        List<int[]> rows = rowBlocks(content);
        int[] at = null;
        for (int[] b : rows) {
            if (r.centerY() >= b[0]) at = b;
        }
        if (at == null) return 0;
        int range = Math.max(0, content.getHeight() - page.getHeight());
        return Math.max(0, Math.min(range, stopFor(at, rows, page.getHeight())));
    }

    /**
     * Title top and strip bottom of each visible row, in {@code content}'s coordinates, listed
     * top to bottom. That order is what {@link #stopFor} searches. In grid mode every line of
     * tiles is its own block (the first one starting at the row title), so a grid taller than
     * the screen still scrolls a whole line at a time.
     */
    private List<int[]> rowBlocks(View content) {
        List<int[]> rows = new ArrayList<>();
        for (Row row : new Row[] {apps, inputs}) {
            if (row.scroll.getVisibility() == View.VISIBLE) {
                rows.add(new int[] {topIn(row.title, content), topIn(row.scroll, content) + row.scroll.getHeight()});
            } else if (row.grid.getVisibility() == View.VISIBLE) {
                int gridTop = topIn(row.grid, content), lastTop = Integer.MIN_VALUE;
                for (int i = 0; i < row.grid.getChildCount(); i++) {
                    View t = row.grid.getChildAt(i);
                    if (t.getTop() == lastTop) continue;
                    int start = lastTop == Integer.MIN_VALUE ? topIn(row.title, content) : gridTop + t.getTop() - dp(12);
                    lastTop = t.getTop();
                    rows.add(new int[] {start, gridTop + t.getBottom() + dp(8)});
                }
            }
        }
        return rows;
    }

    /** The earliest of the top or a row title that shows the whole of {@code at}. */
    private static int stopFor(int[] at, List<int[]> rows, int height) {
        if (height >= at[1]) return 0;
        for (int[] b : rows) {
            if (b[0] <= at[0] && b[0] + height >= at[1]) return b[0];
        }
        return at[0];
    }

    /**
     * When the page is taller than the screen, pads its bottom just enough that every row's stop
     * is reachable. Otherwise the last stop is clamped and the clock above it shows half cut.
     */
    private void fitBottom(ScrollView page) {
        View content = page.getChildAt(0);
        if (content == null || page.getHeight() == 0) return;
        int base = dp(32), height = page.getHeight();
        int natural = content.getHeight() - content.getPaddingBottom() + base;
        int want = base;
        if (natural > height) {
            int last = 0;
            List<int[]> rows = rowBlocks(content);
            for (int[] b : rows) last = Math.max(last, stopFor(b, rows, height));
            want = base + Math.max(0, last + height - natural);
        }
        if (content.getPaddingBottom() != want) {
            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(), want);
        }
    }

    /** {@code v}'s top edge in {@code content}'s coordinates. */
    private static int topIn(View v, View content) {
        int y = 0;
        while (v != null && v != content) {
            y += v.getTop();
            v = (View) v.getParent();
        }
        return y;
    }

    private View build() {
        root = new FrameLayout(this);
        wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wall, new FrameLayout.LayoutParams(MATCH, MATCH));
        shade = new View(this);
        root.addView(shade, new FrameLayout.LayoutParams(MATCH, MATCH));
        View fade = new View(this);
        // Lightens down the wallpaper toward the icons. The first stops stay clear so the
        // top of the photo is left alone and the fade is visible on the picture itself.
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00FFFFFF, 0x00FFFFFF, 0x0CFFFFFF, 0x19FFFFFF, 0x26FFFFFF, 0x33FFFFFF}));
        root.addView(fade, new FrameLayout.LayoutParams(MATCH, MATCH));

        page = new ScrollView(this) {
            /** Scrolls in whole rows: the focused row shows complete, and a row starts at the top edge. */
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                return rowTarget(this, r) - getScrollY();
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                fitBottom(this);
            }

            /**
             * Up and down only move the focus; scrolling follows it. Left to ScrollView, a press
             * with nothing focusable that way scrolls half a screen and leaves rows cut.
             */
            @Override
            public boolean executeKeyEvent(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                    case KeyEvent.KEYCODE_PAGE_UP:
                    case KeyEvent.KEYCODE_PAGE_DOWN:
                    case KeyEvent.KEYCODE_SPACE:
                        return false;
                    default:
                        return super.executeKeyEvent(e);
                }
            }
        };
        page.setFocusable(false);
        page.setFillViewport(true);
        page.setVerticalScrollBarEnabled(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page.setClipChildren(false);
        root.addView(page, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout column = new LinearLayout(this) {
            @Override
            public View focusSearch(View focused, int direction) {
                if (direction == View.FOCUS_DOWN && chips.contains(focused)) {
                    View below = nearest(apps.tiles(), focused);
                    if (below != null) return below;
                }
                return super.focusSearch(focused, direction);
            }
        };
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(0, dp(28), 0, dp(36));
        page.addView(column, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout head = new LinearLayout(this);
        head.setClipChildren(false);
        head.setClipToPadding(false);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(56), 0, dp(48), 0);
        clock = Ui.text(this, 52, Ui.TEXT, Ui.LIGHT);
        head.addView(clock);
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        appsChip = chip(R.drawable.ic_apps, "All Apps", v -> allApps.show(apps.all, cfg));
        head.addView(appsChip);
        head.addView(chip(R.drawable.ic_settings, "System Settings", v -> openSettings()));
        head.addView(chip(R.drawable.ic_options, "Home Settings", v -> startActivity(new Intent(this, Options.class))));
        column.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        column.addView(new View(this), new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout hello = new LinearLayout(this);
        hello.setOrientation(LinearLayout.VERTICAL);
        hello.setPadding(dp(56), 0, dp(80), 0);
        greeting = Ui.text(this, 18, Ui.MUTED, Ui.MEDIUM);
        greeting.setMaxLines(1);
        greeting.setEllipsize(android.text.TextUtils.TruncateAt.END);
        quote = Ui.text(this, 32, Ui.TEXT, Ui.LIGHT);
        quote.setMaxLines(2);
        quote.setEllipsize(android.text.TextUtils.TruncateAt.END);
        quote.setLineSpacing(0, 1.05f);
        hello.addView(greeting);
        LinearLayout.LayoutParams quoteLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        quoteLp.topMargin = dp(8);
        hello.addView(quote, quoteLp);
        column.addView(hello, new LinearLayout.LayoutParams(MATCH, WRAP));

        column.addView(new View(this), new LinearLayout.LayoutParams(MATCH, 0, 0.08f));

        apps = new Row(Catalog.APPS, "", column);
        inputs = new Row(Catalog.INPUTS, "Inputs", column);
        allApps = new AllApps(this, new AllApps.Host() {
            @Override
            public void open(Item item) {
                allApps.dismiss(false);
                focusApps();
                launch(new Intent(item.intent), item.id);
            }

            @Override
            public boolean onHome(Item item) {
                return Catalog.shown(cfg, Catalog.APPS, item);
            }

            @Override
            public void info(Item item) {
                allApps.dismiss(false);
                appInfo(item);
            }

            @Override
            public void favorite(Item item) {
                boolean onHome = Catalog.shown(cfg, Catalog.APPS, item);
                Catalog.setShown(cfg, Catalog.APPS, item, !onHome);
                render(apps);
                Toast.makeText(Home.this, onHome
                        ? "Removed from the home row."
                        : "Added to the home row.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void loadIcon(Item item, Runnable done) {
                io.execute(() -> {
                    Catalog.icon(getPackageManager(), item);
                    ui.post(() -> {
                        if (!isDestroyed()) done.run();
                    });
                });
            }

            @Override
            public void covered(boolean on) {
                // The list sits on top of the home screen. Blocking the page keeps d-pad focus
                // from landing on a tile underneath it. The hold menu uses the same flag, so
                // closing one must not uncover the page while the other is still up.
                page.setDescendantFocusability(on || hold != null && hold.isOpen()
                        ? ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        : ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }

            @Override
            public void closed() {
                if (!resumed) return;
                focusApps();
            }
        });
        root.addView(allApps, new FrameLayout.LayoutParams(MATCH, MATCH));
        hold = new Hold(this, on -> page.setDescendantFocusability(on || allApps.isOpen()
                ? ViewGroup.FOCUS_BLOCK_DESCENDANTS
                : ViewGroup.FOCUS_AFTER_DESCENDANTS));
        root.addView(hold, new FrameLayout.LayoutParams(MATCH, MATCH));
        return root;
    }

    /**
     * The child of {@code strip} horizontally closest to {@code from}, restricted to its top
     * visual row so a wrapped grid does not match a tile several rows further down.
     */
    private static View nearest(ViewGroup strip, View from) {
        int n = strip.getChildCount();
        if (n == 0) return null;
        int[] at = new int[2];
        from.getLocationOnScreen(at);
        int x = at[0] + from.getWidth() / 2;
        int[] ys = new int[n];
        int topY = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            strip.getChildAt(i).getLocationOnScreen(at);
            ys[i] = at[1];
            topY = Math.min(topY, at[1]);
        }
        View best = null;
        int bestGap = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (ys[i] != topY) continue;
            View c = strip.getChildAt(i);
            c.getLocationOnScreen(at);
            int gap = Math.abs(at[0] + c.getWidth() / 2 - x);
            if (gap < bestGap) {
                bestGap = gap;
                best = c;
            }
        }
        return best;
    }

    private View chip(int icon, String label, View.OnClickListener click) {
        int size = dp(36);
        int gap = dp(2);
        FrameLayout box = new FrameLayout(this) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
                getChildAt(0).measure(exact, exact);
                getChildAt(1).measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                setMeasuredDimension(size, size);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                getChildAt(0).layout(0, 0, size, size);
                View name = getChildAt(1);
                int nw = name.getMeasuredWidth(), nh = name.getMeasuredHeight();
                int x = (size - nw) / 2;
                name.layout(x, size + gap, x + nw, size + gap + nh);
            }
        };
        box.setClipChildren(false);
        box.setFocusable(true);
        box.setClickable(true);
        box.setContentDescription(label);
        box.setOnClickListener(click);

        ImageView v = new ImageView(this);
        v.setImageResource(icon);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setDuplicateParentStateEnabled(true);
        int inset = dp(8);
        v.setPadding(inset, inset, inset, inset);
        box.addView(v, new FrameLayout.LayoutParams(size, size));

        TextView name = Ui.text(this, 12, Ui.TEXT, Ui.MEDIUM);
        name.setText(label);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        name.setVisibility(View.INVISIBLE);
        box.addView(name, new FrameLayout.LayoutParams(WRAP, WRAP));

        box.setOnFocusChangeListener((view, has) -> {
            name.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(110).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.leftMargin = dp(2);
        box.setLayoutParams(lp);
        chips.add(box);
        return box;
    }

    private void apply() {
        applied = cfg.version();
        for (View c : chips) {
            Ui.ghostIcon((ImageView) ((ViewGroup) c).getChildAt(0), dp(18));
            c.setVisibility(locked() ? View.GONE : View.VISIBLE);
        }
        render(apps);
        render(inputs);
        background();
        tick();
    }

    /** Rebuilds the app list, keeping loaded art for every package except {@code changed}. */
    private void loadApps(String changed) {
        loadApps(changed, false);
    }

    private void loadApps(String changed, boolean added) {
        int generation = ++appsGeneration;
        io.execute(() -> {
            List<Item> list = Catalog.apps(this);
            ui.post(() -> {
                if (generation != appsGeneration || isDestroyed()) return;
                for (Item fresh : list) {
                    for (Item old : apps.all) {
                        if (old.id.equals(fresh.id) && !old.id.equals(changed) && old.artRequested) {
                            fresh.banner = old.banner;
                            fresh.icon = old.icon;
                            fresh.artRequested = true;
                            fresh.backgroundColor = old.backgroundColor;
                        }
                    }
                    if (added && fresh.id.equals(changed)) {
                        Catalog.setShown(cfg, Catalog.APPS, fresh, Config.autoAdd(cfg));
                    }
                }
                apps.all = list;
                render(apps);
                if (allApps.isOpen()) allApps.show(list, cfg);
            });
        });
    }

    private void loadInputs() {
        inputs.all = Catalog.inputs(this, tv);
        render(inputs);
    }

    /** The last-used times to sort apps by, or null unless Sort order is Recently used. */
    private Map<String, Long> recentFor(String kind) {
        return Catalog.APPS.equals(kind) && "recent".equals(cfg.get(Config.ORDER)) ? recent : null;
    }

    /** With Sort order › Recently used, re-sorts the apps row on the way back home. */
    private void refreshRecent() {
        if (recentFor(Catalog.APPS) == null) return;
        io.execute(() -> {
            Map<String, Long> times = Recent.times(this);
            ui.post(() -> {
                if (isDestroyed() || recentFor(Catalog.APPS) == null) return;
                List<Item> before = Catalog.visible(cfg, Catalog.APPS, apps.all, recent);
                recent = times;
                if (!before.equals(Catalog.visible(cfg, Catalog.APPS, apps.all, recent))) render(apps);
            });
        });
    }

    private boolean locked() {
        return "on".equals(cfg.get(Config.LOCK));
    }

    private void render(Row row) {
        if (hold != null && hold.isOpen()) hold.dismiss(false);
        if (moving != null && moving.kind.equals(row.kind)) stopMoving();
        View focused = getCurrentFocus();
        String keep = focused instanceof Tile && ((Tile) focused).kind.equals(row.kind)
                ? ((Tile) focused).item.id : null;
        boolean gridMode = "grid".equals(cfg.get(Config.LAYOUT));
        ViewGroup target = gridMode ? row.grid : row.strip;
        row.strip.removeAllViews();
        row.grid.removeAllViews();

        boolean enabled = !Catalog.INPUTS.equals(row.kind) || "on".equals(cfg.get(Config.INPUTS));
        List<Item> shown = enabled ? Catalog.visible(cfg, row.kind, row.all, recentFor(row.kind))
                : new ArrayList<>();
        int[] size = cardSize();
        int w = size[0], h = Catalog.APPS.equals(row.kind) ? size[1] : Math.round(size[1] * 0.56f);
        String labels = cfg.get(Config.LABELS);
        int accent = cfg.accent();
        int index = 0, restore = -1;
        for (Item it : shown) {
            Tile t = new Tile(this, it, row.kind, Catalog.label(cfg, it), w, h, labels, accent);
            t.setOnClickListener(v -> open((Tile) v));
            t.setOnLongClickListener(v -> {
                if (!locked()) menu((Tile) v);
                return true;
            });
            if (gridMode) {
                target.addView(t, new ViewGroup.LayoutParams(WRAP, WRAP));
            } else {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
                lp.rightMargin = dp(18);
                target.addView(t, lp);
            }
            if (it.id.equals(keep)) restore = index;
            index++;
            if (Catalog.APPS.equals(row.kind) && !it.artRequested) loadArt(it);
        }
        row.setVisible(!shown.isEmpty());
        if (restore >= 0) {
            target.getChildAt(restore).requestFocus();
        } else if (keep != null && target.getChildCount() > 0) {
            target.getChildAt(0).requestFocus();
        } else if (getCurrentFocus() == null || !landed) {
            focusFirst();
        }
    }

    private void loadArt(Item it) {
        it.artRequested = true;
        io.execute(() -> {
            Catalog.art(this, it);
            ui.post(() -> {
                ViewGroup tiles = apps.tiles();
                for (int i = 0; i < tiles.getChildCount(); i++) tiles.getChildAt(i).invalidate();
            });
        });
    }

    private int[] cardSize() {
        switch (cfg.get(Config.TILES)) {
            case "small":
                return new int[] {dp(144), dp(81)};
            case "large":
                return new int[] {dp(224), dp(126)};
            default:
                return new int[] {dp(180), dp(101)};
        }
    }

    private void focusFirst() {
        apps.scroll.scrollTo(0, 0);
        inputs.scroll.scrollTo(0, 0);
        View first = apps.tiles().getChildAt(0);
        if (first == null) first = inputs.tiles().getChildAt(0);
        landed = apps.tiles().getChildCount() > 0;
        if (first == null) first = chips.get(0);
        first.requestFocus();
    }

    private void tick() {
        String mode = cfg.get(Config.CLOCK);
        boolean h24 = "24h".equals(mode) || "auto".equals(mode) && DateFormat.is24HourFormat(this);
        Date now = new Date();
        if (h24) {
            clock.setText(DateFormat.format("HH:mm", now));
        } else {
            SpannableString half = new SpannableString(" " + DateFormat.format("a", now));
            half.setSpan(new RelativeSizeSpan(0.36f), 0, half.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            clock.setText(DateFormat.format("h:mm", now));
            clock.append(half);
        }
        Calendar day = Calendar.getInstance();
        int hour = day.get(Calendar.HOUR_OF_DAY);
        greeting.setText(Quotes.greeting(hour, cfg.get("name", ""),
                cfg.get(Config.GREETING), cfg.get(Config.GREETING_TEXT, "")));
        // Time of day keeps the line suitable if the screen stays open across a period change.
        // A custom line and the single-list categories are left where they are.
        String category = cfg.get(Config.WELCOME);
        if (Config.welcomeLine(cfg) && Quotes.byPeriod(category)
                && !Quotes.written(cfg.get(Config.WELCOME_TEXT, ""))
                && quotePeriod != Quotes.period(hour)) {
            showQuote(false);
        }
        // hourly and time-of-day rotations change while the screen stays open; a no-op otherwise
        background();
    }

    /** The next line when {@code advance} is set, otherwise the one already chosen for this visit. */
    private void showQuote(boolean advance) {
        if (quote == null) return;
        // Off hides the line and leaves the cycle where it is, so turning it back on resumes.
        if (!Config.welcomeLine(cfg)) {
            quote.setVisibility(View.GONE);
            return;
        }
        quote.setVisibility(View.VISIBLE);
        String category = cfg.get(Config.WELCOME);
        String custom = cfg.get(Config.WELCOME_TEXT, "");
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        // A written line stays put. The category cycle waits until that line is cleared.
        if (Quotes.written(custom)) {
            quote.setText(Quotes.welcome(category, custom, quoteSeed, quoteStep, hour));
            return;
        }
        int period = Quotes.period(category, hour);
        boolean categoryChanged = quoteCategory != null && !category.equals(quoteCategory);
        if (categoryChanged || quotePeriod != period) {
            quoteSeed = Quotes.nextSeed(category, quoteSeed, -1, hour);
            quoteStep = 0;
            quotePeriod = period;
        } else if (advance) {
            int last = Quotes.pick(category, quoteSeed, quoteStep, hour);
            quoteStep++;
            if (quoteStep >= Quotes.count(category, hour)) {
                quoteStep = 0;
                quoteSeed = Quotes.nextSeed(category, quoteSeed, last, hour);
            }
        }
        quoteCategory = category;
        quoteStarted = true;
        quote.setText(Quotes.line(category, quoteSeed, quoteStep, hour));
        getSharedPreferences(QUOTE_PREFS, MODE_PRIVATE).edit()
                .putLong(QUOTE_SEED, quoteSeed)
                .putInt(QUOTE_STEP, quoteStep)
                .putInt(QUOTE_PERIOD, quotePeriod)
                .putString(QUOTE_CATEGORY, quoteCategory)
                .apply();
    }

    private void background() {
        loadServiceWall();
        File file = Walls.shown(cfg);
        root.setBackgroundColor(Color.BLACK);
        int dim = Integer.parseInt(cfg.get(Config.DIM));
        shade.setBackgroundColor(file == null ? Color.TRANSPARENT : Color.argb(dim * 255 / 100, 0, 0, 0));
        String key = file == null ? null : file.getPath() + ":" + file.lastModified();
        if (key == null ? wallKey == null : key.equals(wallKey)) return;
        wallKey = key;
        if (file == null) {
            wall.setImageDrawable(null);
            return;
        }
        io.execute(() -> {
            Bitmap b = decode(file, getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
            ui.post(() -> {
                if (key.equals(wallKey)) wall.setImageBitmap(b);
            });
        });
    }

    /** Fetches a picture when this turn does not already have one saved. */
    private void loadServiceWall() {
        Walls.Need need = Walls.need(cfg);
        if (need == Walls.Need.KEEP || serviceFetch) return;
        String key = Walls.key(cfg);
        String query = Walls.query(cfg);
        String reuse = need == Walls.Need.REFILL ? cfg.get(Walls.URL, "") : null;
        serviceFetch = true;
        net.execute(() -> {
            String image = reuse != null
                    ? (Walls.store(reuse, Walls.file(cfg)) ? reuse : null)
                    : Walls.fetch(query, Walls.file(cfg));
            ui.post(() -> {
                serviceFetch = false;
                if (isDestroyed()) return;
                if (!key.equals(Walls.key(cfg))) {
                    loadServiceWall();
                    return;
                }
                if (image != null) {
                    serviceWarned = false;
                    remember(key, image);
                    background();
                } else if (Walls.shown(cfg) == null && !serviceWarned) {
                    serviceWarned = true;
                    Toast.makeText(this, "Couldn't load the wallpaper", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void remember(String key, String image) {
        if (!key.equals(cfg.get(Walls.KEY, ""))) cfg.set(Walls.KEY, key);
        if (!image.equals(cfg.get(Walls.URL, ""))) cfg.set(Walls.URL, image);
    }

    private static Bitmap decode(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) {
            o.inSampleSize *= 2;
        }
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** Closing All apps returns to its icon. Child lock hides that icon with the others. */
    private void focusApps() {
        if (appsChip.getVisibility() == View.VISIBLE) appsChip.requestFocus();
        else focusFirst();
    }

    /**
     * Fire OS answers the generic settings action with a stub that reopens Amazon's home, which
     * Guard then replaces with ours. The TV settings entry point opens the real settings screen.
     */
    private void openSettings() {
        Intent tvSettings = new Intent(Intent.ACTION_MAIN)
                .addCategory("android.intent.category.LEANBACK_SETTINGS");
        boolean found = tvSettings.resolveActivity(getPackageManager()) != null;
        launch(found ? tvSettings : new Intent(Settings.ACTION_SETTINGS), null);
    }

    private void open(Tile t) {
        if (Catalog.APPS.equals(t.kind)) {
            launch(new Intent(t.item.intent), t.item.id);
            return;
        }
        Intent own = Input.intent(this, cfg, t.item, Catalog.label(cfg, t.item));
        // The built-in screen goes on top of this one in the same task, so BACK returns here and
        // the HOME takeover's singleTask relaunch clears it. NEW_TASK | RESET_TASK_IF_NEEDED on an
        // activity of the task that is already in front only brings that task forward and starts
        // nothing (ActivityStarter.complyActivityFlags), so it is started plainly.
        if (own != null) startActivity(own);
        else launch(new Intent(t.item.intent), null);
    }

    /** The system App info screen: force stop, uninstall, storage, and the rest. */
    private void appInfo(Item item) {
        launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", item.id, null)), null);
    }

    private void launch(Intent i, String pkg) {
        // The store's screen is Amazon's home. Leave it up, as Exit to Amazon Home does.
        if (Guard.AMAZON.equals(pkg) || Guard.STORE.equals(pkg)) Guard.pause();
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            if (pkg != null) Recent.opened(this, pkg);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Couldn't open it", Toast.LENGTH_SHORT).show();
        }
    }

    private void menu(Tile t) {
        boolean app = Catalog.APPS.equals(t.kind);
        List<Hold.Action> actions = new ArrayList<>();
        actions.add(new Hold.Action(Hold.MOVE, R.drawable.ic_move, "Move"));
        actions.add(new Hold.Action(Hold.HIDE, R.drawable.ic_hide, "Hide"));
        if (app) actions.add(new Hold.Action(Hold.INFO, R.drawable.ic_info, "App Info"));
        hold.show(t, actions, action -> {
            switch (action) {
                case Hold.MOVE:
                    if (t.getParent() == null) break;
                    if (app && !"custom".equals(cfg.get(Config.ORDER))) {
                        Toast.makeText(this, "Sort Order is " + Config.ORDER.name(cfg.get(Config.ORDER))
                                + ". Set it to Custom in Home Settings to move apps.",
                                Toast.LENGTH_LONG).show();
                        break;
                    }
                    moving = t;
                    t.setMoving(true);
                    Toast.makeText(this,
                            "Press left or right to move it. Press Back when you are done.",
                            Toast.LENGTH_LONG).show();
                    break;
                case Hold.HIDE:
                    Catalog.setShown(cfg, t.kind, t.item, false);
                    render(app ? apps : inputs);
                    Toast.makeText(this, app
                            ? "Hidden from the home row. Turn it on again in Home Settings, under Apps."
                            : "Hidden from the inputs row. Turn it on again in Home Settings, under Inputs.",
                            Toast.LENGTH_LONG).show();
                    break;
                case Hold.INFO:
                    appInfo(t.item);
                    break;
                default:
                    break;
            }
        });
    }

    private void shift(int by) {
        ViewGroup strip = (ViewGroup) moving.getParent();
        int from = strip.indexOfChild(moving), to = from + by;
        if (to < 0 || to >= strip.getChildCount() || !(strip.getChildAt(to) instanceof Tile)) return;
        ViewGroup.LayoutParams lp = moving.getLayoutParams();
        strip.removeViewAt(from);
        strip.addView(moving, to, lp);
        moving.requestFocus();
    }

    private void stopMoving() {
        Tile t = moving;
        moving = null;
        t.setMoving(false);
        ViewGroup strip = (ViewGroup) t.getParent();
        if (strip == null) return;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < strip.getChildCount(); i++) {
            View child = strip.getChildAt(i);
            if (child instanceof Tile) ids.add(((Tile) child).item.id);
        }
        Catalog.saveOrder(cfg, t.kind, ids);
        applied = cfg.version();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
