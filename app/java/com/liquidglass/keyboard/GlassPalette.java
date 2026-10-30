/*
 * Liquid Glass Keyboard — generation 3
 * Copyright (c) 2026 Liquid Glass Keyboard contributors
 *
 * Released under the MIT License. See the LICENSE file in the project root
 * for the full text, or https://opensource.org/licenses/MIT
 */
package com.liquidglass.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

/** Colour tokens plus the live glass tuning read from preferences. */
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

    /** Live tuning. */
    public final float alphaScale;   // glass opacity multiplier
    public final int gapDp;
    public final int keyHeightDp;
    public final int blurDp;
    public final float orbBoost;     // saturation as a multiplier on orb alpha
    public final float bounce;       // 0..1, how far a key sinks when pressed
    public final boolean glow;
    public final boolean orbs;

    private GlassPalette(boolean dark, float alphaScale, int gapDp, int keyHeightDp,
                         int blurDp, float orbBoost, float bounce, boolean glow, boolean orbs) {
        this.dark = dark;
        this.alphaScale = alphaScale;
        this.gapDp = gapDp;
        this.keyHeightDp = keyHeightDp;
        this.blurDp = blurDp;
        this.orbBoost = orbBoost;
        this.bounce = bounce;
        this.glow = glow;
        this.orbs = orbs;

        orb1 = 0xFF2B6BFF;
        orb2 = 0xFFFF3EA5;
        orb3 = 0xFF19E0C0;
        orb4 = 0xFFFFB02E;

        int kt, km, kb, krt, krb, ke, ks, pt, pm, pb, pe, ps;
        if (dark) {
            bg1 = 0xFF050A22;
            bg2 = 0xFF1B0733;
            ink = 0xFFFFFFFF;
            inkDim = 0x8CFFFFFF;
            kt = 0x5CFFFFFF; km = 0x17FFFFFF; kb = 0x07FFFFFF;
            krt = 0xC7FFFFFF; krb = 0x10FFFFFF; ke = 0x70FFFFFF; ks = 0xD9FFFFFF;
            pt = 0x2BFFFFFF; pm = 0x12FFFFFF; pb = 0x05FFFFFF;
            pe = 0x52FFFFFF; ps = 0x99FFFFFF;
        } else {
            bg1 = 0xFFC6D9FF;
            bg2 = 0xFFF7D9F6;
            ink = 0xFF0B1230;
            inkDim = 0x8C0B1230;
            kt = 0xE0FFFFFF; km = 0xA8FFFFFF; kb = 0x5CFFFFFF;
            krt = 0xFFFFFFFF; krb = 0x2EFFFFFF; ke = 0xEBFFFFFF; ks = 0xFFFFFFFF;
            pt = 0x47FFFFFF; pm = 0x21FFFFFF; pb = 0x0FFFFFFF;
            pe = 0x5CFFFFFF; ps = 0xFFFFFFFF;
        }

        keyTop = scaleAlpha(kt, alphaScale);
        keyMid = scaleAlpha(km, alphaScale);
        keyBot = scaleAlpha(kb, alphaScale);
        keyRimTop = scaleAlpha(krt, alphaScale);
        keyRimBot = scaleAlpha(krb, alphaScale);
        keyEdge = scaleAlpha(ke, Math.min(1f, 0.35f + alphaScale * 0.65f));
        keySpec = scaleAlpha(ks, Math.min(1f, 0.5f + alphaScale * 0.5f));

        kbTop = scaleAlpha(pt, alphaScale);
        kbMid = scaleAlpha(pm, alphaScale);
        kbBot = scaleAlpha(pb, alphaScale);
        kbEdge = scaleAlpha(pe, Math.min(1f, 0.4f + alphaScale * 0.6f));
        kbSpec = scaleAlpha(ps, Math.min(1f, 0.5f + alphaScale * 0.5f));
    }

    public static GlassPalette resolve(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String t = sp.getString("theme", "auto");
        boolean dark;
        if ("dark".equals(t)) {
            dark = true;
        } else if ("light".equals(t)) {
            dark = false;
        } else {
            dark = (c.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }

        int a = sp.getInt("glass_a", 14);            // 0..45 percent
        int gap = sp.getInt("gap", 6);               // 2..14 dp
        int kh = sp.getInt("kh", 52);                // 38..70 dp
        int blur = sp.getInt("blur", 22);            // 0..50 dp
        int sat = sp.getInt("sat", 170);             // 100..300 percent
        int bnc = sp.getInt("bounce", 70);           // 0..100

        return new GlassPalette(dark,
                Math.max(0f, a) / 14f,
                gap, kh, blur,
                Math.max(0.2f, sat / 170f),
                Math.max(0f, Math.min(1f, bnc / 100f)),
                sp.getBoolean("glow", true),
                sp.getBoolean("orbs", true));
    }

    public static boolean soundOn(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("sound", true);
    }

    public static boolean hapticsOn(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("haptics", true);
    }

    /** Kept for older callers: the orbs switch replaced the motion switch. */
    public static boolean motionOn(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("orbs", true);
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
