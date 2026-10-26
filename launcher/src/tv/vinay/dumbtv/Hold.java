package tv.vinay.dumbtv;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The menu for a held tile. The home screen stays up. A wash darkens only toward
 * the right edge, and the actions sit in the lower right, the way Fire TV's hold
 * menu does.
 */
final class Hold extends FrameLayout {
    static final int MOVE = 0;
    static final int HIDE = 2;
    static final int INFO = 3;

    static final class Action {
        final int id;
        final int icon;
        final String label;

        Action(int id, int icon, String label) {
            this.id = id;
            this.icon = icon;
            this.label = label;
        }
    }

    interface Choice {
        void onAction(int id);
    }

    interface Host {
        /** The home screen under the menu should ignore d-pad focus while {@code on}. */
        void covered(boolean on);
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Host host;
    private final LinearLayout menu;
    private Choice choice;
    private View back;
    private int generation;
    private boolean dismissing;

    Hold(Context c, Host host) {
        super(c);
        this.host = host;

        View wash = new View(c);
        // Clear around the middle of the screen, then dark enough at the right that
        // the icons stay readable on a bright wallpaper.
        int washWidth = Math.round(c.getResources().getDisplayMetrics().widthPixels * 0.62f);
        wash.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0x00000000, 0x99000000, 0xF5000000}));
        LayoutParams washLp = new LayoutParams(washWidth, MATCH);
        washLp.gravity = Gravity.END;
        addView(wash, washLp);

        menu = new LinearLayout(c);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.END);
        menu.setPadding(0, 0, dp(56), dp(52));
        menu.setClickable(true);
        menu.setFocusable(false);
        LayoutParams menuLp = new LayoutParams(WRAP, WRAP);
        menuLp.gravity = Gravity.END | Gravity.BOTTOM;
        addView(menu, menuLp);

        setClickable(true);
        setFocusable(false);
        setOnClickListener(v -> dismiss(true));
        setVisibility(GONE);
    }

    /** Keeps d-pad focus on the actions while the menu is open. */
    @Override
    public View focusSearch(View focused, int direction) {
        if (getVisibility() != VISIBLE) return super.focusSearch(focused, direction);
        View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
        return next != null ? next : focused;
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE && !dismissing;
    }

    void show(View restore, List<Action> actions, Choice choice) {
        boolean fresh = getVisibility() != VISIBLE || dismissing;
        generation++;
        dismissing = false;
        animate().cancel();
        menu.animate().cancel();
        this.back = restore;
        this.choice = choice;
        menu.removeAllViews();

        TextView heading = Ui.text(getContext(), 12, Ui.FAINT, Ui.MEDIUM);
        heading.setText("OPTIONS");
        heading.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        headingLp.gravity = Gravity.END;
        headingLp.bottomMargin = dp(14);
        headingLp.rightMargin = dp(8);
        menu.addView(heading, headingLp);

        View first = null;
        for (Action action : actions) {
            View row = row(action);
            menu.addView(row);
            if (first == null) first = row;
        }

        host.covered(true);
        setVisibility(VISIBLE);
        if (fresh) {
            setAlpha(0f);
            menu.setTranslationX(dp(28));
            animate().alpha(1f).setDuration(180).start();
            menu.animate().translationX(0).setDuration(180).start();
        } else {
            setAlpha(1f);
            menu.setTranslationX(0);
        }
        if (first != null && !first.requestFocus()) {
            View focus = first;
            focus.post(focus::requestFocus);
        }
    }

    /**
     * Closes the menu. {@code animate} lets it fade; an instant close is for leaving
     * the home screen, where a fade would still be running when it comes back.
     * @return whether the menu was showing
     */
    boolean dismiss(boolean animate) {
        if (getVisibility() != VISIBLE) return false;
        if (dismissing && animate) return true;
        generation++;
        animate().cancel();
        menu.animate().cancel();
        if (!animate) {
            finish();
            return true;
        }
        dismissing = true;
        int pass = generation;
        menu.animate().translationX(dp(28)).setDuration(140).start();
        animate().alpha(0f).setDuration(140).withEndAction(() -> {
            if (pass != generation) return;
            finish();
        }).start();
        return true;
    }

    private View row(Action action) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setMinimumWidth(dp(220));
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        row.setContentDescription(action.label);
        row.setOnClickListener(v -> pick(action.id));

        int s = dp(22);
        ImageView icon = new ImageView(getContext());
        icon.setImageResource(action.icon);
        Drawable drawn = icon.getDrawable();
        if (drawn != null) drawn.mutate();
        row.addView(icon, new LinearLayout.LayoutParams(s, s));

        TextView label = Ui.text(getContext(), 20, Ui.TEXT, Ui.LIGHT);
        label.setText(action.label);
        label.setPadding(dp(14), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(WRAP, WRAP));
        row.setOnFocusChangeListener((v, has) -> paint(row, icon, label, has));
        paint(row, icon, label, false);

        row.setLayoutParams(new LinearLayout.LayoutParams(WRAP, WRAP));
        return row;
    }

    private void paint(LinearLayout row, ImageView icon, TextView label, boolean focused) {
        int color = focused ? 0xFFFFFFFF : 0x73FFFFFF;
        label.setTextColor(color);
        icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        row.setBackground(focused ? Ui.round(0x59FFFFFF, dp(10)) : null);
    }

    private void pick(int action) {
        Choice chosen = choice;
        dismiss(false);
        if (chosen != null) chosen.onAction(action);
    }

    private void finish() {
        dismissing = false;
        choice = null;
        View restore = back;
        back = null;
        setAlpha(1f);
        menu.setTranslationX(0);
        setVisibility(GONE);
        host.covered(false);
        if (restore != null && restore.isAttachedToWindow()) restore.requestFocus();
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }
}
