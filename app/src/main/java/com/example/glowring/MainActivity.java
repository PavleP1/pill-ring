package com.example.glowring;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0A0D1A;
    private static final int ACCENT = 0xFF8FA8FF;
    private static final String[] STYLES = {"Sweep", "Solid", "Pulse"};
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private SharedPreferences p;
    private LinearLayout col, cur, swatches;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("ring", MODE_PRIVATE);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        setContentView(buildUi());
    }

    // ---------------------------------------------------------------- UI

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.addView(new GlassBackground(this), new FrameLayout.LayoutParams(MATCH, MATCH));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(24), dp(16), dp(40));
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));

        TextView head = text("Glow Ring", 32, true, Color.WHITE);
        col.addView(head);
        TextView sub = text("Customize your camera ring", 14, false, 0xB3FFFFFF);
        sub.setPadding(0, dp(2), 0, dp(16));
        col.addView(sub);

        // Preview + start/stop
        section(null);
        cur.addView(new RingView(this, true), new LinearLayout.LayoutParams(MATCH, dp(150)));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, 0);
        Button start = btn("Start ring", true);
        start.setOnClickListener(v -> startRing());
        Button stop = btn("Stop ring", false);
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        row.addView(start, weight(0, 4));
        row.addView(stop, weight(4, 0));
        cur.addView(row);

        // Themes
        section("Themes");
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tr = new LinearLayout(this);
        for (int i = 0; i < Themes.NAMES.length; i++) {
            final int t = i;
            int[] cs = new int[Themes.COLORS[i].length];
            for (int k = 0; k < cs.length; k++) cs[k] = (Themes.COLORS[i][k] & 0x00FFFFFF) | 0xDD000000;
            Button bt = btn(Themes.NAMES[i], false);
            bt.setBackground(pill(cs, 0x77FFFFFF));
            bt.setShadowLayer(4, 0, 1, 0xAA000000);
            bt.setOnClickListener(v -> { Themes.apply(p, t); setContentView(buildUi()); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(8);
            tr.addView(bt, lp);
        }
        hs.addView(tr);
        cur.addView(hs);

        // Colors
        section("Colors");
        addSeek("Number of colors", "n", 2, 5, 3, this::refreshSwatches);
        swatches = new LinearLayout(this);
        swatches.setPadding(0, dp(12), 0, dp(4));
        cur.addView(swatches);
        refreshSwatches();
        cur.addView(text("Tap a swatch to edit its color", 12, false, 0x80FFFFFF));

        // Style
        section("Style");
        final Button[] chips = new Button[3];
        LinearLayout sr = new LinearLayout(this);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            chips[i] = btn(STYLES[i], false);
            chips[i].setOnClickListener(v -> {
                p.edit().putInt("style", idx).apply();
                updateChips(chips);
            });
            sr.addView(chips[i], weight(i == 0 ? 0 : 4, i == 2 ? 0 : 4));
        }
        cur.addView(sr);
        updateChips(chips);

        final Button glow = btn("Glow effect", false);
        glow.setBackground(chipBg(p.getBoolean("glow", false)));
        glow.setOnClickListener(v -> {
            boolean g = !p.getBoolean("glow", false);
            p.edit().putBoolean("glow", g).apply();
            glow.setBackground(chipBg(g));
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(MATCH, WRAP);
        glp.topMargin = dp(8);
        cur.addView(glow, glp);

        // Shape & animation
        section("Shape & animation");
        addSeek("Thickness (dp)", "thick", 1, 20, 3, null);
        addSeek("Corner roundness (0 = rectangle, 100 = pill)", "round", 0, 100, 100, null);
        addSeek("Rotation (degrees)", "rot", 0, 359, 0, null);
        addSeek("Spin speed (0 = still)", "speed", 0, 100, 20, null);

        // Position
        section("Position");
        addSeek("Width (dp)", "width", 10, 300, 28, null);
        addSeek("Height (dp)", "height", 10, 300, 28, null);
        addSeek("Horizontal (300 = center)", "hpos", 0, 600, 300, null);
        addSeek("Vertical (dp from top)", "vpos", 0, 200, 24, null);

        TextView fine = text("Fine tune (1 dp per tap)", 14, false, 0xCCFFFFFF);
        fine.setPadding(0, dp(14), 0, dp(4));
        cur.addView(fine);
        LinearLayout nr = new LinearLayout(this);
        nudge(nr, "←", "fx", -1);
        nudge(nr, "→", "fx", 1);
        nudge(nr, "↑", "fy", -1);
        nudge(nr, "↓", "fy", 1);
        cur.addView(nr);

        Button reset = btn("Reset to detected camera hole", false);
        reset.setOnClickListener(v -> {
            p.edit().putBoolean("auto", true).putInt("fx", 0).putInt("fy", 0).apply();
            Toast.makeText(this, "Resetting...", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rlp.topMargin = dp(12);
        cur.addView(reset, rlp);
        return root;
    }

    private LinearLayout.LayoutParams weight(int left, int right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1);
        lp.leftMargin = dp(left);
        lp.rightMargin = dp(right);
        return lp;
    }

    private void section(String title) {
        GlassCard c = new GlassCard(this, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = dp(14);
        col.addView(c, lp);
        cur = c;
        if (title != null) {
            TextView t = text(title.toUpperCase(), 12, true, 0x99FFFFFF);
            t.setLetterSpacing(0.12f);
            t.setPadding(0, 0, 0, dp(6));
            c.addView(t);
        }
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Drawable pill(int[] colors, int stroke) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setCornerRadius(dp(100));
        g.setStroke(dp(1), stroke);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(100));
        return new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), g, mask);
    }

    private Drawable chipBg(boolean selected) {
        return selected
                ? pill(new int[]{0xCC6D8BFF, 0xCC9B6BFF}, 0x99FFFFFF)
                : pill(new int[]{0x26FFFFFF, 0x14FFFFFF}, 0x40FFFFFF);
    }

    private void updateChips(Button[] chips) {
        int s = p.getInt("style", 0);
        for (int i = 0; i < chips.length; i++) chips[i].setBackground(chipBg(i == s));
    }

    private Button btn(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        b.setBackground(primary
                ? pill(new int[]{0xFF6D8BFF, 0xFF9B6BFF}, 0x88FFFFFF)
                : pill(new int[]{0x33FFFFFF, 0x1AFFFFFF}, 0x55FFFFFF));
        return b;
    }

    private void nudge(LinearLayout row, String label, final String key, final int delta) {
        Button b = btn(label, false);
        b.setOnClickListener(v -> p.edit().putInt(key, p.getInt(key, 0) + delta).apply());
        row.addView(b, weight(3, 3));
    }

    private SeekBar styledSeek(int max, int progress, int tint) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(progress);
        sb.setProgressTintList(ColorStateList.valueOf(tint));
        sb.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        return sb;
    }

    private void addSeek(final String label, final String key, final int min, int max, int def,
                         final Runnable after) {
        final TextView tv = text(label + ": " + p.getInt(key, def), 14, false, 0xCCFFFFFF);
        tv.setPadding(0, dp(12), 0, 0);
        SeekBar sb = styledSeek(max - min, p.getInt(key, def) - min, ACCENT);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int pr, boolean fromUser) {
                int v = pr + min;
                tv.setText(label + ": " + v);
                if (!fromUser) return;
                SharedPreferences.Editor e = p.edit().putInt(key, v);
                if (key.equals("width") || key.equals("height") || key.equals("hpos") || key.equals("vpos"))
                    e.putBoolean("auto", false);
                e.apply();
                if (after != null) after.run();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        cur.addView(tv);
        cur.addView(sb);
    }

    private void refreshSwatches() {
        swatches.removeAllViews();
        int n = Math.max(2, Math.min(5, p.getInt("n", 3)));
        for (int i = 0; i < n; i++) {
            final int idx = i;
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Themes.color(p, i));
            g.setStroke(dp(2), 0xCCFFFFFF);
            View v = new View(this);
            v.setBackground(g);
            v.setOnClickListener(x -> pickColor(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
            lp.rightMargin = dp(10);
            swatches.addView(v, lp);
        }
    }

    private void pickColor(final int idx) {
        int c = Themes.color(p, idx);
        final int[] rgb = {Color.red(c), Color.green(c), Color.blue(c)};
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        GlassCard box = new GlassCard(this, 0xEE12162B);

        TextView t = text("COLOR " + (idx + 1), 12, true, 0x99FFFFFF);
        t.setLetterSpacing(0.12f);
        box.addView(t);

        final GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(16));
        pg.setColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
        pg.setStroke(dp(1), 0x66FFFFFF);
        View prev = new View(this);
        prev.setBackground(pg);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(MATCH, dp(56));
        plp.topMargin = dp(10);
        box.addView(prev, plp);

        String[] names = {"Red", "Green", "Blue"};
        int[] tints = {0xFFFF6B6B, 0xFF5BE38A, 0xFF6BA8FF};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView l = text(names[i], 14, false, 0xCCFFFFFF);
            l.setPadding(0, dp(10), 0, 0);
            SeekBar sb = styledSeek(255, rgb[i], tints[i]);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int pr, boolean u) {
                    rgb[k] = pr;
                    pg.setColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            box.addView(l);
            box.addView(sb);
        }

        LinearLayout br = new LinearLayout(this);
        br.setPadding(0, dp(16), 0, 0);
        Button cancel = btn("Cancel", false);
        cancel.setOnClickListener(v -> dlg.dismiss());
        Button ok = btn("OK", true);
        ok.setOnClickListener(v -> {
            p.edit().putInt("c" + idx, Color.rgb(rgb[0], rgb[1], rgb[2])).apply();
            refreshSwatches();
            dlg.dismiss();
        });
        br.addView(cancel, weight(0, 4));
        br.addView(ok, weight(4, 0));
        box.addView(br);

        dlg.setContentView(box);
        dlg.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dlg.show();
        dlg.getWindow().setLayout(dp(320), WRAP);
    }

    private void startRing() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps', then tap Start again", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        startForegroundService(new Intent(this, OverlayService.class));
    }

    // ------------------------------------------------- glass look (views)

    /** Dark base with slowly drifting, soft colored blobs. */
    static class GlassBackground extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] cols = {0xFF6A5BFF, 0xFF00C2FF, 0xFFFF4FD8, 0xFF2F6BFF};
        private final float[] bx = {0.15f, 0.85f, 0.30f, 0.90f};
        private final float[] by = {0.10f, 0.30f, 0.75f, 0.95f};
        private final float[] sx = {0.23f, 0.17f, 0.19f, 0.29f};
        private final float[] sy = {0.21f, 0.27f, 0.15f, 0.20f};
        private final float[] ph = {0f, 1.7f, 3.1f, 4.6f};
        private final Shader[] sh = new Shader[4];
        private float radius;

        GlassBackground(Context c) { super(c); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            radius = Math.max(w, h) * 0.42f;
            for (int i = 0; i < 4; i++) {
                int base = cols[i] & 0x00FFFFFF;
                sh[i] = new RadialGradient(0, 0, radius,
                        new int[]{base | 0x99000000, base}, null, Shader.TileMode.CLAMP);
            }
        }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(BG);
            int w = getWidth(), h = getHeight();
            double t = System.nanoTime() / 1e9;
            for (int i = 0; i < 4; i++) {
                float x = (float) (w * (bx[i] + 0.12 * Math.sin(t * sx[i] + ph[i])));
                float y = (float) (h * (by[i] + 0.08 * Math.cos(t * sy[i] + ph[i])));
                paint.setShader(sh[i]);
                c.save();
                c.translate(x, y);
                c.drawCircle(0, 0, radius, paint);
                c.restore();
            }
            postInvalidateOnAnimation();
        }
    }

    /** Frosted translucent card with glossy edge and specular highlight. */
    static class GlassCard extends LinearLayout {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hi = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float d, rad;
        private final int baseColor;

        GlassCard(Context c, int baseColor) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            rad = 26 * d;
            this.baseColor = baseColor;
            setOrientation(VERTICAL);
            setWillNotDraw(false);
            setPadding((int) (18 * d), (int) (16 * d), (int) (18 * d), (int) (16 * d));
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(1.2f * d);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(1.2f * d);
            base.setColor(baseColor);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float in = edge.getStrokeWidth() / 2f;
            r.set(in, in, w - in, h - in);
            fill.setShader(new LinearGradient(0, 0, 0, h, 0x30FFFFFF, 0x10FFFFFF, Shader.TileMode.CLAMP));
            edge.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{0xA0FFFFFF, 0x18FFFFFF, 0x50FFFFFF}, null, Shader.TileMode.CLAMP));
            hi.setShader(new RadialGradient(w * 0.15f, 0, w * 0.75f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            line.setShader(new LinearGradient(rad, 0, w - rad, 0,
                    new int[]{0x00FFFFFF, 0x90FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas c) {
            if (baseColor != 0) c.drawRoundRect(r, rad, rad, base);
            c.drawRoundRect(r, rad, rad, fill);
            c.drawRoundRect(r, rad, rad, hi);
            c.drawRoundRect(r, rad, rad, edge);
            c.drawLine(rad, 1.5f * d, getWidth() - rad, 1.5f * d, line);
        }
    }
}
