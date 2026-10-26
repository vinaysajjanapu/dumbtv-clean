package tv.vinay.dumbtv;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Every installed app, in a panel over the home screen. The Apps row stays the short list;
 * this one is how the rest get opened.
 */
final class AllApps extends FrameLayout {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    /** Fire TV quick-settings fill, label, and the white pill behind the focused row. */
    private static final int PANEL = 0xFF2A2A2A;
    private static final int LABEL = 0xFFC2C2C2;
    private static final int INK = 0xFF1A1A1A;
    private static final int PILL = 0xFFF0F0F0;

    interface Host {
        void open(Item item);

        /** Whether this app is already on the home row. */
        boolean onHome(Item item);

        /** Hold OK chose App info. */
        void info(Item item);

        /** Hold OK chose the home-row favorite. */
        void favorite(Item item);

        /** Loads {@link Item#icon} off the UI thread, then runs {@code done} on it. */
        void loadIcon(Item item, Runnable done);

        /** The home screen under the panel should ignore d-pad focus while {@code on}. */
        void covered(boolean on);

        /** The panel has finished closing. */
        void closed();
    }

    private final Host host;
    private final int width;
    /** How far the card travels to leave the screen, including the gap on its right. */
    private final int slide;
    private final Typeface face;
    private final View scrim;
    private final LinearLayout panel;
    private final TextView count;
    private final LinearLayout list;
    private final LinearLayout popup;
    private View anchor;
    private int generation;
    private boolean dismissing;

