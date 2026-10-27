package com.liquidglass.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.List;

/** Setup, live status and preferences for the keyboard. */
public class SetupActivity extends Activity {

    private TextView status;
    private GlassBackdrop backdrop;
    private GlassPalette pal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

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

        setupTheme();
        setupSize();
        setupToggles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

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
            status.setText("Active \u2014 this is your current keyboard.");
        } else if (enabled) {
            status.setText("Enabled. Tap \u201cChoose keyboard\u201d and pick Liquid Glass.");
        } else {
            status.setText("Not enabled yet. Do the two steps below.");
        }
    }

    private void setupTheme() {
        RadioGroup g = (RadioGroup) findViewById(R.id.radioTheme);
        String cur = getSharedPreferences(GlassPalette.PREFS, MODE_PRIVATE)
                .getString("theme", "system");
        int id = "dark".equals(cur) ? R.id.themeDark
                : "light".equals(cur) ? R.id.themeLight : R.id.themeSystem;
        g.check(id);
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String v = checkedId == R.id.themeDark ? "dark"
                        : checkedId == R.id.themeLight ? "light" : "system";
                getSharedPreferences(GlassPalette.PREFS, MODE_PRIVATE)
                        .edit().putString("theme", v).apply();
                pal = GlassPalette.resolve(SetupActivity.this);
                backdrop.setPalette(pal);
                tint(findViewById(android.R.id.content), pal.ink);
                ((EditText) findViewById(R.id.tryField)).setHintTextColor(pal.inkDim);
            }
        });
    }

    private void setupSize() {
        RadioGroup g = (RadioGroup) findViewById(R.id.radioSize);
        String cur = getSharedPreferences(GlassPalette.PREFS, MODE_PRIVATE)
                .getString("keysize", "medium");
        int id = "small".equals(cur) ? R.id.sizeSmall
                : "large".equals(cur) ? R.id.sizeLarge : R.id.sizeMedium;
        g.check(id);
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String v = checkedId == R.id.sizeSmall ? "small"
                        : checkedId == R.id.sizeLarge ? "large" : "medium";
                getSharedPreferences(GlassPalette.PREFS, MODE_PRIVATE)
                        .edit().putString("keysize", v).apply();
            }
        });
    }

    private void setupToggles() {
        final RadioButton sound = (RadioButton) findViewById(R.id.toggleSound);
        final RadioButton haptics = (RadioButton) findViewById(R.id.toggleHaptics);
        final RadioButton motion = (RadioButton) findViewById(R.id.toggleMotion);
        sound.setChecked(GlassPalette.soundOn(this));
        haptics.setChecked(GlassPalette.hapticsOn(this));
        motion.setChecked(GlassPalette.motionOn(this));
        sound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences(GlassPalette.PREFS, MODE_PRIVATE)
                        .edit().putBoolean("sound", sound.isChecked()).apply();
            }
        });
        haptics.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences(GlassPalette.PREFS, MODE_PRIVATE)
                        .edit().putBoolean("haptics", haptics.isChecked()).apply();
            }
        });
        motion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSharedPreferences(GlassPalette.PREFS, MODE_PRIVATE)
                        .edit().putBoolean("motion", motion.isChecked()).apply();
            }
        });
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
