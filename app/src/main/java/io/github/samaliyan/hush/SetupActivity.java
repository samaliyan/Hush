package io.github.samaliyan.hush;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** How to give Hush the permission to remove texts (one command from a computer). Checks it live. */
public final class SetupActivity extends Screen {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean lastState;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            boolean now = new SmsStore(SetupActivity.this).canWrite();
            if (now != lastState) {
                lastState = now;
                if (now) {
                    new Prefs(SetupActivity.this).setLastDenied(0);
                    SweepJob.schedule(SetupActivity.this);
                }
                rebuild();
            }
            handler.postDelayed(this, 1500);
        }
    };

    @Override String title() {
        return "Permission to remove";
    }

    @Override protected void onResume() {
        super.onResume();
        lastState = new SmsStore(this).canWrite();
        rebuild();
        handler.postDelayed(poll, 1500);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(poll);
        super.onPause();
    }

    @Override void build() {
        SmsStore store = new SmsStore(this);
        boolean done = store.canWrite();
        String cmd = SmsStore.adbCommand(this);

        LinearLayout status = ui.card();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(done ? ui.chip("Done", ui.good, ui.goodSoft) : ui.chip("Not given yet", ui.warn, ui.warnSoft));
        status.addView(top);
        TextView s = ui.text(done
                ? "Hush can remove texts. The permission stays after restarts and app updates. You only need to repeat this if you uninstall Hush."
                : "Android lets only the default messaging app remove texts. One command from a computer gives Hush the same permission, while "
                + store.defaultSmsAppName() + " stays your messaging app. You do this once.", 15, ui.text, Ui.REGULAR);
        s.setPadding(0, ui.dp(10), 0, 0);
        status.addView(s);
        content.addView(status, ui.matchWrap(8));

        content.addView(ui.section("On the phone"));
        LinearLayout phone = ui.card();
        phone.addView(numbered(1, "Open Settings › About phone › Software information, and tap Build number 7 times. "
                + "Enter your PIN if asked. (This turns on Developer options.)"));
        phone.addView(numbered(2, "Go back to Settings. Open Developer options (at the bottom) and turn on USB debugging."));
        content.addView(phone);

        content.addView(ui.section("On the computer"));
        LinearLayout pc = ui.card();
        pc.addView(numbered(3, "Download “SDK Platform-Tools” for your computer from developer.android.com/tools/releases/platform-tools "
                + "and unzip it."));
        pc.addView(numbered(4, "Connect the phone with a USB cable. On the phone, tap Allow when it asks “Allow USB debugging?”."));
        pc.addView(numbered(5, "Open a command window in the unzipped platform-tools folder and run this command:"));

        TextView code = ui.text(cmd, 14, ui.text, Ui.MONO);
        code.setTextIsSelectable(true);
        code.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        code.setBackground(ui.round(ui.bg, 12));
        pc.addView(code, ui.matchWrap(10));
        TextView note = ui.text("On Windows, type .\\ before adb in PowerShell:  .\\adb shell …", 13, ui.sub, Ui.REGULAR);
        note.setPadding(0, ui.dp(8), 0, 0);
        pc.addView(note);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(0, ui.dp(12), 0, 0);
        TextView copy = ui.softButton("Copy", v -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("adb command", cmd));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        });
        TextView share = ui.softButton("Send to computer", v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, cmd);
            try {
                startActivity(Intent.createChooser(i, "Send the command"));
            } catch (Exception ignored) {
                // no app to share with
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        buttons.addView(copy, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp2.leftMargin = ui.dp(10);
        buttons.addView(share, lp2);
        pc.addView(buttons);
        pc.addView(numbered(6, "Come back here. This page shows Done within a few seconds. You can then turn USB debugging off again."));
        content.addView(pc);
    }

    private LinearLayout numbered(int n, String s) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, ui.dp(8), 0, ui.dp(8));
        TextView num = ui.text(String.valueOf(n), 14, ui.accent, Ui.MEDIUM);
        num.setGravity(Gravity.CENTER);
        num.setBackground(ui.round(ui.accentSoft, 14));
        r.addView(num, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));
        TextView t = ui.text(s, 15, ui.text, Ui.REGULAR);
        t.setPadding(ui.dp(12), ui.dp(3), 0, 0);
        r.addView(t, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return r;
    }
}
