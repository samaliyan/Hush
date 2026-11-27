package io.github.samaliyan.hush;

import android.content.Intent;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** "Clean up old texts": lists the advertising texts already on the phone; the user removes them in one go. */
public final class CleanActivity extends Screen {
    private static final int LIMIT = 50_000;
    private List<Cleaner.Found> found;          // null while searching
    private final Set<Long> unchecked = new HashSet<>();
    private boolean working;
    private String result;
    private TextView goButton;

    @Override String title() {
        return "Clean up old texts";
    }

    @Override protected void onResume() {
        super.onResume();
        if (found == null && !working) search();
        else if (!working) rebuild();       // the permission may have been given meanwhile
    }

    private void search() {
        working = true;
        rebuild();
        new Thread(() -> {
            List<Cleaner.Found> f = Cleaner.find(this, 0, LIMIT);
            onUi(() -> {
                found = f;
                working = false;
                rebuild();
            });
        }, "hush-find").start();
    }

    @Override void build() {
        SmsStore store = new SmsStore(this);
        if (!store.canRead()) {
            LinearLayout c = ui.card();
            c.addView(ui.text("Hush needs SMS access first. Allow it on the main screen.", 15, ui.text, Ui.REGULAR));
            content.addView(c, ui.matchWrap(8));
            return;
        }
        if (found == null) {
            LinearLayout c = ui.card();
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.addView(new ProgressBar(this));
            TextView t = ui.text("Looking through your texts…", 15, ui.sub, Ui.REGULAR);
            t.setPadding(0, ui.dp(10), 0, 0);
            c.addView(t);
            content.addView(c, ui.matchWrap(8));
            return;
        }

        if (result != null) {
            LinearLayout r = ui.card();
            r.setBackground(ui.round(ui.goodSoft, 18));
            r.addView(ui.text(result, 15, ui.text, Ui.REGULAR));
            content.addView(r, ui.matchWrap(8));
        }

        boolean write = store.canWrite();
        if (!write) {
            LinearLayout w = ui.card();
            w.setBackground(ui.round(ui.warnSoft, 18));
            w.addView(ui.text("Hush can show these texts but not remove them yet: the permission to remove is missing.", 15, ui.text, Ui.REGULAR));
            TextView b = ui.softButton("How to give it", v -> startActivity(new Intent(this, SetupActivity.class)));
            w.addView(b, ui.matchWrap(12));
            content.addView(w, ui.matchWrap(8));
        }

        if (found.isEmpty()) {
            LinearLayout c = ui.card();
            c.addView(ui.text("No advertising texts found.", 16, ui.text, Ui.MEDIUM));
            TextView t = ui.text("Texts from contacts, login codes and bank texts are never counted.", 14, ui.sub, Ui.REGULAR);
            t.setPadding(0, ui.dp(6), 0, 0);
            c.addView(t);
            content.addView(c, ui.matchWrap(8));
            return;
        }

        LinearLayout head = ui.card();
        head.addView(ui.text(found.size() + (found.size() == 1 ? " advertising text found" : " advertising texts found"), 17, ui.text, Ui.MEDIUM));
        TextView hint = ui.text("Untick any text you want to keep. Removed texts stay in “Removed texts” for "
                + Vault.KEEP_DAYS + " days, so you can restore them.", 14, ui.sub, Ui.REGULAR);
        hint.setPadding(0, ui.dp(6), 0, 0);
        head.addView(hint);
        goButton = ui.button("", v -> removeSelected());
        updateButton();
        head.addView(goButton, ui.matchWrap(14));
        LinearLayout sel = new LinearLayout(this);
        sel.setPadding(0, ui.dp(8), 0, 0);
        TextView all = ui.softButton("Select all", v -> {
            unchecked.clear();
            rebuild();
        });
        TextView none = ui.softButton("Select none", v -> {
            for (Cleaner.Found f : found) unchecked.add(f.msg.id);
            rebuild();
        });
        Ui.setBusy(all, working);
        Ui.setBusy(none, working);
        sel.addView(all, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = ui.dp(10);
        sel.addView(none, lp);
        head.addView(sel);
        content.addView(head, ui.matchWrap(8));

        int shown = 0;
        for (Cleaner.Found f : found) {
            if (++shown > 500) {
                TextView more = ui.text("… and " + (found.size() - 500) + " more (all are removed together).", 14, ui.sub, Ui.REGULAR);
                more.setPadding(ui.dp(6), ui.dp(12), 0, 0);
                content.addView(more);
                break;
            }
            content.addView(item(f), ui.matchWrap(8));
        }
    }

    private LinearLayout item(final Cleaner.Found f) {
        LinearLayout c = ui.card();
        c.setPadding(ui.dp(14), ui.dp(12), ui.dp(16), ui.dp(12));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox box = new CheckBox(this);
        box.setChecked(!unchecked.contains(f.msg.id));
        box.setEnabled(!working);
        box.setContentDescription("Remove this text");
        box.setOnCheckedChangeListener((b, on) -> {
            if (on) unchecked.remove(f.msg.id);
            else unchecked.add(f.msg.id);
            updateButton();
        });
        top.addView(box);
        LinearLayout texts = ui.column();
        TextView from = ui.text(f.msg.sender == null ? "Unknown sender" : f.msg.sender, 15, ui.text, Ui.MEDIUM);
        from.setTextDirection(android.view.View.TEXT_DIRECTION_LTR);
        texts.addView(from);
        texts.addView(ui.text(when(f.msg.date) + "  ·  " + f.verdict.reason.label, 12, ui.sub, Ui.REGULAR));
        top.addView(texts, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        c.addView(top);
        TextView body = ui.messageText(f.msg.body, 14, ui.text, 4);
        body.setPadding(ui.dp(4), ui.dp(8), 0, 0);
        c.addView(body);
        c.setOnClickListener(v -> box.toggle());
        return c;
    }

    private void updateButton() {
        if (goButton == null || found == null) return;
        int n = 0;
        for (Cleaner.Found f : found) if (!unchecked.contains(f.msg.id)) n++;
        goButton.setText(working ? "Removing…" : "Remove " + n + (n == 1 ? " text" : " texts"));
        Ui.setBusy(goButton, working || n == 0 || !new SmsStore(this).canWrite());
    }

    private void removeSelected() {
        if (working || found == null) return;
        final List<Cleaner.Found> list = new ArrayList<>();
        for (Cleaner.Found f : found) if (!unchecked.contains(f.msg.id)) list.add(f);
        if (list.isEmpty()) return;
        working = true;
        rebuild();
        new Thread(() -> {
            Cleaner.Result r = Cleaner.remove(this, list);
            List<Cleaner.Found> again = Cleaner.find(this, 0, LIMIT);
            onUi(() -> {
                working = false;
                unchecked.clear();
                found = again;
                result = r.denied == 0
                        ? "Removed " + r.removed + (r.removed == 1 ? " text." : " texts.")
                        : "Removed " + r.removed + ". Android did not allow removing " + r.denied + ".";
                rebuild();
            });
        }, "hush-clean").start();
    }
}
