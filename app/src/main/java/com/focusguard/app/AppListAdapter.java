package com.focusguard.app;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    private final Context context;
    private final List<AppData> appList;
    private final PrefsManager prefs;

    public AppListAdapter(Context context, List<AppData> appList, PrefsManager prefs) {
        this.context = context;
        this.appList = appList;
        this.prefs = prefs;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppData app = appList.get(position);

        holder.tvAppName.setText(app.appName);
        holder.ivIcon.setImageDrawable(app.icon);

        // Show usage info
        long usageMs = prefs.getUsageToday(app.packageName);
        int usedMin = (int)(usageMs / 60000);
        int limitMin = prefs.getTimeLimit(app.packageName);

        if (app.isMonitored) {
            holder.tvUsage.setText(formatTime(usedMin) + " / " + formatTime(limitMin));
            holder.tvUsage.setVisibility(View.VISIBLE);
        } else {
            holder.tvUsage.setVisibility(View.GONE);
        }

        // Toggle switch without triggering listener
        holder.toggleSwitch.setOnCheckedChangeListener(null);
        holder.toggleSwitch.setChecked(app.isMonitored);
        holder.toggleSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            app.isMonitored = isChecked;
            if (isChecked) {
                prefs.addMonitoredApp(app.packageName);
            } else {
                prefs.removeMonitoredApp(app.packageName);
            }
            notifyItemChanged(position);
        });

        // Click on item → open settings
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, AppSettingsActivity.class);
            intent.putExtra("pkg", app.packageName);
            intent.putExtra("name", app.appName);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    private String formatTime(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60;
        int m = minutes % 60;
        if (m == 0) return h + " hr";
        return h + "hr " + m + "m";
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvAppName, tvUsage;
        Switch toggleSwitch;

        ViewHolder(View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvUsage = itemView.findViewById(R.id.tvUsage);
            toggleSwitch = itemView.findViewById(R.id.toggleSwitch);
        }
    }
}
