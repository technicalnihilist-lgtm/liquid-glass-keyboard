package com.liquidglass.keyboard;

import android.content.Context;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.graphics.Color;

/** Colour tokens for the liquid-glass look, second generation. */
public final class GlassPalette {

    public static final String PREFS = "liquid_glass_keyboard";

    public final boolean dark;
    public final int bg1, bg2;
    public final int ink, inkDim;
    public final int orb1, orb2, orb3, orb4;

    /** Key glass: specular top, body, deep bottom. */
    public final int keyTop, keyMid, keyBot;
    public final int keyRimTop, keyRimBot, keyEdge, keySpec;

    /** Plate (the sheet the keys sit on). */
    public final int kbTop, kbMid, kbBot, kbEdge, kbSpec;

    private GlassPalette(boolean dark) {
        this.dark = dark;
        orb1 = 0xFF2B6BFF;
        orb2 = 0xFFFF3EA5;
        orb3 = 0xFF19E0C0;
        orb4 = 0xFFFFB02E;
        if (dark) {
            bg1 = 0xFF050A22;
            bg2 = 0xFF1B0733;
            ink = 0xFFFFFFFF;
            inkDim = 0x8CFFFFFF;

            keyTop = 0x5CFFFFFF;
            keyMid = 0x17FFFFFF;
            keyBot = 0x07FFFFFF;
            keyRimTop = 0xC7FFFFFF;
            keyRimBot = 0x10FFFFFF;
            keyEdge = 0x70FFFFFF;
            keySpec = 0xD9FFFFFF;

            kbTop = 0x2BFFFFFF;
            kbMid = 0x12FFFFFF;
            kbBot = 0x05FFFFFF;
            kbEdge = 0x52FFFFFF;
            kbSpec = 0x99FFFFFF;
        } else {
            bg1 = 0xFFC6D9FF;
            bg2 = 0xFFF7D9F6;
            ink = 0xFF0B1230;
            inkDim = 0x8C0B1230;

            keyTop = 0xE0FFFFFF;
            keyMid = 0xA8FFFFFF;
            keyBot = 0x5CFFFFFF;
            keyRimTop = 0xFFFFFFFF;
            keyRimBot = 0x2EFFFFFF;
            keyEdge = 0xEBFFFFFF;
            keySpec = 0xFFFFFFFF;

            kbTop = 0x47FFFFFF;
            kbMid = 0x21FFFFFF;
            kbBot = 0x0FFFFFFF;
            kbEdge = 0x5CFFFFFF;
            kbSpec = 0xFFFFFFFF;
        }
    }

    public static GlassPalette resolve(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String t = sp.getString("theme", "system");
        boolean dark;
        if ("dark".equals(t)) {
            dark = true;
        } else if ("light".equals(t)) {
            dark = false;
        } else {
            dark = (c.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }
        return new GlassPalette(dark);
    }

    public static boolean soundOn(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("sound", true);
    }

    public static boolean hapticsOn(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("haptics", true);
    }

    public static boolean motionOn(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("motion", true);
    }

    /** Key height in dp. */
    public static int keyHeightDp(Context c) {
        String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("keysize", "medium");
        if ("small".equals(s)) return 46;
        if ("large".equals(s)) return 58;
        return 52;
    }

    /** HSL to ARGB. h in degrees, s and l in 0..1. */
    public static int hsl(float h, float s, float l, int alpha) {
        h = ((h % 360f) + 360f) % 360f;
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float x = c * (1f - Math.abs(((h / 60f) % 2f) - 1f));
        float m = l - c / 2f;
        float r, g, b;
        int seg = (int) (h / 60f);
        switch (seg) {
            case 0:  r = c; g = x; b = 0f; break;
            case 1:  r = x; g = c; b = 0f; break;
            case 2:  r = 0f; g = c; b = x; break;
            case 3:  r = 0f; g = x; b = c; break;
            case 4:  r = x; g = 0f; b = c; break;
            default: r = c; g = 0f; b = x; break;
        }
        return Color.argb(alpha, clamp(Math.round((r + m) * 255f)),
                clamp(Math.round((g + m) * 255f)), clamp(Math.round((b + m) * 255f)));
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.round(255f * Math.max(0f, Math.min(1f, alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Same hue and saturation, new alpha relative to the original alpha. */
    public static int scaleAlpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * Math.max(0f, factor));
        return (color & 0x00FFFFFF) | (Math.min(255, a) << 24);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
