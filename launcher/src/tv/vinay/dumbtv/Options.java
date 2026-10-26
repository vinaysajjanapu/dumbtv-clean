package tv.vinay.dumbtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The same shape as Fire TV settings: a tile grid, then a section in three panes.
 * The wallpaper is a 16:9 preview in the right pane, not the page background.
 */
public final class Options extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int FRONT = 0, WALL = 1, APPS = 2, INPUTS = 3, GREET = 4;
    private static final int PAGE = 0xFF0A0A0A;
    private static final int TILE_FILL = 0xFF3A3A3A;
    private static final int TILE_INK = 0xFFF0F0F0;
    /** Focused system tiles grow a little. The outline sits on that larger tile. */
    private static final float TILE_FOCUS = 1.07f;
    private static final int ROW_ON = 0xFFDCDCDC;
    /** Each settings pane is its own shade, light to dark, as on Fire TV. */
    private static final int PANE_LEFT_TOP = 0xFF48484A;
    private static final int PANE_LEFT_BOTTOM = 0xFF323234;
    private static final int PANE_MID = 0xFF2C2C2C;
    private static final int PANE_RIGHT = 0xFF1C1C1C;
    private static final int INK = 0xFF1A1A1A;
    private static final int INK_SOFT = 0xFF5C5C5C;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final List<Runnable> refreshers = new ArrayList<>();
    private final List<Runnable> appRefreshers = new ArrayList<>();
    private final List<Runnable> inputRefreshers = new ArrayList<>();

    private Config cfg;
    private FrameLayout root;
    private ImageView preview;
    private View previewShade;
    private View[] pages;
    private TextView nameView, wallNote, appsNote, inputsNote, greetNote, greetPreview, linePreview;
    private View nameTile, wallpaperTile, appsTile, inputsTile, greetingTile, amazonTile, returnTile;
    private View queryRow, orderRow, autoRow, inputsChoice, switchRow, greetChoice, greetCustom,
            welcomeChoice, welcomeCustom;
    private LinearLayout appList, inputList;
    private int screen = FRONT;
    private int appGeneration;
    private boolean named, fetching;
    private String wallKey;
    private Typeface emberRegular, emberMedium;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(PAGE);
        root.setClipChildren(false);
        FrameLayout stage = new FrameLayout(this);
        stage.setClipChildren(false);
        root.addView(stage, new FrameLayout.LayoutParams(MATCH, MATCH));

        pages = new View[] {buildFront(), buildWallpaper(), buildApps(), buildInputs(), buildGreeting()};
        for (View page : pages) stage.addView(page);
        setContentView(root);
        showScreen(FRONT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg.refresh();
        refresh();
        paint();
        fetchIfDue();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        net.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (screen != FRONT) {
            showScreen(FRONT);
            return;
        }
        super.onBackPressed();
    }

    private void showScreen(int next) {
        screen = next;
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == next ? View.VISIBLE : View.GONE);
        }
        root.setBackgroundColor(next == FRONT ? PAGE : PANE_RIGHT);
        if (next == FRONT) (returnTile != null ? returnTile : nameTile).requestFocus();
        else if (next == WALL) queryRow.requestFocus();
        else if (next == APPS) orderRow.requestFocus();
        else if (next == INPUTS) inputsChoice.requestFocus();
        else if (next == GREET) greetChoice.requestFocus();
    }

    private View buildFront() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(52), dp(28), dp(52), dp(28));
        page.setClipChildren(false);
        // A focused tile draws past its slot. The left one grows into this padding.
        page.setClipToPadding(false);
        page.setLayoutParams(new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView title = Ui.text(this, 14, TILE_INK, ember());
        title.setText("Home Settings");
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        titleLp.bottomMargin = dp(40);
        page.addView(title, titleLp);

        nameTile = tile(R.drawable.ic_tile_name, "Your Name", v -> editText("Your Name", "name", 32));
        nameView = (TextView) ((ViewGroup) nameTile).getChildAt(1);
        wallpaperTile = tile(R.drawable.ic_tile_wallpaper, "Wallpaper", v -> open(WALL, wallpaperTile));
        appsTile = tile(R.drawable.ic_tile_apps, "Apps", v -> openApps());
        inputsTile = tile(R.drawable.ic_tile_input, "Inputs", v -> openInputs());
        greetingTile = tile(R.drawable.ic_tile_greeting, "Greeting", v -> open(GREET, greetingTile));
        amazonTile = tile(R.drawable.ic_tile_exit, "Exit to Amazon Home", v -> {
            Guard.amazon(this);
            finish();
        });
        // Five across, the same count and tile size as Fire TV settings. Anything past that
        // wraps to the next row, left aligned, the way the system grid does.
        View[] tiles = {nameTile, wallpaperTile, appsTile, inputsTile, greetingTile, amazonTile};
        final int columns = 5;
        LinearLayout row = null;
        for (int i = 0; i < tiles.length; i++) {
            if (i % columns == 0) {
                row = tileRow(i == 0 ? 0 : dp(12));
                page.addView(row);
            }
            if (i % columns == columns - 1) {
                ((LinearLayout.LayoutParams) tiles[i].getLayoutParams()).rightMargin = 0;
            }
            row.addView(tiles[i]);
        }
        return page;
    }

    private void open(int page, View tile) {
        returnTile = tile;
        showScreen(page);
    }

    private View tile(int icon, String title, View.OnClickListener click) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOnClickListener(click);
        tile.setId(View.generateViewId());
        ImageView mark = new ImageView(this);
        mark.setImageResource(icon);
        mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        tile.addView(mark, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView label = Ui.text(this, 14, TILE_INK, ember());
        label.setText(title);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        labelLp.topMargin = dp(12);
        tile.addView(label, labelLp);
        tile.setOnFocusChangeListener((v, has) -> paintTile(v, has));
        paintTile(tile, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(161.5f), dp(133));
        lp.rightMargin = dp(12);
        tile.setLayoutParams(lp);
        return tile;
    }

    private View panes(int icon, String title, View list, View aside) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setLayoutParams(new FrameLayout.LayoutParams(MATCH, MATCH));
        View left = brand(icon, title);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {PANE_LEFT_TOP, PANE_LEFT_BOTTOM}));
        page.addView(left, new LinearLayout.LayoutParams(0, MATCH, 0.375f));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(PANE_MID);
        scroll.setFocusable(false);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            View focused = getCurrentFocus();
            if (focused != null) stickHelp(focused);
        });
        scroll.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        page.addView(scroll, new LinearLayout.LayoutParams(0, MATCH, 0.3125f));
        aside.setBackgroundColor(PANE_RIGHT);
        page.addView(aside, new LinearLayout.LayoutParams(0, MATCH, 0.3125f));
        return page;
    }

    private View brand(int icon, String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        ImageView mark = new ImageView(this);
        mark.setImageResource(icon);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(mark, new LinearLayout.LayoutParams(dp(84), dp(84)));
        TextView name = Ui.text(this, 24, 0xFFEBEBEB, emberMedium());
        name.setText(title);
        name.setGravity(Gravity.CENTER);
        name.setPadding(dp(24), 0, dp(24), 0);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        nameLp.topMargin = dp(40);
        box.addView(name, nameLp);
        return box;
    }

    private LinearLayout listColumn() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(141), 0, dp(48));
        return list;
    }

    private TextView helpText() {
        TextView note = Ui.text(this, 14, 0xFFECECEC, emberMedium());
        note.setLineSpacing(0, 1.3f);
        return note;
    }

    private View helpPane(TextView note) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(40), 0, dp(36), dp(24));
        box.addView(note, new LinearLayout.LayoutParams(MATCH, WRAP));
        return box;
    }

    private View buildWallpaper() {
        wallNote = helpText();
        Ratio frame = new Ratio(this);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(preview, new FrameLayout.LayoutParams(MATCH, MATCH));
        previewShade = new View(this);
        frame.addView(previewShade, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout aside = new LinearLayout(this);
        aside.setOrientation(LinearLayout.VERTICAL);
        aside.setPadding(dp(20), dp(184), dp(36), dp(24));
        aside.addView(frame, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        noteLp.topMargin = dp(16);
        aside.addView(wallNote, noteLp);

        LinearLayout list = listColumn();
        queryRow = textRow("Search Query", "provider.name", 48, Walls.DEFAULT_QUERY, wallNote,
                "The search query used to find a picture. Press OK to edit. Clear goes back to "
                        + Walls.DEFAULT_QUERY + ".");
        list.addView(queryRow);
        list.addView(choice(Config.DIM, wallNote, "How much the picture is darkened."));
        list.addView(choice(Config.ROTATE, wallNote, ""));
        list.addView(action("Get Another Picture", this::nextWallpaper, wallNote,
                "Shows a different picture now."));
        list.setPadding(0, dp(184), 0, dp(48));
        return panes(R.drawable.ic_tile_wallpaper, "Wallpaper", list, aside);
    }

    private View buildApps() {
        appsNote = helpText();
        LinearLayout list = listColumn();
        list.addView(section("Preferences", 0));
        orderRow = choice(Config.ORDER, appsNote,
                "How apps are arranged. Custom keeps the order you set with Move.");
        list.addView(orderRow);
        autoRow = choice(Config.AUTO, appsNote,
                "A newly installed app is added to the home row when this is On.");
        autoRow.setId(View.generateViewId());
        list.addView(autoRow);
        list.addView(section("Home Row", dp(18)));
        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        list.addView(appList, new LinearLayout.LayoutParams(MATCH, WRAP));
        return panes(R.drawable.ic_tile_apps, "Apps", list, helpPane(appsNote));
    }

    private void openApps() {
        returnTile = appsTile;
        clearAppRows();
        showScreen(APPS);
        int generation = ++appGeneration;
        io.execute(() -> {
            List<Item> items = Catalog.apps(this);
            List<Item> favorites = new ArrayList<>();
            List<Item> rest = new ArrayList<>();
            for (Item it : items) {
                (Catalog.shown(cfg, Catalog.APPS, it) ? favorites : rest).add(it);
            }
            Collator collator = Collator.getInstance();
            Collections.sort(favorites, (a, b) -> byName(collator, a, b));
            Collections.sort(rest, (a, b) -> byName(collator, a, b));
            favorites.addAll(rest);
            ui.post(() -> {
                if (generation != appGeneration || isDestroyed() || screen != APPS) return;
                if (favorites.isEmpty()) {
                    TextView empty = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
                    empty.setText("No apps installed");
                    empty.setPadding(dp(14), dp(8), dp(14), 0);
                    appList.addView(empty);
                    return;
                }
                for (Item it : favorites) appList.addView(favoriteRow(it, generation));
                linkFavorites();
            });
        });
    }

    private int byName(Collator collator, Item a, Item b) {
        return collator.compare(Catalog.label(cfg, a).toString(), Catalog.label(cfg, b).toString());
    }

    private View buildInputs() {
        inputsNote = helpText();
        LinearLayout list = listColumn();
        inputsChoice = choice(Config.INPUTS, inputsNote,
                "Shows HDMI and other inputs in a row on the home screen.");
        switchRow = choice(Config.SWITCH, inputsNote, "");
        list.addView(inputsChoice);
        list.addView(switchRow);
        inputList = new LinearLayout(this);
        inputList.setOrientation(LinearLayout.VERTICAL);
        list.addView(inputList, new LinearLayout.LayoutParams(MATCH, WRAP));
        return panes(R.drawable.ic_tile_input, "Inputs", list, helpPane(inputsNote));
    }

    private View buildGreeting() {
        greetNote = helpText();
        LinearLayout aside = new LinearLayout(this);
        aside.setOrientation(LinearLayout.VERTICAL);
        aside.setPadding(dp(40), dp(141), dp(36), dp(24));
        TextView previewLabel = Ui.text(this, 12, 0xFFECECEC, ember());
        previewLabel.setText("PREVIEW");
        previewLabel.setLetterSpacing(0.14f);
        aside.addView(previewLabel);
        greetPreview = Ui.text(this, 16, Ui.MUTED, ember());
        greetPreview.setMaxLines(2);
        greetPreview.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams greetLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        greetLp.topMargin = dp(14);
        aside.addView(greetPreview, greetLp);
        linePreview = Ui.text(this, 22, 0xFFEBEBEB, ember());
        linePreview.setMaxLines(4);
        linePreview.setEllipsize(TextUtils.TruncateAt.END);
        linePreview.setLineSpacing(0, 1.05f);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lineLp.topMargin = dp(8);
        aside.addView(linePreview, lineLp);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        noteLp.topMargin = dp(28);
        aside.addView(greetNote, noteLp);

        LinearLayout list = listColumn();
        list.addView(section("Greeting", 0));
        greetChoice = choice(Config.GREETING, greetNote,
                "Changes with the time of day and adds your name. Custom uses the words below.");
        list.addView(greetChoice);
        greetCustom = textRow("Custom Greeting", Config.GREETING_TEXT, 48, null, "Not Set", greetNote,
                "Replaces the time-of-day greeting, including your name. Clear it to use that greeting again.");
        list.addView(greetCustom);
        list.addView(welcomeSection());
        return panes(R.drawable.ic_tile_greeting, "Greeting", list, aside);
    }

    /** The category, the custom line and its switch. Hidden until {@link Config#WELCOME_LINE}. */
    private View welcomeSection() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(section("Welcome Line", dp(18)));
        block.addView(welcomeShowRow());
        welcomeChoice = choice(Config.WELCOME, greetNote,
                "The kind of line under the greeting. Press left or right to change it. A custom line replaces this.");
        block.addView(welcomeChoice);
        welcomeCustom = textRow("Custom Line", Config.WELCOME_TEXT, 80, null, "Not Set", greetNote,
                "Uses these words instead of the list. Clear them to use the style again.");
        block.addView(welcomeCustom);
        if (!Config.WELCOME_LINE) block.setVisibility(View.GONE);
        return block;
    }

    private LinearLayout tileRow(int top) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = top;
        row.setLayoutParams(lp);
        return row;
    }

    private void openInputs() {
        returnTile = inputsTile;
        clearRows(inputList, inputRefreshers);
        showScreen(INPUTS);
        android.media.tv.TvInputManager tv = (android.media.tv.TvInputManager)
                getSystemService(Context.TV_INPUT_SERVICE);
        List<Item> items = Catalog.inputs(this, tv);
        if (items.isEmpty()) {
            TextView empty = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
            empty.setText("No inputs found");
            empty.setPadding(dp(14), dp(8), dp(14), 0);
            inputList.addView(empty);
            return;
        }
        for (Item it : items) inputList.addView(toggleRow(it));
    }

    private void clearAppRows() {
        clearRows(appList, appRefreshers);
    }

    private void clearRows(LinearLayout list, List<Runnable> bucket) {
        refreshers.removeAll(bucket);
        bucket.clear();
        list.removeAllViews();
    }

    private View toggleRow(Item it) {
        LinearLayout row = row();
        TextView title = label(Catalog.label(cfg, it).toString());
        TextView value = sub("");
        row.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
        row.addView(value, new LinearLayout.LayoutParams(MATCH, WRAP));
        row.setOnClickListener(v -> {
            Catalog.setShown(cfg, Catalog.INPUTS, it, !Catalog.shown(cfg, Catalog.INPUTS, it));
            refresh();
        });
        Runnable show = () -> {
            boolean on = Catalog.shown(cfg, Catalog.INPUTS, it);
            boolean focused = row.isFocused();
            value.setText(on ? "Shown" : "Hidden");
            row.setContentDescription(title.getText() + (on ? ", shown" : ", hidden"));
            paintRow(row, focused, title, value);
            if (focused) inputsNote.setText(on ? "Shown in the inputs row." : "Hidden from the inputs row.");
        };
        row.setOnFocusChangeListener((v, has) -> show.run());
        refreshers.add(show);
        inputRefreshers.add(show);
        show.run();
        return row;
    }

    private View favoriteRow(Item it, int pass) {
        LinearLayout row = row();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setId(View.generateViewId());
        row.setTag(it);
        int s = dp(28);
        FrameLayout mark = new FrameLayout(this);
        String name = Catalog.label(cfg, it).toString();
        TextView letter = Ui.text(this, 14, Ui.TEXT, Ui.MEDIUM);
        letter.setGravity(Gravity.CENTER);
        letter.setText(name.isEmpty() ? "?"
                : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.getDefault()));
        letter.setBackground(Ui.round(0x22FFFFFF, dp(6)));
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.addView(letter, new FrameLayout.LayoutParams(s, s));
        mark.addView(icon, new FrameLayout.LayoutParams(s, s));
        row.addView(mark, new LinearLayout.LayoutParams(s, s));
        TextView title = label(name);
        title.setPadding(dp(12), 0, dp(12), 0);
        row.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        Toggle toggle = new Toggle(this);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(40), dp(24)));
        row.setOnClickListener(v -> {
            Catalog.setShown(cfg, Catalog.APPS, it, !Catalog.shown(cfg, Catalog.APPS, it));
            refresh();
            placeFavorite(row);
        });
        Runnable show = () -> {
            boolean on = Catalog.shown(cfg, Catalog.APPS, it);
            boolean focused = row.isFocused();
            toggle.setOn(on, focused);
            letter.setTextColor(focused ? INK : Ui.TEXT);
            letter.setBackground(Ui.round(focused ? 0x14000000 : 0x22FFFFFF, dp(6)));
            row.setContentDescription(name + (on ? ", shown on the home row" : ", not on the home row"));
            paintRow(row, focused, title, null);
            if (focused) appsNote.setText(on ? "Shown on the home row." : "Not on the home row.");
        };
        row.setOnFocusChangeListener((v, has) -> show.run());
        refreshers.add(show);
        appRefreshers.add(show);
        show.run();
        if (it.icon != null) showIcon(icon, letter, it);
        else io.execute(() -> {
            Catalog.icon(getPackageManager(), it);
            ui.post(() -> {
                if (pass != appGeneration || icon.getParent() == null) return;
                showIcon(icon, letter, it);
            });
        });
        return row;
    }

    private void placeFavorite(View row) {
        Item it = (Item) row.getTag();
        boolean on = Catalog.shown(cfg, Catalog.APPS, it);
        Collator collator = Collator.getInstance();
        String name = Catalog.label(cfg, it).toString();
        appList.removeView(row);
        int index = appList.getChildCount();
        for (int i = 0; i < appList.getChildCount(); i++) {
            Object tag = appList.getChildAt(i).getTag();
            if (!(tag instanceof Item)) continue;
            Item other = (Item) tag;
            boolean otherOn = Catalog.shown(cfg, Catalog.APPS, other);
            if (on && !otherOn) {
                index = i;
                break;
            }
            if (on == otherOn && collator.compare(name, Catalog.label(cfg, other).toString()) < 0) {
                index = i;
                break;
            }
        }
        appList.addView(row, index);
        linkFavorites();
        row.requestFocus();
    }

    private void linkFavorites() {
        if (autoRow == null || appList.getChildCount() == 0) return;
        View first = null;
        for (int i = 0; i < appList.getChildCount(); i++) {
            View child = appList.getChildAt(i);
            child.setNextFocusUpId(View.NO_ID);
            if (first == null && child.getTag() instanceof Item) first = child;
        }
        if (first == null) return;
        autoRow.setNextFocusDownId(first.getId());
        first.setNextFocusUpId(autoRow.getId());
    }

    private void showIcon(ImageView icon, TextView letter, Item it) {
        Drawable copy = copy(it.icon);
        if (copy == null) return;
        icon.setImageDrawable(copy);
        letter.setVisibility(View.GONE);
    }

    /** A tile may draw the same icon with its own bounds, so a row needs its own copy. */
    private static Drawable copy(Drawable src) {
        if (src == null) return null;
        Drawable.ConstantState state = src.getConstantState();
        return state != null ? state.newDrawable() : src;
    }

    private TextView section(String text, int top) {
        TextView title = Ui.text(this, 12, 0xFFECECEC, ember());
        title.setText(text.toUpperCase(Locale.ROOT));
        title.setLetterSpacing(0.06f);
        title.setPadding(dp(24), top, dp(24), dp(28));
        return title;
    }

    private void refresh() {
        String n = cfg.get("name", "").trim();
        named = !n.isEmpty();
        if (nameView != null) nameView.setText(named ? n : "Your Name");
        if (switchRow != null) switchRow.setAlpha("off".equals(cfg.get(Config.INPUTS)) ? 0.4f : 1f);
        if (greetCustom != null) {
            greetCustom.setAlpha(Quotes.CUSTOM.equals(cfg.get(Config.GREETING)) ? 1f : 0.4f);
        }
        boolean lineOn = Config.welcomeLine(cfg);
        if (welcomeChoice != null) {
            boolean covered = !lineOn || Quotes.written(cfg.get(Config.WELCOME_TEXT, ""));
            welcomeChoice.setAlpha(covered ? 0.4f : 1f);
        }
        if (welcomeCustom != null) welcomeCustom.setAlpha(lineOn ? 1f : 0.4f);
        paintGreeting();
        for (Runnable r : refreshers) r.run();
    }

    /**
     * The greeting as it will appear, and one line from the chosen list. A custom line is the
     * line itself. The home screen still walks through the list one line at a time.
     */
    private void paintGreeting() {
        if (greetPreview == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greetPreview.setText(Quotes.greeting(hour, cfg.get("name", ""),
                cfg.get(Config.GREETING), cfg.get(Config.GREETING_TEXT, "")));
        boolean lineOn = Config.welcomeLine(cfg);
        linePreview.setVisibility(lineOn ? View.VISIBLE : View.GONE);
        if (lineOn) {
            linePreview.setText(Quotes.welcome(cfg.get(Config.WELCOME), cfg.get(Config.WELCOME_TEXT, ""),
                    1, 0, hour));
        }
    }

    /** On shows the line under the greeting. Off leaves the greeting on its own. */
    private View welcomeShowRow() {
        LinearLayout row = row();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("Show Welcome Line");
        TextView value = sub("");
        text.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
        text.addView(value, new LinearLayout.LayoutParams(MATCH, WRAP));
        row.addView(text, new LinearLayout.LayoutParams(0, WRAP, 1f));
        Toggle toggle = new Toggle(this);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(40), dp(24)));
        Runnable flip = () -> {
            cfg.set(Config.WELCOME_SHOW, Config.welcomeLine(cfg) ? "off" : "on");
            refresh();
        };
        row.setOnClickListener(v -> flip.run());
        row.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() != 0) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                flip.run();
                return true;
            }
            return false;
        });
        Runnable show = () -> {
            boolean on = Config.welcomeLine(cfg);
            boolean focused = row.isFocused();
            value.setText(on ? "On" : "Off");
            toggle.setOn(on, focused);
            row.setContentDescription("Show Welcome Line, " + (on ? "on" : "off"));
            paintRow(row, focused, title, value);
            if (focused) {
                greetNote.setText("Shows a short line under the greeting. Off leaves only the greeting.");
            }
        };
        row.setOnFocusChangeListener((v, has) -> show.run());
        refreshers.add(show);
        show.run();
        return row;
    }

    private View choice(Config.Choice c, TextView aside, String help) {
        LinearLayout row = row();
        TextView title = label(c.title);
        TextView value = sub("");
        row.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
        row.addView(value, new LinearLayout.LayoutParams(MATCH, WRAP));
        row.setOnClickListener(v -> step(c, 1));
        row.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) return step(c, -1);
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) return step(c, 1);
            return false;
        });
        Runnable show = () -> {
            boolean focused = row.isFocused();
            value.setText(c.name(cfg.get(c)));
            paintRow(row, focused, title, value);
            if (focused && aside != null) aside.setText(rowHelp(c, help));
        };
        row.setOnFocusChangeListener((v, has) -> show.run());
        refreshers.add(show);
        show.run();
        return row;
    }

    private boolean step(Config.Choice c, int by) {
        int i = Arrays.asList(c.values).indexOf(cfg.get(c));
        int n = c.values.length;
        cfg.set(c.key, c.values[((i + by) % n + n) % n]);
        refresh();
        if (c == Config.ROTATE) {
            // Keep the picture up. The new schedule takes the next one when its turn comes.
            cfg.set(Walls.KEY, Walls.key(cfg));
        } else if (c == Config.DIM) {
            paint();
        }
        return true;
    }

    /** Help for the choice in focus. Rotation and where an input opens follow the current value. */
    private String rowHelp(Config.Choice c, String help) {
        if (c == Config.ROTATE) return rotateHelp();
        if (c == Config.SWITCH) return switchHelp();
        return help;
    }

    /** What the rotation choice in focus will do. It follows left and right. */
    private String rotateHelp() {
        String mode = cfg.get(Config.ROTATE);
        if ("visit".equals(mode)) {
            return "Changes each time you come back to the home screen.";
        }
        if ("hourly".equals(mode)) {
            return "Changes at the start of each hour.";
        }
        if ("daily".equals(mode)) {
            return "Changes at the start of each day.";
        }
        if ("daytime".equals(mode)) {
            return "Morning, afternoon, and night each get their own picture. "
                    + "Morning is 6 AM to 1 PM, afternoon is 1 PM to 8 PM, and night is 8 PM to 6 AM.";
        }
        return "This picture stays until you choose Get Another Picture.";
    }

    /** Where the input choice in focus will open. It follows left and right. */
    private String switchHelp() {
        String mode = cfg.get(Config.SWITCH);
        if ("tv".equals(mode)) {
            return "Always opens the input in the TV's own app.";
        }
        if ("home".equals(mode)) {
            return "Always shows the input on this screen. Press Back to return home.";
        }
        return "Uses the TV's app when only one app can open the input. Otherwise it opens on this screen.";
    }

    private View textRow(String title, String key, int max, String fallback, TextView aside, String help) {
        return textRow(title, key, max, fallback, null, aside, help);
    }

    /**
     * {@code fallback} is shown, and offered in the editor, when the value is blank.
     * {@code empty} is only the row label for a blank value, so it is never saved.
     */
    private View textRow(String title, String key, int max, String fallback, String empty,
            TextView aside, String help) {
        LinearLayout row = row();
        TextView name = label(title);
        TextView value = sub("");
        value.setMaxLines(1);
        value.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(MATCH, WRAP));
        row.addView(value, new LinearLayout.LayoutParams(MATCH, WRAP));
        row.setOnClickListener(v -> editText(title, key, max, fallback));
        Runnable show = () -> {
            String n = cfg.get(key, "").trim();
            if (n.isEmpty() && fallback != null) n = fallback;
            else if (n.isEmpty() && empty != null) n = empty;
            boolean focused = row.isFocused();
            value.setText(n);
            paintRow(row, focused, name, value);
            if (focused && aside != null) aside.setText(help);
        };
        row.setOnFocusChangeListener((v, has) -> show.run());
        refreshers.add(show);
        show.run();
        return row;
    }

    private View action(String text, Runnable run, TextView aside, String help) {
        LinearLayout row = row();
        TextView title = label(text);
        row.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
        row.setOnClickListener(v -> run.run());
        row.setOnFocusChangeListener((v, has) -> {
            paintRow(row, has, title, null);
            if (has && aside != null) aside.setText(help);
        });
        refreshers.add(() -> paintRow(row, row.isFocused(), title, null));
        return row;
    }

    private void editText(String title, String key, int max) {
        editText(title, key, max, null);
    }

    private void editText(String title, String key, int max, String fallback) {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setFilters(new InputFilter[] {new InputFilter.LengthFilter(max)});
        String current = cfg.get(key, "").trim();
        if (current.isEmpty() && fallback != null) current = fallback;
        field.setText(current);
        field.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(field);
        AlertDialog d = new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Save", (dialog, w) -> saveText(key, field.getText().toString().trim()))
                .setNeutralButton("Clear", (dialog, w) -> saveText(key, null))
                .setNegativeButton("Cancel", null)
                .create();
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
    }

    private void saveText(String key, String value) {
        cfg.set(key, value);
        refresh();
        if ("provider.name".equals(key)) {
            paint();
            fetchIfDue();
        }
    }

    private void nextWallpaper() {
        cfg.nextWallpaper();
        refresh();
        paint();
        fetchIfDue();
    }

    private void paint() {
        if (preview == null) return;
        File file = Walls.shown(cfg);
        if (file == null) {
            preview.setImageDrawable(null);
            previewShade.setBackgroundColor(Color.TRANSPARENT);
            wallKey = null;
            return;
        }
        int dim = Integer.parseInt(cfg.get(Config.DIM));
        previewShade.setBackgroundColor(Color.argb(dim * 255 / 100, 0, 0, 0));
        String key = file.getPath() + ":" + file.lastModified();
        if (key.equals(wallKey)) return;
        wallKey = key;
        io.execute(() -> {
            Bitmap bitmap = decode(file, dp(640), dp(360));
            ui.post(() -> {
                if (!isDestroyed() && key.equals(wallKey)) preview.setImageBitmap(bitmap);
            });
        });
    }

    /** Loads a picture when this turn does not already have one saved. */
    private void fetchIfDue() {
        Walls.Need need = Walls.need(cfg);
        if (fetching || need == Walls.Need.KEEP) return;
        String key = Walls.key(cfg);
        String query = Walls.query(cfg);
        String reuse = need == Walls.Need.REFILL ? cfg.get(Walls.URL, "") : null;
        fetching = true;
        net.execute(() -> {
            String image = reuse != null
                    ? (Walls.store(reuse, Walls.file(cfg)) ? reuse : null)
                    : Walls.fetch(query, Walls.file(cfg));
            ui.post(() -> {
                fetching = false;
                if (isDestroyed()) return;
                if (!key.equals(Walls.key(cfg))) {
                    fetchIfDue();
                    return;
                }
                if (image != null) {
                    remember(key, image);
                    paint();
                } else if (Walls.shown(cfg) == null) {
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

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(24), dp(14), dp(24), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        row.setLayoutParams(lp);
        return row;
    }

    private void paintTile(View tile, boolean focused) {
        GradientDrawable d = Ui.round(TILE_FILL, dp(8));
        if (focused) d.setStroke(dp(2), TILE_INK);
        tile.setBackground(d);
        float scale = focused ? TILE_FOCUS : 1f;
        tile.setScaleX(scale);
        tile.setScaleY(scale);
    }

    /** Fire TV draws its settings in Amazon Ember, which the device already has. */
    private Typeface ember() {
        return emberFace(false);
    }

    private Typeface emberMedium() {
        return emberFace(true);
    }

    private Typeface emberFace(boolean medium) {
        Typeface cached = medium ? emberMedium : emberRegular;
        if (cached != null) return cached;
        String file = medium
                ? "/system/fonts/Amazon-Ember-Medium.ttf"
                : "/system/fonts/Amazon-Ember-Regular.ttf";
        Typeface face;
        try {
            face = Typeface.createFromFile(file);
        } catch (RuntimeException ignored) {
            face = Typeface.SANS_SERIF;
        }
        if (medium) emberMedium = face;
        else emberRegular = face;
        return face;
    }

    private void paintRow(View row, boolean focused, TextView title, TextView value) {
        row.setBackground(Ui.round(focused ? ROW_ON : Color.TRANSPARENT, 0));
        if (title != null) title.setTextColor(focused ? INK : 0xFFEBEBEB);
        if (value != null) value.setTextColor(focused ? INK_SOFT : Ui.MUTED);
        if (focused) row.post(() -> stickHelp(row));
    }

    /** The right-hand note sits level with the focused row, as on Fire TV. */
    private void stickHelp(View row) {
        TextView note = screen == APPS ? appsNote : screen == INPUTS ? inputsNote : null;
        if (note == null || !row.isFocused() || note.getParent() == null) return;
        int[] rowAt = new int[2];
        int[] paneAt = new int[2];
        row.getLocationInWindow(rowAt);
        ((View) note.getParent()).getLocationInWindow(paneAt);
        int y = rowAt[1] - paneAt[1];
        note.setTranslationY(Math.max(dp(48), y));
    }

    private TextView label(String text) {
        TextView v = Ui.text(this, 16, 0xFFEBEBEB, ember());
        v.setText(text);
        v.setMaxLines(1);
        v.setEllipsize(TextUtils.TruncateAt.END);
        return v;
    }

    private TextView sub(String text) {
        TextView v = Ui.text(this, 11, Ui.MUTED, ember());
        v.setText(text);
        v.setMaxLines(1);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setPadding(0, dp(2), 0, 0);
        return v;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    /** A 16:9 frame. The picture is fitted inside it, not stretched. */
    private final class Ratio extends FrameLayout {
        Ratio(Context c) {
            super(c);
            setBackgroundColor(Color.BLACK);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            if (width <= 0) width = dp(320);
            int height = Math.max(1, width * 9 / 16);
            int w = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int h = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(w, h);
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int w = right - left, h = bottom - top;
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).layout(0, 0, w, h);
        }
    }

    /** A gray switch. The row around it takes focus; this only shows on or off. */
    private static final class Toggle extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean on;

        Toggle(Context c) {
            super(c);
            setOn(false, false);
        }

        void setOn(boolean on, boolean light) {
            this.on = on;
            if (light) {
                track.setColor(on ? 0xFF1A1A1A : 0x33000000);
                knob.setColor(on ? 0xFFFFFFFF : 0xFF6E6E6E);
            } else {
                track.setColor(on ? 0xCCFFFFFF : 0x33FFFFFF);
                knob.setColor(on ? 0xFF1C1D21 : 0xFFB0B0B0);
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight(), w = getWidth();
            float r = h / 2f;
            canvas.drawRoundRect(0, 0, w, h, r, r, track);
            float pad = h * 0.14f;
            float d = h - pad * 2;
            float x = on ? w - pad - d : pad;
            canvas.drawCircle(x + d / 2f, h / 2f, d / 2f, knob);
        }
    }
}
