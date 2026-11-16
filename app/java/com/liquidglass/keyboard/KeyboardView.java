/*
 * Liquid Glass Keyboard — generation 2
 * Copyright (c) 2026 Liquid Glass Keyboard contributors
 *
 * Released under the MIT License. See the LICENSE file in the project root
 * for the full text, or https://opensource.org/licenses/MIT
 */
package com.liquidglass.keyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Second-generation liquid glass: every key is a layered pane of glass — a specular
 * top edge, a body that thins towards the bottom, a caustic pool of light along its
 * base, a bright inner rim and a soft contact shadow. The static glass is rendered
 * once into a bitmap; only the pressed key, the labels and the flare are redrawn.
 */
public class KeyboardView extends View {

    public interface Listener {
        void onText(String s);
        void onBackspace();
        void onEnter();
    }

    private static final long FLASH_MS = 620L;
    private static final long LONG_PRESS_MS = 330L;
    private static final long REPEAT_DELAY_MS = 400L;
    private static final long REPEAT_EVERY_MS = 55L;
    private static final long DRIFT_MS = 110L;

    private Listener listener;
    private GlassPalette pal = null;

    private final float density;
    private int page = KeyboardLayout.LETTERS;
    private boolean shift, caps;
    private long lastShiftTap;
    private float hue = 205f;

    private int kh;                       // key height px
    private int gap, sidePad, band, platePadTop, platePadBot, kbCorner;

    private List<List<Key>> rows = new ArrayList<List<Key>>();
    private final List<List<RectF>> rects = new ArrayList<List<RectF>>();

    // Cached geometry. The key grid only changes when the width, the row set, the key
    // height or the gap changes, so it is rebuilt on demand instead of on every frame
    // and on every touch. contentTop and pitch describe the same grid arithmetically.
    private boolean layoutDirty = true;
    private int layoutW = -1, layoutHk = -1, layoutGap = -1;
    private int contentTop, pitch;
    private Path clipPath;
    private int clipW = -1, clipH = -1;

    private Key activeKey;
    private int activeRow = -1, activeCol = -1;
    private long pressTime;
    private boolean down;
    private float pressX, pressY;

    private final List<String> altList = new ArrayList<String>();
    private final List<RectF> altRects = new ArrayList<RectF>();
    private int altIndex = -1;
    private boolean altActive;

    private String enterLabel = "return";
    private final Handler handler = new Handler(Looper.getMainLooper());
    // One runnable per timer role, created once and reused. A long session presses
    // thousands of keys, and this used to allocate a fresh runnable for every press.
    private final Runnable longPressRun = new Runnable() {
        @Override
        public void run() {
            if (activeKey == null || activeKey.alts == null) return;
            altList.clear();
            boolean upper = page == KeyboardLayout.LETTERS && (shift || caps);
            for (String a : activeKey.alts) {
                altList.add(upper ? a.toUpperCase() : a);
            }
            buildAltRects();
            altActive = true;
            altIndex = -1;
            invalidate();
        }
    };
    private final Runnable repeatRun = new Runnable() {
        @Override
        public void run() {
            // A tick that outlived its press must not keep deleting into a new field.
            if (!down) return;
            if (listener != null) listener.onBackspace();
            handler.postDelayed(this, REPEAT_EVERY_MS);
        }
    };

    private Bitmap base;                  // the static glass: plate + key bodies
    private boolean baseDirty = true;
    private boolean blurBackdrop;         // true when the window really blurs the app behind
    private boolean motion = true;
    private boolean attachedVisible;
    private long driftStart = SystemClock.uptimeMillis();

    // Scratch paint and geometry reused on every frame. Each use resets whatever it
    // sets, so nothing leaks from one key or one frame into the next. A soak run
    // draws tens of thousands of frames and used to allocate a Paint, a Typeface
    // lookup and a FontMetrics for every key of every frame.
    private static final Typeface LABEL_FACE =
            Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Paint bgPaint = new Paint();
    private final Paint orbPaint = new Paint();
    private final Paint flarePaint = new Paint();
    private final Paint labelPaint = new Paint();
    private final Paint litPaint = new Paint();
    private final Paint litRimPaint = new Paint();
    private final Paint pressTintPaint = new Paint();
    private final Paint pressBloomPaint = new Paint();
    private final Paint pressGlowPaint = new Paint();
    private final Paint pressRimPaint = new Paint();
    private final Paint pressSpecPaint = new Paint();
    private final Path tmpPath = new Path();
    private final RectF tmpRect = new RectF();
    private final Paint.FontMetrics fmScratch = new Paint.FontMetrics();

