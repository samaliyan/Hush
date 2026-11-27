package io.github.samaliyan.hush;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Base of every screen: a scrolling column with a title (and a back arrow on inner screens). */
abstract class Screen extends Activity {
    Ui ui;
    LinearLayout content;
    private ScrollView scroll;

    /** Screen title, or null for the main screen (it draws its own header). */
    abstract String title();

    /** Fills {@link #content}. Called on open and on {@link #rebuild()}. */
    abstract void build();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        ui.applyWindow(this);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        int side = Math.max(ui.dp(16), (getResources().getDisplayMetrics().widthPixels - ui.dp(640)) / 2);
        content = ui.column();
        content.setPadding(side, ui.dp(8), side, ui.dp(32));
        scroll.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        // keep content clear of the status and navigation bars (Android 15 draws edge to edge)
        scroll.setOnApplyWindowInsetsListener((v, in) -> {
            @SuppressWarnings("deprecation") int top = in.getSystemWindowInsetTop();
            @SuppressWarnings("deprecation") int bottom = in.getSystemWindowInsetBottom();
            v.setPadding(0, top, 0, bottom);
            return in;
        });
        rebuild();
    }

    /** Builds the screen again, keeping the scroll position. */
    void rebuild() {
        final int y = scroll.getScrollY();
        content.removeAllViews();
        String t = title();
        if (t != null) content.addView(header(t));
        build();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private LinearLayout header(String t) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, ui.dp(6), 0, ui.dp(6));
        TextView back = ui.text("‹", 30, ui.text, Ui.REGULAR);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setBackground(ui.ripple(null, 0));
        back.setOnClickListener(v -> finish());
        h.addView(back, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(48)));
        TextView title = ui.text(t, 22, ui.text, Ui.MEDIUM);
        title.setPadding(ui.dp(4), 0, 0, 0);
        h.addView(title);
        return h;
    }

    /** Runs on the UI thread if the screen is still open. */
    void onUi(Runnable r) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) r.run();
        });
    }

    static String when(long t) {
        long d = System.currentTimeMillis() - t;
        if (d < 60_000) return "just now";
        if (d < 3600_000) return (d / 60_000) + " min ago";
        java.util.Calendar now = java.util.Calendar.getInstance(), c = java.util.Calendar.getInstance();
        c.setTimeInMillis(t);
        String time = android.text.format.DateFormat.format("HH:mm", c).toString();
        if (now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == c.get(java.util.Calendar.DAY_OF_YEAR)) return "Today " + time;
        now.add(java.util.Calendar.DAY_OF_YEAR, -1);
        if (now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)
                && now.get(java.util.Calendar.DAY_OF_YEAR) == c.get(java.util.Calendar.DAY_OF_YEAR)) return "Yesterday " + time;
        return android.text.format.DateFormat.format("d MMM yyyy, HH:mm", c).toString();
    }
}
