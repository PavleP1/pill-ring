package com.example.glowring;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.WindowManager;

public class OverlayService extends Service {
    private WindowManager wm;
    private RingView view;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("ring", "Glow ring", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "ring")
                .setContentTitle("Glow ring is on")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
        startForeground(1, n);

        if (view == null) {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            view = new RingView(this, false);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            wm.addView(view, lp);
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (view != null && wm != null) wm.removeView(view);
        view = null;
        super.onDestroy();
    }
}