    public KeyboardView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(true);
        motion = GlassPalette.motionOn(c);
        initMetrics();
        rows = KeyboardLayout.rows(page);
    }

    private void initMetrics() {
        gap = dp(pal != null ? pal.gapDp : 6);
        sidePad = dp(6);
        band = dp(44);
        platePadTop = dp(10);
        platePadBot = dp(9);
        kbCorner = dp(30);
        kh = dp(pal != null ? pal.keyHeightDp : 52);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    // ------------------------------------------------------------- wiring

    public void setListener(Listener l) {
        listener = l;
    }

    public void setPalette(GlassPalette p) {
        // Rebake only when the glass itself changed. onStartInputView resolves the
        // palette on every show and this used to force a full-window bitmap rebuild
        // even when the settings were identical.
        boolean reglass = pal == null || pal.dark != p.dark
                || pal.alphaScale != p.alphaScale
                || pal.gapDp != p.gapDp || pal.keyHeightDp != p.keyHeightDp;
        pal = p;
        int oldKh = kh, oldGap = gap;
        initMetrics();
        motion = GlassPalette.motionOn(getContext());
        if (reglass) baseDirty = true;
        layoutDirty = true;
        // A new key height or gap changes the board's measured height: re-measure
        // instead of keeping the height the view was first given.
        if (oldKh != kh || oldGap != gap) requestLayout();
        invalidate();
    }

    public void setEnterLabel(String s) {
        enterLabel = s;
        invalidate();
    }

    /** True when the system blurs whatever sits behind this window. */
    public void setBlurBackdrop(boolean on) {
        if (blurBackdrop != on) {
            blurBackdrop = on;
            baseDirty = true;
            invalidate();
        }
    }

    public void reset(boolean autoShift) {
        cancelPending();
        // The baked glass depends on the page and the palette, not on the caret, the
        // enter label or the shift state, so a show that lands on the letter page the
        // board is already on keeps its bitmap.
        boolean repage = page != KeyboardLayout.LETTERS;
        page = KeyboardLayout.LETTERS;
        rows = KeyboardLayout.rows(page);
        shift = autoShift;
        caps = false;
        activeKey = null;
        down = false;
        altActive = false;
        altIndex = -1;
        altList.clear();
        altRects.clear();
        if (repage) baseDirty = true;
        layoutDirty = true;
        requestLayout();
        invalidate();
    }

    /** Used by the service for sentence auto-capitalisation. */
    public void setShift(boolean on) {
        shift = on;
        if (!on) caps = false;
        invalidate();
    }

    private int currentKeyHeight() {
        return page == KeyboardLayout.EMOJI ? Math.round(kh * 0.82f) : kh;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int hk = currentKeyHeight();
        int n = Math.max(1, rows.size());
        int h = band + platePadTop + n * hk + (n - 1) * gap + platePadBot;
        // Never claim more height than the window offers, or the bottom rows get
        // clipped by the IME window instead of the board being cut where it fits.
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            int limit = MeasureSpec.getSize(heightSpec);
            if (limit > 0 && h > limit) h = limit;
        }
        setMeasuredDimension(w, Math.max(0, h));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        baseDirty = true;
        layoutDirty = true;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        attachedVisible = visibility == VISIBLE;
        if (attachedVisible) {
            driftStart = SystemClock.uptimeMillis();
            invalidate();
        } else {
            // Hidden mid-press: drop the queued long-press and repeat, so a held
            // backspace cannot keep deleting into whatever field comes next.
            cancelPending();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // The IME window is rebuilt on every show, so a detached view used to leave
        // its full-window ARGB bitmap and its 110 ms repaint loop behind.
        attachedVisible = false;
        cancelPending();
        handler.removeCallbacksAndMessages(null);
        if (base != null) {
            base.recycle();
            base = null;
        }
        baseDirty = true;
        clipPath = null;
        clipW = -1;
        clipH = -1;
    }

    /** Rebuild the key grid only when something that shapes it actually changed. */
    private void layoutKeys() {
        int w = getWidth();
        int hk = currentKeyHeight();
        int n = rows.size();
        if (!layoutDirty && layoutW == w && layoutHk == hk && layoutGap == gap
                && rects.size() == n) {
            return;
        }
        layoutW = w;
        layoutHk = hk;
        layoutGap = gap;
        layoutDirty = false;

        rects.clear();
        float avail = Math.max(0f, w - 2f * sidePad);
        contentTop = band + platePadTop;
        pitch = hk + gap;
        float y = contentTop;
        for (int r = 0; r < n; r++) {
            List<Key> row = rows.get(r);
            int count = row.size();
            float sum = 0f;
            for (int i = 0; i < count; i++) sum += row.get(i).weight;
            if (sum <= 0f) sum = Math.max(1f, count);
            float gaps = gap * Math.max(0, count - 1);
            float unit = Math.max(0f, (avail - gaps) / sum);
            float x = sidePad;
            float right = sidePad + avail;
            List<RectF> rr = new ArrayList<RectF>(count);
            for (int i = 0; i < count; i++) {
                Key k = row.get(i);
                float kw = Math.max(0f, unit * k.weight);
                if (i == count - 1) {
                    // The last key takes up the sub-pixel drift of the sum so the row
                    // ends exactly on the right margin instead of leaving a hairline.
                    kw = Math.max(0f, right - x);
                }
                rr.add(new RectF(x, y, x + kw, y + hk));
                x += kw + gap;
            }
            rects.add(rr);
            y += pitch;
        }
    }

    /** The rect of one key, or null when the cached grid does not cover it. */
    private RectF keyRect(int r, int c) {
        if (r < 0 || r >= rects.size()) return null;
        List<RectF> row = rects.get(r);
        if (c < 0 || c >= row.size()) return null;
        return row.get(c);
    }

    /** Rows sit on a uniform pitch, so the row under y is arithmetic, not a scan. */
    private int rowAt(float y) {
        int n = rects.size();
        if (n <= 0 || pitch <= 0) return -1;
        int r = (int) Math.floor((y - contentTop + gap / 2f) / pitch);
        if (r >= n) r = n - 1;
        if (r < 0) return -1;
        // The old scan took the first row whose band covered y, so at the exact point
        // where two bands meet it picked the upper one. Keep that: step back when the
        // touch is still inside the row above.
        if (r > 0) {
            RectF prev = keyRect(r - 1, 0);
            if (prev != null && y <= prev.bottom + gap / 2f) r--;
        }
        RectF b = keyRect(r, 0);
        if (b == null) return -1;
        if (y < b.top - gap / 2f || y > b.bottom + gap / 2f) return -1;
        return r;
    }

    private int colAt(int r, float x) {
        if (r < 0 || r >= rects.size()) return -1;
        List<RectF> row = rects.get(r);
        for (int c = 0; c < row.size(); c++) {
            RectF b = row.get(c);
            if (x >= b.left - gap / 2f && x <= b.right + gap / 2f) return c;
        }
        return -1;
    }

    // -------------------------------------------------------- static glass

    private void ensureBase() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (base != null && base.getWidth() == w && base.getHeight() == h && !baseDirty) return;
        if (base != null) base.recycle();
        base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(base);
        layoutKeys();
        drawPlate(c);
        for (int r = 0; r < rows.size() && r < rects.size(); r++) {
            List<Key> row = rows.get(r);
            List<RectF> rr = rects.get(r);
            for (int i = 0; i < row.size() && i < rr.size(); i++) {
                drawKeyBody(c, row.get(i), rr.get(i));
            }
        }
        baseDirty = false;
    }

    private void drawPlate(Canvas c) {
        float w = getWidth(), h = getHeight();
        float rad = kbCorner;
        Path p = new Path();
        p.addRoundRect(new RectF(0, band, w, h + rad), rad, rad, Path.Direction.CW);

        c.save();
        c.clipPath(p);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, band, 0, h,
                new int[]{pal.kbTop, pal.kbMid, pal.kbBot},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, band, w, h, fill);

        // light gathering under the top rim, then a shadow just beneath it
        Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        sheen.setShader(new LinearGradient(0, band, 0, band + dp(26),
                pal.dark ? 0x1AFFFFFF : 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, band, w, band + dp(26), sheen);

        Paint under = new Paint(Paint.ANTI_ALIAS_FLAG);
        under.setShader(new LinearGradient(0, band, 0, band + dp(6),
                pal.dark ? 0x40000000 : 0x14000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, band, w, band + dp(6), under);
        c.restore();

        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(dp(1));
        st.setColor(pal.kbEdge);
        c.drawPath(p, st);

        // specular rim along the top of the sheet
        Paint spec = new Paint(Paint.ANTI_ALIAS_FLAG);
        spec.setColor(pal.kbSpec);
        spec.setStrokeWidth(dp(1.7f));
        spec.setStrokeCap(Paint.Cap.ROUND);
        spec.setMaskFilter(new BlurMaskFilter(dp(1.1f), BlurMaskFilter.Blur.NORMAL));
        c.drawLine(rad * 0.65f, band + dp(1.1f), w - rad * 0.65f, band + dp(1.1f), spec);
        spec.setMaskFilter(null);
    }

    private void drawKeyBody(Canvas c, Key k, RectF r) {
        if (k.type == Key.SPACER) return;
        float rad = dp(9);

        // ambient shadow: wide, soft, sits the key off the sheet
        Paint amb = new Paint(Paint.ANTI_ALIAS_FLAG);
        amb.setColor(pal.dark ? 0x3D000000 : 0x26000000);
        amb.setMaskFilter(new BlurMaskFilter(dp(6f), BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(new RectF(r.left + dp(1), r.top + dp(3), r.right - dp(1),
                r.bottom + dp(6)), rad, rad, amb);

        // contact shadow: tight, right under the glass
        Paint contact = new Paint(Paint.ANTI_ALIAS_FLAG);
        contact.setColor(pal.dark ? 0x26000000 : 0x1A000000);
        contact.setMaskFilter(new BlurMaskFilter(dp(1.6f), BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(new RectF(r.left, r.top + dp(1.4f), r.right, r.bottom + dp(2.2f)),
                rad, rad, contact);

        c.save();
        Path cp = new Path();
        cp.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(cp);

        // the pane itself: bright at the top, thinning downwards
        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                new int[]{pal.keyTop, pal.keyMid, pal.keyBot},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(r, body);

        // light source, upper left
        Paint tl = new Paint(Paint.ANTI_ALIAS_FLAG);
        tl.setShader(new RadialGradient(r.left + r.width() * 0.26f, r.top + r.height() * 0.10f,
                Math.max(dp(6), r.width() * 0.95f),
                pal.dark ? 0x44FFFFFF : 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(r, tl);

        // caustic pool along the base of the pane
        Paint caustic = new Paint(Paint.ANTI_ALIAS_FLAG);
        caustic.setShader(new RadialGradient(r.centerX(), r.bottom + r.height() * 0.18f,
                Math.max(dp(8), r.width() * 0.85f),
                pal.dark ? 0x2CFFFFFF : 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(r, caustic);

        // inner shadow where the glass meets the sheet
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setShader(new LinearGradient(0, r.bottom - dp(9), 0, r.bottom,
                0x00000000, pal.dark ? 0x26000000 : 0x14000000, Shader.TileMode.CLAMP));
        c.drawRect(new RectF(r.left, r.bottom - dp(9), r.right, r.bottom), inner);
        c.restore();

        // inner rim, bright at the top and fading to nothing at the bottom
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(dp(1.1f));
        rim.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                pal.keyRimTop, pal.keyRimBot, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(r.left + dp(0.55f), r.top + dp(0.55f),
                r.right - dp(0.55f), r.bottom - dp(0.55f)), rad, rad, rim);

        // outer edge
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(dp(0.8f));
        edge.setColor(pal.keyEdge);
        c.drawRoundRect(r, rad, rad, edge);

        // specular highlight along the top edge
        Paint spec = new Paint(Paint.ANTI_ALIAS_FLAG);
        spec.setColor(pal.keySpec);
        spec.setStrokeWidth(dp(1.3f));
        spec.setStrokeCap(Paint.Cap.ROUND);
        spec.setMaskFilter(new BlurMaskFilter(dp(0.7f), BlurMaskFilter.Blur.NORMAL));
        float inset = rad * 1.15f;
        c.drawLine(r.left + inset, r.top + dp(2f), r.right - inset, r.top + dp(2f), spec);
        spec.setMaskFilter(null);
    }

    // ------------------------------------------------------- live drawing

    @Override
    protected void onDraw(Canvas canvas) {
        if (pal == null) pal = GlassPalette.resolve(getContext());
        layoutKeys();
        ensureBase();

        float w = getWidth(), h = getHeight();
        long now = SystemClock.uptimeMillis();
        float t = (now - pressTime) / (float) FLASH_MS;
        float intensity = down ? 1f : Math.max(0f, 1f - t);
        if (intensity <= 0f) activeKey = null;

        canvas.save();
        if (clipPath == null || clipW != (int) w || clipH != (int) h) {
            clipPath = new Path();
            clipPath.addRoundRect(new RectF(0, 0, w, h + kbCorner), kbCorner, kbCorner,
                    Path.Direction.CW);
            clipW = (int) w;
            clipH = (int) h;
        }
        canvas.clipPath(clipPath);

        drawBackdrop(canvas, now);
        drawFlare(canvas, now);

        if (base != null) canvas.drawBitmap(base, 0, 0, null);

        drawStateOverlays(canvas, intensity);
        drawLabels(canvas, intensity);
        drawPreview(canvas);
        drawAltStrip(canvas);

        canvas.restore();

        if (intensity > 0f && !down && t < 1f) postInvalidateOnAnimation();
        else if (motion && attachedVisible) postInvalidateDelayed(DRIFT_MS);
    }

    private void drawBackdrop(Canvas canvas, long now) {
        float w = getWidth(), h = getHeight();
        float veil = blurBackdrop ? 0.42f : 1f;

        Paint p = bgPaint;
        p.reset();
        p.setAntiAlias(true);
        p.setShader(new LinearGradient(0, 0, w * 0.72f, h,
                GlassPalette.scaleAlpha(pal.bg1, veil),
                GlassPalette.scaleAlpha(pal.bg2, veil), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, p);

        float phase = (motion && attachedVisible && pal.orbs) ? (now - driftStart) / 1000f : 0f;
        float dx = (float) Math.sin(phase * 0.32f) * dp(16);
        float dy = (float) Math.cos(phase * 0.24f) * dp(11);
        float m = Math.max(w, h);
        float k = (pal.dark ? 1f : 0.6f) * (blurBackdrop ? 0.55f : 1f) * pal.orbBoost;

        if (!pal.orbs) { k = 0f; }
        orb(canvas, w * 0.02f + dx, h * 0.00f + dy, m * 0.64f, pal.orb1, 0.58f * k);
        orb(canvas, w * 1.00f - dx * 0.6f, h * 0.22f - dy, m * 0.56f, pal.orb2, 0.5f * k);
        orb(canvas, w * 0.05f - dx * 0.4f, h * 1.02f + dy * 0.5f, m * 0.6f, pal.orb3, 0.44f * k);
        orb(canvas, w * 0.95f, h * 0.88f, m * 0.44f, pal.orb4, 0.36f * k);
    }

    private void orb(Canvas canvas, float cx, float cy, float r, int color, float alpha) {
        Paint p = orbPaint;
        p.reset();
        p.setAntiAlias(true);
        int solid = GlassPalette.withAlpha(color, alpha);
        int mid = GlassPalette.withAlpha(color, alpha * 0.45f);
        int far = GlassPalette.withAlpha(color, alpha * 0.12f);
        int edge = GlassPalette.withAlpha(color, 0f);
        p.setShader(new RadialGradient(cx, cy, Math.max(1f, r),
                new int[]{solid, mid, far, edge}, new float[]{0f, 0.42f, 0.72f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, p);
    }

    private void drawFlare(Canvas canvas, long now) {
        if (activeKey == null || !pal.glow) return;
        float t = (now - pressTime) / 720f;
        if (t < 0f || t > 1f) return;
        float r = dp(70) * (0.5f + 1.5f * t);
        int c1 = GlassPalette.hsl(hue, 1f, 0.70f, Math.round(190 * (1f - t)));
        int c2 = GlassPalette.hsl(hue, 1f, 0.62f, Math.round(70 * (1f - t)));
        int c3 = GlassPalette.hsl(hue, 1f, 0.60f, 0);
        Paint p = flarePaint;
        p.reset();
        p.setAntiAlias(true);
        p.setShader(new RadialGradient(pressX, pressY, Math.max(1f, r),
                new int[]{c1, c2, c3}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(pressX, pressY, r, p);
    }

    private boolean isShiftKey(Key k) {
        return k.type == Key.FN && "shift".equals(k.code);
    }

    private void drawStateOverlays(Canvas canvas, float intensity) {
        float rad = dp(9);
        for (int r = 0; r < rows.size() && r < rects.size(); r++) {
            List<Key> row = rows.get(r);
            List<RectF> rr = rects.get(r);
            for (int c = 0; c < row.size() && c < rr.size(); c++) {
                Key k = row.get(c);
                if (k.type == Key.SPACER) continue;
                RectF box = rr.get(c);
                boolean pressed = intensity > 0f && activeKey != null
                        && r == activeRow && c == activeCol;

                if (isShiftKey(k) && (shift || caps) && !pressed) {
                    Paint lit = litPaint;
                    lit.reset();
                    lit.setAntiAlias(true);
                    lit.setShader(new LinearGradient(0, box.top, 0, box.bottom,
                            0xF2FFFFFF, 0xB8FFFFFF, Shader.TileMode.CLAMP));
                    canvas.drawRoundRect(box, rad, rad, lit);
                    Paint rim = litRimPaint;
                    rim.reset();
                    rim.setAntiAlias(true);
                    rim.setStyle(Paint.Style.STROKE);
                    rim.setStrokeWidth(dp(1.1f));
                    rim.setColor(0xFFFFFFFF);
                    canvas.drawRoundRect(box, rad, rad, rim);
                }
                if (pressed) {
                    drawPressedOverlay(canvas, box, intensity, rad);
                }
            }
        }
    }

    private void drawPressedOverlay(Canvas canvas, RectF r, float intensity, float rad) {
        // The mock scales a pressed key to 0.86; here the key body lives in the
        // baked bitmap, so the pressed treatment is drawn inset by the same
        // amount and reads as the key sinking under the finger.
        float sink = 0.14f * (pal != null ? pal.bounce : 0.7f) * intensity;
        RectF k = tmpRect;
        k.set(r);
        k.inset(k.width() * sink / 2f, k.height() * sink / 2f);
        float kr = rad * (1f - sink);

        canvas.save();
        Path cp = tmpPath;
        cp.reset();
        cp.addRoundRect(k, kr, kr, Path.Direction.CW);
        canvas.clipPath(cp);

        Paint tint = pressTintPaint;
        tint.reset();
        tint.setAntiAlias(true);
        tint.setShader(new LinearGradient(0, k.top, 0, k.bottom,
                GlassPalette.hsl(hue, 1f, 0.92f, Math.round(120 * intensity)),
                GlassPalette.hsl(hue, 1f, 0.62f, Math.round(55 * intensity)),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(k, kr, kr, tint);

        if (pal.glow) {
            Paint bloom = pressBloomPaint;
            bloom.reset();
            bloom.setAntiAlias(true);
            bloom.setShader(new RadialGradient(pressX, pressY,
                    Math.max(dp(12), k.width() * 1.05f),
                    GlassPalette.hsl(hue, 1f, 0.97f, Math.round(150 * intensity)),
                    GlassPalette.hsl(hue, 1f, 0.82f, 0), Shader.TileMode.CLAMP));
            canvas.drawRect(k, bloom);
        }
        canvas.restore();

        if (pal.glow) {
            Paint g = pressGlowPaint;
            g.reset();
            g.setAntiAlias(true);
            g.setStyle(Paint.Style.STROKE);
            g.setStrokeWidth(dp(1.2f));
            for (int i = 0; i < 3; i++) {
                float spread = dp(1.5f + i * 5f) * intensity;
                g.setColor(GlassPalette.hsl(hue, 1f, 0.74f,
                        Math.round(140 * intensity / (i + 1.4f))));
                canvas.drawRoundRect(new RectF(k.left - spread, k.top - spread,
                        k.right + spread, k.bottom + spread), kr + spread, kr + spread, g);
            }
        }

        Paint rim = pressRimPaint;
        rim.reset();
        rim.setAntiAlias(true);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(dp(1.2f));
        rim.setColor(GlassPalette.hsl(hue, 1f, 0.86f, Math.round(245 * intensity)));
        canvas.drawRoundRect(k, kr, kr, rim);

        Paint spec = pressSpecPaint;
        spec.reset();
        spec.setAntiAlias(true);
        spec.setColor(GlassPalette.hsl(hue, 1f, 1f, Math.round(200 * intensity)));
        spec.setStrokeWidth(dp(1.3f));
        spec.setStrokeCap(Paint.Cap.ROUND);
        float inset = kr * 1.15f;
        canvas.drawLine(k.left + inset, k.top + dp(2f), k.right - inset, k.top + dp(2f), spec);
    }

    private String labelFor(Key k) {
        if (k.type == Key.SPACER) return "";
        if (k.type == Key.FN && "enter".equals(k.code)) return enterLabel;
        if (page == KeyboardLayout.LETTERS && k.type == Key.NORMAL && (shift || caps)) {
            return k.label.toUpperCase();
        }
        return k.label;
    }

    private void drawLabels(Canvas canvas, float intensity) {
        for (int r = 0; r < rows.size() && r < rects.size(); r++) {
            List<Key> row = rows.get(r);
            List<RectF> rr = rects.get(r);
            for (int c = 0; c < row.size() && c < rr.size(); c++) {
                Key k = row.get(c);
                if (k.type == Key.SPACER) continue;
                RectF box = rr.get(c);
                boolean pressed = intensity > 0f && activeKey != null
                        && r == activeRow && c == activeCol;
                boolean lit = isShiftKey(k) && (shift || caps);
                int color = lit ? 0xFF0B1230 : pal.ink;

                if (k.type == Key.FN) {
                    if ("shift".equals(k.code)) {
                        drawShiftIcon(canvas, box, color, caps);
                        continue;
                    }
                    if ("back".equals(k.code)) {
                        drawBackIcon(canvas, box, color);
                        continue;
                    }
                    if ("page:emo".equals(k.code)) {
                        drawEmojiIcon(canvas, box, color);
                        continue;
                    }
                }

                String label = labelFor(k);
                if (label.length() == 0) continue;

                Paint tx = labelPaint;
                tx.reset();
                tx.setAntiAlias(true);
                tx.setColor(color);
                tx.setTextAlign(Paint.Align.CENTER);
                tx.setTypeface(LABEL_FACE);
                float size;
                if (k.type == Key.FN) {
                    size = dp(15);
                } else if (page == KeyboardLayout.EMOJI) {
                    size = kh * 0.44f;
                } else {
                    size = dp(21);
                }
                tx.setTextSize(size);
                if (pal.dark) {
                    tx.setShadowLayer(dp(2.2f), 0f, dp(0.8f), 0x40000000);
                }
                float maxW = Math.max(dp(1), box.width() - dp(10));
                float tw = tx.measureText(label);
                if (tw > maxW && tw > 0) {
                    tx.setTextSize(Math.max(dp(1), size * (maxW / tw)));
                }
                Paint.FontMetrics fm = fmScratch;
                tx.getFontMetrics(fm);
                float baseline = box.centerY() - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(label, box.centerX(), baseline, tx);
            }
        }
    }

    private void drawShiftIcon(Canvas canvas, RectF r, int color, boolean locked) {
        float cx = r.centerX(), cy = r.centerY() - (locked ? dp(2) : 0);
        float s = Math.min(r.width(), r.height()) * 0.27f;
        Path p = new Path();
        p.moveTo(cx, cy - s);
        p.lineTo(cx + s * 0.88f, cy - s * 0.02f);
        p.lineTo(cx + s * 0.34f, cy - s * 0.02f);
        p.lineTo(cx + s * 0.34f, cy + s * 0.74f);
        p.lineTo(cx - s * 0.34f, cy + s * 0.74f);
        p.lineTo(cx - s * 0.34f, cy - s * 0.02f);
        p.lineTo(cx - s * 0.88f, cy - s * 0.02f);
        p.close();
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setColor(color);
        canvas.drawPath(p, f);
        if (locked) {
            Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
            bar.setColor(color);
            RectF b = new RectF(cx - s * 0.42f, cy + s * 1.02f, cx + s * 0.42f, cy + s * 1.24f);
            canvas.drawRoundRect(b, dp(1), dp(1), bar);
        }
    }

    private void drawBackIcon(Canvas canvas, RectF r, int color) {
        float cx = r.centerX(), cy = r.centerY();
        float s = Math.min(r.width(), r.height()) * 0.24f;
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(dp(1.7f));
        st.setStrokeCap(Paint.Cap.ROUND);
        st.setStrokeJoin(Paint.Join.ROUND);
        st.setColor(color);

        Path p = new Path();
        p.moveTo(cx - s * 1.25f, cy);
        p.lineTo(cx - s * 0.35f, cy - s * 0.88f);
        p.lineTo(cx + s * 1.02f, cy - s * 0.88f);
        p.quadTo(cx + s * 1.34f, cy - s * 0.88f, cx + s * 1.34f, cy - s * 0.55f);
        p.lineTo(cx + s * 1.34f, cy + s * 0.55f);
        p.quadTo(cx + s * 1.34f, cy + s * 0.88f, cx + s * 1.02f, cy + s * 0.88f);
        p.lineTo(cx - s * 0.35f, cy + s * 0.88f);
        p.close();
        canvas.drawPath(p, st);

        canvas.drawLine(cx + s * 0.02f, cy - s * 0.34f, cx + s * 0.62f, cy + s * 0.30f, st);
        canvas.drawLine(cx + s * 0.02f, cy + s * 0.30f, cx + s * 0.62f, cy - s * 0.34f, st);
    }

    private void drawEmojiIcon(Canvas canvas, RectF r, int color) {
        float cx = r.centerX(), cy = r.centerY();
        float s = Math.min(r.width(), r.height()) * 0.26f;
        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(dp(1.7f));
        st.setStrokeCap(Paint.Cap.ROUND);
        st.setColor(color);
        canvas.drawCircle(cx, cy, s, st);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(color);
        canvas.drawCircle(cx - s * 0.40f, cy - s * 0.30f, dp(1.5f), dot);
        canvas.drawCircle(cx + s * 0.40f, cy - s * 0.30f, dp(1.5f), dot);

        RectF arc = new RectF(cx - s * 0.58f, cy - s * 0.22f, cx + s * 0.58f, cy + s * 0.60f);
        canvas.drawArc(arc, 25f, 130f, false, st);
    }

    private void drawPreview(Canvas canvas) {
        if (!down || altActive || activeKey == null) return;
        if (activeKey.type != Key.NORMAL) return;
        RectF kr = keyRect(activeRow, activeCol);
        if (kr == null) return;
        float bw = kr.width() * 1.2f;
        float bh = currentKeyHeight() * 1.15f;
        float cx = Math.max(bw / 2f + dp(2), Math.min(getWidth() - bw / 2f - dp(2), kr.centerX()));
        float cy = kr.top - bh * 0.62f;
        if (cy - bh / 2f < dp(2)) cy = kr.top + bh * 0.62f;
        RectF b = new RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f);
        drawBubble(canvas, b, labelFor(activeKey), dp(24), false);
    }

    private void drawAltStrip(Canvas canvas) {
        if (!altActive || altRects.isEmpty()) return;
        for (int i = 0; i < altRects.size() && i < altList.size(); i++) {
            drawBubble(canvas, altRects.get(i), altList.get(i), dp(21), i == altIndex);
        }
    }

    private void drawBubble(Canvas canvas, RectF b, String label, float textSize, boolean selected) {
        float rad = dp(16);

        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColor(0x44000000);
        sh.setMaskFilter(new BlurMaskFilter(dp(5f), BlurMaskFilter.Blur.NORMAL));
        canvas.drawRoundRect(new RectF(b.left, b.top + dp(5), b.right, b.bottom + dp(7)),
                rad, rad, sh);
        sh.setMaskFilter(null);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                selected ? GlassPalette.hsl(hue, 1f, 0.88f, 245) : 0xF0FFFFFF,
                selected ? GlassPalette.hsl(hue, 1f, 0.64f, 225) : 0xD9F2F5FF,
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(b, rad, rad, fill);

        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(dp(1.2f));
        rim.setColor(selected ? GlassPalette.hsl(hue, 1f, 0.72f, 255) : 0xE6FFFFFF);
        canvas.drawRoundRect(b, rad, rad, rim);

        Paint spec = new Paint(Paint.ANTI_ALIAS_FLAG);
        spec.setColor(0xFFFFFFFF);
        spec.setStrokeWidth(dp(1.2f));
        spec.setStrokeCap(Paint.Cap.ROUND);
        spec.setMaskFilter(new BlurMaskFilter(dp(0.7f), BlurMaskFilter.Blur.NORMAL));
        canvas.drawLine(b.left + rad * 0.9f, b.top + dp(2f), b.right - rad * 0.9f,
                b.top + dp(2f), spec);
        spec.setMaskFilter(null);

        Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
        tx.setColor(0xFF0B1230);
        tx.setTextAlign(Paint.Align.CENTER);
        tx.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tx.setTextSize(textSize);
        Paint.FontMetrics fm = tx.getFontMetrics();
        canvas.drawText(label, b.centerX(), b.centerY() - (fm.ascent + fm.descent) / 2f, tx);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return onDown(e);
            case MotionEvent.ACTION_MOVE:
                onMove(e);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onUp(e);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                // A second finger must not re-arm or clear the press the first one
                // is holding; consuming it keeps the repeat chain single.
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private boolean onDown(MotionEvent e) {
        layoutKeys();
        // Drop any timer still armed from an earlier press (its ACTION_UP can be
        // swallowed by a pointer event) before this press arms its own.
        cancelPending();
        int r = rowAt(e.getY());
        if (r < 0) return true;
        int c = colAt(r, e.getX());
        if (c < 0) return true;
        Key k = rows.get(r).get(c);
        if (k.type == Key.SPACER) return true;

        activeKey = k;
        activeRow = r;
        activeCol = c;
        pressTime = SystemClock.uptimeMillis();
        down = true;
        pressX = e.getX();
        pressY = e.getY();
        altActive = false;
        altIndex = -1;
        altList.clear();
        altRects.clear();

        hue = (hue + 41f) % 360f;
        feedback();
        fire(k);

        if (k.alts != null && k.alts.length > 0) scheduleLongPress();
        if (k.type == Key.FN && "back".equals(k.code)) scheduleRepeat();

        invalidate();
        return true;
    }

    private void onMove(MotionEvent e) {
        if (altActive) {
            int idx = -1;
            for (int i = 0; i < altRects.size(); i++) {
                RectF b = altRects.get(i);
                if (e.getX() >= b.left && e.getX() <= b.right
                        && e.getY() >= b.top - dp(6) && e.getY() <= b.bottom + dp(10)) {
                    idx = i;
                }
            }
            if (idx != altIndex) {
                altIndex = idx;
                invalidate();
            }
            return;
        }
        if (!down || activeKey == null) return;
        RectF kr = keyRect(activeRow, activeCol);
        if (kr == null) return;
        if (e.getY() < kr.top - kr.height() * 1.1f || e.getY() > kr.bottom + kr.height() * 1.1f) {
            cancelPress();
        }
    }

    private void cancelPress() {
        cancelPending();
        down = false;
        altActive = false;
        altIndex = -1;
        altList.clear();
        altRects.clear();
        pressTime = SystemClock.uptimeMillis() - FLASH_MS;
        activeKey = null;
        invalidate();
    }

    private void onUp(MotionEvent e) {
        cancelPending();

        if (altActive && altIndex >= 0 && altIndex < altList.size()) {
            String alt = altList.get(altIndex);
            if (listener != null) {
                listener.onBackspace();
                listener.onText(alt);
            }
        }
        altActive = false;
        altIndex = -1;
        altList.clear();
        altRects.clear();
        down = false;
        pressTime = SystemClock.uptimeMillis();
        invalidate();
    }

    private void scheduleLongPress() {
        handler.removeCallbacks(longPressRun);
        handler.postDelayed(longPressRun, LONG_PRESS_MS);
    }

    private void buildAltRects() {
        altRects.clear();
        RectF kr = keyRect(activeRow, activeCol);
        int n = altList.size();
        if (kr == null || n <= 0) return;
        float g = dp(3);
        float bw = dp(44);
        float bh = currentKeyHeight() * 0.95f;
        float total = n * bw + (n - 1) * g;
        // If the strip is wider than the screen the old code pushed it off the left
        // edge; shrink the bubbles instead so every alternate stays reachable.
        float limit = Math.max(0f, getWidth() - dp(8));
        if (total > limit && n > 0) {
            bw = Math.max(dp(18), (limit - (n - 1) * g) / n);
            total = n * bw + (n - 1) * g;
        }
        float x = kr.centerX() - total / 2f;
        float minX = dp(4);
        float maxX = Math.max(minX, getWidth() - dp(4) - total);
        if (x < minX) x = minX;
        if (x > maxX) x = maxX;
        float y = kr.top - bh - dp(8);
        if (y < dp(2)) y = dp(2);
        for (int i = 0; i < n; i++) {
            altRects.add(new RectF(x, y, x + bw, y + bh));
            x += bw + g;
        }
    }

    private void scheduleRepeat() {
        // Cancel first: arming twice used to leave two chains deleting in parallel.
        handler.removeCallbacks(repeatRun);
        handler.postDelayed(repeatRun, REPEAT_DELAY_MS);
    }

    private void stopRepeat() {
        handler.removeCallbacks(repeatRun);
    }

    private void fire(Key k) {
        if (k.type == Key.FN) {
            String c = k.code;
            if ("shift".equals(c)) {
                long now = SystemClock.uptimeMillis();
                if (now - lastShiftTap < 330L) {
                    caps = !caps;
                    shift = caps;
                } else {
                    shift = !shift;
                    caps = false;
                }
                lastShiftTap = now;
            } else if ("back".equals(c)) {
                if (listener != null) listener.onBackspace();
            } else if ("space".equals(c)) {
                if (listener != null) listener.onText(" ");
            } else if ("enter".equals(c)) {
                if (listener != null) listener.onEnter();
            } else if (c.startsWith("page:")) {
                setPage(c.substring(5));
            }
            return;
        }
        String s = k.code;
        if (page == KeyboardLayout.LETTERS && (shift || caps) && s.length() == 1) {
            s = s.toUpperCase();
        }
        if (listener != null) listener.onText(s);
        if (shift && !caps) shift = false;
    }

    private void setPage(String name) {
        int p;
        if ("123".equals(name)) p = KeyboardLayout.NUMBERS;
        else if ("sym".equals(name)) p = KeyboardLayout.SYMBOLS;
        else if ("emo".equals(name)) p = KeyboardLayout.EMOJI;
        else p = KeyboardLayout.LETTERS;
        if (p == page) return;
        cancelPending();
        page = p;
        rows = KeyboardLayout.rows(page);
        activeKey = null;
        down = false;
        altActive = false;
        altIndex = -1;
        altList.clear();
        altRects.clear();
        if (page != KeyboardLayout.LETTERS) {
            shift = false;
            caps = false;
        }
        baseDirty = true;
        layoutDirty = true;
        requestLayout();
        invalidate();
    }

    /** Drop any queued long-press or key-repeat so it cannot fire on a stale grid. */
    private void cancelPending() {
        handler.removeCallbacks(longPressRun);
        stopRepeat();
    }

    private void feedback() {
        Context c = getContext();
        if (GlassPalette.hapticsOn(c)) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (GlassPalette.soundOn(c)) {
            try {
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) am.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.55f);
            } catch (Throwable ignored) {
                // sound is a nicety, never fatal
            }
        }
    }
}
