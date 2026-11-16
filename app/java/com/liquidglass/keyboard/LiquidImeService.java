/*
 * Liquid Glass Keyboard — generation 2
 * Copyright (c) 2026 Liquid Glass Keyboard contributors
 *
 * Released under the MIT License. See the LICENSE file in the project root
 * for the full text, or https://opensource.org/licenses/MIT
 */
package com.liquidglass.keyboard;

import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/** A complete offline input method: no network, no telemetry, no account. */
public class LiquidImeService extends InputMethodService implements KeyboardView.Listener {

    private KeyboardView keyboard;

    @Override
    public View onCreateInputView() {
        keyboard = new KeyboardView(this);
        keyboard.setListener(this);
        GlassPalette pal = GlassPalette.resolve(this);
        keyboard.setPalette(pal);
        applyBackdropBlur(pal);
        return keyboard;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboard == null) return;
        // Resolve once per show instead of three times: each resolve reads the
        // preference file, and this runs every time the keyboard comes up.
        GlassPalette pal = GlassPalette.resolve(this);
        keyboard.setPalette(pal);
        keyboard.reset(shouldAutoShift(info));
        keyboard.setEnterLabel(enterLabelFor(info));
        applyBackdropBlur(pal);
    }

    /**
     * Ask the system to blur whatever sits behind the keyboard window. Supported on
     * Android 12+ where cross-window blur is enabled; everywhere else the board keeps
     * its own painted backdrop, so nothing breaks.
     */
    private void applyBackdropBlur(GlassPalette pal) {
        boolean applied = false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Window window = getWindow() == null ? null : getWindow().getWindow();
                if (window != null && pal.blurDp > 0) {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null || wm.isCrossWindowBlurEnabled()) {
                        int radius = (int) (pal.blurDp
                                * getResources().getDisplayMetrics().density);
                        WindowManager.LayoutParams lp = window.getAttributes();
                        // Setting the attributes relayouts the IME window, so only do
                        // it when the radius really changed; a show used to do it always.
                        boolean changed = (lp.flags
                                & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) == 0
                                || lp.getBlurBehindRadius() != radius;
                        if (changed) {
                            lp.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                            lp.setBlurBehindRadius(radius);
                            window.setAttributes(lp);
                        }
                        applied = true;
                    }
                }
            }
        } catch (Throwable ignored) {
            applied = false;
        }
        if (keyboard != null) keyboard.setBlurBackdrop(applied);
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // A custom board should never be blown up to fullscreen.
        return false;
    }

    private boolean shouldAutoShift(EditorInfo info) {
        if (info == null) return false;
        int type = info.inputType & InputType.TYPE_MASK_CLASS;
        if (type != InputType.TYPE_CLASS_TEXT) return false;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
            return false;
        }
        int flags = info.inputType & (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        return flags != 0;
    }

    private String enterLabelFor(EditorInfo info) {
        if (info == null) return "return";
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "return";
        switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_GO: return "go";
            case EditorInfo.IME_ACTION_SEARCH: return "search";
            case EditorInfo.IME_ACTION_SEND: return "send";
            case EditorInfo.IME_ACTION_NEXT: return "next";
            case EditorInfo.IME_ACTION_DONE: return "done";
            case EditorInfo.IME_ACTION_PREVIOUS: return "prev";
            default: return "return";
        }
    }

    // ------------------------------------------------------------- Listener

    @Override
    public void onText(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || s == null) return;
        ic.commitText(s, 1);
        if (keyboard != null && s.length() > 0) {
            char last = s.charAt(s.length() - 1);
            if (last == '.' || last == '!' || last == '?') {
                keyboard.setShift(true);
            }
        }
    }

    @Override
    public void onBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        CharSequence selection = ic.getSelectedText(0);
        if (selection != null && selection.length() > 0) {
            ic.commitText("", 1);
            return;
        }
        CharSequence before = ic.getTextBeforeCursor(4, 0);
        int n = 1;
        if (before != null && before.length() >= 2) {
            char last = before.charAt(before.length() - 1);
            char prev = before.charAt(before.length() - 2);
            if (Character.isLowSurrogate(last) && Character.isHighSurrogate(prev)) {
                n = 2; // step over an emoji or other surrogate pair
            }
        }
        ic.deleteSurroundingText(n, 0);
    }

    @Override
    public void onEnter() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null) return;

        int action = ei == null ? EditorInfo.IME_ACTION_NONE
                : (ei.imeOptions & EditorInfo.IME_MASK_ACTION);
        boolean noAction = ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;

        if (!noAction && action != EditorInfo.IME_ACTION_NONE
                && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
            return;
        }
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    }
}
