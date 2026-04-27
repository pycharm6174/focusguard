package com.focusguard.app;

import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String CHANNEL_SERVICE = "fg_service_ch";
    public static final String CHANNEL_ALERTS  = "usage_alerts_ch";

    private RecyclerView recyclerView;
    private AppListAdapter adapter;
    private final List<AppData> appList = new ArrayList<>();
    private PrefsManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefsManager(this);
        createNotificationChannels();

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        Button btnSetup = findViewById(R.id.btnSetup);
        btnSetup.setOnClickListener(v -> runPermissionFlow());

        loadInstalledApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshListData();
        updateStatusText();
    }

    // ---- Load Apps ----

    private void loadInstalledApps() {
        appList.clear();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo info : allApps) {
            // Show user-installed apps only (skip pure system apps)
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                // Skip our own app
                if (info.packageName.equals(getPackageName())) continue;

                AppData appData = new AppData(
                    info.packageName,
                    pm.getApplicationLabel(info).toString(),
                    pm.getApplicationIcon(info)
                );
                appData.isMonitored = prefs.isMonitored(info.packageName);
                appData.timeLimitMinutes = prefs.getTimeLimit(info.packageName);
                appList.add(appData);
            }
        }

        // Sort alphabetically
        appList.sort((a, b) -> a.appName.compareToIgnoreCase(b.appName));

        adapter = new AppListAdapter(this, appList, prefs);
        recyclerView.setAdapter(adapter);
    }

    private void refreshListData() {
        for (AppData app : appList) {
            app.isMonitored = prefs.isMonitored(app.packageName);
            app.timeLimitMinutes = prefs.getTimeLimit(app.packageName);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    // ---- Permission Flow ----

    private void runPermissionFlow() {
        if (!hasUsageStatsPermission()) {
            showDialog(
                "📊 Usage Access Permission",
                "FocusGuard ko apps ka usage track karne ke liye permission chahiye.\n\n" +
                "Agle screen mein:\n" +
                "1. 'FocusGuard' dhundein\n" +
                "2. Toggle ON karein\n" +
                "3. Wapas aayein",
                "Settings Kholein",
                () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            );
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showDialog(
                "🔒 Overlay Permission",
                "Jab koi app ka time khatam ho, FocusGuard ek blocking screen dikhayega.\n\n" +
                "Agle screen mein FocusGuard ke liye\n'Display over other apps' ON karein.",
                "Settings Kholein",
                () -> startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())))
            );
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        requestBatteryOptimizationExemption();
        startMonitorService();
        updateStatusText();

        new AlertDialog.Builder(this)
            .setTitle("✅ Sab Ready Hai!")
            .setMessage("FocusGuard chal raha hai!\n\nAb apps ke saamne toggle ON karein aur unka time limit set karein.")
            .setPositiveButton("OK", null)
            .show();
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void requestBatteryOptimizationExemption() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } catch (Exception e) {
            // Ignore if not supported
        }
    }

    private void showDialog(String title, String msg, String btnText, Runnable action) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(btnText, (d, w) -> action.run())
            .setNegativeButton("Baad Mein", null)
            .setCancelable(false)
            .show();
    }

    // ---- Service ----

    private void startMonitorService() {
        Intent intent = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // ---- Status ----

    private void updateStatusText() {
        TextView tvStatus = findViewById(R.id.tvStatus);
        boolean usage = hasUsageStatsPermission();
        boolean overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);

        if (usage && overlay) {
            tvStatus.setText("✅ Monitoring chal raha hai");
            tvStatus.setTextColor(0xFF2E7D32);
        } else {
            tvStatus.setText("⚠️ Permissions baaki hain — 'Setup' dabayein");
            tvStatus.setTextColor(0xFFE65100);
        }
    }

    // ---- Notifications ----

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            NotificationChannel service = new NotificationChannel(
                CHANNEL_SERVICE, "FocusGuard Service",
                NotificationManager.IMPORTANCE_LOW);
            service.setDescription("Background monitoring");
            nm.createNotificationChannel(service);

            NotificationChannel alerts = new NotificationChannel(
                CHANNEL_ALERTS, "Usage Alerts",
                NotificationManager.IMPORTANCE_HIGH);
            alerts.setDescription("10 minute reminders");
            nm.createNotificationChannel(alerts);
        }
    }
}
