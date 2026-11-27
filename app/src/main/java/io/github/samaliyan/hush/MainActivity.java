package io.github.samaliyan.hush;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Main screen: status, setup steps, the message tools and settings. */
public final class MainActivity extends Screen {
    private static final int REQ_SMS = 1, REQ_CONTACTS = 2;
    private static final long UPDATE_CHECK_EVERY = 3600_000L;
    private Updater updater;

    @Override String title() {
        return null;
    }

    @Override protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        SweepJob.schedule(this);
    }

    @Override protected void onResume() {
        super.onResume();
        rebuild();
        Prefs p = new Prefs(this);
        if (updater != null && System.currentTimeMillis() - p.lastUpdateCheck() > UPDATE_CHECK_EVERY) updater.check(true);
    }

    @Override protected void onDestroy() {
        if (updater != null) updater.destroy();
        super.onDestroy();
    }

    @Override void build() {
        if (updater == null) updater = new Updater(this, ui);
        Prefs prefs = new Prefs(this);
        SmsStore store = new SmsStore(this);
        boolean read = store.canRead(), write = store.canWrite(), notif = NotifListener.connected() || listenerEnabled();
        boolean contacts = store.canReadContacts();
        String appName = store.defaultSmsAppName();

        // ---- header
        TextView name = ui.text("Hush", 30, ui.text, Ui.MEDIUM);
        name.setPadding(ui.dp(6), ui.dp(18), 0, 0);
        content.addView(name);
        TextView tag = ui.text("Removes advertising texts from " + appName + ".", 15, ui.sub, Ui.REGULAR);
        tag.setPadding(ui.dp(6), ui.dp(4), 0, ui.dp(4));
        content.addView(tag);

        // ---- status
        LinearLayout status = ui.card();
        String state, detail;
        int fg, bg;
        if (!read || !write) {
            state = "Setup needed";
            detail = "Finish the steps below. Until then nothing is removed.";
            fg = ui.warn;
            bg = ui.warnSoft;
        } else if (!prefs.enabled()) {
            state = "Paused";
            detail = "Removing is switched off in Settings below.";
            fg = ui.sub;
            bg = ui.line;
        } else {
            state = "Active";
            detail = "Advertising texts are removed as soon as they arrive.";
            fg = ui.good;
            bg = ui.goodSoft;
        }
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(ui.chip(state, fg, bg));
        status.addView(top);
        TextView d = ui.text(detail, 15, ui.text, Ui.REGULAR);
        d.setPadding(0, ui.dp(10), 0, 0);
        status.addView(d);

        Vault vault = Vault.get(this);
        java.util.Calendar midnight = java.util.Calendar.getInstance();
        midnight.set(java.util.Calendar.HOUR_OF_DAY, 0);
        midnight.set(java.util.Calendar.MINUTE, 0);
        midnight.set(java.util.Calendar.SECOND, 0);
        midnight.set(java.util.Calendar.MILLISECOND, 0);
        LinearLayout stats = new LinearLayout(this);
        stats.setPadding(0, ui.dp(16), 0, 0);
        stats.addView(stat("Today", vault.countSince(midnight.getTimeInMillis())), weight());
        stats.addView(stat("Last 7 days", vault.countSince(System.currentTimeMillis() - Vault.KEEP_DAYS * 24L * 3600_000L)), weight());
        stats.addView(stat("Total", prefs.removedTotal()), weight());
        status.addView(stats);
        content.addView(status, ui.matchWrap(14));

        // ---- warning when removing was refused
        if (prefs.lastDenied() > 0 && read) {
            LinearLayout w = ui.card();
            w.setBackground(ui.round(ui.warnSoft, 18));
            String msg = write
                    ? "Your phone refused to remove a text even though Hush has the permission. "
                    + "Please tell the developer which phone and Android version you use."
                    : "Hush found advertising texts but could not remove them: the permission to remove is missing. "
                    + "Finish “Permission to remove” below.";
            w.addView(ui.text(msg, 14, ui.text, Ui.REGULAR));
            content.addView(w, ui.matchWrap(12));
        }

        // ---- setup
        content.addView(ui.section("Setup"));
        LinearLayout setup = ui.card();
        setup.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(4));
        setup.addView(step("SMS access", "To check each new text.", read, false, false, v -> askSms()));
        setup.addView(ui.divider());
        setup.addView(step("Permission to remove", "One time, from a computer.", write, false, true,
                v -> startActivity(new Intent(this, SetupActivity.class))));
        setup.addView(ui.divider());
        setup.addView(step("Hide their notifications", "Takes away the notification of a removed text.", notif, true, true,
                v -> openListenerSettings()));
        setup.addView(ui.divider());
        setup.addView(step("Keep texts from contacts", "Texts from saved contacts are never removed.", contacts, true, false,
                v -> askContacts()));
        content.addView(setup);

        // ---- messages
        content.addView(ui.section("Messages"));
        LinearLayout msgs = ui.card();
        msgs.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(4));
        msgs.addView(ui.row("Clean up old texts", "Find advertising texts already on the phone.", null,
                v -> startActivity(new Intent(this, CleanActivity.class))));
        msgs.addView(ui.divider());
        msgs.addView(ui.row("Removed texts", "Kept for " + Vault.KEEP_DAYS + " days. You can restore any of them.", null,
                v -> startActivity(new Intent(this, RemovedActivity.class))));
        msgs.addView(ui.divider());
        msgs.addView(ui.row("Rules", "Always allow or always block senders and words.", null,
                v -> startActivity(new Intent(this, RulesActivity.class))));
        content.addView(msgs);

        // ---- settings
        content.addView(ui.section("Settings"));
        LinearLayout set = ui.card();
        set.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(4));
        set.addView(ui.switchRow("Remove advertising texts", null, prefs.enabled(), (s, on) -> {
            prefs.setEnabled(on);
            rebuild();
        }));
        set.addView(ui.divider());
        set.addView(ui.switchRow("Advertising lines",
                "Also remove texts from numbers that start with 1000, 2000, 3000 or 5000. Login codes and bank texts from them are kept.",
                prefs.adLines(), (s, on) -> prefs.setAdLines(on)));
        set.addView(ui.divider());
        set.addView(ui.switchRow("Sound only for real texts",
                "Android plays the sound before a text can be removed. With this on, " + appName
                        + " stays silent and Hush plays the sound only for texts it keeps.",
                prefs.soundForReal(), (s, on) -> {
                    prefs.setSoundForReal(on);
                    if (on) explainSound(appName);
                }));
        content.addView(set);

        // ---- about
        content.addView(ui.section("About"));
        LinearLayout about = ui.card();
        about.setPadding(ui.dp(14), ui.dp(4), ui.dp(14), ui.dp(4));
        about.addView(ui.row("Check for updates", null, "Version " + updater.versionName(), v -> updater.check(false)));
        about.addView(ui.divider());
        about.addView(ui.row("Project page", "github.com/" + Updater.REPO, null,
                v -> open(Uri.parse(Updater.REPO_URL))));
        content.addView(about);
    }

    // ------------------------------------------------------------------ pieces

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private LinearLayout stat(String label, long n) {
        LinearLayout l = ui.column();
        l.addView(ui.text(String.valueOf(n), 22, ui.text, Ui.MEDIUM));
        TextView t = ui.text(label, 13, ui.sub, Ui.REGULAR);
        t.setPadding(0, ui.dp(2), 0, 0);
        l.addView(t);
        return l;
    }

    /** A setup step; a finished one is no longer tappable, except where it can be changed back. */
    private LinearLayout step(String title, String detail, boolean done, boolean optional, boolean tapWhenDone,
                              android.view.View.OnClickListener click) {
        boolean tappable = !done || tapWhenDone;
        LinearLayout r = ui.row(title, detail, null, tappable ? click : null);
        TextView chip = done ? ui.chip("Done", ui.good, ui.goodSoft)
                : optional ? ui.chip("Optional", ui.sub, ui.line) : ui.chip("Needed", ui.warn, ui.warnSoft);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = ui.dp(10);
        r.addView(chip, tappable ? r.getChildCount() - 1 : r.getChildCount(), lp);      // before the "›"
        return r;
    }

    private void explainSound(String appName) {
        new AlertDialog.Builder(this, ui.dialogTheme())
                .setTitle("Silence " + appName)
                .setMessage("Turn off the sound of " + appName + "'s message notifications. The notifications stay; "
                        + "only the sound stops.\n\nHush then plays the phone's notification sound for each text it keeps, "
                        + "and nothing for advertising. It follows Silent, Vibrate and Do Not Disturb.")
                .setPositiveButton("Open its settings", (dlg, w) -> openAppNotificationSettings(new SmsStore(this).defaultSmsApp()))
                .setNegativeButton("Later", null)
                .show();
    }

    // ------------------------------------------------------------------ actions

    private void askSms() {
        requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS}, REQ_SMS);
    }

    private void askContacts() {
        requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        boolean all = res.length > 0;
        for (int r : res) all &= r == PackageManager.PERMISSION_GRANTED;
        if (!all && perms.length > 0 && !shouldShowRequestPermissionRationale(perms[0])) {
            // Android does not ask again after "Don't allow" twice: send the user to the app's settings
            new AlertDialog.Builder(this, ui.dialogTheme())
                    .setTitle(code == REQ_SMS ? "SMS access is off" : "Contacts access is off")
                    .setMessage("Android will not ask again. Open Hush's settings, tap Permissions and allow "
                            + (code == REQ_SMS ? "SMS." : "Contacts."))
                    .setPositiveButton("Open settings", (d, w) -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null))))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
        if (all && code == REQ_SMS) {
            SweepJob.schedule(this);
            new Thread(() -> Cleaner.autoPass(this), "hush-first").start();
        }
        rebuild();
    }

    private boolean listenerEnabled() {
        ComponentName me = new ComponentName(this, NotifListener.class);
        if (Build.VERSION.SDK_INT >= 27) {
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            return nm != null && nm.isNotificationListenerAccessGranted(me);
        }
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(me.flattenToString());
    }

    private void openListenerSettings() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 30) {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            new ComponentName(this, NotifListener.class).flattenToString());
            if (i.resolveActivity(getPackageManager()) != null) {
                open(i);
                return;
            }
        }
        open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void openAppNotificationSettings(String pkg) {
        if (pkg == null) {
            open(new Intent(Settings.ACTION_SETTINGS));
            return;
        }
        open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg));
    }

    private void open(Uri u) {
        open(new Intent(Intent.ACTION_VIEW, u));
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Could not open it on this phone.", android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
