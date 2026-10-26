package tv.vinay.dumbtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** A card drawn in one pass: banner, or icon plus name, or an input name. Focus grows the card. */
final class Tile extends View {
    /** Large enough that the focused card reads on its own, with no outline. */
    private static final float FOCUS_SCALE = 1.26f;

    final Item item;
    final String kind;
    private final CharSequence title;
    private final int cardW, cardH, labelH;
    private final boolean labelAlways, labelOnFocus;
    private final float radius, stroke;
    private final int accent;
    private final RectF card = new RectF();
    private final Path clip = new Path();
    private final Path arrow = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint inner = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint under = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable recenter = () -> {
        if (!isFocused() || !isAttachedToWindow()) return;
        requestRectangleOnScreen(new Rect(0, 0, getWidth(), getHeight()), true);
    };
    private ValueAnimator grow;
    private float shown = 1f;
    private boolean recenterPosted;
    private boolean moving;

    Tile(Context c, Item item, String kind, CharSequence title, int cardW, int cardH,
            String labels, int accent) {
        super(c);
        this.item = item;
        this.kind = kind;
        this.title = title;
        this.cardW = cardW;
        this.cardH = cardH;
        boolean app = Catalog.APPS.equals(kind);
        labelH = app ? Ui.dp(c, 26) : 0;
        labelAlways = "always".equals(labels);
        labelOnFocus = "focus".equals(labels);
        radius = Ui.dp(c, 10);
        stroke = Ui.dp(c, 2.5f);
        this.accent = accent;

        fill.setColor(app ? Ui.SURFACE : 0xFF17181B);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setColor(accent);
        dot.setColor(0xFF34C759);
        inner.setColor(Ui.TEXT);
        inner.setTypeface(Ui.MEDIUM);
        inner.setTextAlign(Paint.Align.CENTER);
        inner.setTextSize(Ui.dp(c, app ? 14 : 17));
        under.setColor(Ui.TEXT);
        under.setTypeface(Ui.MEDIUM);
        under.setTextAlign(Paint.Align.CENTER);
        under.setTextSize(Ui.dp(c, 13));

        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(title);
        setScaleX(1f);
        setScaleY(1f);
    }

    void setMoving(boolean on) {
        moving = on;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Height stays at the focused size, so the row does not jump as focus moves.
        setMeasuredDimension(Math.round(cardW * shown), Math.round(cardH * FOCUS_SCALE) + labelH);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int w = right - left, h = bottom - top;
        int band = h - labelH;
        int ch = Math.min(band, Math.round(cardH * shown));
        int y = (band - ch) / 2;
        card.set(0, y, Math.min(w, Math.round(cardW * shown)), y + ch);
        clip.reset();
        clip.addRoundRect(card, radius, radius, Path.Direction.CW);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        if (isFocused() && w != oldW) keepCentered();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        animate().cancel();
        setScaleX(1f);
        setScaleY(1f);
        animateSize(focused ? FOCUS_SCALE : 1f);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (grow != null) grow.cancel();
        removeCallbacks(recenter);
        recenterPosted = false;
        super.onDetachedFromWindow();
    }

    /**
     * Grows the layout slot, not a drawing scale. The row then slides the other cards
     * along and the margin between them stays where it is.
     */
    private void animateSize(float target) {
        if (grow != null) grow.cancel();
        if (shown == target) return;
        grow = ValueAnimator.ofFloat(shown, target);
        grow.setDuration(180);
        grow.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            requestLayout();
        });
        grow.start();
    }

    /** Holds the focused card still on screen while its extra width pushes the others aside. */
    private void keepCentered() {
        if (recenterPosted) return;
        recenterPosted = true;
        post(recenter);
        post(() -> recenterPosted = false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int cw = Math.round(card.width()), ch = Math.round(card.height());
        if (cw <= 0 || ch <= 0) {
            cw = cardW;
            ch = cardH;
        }
        Drawable banner = item.banner, icon = item.icon;
        canvas.save();
        canvas.clipPath(clip);
        canvas.translate(card.left, card.top);
        canvas.drawRect(0, 0, cw, ch, fill);
        if (banner != null) {
            drawBanner(canvas, banner, cw, ch);
        } else if (icon != null) {
            int size = ch * 46 / 100;
            int left = (cw - size) / 2, top = ch * 12 / 100;
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(canvas);
            drawLine(canvas, inner, cw / 2f, ch - ch * 13 / 100f, cw);
        } else if (!Catalog.APPS.equals(kind)) {
            drawLine(canvas, inner, cw / 2f, ch / 2f - (inner.ascent() + inner.descent()) / 2, cw);
        }
        if (moving) canvas.drawColor(0x99000000);
        canvas.restore();

        if (item.state == TvInputManager.INPUT_STATE_CONNECTED) {
            float r = Ui.dp(getContext(), 3.5f);
            canvas.drawCircle(card.right - r * 3, card.top + r * 3, r, dot);
        }
        if (isFocused() || moving) {
            float half = stroke / 2;
            ring.setPathEffect(moving ? new DashPathEffect(new float[] {stroke * 3, stroke * 2}, 0) : null);
            ring.setColor(moving ? accent : 0x33B0B0B0);
            canvas.drawRoundRect(card.left + half, card.top + half, card.right - half,
                    card.bottom - half, radius - half, radius - half, ring);
        }
        if (moving) {
            canvas.save();
            canvas.translate(card.left, card.top);
            drawArrows(canvas, cw, ch);
            canvas.restore();
        }
        boolean label = banner != null && (labelAlways || labelOnFocus && isFocused());
        if (labelH > 0 && label) {
            drawLine(canvas, under, card.centerX(), getHeight() - Ui.dp(getContext(), 6), cw);
        }
    }

    /** Fills the card with the banner, centre-cropping any overflow instead of stretching it. */
    private void drawBanner(Canvas canvas, Drawable banner, int cw, int ch) {
        int iw = banner.getIntrinsicWidth(), ih = banner.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) {
            banner.setBounds(0, 0, cw, ch);
        } else {
            float scale = Math.max((float) cw / iw, (float) ch / ih);
            int w = Math.round(iw * scale), h = Math.round(ih * scale);
            int left = (cw - w) / 2, top = (ch - h) / 2;
            banner.setBounds(left, top, left + w, top + h);
        }
        banner.draw(canvas);
    }

    private void drawLine(Canvas canvas, TextPaint paint, float x, float baseline, int cw) {
        float room = cw - Ui.dp(getContext(), 16);
        CharSequence s = TextUtils.ellipsize(title, paint, room, TextUtils.TruncateAt.END);
        canvas.drawText(s, 0, s.length(), x, baseline, paint);
    }

    private void drawArrows(Canvas canvas, int cw, int ch) {
        float h = ch / 6f, w = h * 0.7f, mid = ch / 2f, pad = h * 0.8f;
        inner.setStyle(Paint.Style.FILL);
        arrow.reset();
        arrow.moveTo(pad, mid);
        arrow.lineTo(pad + w, mid - h / 2);
        arrow.lineTo(pad + w, mid + h / 2);
        arrow.close();
        arrow.moveTo(cw - pad, mid);
        arrow.lineTo(cw - pad - w, mid - h / 2);
        arrow.lineTo(cw - pad - w, mid + h / 2);
        arrow.close();
        canvas.drawPath(arrow, inner);
    }
}
