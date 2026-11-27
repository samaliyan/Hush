package io.github.samaliyan.hush;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Colours and small view factories shared by all screens (the UI is built in code). */
final class Ui {
    final Context ctx;
    final boolean dark;
    final int bg, surface, text, sub, line, accent, accentSoft, onAccent, good, goodSoft, warn, warnSoft, danger;

    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MONO = Typeface.MONOSPACE;

    Ui(Context c) {
        ctx = c;
        dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            bg = 0xFF0E1116; surface = 0xFF171B22; text = 0xFFECEFF4; sub = 0xFF9AA3AF; line = 0xFF262C36;
            accent = 0xFF8AB4FF; accentSoft = 0xFF1B2A42; onAccent = 0xFF0B1220;
            good = 0xFF5BD08A; goodSoft = 0xFF15301F; warn = 0xFFF2B65A; warnSoft = 0xFF372A14; danger = 0xFFF0605A;
        } else {
            bg = 0xFFF4F6F9; surface = 0xFFFFFFFF; text = 0xFF12161C; sub = 0xFF66707D; line = 0xFFE3E7ED;
            accent = 0xFF2563EB; accentSoft = 0xFFE7EEFD; onAccent = 0xFFFFFFFF;
            good = 0xFF15803D; goodSoft = 0xFFE3F5EA; warn = 0xFFB45309; warnSoft = 0xFFFDF1DE; danger = 0xFFDC3B33;
        }
    }

    @SuppressWarnings("deprecation")
    void applyWindow(Activity a) {
        Window w = a.getWindow();
        w.getDecorView().setBackgroundColor(bg);
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(bg);
        int f = w.getDecorView().getSystemUiVisibility();
        if (dark) f &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        else f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(f);
    }

    int dialogTheme() {
        return dark ? R.style.Dialog_Dark : R.style.Dialog_Light;
    }

    int dp(float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    TextView text(float sp, int color, Typeface face) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setLineSpacing(dp(2), 1f);
        return t;
    }

    TextView text(CharSequence s, float sp, int color, Typeface face) {
        TextView t = text(sp, color, face);
        t.setText(s);
        return t;
    }

    /** Text of a message: Persian is shown right-to-left, English left-to-right. */
    TextView messageText(CharSequence s, float sp, int color, int maxLines) {
        TextView t = text(s, sp, color, REGULAR);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        if (maxLines > 0) {
            t.setMaxLines(maxLines);
            t.setEllipsize(TextUtils.TruncateAt.END);
        }
        return t;
    }

    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    Drawable ripple(Drawable content, float radiusDp) {
        int rc = dark ? 0x33FFFFFF : 0x1F000000;
        return new RippleDrawable(ColorStateList.valueOf(rc), content, content != null ? round(0xFFFFFFFF, radiusDp) : null);
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** White rounded card. */
    LinearLayout card() {
        LinearLayout l = column();
        l.setBackground(round(surface, 18));
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        return l;
    }

    LinearLayout.LayoutParams matchWrap(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    /** Small grey section title above a card. */
    TextView section(String s) {
        TextView t = text(s.toUpperCase(java.util.Locale.ROOT), 12, sub, MEDIUM);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(6), dp(22), dp(6), dp(8));
        return t;
    }

    /** Filled button. */
    TextView button(String label, View.OnClickListener click) {
        TextView b = text(label, 15, onAccent, MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48));
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setBackground(ripple(round(accent, 14), 14));
        b.setOnClickListener(click);
        return b;
    }

    /** Light button for secondary actions. */
    TextView softButton(String label, View.OnClickListener click) {
        TextView b = text(label, 15, accent, MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(44));
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        b.setBackground(ripple(round(accentSoft, 12), 12));
        b.setOnClickListener(click);
        return b;
    }

    /** Disables a button and shows it greyed out (for buttons that start work). */
    static void setBusy(View b, boolean busy) {
        b.setEnabled(!busy);
        b.setAlpha(busy ? 0.45f : 1f);
    }

    /** A tappable row inside a card: title, optional detail, optional value on the right. */
    LinearLayout row(String title, String detail, String value, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(ctx);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(4), dp(12), dp(4), dp(12));
        r.setMinimumHeight(dp(52));
        LinearLayout texts = column();
        texts.addView(text(title, 16, text, REGULAR));
        if (detail != null) {
            TextView d = text(detail, 13, sub, REGULAR);
            d.setPadding(0, dp(3), 0, 0);
            texts.addView(d);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (value != null) {
            TextView v = text(value, 15, sub, REGULAR);
            v.setPadding(dp(12), 0, 0, 0);
            r.addView(v);
        }
        if (click != null) {
            TextView arrow = text("›", 22, sub, REGULAR);
            arrow.setPadding(dp(10), 0, 0, dp(2));
            r.addView(arrow);
            r.setBackground(ripple(null, 0));
            r.setOnClickListener(click);
        }
        return r;
    }

    /** A row with a switch. */
    @SuppressWarnings("UseSwitchCompatOrMaterialCode")
    LinearLayout switchRow(String title, String detail, boolean on, SwitchListener l) {
        LinearLayout r = new LinearLayout(ctx);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(4), dp(12), dp(4), dp(12));
        LinearLayout texts = column();
        texts.addView(text(title, 16, text, REGULAR));
        if (detail != null) {
            TextView d = text(detail, 13, sub, REGULAR);
            d.setPadding(0, dp(3), 0, 0);
            texts.addView(d);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch s = new Switch(ctx);
        s.setChecked(on);
        s.setContentDescription(title);
        s.setPadding(dp(12), 0, 0, 0);
        s.setOnCheckedChangeListener((b, v) -> l.changed(s, v));
        r.addView(s);
        r.setOnClickListener(v -> s.toggle());
        return r;
    }

    interface SwitchListener {
        void changed(Switch s, boolean on);
    }

    View divider() {
        View v = new View(ctx);
        v.setBackgroundColor(line);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.8f))));
        return v;
    }

    /** Small rounded label ("Done", "Needed", "Optional"). */
    TextView chip(String s, int fg, int bgColor) {
        TextView t = text(s, 12, fg, MEDIUM);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(round(bgColor, 20));
        return t;
    }
}
