package com.focusguard.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class BlockerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocker);

        String pkg = getIntent().getStringExtra("pkg");
        if (pkg == null) { finish(); return; }

        PrefsManager prefs = new PrefsManager(this);
        int limitMin = prefs.getTimeLimit(pkg);
        long usageMs = prefs.getUsageToday(pkg);
        int usedMin = (int)(usageMs / 60000);

        // Set app icon
        ImageView ivIcon = findViewById(R.id.ivBlockedIcon);
        try {
            Drawable icon = getPackageManager().getApplicationIcon(pkg);
            ivIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            ivIcon.setImageResource(android.R.drawable.ic_dialog_alert);
        }

        // Set texts
        String appName = getAppName(pkg);
        TextView tvTitle = findViewById(R.id.tvBlockTitle);
        TextView tvMessage = findViewById(R.id.tvBlockMessage);
        TextView tvStats = findViewById(R.id.tvBlockStats);

        tvTitle.setText(appName + " ka time khatam! 🔒");
        tvMessage.setText("Aapne aaj ka " + formatTime(limitMin) + " ka limit use kar liya.");
        tvStats.setText("Aaj ka total usage: " + formatTime(usedMin));

        // Go Home button
        Button btnHome = findViewById(R.id.btnGoHome);
        btnHome.setOnClickListener(v -> {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
            finish();
        });

        // Emergency Unlock button
        Button btnUnlock = findViewById(R.id.btnEmergency);
        btnUnlock.setOnClickListener(v -> showUnlockDialog(pkg, prefs));
    }

    @Override
    public void onBackPressed() {
        // Prevent back button from going back to blocked app
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    private void showUnlockDialog(String pkg, PrefsManager prefs) {
        new AlertDialog.Builder(this)
            .setTitle("🔓 Emergency Unlock")
            .setMessage("Kya aap sach mein yeh app 15 minute ke liye unlock karna chahte hain?\n\n" +
                        "Ye sirf emergency ke liye hai!")
            .setPositiveButton("Haan, Unlock Karo", (d, w) -> {
                // Give 15 more minutes
                int current = prefs.getTimeLimit(pkg);
                prefs.setTimeLimit(pkg, current + 15);
                finish();
            })
            .setNegativeButton("Nahi, Band Rakhein", null)
            .show();
    }

    private String formatTime(int minutes) {
        if (minutes < 60) return minutes + " minute";
        int h = minutes / 60;
        int m = minutes % 60;
        if (m == 0) return h + " ghanta";
        return h + " ghanta " + m + " minute";
    }

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
