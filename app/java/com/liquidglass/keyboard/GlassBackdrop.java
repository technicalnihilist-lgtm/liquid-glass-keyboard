package com.liquidglass.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Gradient sky plus drifting colour orbs: the shared liquid-glass backdrop. */
public class GlassBackdrop extends View {

    private GlassPalette pal;
    private final long start = System.currentTimeMillis();

    public GlassBackdrop(Context c) {
        super(c);
    }

    public GlassBackdrop(Context c, AttributeSet a) {
        super(c, a);
    }

    public void setPalette(GlassPalette p) {
        pal = p;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (pal == null) pal = GlassPalette.resolve(getContext());
        draw(canvas, getWidth(), getHeight(), pal);
        if (GlassPalette.motionOn(getContext()) && isShown()) {
            postInvalidateDelayed(90);
        }
    }

    public static void draw(Canvas canvas, float w, float h, GlassPalette pal) {
        if (w <= 0 || h <= 0) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0f, 0f, w * 0.72f, h, pal.bg1, pal.bg2,
                Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, w, h, p);

        float m = Math.max(w, h);
        float k = pal.dark ? 1f : 0.6f;
        orb(canvas, w * 0.02f, h * 0.00f, m * 0.64f, pal.orb1, 0.58f * k);
        orb(canvas, w * 1.00f, h * 0.22f, m * 0.56f, pal.orb2, 0.50f * k);
        orb(canvas, w * 0.05f, h * 1.02f, m * 0.60f, pal.orb3, 0.44f * k);
        orb(canvas, w * 0.95f, h * 0.88f, m * 0.44f, pal.orb4, 0.36f * k);
    }

    private static void orb(Canvas canvas, float cx, float cy, float r, int color, float alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int solid = GlassPalette.withAlpha(color, alpha);
        int mid = GlassPalette.withAlpha(color, alpha * 0.45f);
        int far = GlassPalette.withAlpha(color, alpha * 0.12f);
        int edge = GlassPalette.withAlpha(color, 0f);
        p.setShader(new RadialGradient(cx, cy, Math.max(1f, r),
                new int[]{solid, mid, far, edge}, new float[]{0f, 0.42f, 0.72f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, p);
    }
}
