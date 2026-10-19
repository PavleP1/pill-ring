package com.example.glowring;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import java.util.List;

public class RingView extends View implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final SharedPreferences p;
    private final boolean preview;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix m = new Matrix();
    private final float d;
    private SweepGradient shader;
    private float lastCx = -1, lastCy = -1;
    private Rect cutout;

    public RingView(Context c, boolean preview) {
        super(c);
        this.preview = preview;
        p = c.getSharedPreferences("ring", Context.MODE_PRIVATE);
        d = c.getResources().getDisplayMetrics().density;
        paint.setStyle(Paint.Style.STROKE);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        p.registerOnSharedPreferenceChangeListener(this);
        if (!preview) {
            detect();
            postDelayed(() -> { detect(); invalidate(); }, 400);
        }
    }

    @Override protected void onDetachedFromWindow() {
        p.unregisterOnSharedPreferenceChangeListener(this);
        super.onDetachedFromWindow();
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        shader = null;
        invalidate();
    }

    private void detect() {
        if (Build.VERSION.SDK_INT < 28) return;
        WindowInsets in = getRootWindowInsets();
        if (in == null) return;
        DisplayCutout dc = in.getDisplayCutout();
        if (dc == null) return;
        List<Rect> r = dc.getBoundingRects();
        if (!r.isEmpty()) cutout = new Rect(r.get(0));
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        int n = Math.max(2, Math.min(5, p.getInt("n", 3)));
        int style = p.getInt("style", 0);
        int speed = p.getInt("speed", 20);
        float th = p.getInt("thick", 3) * d;
        float cx, cy, rw, rh;

        if (preview) {
            cx = w / 2f; cy = h / 2f;
            rw = Math.min(w * 0.7f, 220 * d); rh = rw * 0.5f;
        } else if (p.getBoolean("auto", true) && cutout != null) {
            cx = cutout.centerX(); cy = cutout.centerY();
            rw = cutout.width() + 8 * d; rh = cutout.height() + 8 * d;
        } else {
            cx = w / 2f + (p.getInt("hpos", 300) - 300) * d;
            cy = p.getInt("vpos", 24) * d;
            rw = p.getInt("width", 28) * d;
            rh = p.getInt("height", 28) * d;
        }
        if (!preview) {
            cx += p.getInt("fx", 0) * d;
            cy += p.getInt("fy", 0) * d;
        }

        rect.set(cx - rw / 2, cy - rh / 2, cx + rw / 2, cy + rh / 2);
        float corner = Math.min(rw, rh) / 2f * p.getInt("round", 100) / 100f;

        if (shader == null || cx != lastCx || cy != lastCy) {
            int[] cols = new int[n + 1];
            for (int i = 0; i < n; i++) cols[i] = Themes.color(p, i);
            cols[n] = cols[0];
            shader = new SweepGradient(cx, cy, cols, null);
            lastCx = cx; lastCy = cy;
        }

        float t = System.nanoTime() / 1e9f;
        if (style == 1) {
            paint.setShader(null);
            paint.setColor(Themes.color(p, 0));
        } else {
            float ang = (p.getInt("rot", 0) + t * speed * 3.6f) % 360f;
            m.setRotate(ang, cx, cy);
            shader.setLocalMatrix(m);
            paint.setShader(shader);
            paint.setColor(Color.WHITE);
        }

        int base = 255;
        if (style == 2) base = (int) (110 + 145 * (0.5f + 0.5f * Math.sin(t * 5f)));

        if (p.getBoolean("glow", false)) {
            paint.setAlpha(base / 4);
            paint.setStrokeWidth(th * 3);
            canvas.drawRoundRect(rect, corner, corner, paint);
        }
        paint.setAlpha(base);
        paint.setStrokeWidth(th);
        canvas.drawRoundRect(rect, corner, corner, paint);

        if (style == 2 || (style == 0 && speed > 0)) postInvalidateOnAnimation();
    }
}
