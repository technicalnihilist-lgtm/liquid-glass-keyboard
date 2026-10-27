package com.liquidglass.keyboard;

import android.content.Context;
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
 * The keyboard itself: a canvas-drawn glass board with per-key glow,
 * popup previews, long-press accents and a repeating backspace.
 */
public class KeyboardView extends View {

    public interface Listener {
        void onText(String s);
        void onBackspace();
        void onEnter();
    }

    private static final long FLASH_MS = 650L;
    private static final long LONG_PRESS_MS = 330L;
    private static final long REPEAT_DELAY_MS = 400L;
    private static final long REPEAT_EVERY_MS = 55L;

    private Listener listener;
    private GlassPalette pal = null;

    private final float density;
    private int page = KeyboardLayout.LETTERS;
    private boolean shift, caps;
    private long lastShiftTap;
    private float hue = 200f;

    private int kh;           // key height px
    private int gap, sidePad, band, platePadTop, platePadBot, kbCorner;

    private List<List<Key>> rows = new ArrayList<List<Key>>();
    private final List<List<RectF>> rects = new ArrayList<List<RectF>>();

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
    private Runnable longPressRun;
    private Runnable repeatRun;

    public KeyboardView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(true);
        initMetrics();
        rows = KeyboardLayout.rows(page);
    }

    private void initMetrics() {
        gap = dp(6);
        sidePad = dp(6);
        band = dp(46);
        platePadTop = dp(10);
        platePadBot = dp(9);
        kbCorner = dp(26);
        kh = dp(GlassPalette.keyHeightDp(getContext()));
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setPalette(GlassPalette p) {
        pal = p;
        initMetrics();
        invalidate();
    }

    public void setEnterLabel(String s) {
        enterLabel = s;
        invalidate();
    }

    /** Called by the service when a new field is focused. */
    public void reset(boolean autoShift) {
        page = KeyboardLayout.LETTERS;
        rows = KeyboardLayout.rows(page);
        shift = autoShift;
        caps = false;
        activeKey = null;
        down = false;
        altActive = false;
        altList.clear();
        altRects.clear();
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
        setMeasuredDimension(w, h);
    }

    private void layoutKeys() {
        rects.clear();
        float avail = getWidth() - 2f * sidePad;
        float y = band + platePadTop;
        int hk = currentKeyHeight();
        for (int r = 0; r < rows.size(); r++) {
            List<Key> row = rows.get(r);
            float sum = 0f;
            for (Key k : row) sum += k.weight;
            float gaps = gap * Math.max(0, row.size() - 1);
            float unit = (avail - gaps) / Math.max(0.0001f, sum);
            float x = sidePad;
            List<RectF> rr = new ArrayList<RectF>();
            for (Key k : row) {
                float kw = unit * k.weight;
                rr.add(new RectF(x, y, x + kw, y + hk));
                x += kw + gap;
            }
            rects.add(rr);
            y += hk + gap;
        }
    }

    private int rowAt(float y) {
        for (int r = 0; r < rects.size(); r++) {
            List<RectF> row = rects.get(r);
            if (!row.isEmpty() && y >= row.get(0).top - gap / 2f && y <= row.get(0).bottom + gap / 2f) {
                return r;
            }
        }
        return -1;
    }

    private int colAt(int r, float x) {
        List<RectF> row = rects.get(r);
        for (int c = 0; c < row.size(); c++) {
            RectF b = row.get(c);
            if (x >= b.left - gap / 2f && x <= b.right + gap / 2f) return c;
        }
        return -1;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        if (pal == null) pal = GlassPalette.resolve(getContext());
        layoutKeys();

        float w = getWidth();
        float h = getHeight();

        canvas.save();
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, w, h + kbCorner), kbCorner, kbCorner, Path.Direction.CW);
        canvas.clipPath(clip);
        GlassBackdrop.draw(canvas, w, h, pal);
        drawFlare(canvas);
        canvas.restore();

        drawPlate(canvas, w, h);

        long now = SystemClock.uptimeMillis();
        float t = (now - pressTime) / (float) FLASH_MS;
        float intensity = down ? 1f : Math.max(0f, 1f - t);
        if (intensity <= 0f) activeKey = null;

        for (int r = 0; r < rows.size() && r < rects.size(); r++) {
            List<Key> row = rows.get(r);
            List<RectF> rr = rects.get(r);
            for (int c = 0; c < row.size() && c < rr.size(); c++) {
                boolean act = intensity > 0f && activeKey != null
                        && r == activeRow && c == activeCol;
                drawKey(canvas, row.get(c), rr.get(c), act ? intensity : 0f);
            }
        }

        drawPreview(canvas);
        drawAltStrip(canvas);

        if (intensity > 0f && !down && t < 1f) postInvalidateOnAnimation();
    }

    private void drawPlate(Canvas canvas, float w, float h) {
        Path p = new Path();
        p.addRoundRect(new RectF(0, band, w, h + kbCorner), kbCorner, kbCorner, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(p);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, band, 0, h, pal.kbTop, pal.kbBot, Shader.TileMode.CLAMP));
        canvas.drawRect(0, band, w, h, fill);
        canvas.restore();

        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(dp(1));
        st.setColor(pal.kbEdge);
        canvas.drawPath(p, st);
    }

    private void drawFlare(Canvas canvas) {
        if (activeKey == null) return;
        long now = SystemClock.uptimeMillis();
        float t = (now - pressTime) / 700f;
        if (t < 0f || t > 1f) return;
        float r = dp(65) * (0.5f + 1.4f * t);
        int c1 = GlassPalette.hsl(hue, 1f, 0.68f, Math.round(210 * (1f - t)));
        int c2 = GlassPalette.hsl(hue, 1f, 0.60f, Math.round(80 * (1f - t)));
        int c3 = GlassPalette.hsl(hue, 1f, 0.60f, 0);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(pressX, pressY, Math.max(1f, r),
                new int[]{c1, c2, c3}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(pressX, pressY, r, p);
    }

    private boolean isShiftKey(Key k) {
        return k.type == Key.FN && "shift".equals(k.code);
    }

    private String labelFor(Key k) {
        if (k.type == Key.SPACER) return "";
        if (isShiftKey(k)) return caps ? "\u21EA" : "\u21E7";
        if (k.type == Key.FN && "enter".equals(k.code)) return enterLabel;
        if (page == KeyboardLayout.LETTERS && k.type == Key.NORMAL && (shift || caps)) {
            return k.label.toUpperCase();
        }
        return k.label;
    }

    private void drawKey(Canvas canvas, Key k, RectF r, float intensity) {
        if (k.type == Key.SPACER) return;
        float rad = dp(11);
        boolean shiftOn = isShiftKey(k) && (shift || caps);

        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColor(0x1C000000);
        canvas.drawRoundRect(new RectF(r.left, r.top + dp(3), r.right, r.bottom + dp(3)), rad, rad, sh);
        sh.setColor(0x10000000);
        canvas.drawRoundRect(new RectF(r.left, r.top + dp(6), r.right, r.bottom + dp(6)), rad, rad, sh);

        if (intensity > 0f) {
            float cx = r.centerX(), cy = r.centerY();
            float hr = r.width() * 0.95f;
            Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
            halo.setShader(new RadialGradient(cx, cy, Math.max(1f, hr),
                    new int[]{
                            GlassPalette.hsl(hue, 1f, 0.68f, Math.round(95 * intensity)),
                            GlassPalette.hsl(hue, 1f, 0.62f, 0)},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, hr, halo);
        }

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        int top = shiftOn ? 0xE6FFFFFF : pal.keyTop;
        int bot = shiftOn ? 0x8CFFFFFF : pal.keyBot;
        fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, top, bot, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(r, rad, rad, fill);

        if (intensity > 0f) {
            Paint glowFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            glowFill.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                    GlassPalette.hsl(hue, 1f, 0.85f, Math.round(150 * intensity)),
                    GlassPalette.hsl(hue, 1f, 0.65f, Math.round(70 * intensity)),
                    Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, rad, rad, glowFill);

            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setStyle(Paint.Style.STROKE);
            g.setStrokeWidth(dp(1.2f));
            for (int i = 0; i < 3; i++) {
                float spread = dp(1.5f + i * 5f) * intensity;
                g.setColor(GlassPalette.hsl(hue, 1f, 0.72f,
                        Math.round(130 * intensity / (i + 1.4f))));
                canvas.drawRoundRect(new RectF(r.left - spread, r.top - spread,
                        r.right + spread, r.bottom + spread), rad + spread, rad + spread, g);
            }
        }

        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(dp(1));
        st.setColor(intensity > 0f
                ? GlassPalette.hsl(hue, 1f, 0.78f, Math.round(235 * intensity))
                : pal.keyEdge);
        canvas.drawRoundRect(r, rad, rad, st);

        Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
        hl.setColor(shiftOn ? 0xFFFFFFFF : pal.keyHighlight);
        hl.setStrokeWidth(dp(1.4f));
        hl.setStrokeCap(Paint.Cap.ROUND);
        float inset = rad * 0.95f;
        canvas.drawLine(r.left + inset, r.top + dp(2.2f), r.right - inset,
                r.top + dp(2.2f), hl);

        int iconColor = shiftOn ? 0xFF0B1230 : pal.ink;
        if (k.type == Key.FN) {
            if ("shift".equals(k.code)) {
                drawShiftIcon(canvas, r, iconColor, caps);
                return;
            }
            if ("back".equals(k.code)) {
                drawBackIcon(canvas, r, iconColor);
                return;
            }
            if ("page:emo".equals(k.code)) {
                drawEmojiIcon(canvas, r, iconColor);
                return;
            }
        }

        String label = labelFor(k);
        if (label.length() == 0) return;

        Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
        tx.setColor(shiftOn ? 0xFF0B1230 : pal.ink);
        tx.setTextAlign(Paint.Align.CENTER);
        tx.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        float size;
        if (k.type == Key.FN) {
            size = dp(15);
            if ("shift".equals(k.code) || "back".equals(k.code)) size = dp(21);
        } else if (page == KeyboardLayout.EMOJI) {
            size = kh * 0.44f;
        } else {
            size = dp(21);
        }
        tx.setTextSize(size);
        float maxW = r.width() - dp(10);
        float tw = tx.measureText(label);
        if (tw > maxW && tw > 0) {
            tx.setTextSize(size * (maxW / tw));
        }
        Paint.FontMetrics fm = tx.getFontMetrics();
        float baseline = r.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, r.centerX(), baseline, tx);
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
        if (activeRow < 0 || activeRow >= rects.size()) return;
        if (activeCol < 0 || activeCol >= rects.get(activeRow).size()) return;
        RectF kr = rects.get(activeRow).get(activeCol);
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
        float rad = dp(15);
        Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG);
        sh.setColor(0x33000000);
        canvas.drawRoundRect(new RectF(b.left, b.top + dp(4), b.right, b.bottom + dp(4)), rad, rad, sh);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                selected ? GlassPalette.hsl(hue, 1f, 0.86f, 240) : 0xF2FFFFFF,
                selected ? GlassPalette.hsl(hue, 1f, 0.66f, 220) : 0xE6F4F6FF,
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(b, rad, rad, fill);

        Paint st = new Paint(Paint.ANTI_ALIAS_FLAG);
        st.setStyle(Paint.Style.STROKE);
        st.setStrokeWidth(dp(1.2f));
        st.setColor(selected ? GlassPalette.hsl(hue, 1f, 0.72f, 255) : 0xCCFFFFFF);
        canvas.drawRoundRect(b, rad, rad, st);

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
            default:
                return super.onTouchEvent(e);
        }
    }

    private boolean onDown(MotionEvent e) {
        layoutKeys();
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
        if (activeRow < 0 || activeRow >= rects.size()) return;
        RectF kr = rects.get(activeRow).get(activeCol);
        if (e.getY() < kr.top - kr.height() * 1.1f || e.getY() > kr.bottom + kr.height() * 1.1f) {
            cancelPress();
        }
    }

    private void cancelPress() {
        if (longPressRun != null) handler.removeCallbacks(longPressRun);
        stopRepeat();
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
        if (longPressRun != null) handler.removeCallbacks(longPressRun);
        stopRepeat();

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
        longPressRun = new Runnable() {
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
        handler.postDelayed(longPressRun, LONG_PRESS_MS);
    }

    private void buildAltRects() {
        altRects.clear();
        if (activeRow < 0 || activeRow >= rects.size()) return;
        RectF kr = rects.get(activeRow).get(activeCol);
        float bw = dp(44);
        float bh = currentKeyHeight() * 0.95f;
        float g = dp(3);
        int n = altList.size();
        float total = n * bw + (n - 1) * g;
        float x = kr.centerX() - total / 2f;
        if (x < dp(4)) x = dp(4);
        if (x + total > getWidth() - dp(4)) x = getWidth() - dp(4) - total;
        float y = kr.top - bh - dp(8);
        if (y < dp(2)) y = dp(2);
        for (int i = 0; i < n; i++) {
            altRects.add(new RectF(x, y, x + bw, y + bh));
            x += bw + g;
        }
    }

    private void scheduleRepeat() {
        repeatRun = new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onBackspace();
                handler.postDelayed(repeatRun, REPEAT_EVERY_MS);
            }
        };
        handler.postDelayed(repeatRun, REPEAT_DELAY_MS);
    }

    private void stopRepeat() {
        if (repeatRun != null) handler.removeCallbacks(repeatRun);
        repeatRun = null;
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
        page = p;
        rows = KeyboardLayout.rows(page);
        activeKey = null;
        down = false;
        altActive = false;
        altList.clear();
        altRects.clear();
        if (page != KeyboardLayout.LETTERS) {
            shift = false;
            caps = false;
        }
        requestLayout();
        invalidate();
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
