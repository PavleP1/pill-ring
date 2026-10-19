package com.example.glowring;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SharedPreferences p;
    private LinearLayout col, swatches;
    private static final String[] STYLES = {"Sweep (spinning)", "Solid", "Pulse"};

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("ring", MODE_PRIVATE);
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(32));
        sv.addView(col);

        title("Glow Ring");
        col.addView(new RingView(this, true),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)));

        LinearLayout row = new LinearLayout(this);
        Button start = new Button(this); start.setText("Start ring");
        start.setOnClickListener(v -> startRing());
        Button stop = new Button(this); stop.setText("Stop ring");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        row.addView(start, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(stop, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        col.addView(row);

        title("Themes");
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout tr = new LinearLayout(this);
        for (int i = 0; i < Themes.NAMES.length; i++) {
            final int t = i;
            Button bt = new Button(this);
            bt.setText(Themes.NAMES[i]);
            bt.setOnClickListener(v -> { Themes.apply(p, t); setContentView(buildUi()); });
            tr.addView(bt);
        }
        hs.addView(tr);
        col.addView(hs);

        title("Colors (tap a swatch to edit)");
        addSeek("Number of colors", "n", 2, 5, 3, this::refreshSwatches);
        swatches = new LinearLayout(this);
        col.addView(swatches);
        refreshSwatches();

        title("Style");
        final Button styleBtn = new Button(this);
        styleBtn.setText("Style: " + STYLES[p.getInt("style", 0)]);
        styleBtn.setOnClickListener(v -> {
            int s = (p.getInt("style", 0) + 1) % STYLES.length;
            p.edit().putInt("style", s).apply();
            styleBtn.setText("Style: " + STYLES[s]);
        });
        col.addView(styleBtn);
        final Button glowBtn = new Button(this);
        glowBtn.setText("Glow: " + (p.getBoolean("glow", false) ? "on" : "off"));
        glowBtn.setOnClickListener(v -> {
            boolean g = !p.getBoolean("glow", false);
            p.edit().putBoolean("glow", g).apply();
            glowBtn.setText("Glow: " + (g ? "on" : "off"));
        });
        col.addView(glowBtn);

        title("Shape & animation");
        addSeek("Thickness (dp)", "thick", 1, 20, 3, null);
        addSeek("Corner roundness (0 = rectangle, 100 = pill)", "round", 0, 100, 100, null);
        addSeek("Rotation (degrees)", "rot", 0, 359, 0, null);
        addSeek("Spin speed (0 = still)", "speed", 0, 100, 20, null);

        title("Position");
        addSeek("Width (dp)", "width", 10, 300, 28, null);
        addSeek("Height (dp)", "height", 10, 300, 28, null);
        addSeek("Horizontal (300 = center)", "hpos", 0, 600, 300, null);
        addSeek("Vertical (dp from top)", "vpos", 0, 200, 24, null);

        TextView fine = new TextView(this);
        fine.setText("Fine tune (1 dp per tap)");
        fine.setPadding(0, dp(12), 0, 0);
        col.addView(fine);
        LinearLayout nr = new LinearLayout(this);
        nudge(nr, "←", "fx", -1);
        nudge(nr, "→", "fx", 1);
        nudge(nr, "↑", "fy", -1);
        nudge(nr, "↓", "fy", 1);
        col.addView(nr);

        Button reset = new Button(this);
        reset.setText("Reset to detected camera hole");
        reset.setOnClickListener(v -> {
            p.edit().putBoolean("auto", true).putInt("fx", 0).putInt("fy", 0).apply();
            Toast.makeText(this, "Resetting...", Toast.LENGTH_SHORT).show();
        });
        col.addView(reset);
        return sv;
    }

    private void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setPadding(0, dp(20), 0, dp(4));
        col.addView(t);
    }

    private void nudge(LinearLayout row, String label, final String key, final int delta) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(v -> p.edit().putInt(key, p.getInt(key, 0) + delta).apply());
        row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    private void addSeek(final String label, final String key, final int min, int max, int def,
                         final Runnable after) {
        final TextView tv = new TextView(this);
        int cur = p.getInt(key, def);
        tv.setText(label + ": " + cur);
        tv.setPadding(0, dp(12), 0, 0);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(cur - min);
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
        col.addView(tv);
        col.addView(sb);
    }

    private void refreshSwatches() {
        swatches.removeAllViews();
        int n = Math.max(2, Math.min(5, p.getInt("n", 3)));
        for (int i = 0; i < n; i++) {
            final int idx = i;
            int c = Themes.color(p, i);
            Button b = new Button(this);
            b.setText(String.valueOf(i + 1));
            b.setBackgroundColor(c);
            b.setTextColor(Color.red(c) + Color.green(c) + Color.blue(c) > 380 ? Color.BLACK : Color.WHITE);
            b.setOnClickListener(v -> pickColor(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
            lp.setMargins(dp(3), dp(6), dp(3), dp(6));
            swatches.addView(b, lp);
        }
    }

    private void pickColor(final int idx) {
        int c = Themes.color(p, idx);
        final int[] rgb = {Color.red(c), Color.green(c), Color.blue(c)};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), 0);
        final View prev = new View(this);
        prev.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
        box.addView(prev, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        String[] names = {"Red", "Green", "Blue"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            TextView l = new TextView(this);
            l.setText(names[i]);
            SeekBar sb = new SeekBar(this);
            sb.setMax(255);
            sb.setProgress(rgb[i]);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int pr, boolean u) {
                    rgb[k] = pr;
                    prev.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            box.addView(l);
            box.addView(sb);
        }
        new AlertDialog.Builder(this)
                .setTitle("Color " + (idx + 1))
                .setView(box)
                .setPositiveButton("OK", (d, w) -> {
                    p.edit().putInt("c" + idx, Color.rgb(rgb[0], rgb[1], rgb[2])).apply();
                    refreshSwatches();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
}
