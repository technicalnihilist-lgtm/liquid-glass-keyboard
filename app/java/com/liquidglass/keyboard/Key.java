/*
 * Liquid Glass Keyboard — generation 2
 * Copyright (c) 2026 Liquid Glass Keyboard contributors
 *
 * Released under the MIT License. See the LICENSE file in the project root
 * for the full text, or https://opensource.org/licenses/MIT
 */
package com.liquidglass.keyboard;

/** One key on the board. */
public final class Key {

    public static final int NORMAL = 0;   // types a character
    public static final int FN = 1;       // runs an action token
    public static final int SPACER = 2;   // invisible width filler

    public final String label;
    public final String code;
    public final float weight;
    public final int type;
    public final String[] alts;

    public Key(String label, String code, float weight, int type, String[] alts) {
        this.label = label;
        this.code = code;
        this.weight = weight;
        this.type = type;
        this.alts = alts;
    }

    public static Key ch(String c) {
        return new Key(c, c, 1f, NORMAL, null);
    }

    public static Key ch(String c, float w) {
        return new Key(c, c, w, NORMAL, null);
    }

    public static Key fn(String label, String code, float w) {
        return new Key(label, code, w, FN, null);
    }

    public static Key spacer(float w) {
        return new Key("", "", w, SPACER, null);
    }
}