    AllApps(Context c, Host host) {
        super(c);
        this.host = host;
        int screen = c.getResources().getDisplayMetrics().widthPixels;
        // The quick-settings card sits 20dp off the top, right and bottom, 287dp wide, corners 24dp.
        int inset = dp(20);
        width = Math.min(dp(287), Math.max(dp(200), screen - inset * 2));
        slide = width + inset;
        face = Ui.ember();

        scrim = new View(c);
        scrim.setOnClickListener(v -> {
            if (!dismissPopup()) dismiss(true);
        });
        addView(scrim, new LayoutParams(MATCH, MATCH));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.round(PANEL, dp(24)));
        panel.setClipToOutline(true);
        panel.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(24));
            }
        });
        panel.setPadding(dp(20), dp(18), dp(20), dp(12));
        panel.setClickable(true);
        panel.setOnClickListener(v -> dismissPopup());

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), 0, dp(16), 0);
        TextView title = Ui.text(c, 16, LABEL, face);
        title.setText("All Apps");
        head.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        count = Ui.text(c, 16, LABEL, face);
        head.addView(count);
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        headLp.bottomMargin = dp(14);
        panel.addView(head, headLp);

        ScrollView scroll = new ScrollView(c);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(MATCH, WRAP));
        panel.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        LayoutParams side = new LayoutParams(width, MATCH);
        side.gravity = Gravity.END;
        side.topMargin = inset;
        side.bottomMargin = inset;
        side.rightMargin = inset;
        addView(panel, side);

        popup = new LinearLayout(c);
        popup.setOrientation(LinearLayout.VERTICAL);
        popup.setBackground(Ui.round(PANEL, dp(16)));
        popup.setElevation(dp(8));
        popup.setPadding(dp(6), dp(6), dp(6), dp(6));
        popup.setFocusable(false);
        popup.setVisibility(GONE);
        addView(popup, new LayoutParams(dp(236), WRAP));
        setVisibility(GONE);
    }

    /** Closes the small menu beside a row. @return whether it was open */
    boolean dismissPopup() {
        if (popup.getVisibility() == GONE) return false;
        popup.animate().cancel();
        popup.setVisibility(GONE);
        View row = anchor;
        anchor = null;
        if (row != null) {
            paint(row, row.hasFocus());
            if (row.isAttachedToWindow() && !row.hasFocus()) row.requestFocus();
        }
        return true;
    }

    /** Keeps d-pad focus inside the panel while it is open. */
    @Override
    public View focusSearch(View focused, int direction) {
        if (getVisibility() != VISIBLE) return super.focusSearch(focused, direction);
        if (popup.getVisibility() == VISIBLE) {
            View next = FocusFinder.getInstance().findNextFocus(popup, focused, direction);
            return next != null ? next : focused;
        }
        return FocusFinder.getInstance().findNextFocus(this, focused, direction);
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE && !dismissing;
    }

    /**
     * Closes the panel. {@code animate} slides it off; an instant close is for leaving the
     * home screen, where a slide would still be running when it comes back.
     * @return whether the panel was showing
     */
    boolean dismiss(boolean animate) {
        dismissPopup();
        if (getVisibility() != VISIBLE) return false;
        if (dismissing && animate) return true;
        generation++;
        panel.animate().cancel();
        scrim.animate().cancel();
        if (!animate) {
            finish(false);
            return true;
        }
        dismissing = true;
        int pass = generation;
        panel.animate().translationX(slide).setDuration(180).withEndAction(() -> {
            if (pass != generation) return;
            finish(true);
        }).start();
        scrim.animate().alpha(0f).setDuration(180).start();
        return true;
    }

    void show(List<Item> items, Config cfg) {
        dismissPopup();
        boolean fresh = getVisibility() != VISIBLE || dismissing;
        generation++;
        dismissing = false;
        panel.animate().cancel();
        scrim.animate().cancel();

        String keep = null;
        View current = findFocus();
        if (current != null && current.getTag() instanceof String) keep = (String) current.getTag();
        list.removeAllViews();

        List<Item> shown = new ArrayList<>(items);
        Collator collator = Collator.getInstance();
        Collections.sort(shown, (a, b) -> collator.compare(name(cfg, a), name(cfg, b)));
        count.setText(shown.size() == 1 ? "1 app" : shown.size() + " apps");
        int pass = generation;
        for (Item it : shown) list.addView(row(it, name(cfg, it), pass));
        if (shown.isEmpty()) {
            TextView empty = Ui.text(getContext(), 16, LABEL, face);
            empty.setText("No apps installed");
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.setPadding(dp(16), 0, dp(16), 0);
            empty.setFocusable(true);
            empty.setOnFocusChangeListener((v, has) -> paint(empty, has));
            paint(empty, false);
            LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(MATCH, dp(48));
            empty.setLayoutParams(emptyLp);
            list.addView(empty);
        }

        View target = null;
        if (keep != null) {
            for (int i = 0; i < list.getChildCount(); i++) {
                if (keep.equals(list.getChildAt(i).getTag())) {
                    target = list.getChildAt(i);
                    break;
                }
            }
        }
        if (target == null && list.getChildCount() > 0) target = list.getChildAt(0);

        if (fresh) {
            panel.setTranslationX(slide);
            scrim.setAlpha(0f);
        } else {
            panel.setTranslationX(0);
            scrim.setAlpha(1f);
        }
        setVisibility(VISIBLE);
        if (fresh) {
            panel.animate().translationX(0).setDuration(180).start();
            scrim.animate().alpha(1f).setDuration(180).start();
        }
        host.covered(true);
        if (target != null && !target.requestFocus()) {
            View focus = target;
            focus.post(focus::requestFocus);
        }
    }

    private View row(Item it, String label, int pass) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setTag(it.id);
        row.setContentDescription(label);
        row.setOnClickListener(v -> {
            if (dismissPopup()) return;
            host.open(it);
        });
        row.setOnLongClickListener(v -> {
            showPopup(row, it);
            return true;
        });
        row.setOnFocusChangeListener((v, has) -> paint(row, has));
        paint(row, false);

        int s = dp(20);
        FrameLayout mark = new FrameLayout(getContext());
        TextView letter = Ui.text(getContext(), 12, LABEL, face);
        letter.setGravity(Gravity.CENTER);
        letter.setText(label.isEmpty() ? "?"
                : label.substring(0, label.offsetByCodePoints(0, 1)).toUpperCase(Locale.getDefault()));
        letter.setBackground(Ui.round(0xFF3A3A3A, dp(5)));
        ImageView icon = new ImageView(getContext());
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.addView(letter, new FrameLayout.LayoutParams(s, s));
        mark.addView(icon, new FrameLayout.LayoutParams(s, s));
        row.addView(mark, new LinearLayout.LayoutParams(s, s));

        TextView name = Ui.text(getContext(), 16, LABEL, face);
        name.setText(label);
        name.setPadding(dp(12), 0, 0, 0);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(48));
        lp.bottomMargin = dp(14);
        row.setLayoutParams(lp);

        if (it.icon != null) showIcon(icon, letter, it);
        else host.loadIcon(it, () -> {
            if (pass != generation || icon.getParent() == null) return;
            showIcon(icon, letter, it);
        });
        return row;
    }

    private void showIcon(ImageView icon, TextView letter, Item it) {
        Drawable copy = copy(it.icon);
        if (copy == null) return;
        icon.setImageDrawable(copy);
        letter.setVisibility(GONE);
    }

    /** A tile draws {@link Item#icon} with its own bounds, so a row needs its own copy. */
    private static Drawable copy(Drawable src) {
        if (src == null) return null;
        Drawable.ConstantState state = src.getConstantState();
        return state != null ? state.newDrawable() : src;
    }

    /**
     * A small menu to the left of the held row: App info, and add or remove the home-row
     * favorite. It stays beside that name instead of covering the list.
     */
    private void showPopup(View row, Item it) {
        if (anchor != null && anchor != row) paint(anchor, anchor.hasFocus());
        anchor = row;
        paint(row, true);
        popup.animate().cancel();
        popup.removeAllViews();
        popup.addView(action(R.drawable.ic_info, "App Info", () -> {
            dismissPopup();
            host.info(it);
        }));
        String favorite = host.onHome(it) ? "Remove from Favorites" : "Add to Favorites";
        popup.addView(action(R.drawable.ic_favorite, favorite, () -> {
            dismissPopup();
            host.favorite(it);
        }));
        popup.setVisibility(INVISIBLE);
        popup.post(() -> {
            if (anchor != row) return;
            place(row);
            popup.setAlpha(0f);
            popup.setVisibility(VISIBLE);
            popup.animate().alpha(1f).setDuration(120).start();
            View first = popup.getChildAt(0);
            if (first != null && !first.requestFocus()) first.post(first::requestFocus);
        });
    }

    /** Puts the menu just left of the list, lined up with {@code row}. */
    private void place(View row) {
        popup.measure(View.MeasureSpec.makeMeasureSpec(popup.getLayoutParams().width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int ph = popup.getMeasuredHeight();
        int[] rowAt = new int[2];
        int[] rootAt = new int[2];
        row.getLocationInWindow(rowAt);
        getLocationInWindow(rootAt);
        int y = rowAt[1] - rootAt[1] + (row.getHeight() - ph) / 2;
        int x = panel.getLeft() + (int) panel.getTranslationX() - popup.getMeasuredWidth() - dp(12);
        int edge = dp(16);
        if (x < edge) x = edge;
        int maxY = getHeight() - ph - edge;
        if (y > maxY) y = Math.max(edge, maxY);
        if (y < edge) y = edge;
        popup.setX(x);
        popup.setY(y);
    }

    private View action(int iconRes, String label, Runnable pick) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setContentDescription(label);
        row.setOnClickListener(v -> pick.run());

        ImageView icon = new ImageView(getContext());
        icon.setImageResource(iconRes);
        Drawable drawn = icon.getDrawable();
        if (drawn != null) drawn.mutate();
        int s = dp(18);
        row.addView(icon, new LinearLayout.LayoutParams(s, s));

        TextView text = Ui.text(getContext(), 16, LABEL, face);
        text.setText(label);
        text.setPadding(dp(12), 0, 0, 0);
        text.setMaxLines(1);
        text.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(text, new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.setOnFocusChangeListener((v, has) -> {
            int color = has ? INK : LABEL;
            text.setTextColor(color);
            icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            row.setBackground(has ? Ui.round(PILL, dp(12)) : null);
        });
        icon.setColorFilter(LABEL, PorterDuff.Mode.SRC_IN);
        row.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(40)));
        return row;
    }

    private void paint(View row, boolean focused) {
        boolean on = focused || row == anchor;
        row.setBackground(on ? Ui.round(PILL, dp(16)) : null);
        int color = on ? INK : LABEL;
        if (row instanceof TextView) {
            ((TextView) row).setTextColor(color);
            return;
        }
        View label = ((ViewGroup) row).getChildAt(1);
        if (label instanceof TextView) ((TextView) label).setTextColor(color);
    }

    private String name(Config cfg, Item it) {
        CharSequence label = Catalog.label(cfg, it);
        return label == null ? "" : label.toString();
    }

    private void finish(boolean notify) {
        dismissing = false;
        setVisibility(GONE);
        panel.setTranslationX(0);
        scrim.setAlpha(1f);
        host.covered(false);
        if (notify) host.closed();
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }
}
