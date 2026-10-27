package com.liquidglass.keyboard;

import java.util.ArrayList;
import java.util.List;

/** Row definitions for every page of the keyboard. */
public final class KeyboardLayout {

    public static final int LETTERS = 0;
    public static final int NUMBERS = 1;
    public static final int SYMBOLS = 2;
    public static final int EMOJI   = 3;

    private static final String[] EMOJI_CHARS = {
            "\uD83D\uDE00", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE01",
            "\uD83D\uDE06", "\uD83D\uDE05", "\uD83D\uDE02", "\uD83E\uDD23",
            "\uD83D\uDE0A", "\uD83D\uDE07", "\uD83D\uDE42", "\uD83D\uDE43",
            "\uD83D\uDE09", "\uD83D\uDE0C", "\uD83D\uDE0D", "\uD83E\uDD70",
            "\uD83D\uDE18", "\uD83D\uDE0B", "\uD83D\uDE1B", "\uD83D\uDE1C",
            "\uD83E\uDD2A", "\uD83E\uDD28", "\uD83E\uDDD0", "\uD83E\uDD13",
            "\uD83D\uDE0E", "\uD83E\uDD73", "\uD83D\uDE0F", "\uD83D\uDE12",
            "\uD83D\uDE1E", "\uD83D\uDE14", "\uD83D\uDE1F", "\uD83D\uDE15",
            "\uD83D\uDE41", "\uD83D\uDE29", "\uD83D\uDE22", "\uD83D\uDE2D",
            "\uD83D\uDE24", "\uD83D\uDE20", "\uD83D\uDE21", "\uD83E\uDD14",
            "\uD83E\uDD2F", "\uD83D\uDE34", "\uD83D\uDE31", "\uD83D\uDE28",
            "\uD83D\uDE30", "\uD83D\uDE25", "\uD83D\uDE13", "\uD83E\uDD79"
    };

    private KeyboardLayout() {
    }

    public static List<List<Key>> rows(int page) {
        List<List<Key>> out = new ArrayList<List<Key>>();
        if (page == NUMBERS) {
            out.add(chars("1234567890"));
            out.add(chars("-/:;()\u20B9&@\""));
            List<Key> r3 = new ArrayList<Key>();
            r3.add(Key.fn("#+=", "page:sym", 1.5f));
            r3.add(Key.ch("."));
            r3.add(Key.ch(","));
            r3.add(Key.ch("?"));
            r3.add(Key.ch("!"));
            r3.add(Key.ch("'"));
            r3.add(Key.fn("\u232B", "back", 1.5f));
            out.add(r3);
            out.add(bottomRow("ABC", "page:abc"));
        } else if (page == SYMBOLS) {
            out.add(chars("[]{}#%^*+="));
            out.add(chars("_\\|~<>\u20AC\u00A3\u00A5\u2022"));
            List<Key> r3 = new ArrayList<Key>();
            r3.add(Key.fn("123", "page:123", 1.5f));
            r3.add(Key.ch("."));
            r3.add(Key.ch(","));
            r3.add(Key.ch("?"));
            r3.add(Key.ch("!"));
            r3.add(Key.ch("'"));
            r3.add(Key.fn("\u232B", "back", 1.5f));
            out.add(r3);
            out.add(bottomRow("ABC", "page:abc"));
        } else if (page == EMOJI) {
            for (int r = 0; r < 6; r++) {
                List<Key> row = new ArrayList<Key>();
                for (int c = 0; c < 8; c++) {
                    int i = r * 8 + c;
                    if (i < EMOJI_CHARS.length) {
                        row.add(new Key(EMOJI_CHARS[i], EMOJI_CHARS[i], 1f, Key.NORMAL, null));
                    }
                }
                out.add(row);
            }
            List<Key> last = new ArrayList<Key>();
            last.add(Key.fn("ABC", "page:abc", 1.6f));
            last.add(Key.fn("123", "page:123", 1.2f));
            last.add(Key.fn("space", "space", 4.2f));
            last.add(Key.fn("\u232B", "back", 1.6f));
            out.add(last);
        } else {
            out.add(letters("qwertyuiop"));
            List<Key> r2 = new ArrayList<Key>();
            r2.add(Key.spacer(0.5f));
            r2.addAll(letters("asdfghjkl"));
            r2.add(Key.spacer(0.5f));
            out.add(r2);
            List<Key> r3 = new ArrayList<Key>();
            r3.add(Key.fn("\u21E7", "shift", 1.5f));
            r3.addAll(letters("zxcvbnm"));
            r3.add(Key.fn("\u232B", "back", 1.5f));
            out.add(r3);
            List<Key> r4 = new ArrayList<Key>();
            r4.add(Key.fn("123", "page:123", 1.3f));
            r4.add(Key.fn("\u263A", "page:emo", 1f));
            r4.add(Key.ch(",", 0.9f));
            r4.add(Key.fn("space", "space", 3.9f));
            r4.add(Key.ch(".", 0.9f));
            r4.add(Key.fn("return", "enter", 1.9f));
            out.add(r4);
        }
        return out;
    }

    private static List<Key> bottomRow(String pageLabel, String pageCode) {
        List<Key> r = new ArrayList<Key>();
        r.add(Key.fn(pageLabel, pageCode, 1.5f));
        r.add(Key.fn("\u263A", "page:emo", 1f));
        r.add(Key.ch(",", 0.9f));
        r.add(Key.fn("space", "space", 3.7f));
        r.add(Key.ch(".", 0.9f));
        r.add(Key.fn("return", "enter", 1.9f));
        return r;
    }

    private static List<Key> chars(String s) {
        List<Key> row = new ArrayList<Key>();
        for (int i = 0; i < s.length(); i++) {
            row.add(Key.ch(String.valueOf(s.charAt(i))));
        }
        return row;
    }

    private static List<Key> letters(String s) {
        List<Key> row = new ArrayList<Key>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            row.add(new Key(String.valueOf(c), String.valueOf(c), 1f, Key.NORMAL, altsFor(c)));
        }
        return row;
    }

    /** Long-press alternates. */
    private static String[] altsFor(char c) {
        switch (c) {
            case 'a': return new String[]{"\u00E0", "\u00E1", "\u00E2", "\u00E4", "\u00E3", "\u00E5", "\u00E6"};
            case 'e': return new String[]{"\u00E8", "\u00E9", "\u00EA", "\u00EB", "\u0113", "\u0117", "\u0119"};
            case 'i': return new String[]{"\u00EE", "\u00EF", "\u00ED", "\u012B", "\u00EC"};
            case 'o': return new String[]{"\u00F4", "\u00F6", "\u00F3", "\u00F2", "\u0153", "\u00F8", "\u014D"};
            case 'u': return new String[]{"\u00FB", "\u00FC", "\u00FA", "\u00F9", "\u016B"};
            case 'c': return new String[]{"\u00E7", "\u0107", "\u010D"};
            case 'n': return new String[]{"\u00F1", "\u0144"};
            case 's': return new String[]{"\u00DF", "\u015B", "\u0161"};
            case 'y': return new String[]{"\u00FF", "\u00FD"};
            case 'z': return new String[]{"\u017E", "\u017A", "\u017C"};
            case 'l': return new String[]{"\u0142"};
            case 'g': return new String[]{"\u011F"};
            case 'r': return new String[]{"\u0159"};
            case 't': return new String[]{"\u0165"};
            case 'd': return new String[]{"\u010F"};
            case 'p': return new String[]{"\u00FE"};
            default:  return null;
        }
    }
}
