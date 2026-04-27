package com.focusguard.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class PrefsManager {
    private static final String PREF_NAME = "FocusGuardData";
    private static final String KEY_MONITORED = "monitored_apps";
    private static final String PFX_LIMIT = "limit_";
    private static final String PFX_USAGE = "usage_";
    private static final String PFX_RESET = "reset_";
    private static final String PFX_ALERT = "alert_";

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ---- Monitored Apps ----

    public Set<String> getMonitoredApps() {
        return new HashSet<>(prefs.getStringSet(KEY_MONITORED, new HashSet<>()));
    }

    public void addMonitoredApp(String pkg) {
        Set<String> apps = getMonitoredApps();
        apps.add(pkg);
        prefs.edit().putStringSet(KEY_MONITORED, apps).apply();
    }

    public void removeMonitoredApp(String pkg) {
        Set<String> apps = getMonitoredApps();
        apps.remove(pkg);
        prefs.edit().putStringSet(KEY_MONITORED, apps).apply();
    }

    public boolean isMonitored(String pkg) {
        return getMonitoredApps().contains(pkg);
    }

    // ---- Time Limits ----

    public void setTimeLimit(String pkg, int minutes) {
        prefs.edit().putInt(PFX_LIMIT + pkg, minutes).apply();
    }

    public int getTimeLimit(String pkg) {
        return prefs.getInt(PFX_LIMIT + pkg, 60); // default 1 hour
    }

    // ---- Daily Usage Tracking ----

    public void addUsage(String pkg, long milliseconds) {
        resetIfNewDay(pkg);
        long current = getRawUsage(pkg);
        prefs.edit().putLong(PFX_USAGE + pkg, current + milliseconds).apply();
    }

    public long getUsageToday(String pkg) {
        resetIfNewDay(pkg);
        return getRawUsage(pkg);
    }

    private long getRawUsage(String pkg) {
        return prefs.getLong(PFX_USAGE + pkg, 0);
    }

    private void resetIfNewDay(String pkg) {
        long lastReset = prefs.getLong(PFX_RESET + pkg, 0);
        long todayStart = getStartOfToday();
        if (lastReset < todayStart) {
            prefs.edit()
                .putLong(PFX_USAGE + pkg, 0)
                .putLong(PFX_RESET + pkg, todayStart)
                .putInt(PFX_ALERT + pkg, 0)
                .apply();
        }
    }

    private long getStartOfToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ---- Alert Tracking (every 10 min) ----

    public int getAlertCount(String pkg) {
        return prefs.getInt(PFX_ALERT + pkg, 0);
    }

    public void setAlertCount(String pkg, int count) {
        prefs.edit().putInt(PFX_ALERT + pkg, count).apply();
    }
}
