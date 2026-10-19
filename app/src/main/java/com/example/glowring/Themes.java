package com.example.glowring;

import android.content.SharedPreferences;

final class Themes {
    static final String[] NAMES = {"Serbia", "Neon", "Rainbow", "Ocean", "Sunset", "Mint", "Mono"};
    static final int[][] COLORS = {
        {0xFFC6363C, 0xFF0C4076, 0xFFFFFFFF},
        {0xFFFF00E5, 0xFF00F0FF, 0xFF7CFF00},
        {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF},
        {0xFF00C6FF, 0xFF0072FF, 0xFF7EF9FF},
        {0xFFFF512F, 0xFFF09819, 0xFFDD2476},
        {0xFF00F5A0, 0xFF00D9F5},
        {0xFFFFFFFF, 0xFF9E9E9E}
    };

    static int color(SharedPreferences p, int i) {
        int[] d = COLORS[0];
        return p.getInt("c" + i, i < d.length ? d[i] : 0xFFFFFFFF);
    }

    static void apply(SharedPreferences p, int theme) {
        int[] c = COLORS[theme];
        SharedPreferences.Editor e = p.edit();
        e.putInt("n", c.length);
        for (int i = 0; i < c.length; i++) e.putInt("c" + i, c[i]);
        e.apply();
    }
}
