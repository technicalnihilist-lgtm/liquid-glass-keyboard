/*
 * Liquid Glass Keyboard — generation 3
 * Copyright (c) 2026 Liquid Glass Keyboard contributors
 *
 * Released under the MIT License. See the LICENSE file in the project root
 * for the full text, or https://opensource.org/licenses/MIT
 */
package com.liquidglass.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Setup, live status and the glass tuning controls. */
public class SetupActivity extends Activity {

    private static final String P = GlassPalette.PREFS;

    private TextView status;
    private GlassBackdrop backdrop;
    private GlassPalette pal;
    private SharedPreferences sp;
    private final List<Button> segButtons = new ArrayList<Button>();
    private String segValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        sp = getSharedPreferences(P, MODE_PRIVATE);

        backdrop = (GlassBackdrop) findViewById(R.id.backdrop);
        status = (TextView) findViewById(R.id.status);
        pal = GlassPalette.resolve(this);
        backdrop.setPalette(pal);
        tint(findViewById(android.R.id.content), pal.ink);

        EditText field = (EditText) findViewById(R.id.tryField);
        field.setHintTextColor(pal.inkDim);
        field.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        findViewById(R.id.btnEnable).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            }
        });

        findViewById(R.id.btnSelect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });

        buildGlassCard();
        buildKeysCard();
        buildAppearanceCard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    // ------------------------------------------------------------------ ui

    private TextView label(String text, float sizeSp, boolean bold, float alpha) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(pal.ink);
        t.setAlpha(alpha);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private void section(LinearLayout card, String text) {
        TextView t = label(text, 12f, true, 0.75f);
        t.setLetterSpacing(0.12f);
        card.addView(t);
    }

    /** A labelled slider that writes an int preference. */
    private void slider(LinearLayout card, String title, final String key,
                        final int min, final int max, final String suffix) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        TextView name = label(title, 16f, false, 1f);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView value = label("", 15f, true, 0.7f);

        row.addView(name);
        row.addView(value);
        card.addView(row);

        int def = sp.getInt(key, defaultValue(key));
        final SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, def - min)));
        value.setText((min + bar.getProgress()) + suffix);

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int v = min + progress;
                value.setText(v + suffix);
                if (fromUser) sp.edit().putInt(key, v).apply();
            }

            @Override public void onStartTrackingTouch(SeekBar sb) { }

            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        card.addView(bar);
    }

    private int defaultValue(String key) {
        if ("glass_a".equals(key)) return 14;
        if ("gap".equals(key)) return 6;
        if ("kh".equals(key)) return 52;
        if ("blur".equals(key)) return 22;
        if ("sat".equals(key)) return 170;
        if ("bounce".equals(key)) return 70;
        return 0;
    }

    private void toggle(LinearLayout card, String title, final String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);

        TextView name = label(title, 16f, false, 1f);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                sp.edit().putBoolean(key, checked).apply();
            }
        });

        row.addView(name);
        row.addView(sw);
        card.addView(row);
    }

    private void segmented(LinearLayout card, String title, String key, String def,
                           String[] values, String[] labels) {
        section(card, title);
        segValue = sp.getString(key, def);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        segButtons.clear();

        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            b.setAllCaps(false);
            b.setBackgroundResource(value.equals(segValue)
                    ? R.drawable.btn_glass : android.R.color.transparent);
            b.setTextColor(pal.ink);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    segValue = value;
                    sp.edit().putString("theme", value).apply();
                    for (Button other : segButtons) {
                        other.setBackgroundResource(other == v
                                ? R.drawable.btn_glass : android.R.color.transparent);
                    }
                    pal = GlassPalette.resolve(SetupActivity.this);
                    backdrop.setPalette(pal);
                    tint(findViewById(android.R.id.content), pal.ink);
                }
            });
            segButtons.add(b);
            row.addView(b);
        }
        card.addView(row);
    }

    private void buildGlassCard() {
        LinearLayout card = (LinearLayout) findViewById(R.id.cardGlass);
        section(card, "GLASS");
        slider(card, "Transparency", "glass_a", 0, 45, "%");
        slider(card, "Frosting", "blur", 0, 50, " px");
        slider(card, "Colour boost", "sat", 100, 300, "%");
        toggle(card, "Floating colours", "orbs", true);
    }

    private void buildKeysCard() {
        LinearLayout card = (LinearLayout) findViewById(R.id.cardKeys);
        section(card, "KEYBOARD");
        slider(card, "Key height", "kh", 38, 70, " px");
        slider(card, "Space between keys", "gap", 2, 14, " px");
        toggle(card, "Glow on press", "glow", true);
    }

    private void buildAppearanceCard() {
        LinearLayout card = (LinearLayout) findViewById(R.id.cardAppearance);
        section(card, "APPEARANCE");
        segmented(card, "THEME", "theme", "auto",
                new String[]{"auto", "light", "dark"},
                new String[]{"Auto", "Light", "Dark"});
        slider(card, "Bounce", "bounce", 0, 100, "%");
        toggle(card, "Key click sound", "sound", true);
        toggle(card, "Haptic tap", "haptics", true);

        Button reset = new Button(this);
        reset.setText("Reset to defaults");
        reset.setAllCaps(false);
        reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        reset.setBackgroundResource(R.drawable.btn_glass);
        reset.setTextColor(pal.ink);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(16), 0, 0);
        reset.setLayoutParams(lp);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit()
                        .putInt("glass_a", 14).putInt("blur", 22).putInt("sat", 170)
                        .putInt("kh", 52).putInt("gap", 6).putInt("bounce", 70)
                        .putBoolean("glow", true).putBoolean("orbs", true)
                        .putString("theme", "auto")
                        .putBoolean("sound", true).putBoolean("haptics", true)
                        .apply();
                recreate();
            }
        });
        card.addView(reset);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // -------------------------------------------------------------- status

    private void updateStatus() {
        boolean enabled = false, selected = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            List<InputMethodInfo> list = imm.getEnabledInputMethodList();
            for (InputMethodInfo info : list) {
                if (getPackageName().equals(info.getPackageName())) {
                    enabled = true;
                    break;
                }
            }
            String def = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            selected = def != null && def.startsWith(getPackageName() + "/");
        }
        if (selected) {
            status.setText("Active — this is your current keyboard.");
        } else if (enabled) {
            status.setText("Enabled. Tap “Choose keyboard” and pick Liquid Glass.");
        } else {
            status.setText("Not enabled yet. Do the two steps below.");
        }
    }

    private void tint(View v, int color) {
        if (v instanceof TextView) {
            ((TextView) v).setTextColor(color);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                tint(g.getChildAt(i), color);
            }
        }
    }
}
