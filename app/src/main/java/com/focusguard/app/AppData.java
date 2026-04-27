package com.focusguard.app;

import android.graphics.drawable.Drawable;

public class AppData {
    public String packageName;
    public String appName;
    public Drawable icon;
    public boolean isMonitored;
    public int timeLimitMinutes;

    public AppData(String packageName, String appName, Drawable icon) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
        this.isMonitored = false;
        this.timeLimitMinutes = 60;
    }
}
