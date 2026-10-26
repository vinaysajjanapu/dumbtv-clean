package tv.vinay.dumbtv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.TextView;

final class Ui {
    static final int TEXT = 0xFFF2F2F3;
    static final int MUTED = 0x99FFFFFF;
    static final int FAINT = 0x66FFFFFF;
    static final int SURFACE = 0xFF1C1D21;
    static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static Typeface emberRegular, emberMedium;

    private Ui() {}

    /**
     * Fire TV draws its quick-settings panel in Amazon Ember, which the device already has.
     * Roboto stands in where that file is missing.
     */
    static Typeface ember() {
        return ember(false);
    }

    static Typeface ember(boolean medium) {
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

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, float sp, int color, Typeface face) {
        TextView v = new TextView(c);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(face);
        v.setIncludeFontPadding(false);
        return v;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** A faint disc only while focused. For the small icons along the top. */
    static void ghostIcon(ImageView v, float radius) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, round(0x33FFFFFF, radius));
        bg.addState(new int[0], round(0x00000000, radius));
        v.setBackground(bg);
        Drawable icon = v.getDrawable();
        if (icon == null) return;
        icon.mutate().setTintList(new ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {0xFFFFFFFF, 0xCCFFFFFF}));
    }
}
