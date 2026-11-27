package io.github.samaliyan.hush;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Copies of the removed texts (last {@link Vault#KEEP_DAYS} days), each can be put back. */
public final class RemovedActivity extends Screen {
    private final Set<Long> busy = new HashSet<>();

    @Override String title() {
        return "Removed texts";
    }

    @Override protected void onResume() {
        super.onResume();
        rebuild();
    }

    @Override void build() {
        List<Vault.Item> items = Vault.get(this).list();
        TextView intro = ui.text("Copies are kept for " + Vault.KEEP_DAYS + " days, then deleted. "
                + "Restore puts a text back into " + new SmsStore(this).defaultSmsAppName() + ".", 14, ui.sub, Ui.REGULAR);
        intro.setPadding(ui.dp(6), ui.dp(4), ui.dp(6), ui.dp(4));
        content.addView(intro);

        if (items.isEmpty()) {
            LinearLayout c = ui.card();
            c.addView(ui.text("Nothing removed in the last " + Vault.KEEP_DAYS + " days.", 16, ui.text, Ui.REGULAR));
            content.addView(c, ui.matchWrap(10));
            return;
        }
        for (Vault.Item i : items) content.addView(item(i), ui.matchWrap(8));

        TextView clear = ui.softButton("Delete all copies", v -> new AlertDialog.Builder(this, ui.dialogTheme())
                .setTitle("Delete all copies?")
                .setMessage("The removed texts can then no longer be restored.")
                .setPositiveButton("Delete", (d, w) -> {
                    Vault.get(this).clear();
                    rebuild();
                })
                .setNegativeButton("Cancel", null)
                .show());
        content.addView(clear, ui.matchWrap(18));
    }

    private LinearLayout item(final Vault.Item i) {
        LinearLayout c = ui.card();
        c.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = ui.column();
        TextView from = ui.text(i.sender.isEmpty() ? "Unknown sender" : i.sender, 15, ui.text, Ui.MEDIUM);
        from.setTextDirection(android.view.View.TEXT_DIRECTION_LTR);
        texts.addView(from);
        texts.addView(ui.text("Received " + when(i.date) + "  ·  " + i.reason, 12, ui.sub, Ui.REGULAR));
        top.addView(texts, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView restore = ui.softButton(busy.contains(i.id) ? "Restoring…" : "Restore", v -> askRestore(i));
        Ui.setBusy(restore, busy.contains(i.id));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = ui.dp(10);
        top.addView(restore, lp);
        c.addView(top);
        TextView body = ui.messageText(i.body, 14, ui.text, 6);
        body.setPadding(0, ui.dp(8), 0, 0);
        c.addView(body);
        return c;
    }

    private void askRestore(final Vault.Item i) {
        if (!new SmsStore(this).canWrite()) {
            new AlertDialog.Builder(this, ui.dialogTheme())
                    .setTitle("Permission to remove is missing")
                    .setMessage("Putting a text back needs the same permission as removing it.")
                    .setPositiveButton("How to give it", (d, w) -> startActivity(new Intent(this, SetupActivity.class)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        final CheckBox always = new CheckBox(this);
        boolean isNumber = !Rules.normalizeNumber(i.sender).isEmpty();
        always.setText("Never remove texts from " + i.sender);
        always.setTextColor(ui.text);
        always.setChecked(true);
        LinearLayout box = ui.column();
        box.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), 0);
        if (isNumber) box.addView(always);
        new AlertDialog.Builder(this, ui.dialogTheme())
                .setTitle("Restore this text?")
                .setMessage("It goes back into " + new SmsStore(this).defaultSmsAppName() + " as a read text. It may take a moment to appear there.")
                .setView(isNumber ? box : null)
                .setPositiveButton("Restore", (d, w) -> restore(i, isNumber && always.isChecked()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restore(final Vault.Item i, final boolean allowSender) {
        busy.add(i.id);
        rebuild();
        new Thread(() -> {
            Prefs prefs = new Prefs(this);
            // remembered first, so the store watcher never removes it again
            prefs.addRestored(i.sender, i.body);
            if (allowSender) prefs.addAllowNumber(i.sender);
            boolean ok = new SmsStore(this).restore(i.sender, i.body, i.date);
            if (ok) {
                Vault.get(this).delete(i.id);
                prefs.addRemoved(-1);       // the counters count texts that stayed removed
            }
            onUi(() -> {
                busy.remove(i.id);
                Toast.makeText(this, ok ? "Restored" : "Android did not allow putting it back.", Toast.LENGTH_LONG).show();
                rebuild();
            });
        }, "hush-restore").start();
    }
}
