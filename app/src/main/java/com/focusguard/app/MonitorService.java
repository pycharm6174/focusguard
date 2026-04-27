package com.focusguard.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class MonitorService extends Service {

    private static final int NOTIF_ID_SERVICE = 1;
    private static final int NOTIF_ID_ALERT_BASE = 1000;
    private static final long CHECK_INTERVAL_MS = 10_000; // Check every 10 seconds
    private static final int ALERT_INTERVAL_MIN = 10;    // Alert every 10 minutes

    private Handler handler;
    private PrefsManager prefs;
    private NotificationManager notifManager;

    private String lastBlockedPkg = "";
    private long sessionStartTime = 0;
    private String currentTrackedPkg = "";

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        prefs = new PrefsManager(this);
        notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        startForeground(NOTIF_ID_SERVICE, buildServiceNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(monitorRunnable);
        handler.post(monitorRunnable);
        return START_STICKY; // Restart if killed
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(monitorRunnable);
    }

    // ---- Main Monitoring Loop ----

    private final Runnable monitorRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                checkCurrentApp();
            } catch (Exception e) {
                // Silent fail, keep running
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    private void checkCurrentApp() {
        String foregroundPkg = getForegroundApp();
        if (foregroundPkg == null || foregroundPkg.isEmpty()) return;
        if (foregroundPkg.equals(getPackageName())) return;

        Set<String> monitored = prefs.getMonitoredApps();
        if (!monitored.contains(foregroundPkg)) {
            // Reset session if user switched to non-monitored app
            currentTrackedPkg = "";
            sessionStartTime = 0;
            return;
        }

        // Track session
        long now = System.currentTimeMillis();
        if (!foregroundPkg.equals(currentTrackedPkg)) {
            // New app opened
            if (!currentTrackedPkg.isEmpty() && sessionStartTime > 0) {
                prefs.addUsage(currentTrackedPkg, now - sessionStartTime);
            }
            currentTrackedPkg = foregroundPkg;
            sessionStartTime = now;
        } else {
            // Same app, add usage in chunks
            long elapsed = now - sessionStartTime;
            if (elapsed > CHECK_INTERVAL_MS) {
                prefs.addUsage(foregroundPkg, elapsed);
                sessionStartTime = now;
            }
        }

        // Check limits
        long totalUsageMs = prefs.getUsageToday(foregroundPkg);
        int limitMs = prefs.getTimeLimit(foregroundPkg) * 60 * 1000;
        int usedMin = (int)(totalUsageMs / 60000);

        // Check if limit exceeded → show blocker
        if (totalUsageMs >= limitMs) {
            if (!foregroundPkg.equals(lastBlockedPkg)) {
                lastBlockedPkg = foregroundPkg;
                showBlocker(foregroundPkg);
            }
            return;
        }
        lastBlockedPkg = "";

        // Check 10-minute alerts
        int alertsDue = usedMin / ALERT_INTERVAL_MIN;
        int alertsShown = prefs.getAlertCount(foregroundPkg);

        if (alertsDue > alertsShown) {
            prefs.setAlertCount(foregroundPkg, alertsDue);
            sendUsageAlert(foregroundPkg, usedMin, limitMs / 60000);
        }
    }

    // ---- Get Foreground App ----

    private String getForegroundApp() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, now - 60000, now);

            if (stats == null || stats.isEmpty()) return null;

            SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
            for (UsageStats s : stats) {
                sortedMap.put(s.getLastTimeUsed(), s);
            }
            if (!sortedMap.isEmpty()) {
                return sortedMap.get(sortedMap.lastKey()).getPackageName();
            }
        } catch (Exception e) {
            // Permission not granted
        }
        return null;
    }

    // ---- Blocking Screen ----

    private void showBlocker(String pkg) {
        Intent intent = new Intent(this, BlockerActivity.class);
        intent.putExtra("pkg", pkg);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    // ---- Usage Alert Notification ----

    private void sendUsageAlert(String pkg, int usedMin, int limitMin) {
        String appName = getAppName(pkg);
        int remaining = limitMin - usedMin;

        String title = "⏰ " + appName + " — " + usedMin + " min ho gaye!";
        String body;
        if (remaining <= 10) {
            body = "⚠️ Sirf " + remaining + " minute baaki hain aaj ke liye!";
        } else {
            body = remaining + " minute aur bache hain aaj ke limit mein.";
        }

        PendingIntent pi = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(this, MainActivity.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .build();

        notifManager.notify(NOTIF_ID_ALERT_BASE + pkg.hashCode(), notif);
    }

    // ---- Service Foreground Notification ----

    private Notification buildServiceNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, MainActivity.CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("FocusGuard Active")
            .setContentText("App usage monitor chal raha hai")
            .setOngoing(true)
            .setContentIntent(pi)
            .build();
    }

    // ---- Helper ----

    private String getAppName(String pkg) {
        try {
            return getPackageManager()
                .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))
                .toString();
        } catch (Exception e) {
            return pkg;
        }
    }
}
