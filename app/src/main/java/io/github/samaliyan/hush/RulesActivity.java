package io.github.samaliyan.hush;

import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** The user's own lists, and a box to try a text against all rules. */
public final class RulesActivity extends Screen {
    private String testSender = "", testBody = "";

    @Override String title() {
        return "Rules";
    }

    private interface Getter {
        List<String> get(Prefs p);
    }

    private interface Setter {
        void set(Prefs p, List<String> v);
    }

    @Override void build() {
        Prefs prefs = new Prefs(this);
        TextView intro = ui.text("Your lists come first. Login codes and bank texts are always kept, "
                + "except from a sender on your Block list.", 14, ui.sub, Ui.REGULAR);
        intro.setPadding(ui.dp(6), ui.dp(4), ui.dp(6), 0);
        content.addView(intro);

        list(prefs, "Block senders", "A number, the start of a number (for example 0935), or a sender name.", true,
                Prefs::blockNumbers, Prefs::setBlockNumbers);
        list(prefs, "Block words", "Texts that contain the word or phrase.", false,
                Prefs::blockWords, Prefs::setBlockWords);
        list(prefs, "Allow senders", "Texts from these numbers or sender names are never removed.", true,
                Prefs::allowNumbers, Prefs::setAllowNumbers);
        list(prefs, "Allow words", "Texts that contain the word or phrase are never removed.", false,
                Prefs::allowWords, Prefs::setAllowWords);

        // ---- try a text
        content.addView(ui.section("Try a text"));
        LinearLayout t = ui.card();
        EditText sender = field("Sender (number or name)", testSender, LINE);
        EditText body = field("Text", testBody, MULTI);
        body.setMinLines(2);
        t.addView(sender);
        t.addView(body, ui.matchWrap(8));
        TextView answer = ui.text("", 15, ui.text, Ui.MEDIUM);
        answer.setPadding(ui.dp(4), ui.dp(12), 0, 0);
        Runnable check = () -> {
            testSender = sender.getText().toString();
            testBody = body.getText().toString();
            if (testSender.trim().isEmpty() && testBody.trim().isEmpty()) {
                answer.setText("");
                return;
            }
            Rules.Verdict v = new Prefs(this).rules().check(testSender, testBody, new SmsStore(this).isContact(testSender));
            answer.setTextColor(v.remove ? ui.danger : ui.good);
            answer.setText((v.remove ? "Removed" : "Kept") + "  ·  " + v.reason.label);
        };
        android.text.TextWatcher w = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override public void afterTextChanged(android.text.Editable e) {
                check.run();
            }
        };
        sender.addTextChangedListener(w);
        body.addTextChangedListener(w);
        check.run();
        t.addView(answer);
        content.addView(t);
    }

    private void list(Prefs prefs, String title, String hint, boolean numbers, Getter get, Setter set) {
        content.addView(ui.section(title));
        LinearLayout c = ui.card();
        c.setPadding(ui.dp(16), ui.dp(10), ui.dp(10), ui.dp(12));
        TextView h = ui.text(hint, 13, ui.sub, Ui.REGULAR);
        h.setPadding(0, 0, 0, ui.dp(6));
        c.addView(h);
        List<String> items = get.get(prefs);
        for (int idx = 0; idx < items.size(); idx++) {
            final int at = idx;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            TextView v = ui.messageText(items.get(idx), 15, ui.text, 2);
            r.addView(v, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView x = ui.text("✕", 16, ui.sub, Ui.REGULAR);
            x.setGravity(Gravity.CENTER);
            x.setContentDescription("Delete " + items.get(idx));
            x.setBackground(ui.ripple(null, 0));
            x.setOnClickListener(b -> {
                List<String> now = get.get(prefs);
                if (at < now.size()) now.remove(at);
                set.set(prefs, now);
                rebuild();
            });
            r.addView(x, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
            c.addView(r);
        }
        LinearLayout add = new LinearLayout(this);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setPadding(0, ui.dp(6), ui.dp(6), 0);
        EditText f = field(numbers ? "Add a number or name" : "Add a word", "", LINE);
        TextView b = ui.softButton("Add", null);
        Runnable doAdd = () -> {
            String s = f.getText().toString().trim();
            if (s.isEmpty()) return;
            List<String> now = get.get(prefs);
            if (!now.contains(s)) now.add(s);
            set.set(prefs, now);
            android.view.inputmethod.InputMethodManager im = getSystemService(android.view.inputmethod.InputMethodManager.class);
            if (im != null) im.hideSoftInputFromWindow(f.getWindowToken(), 0);
            rebuild();
        };
        b.setOnClickListener(v -> doAdd.run());
        f.setImeOptions(EditorInfo.IME_ACTION_DONE);
        f.setOnEditorActionListener((v, id, e) -> {
            doAdd.run();
            return true;
        });
        add.addView(f, new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = ui.dp(10);
        add.addView(b, lp);
        c.addView(add);
        content.addView(c);
    }

    private static final int LINE = 0, MULTI = 1;

    private EditText field(String hint, String value, int kind) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setContentDescription(hint);
        e.setText(value);
        e.setTextColor(ui.text);
        e.setHintTextColor(ui.sub);
        e.setTextSize(15);
        e.setTextDirection(android.view.View.TEXT_DIRECTION_FIRST_STRONG);
        e.setInputType(kind == MULTI ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_CLASS_TEXT);
        if (kind == LINE) e.setSingleLine(true);
        e.setBackground(ui.round(ui.bg, 12));
        e.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        return e;
    }
}
