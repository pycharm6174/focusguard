package com.focusguard.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AppSettingsActivity extends AppCompatActivity {

    private String pkg;
    private PrefsManager prefs;
    private int selectedMinutes = 60;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_settings);

        pkg = getIntent().getStringExtra("pkg");
        String appName = getIntent().getStringExtra("name");
        prefs = new PrefsManager(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(appName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        selectedMinutes = prefs.getTimeLimit(pkg);

        Switch swMonitor = findViewById(R.id.swMonitor);
        SeekBar seekBar = findViewById(R.id.seekBarLimit);
        TextView tvLimit = findViewById(R.id.tvLimitDisplay);
        TextView tvUsageToday = findViewById(R.id.tvUsageToday);
        Button btnSave = findViewById(R.id.btnSave);
        Button btn15 = findViewById(R.id.btn15);
        Button btn30 = findViewById(R.id.btn30);
        Button btn60 = findViewById(R.id.btn60);
        Button btn120 = findViewById(R.id.btn120);

        // Set current state
        swMonitor.setChecked(prefs.isMonitored(pkg));
        seekBar.setMax(240); // max 4 hours
        seekBar.setProgress(selectedMinutes);
        tvLimit.setText(formatTime(selectedMinutes));

        // Show today's usage
        long usageMs = prefs.getUsageToday(pkg);
        int usedMin = (int)(usageMs / 60000);
        tvUsageToday.setText("Aaj ka usage: " + formatTime(usedMin));

        // SeekBar listener
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                // Minimum 5 minutes
                selectedMinutes = Math.max(5, progress);
                tvLimit.setText(formatTime(selectedMinutes));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // Quick preset buttons
        btn15.setOnClickListener(v -> { selectedMinutes = 15; seekBar.setProgress(15); tvLimit.setText(formatTime(15)); });
        btn30.setOnClickListener(v -> { selectedMinutes = 30; seekBar.setProgress(30); tvLimit.setText(formatTime(30)); });
        btn60.setOnClickListener(v -> { selectedMinutes = 60; seekBar.setProgress(60); tvLimit.setText(formatTime(60)); });
        btn120.setOnClickListener(v -> { selectedMinutes = 120; seekBar.setProgress(120); tvLimit.setText(formatTime(120)); });

        // Save button
        btnSave.setOnClickListener(v -> {
            prefs.setTimeLimit(pkg, selectedMinutes);
            if (swMonitor.isChecked()) {
                prefs.addMonitoredApp(pkg);
            } else {
                prefs.removeMonitoredApp(pkg);
            }
            Toast.makeText(this, "✅ Settings save ho gayi!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private String formatTime(int minutes) {
        if (minutes < 60) return minutes + " minute";
        int h = minutes / 60;
        int m = minutes % 60;
        if (m == 0) return h + " ghanta";
        return h + " ghanta " + m + " minute";
    }
}
